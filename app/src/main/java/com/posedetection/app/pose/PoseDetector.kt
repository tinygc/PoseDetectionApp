package com.posedetection.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
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
import java.io.File
import java.io.IOException

class PoseDetector(
    private val context: Context,
    private val onPoseDetected: (List<PointF>) -> Unit,
    private val onError: (String) -> Unit
) {
    
    private var poseLandmarker: PoseLandmarker? = null
    private var isInitialized = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
      companion object {
        private const val TAG = "PoseDetector"
        private const val MODEL_FILE = "pose_landmarker.task"
        private const val TARGET_FPS = 10 // As specified in requirements
    }    fun initialize() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting pose detector initialization")
                
                // Check if asset file exists first
                checkAssetFile()
                
                // Copy asset file to internal storage for more reliable access
                val modelFile = copyAssetToInternalStorage()
                
                Log.d(TAG, "Creating base options with model file: ${modelFile.absolutePath}")
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_FILE) // Keep using asset path first
                    .build()
                
                Log.d(TAG, "Creating pose landmarker options")
                val options = PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result: PoseLandmarkerResult, _: MPImage ->
                        Log.d(TAG, "Pose detection result received")
                        handlePoseResult(result)
                    }
                    .setErrorListener { error: RuntimeException ->
                        Log.e(TAG, "Pose detection runtime error", error)
                        onError("Pose detection error: ${error.message}")
                    }
                    .build()
                
                Log.d(TAG, "Creating PoseLandmarker from options")
                poseLandmarker = PoseLandmarker.createFromOptions(context, options)
                isInitialized = true
                
                Log.d(TAG, "Pose detector initialization completed successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize pose detector", e)
                // Try alternative initialization with file path
                tryAlternativeInitialization(e)
            }
        }
    }
    
    private fun copyAssetToInternalStorage(): File {
        val modelFile = File(context.filesDir, MODEL_FILE)
        if (!modelFile.exists()) {
            context.assets.open(MODEL_FILE).use { inputStream ->
                modelFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d(TAG, "Asset file copied to internal storage: ${modelFile.absolutePath}")
        }
        return modelFile
    }
    
    private fun tryAlternativeInitialization(originalException: Exception) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Trying alternative initialization method")
                
                val modelFile = copyAssetToInternalStorage()
                
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(modelFile.absolutePath)
                    .build()
                
                val options = PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result: PoseLandmarkerResult, _: MPImage ->
                        Log.d(TAG, "Pose detection result received (alternative method)")
                        handlePoseResult(result)
                    }
                    .setErrorListener { error: RuntimeException ->
                        Log.e(TAG, "Pose detection runtime error (alternative method)", error)
                        onError("Pose detection error: ${error.message}")
                    }
                    .build()
                
                poseLandmarker = PoseLandmarker.createFromOptions(context, options)
                isInitialized = true
                
                Log.d(TAG, "Alternative pose detector initialization completed successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Alternative initialization also failed", e)
                onError("Failed to initialize pose detector (both methods failed):\nOriginal: ${originalException.message}\nAlternative: ${e.message}")
            }
        }
    }
      private fun checkAssetFile() {
        try {
            Log.d(TAG, "Checking asset file: $MODEL_FILE")
            val inputStream = context.assets.open(MODEL_FILE)
            val size = inputStream.available()
            inputStream.close()
            Log.d(TAG, "Asset file $MODEL_FILE found. Size: $size bytes")
            
            // Also list all assets to verify structure
            val assetList = context.assets.list("")
            Log.d(TAG, "Available assets: ${assetList?.joinToString(", ")}")
            
        } catch (e: IOException) {
            Log.e(TAG, "Asset file $MODEL_FILE not found", e)
            throw RuntimeException("Asset file $MODEL_FILE not found or cannot be opened: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error checking asset file", e)
            throw RuntimeException("Unexpected error checking asset file: ${e.message}")
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
    private var imageWidth: Float = 640f
    private var imageHeight: Float = 480f
    
    private fun getImageWidth(): Float = imageWidth
    private fun getImageHeight(): Float = imageHeight
    
    fun updateImageSize(width: Float, height: Float) {
        imageWidth = width
        imageHeight = height
    }
    
    fun release() {
        poseLandmarker?.close()
        poseLandmarker = null
        isInitialized = false
    }
    
    fun isReady(): Boolean = isInitialized && poseLandmarker != null
}