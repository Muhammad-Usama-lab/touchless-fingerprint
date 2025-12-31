package com.biometrics.utils

import android.graphics.PointF
import android.graphics.RectF
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Shared ROI (Region of Interest) calculator for fingerprint extraction.
 * Used by both OverlayView (UI visualization) and FingerprintExtractor (actual extraction).
 *
 * Write once, use everywhere! 🎯
 */
object FingerprintROICalculator {

    /**
     * Fixed ROI dimensions for consistent fingerprint capture
     * These dimensions are optimized for horizontal hand orientation
     * (fingers pointing left-to-right)
     *
     * CRITICAL: Height must be small enough to isolate individual fingers
     * and avoid capturing adjacent fingers above/below
     */
    private const val ROI_WIDTH = 120   // Width in pixels (capture fingertip area only)
    private const val ROI_HEIGHT = 60   // Height in pixels (reduced to avoid adjacent fingers)

    /**
     * Calculate ROI for a finger using FIXED-SIZE BOUNDING BOX approach.
     * Creates a consistent rectangle centered on the fingertip area.
     *
     * This replaces the old perpendicular vector approach which created
     * horizontal slices instead of proper vertical rectangles.
     *
     * @param landmarks All hand landmarks from MediaPipe
     * @param landmarkIndices Indices for the specific finger (e.g., [1,2,3,4] for thumb)
     * @param imageWidth Width of the source image in pixels
     * @param imageHeight Height of the source image in pixels
     * @param paddingPercent Not used anymore (kept for API compatibility)
     * @return RectF representing the finger ROI in pixel coordinates
     */
    fun calculateFingerROI(
        landmarks: List<NormalizedLandmark>,
        landmarkIndices: List<Int>,
        imageWidth: Int,
        imageHeight: Int,
        paddingPercent: Float = 0f  // Not used, kept for compatibility
    ): RectF {

        // Convert normalized landmarks to pixel coordinates
        val points = landmarkIndices.map { idx ->
            val lm = landmarks[idx]
            PointF(lm.x() * imageWidth, lm.y() * imageHeight)
        }

        // Get the last 3 points: tip (index 3), DIP joint (index 2), PIP joint (index 1)
        val tip = points.last()
        val dip = points[points.size - 2]
        val pip = points[points.size - 3]

        // Calculate CENTER POINT focused on fingertip pad (between TIP and DIP)
        // This is where the clearest fingerprint ridges are visible
        // Weight heavily towards TIP-DIP area, less towards PIP
        val centerX = (tip.x * 0.5f + dip.x * 0.5f)  // 50% tip, 50% DIP (midpoint)
        val centerY = (tip.y * 0.5f + dip.y * 0.5f)  // Focus on fingertip pad area

        // Create FIXED-SIZE rectangle centered on this point
        val halfWidth = ROI_WIDTH / 2f
        val halfHeight = ROI_HEIGHT / 2f

        val left = centerX - halfWidth
        val right = centerX + halfWidth
        val top = centerY - halfHeight
        val bottom = centerY + halfHeight

        // Clamp to image bounds
        val roi = RectF(
            max(0f, left),
            max(0f, top),
            min(imageWidth.toFloat(), right),
            min(imageHeight.toFloat(), bottom)
        )

        // Calculate finger length for logging
        val fingerLength = sqrt((tip.x - pip.x) * (tip.x - pip.x) + (tip.y - pip.y) * (tip.y - pip.y))

        // Debug log
        android.util.Log.d(
            "ROICalculator",
            "✓ Fixed ROI: ${roi.width().toInt()}×${roi.height().toInt()}px | " +
            "Center: (${centerX.toInt()},${centerY.toInt()}) | " +
            "FingerLength: ${fingerLength.toInt()}px | Image: ${imageWidth}×${imageHeight}"
        )

        return roi
    }

    /**
     * Validate if an ROI meets minimum quality requirements
     *
     * With fixed-size ROIs, validation is much simpler - we just check:
     * 1. ROI is within image bounds
     * 2. ROI has reasonable dimensions (close to target size)
     *
     * @param roi The region of interest to validate
     * @param imageWidth Width of the source image
     * @param imageHeight Height of the source image
     * @param minSize Minimum width/height in pixels
     * @param minAspectRatio Not used (kept for compatibility)
     * @param maxAspectRatio Not used (kept for compatibility)
     * @return true if ROI is valid, false otherwise
     */
    fun isValidROI(
        roi: RectF,
        imageWidth: Int,
        imageHeight: Int,
        minSize: Int = 60,  // Minimum size for fingerprint detail
        minAspectRatio: Float = 0.3f,  // Not used anymore
        maxAspectRatio: Float = 3.0f   // Not used anymore
    ): Boolean {
        val width = roi.width()
        val height = roi.height()

        // Check if ROI is within image bounds (allow small margin for edge cases)
        if (roi.left < -5 || roi.top < -5 || roi.right > imageWidth + 5 || roi.bottom > imageHeight + 5) {
            android.util.Log.d("ROICalculator", "ROI out of bounds: $roi (image: ${imageWidth}×${imageHeight})")
            return false
        }

        // Check minimum size - ROI should be at least 60% of target size
        // Target is 120×60, so minimum is 72×36
        if (width < 72 || height < 36) {
            android.util.Log.d("ROICalculator", "ROI too small: ${width.toInt()}×${height.toInt()} (min: 72×36)")
            return false
        }

        // All checks passed
        return true
    }
}
