package com.posedetection.app.pose

import android.graphics.PointF
import kotlin.math.*

data class PoseScore(
    val overallGrade: String,
    val overallScore: Int,
    val armGrade: String,
    val legGrade: String,
    val bodyGrade: String
)

class PoseEvaluator {
    
    companion object {
        // Joint indices based on MediaPipe pose landmarks
        private const val LEFT_SHOULDER = 11
        private const val RIGHT_SHOULDER = 12
        private const val LEFT_ELBOW = 13
        private const val RIGHT_ELBOW = 14
        private const val LEFT_WRIST = 15
        private const val RIGHT_WRIST = 16
        private const val LEFT_HIP = 23
        private const val RIGHT_HIP = 24
        private const val LEFT_KNEE = 25
        private const val RIGHT_KNEE = 26
        private const val LEFT_ANKLE = 27
        private const val RIGHT_ANKLE = 28
        
        // Weight ratios as per requirements
        private const val SILHOUETTE_WEIGHT = 0.7f
        private const val JOINT_ANGLE_WEIGHT = 0.3f
    }
    
    fun evaluatePose(currentPose: List<PointF>, referencePose: List<PointF>): PoseScore {
        if (currentPose.size < 33 || referencePose.size < 33) {
            return PoseScore("E", 0, "E", "E", "E")
        }
        
        // Calculate silhouette similarity (70% weight)
        val silhouetteScore = calculateSilhouetteScore(currentPose, referencePose)
        
        // Calculate joint angle similarity (30% weight)
        val jointAngleScore = calculateJointAngleScore(currentPose, referencePose)
        
        // Combined score
        val overallScore = (silhouetteScore * SILHOUETTE_WEIGHT + jointAngleScore * JOINT_ANGLE_WEIGHT).toInt()
        
        // Calculate part-specific scores
        val armScore = calculateArmScore(currentPose, referencePose)
        val legScore = calculateLegScore(currentPose, referencePose)
        val bodyScore = calculateBodyScore(currentPose, referencePose)
        
        return PoseScore(
            overallGrade = scoreToGrade(overallScore),
            overallScore = overallScore,
            armGrade = scoreToGrade(armScore),
            legGrade = scoreToGrade(legScore),
            bodyGrade = scoreToGrade(bodyScore)
        )
    }
    
    private fun calculateSilhouetteScore(current: List<PointF>, reference: List<PointF>): Float {
        // Simplified silhouette comparison based on overall pose shape
        val currentCentroid = calculateCentroid(current)
        val referenceCentroid = calculateCentroid(reference)
        
        var totalSimilarity = 0f
        var validPoints = 0
        
        for (i in current.indices) {
            if (i < reference.size) {
                val currentNormalized = normalizePoint(current[i], currentCentroid)
                val referenceNormalized = normalizePoint(reference[i], referenceCentroid)
                
                val distance = calculateDistance(currentNormalized, referenceNormalized)
                val similarity = max(0f, 1f - distance / 100f) // Normalize distance
                
                totalSimilarity += similarity
                validPoints++
            }
        }
        
        return if (validPoints > 0) (totalSimilarity / validPoints) * 100f else 0f
    }
    
    private fun calculateJointAngleScore(current: List<PointF>, reference: List<PointF>): Float {
        val angleScores = mutableListOf<Float>()
        
        // Calculate angle scores for important joints (elbows, wrists, knees, ankles)
        angleScores.add(calculateAngleScore(current, reference, LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST))
        angleScores.add(calculateAngleScore(current, reference, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST))
        angleScores.add(calculateAngleScore(current, reference, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE))
        angleScores.add(calculateAngleScore(current, reference, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE))
        
        return angleScores.filter { it >= 0 }.average().toFloat() * 100f
    }
    
    private fun calculateAngleScore(current: List<PointF>, reference: List<PointF>, p1: Int, p2: Int, p3: Int): Float {
        if (p1 >= current.size || p2 >= current.size || p3 >= current.size ||
            p1 >= reference.size || p2 >= reference.size || p3 >= reference.size) {
            return -1f
        }
        
        val currentAngle = calculateAngle(current[p1], current[p2], current[p3])
        val referenceAngle = calculateAngle(reference[p1], reference[p2], reference[p3])
        
        val angleDifference = abs(currentAngle - referenceAngle)
        return max(0f, 1f - angleDifference / 180f) // Normalize angle difference
    }
    
    private fun calculateArmScore(current: List<PointF>, reference: List<PointF>): Int {
        val leftArmScore = calculateAngleScore(current, reference, LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST)
        val rightArmScore = calculateAngleScore(current, reference, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST)
        
        val avgScore = (leftArmScore + rightArmScore) / 2f
        return (avgScore * 100f).toInt()
    }
    
    private fun calculateLegScore(current: List<PointF>, reference: List<PointF>): Int {
        val leftLegScore = calculateAngleScore(current, reference, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE)
        val rightLegScore = calculateAngleScore(current, reference, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE)
        
        val avgScore = (leftLegScore + rightLegScore) / 2f
        return (avgScore * 100f).toInt()
    }
    
    private fun calculateBodyScore(current: List<PointF>, reference: List<PointF>): Int {
        // Body score based on torso alignment
        val bodyAlignment = calculateSilhouetteScore(
            current.subList(LEFT_SHOULDER, RIGHT_HIP + 1),
            reference.subList(LEFT_SHOULDER, RIGHT_HIP + 1)
        )
        return bodyAlignment.toInt()
    }
    
    private fun calculateCentroid(points: List<PointF>): PointF {
        val x = points.map { it.x }.average().toFloat()
        val y = points.map { it.y }.average().toFloat()
        return PointF(x, y)
    }
    
    private fun normalizePoint(point: PointF, centroid: PointF): PointF {
        return PointF(point.x - centroid.x, point.y - centroid.y)
    }
    
    private fun calculateDistance(p1: PointF, p2: PointF): Float {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }
    
    private fun calculateAngle(p1: PointF, p2: PointF, p3: PointF): Float {
        val vector1 = PointF(p1.x - p2.x, p1.y - p2.y)
        val vector2 = PointF(p3.x - p2.x, p3.y - p2.y)
        
        val dot = vector1.x * vector2.x + vector1.y * vector2.y
        val mag1 = sqrt(vector1.x.pow(2) + vector1.y.pow(2))
        val mag2 = sqrt(vector2.x.pow(2) + vector2.y.pow(2))
        
        if (mag1 == 0f || mag2 == 0f) return 0f
        
        val cosAngle = dot / (mag1 * mag2)
        return acos(cosAngle.coerceIn(-1f, 1f)) * 180f / PI.toFloat()
    }
    
    private fun scoreToGrade(score: Int): String {
        return when {
            score >= 90 -> "A"
            score >= 80 -> "B"
            score >= 70 -> "C"
            score >= 60 -> "D"
            else -> "E"
        }
    }
}