package com.posedetection.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.posedetection.app.R

class SkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isSkeletonVisible = false
    private var landmarks: List<PointF> = emptyList()
    
    private val jointPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.skeleton_joint_color)
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }
    
    private val bonePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.skeleton_bone_color)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val armPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.skeleton_arm_color)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val legPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.skeleton_leg_color)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    fun updateLandmarks(newLandmarks: List<PointF>) {
        landmarks = newLandmarks
        invalidate()
    }
    
    fun setSkeletonVisibility(visible: Boolean) {
        isSkeletonVisible = visible
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isSkeletonVisible || landmarks.isEmpty()) {
            return
        }
        
        // Draw skeleton connections based on MediaPipe pose landmarks
        drawSkeletonConnections(canvas)
        drawJoints(canvas)
    }
    
    private fun drawSkeletonConnections(canvas: Canvas) {
        // MediaPipe pose landmark indices
        // Reference: https://google.github.io/mediapipe/solutions/pose.html
        
        // Face connections (using bone paint)
        drawConnection(canvas, 0, 1, bonePaint) // nose to left_eye_inner
        drawConnection(canvas, 0, 4, bonePaint) // nose to right_eye_inner
        drawConnection(canvas, 1, 2, bonePaint) // left_eye_inner to left_eye
        drawConnection(canvas, 2, 3, bonePaint) // left_eye to left_eye_outer
        drawConnection(canvas, 4, 5, bonePaint) // right_eye_inner to right_eye
        drawConnection(canvas, 5, 6, bonePaint) // right_eye to right_eye_outer
        drawConnection(canvas, 7, 8, bonePaint) // left_ear to right_ear
        
        // Torso connections (using bone paint)
        drawConnection(canvas, 11, 12, bonePaint) // left_shoulder to right_shoulder
        drawConnection(canvas, 11, 23, bonePaint) // left_shoulder to left_hip
        drawConnection(canvas, 12, 24, bonePaint) // right_shoulder to right_hip
        drawConnection(canvas, 23, 24, bonePaint) // left_hip to right_hip
        
        // Left arm connections (using arm paint)
        drawConnection(canvas, 11, 13, armPaint) // left_shoulder to left_elbow
        drawConnection(canvas, 13, 15, armPaint) // left_elbow to left_wrist
        drawConnection(canvas, 15, 17, armPaint) // left_wrist to left_pinky
        drawConnection(canvas, 15, 19, armPaint) // left_wrist to left_index
        drawConnection(canvas, 15, 21, armPaint) // left_wrist to left_thumb
        drawConnection(canvas, 17, 19, armPaint) // left_pinky to left_index
        
        // Right arm connections (using arm paint)
        drawConnection(canvas, 12, 14, armPaint) // right_shoulder to right_elbow
        drawConnection(canvas, 14, 16, armPaint) // right_elbow to right_wrist
        drawConnection(canvas, 16, 18, armPaint) // right_wrist to right_pinky
        drawConnection(canvas, 16, 20, armPaint) // right_wrist to right_index
        drawConnection(canvas, 16, 22, armPaint) // right_wrist to right_thumb
        drawConnection(canvas, 18, 20, armPaint) // right_pinky to right_index
        
        // Left leg connections (using leg paint)
        drawConnection(canvas, 23, 25, legPaint) // left_hip to left_knee
        drawConnection(canvas, 25, 27, legPaint) // left_knee to left_ankle
        drawConnection(canvas, 27, 29, legPaint) // left_ankle to left_heel
        drawConnection(canvas, 27, 31, legPaint) // left_ankle to left_foot_index
        drawConnection(canvas, 29, 31, legPaint) // left_heel to left_foot_index
        
        // Right leg connections (using leg paint)
        drawConnection(canvas, 24, 26, legPaint) // right_hip to right_knee
        drawConnection(canvas, 26, 28, legPaint) // right_knee to right_ankle
        drawConnection(canvas, 28, 30, legPaint) // right_ankle to right_heel
        drawConnection(canvas, 28, 32, legPaint) // right_ankle to right_foot_index
        drawConnection(canvas, 30, 32, legPaint) // right_heel to right_foot_index
    }
    
    private fun drawConnection(canvas: Canvas, startIndex: Int, endIndex: Int, paint: Paint) {
        if (startIndex < landmarks.size && endIndex < landmarks.size) {
            val start = landmarks[startIndex]
            val end = landmarks[endIndex]
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }
    }
    
    private fun drawJoints(canvas: Canvas) {
        for (landmark in landmarks) {
            canvas.drawCircle(landmark.x, landmark.y, 6f, jointPaint)
        }
    }
}