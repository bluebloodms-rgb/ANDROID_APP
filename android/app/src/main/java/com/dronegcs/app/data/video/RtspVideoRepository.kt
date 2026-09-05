package com.dronegcs.app.data.video

import android.content.Context
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
 * IMPORTANT: ExoPlayer and PlayerView must only be accessed on the MAIN
 * thread (Media3 asserts this), so all player lifecycle work runs on
 * [mainScope] — not on a background dispatcher.
 */
class RtspVideoRepository(
    private val context: Context
) {

    // Media3 requires main-thread access; ExoPlayer internally offloads work.
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var watchdogJob: Job? = null

    private companion object {
        // Restart the stream after this long stuck in BUFFERING.
        const val STALL_RESTART_MS = 10_000L
    }

    // Last successfully-started URL, used by the stall watchdog to recover.
    private var lastUrl: String? = null

    // URL requested via play() before the player/view was ready (e.g. when the
    // source is restored on app launch before the UI binds the PlayerView).
    private var pendingUrl: String? = null

    // State
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering = _buffering.asStateFlow()

    fun bindToPlayerView(playerView: PlayerView) {
        this.playerView = playerView
        playerView.useController = false
        playerView.setKeepContentOnPlayerReset(true)
        mainScope.launch {
            // Harden every player path: on some devices/drivers ExoPlayer
            // creation or surface attach can throw (seen on older Androids).
            // The app must never crash — surface the error in the UI instead.
            try {
                ensurePlayer()
                playerView.player = player
                // If play() ran before the view was bound (launch restore path),
                // (re)start the requested stream now that the player exists.
                pendingUrl?.let { url -> startPlayback(url) }
            } catch (t: Throwable) {
                Timber.e(t, "Player bind failed")
                _error.value = "Video init failed: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    private fun ensurePlayer() {
        if (player != null) return
        // Low-latency load control: RTSP is live, buffering more than ~1 s of
        // media adds latency that never recovers (ExoPlayer defaults buffer 50s).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                // Media3 enforces a hard floor on these values (older builds
                // throw "minBufferMs cannot be less than ..." at player init,
                // killing the stream). Use the smallest ALLOWED values: still
                // far below ExoPlayer's 50s default, so RTSP stays low-latency.
                /* minBufferMs = */ 2000,
                /* maxBufferMs = */ 5000,
                /* bufferForPlaybackMs = */ 1000,
                /* bufferForPlaybackAfterRebufferMs = */ 2000
            )
            .build()
        val exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
        // NOTE: media3 1.x has no ExoPlayer.setLiveConfiguration(...) top-level
        // Live-window pinning for RTSP is handled via MediaItem.LiveConfiguration below
        // and the low-buffer LoadControl above keeps us near the live edge.

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _isPlaying.value = (playbackState == ExoPlayer.STATE_READY)
                _buffering.value = (playbackState == ExoPlayer.STATE_BUFFERING)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Timber.e(error, "ExoPlayer error")
                _error.value = error.message
                _isPlaying.value = false
            }
        })

        player = exoPlayer
        Timber.d("ExoPlayer initialized for RTSP")
    }

    /** Live watchdog: restart the stream if it stalls (TCP can block silently
     *  after a heavy loss burst; auto-recover instead of a frozen image). */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        var bufferingSinceMs = 0L
        watchdogJob = mainScope.launch {
            while (isActive) {
                delay(2000)
                val p = player ?: break
                if (!p.playWhenReady) continue
                val now = System.currentTimeMillis()
                if (p.playbackState == ExoPlayer.STATE_BUFFERING) {
                    if (bufferingSinceMs == 0L) {
                        bufferingSinceMs = now
                    } else if (now - bufferingSinceMs > STALL_RESTART_MS) {
                        Timber.w("RTSP stream stalled >${STALL_RESTART_MS / 1000}s - restarting")
                        bufferingSinceMs = 0L
                        restartPlayback()
                    }
                } else {
                    bufferingSinceMs = 0L
                }
            }
        }
    }

    private fun restartPlayback() {
        val url = pendingUrl ?: lastUrl ?: return
        startPlayback(url)
    }

    private fun startPlayback(rtspUrl: String) {
        val exoPlayer = player ?: return
        try {
            val mediaItem = MediaItem.Builder()
                .setUri(rtspUrl)
                // Target the live edge; avoid min/max offset setters — they are
                // validated against the window and can throw on some streams.
                .setLiveConfiguration(
                    androidx.media3.common.MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(0)
                        .build()
                )
                .build()
            val mediaSource = RtspMediaSource.Factory()
                // RTP over TCP (interleaved): UDP loses packets on the lossy SIYI
                // WiFi link and H.264 renders green/magenta macroblock smear.
                // TCP delivers every packet (or stalls) -> no corruption.
                .setForceUseRtpTcp(true)
                .setTimeoutMs(5000)
                .createMediaSource(mediaItem)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.setPlaybackSpeed(1.0f)
            _buffering.value = true
            _error.value = null
            lastUrl = rtspUrl
            startWatchdog()
            Timber.d("Starting RTSP stream: $rtspUrl")
        } catch (e: Exception) {
            // Never let a bad URL or blocked transport (cleartext policy on
            // Android 9+) crash the app - surface it in the RTSP dialog instead.
            Timber.e(e, "Failed to start RTSP stream: $rtspUrl")
            _buffering.value = false
            _isPlaying.value = false
            _error.value = "Stream error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun play(rtspUrl: String) {
        pendingUrl = rtspUrl
        mainScope.launch {
            try {
                ensurePlayer()
                startPlayback(rtspUrl)
                pendingUrl = null
            } catch (t: Throwable) {
                Timber.e(t, "Failed to start RTSP stream: $rtspUrl")
                pendingUrl = null
                _buffering.value = false
                _isPlaying.value = false
                _error.value = "Stream error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    fun pause() {
        mainScope.launch { player?.playWhenReady = false }
        _isPlaying.value = false
    }

    fun stop() {
        pendingUrl = null
        watchdogJob?.cancel()
        mainScope.launch {
            player?.stop()
        }
        _isPlaying.value = false
        _buffering.value = false
    }

    fun release() {
        pendingUrl = null
        watchdogJob?.cancel()
        mainScope.launch {
            player?.release()
            player = null
            playerView?.player = null
            playerView = null
        }
        _isPlaying.value = false
        _buffering.value = false
    }

    fun setSurface(surface: Surface?) {
        mainScope.launch { player?.setVideoSurface(surface) }
    }

    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0
    }

    fun getDuration(): Long {
        return player?.duration ?: 0
    }
}