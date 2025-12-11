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
     * Default padding percentage around detected finger region
     */
    const val DEFAULT_PADDING_PERCENT = 0.30f

    /**
     * Calculate ROI for a finger using perpendicular vector approach.
     * Creates a properly oriented rectangle around the finger tip area.
     *
     * @param landmarks All hand landmarks from MediaPipe
     * @param landmarkIndices Indices for the specific finger (e.g., [1,2,3,4] for thumb)
     * @param imageWidth Width of the source image in pixels
     * @param imageHeight Height of the source image in pixels
     * @param paddingPercent Padding to add around the ROI (default: 30%)
     * @return RectF representing the finger ROI in pixel coordinates
     */
    fun calculateFingerROI(
        landmarks: List<NormalizedLandmark>,
        landmarkIndices: List<Int>,
        imageWidth: Int,
        imageHeight: Int,
        paddingPercent: Float = DEFAULT_PADDING_PERCENT
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

        // Calculate finger direction vector (from DIP to tip)
        val dx = tip.x - dip.x
        val dy = tip.y - dip.y
        val length = sqrt(dx * dx + dy * dy)

        // Calculate perpendicular vector (across the finger width)
        // This creates the "width" dimension of our rectangle
        val perpX = -dy / length
        val perpY = dx / length

        // Estimate finger width as 65% of the segment length
        // This ratio works well for typical finger proportions
        val fingerWidth = length * 0.65f

        // Create rectangle from tip to PIP joint (covers fingerprint area)
        val halfWidth = fingerWidth / 2

        // Calculate the four corners by extending perpendicular to finger axis
        val left = min(tip.x + perpX * halfWidth, pip.x + perpX * halfWidth)
        val right = max(tip.x - perpX * halfWidth, pip.x - perpX * halfWidth)
        val top = min(tip.y + perpY * halfWidth, pip.y + perpY * halfWidth)
        val bottom = max(tip.y - perpY * halfWidth, pip.y - perpY * halfWidth)

        // Normalize coordinates to ensure left < right and top < bottom
        // This prevents negative widths/heights when fingers are horizontal
        val normalizedLeft = min(left, right)
        val normalizedRight = max(left, right)
        val normalizedTop = min(top, bottom)
        val normalizedBottom = max(top, bottom)

        // Calculate dimensions from normalized coordinates
        val width = normalizedRight - normalizedLeft
        val height = normalizedBottom - normalizedTop

        // Add padding to expand the rectangle
        val paddingX = width * paddingPercent
        val paddingY = height * paddingPercent

        // Return ROI clamped to image bounds
        val roi = RectF(
            max(0f, normalizedLeft - paddingX),
            max(0f, normalizedTop - paddingY),
            min(imageWidth.toFloat(), normalizedRight + paddingX),
            min(imageHeight.toFloat(), normalizedBottom + paddingY)
        )

        // Debug log - track ROI calculation
        android.util.Log.d(
            "ROICalculator",
            "✓ Calculated ROI: ${roi.width().toInt()}x${roi.height().toInt()}px | " +
            "FingerLength: ${length.toInt()}px | Image: ${imageWidth}x${imageHeight}"
        )

        return roi
    }

    /**
     * Validate if an ROI meets minimum quality requirements
     *
     * @param roi The region of interest to validate
     * @param imageWidth Width of the source image
     * @param imageHeight Height of the source image
     * @param minSize Minimum width/height in pixels (default: 30)
     * @param minAspectRatio Minimum acceptable aspect ratio (default: 0.3)
     * @param maxAspectRatio Maximum acceptable aspect ratio (default: 3.0)
     * @return true if ROI is valid, false otherwise
     */
    fun isValidROI(
        roi: RectF,
        imageWidth: Int,
        imageHeight: Int,
        minSize: Int = 15,  // Relaxed from 30 to allow horizontal hand ROIs
        minAspectRatio: Float = 0.3f,
        maxAspectRatio: Float = 8.0f  // Relaxed from 3.0 to allow horizontal hand ROIs
    ): Boolean {
        val width = roi.width()
        val height = roi.height()

        // Check if ROI is within image bounds
        if (roi.left < 0 || roi.top < 0 || roi.right > imageWidth || roi.bottom > imageHeight) {
            return false
        }

        // Check minimum size
        if (width < minSize || height < minSize) {
            return false
        }

        // Check aspect ratio (fingerprint should be roughly rectangular)
        val aspectRatio = width / height
        if (aspectRatio < minAspectRatio || aspectRatio > maxAspectRatio) {
            return false
        }

        return true
    }
}
