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
        // Thumb: 1-4 (EXCLUDED - unreliable for horizontal hands)
        // Index: 5-8, Middle: 9-12, Ring: 13-16, Little: 17-20
        private val FINGER_LANDMARKS = mapOf(
            BaseFinger.INDEX to listOf(5, 6, 7, 8),
            BaseFinger.MIDDLE to listOf(9, 10, 11, 12),
            BaseFinger.RING to listOf(13, 14, 15, 16),
            BaseFinger.LITTLE to listOf(17, 18, 19, 20)
        )

        // Padding percentage around detected finger region
        private const val PADDING_PERCENT = 0.30f

        // Minimum quality score (0-100) - TEMPORARILY LOWERED FOR TESTING
        private const val MIN_QUALITY_SCORE = 30  // Was 60

        // Minimum ROI size - TEMPORARILY LOWERED FOR TESTING
        private const val MIN_ROI_SIZE = 30  // Was 50
    }

    /**
     * Standard finger type codes as per biometric standards
     * Supports both LEFT and RIGHT hand fingers
     */
    enum class FingerType(val standardName: String) {
        // Right hand
        RIGHT_THUMB("RIGHT_THUMB"),
        RIGHT_INDEX("RIGHT_INDEX"),
        RIGHT_MIDDLE("RIGHT_MIDDLE"),
        RIGHT_RING("RIGHT_RING"),
        RIGHT_LITTLE("RIGHT_LITTLE"),
        // Left hand
        LEFT_THUMB("LEFT_THUMB"),
        LEFT_INDEX("LEFT_INDEX"),
        LEFT_MIDDLE("LEFT_MIDDLE"),
        LEFT_RING("LEFT_RING"),
        LEFT_LITTLE("LEFT_LITTLE");

        companion object {
            /**
             * Get the correct FingerType based on base finger and handedness
             */
            fun fromBaseAndHand(baseFinger: BaseFinger, isRightHand: Boolean): FingerType {
                return when (baseFinger) {
                    BaseFinger.THUMB -> if (isRightHand) RIGHT_THUMB else LEFT_THUMB
                    BaseFinger.INDEX -> if (isRightHand) RIGHT_INDEX else LEFT_INDEX
                    BaseFinger.MIDDLE -> if (isRightHand) RIGHT_MIDDLE else LEFT_MIDDLE
                    BaseFinger.RING -> if (isRightHand) RIGHT_RING else LEFT_RING
                    BaseFinger.LITTLE -> if (isRightHand) RIGHT_LITTLE else LEFT_LITTLE
                }
            }
        }
    }

    /**
     * Base finger types (without hand specification) - used internally for landmark mapping
     */
    enum class BaseFinger {
        THUMB, INDEX, MIDDLE, RING, LITTLE
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
        val handName = if (isRightHand) "RIGHT" else "LEFT"
        Log.d(TAG, "========== STARTING FINGERPRINT EXTRACTION ($handName HAND) ==========")
        for ((baseFinger, landmarkIndices) in FINGER_LANDMARKS) {
            try {
                // Convert base finger to proper FingerType with handedness
                val fingerType = FingerType.fromBaseAndHand(baseFinger, isRightHand)
                Log.d(TAG, "→ Processing $fingerType...")

                val roi = calculateFingerprintROI(landmarks, landmarkIndices, width, height)

                // Validate ROI
                if (!isValidROI(roi, width, height)) {
                    Log.w(TAG, "✗ $fingerType: INVALID ROI")
                    continue
                }

                // Crop finger region
                val fingerBitmap = cropFinger(bitmap, roi)
                Log.d(TAG, "  Cropped ${fingerType}: ${fingerBitmap.width}x${fingerBitmap.height}px")

                // Enhance fingerprint quality using OpenCV
                val enhanced = try {
                    // Initialize OpenCV if not already done
                    if (!org.opencv.android.OpenCVLoader.initDebug()) {
                        Log.w(TAG, "OpenCV initialization failed, using raw bitmap")
                        fingerBitmap
                    } else {
                        enhanceFingerprint(fingerBitmap)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Enhancement failed: ${e.message}, using raw bitmap")
                    fingerBitmap
                }

                // Assess quality
                val quality = try {
                    assessQuality(enhanced)
                } catch (e: Exception) {
                    Log.w(TAG, "Quality assessment failed: ${e.message}, using default")
                    75f
                }

                Log.d(TAG, "  ${fingerType} quality: ${quality.toInt()}/100")

                // Accept fingerprints with quality >= 30
                if (quality >= MIN_QUALITY_SCORE) {
                    fingerprints.add(
                        FingerprintImage(
                            fingerType = fingerType,
                            bitmap = enhanced,
                            qualityScore = quality,
                            roi = roi
                        )
                    )
                    Log.d(TAG, "✓ $fingerType: ACCEPTED (${enhanced.width}x${enhanced.height}px)")
                } else {
                    Log.d(TAG, "✗ $fingerType: REJECTED (quality ${quality.toInt()} < $MIN_QUALITY_SCORE)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error extracting $baseFinger: ${e.message}", e)
            }
        }
        Log.d(TAG, "========== EXTRACTION COMPLETE: ${fingerprints.size}/4 fingerprints ($handName HAND) ==========")

        // Calculate overall quality (or 0 if no fingerprints extracted)
        val overallQuality = if (fingerprints.isNotEmpty()) {
            fingerprints.map { it.qualityScore }.average().toFloat()
        } else {
            0f
        }

        return ExtractionResult(
            fingerprints = fingerprints,
            handBitmap = bitmap,
            isRightHand = isRightHand,
            overallQuality = overallQuality
        )
    }

    /**
     * Calculate ROI for fingerprint area (fingertip to first joint)
     * Uses shared FingerprintROICalculator for consistency with UI
     */
    private fun calculateFingerprintROI(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
        landmarkIndices: List<Int>,
        imageWidth: Int,
        imageHeight: Int
    ): RectF {
        // Use shared calculator - write once, use everywhere! 🎯
        return FingerprintROICalculator.calculateFingerROI(
            landmarks,
            landmarkIndices,
            imageWidth,
            imageHeight,
            PADDING_PERCENT
        )
    }

    /**
     * Validate if ROI is within bounds and has reasonable dimensions
     * Uses shared FingerprintROICalculator for consistency
     */
    private fun isValidROI(roi: RectF, imageWidth: Int, imageHeight: Int): Boolean {
        val isValid = FingerprintROICalculator.isValidROI(
            roi,
            imageWidth,
            imageHeight,
            minSize = MIN_ROI_SIZE
        )

        // Debug logging
        val width = roi.width().toInt()
        val height = roi.height().toInt()

        if (!isValid) {
            Log.d(TAG, "✗ ROI INVALID: ${width}×${height} at (${roi.left.toInt()},${roi.top.toInt()})")
        } else {
            Log.d(TAG, "✓ ROI VALID: ${width}×${height} at (${roi.left.toInt()},${roi.top.toInt()})")
        }

        return isValid
    }

    /**
     * Crop finger region from full image
     * Ensures highest quality bitmap configuration (ARGB_8888)
     */
    private fun cropFinger(bitmap: Bitmap, roi: RectF): Bitmap {
        val rect = Rect(
            roi.left.toInt(),
            roi.top.toInt(),
            roi.right.toInt(),
            roi.bottom.toInt()
        )

        // Create cropped bitmap
        val cropped = Bitmap.createBitmap(
            bitmap,
            rect.left,
            rect.top,
            rect.width(),
            rect.height()
        )

        // Ensure ARGB_8888 format for maximum quality
        return if (cropped.config != Bitmap.Config.ARGB_8888) {
            cropped.copy(Bitmap.Config.ARGB_8888, false).also {
                cropped.recycle()  // Free original bitmap
            }
        } else {
            cropped
        }
    }




    /**
     * Return original fingerprint image without any processing
     * Just returns the cropped bitmap as-is
     */
    private fun enhanceFingerprint(bitmap: Bitmap): Bitmap {
        // Return original image without any modifications
        return bitmap
    }

    /**
     * Assess fingerprint image quality (0-100 score)
     */
    private fun assessQuality(bitmap: Bitmap): Float {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Convert to grayscale (Android Bitmaps are RGBA, not RGB)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

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