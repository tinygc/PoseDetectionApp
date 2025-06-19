package com.posedetection.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.util.Log
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val previewView: PreviewView,
    private val onFrameAnalyzed: (Bitmap, Long) -> Unit,
    private val onError: (String) -> Unit
) {
      private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var isMirrorMode = false
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    companion object {
        private const val TAG = "CameraManager"
        private const val TARGET_FPS = 10 // As specified in requirements
    }
    
    fun startCamera(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner)
            } catch (e: Exception) {
                onError("Failed to start camera: ${e.message}")
                Log.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
      private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner) {
        val cameraProvider = cameraProvider ?: return        // Preview use case
        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // Image analyzer use case for pose detection
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analyzer ->
                analyzer.setAnalyzer(cameraExecutor, PoseAnalyzer())
            }
        
        // Select camera with fallback strategy
        val cameraSelector = selectBestCamera(cameraProvider)
        
        if (cameraSelector == null) {
            onError("No available camera found")
            Log.e(TAG, "No camera available")
            return
        }
          try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()
            
            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            
            // Apply initial mirror mode setting after camera is bound
            updatePreviewTransform()
            
            Log.d(TAG, "Camera bound successfully with selector: $cameraSelector")
            
        } catch (e: Exception) {
            onError("Failed to bind camera use cases: ${e.message}")
            Log.e(TAG, "Use case binding failed", e)
        }
    }
    
    private fun selectBestCamera(cameraProvider: ProcessCameraProvider): CameraSelector? {
        // List available cameras for debugging
        val availableCameras = cameraProvider.availableCameraInfos
        Log.d(TAG, "Available cameras: ${availableCameras.size}")
        
        for (i in availableCameras.indices) {
            val cameraInfo = availableCameras[i]
            Log.d(TAG, "Camera $i: ${cameraInfo}")
        }
        
        // Try different camera selectors in order of preference
        val selectors = listOf(
            // Prefer external/USB cameras (usually have no lens facing specified)
            CameraSelector.DEFAULT_BACK_CAMERA,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            // Fallback to any available camera
            CameraSelector.Builder().build()
        )
        
        for (selector in selectors) {
            try {
                if (cameraProvider.hasCamera(selector)) {
                    Log.d(TAG, "Selected camera with selector: $selector")
                    return selector
                }
            } catch (e: Exception) {
                Log.d(TAG, "Camera selector $selector not available: ${e.message}")
            }
        }
        
        // Last resort: try to use the first available camera
        if (availableCameras.isNotEmpty()) {
            Log.d(TAG, "Using first available camera as fallback")
            return CameraSelector.Builder().build()
        }
        
        return null
    }
    
    private inner class PoseAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalyzedTimestamp = 0L
        private val frameInterval = 1000L / TARGET_FPS // Milliseconds between frames
        
        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()
              // Throttle frame rate to TARGET_FPS
            if (currentTimestamp - lastAnalyzedTimestamp >= frameInterval) {
                val bitmap = imageProxyToBitmap(imageProxy)
                bitmap?.let {
                    // ミラー変換をカメラデータに適用しない（UIレベルでのみ反転）
                    // データはオリジナル状態で渡す
                    onFrameAnalyzed(it, currentTimestamp)
                }
                lastAnalyzedTimestamp = currentTimestamp
            }
            
            imageProxy.close()
        }
        
        private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
            return try {
                val yBuffer = imageProxy.planes[0].buffer
                val uBuffer = imageProxy.planes[1].buffer
                val vBuffer = imageProxy.planes[2].buffer
                
                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()
                
                val nv21 = ByteArray(ySize + uSize + vSize)
                
                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)
                
                val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(android.graphics.Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
                val imageBytes = out.toByteArray()
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) {
                Log.e(TAG, "Error converting ImageProxy to Bitmap", e)
                null
            }
        }
    }
      fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
    
    fun getCameraInfo(): CameraInfo? = camera?.cameraInfo
    
    fun isCameraActive(): Boolean = camera != null    /**
     * Set mirror mode for camera preview
     */
    fun setMirrorMode(enabled: Boolean) {
        isMirrorMode = enabled
        Log.d(TAG, "Setting mirror mode to: $enabled")
        
        // Force update on main thread
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            updatePreviewTransform()
        } else {
            previewView.post {
                updatePreviewTransform()
            }
        }
    }    private fun updatePreviewTransform() {
        try {
            val scaleValue = if (isMirrorMode) -1f else 1f
            
            // Apply scale transformation to PreviewView
            previewView.scaleX = scaleValue
            
            // Force view update
            previewView.invalidate()
            previewView.requestLayout()
            
            Log.d(TAG, "Applied scale transformation: scaleX = $scaleValue")
            Log.d(TAG, "PreviewView current scaleX: ${previewView.scaleX}")
            Log.d(TAG, "PreviewView dimensions: ${previewView.width}x${previewView.height}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying mirror transformation", e)
        }
    }
    
    /**
     * Get information about all available cameras for debugging
     */
    fun getAvailableCamerasInfo(): List<String> {
        val cameraProvider = cameraProvider ?: return emptyList()
        val availableCameras = cameraProvider.availableCameraInfos
        
        return availableCameras.mapIndexed { index, cameraInfo ->
            try {
                val lensFacing = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) && 
                    cameraInfo == cameraProvider.availableCameraInfos.find { 
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) 
                    } -> "BACK"
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) && 
                    cameraInfo == cameraProvider.availableCameraInfos.find { 
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) 
                    } -> "FRONT"
                    else -> "EXTERNAL/USB"
                }
                "Camera $index: $lensFacing"
            } catch (e: Exception) {
                "Camera $index: Unknown (${e.message})"
            }
        }
    }
}