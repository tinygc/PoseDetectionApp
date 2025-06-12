package com.posedetection.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var countdown = 0
    private var currentPoseLandmarks: List<PointF> = emptyList()
    private var referencePoseLandmarks: List<PointF> = emptyList()
    
    private val handler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        private const val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
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
        )
        
        // Start camera and pose detection
        poseDetector.initialize()
        cameraManager.startCamera(this)
    }
    
    private fun handlePoseDetected(landmarks: List<PointF>) {
        currentPoseLandmarks = landmarks
        
        // Update skeleton overlay
        binding.skeletonOverlay.updateLandmarks(landmarks)
        binding.skeletonOverlay.setSkeletonVisibility(isSkeletonDisplayEnabled)
        
        // Evaluate pose if reference pose is available
        if (referencePoseLandmarks.isNotEmpty()) {
            val score = poseEvaluator.evaluatePose(currentPoseLandmarks, referencePoseLandmarks)
            updateScoreDisplay(score)
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
        binding.skeletonOverlay.setSkeletonVisibility(isSkeletonDisplayEnabled)
    }
    
    private fun handleExitApp() {
        finish()
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
        if (::poseDetector.isInitialized) {
            poseDetector.release()
        }
        if (::cameraManager.isInitialized) {
            cameraManager.stopCamera()
        }
        handler.removeCallbacksAndMessages(null)
    }
}