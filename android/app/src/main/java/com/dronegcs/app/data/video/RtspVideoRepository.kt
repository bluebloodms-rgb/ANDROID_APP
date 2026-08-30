package com.dronegcs.app.data.video

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.dronegcs.app.domain.model.VideoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Repository for RTSP/UDP video streaming using ExoPlayer (Media3)
 */
class RtspVideoRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    // State
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering = _buffering.asStateFlow()

    fun bindToPlayerView(playerView: PlayerView) {
        this.playerView = playerView
        initPlayer()
    }

    private fun initPlayer() {
        scope.launch {
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
            playerView?.player = exoPlayer
            Timber.d("ExoPlayer initialized for RTSP")
        }
    }

    fun play(rtspUrl: String) {
        scope.launch {
            _error.value = null
            _buffering.value = true

            val mediaItem = MediaItem.fromUri(rtspUrl)
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true
            Timber.d("Starting RTSP stream: $rtspUrl")
        }
    }

    fun pause() {
        player?.playWhenReady = false
        _isPlaying.value = false
    }

    fun stop() {
        player?.stop()
        _isPlaying.value = false
        _buffering.value = false
    }

    fun release() {
        player?.release()
        player = null
        playerView?.player = null
        playerView = null
        _isPlaying.value = false
        _buffering.value = false
    }

    fun setSurface(surface: Surface?) {
        player?.setVideoSurface(surface)
    }

    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0
    }

    fun getDuration(): Long {
        return player?.duration ?: 0
    }
}