package com.posedetection.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PoseDetector(
    private val context: Context,
    private val onPoseDetected: (List<PointF>) -> Unit,
    private val onError: (String) -> Unit
) {
    
    private var poseLandmarker: PoseLandmarker? = null
    private var isInitialized = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    companion object {
        private const val MODEL_FILE = "pose_landmarker.task"
        private const val TARGET_FPS = 10 // As specified in requirements
    }
    
    fun initialize() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_FILE)
                    .build()
                
                val options = PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(PoseLandmarker.ResultListener { result, inputImage ->
                        handlePoseResult(result)
                    })
                    .setErrorListener(PoseLandmarker.ErrorListener { error ->
                        onError("Pose detection error: ${error.message}")
                    })
                    .build()
                
                poseLandmarker = PoseLandmarker.createFromOptions(context, options)
                isInitialized = true
                
            } catch (e: Exception) {
                onError("Failed to initialize pose detector: ${e.message}")
            }
        }
    }
    
    fun detectPose(bitmap: Bitmap, timestampMs: Long) {
        if (!isInitialized || poseLandmarker == null) {
            return
        }
        
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            poseLandmarker?.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            onError("Error during pose detection: ${e.message}")
        }
    }
    
    private fun handlePoseResult(result: PoseLandmarkerResult) {
        if (result.landmarks().isNotEmpty()) {
            val landmarks = result.landmarks()[0] // First person's landmarks
            val points = landmarks.map { landmark ->
                PointF(
                    landmark.x() * getImageWidth(), // Convert normalized coordinates to pixel coordinates
                    landmark.y() * getImageHeight()
                )
            }
            
            coroutineScope.launch(Dispatchers.Main) {
                onPoseDetected(points)
            }
        }
    }
    
    // These values should be set based on the actual camera preview size
    private fun getImageWidth(): Float = 640f // TODO: Get actual preview width
    private fun getImageHeight(): Float = 480f // TODO: Get actual preview height
    
    fun updateImageSize(width: Float, height: Float) {
        // TODO: Store actual image dimensions for coordinate conversion
    }
    
    fun release() {
        poseLandmarker?.close()
        poseLandmarker = null
        isInitialized = false
    }
    
    fun isReady(): Boolean = isInitialized && poseLandmarker != null
}