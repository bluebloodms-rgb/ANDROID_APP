package com.dronegcs.app.viewmodel

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dronegcs.app.data.camera.CameraXPreviewRepository
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
import javax.inject.Inject

/**
 * ViewModel for managing video sources (CameraX + RTSP)
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraXRepository: CameraXPreviewRepository,
    private val rtspRepository: RtspVideoRepository
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

    // Current camera facing
    private val _cameraFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val cameraFacing = _cameraFacing.asStateFlow()

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
                _cameraFacing.value = source.facing
                // Note: bindToPreviewView called from UI with lifecycle
            }
            is VideoSource.RtspStream -> {
                rtspRepository.play(source.url)
            }
            VideoSource.None -> {}
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

    fun switchCamera() {
        val currentSource = _videoSource.value
        if (currentSource is VideoSource.PhoneCamera) {
            val newFacing = if (currentSource.facing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            val newSource = VideoSource.PhoneCamera(cameraIndex = currentSource.cameraIndex, facing = newFacing)
            setVideoSource(newSource)
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
