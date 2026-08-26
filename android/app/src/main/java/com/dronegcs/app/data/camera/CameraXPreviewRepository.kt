package com.dronegcs.app.data.camera

import android.content.Context
import android.util.Size
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.dronegcs.app.domain.model.VideoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Repository for CameraX preview functionality
 * Handles phone camera preview with lifecycle awareness
 */
class CameraXPreviewRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var previewView: PreviewView? = null

    // State
    private val _isActive = MutableStateFlow(false)
    val isActive = _isActive.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        initCameraProvider()
    }

    private fun initCameraProvider() {
        scope.launch(Dispatchers.IO) {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(context).await()
                Timber.d("CameraX ProcessCameraProvider initialized")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize CameraX")
                _error.value = "Camera initialization failed: ${e.message}"
            }
        }
    }

    fun bindToPreviewView(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        this.previewView = previewView
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        startCamera(lifecycleOwner)
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        _isActive.value = false
        Timber.d("CameraX unbound")
    }

    fun switchCamera(facing: Int) {
        currentCameraSelector = when (facing) {
            CameraSelector.LENS_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        cameraProvider?.unbindAll()
        if (previewView != null) {
            // Will be rebound by caller with lifecycle
        }
    }

    private fun startCamera(lifecycleOwner: LifecycleOwner) {
        scope.launch(Dispatchers.IO) {
            val provider = cameraProvider ?: return@launch
            provider.unbindAll()

            val preview = Preview.Builder()
                .setTargetResolution(Size(1920, 1080))
                .build()
                .also { it.setSurfaceProvider(previewView!!.surfaceProvider) }

            val cameraSelector = currentCameraSelector

            try {
                val camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                _isActive.value = true
                _error.value = null
                Timber.d("CameraX preview started with ${camera.cameraInfo}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start camera preview")
                _error.value = "Failed to start camera: ${e.message}"
                _isActive.value = false
            }
        }
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        scope.cancel()
    }
}