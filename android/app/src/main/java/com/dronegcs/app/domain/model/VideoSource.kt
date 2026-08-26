package com.dronegcs.app.domain.model

import androidx.camera.core.CameraSelector

/**
 * Video source types for the drone GCS
 */
sealed interface VideoSource {
    data class PhoneCamera(val cameraIndex: Int = 0, val facing: Int = CameraSelector.LENS_FACING_BACK) : VideoSource {
        companion object {
            const val BACK = CameraSelector.LENS_FACING_BACK
            const val FRONT = CameraSelector.LENS_FACING_FRONT
        }
    }

    data class RtspStream(val url: String) : VideoSource

    object None : VideoSource

    companion object {
        fun fromString(source: String): VideoSource {
            return when {
                source.startsWith("rtsp://") || source.startsWith("udp://") -> RtspStream(source)
                source == "front" -> PhoneCamera(facing = CameraSelector.LENS_FACING_FRONT)
                source == "back" -> PhoneCamera(facing = CameraSelector.LENS_FACING_BACK)
                else -> None
            }
        }
    }
}