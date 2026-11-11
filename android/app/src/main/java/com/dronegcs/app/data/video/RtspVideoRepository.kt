package com.dronegcs.app.data.video

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Repository for RTSP/UDP video streaming using ExoPlayer (Media3).
 *
 * IMPORTANT: ExoPlayer runs on a dedicated playback HandlerThread to isolate
 * video crashes from the main UI and telemetry. On internal crashes (e.g.,
 * RtspMessageUtil.parseRequest), the player is released and rebuilt automatically.
 */
class RtspVideoRepository(
    private val context: Context
) {

    // Dedicated playback thread - isolates ExoPlayer internal exceptions
    private val playbackThread = HandlerThread("ExoPlayer-RTSP").apply { start() }
    private val playbackHandler = Handler(playbackThread.looper)

    // Main thread scope for UI state updates
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var watchdogJob: Job? = null

    // URL requested via play() before the player/view was ready
    private var pendingUrl: String? = null

    // Crash recovery state
    private var retryCount = 0
    private const val MAX_RETRIES = 1

    // State
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering = _buffering.asStateFlow()

    // Synchronization for player lifecycle
    private val playerLock = Any()

    fun bindToPlayerView(playerView: PlayerView) {
        this.playerView = playerView
        playerView.useController = false
        playerView.setKeepContentOnPlayerReset(true)
        mainScope.launch {
            ensurePlayer()
            playerView.player = player
            // If play() ran before the view was bound (launch restore path),
            // (re)start the requested stream now that the player exists.
            pendingUrl?.let { url -> startPlayback(url) }
        }
    }

    private fun ensurePlayer() {
        synchronized(playerLock) {
            if (player != null) return
            // Low-latency load control: RTSP is live, buffering more than ~1 s of
            // media adds latency that never recovers (ExoPlayer defaults buffer 50s).
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 200,
                    /* maxBufferMs = */ 1000,
                    /* bufferForPlaybackMs = */ 200,
                    /* bufferForPlaybackAfterRebufferMs = */ 500
                )
                .build()

            // Run ExoPlayer on dedicated thread to isolate crashes from UI/telemetry
            val exoPlayer = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setPlaybackLooper(playbackThread.looper)
                .build()

            // Uncaught exception handler on playback thread - catches RtspMessageUtil.parseRequest crashes
            // This handler catches exceptions from the playback thread's looper
            playbackThread.uncaughtExceptionHandler = { thread, throwable ->
                Timber.e(throwable, "ExoPlayer playback thread crashed: ${throwable.message}")
                // Schedule recovery on main thread
                mainScope.launch {
                    handlePlaybackCrash()
                }
            }

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    mainScope.launch {
                        _isPlaying.value = (playbackState == ExoPlayer.STATE_READY)
                        _buffering.value = (playbackState == ExoPlayer.STATE_BUFFERING)
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Timber.e(error, "ExoPlayer error: ${error.errorCode}")
                    mainScope.launch {
                        _error.value = error.message
                        _isPlaying.value = false
                        _buffering.value = false
                    }
                }

                override fun onCues(cues: androidx.media3.common.Cues) { /* ignore */ }
            })

            // Also set an uncaught exception handler on the player itself for any
            // exceptions that escape the internal threads (Media3 1.4+)
            exoPlayer.setUncaughtExceptionHandler { throwable ->
                Timber.e(throwable, "ExoPlayer uncaught exception: ${throwable.message}")
                mainScope.launch {
                    handlePlaybackCrash()
                }
            }

            player = exoPlayer
            Timber.d("ExoPlayer initialized for RTSP on dedicated thread")
        }
    }

    /** Handle ExoPlayer crash: release, rebuild, and retry once */
    private fun handlePlaybackCrash() {
        synchronized(playerLock) {
            if (retryCount >= MAX_RETRIES) {
                Timber.e("Max RTSP retries reached, giving up")
                _error.value = "Video stream failed after $MAX_RETRIES retries"
                _isPlaying.value = false
                _buffering.value = false
                return
            }

            retryCount++
            Timber.w("RTSP crash recovery attempt $retryCount/$MAX_RETRIES")

            // Release crashed player
            releasePlayer()

            // Rebuild and retry the pending URL
            pendingUrl?.let { url ->
                mainScope.launch {
                    delay(1000) // Brief pause before retry
                    ensurePlayer()
                    startPlayback(url)
                }
            }
        }
    }

    /** Catch-up watchdog: if decode drift pushes us behind the live edge,
     *  briefly play faster (1.1x) to re-sync, else restore 1.0x. */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = mainScope.launch {
            while (isActive) {
                delay(2000)
                val p = player ?: break
                // Post to playback thread to safely access player state
                playbackHandler.post {
                    synchronized(playerLock) {
                        val playerRef = player ?: return@post
                        if (playerRef.playbackState != ExoPlayer.STATE_READY || !playerRef.playWhenReady) return@post
                        try {
                            val offset = playerRef.currentLiveOffset
                            if (offset > 600) {
                                playerRef.setPlaybackSpeed(1.1f)
                                Timber.d("Live catch-up: offset=${offset}ms -> speed 1.1x")
                            } else if (offset in 0..200) {
                                playerRef.setPlaybackSpeed(1.0f)
                            }
                        } catch (_: Exception) {
                            // currentLiveOffset is unset for non-live windows
                        }
                    }
                }
            }
        }
    }

    private fun startPlayback(rtspUrl: String) {
        val exoPlayer = synchronized(playerLock) { player } ?: return
        val mediaItem = MediaItem.Builder()
            .setUri(rtspUrl)
            .setLiveConfiguration(
                androidx.media3.common.MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(0)
                    .setMinOffsetMs(0)
                    .setMaxOffsetMs(0)
                    .build()
            )
            .build()

        // Use TCP transport for more stable interleaved framing (avoids UDP packet loss desync)
        val mediaSource = RtspMediaSource.Factory()
            .setTimeoutMs(5000)
            .setForceUseRtpTcp(true)
            .createMediaSource(mediaItem)

        // Wrap in try/catch to catch synchronous exceptions during prepare
        try {
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.setPlaybackSpeed(1.0f)
            _buffering.value = true
            _error.value = null
            startWatchdog()
            Timber.d("Starting RTSP stream: $rtspUrl (TCP forced)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to prepare RTSP stream: $rtspUrl")
            mainScope.launch {
                handlePlaybackCrash()
            }
        }
    }

    fun play(rtspUrl: String) {
        // Validate RTSP URL format to prevent crashes from malformed URLs
        if (!rtspUrl.startsWith("rtsp://", ignoreCase = true)) {
            Timber.e("Invalid RTSP URL (must start with rtsp://): $rtspUrl")
            _error.value = "Invalid RTSP URL format"
            _isPlaying.value = false
            return
        }

        pendingUrl = rtspUrl
        retryCount = 0 // Reset retry counter on new stream
        mainScope.launch {
            ensurePlayer()
            startPlayback(rtspUrl)
            pendingUrl = null
        }
    }

    fun pause() {
        playbackHandler.post { synchronized(playerLock) { player?.playWhenReady = false } }
        _isPlaying.value = false
    }

    fun stop() {
        pendingUrl = null
        retryCount = 0
        watchdogJob?.cancel()
        releasePlayer()
        _isPlaying.value = false
        _buffering.value = false
    }

    fun release() {
        pendingUrl = null
        retryCount = 0
        watchdogJob?.cancel()
        releasePlayer()
        playbackThread.quitSafely()
        mainScope.launch {
            playerView?.player = null
            playerView = null
        }
        _isPlaying.value = false
        _buffering.value = false
    }

    private fun releasePlayer() {
        synchronized(playerLock) {
            val p = player
            if (p != null) {
                playbackHandler.post {
                    p.release()
                }
                player = null
            }
        }
    }

    fun setSurface(surface: Surface?) {
        playbackHandler.post { synchronized(playerLock) { player?.setVideoSurface(surface) } }
    }

    fun getCurrentPosition(): Long {
        // This is called from main thread, but position is safe to read
        return synchronized(playerLock) { player?.currentPosition ?: 0 }
    }

    fun getDuration(): Long {
        return synchronized(playerLock) { player?.duration ?: 0 }
    }
}