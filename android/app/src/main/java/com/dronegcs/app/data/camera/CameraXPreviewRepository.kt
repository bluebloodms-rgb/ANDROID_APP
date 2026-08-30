package com.dronegcs.app.data.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Repository for CameraX preview functionality.
 *
 * IMPORTANT: all CameraX calls (unbindAll / bindToLifecycle) MUST run on the
 * main thread - CameraX throws IllegalStateException otherwise. The injected
 * scope may use any dispatcher, so camera work is wrapped in withContext(Main).
 */
class CameraXPreviewRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null

    // Prevents duplicate concurrent bind attempts (they race and crash)
    private val bindMutex = Mutex()

    // State
    private val _isActive = MutableStateFlow(false)
    val isActive = _isActive.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        initCameraProvider()
    }

    private fun initCameraProvider() {
        scope.launch {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(context).await()
                Timber.d("CameraX ProcessCameraProvider initialized")
                // If a preview was requested before the provider was ready,
                // start it now.
                withContext(Dispatchers.Main) {
                    val owner = lifecycleOwner ?: return@withContext
                    if (_isActive.value == false && previewView != null) {
                        startCameraInternal(owner)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize CameraX")
                _error.value = "Camera initialization failed: ${e.message}"
            }
        }
    }

    fun bindToPreviewView(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        this.previewView = previewView
        this.lifecycleOwner = lifecycleOwner
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        startCamera(lifecycleOwner)
    }

    fun unbind() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Timber.w(e, "unbind failed")
        }
        _isActive.value = false
        Timber.d("CameraX unbound")
    }

    fun switchCamera(facing: Int) {
        currentCameraSelector = when (facing) {
            CameraSelector.LENS_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        val owner = lifecycleOwner ?: return
        startCamera(owner)
    }

    private fun startCamera(lifecycleOwner: LifecycleOwner) {
        scope.launch {
            bindMutex.withLock {
                withContext(Dispatchers.Main) {
                    startCameraInternal(lifecycleOwner)
                }
            }
        }
    }

    private fun startCameraInternal(lifecycleOwner: LifecycleOwner) {
        val provider = cameraProvider ?: return
        val surfaceProvider = previewView?.surfaceProvider ?: return
        try {
            provider.unbindAll()
        } catch (e: Exception) {
            Timber.w(e, "unbindAll before bind failed (ignorable)")
        }

        val preview = Preview.Builder()
            .setTargetResolution(Size(1920, 1080))
            .build()
            .also { it.setSurfaceProvider(surfaceProvider) }

        val cameraSelector = currentCameraSelector

        try {
            val camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
            _isActive.value = true
            _error.value = null
            Timber.d("CameraX preview started: ${camera.cameraInfo}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start camera preview")
            _error.value = "Failed to start camera: ${e.message}"
            _isActive.value = false
        }
    }

    fun shutdown() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        _isActive.value = false
    }
}
