package com.biometrics.utils

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import android.graphics.PointF

/**
 * Extracts individual fingerprint regions from a hand image using MediaPipe landmarks
 */
class FingerprintExtractor {

    companion object {
        private const val TAG = "FingerprintExtractor"

        // MediaPipe Hand Landmark indices
        // Thumb: 1-4, Index: 5-8, Middle: 9-12, Ring: 13-16, Pinky: 17-20
        private val FINGER_LANDMARKS = mapOf(
            FingerType.THUMB to listOf(1, 2, 3, 4),
            FingerType.INDEX to listOf(5, 6, 7, 8),
            FingerType.MIDDLE to listOf(9, 10, 11, 12),
            FingerType.RING to listOf(13, 14, 15, 16),
            FingerType.PINKY to listOf(17, 18, 19, 20)
        )

        // Padding percentage around detected finger region
        private const val PADDING_PERCENT = 0.30f

        // Minimum quality score (0-100)
        private const val MIN_QUALITY_SCORE = 60
    }

    enum class FingerType {
        THUMB, INDEX, MIDDLE, RING, PINKY
    }

    data class FingerprintImage(
        val fingerType: FingerType,
        val bitmap: Bitmap,
        val qualityScore: Float,
        val roi: RectF
    )

    data class ExtractionResult(
        val fingerprints: List<FingerprintImage>,
        val handBitmap: Bitmap,
        val isRightHand: Boolean,
        val overallQuality: Float
    )

