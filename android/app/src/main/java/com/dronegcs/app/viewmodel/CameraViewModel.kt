package com.dronegcs.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dronegcs.app.data.camera.CameraXPreviewRepository
import com.dronegcs.app.data.datastore.SettingsRepository
import com.dronegcs.app.data.video.RtspVideoRepository
import com.dronegcs.app.domain.model.VideoSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for managing video sources (CameraX + RTSP)
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraXRepository: CameraXPreviewRepository,
    private val rtspRepository: RtspVideoRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // Current video source
    private val _videoSource = MutableStateFlow<VideoSource>(VideoSource.None)
    val videoSource = _videoSource
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VideoSource.None
        )

    // CameraX state
    val isCameraActive = cameraXRepository.isActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cameraError = cameraXRepository.error
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // RTSP state
    val isRtspPlaying = rtspRepository.isPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val rtspError = rtspRepository.error
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val rtspBuffering = rtspRepository.buffering
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Default RTSP URL
    private val _defaultRtspUrl = MutableStateFlow("")
    val defaultRtspUrl = _defaultRtspUrl.asStateFlow()

    fun setVideoSource(source: VideoSource) {
        // Clean up previous source
        when (_videoSource.value) {
            is VideoSource.PhoneCamera -> cameraXRepository.unbind()
            is VideoSource.RtspStream -> rtspRepository.stop()
            else -> {}
        }

        _videoSource.value = source

        // Start new source
        when (source) {
            is VideoSource.PhoneCamera -> {
                persistVideoSource(settingsRepository.VIDEO_SOURCE_CAMERA, null, source.facing)
                // Note: bindToPreviewView called from UI with lifecycle
            }
            is VideoSource.RtspStream -> {
                _defaultRtspUrl.value = source.url
                persistVideoSource(settingsRepository.VIDEO_SOURCE_RTSP, source.url, null)
                rtspRepository.play(source.url)
            }
            VideoSource.None -> {}
        }
    }

    /**
     * Restores the last-used video source on app launch (Windows parity: the
     * desktop GCS remembers the video source between runs). Falls back to the
     * saved default camera facing when the saved mode is camera/invalid.
     */
    fun restoreSavedVideoSource() {
        viewModelScope.launch {
            try {
                val mode = settingsRepository.getVideoSourceMode()
                if (mode == settingsRepository.VIDEO_SOURCE_RTSP) {
                    val url = settingsRepository.getRtspUrl()
                    if (!url.isNullOrBlank()) {
                        Timber.i("Restoring saved video source: RTSP $url")
                        setVideoSource(VideoSource.RtspStream(url))
                        return@launch
                    }
                    Timber.w("Saved video source is RTSP but URL is empty; falling back to camera")
                }
                // Front/back switching was removed from the UI: always restore the back camera.
                Timber.i("Restoring saved video source: phone camera")
                setVideoSource(VideoSource.PhoneCamera())
            } catch (e: Exception) {
                Timber.w(e, "Failed to restore saved video source; using default camera")
                setVideoSource(VideoSource.PhoneCamera())
            }
        }
    }

    private fun persistVideoSource(mode: String, rtspUrl: String?, facing: Int?) {
        viewModelScope.launch {
            try {
                settingsRepository.setVideoSourceMode(mode)
                if (rtspUrl != null) settingsRepository.setRtspUrl(rtspUrl)
                if (facing != null) settingsRepository.setDefaultCameraFacing(facing)
            } catch (e: Exception) {
                Timber.w(e, "Failed to persist video source")
            }
        }
    }

    fun bindCameraPreview(previewView: androidx.camera.view.PreviewView, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        if (_videoSource.value is VideoSource.PhoneCamera) {
            cameraXRepository.bindToPreviewView(previewView, lifecycleOwner)
        }
    }

    fun releaseVideoResources() {
        cameraXRepository.unbind()
        rtspRepository.release()
    }

    fun bindRtspPlayerView(playerView: androidx.media3.ui.PlayerView) {
        if (_videoSource.value is VideoSource.RtspStream) {
            rtspRepository.bindToPlayerView(playerView)
        }
    }

    fun setDefaultRtspUrl(url: String) {
        _defaultRtspUrl.value = url
    }

    override fun onCleared() {
        super.onCleared()
        cameraXRepository.unbind()
        rtspRepository.release()
    }
}
