package com.posedetection.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.posedetection.app.camera.CameraManager
import com.posedetection.app.databinding.ActivityMainBinding
import com.posedetection.app.pose.PoseDetector
import com.posedetection.app.pose.PoseEvaluator
import com.posedetection.app.pose.PoseScore

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var poseDetector: PoseDetector
    private lateinit var poseEvaluator: PoseEvaluator
      private var isSkeletonDisplayEnabled = false
    private var isRecording = false
    private var isMirrorMode = false
    private var countdown = 0
    private var currentPoseLandmarks: List<PointF> = emptyList()
    private var referencePoseLandmarks: List<PointF> = emptyList()
    
    private val handler = Handler(Looper.getMainLooper())
    private var mirrorStateCheckRunnable: Runnable? = null
    
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
        // Initialize UI components
        binding.apply {
            // Set initial states
            skeletonStatusText.text = if (isSkeletonDisplayEnabled) {
                getString(R.string.skeleton_display_on)
            } else {
                getString(R.string.skeleton_display_off)
            }
        }
    }
    
    private fun checkPermissions() {
        if (allPermissionsGranted()) {
            initializeCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                initializeCamera()
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun initializeCamera() {
        // Initialize pose detection components
        poseEvaluator = PoseEvaluator()
        
        poseDetector = PoseDetector(
            context = this,
            onPoseDetected = { landmarks ->
                handlePoseDetected(landmarks)
            },
            onError = { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        )
        
        // Initialize camera manager
        cameraManager = CameraManager(
            context = this,
            previewView = binding.cameraPreview,
            onFrameAnalyzed = { bitmap, timestamp ->
                poseDetector.detectPose(bitmap, timestamp)
            },
            onError = { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        )        // Start camera and pose detection
        poseDetector.initialize()
        cameraManager.startCamera(this)
        
        // Initialize skeleton overlay state
        binding.skeletonOverlay.setMirrorMode(isMirrorMode)
        binding.skeletonOverlay.setSkeletonVisibility(isSkeletonDisplayEnabled)
        
        // Update pose detector with actual preview size when available
        handler.postDelayed({
            val previewWidth = binding.cameraPreview.width.toFloat()
            val previewHeight = binding.cameraPreview.height.toFloat()
            if (previewWidth > 0 && previewHeight > 0) {
                poseDetector.updateImageSize(previewWidth, previewHeight)
                Log.d("MainActivity", "Updated pose detector image size: ${previewWidth}x${previewHeight}")
            }
        }, 1500)
        
        // Log available cameras for debugging
        Handler(Looper.getMainLooper()).postDelayed({
            val availableCameras = cameraManager.getAvailableCamerasInfo()
            Log.d("MainActivity", "Available cameras: $availableCameras")
        }, 1000)
    }    private fun handlePoseDetected(landmarks: List<PointF>) {
        // Apply mirror transformation to landmarks if mirror mode is enabled
        val processedLandmarks = if (isMirrorMode) {
            mirrorLandmarks(landmarks)
        } else {
            landmarks
        }
        currentPoseLandmarks = processedLandmarks

        // Log mirror mode state during pose detection
        Log.d("MainActivity", "handlePoseDetected - isMirrorMode: $isMirrorMode, landmarks count: ${landmarks.size}")

        // Update skeleton overlay with original landmarks (since overlay handles mirroring via canvas transformation)
        binding.skeletonOverlay.updateLandmarks(landmarks)
        // ミラーモード設定は毎フレーム実行せず、切り替え時のみ行う
        // binding.skeletonOverlay.setMirrorMode(isMirrorMode)

        // setSkeletonVisibilityは骨格表示のトグル時のみ呼び出す
        // binding.skeletonOverlay.setSkeletonVisibility(isSkeletonDisplayEnabled)

        // Evaluate pose if reference pose is available
        if (referencePoseLandmarks.isNotEmpty()) {
            val score = poseEvaluator.evaluatePose(currentPoseLandmarks, referencePoseLandmarks)
            updateScoreDisplay(score)
        }
    }
    
    private fun mirrorLandmarks(landmarks: List<PointF>): List<PointF> {
        val viewWidth = binding.cameraContainer.width.toFloat()
        return landmarks.map { landmark ->
            PointF(viewWidth - landmark.x, landmark.y)
        }
    }
    
    private fun updateScoreDisplay(score: PoseScore) {
        binding.apply {
            overallGradeText.text = score.overallGrade
            overallScoreText.text = "(${score.overallScore}点)"
            armScoreText.text = score.armGrade
            legScoreText.text = score.legGrade
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_PROG_RED -> {
                handleRedButton()
                true
            }
            KeyEvent.KEYCODE_PROG_GREEN -> {
                handleGreenButton()
                true
            }
            KeyEvent.KEYCODE_PROG_BLUE -> {
                handleBlueButton()
                true
            }
            KeyEvent.KEYCODE_9 -> {
                handleExitApp()
                true
            }
            KeyEvent.KEYCODE_1 -> {
                handleMirrorToggle()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
    
    private fun handleRedButton() {
        // Handle dynamic sequence recording
        if (!isRecording) {
            startDynamicRecording()
        } else {
            stopDynamicRecording()
        }
    }
    
    private fun handleGreenButton() {
        // Handle static pose recording with countdown
        startStaticPoseCountdown()
    }
    
    private fun handleBlueButton() {
        // Toggle skeleton display
        isSkeletonDisplayEnabled = !isSkeletonDisplayEnabled
        binding.skeletonStatusText.text = if (isSkeletonDisplayEnabled) {
            getString(R.string.skeleton_display_on)
        } else {
            getString(R.string.skeleton_display_off)
        }
        // Update skeleton display
        binding.skeletonOverlay.setSkeletonVisibility(isSkeletonDisplayEnabled)    }
    
    private fun handleExitApp() {
        finish()
    }
    
    private fun handleMirrorToggle() {
        // 現在の状態をログ出力
        Log.d("MainActivity", "Mirror toggle pressed - current state: $isMirrorMode")

        // Toggle mirror mode
        isMirrorMode = !isMirrorMode
        
        Log.d("MainActivity", "Toggling mirror mode to: $isMirrorMode")

        // 先に監視を停止し、既存のモニタリングを解除
        stopMirrorStateMonitoring()

        try {
            // Update camera manager setting (this will handle the preview transformation)
            Log.d("MainActivity", "Setting camera mirror mode to: $isMirrorMode")
            cameraManager.setMirrorMode(isMirrorMode)

            // Update skeleton overlay mirror setting with confirmation
            Log.d("MainActivity", "Setting skeleton overlay mirror mode to: $isMirrorMode")
            binding.skeletonOverlay.setMirrorMode(isMirrorMode)

            // Force immediate redraw to apply changes
            binding.skeletonOverlay.invalidate()
            Log.d("MainActivity", "Forced invalidate on skeletonOverlay")

            // Check if mirror mode was actually applied
            val actualMirrorMode = binding.skeletonOverlay.getMirrorMode()
            Log.d("MainActivity", "SkeletonOverlay getMirrorMode() returns: $actualMirrorMode")

            // Show status message
            val statusMessage = if (isMirrorMode) {
                "鏡面表示: ON"
            } else {
                "鏡面表示: OFF"
            }
            Toast.makeText(this, statusMessage, Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("MainActivity", "Error during mirror toggle: ${e.message}", e)
        }

        // ミラーモードの状態を維持するための監視を開始（長期的に実行）
        startMirrorStateMonitoring()
    }

    private fun startMirrorStateMonitoring() {
        // Cancel any existing monitoring
        mirrorStateCheckRunnable?.let { handler.removeCallbacks(it) }

        mirrorStateCheckRunnable = object : Runnable {
            override fun run() {
                Log.d("MainActivity", "Mirror state monitoring - current isMirrorMode: $isMirrorMode")

                // 明示的にSkeletonOverlayのミラーモード状態を強制的に合わせる
                if (binding.skeletonOverlay.getMirrorMode() != isMirrorMode) {
                    Log.d("MainActivity", "Fixing mirror mode discrepancy in SkeletonOverlay")
                    binding.skeletonOverlay.setMirrorMode(isMirrorMode)
                    binding.skeletonOverlay.invalidate()
                }

                // カメラのミラーモード状態も確認して合わせる
                cameraManager.setMirrorMode(isMirrorMode)
                
                // 長期的に監視を継続（アプリ終了まで実行）
                handler.postDelayed(this, 1000) // 1秒ごとにチェック
            }
        }
        
        mirrorStateCheckRunnable?.let { handler.post(it) }
        Log.d("MainActivity", "Started persistent mirror state monitoring")
    }
    
    private fun stopMirrorStateMonitoring() {
        mirrorStateCheckRunnable?.let { handler.removeCallbacks(it) }
        mirrorStateCheckRunnable = null
    }
    
    private fun startDynamicRecording() {
        isRecording = true
        binding.recordingStatusText.text = "録画中..."
        // TODO: Start 10-second dynamic recording
    }
    
    private fun stopDynamicRecording() {
        isRecording = false
        binding.recordingStatusText.text = ""
        // TODO: Stop dynamic recording
    }
    
    private fun startStaticPoseCountdown() {
        countdown = 5
        updateCountdownDisplay()
        
        val countdownRunnable = object : Runnable {
            override fun run() {
                if (countdown > 0) {
                    updateCountdownDisplay()
                    countdown--
                    handler.postDelayed(this, 1000) // 1 second delay
                } else {
                    // Countdown finished - capture current pose as reference
                    binding.countdownText.text = ""
                    captureReferencePose()
                }
            }
        }
        handler.post(countdownRunnable)
    }
    
    private fun captureReferencePose() {
        if (currentPoseLandmarks.isNotEmpty()) {
            referencePoseLandmarks = currentPoseLandmarks.toList()
            Toast.makeText(this, "リファレンスポーズを保存しました", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateCountdownDisplay() {
        binding.countdownText.text = if (countdown > 0) countdown.toString() else ""
    }
      override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        stopMirrorStateMonitoring()
        if (::poseDetector.isInitialized) {
            poseDetector.release()
        }
        if (::cameraManager.isInitialized) {
            cameraManager.stopCamera()
        }
        handler.removeCallbacksAndMessages(null)
    }
}