    /**
     * Extract fingerprint regions from hand image using landmarks
     */
    fun extractFingerprints(
        bitmap: Bitmap,
        handLandmarkerResult: HandLandmarkerResult,
        handIndex: Int = 0
    ): ExtractionResult? {

        if (handLandmarkerResult.landmarks().isEmpty()) {
            Log.w(TAG, "No hands detected")
            return null
        }

        val landmarks = handLandmarkerResult.landmarks()[handIndex]
        val handedness = handLandmarkerResult.handedness()[handIndex]
        val isRightHand = handedness[0].categoryName() == "Right"

        val width = bitmap.width
        val height = bitmap.height
        val fingerprints = mutableListOf<FingerprintImage>()

        // Process each finger
        for ((fingerType, landmarkIndices) in FINGER_LANDMARKS) {
            try {
                val roi = calculateFingerprintROI(landmarks, landmarkIndices, width, height)

                // Validate ROI
                if (!isValidROI(roi, width, height)) {
                    Log.w(TAG, "Invalid ROI for $fingerType")
                    continue
                }

                // Crop finger region
                val fingerBitmap = cropFinger(bitmap, roi)

                // Enhance and assess quality
                val enhancedBitmap = enhanceFingerprint(fingerBitmap)
                val quality = assessQuality(enhancedBitmap)

                if (quality >= MIN_QUALITY_SCORE) {
                    fingerprints.add(
                        FingerprintImage(
                            fingerType = fingerType,
                            bitmap = enhancedBitmap,
                            qualityScore = quality,
                            roi = roi
                        )
                    )
                } else {
                    Log.d(TAG, "$fingerType quality too low: $quality")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting $fingerType: ${e.message}", e)
            }
        }

        val overallQuality = fingerprints.map { it.qualityScore }.average().toFloat()

        return ExtractionResult(
            fingerprints = fingerprints,
            handBitmap = bitmap,
            isRightHand = isRightHand,
            overallQuality = overallQuality
        )
    }

    /**
     * Calculate ROI for fingerprint area (fingertip to first joint)
     */
    private fun calculateFingerprintROI(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        landmarkIndices: List<Int>,
        imageWidth: Int,
        imageHeight: Int
    ): RectF {

        // Get landmark positions
        val points = landmarkIndices.map { idx ->
            val lm = landmarks[idx]
            PointF(lm.x() * imageWidth, lm.y() * imageHeight)
        }

        // For fingerprint, we want from tip (last index) to second joint (second index)
        // This gives us the distal phalanx where the fingerprint is
        val tip = points.last()
        val dip = points[points.size - 2] // Distal interphalangeal joint
        val pip = points[points.size - 3] // Proximal interphalangeal joint

        // Calculate finger direction vector
        val dx = tip.x - dip.x
        val dy = tip.y - dip.y
        val length = sqrt(dx * dx + dy * dy)

        // Calculate perpendicular vector for width
        val perpX = -dy / length
        val perpY = dx / length

        // Estimate finger width (typically 60-70% of segment length)
        val fingerWidth = length * 0.65f

        // Create ROI from tip to PIP joint (covers full fingerprint area)
        val roiLength = sqrt((tip.x - pip.x) * (tip.x - pip.x) + (tip.y - pip.y) * (tip.y - pip.y))

        // Calculate corners of rectangle
        val halfWidth = fingerWidth / 2
        val left = min(tip.x + perpX * halfWidth, pip.x + perpX * halfWidth)
        val right = max(tip.x - perpX * halfWidth, pip.x - perpX * halfWidth)
        val top = min(tip.y + perpY * halfWidth, pip.y + perpY * halfWidth)
        val bottom = max(tip.y - perpY * halfWidth, pip.y - perpY * halfWidth)

        // Add padding
        val paddingX = (right - left) * PADDING_PERCENT
        val paddingY = (bottom - top) * PADDING_PERCENT

        return RectF(
            max(0f, left - paddingX),
            max(0f, top - paddingY),
            min(imageWidth.toFloat(), right + paddingX),
            min(imageHeight.toFloat(), bottom + paddingY)
        )
    }

    /**
     * Validate if ROI is within bounds and has reasonable dimensions
     */
    private fun isValidROI(roi: RectF, imageWidth: Int, imageHeight: Int): Boolean {
        val width = roi.width()
        val height = roi.height()

        // Check if ROI is within image bounds
        if (roi.left < 0 || roi.top < 0 || roi.right > imageWidth || roi.bottom > imageHeight) {
            return false
        }

        // Check minimum size (at least 50x50 pixels for fingerprint)
        if (width < 50 || height < 50) {
            return false
        }

        // Check aspect ratio (fingerprint should be roughly 0.5 to 2.0)
        val aspectRatio = width / height
        if (aspectRatio < 0.3 || aspectRatio > 3.0) {
            return false
        }

        return true
    }

    /**
     * Crop finger region from full image
     */
    private fun cropFinger(bitmap: Bitmap, roi: RectF): Bitmap {
        val rect = Rect(
            roi.left.toInt(),
            roi.top.toInt(),
            roi.right.toInt(),
            roi.bottom.toInt()
        )

        return Bitmap.createBitmap(
            bitmap,
            rect.left,
            rect.top,
            rect.width(),
            rect.height()
        )
    }




    /**
     * Enhance fingerprint image quality
     */
    private fun enhanceFingerprint(bitmap: Bitmap): Bitmap
    {
        // Convert to OpenCV Mat
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

        // Apply histogram equalization for better contrast
        Imgproc.equalizeHist(gray, gray)

        // Use Gaussian blur for denoising (available in all OpenCV versions)
        val denoised = Mat()
        Imgproc.GaussianBlur(gray, denoised, Size(5.0, 5.0), 0.0)

        // Sharpen image
        val sharpened = Mat()
        val kernel = Mat(3, 3, CvType.CV_32F)
        kernel.put(0, 0,
            0.0, -1.0, 0.0,
            -1.0, 5.0, -1.0,
            0.0, -1.0, 0.0
        )
        Imgproc.filter2D(denoised, sharpened, -1, kernel)

        // Convert back to Bitmap
        val result = Bitmap.createBitmap(
            sharpened.cols(),
            sharpened.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(sharpened, result)

        // Release OpenCV resources
        mat.release()
        gray.release()
        denoised.release()
        sharpened.release()
        kernel.release()

        return result
    }

    /**
     * Assess fingerprint image quality (0-100 score)
     */
    private fun assessQuality(bitmap: Bitmap): Float {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

        var totalScore = 0f
        var scoreCount = 0

        // 1. Sharpness (Laplacian variance)
        val laplacian = Mat()
        Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        Core.meanStdDev(laplacian, mean, stddev)
        val sharpness = (stddev.get(0, 0)[0] / 50.0).coerceIn(0.0, 1.0) // Normalize
        totalScore += sharpness.toFloat() * 100
        scoreCount++

        // 2. Contrast (standard deviation of intensity)
        Core.meanStdDev(gray, mean, stddev)
        val contrast = (stddev.get(0, 0)[0] / 128.0).coerceIn(0.0, 1.0) // Normalize
        totalScore += contrast.toFloat() * 100
        scoreCount++

        // 3. Brightness check (mean should be in middle range)
        val brightness = mean.get(0, 0)[0]
        val brightnessScore = if (brightness in 60.0..180.0) {
            1.0 - abs(brightness - 120.0) / 120.0
        } else {
            0.0
        }
        totalScore += brightnessScore.toFloat() * 100
        scoreCount++

        // Cleanup
        mat.release()
        gray.release()
        laplacian.release()
        mean.release()
        stddev.release()

        return totalScore / scoreCount
    }
}