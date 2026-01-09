package com.biometrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import com.biometrics.utils.FingerprintExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: HandLandmarkerResult? = null
    private var previousResults: HandLandmarkerResult? = null

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // Fingerprint detection
    private val fingerprintExtractor = FingerprintExtractor()
    private var fingerprintROIs = mutableMapOf<FingerprintExtractor.FingerType, RectF>()
    private var fingerQualityScores = mutableMapOf<FingerprintExtractor.FingerType, Float>()

    private var isCapturing = false

    // Focus state - must be focused before capture
    private var isFocused = false
    private var isFocusing = false

    // Callback for when stable and ready to capture - triggers INSTANTLY when conditions met
    var onReadyToCapture: (() -> Unit)? = null

    // Called by CameraFragment to update focus state
    fun setFocusState(focused: Boolean, focusing: Boolean) {
        isFocused = focused
        isFocusing = focusing
    }

    // Guide rectangle for hand placement (rounded corners)
    private val guidePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
        isAntiAlias = true
    }

    private val guideRect = RectF()
    private val cornerRadius = 40f  // Rounded corners

    // Distance level (0.0 = too far, 0.5 = perfect, 1.0 = too close)
    private var distanceLevel = 0f

    // Crosshair paint
    private val crosshairPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
        isAntiAlias = true
    }

    // Distance indicator paints
    private val distanceBarPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val distanceTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // Status text paint
    private val statusTextPaint = Paint().apply {
        color = Color.parseColor("#FFEB3B")  // Yellow
        textSize = 36f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Hand position status
    private var handPositionStatus: HandPositionStatus = HandPositionStatus.NO_HAND

    enum class HandPositionStatus(val message: String, val color: Int) {
        NO_HAND("Place your hand in the box", Color.WHITE),
        TOO_FAR("Move closer", Color.RED),
        TOO_CLOSE("Move back", Color.RED),
        OUTSIDE_BOX("Place hand in box", Color.YELLOW),
        NOT_STABLE("Stay still", Color.YELLOW),
        FOCUSING("Focusing...", Color.CYAN),
        PERFECT("Taking Picture", Color.parseColor("#4CAF50"))  // Green
    }

    fun clear() {
        results = null
        previousResults = null
        invalidate()
        fingerprintROIs.clear()
        fingerQualityScores.clear()
        isCapturing = false
    }

    private fun checkStability(): Boolean {
        if (results == null || previousResults == null) return false

        val currentLandmarks = results!!.landmarks()
        val prevLandmarks = previousResults!!.landmarks()

        if (currentLandmarks.isEmpty() || prevLandmarks.isEmpty()) return false
        if (currentLandmarks.size != prevLandmarks.size) return false

        val currentHand = currentLandmarks[0]
        val prevHand = prevLandmarks[0]

        // Calculate movement between frames
        var totalDistance = 0f
        for (i in currentHand.indices) {
            val dx = currentHand[i].x() - prevHand[i].x()
            val dy = currentHand[i].y() - prevHand[i].y()
            totalDistance += sqrt(dx * dx + dy * dy)
        }

        val averageDistance = totalDistance / currentHand.size
        return averageDistance < 0.003f  // Stability threshold (stricter: was 0.005)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // Draw the clean UI (like reference image)
        drawGuideBox(canvas)           // Rounded rectangle guide
        drawDistanceIndicator(canvas)  // TOO FAR / TOO CLOSE scale on left
        drawCrosshair(canvas)          // Crosshair in center
        drawStatusText(canvas)         // "Taking Picture" or status message

        // Calculate ROIs in background (needed for capture) but don't draw them
        results?.let { handLandmarkerResult ->
            // Update hand position status
            if (handLandmarkerResult.landmarks().isNotEmpty()) {
                val landmarks = handLandmarkerResult.landmarks()[0]
                handPositionStatus = checkHandPositionStatus(landmarks)
                updateDistanceLevel(landmarks)
            } else {
                handPositionStatus = HandPositionStatus.NO_HAND
                distanceLevel = 0f
            }
        }
    }

    /**
     * Draw rounded rectangle guide box (like reference UI)
     */
    private fun drawGuideBox(canvas: Canvas) {
        // Calculate guide box - positioned to right side to leave room for distance indicator
        val guideWidth = width * 0.75f
        val guideHeight = height * 0.55f
        val left = width * 0.20f  // Offset to right to make room for distance indicator
        val top = height * 0.25f

        guideRect.set(left, top, left + guideWidth, top + guideHeight)

        // Draw rounded rectangle with white border
        guidePaint.color = Color.WHITE
        guidePaint.strokeWidth = 3f
        canvas.drawRoundRect(guideRect, cornerRadius, cornerRadius, guidePaint)
    }

    /**
     * Draw distance indicator on left side (TOO FAR at top, TOO CLOSE at bottom)
     */
    private fun drawDistanceIndicator(canvas: Canvas) {
        val indicatorLeft = width * 0.03f
        val indicatorWidth = width * 0.08f
        val indicatorTop = guideRect.top + 40f
        val indicatorBottom = guideRect.bottom - 40f
        val indicatorHeight = indicatorBottom - indicatorTop

        // Draw "TOO FAR" text at top
        distanceTextPaint.textSize = 20f
        canvas.drawText("TOO", indicatorLeft + indicatorWidth / 2, indicatorTop - 25, distanceTextPaint)
        canvas.drawText("FAR", indicatorLeft + indicatorWidth / 2, indicatorTop - 5, distanceTextPaint)

        // Draw "TOO CLOSE" text at bottom
        canvas.drawText("TOO", indicatorLeft + indicatorWidth / 2, indicatorBottom + 20, distanceTextPaint)
        canvas.drawText("CLOSE", indicatorLeft + indicatorWidth / 2, indicatorBottom + 42, distanceTextPaint)

        // Draw scale bars (like the reference image)
        val numBars = 10
        val barHeight = indicatorHeight / numBars
        val barSpacing = 4f

        for (i in 0 until numBars) {
            val barTop = indicatorTop + i * barHeight + barSpacing / 2
            val barBottom = barTop + barHeight - barSpacing
            val barRect = RectF(indicatorLeft, barTop, indicatorLeft + indicatorWidth, barBottom)

            // Calculate if this bar should be highlighted based on distance level
            // distanceLevel: 0 = too far (top), 0.5 = perfect (middle), 1 = too close (bottom)
            val barPosition = i.toFloat() / numBars

            // Determine bar color based on distance
            val isActive = when {
                distanceLevel < 0.3f -> barPosition < 0.3f  // Too far - light up top bars
                distanceLevel > 0.7f -> barPosition > 0.7f  // Too close - light up bottom bars
                else -> barPosition in 0.3f..0.7f  // Good - light up middle bars
            }

            if (isActive && results != null && results!!.landmarks().isNotEmpty()) {
                // Active bar - green for good, red for bad
                distanceBarPaint.color = when {
                    distanceLevel in 0.3f..0.7f -> Color.parseColor("#4CAF50")  // Green - good
                    else -> Color.RED  // Red - too far or too close
                }
            } else {
                // Inactive bar - dark gray
                distanceBarPaint.color = Color.parseColor("#333333")
            }

            canvas.drawRoundRect(barRect, 4f, 4f, distanceBarPaint)
        }

        // Draw arrow indicator showing current position
        if (results != null && results!!.landmarks().isNotEmpty()) {
            val arrowY = indicatorTop + distanceLevel * indicatorHeight
            val arrowPaint = Paint().apply {
                color = Color.parseColor("#4CAF50")  // Green arrow
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            // Draw triangle arrow pointing right
            val path = android.graphics.Path()
            path.moveTo(indicatorLeft + indicatorWidth + 5, arrowY)
            path.lineTo(indicatorLeft + indicatorWidth + 20, arrowY - 10)
            path.lineTo(indicatorLeft + indicatorWidth + 20, arrowY + 10)
            path.close()
            canvas.drawPath(path, arrowPaint)
        }
    }

    /**
     * Draw crosshair/target in center of guide box
     */
    private fun drawCrosshair(canvas: Canvas) {
        val centerX = guideRect.centerX()
        val centerY = guideRect.centerY()
        val crosshairSize = 30f
        val circleRadius = 20f

        crosshairPaint.color = Color.WHITE
        crosshairPaint.strokeWidth = 2f

        // Draw circle
        canvas.drawCircle(centerX, centerY, circleRadius, crosshairPaint)

        // Draw crosshair lines
        canvas.drawLine(centerX - crosshairSize, centerY, centerX - circleRadius - 5, centerY, crosshairPaint)
        canvas.drawLine(centerX + circleRadius + 5, centerY, centerX + crosshairSize, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - crosshairSize, centerX, centerY - circleRadius - 5, crosshairPaint)
        canvas.drawLine(centerX, centerY + circleRadius + 5, centerX, centerY + crosshairSize, crosshairPaint)
    }

    /**
     * Draw status text at top (like "Taking Picture")
     */
    private fun drawStatusText(canvas: Canvas) {
        val statusY = guideRect.top - 30f

        // Set color based on status
        statusTextPaint.color = when (handPositionStatus) {
            HandPositionStatus.PERFECT -> Color.parseColor("#4CAF50")  // Green
            HandPositionStatus.NO_HAND -> Color.WHITE
            else -> Color.parseColor("#FFEB3B")  // Yellow for warnings
        }

        canvas.drawText(handPositionStatus.message, guideRect.centerX(), statusY, statusTextPaint)
    }

    /**
     * Update distance level based on finger width (for distance indicator)
     */
    private fun updateDistanceLevel(landmarks: List<NormalizedLandmark>) {
        // Calculate average finger width to determine distance
        val fingerWidths = mutableListOf<Float>()
        val fingers = listOf(
            Pair(7, 6), Pair(11, 10), Pair(15, 14), Pair(19, 18)
        )

        for ((pipIndex, dipIndex) in fingers) {
            val width = calculateFingerWidth(landmarks, pipIndex, dipIndex)
            if (width > 0) fingerWidths.add(width)
        }

        if (fingerWidths.isEmpty()) {
            distanceLevel = 0f
            return
        }

        val avgWidth = fingerWidths.average().toFloat()

        // Map finger width to distance level
        // minFingerWidth (70) = too far (0.0)
        // maxFingerWidth (150) = too close (1.0)
        // perfect (110) = 0.5
        val minWidth = 40f
        val maxWidth = 150f
        distanceLevel = ((avgWidth - minWidth) / (maxWidth - minWidth)).coerceIn(0f, 1f)
    }


    private fun checkHandPositionStatus(landmarks: List<NormalizedLandmark>): HandPositionStatus {
        // 1. Check distance to camera (PRIORITY: must be close enough for fingerprints)
        val distanceStatus = checkCameraDistance(landmarks)
        if (distanceStatus != null) {
            return distanceStatus
        }

        // 2. REMOVED: Finger spacing check (user doesn't want this constraint)
        // Users can have fingers naturally positioned without strict spacing requirements

        // 3. Check if hand is inside guide box
        if (!isHandInsideGuideBox(landmarks)) {
            return HandPositionStatus.OUTSIDE_BOX
        }

        // 4. Check stability (must be stable to capture good fingerprints)
        if (!checkStability()) {
            return HandPositionStatus.NOT_STABLE
        }

        // 5. Check focus state - must be focused before capture
        if (isFocusing || !isFocused) {
            return HandPositionStatus.FOCUSING
        }

        // All checks passed! Ready for capture
        return HandPositionStatus.PERFECT
    }


    private fun isHandInsideGuideBox(landmarks: List<NormalizedLandmark>): Boolean {
        // ONLY check FINGERTIPS, not entire hand!
        // Index (8), Middle (12), Ring (16), Pinky (20)
        val fingertipIndices = listOf(8, 12, 16, 20)

        val fingertipPoints = fingertipIndices.map { index ->
            PointF(
                landmarks[index].x() * imageWidth * scaleFactor,
                landmarks[index].y() * imageHeight * scaleFactor
            )
        }

        // Calculate fingertips bounding box
        val fingertipsLeft = fingertipPoints.minOf { it.x }
        val fingertipsRight = fingertipPoints.maxOf { it.x }
        val fingertipsTop = fingertipPoints.minOf { it.y }
        val fingertipsBottom = fingertipPoints.maxOf { it.y }

        // Check if ALL fingertips are inside guide box (with small margin)
        val margin = 0.05f  // Small margin for edge tolerance
        val isInside =
            fingertipsLeft >= guideRect.left - (guideRect.width() * margin) &&
            fingertipsRight <= guideRect.right + (guideRect.width() * margin) &&
            fingertipsTop >= guideRect.top - (guideRect.height() * margin) &&
            fingertipsBottom <= guideRect.bottom + (guideRect.height() * margin)

        Log.d(TAG, "Fingertips inside box: $isInside (L:$fingertipsLeft R:$fingertipsRight T:$fingertipsTop B:$fingertipsBottom)")
        return isInside
    }

    private fun checkCameraDistance(landmarks: List<NormalizedLandmark>): HandPositionStatus? {
        // Calculate average finger width across all fingers (except thumb)
        val fingerWidths = mutableListOf<Float>()

        // Define finger segments: (PIP, DIP) joints for each finger
        // Index, Middle, Ring, Pinky (skip thumb - it's at different angle)
        val fingers = listOf(
            Pair(7, 6),   // Index: PIP (7) to DIP (6)
            Pair(11, 10), // Middle: PIP (11) to DIP (10)
            Pair(15, 14), // Ring: PIP (15) to DIP (14)
            Pair(19, 18)  // Pinky: PIP (19) to DIP (18)
        )

        for ((pipIndex, dipIndex) in fingers) {
            val width = calculateFingerWidth(landmarks, pipIndex, dipIndex)
            if (width > 0) {  // Only add valid measurements
                fingerWidths.add(width)
            }
        }

        if (fingerWidths.isEmpty()) {
            return null  // Can't determine distance, skip this check
        }

        val avgFingerWidth = fingerWidths.average().toFloat()

        // ROIs must be large enough for good fingerprint quality
        // Increased thresholds to require hand to be CLOSER to camera
        val minROIWidth = 120   // Was 84 - now requires larger ROIs
        val minROIHeight = 80   // Was 60 - now requires larger ROIs
        val hasSmallROIs = fingerprintROIs.values.any { roi ->
            roi.width() < minROIWidth || roi.height() < minROIHeight
        }

        // Minimum finger width threshold - INCREASED to require closer hand
        val minFingerWidth = 70f   // Was 40 - now requires hand to be much closer
        val maxFingerWidth = 150f  // Was 130 - slightly increased for flexibility

        Log.d(TAG, "Camera distance: avgFingerWidth=$avgFingerWidth (need >$minFingerWidth), smallROIs=$hasSmallROIs")

        return when {
            avgFingerWidth < minFingerWidth || hasSmallROIs -> {
                Log.d(TAG, "Hand TOO_FAR: avgWidth=$avgFingerWidth (need >$minFingerWidth) or ROIs too small")
                HandPositionStatus.TOO_FAR
            }
            avgFingerWidth > maxFingerWidth -> {
                Log.d(TAG, "Hand TOO_CLOSE: avgWidth=$avgFingerWidth (max $maxFingerWidth)")
                HandPositionStatus.TOO_CLOSE
            }
            else -> {
                Log.d(TAG, "Distance GOOD: avgWidth=$avgFingerWidth ✓")
                null  // Distance is good, check other conditions
            }
        }
    }

    private fun calculateFingerWidth(
        landmarks: List<NormalizedLandmark>,
        pipIndex: Int,
        dipIndex: Int
    ): Float {
        // Get landmarks in pixel coordinates
        val pip = PointF(
            landmarks[pipIndex].x() * imageWidth,
            landmarks[pipIndex].y() * imageHeight
        )
        val dip = PointF(
            landmarks[dipIndex].x() * imageWidth,
            landmarks[dipIndex].y() * imageHeight
        )

        // Calculate finger direction vector
        val dx = dip.x - pip.x
        val dy = dip.y - pip.y
        val length = sqrt(dx * dx + dy * dy)

        if (length < 1f) return 0f  // Invalid segment

        // Calculate perpendicular vector (across finger width)
        val perpX = -dy / length
        val perpY = dx / length

        // Estimate finger width as 65% of segment length
        // (This is the same ratio used in ROI calculation)
        val fingerWidth = length * 0.65f

        return fingerWidth
    }

    fun resetCapture() {
        isCapturing = false
        Log.d(TAG, "Capture reset")
    }


    // Get current fingerprint ROIs for capture
    fun getFingerprintROIs(): Map<FingerprintExtractor.FingerType, RectF> {
        // Return SCALED ROIs (preview/screenshot coordinates)
        // NOT MediaPipe frame coordinates!
        return fingerprintROIs.mapValues { (_, roi) ->
            RectF(
                roi.left * scaleFactor,
                roi.top * scaleFactor,
                roi.right * scaleFactor,
                roi.bottom * scaleFactor
            )
        }
    }

    fun setResults(
        handLandmarkerResult: HandLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = handLandmarkerResult

        // Calculate fingerprint ROIs
        if (handLandmarkerResult.landmarks().isNotEmpty()) {
            calculateFingerprintROIs(imageHeight, imageWidth)

            // Check hand position status (includes ALL checks: distance, stability, position)
            val landmarks = handLandmarkerResult.landmarks()[0]
            val positionStatus = checkHandPositionStatus(landmarks)

            // INSTANT CAPTURE when 3 conditions are met:
            // 1. Hand is STABLE (not moving)
            // 2. Hand is CLOSE to camera (not too far, not too close)
            // 3. ROI COORDINATES exist (fingerprintROIs not empty)
            val hasROIs = fingerprintROIs.isNotEmpty()

            if (positionStatus == HandPositionStatus.PERFECT && hasROIs && !isCapturing) {
                // All conditions met - CAPTURE IMMEDIATELY!
                isCapturing = true
                Log.d(TAG, "🎯 INSTANT CAPTURE - Stable: ✓ | Close: ✓ | ROIs: ${fingerprintROIs.size}")
                onReadyToCapture?.invoke()
            }

        } else {
            fingerprintROIs.clear()
            fingerQualityScores.clear()
        }

        // Store for next frame comparison (needed for stability check)
        previousResults = results
        results = handLandmarkerResult

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    private fun calculateFingerprintROIs(imageHeight: Int, imageWidth: Int) {
        if (results == null || results!!.landmarks().isEmpty()) return

        val landmarks = results!!.landmarks()[0]
        fingerprintROIs.clear()

        // Detect handedness (RIGHT or LEFT) - MediaPipe detects correctly
        val isRightHand = if (results!!.handedness().isNotEmpty()) {
            results!!.handedness()[0][0].categoryName() == "Right"
        } else {
            true  // Default to right hand if unknown
        }
        val handName = if (isRightHand) "RIGHT" else "LEFT"

        // Define finger landmark indices using BaseFinger
        // Thumb excluded - unreliable for horizontal hands
        val fingerLandmarks = mapOf(
            FingerprintExtractor.BaseFinger.INDEX to listOf(5, 6, 7, 8),
            FingerprintExtractor.BaseFinger.MIDDLE to listOf(9, 10, 11, 12),
            FingerprintExtractor.BaseFinger.RING to listOf(13, 14, 15, 16),
            FingerprintExtractor.BaseFinger.LITTLE to listOf(17, 18, 19, 20)
        )

        // Calculate ROI for each finger with proper naming
        for ((baseFinger, landmarkIndices) in fingerLandmarks) {
            try {
                // Convert to standard FingerType with handedness
                val fingerType = FingerprintExtractor.FingerType.fromBaseAndHand(baseFinger, isRightHand)

                val roi = calculateFingerROI(landmarks, landmarkIndices, imageWidth, imageHeight)
                fingerprintROIs[fingerType] = roi

                // Use hand detection confidence
                val handConfidence = if (results!!.handedness().isNotEmpty()) {
                    results!!.handedness()[0][0].score() * 100f
                } else {
                    50f
                }

                fingerQualityScores[fingerType] = handConfidence

                Log.d(TAG, "Finger: $fingerType ($handName) | Confidence: ${handConfidence.toInt()}%")
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating ROI for $baseFinger", e)
            }
        }
    }

    private fun calculateFingerROI(
        landmarks: List<NormalizedLandmark>,
        landmarkIndices: List<Int>,
        imageWidth: Int,
        imageHeight: Int
    ): RectF {
        // Use shared calculator - write once, use everywhere! 🎯
        return com.biometrics.utils.FingerprintROICalculator.calculateFingerROI(
            landmarks,
            landmarkIndices,
            imageWidth,
            imageHeight,
            paddingPercent = 0.30f
        )
    }

    companion object {
        private const val TAG = "OverlayView"
    }
}