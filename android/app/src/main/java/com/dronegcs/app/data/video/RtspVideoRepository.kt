package com.dronegcs.app.data.video

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        mainScope.launch {
            ensurePlayer()
            playerView.player = player
            // If play() ran before the view was bound (launch restore path),
            // (re)start the requested stream now that the player exists.
            pendingUrl?.let { url -> startPlayback(url) }
        }
    }

    private fun ensurePlayer() {
        if (player != null) return
        val exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context))
            .build()

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

    private fun startPlayback(rtspUrl: String) {
        val exoPlayer = player ?: return
        val mediaItem = MediaItem.fromUri(rtspUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        _buffering.value = true
        _error.value = null
        Timber.d("Starting RTSP stream: $rtspUrl")
    }

    fun play(rtspUrl: String) {
        pendingUrl = rtspUrl
        mainScope.launch {
            ensurePlayer()
            startPlayback(rtspUrl)
            pendingUrl = null
        }
    }

    fun pause() {
        mainScope.launch { player?.playWhenReady = false }
        _isPlaying.value = false
    }

    fun stop() {
        pendingUrl = null
        mainScope.launch {
            player?.stop()
        }
        _isPlaying.value = false
        _buffering.value = false
    }

    fun release() {
        pendingUrl = null
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