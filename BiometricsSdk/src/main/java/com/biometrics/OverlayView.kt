package com.biometrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.PointF
import android.graphics.RectF
import com.biometrics.utils.FingerprintExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: HandLandmarkerResult? = null
    private var previousResults: HandLandmarkerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private var captureListener: CaptureListener? = null
    private var captureTriggered = false

    // Fingerprint detection
    private val fingerprintExtractor = FingerprintExtractor()
    private var fingerprintROIs = mutableMapOf<FingerprintExtractor.FingerType, RectF>()
    private var fingerQualityScores = mutableMapOf<FingerprintExtractor.FingerType, Float>()


    private var stableFrameCount = 0
    private val REQUIRED_STABLE_FRAMES = 30  // ~1 second at 30fps (reduced from 90 for faster capture)
    private var isCapturing = false
    private var framesSinceLastReset = 0  // Track frames since last reset to be more forgiving


    // Callback for when stable and ready to capture
    var onReadyToCapture: (() -> Unit)? = null


    // Paint for fingerprint ROI boxes
    private val roiPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val roiPaintLowQuality = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
    }

    // Guide rectangle for hand placement
    private val guidePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        pathEffect = DashPathEffect(floatArrayOf(20f, 20f), 0f)
    }

    private val guideRect = RectF()

    // Hand position status
    private var handPositionStatus: HandPositionStatus = HandPositionStatus.NO_HAND

    enum class HandPositionStatus(val message: String, val color: Int) {
        NO_HAND("Place hand in frame", Color.WHITE),
        TOO_FAR("Move hand CLOSER to camera", Color.RED),
        TOO_CLOSE("Move hand BACK from camera", Color.RED),
        OUTSIDE_BOX("Move hand into guide box", Color.YELLOW),
        NOT_STABLE("Hold hand STEADY", Color.YELLOW),
        PERFECT("✓ Hold steady!", Color.GREEN)
        // REMOVED: LOW_QUALITY, TOO_BLURRY (quality check disabled)
    }

    interface CaptureListener {
        fun onCapture(result: HandLandmarkerResult)
    }

    init {
        initPaints()
    }

    fun setCaptureListener(listener: CaptureListener) {
        this.captureListener = listener
    }

    fun clear() {
        results = null
        previousResults = null
        linePaint.reset()
        pointPaint.reset()
        invalidate()
        initPaints()
        captureTriggered = false
        fingerprintROIs.clear()
        fingerQualityScores.clear()
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

    private fun initPaints() {
        linePaint.color =
            ContextCompat.getColor(context!!, R.color.bsdk_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // Draw guide rectangle first (always visible)
        drawGuideRectangle(canvas)

        results?.let { handLandmarkerResult ->
            // Finger names for reference (thumb excluded)
            val fingerNames = mapOf(
                8 to "Index Finger",
                12 to "Middle Finger",
                16 to "Ring Finger",
                20 to "Pinky Finger"
            )

            for ((handIndex, landmark) in handLandmarkerResult.landmarks().withIndex()) {
                // OLD AUTO-CAPTURE TRIGGER REMOVED!
                // Was checking handNearRightEdge - this bypassed quality checks
                // Now using ONLY the quality-based trigger in setResults()

                for (normalizedLandmark in landmark) {
                    canvas.drawPoint(
                        normalizedLandmark.x() * imageWidth * scaleFactor,
                        normalizedLandmark.y() * imageHeight * scaleFactor,
                        pointPaint
                    )
                }

                HandLandmarker.HAND_CONNECTIONS.forEach {
                    canvas.drawLine(
                        landmark.get(it!!.start())
                            .x() * imageWidth * scaleFactor,
                        landmark.get(it.start())
                            .y() * imageHeight * scaleFactor,
                        landmark.get(it.end())
                            .x() * imageWidth * scaleFactor,
                        landmark.get(it.end())
                            .y() * imageHeight * scaleFactor,
                        linePaint
                    )
                }
            }

            // Draw fingerprint ROIs
            drawFingerprintROIs(canvas)

            // Draw quality indicators
            drawQualityIndicators(canvas)

            // Draw hand position guidance
            drawHandGuidance(canvas)

            drawCapturingIndicator(canvas)
        }
    }

    private fun drawGuideRectangle(canvas: Canvas) {
        // Calculate guide box (85% width, 50% height, centered)
        // Enlarged to allow hand to be closer while staying in frame
        val guideWidth = width * 0.85f
        val guideHeight = height * 0.50f
        val left = (width - guideWidth) / 2
        val top = height * 0.20f  // Position higher to center better

        guideRect.set(left, top, left + guideWidth, top + guideHeight)

        // Set color based on hand position status
        guidePaint.color = handPositionStatus.color
        guidePaint.strokeWidth = if (handPositionStatus == HandPositionStatus.PERFECT) 12f else 8f

        // Draw the rectangle
        canvas.drawRect(guideRect, guidePaint)

        // Draw instruction text at top
        val instructionPaint = Paint(textPaint).apply {
            textSize = 36f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }

        val instruction = "Place hand here"
        canvas.drawText(
            instruction,
            width / 2f,
            top - 20,
            instructionPaint
        )
    }

    private fun drawHandGuidance(canvas: Canvas) {
        if (results == null || results!!.landmarks().isEmpty()) {
            handPositionStatus = HandPositionStatus.NO_HAND
            return
        }

        // Get hand position status
        val landmarks = results!!.landmarks()[0]
        handPositionStatus = checkHandPositionStatus(landmarks)

        // Draw status message
        val messagePaint = Paint(textPaint).apply {
            textSize = 44f
            color = handPositionStatus.color
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val y = guideRect.bottom + 80

        // Draw background for message
        val textBounds = android.graphics.Rect()
        messagePaint.getTextBounds(handPositionStatus.message, 0, handPositionStatus.message.length, textBounds)
        canvas.drawRect(
            width / 2f - textBounds.width() / 2 - 20,
            y - textBounds.height() - 10,
            width / 2f + textBounds.width() / 2 + 20,
            y + 10,
            textBackgroundPaint
        )

        // Draw message
        canvas.drawText(
            handPositionStatus.message,
            width / 2f,
            y,
            messagePaint
        )
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

        // 5. DISABLED: Quality check (user doesn't want lighting/quality constraints)
        // Users can capture regardless of lighting conditions
        /*
        val avgQuality = if (fingerQualityScores.isNotEmpty()) {
            fingerQualityScores.values.average()
        } else {
            0.0
        }

        // Require VERY HIGH quality for fingerprint matching (85%+)
        if (avgQuality < 85.0) {
            return HandPositionStatus.LOW_QUALITY
        }
        */

        // All checks passed! Ready for capture
        return HandPositionStatus.PERFECT
    }

    private fun areFingersSpread(landmarks: List<NormalizedLandmark>): Boolean {
        // Calculate spacing between fingertips
        // Index (8), Middle (12), Ring (16), Pinky (20)
        val indexTip = PointF(landmarks[8].x() * imageWidth, landmarks[8].y() * imageHeight)
        val middleTip = PointF(landmarks[12].x() * imageWidth, landmarks[12].y() * imageHeight)
        val ringTip = PointF(landmarks[16].x() * imageWidth, landmarks[16].y() * imageHeight)
        val pinkyTip = PointF(landmarks[20].x() * imageWidth, landmarks[20].y() * imageHeight)

        // Calculate average spacing between adjacent fingers
        val spacing1 = distance(indexTip, middleTip)
        val spacing2 = distance(middleTip, ringTip)
        val spacing3 = distance(ringTip, pinkyTip)
        val avgSpacing = (spacing1 + spacing2 + spacing3) / 3

        // If average spacing > 60px, fingers are spread
        val threshold = 60f
        Log.d(TAG, "Finger spacing: $avgSpacing (threshold: $threshold)")
        return avgSpacing > threshold
    }

    private fun isHandHorizontal(landmarks: List<NormalizedLandmark>): Boolean {
        // Check orientation by comparing wrist to middle fingertip
        val wrist = PointF(landmarks[0].x() * imageWidth, landmarks[0].y() * imageHeight)
        val middleTip = PointF(landmarks[12].x() * imageWidth, landmarks[12].y() * imageHeight)

        val dx = kotlin.math.abs(middleTip.x - wrist.x)
        val dy = kotlin.math.abs(middleTip.y - wrist.y)

        // If horizontal distance > vertical distance, hand is horizontal
        val isHorizontal = dx > dy
        Log.d(TAG, "Hand orientation: dx=$dx, dy=$dy, isHorizontal=$isHorizontal")
        return isHorizontal
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

    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
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

        // Also check if ROIs are large enough (additional validation)
        // ROIs should be at least 84×60 for horizontal fingerprints (60% of 140×100 target)
        val hasSmallROIs = fingerprintROIs.values.any { roi ->
            roi.width() < 84 || roi.height() < 60
        }

        Log.d(TAG, "Camera distance check: avgFingerWidth=$avgFingerWidth, hasSmallROIs=$hasSmallROIs")

        return when {
            avgFingerWidth < 40f || hasSmallROIs -> {
                Log.d(TAG, "Hand TOO_FAR: avgWidth=$avgFingerWidth (min 40)")
                HandPositionStatus.TOO_FAR
            }
            avgFingerWidth > 130f -> {
                Log.d(TAG, "Hand TOO_CLOSE: avgWidth=$avgFingerWidth (max 130)")
                HandPositionStatus.TOO_CLOSE
            }
            else -> {
                Log.d(TAG, "Distance GOOD: avgWidth=$avgFingerWidth")
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
        stableFrameCount = 0
        Log.d(TAG, "Capture reset")
    }


    private fun drawCapturingIndicator(canvas: Canvas) {
        if (stableFrameCount > 0 && !isCapturing) {
            val progress = stableFrameCount.toFloat() / REQUIRED_STABLE_FRAMES
            val text = "Hold steady... ${(progress * 100).toInt()}%"

            val x = width / 2f
            val y = 100f

            // Draw background
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            textPaint.textAlign = Paint.Align.CENTER

            canvas.drawRect(
                x - textBounds.width() / 2 - 20,
                y - textBounds.height() - 10,
                x + textBounds.width() / 2 + 20,
                y + 10,
                textBackgroundPaint
            )

            // Draw text
            val indicatorPaint = Paint(textPaint).apply {
                color = Color.YELLOW
                textSize = 40f
            }
            canvas.drawText(text, x, y, indicatorPaint)

            // Reset text align
            textPaint.textAlign = Paint.Align.LEFT
        }

        if (isCapturing) {
            val text = "✓ CAPTURING..."
            val x = width / 2f
            val y = 100f

            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            textPaint.textAlign = Paint.Align.CENTER

            canvas.drawRect(
                x - textBounds.width() / 2 - 20,
                y - textBounds.height() - 10,
                x + textBounds.width() / 2 + 20,
                y + 10,
                textBackgroundPaint
            )

            val capturingPaint = Paint(textPaint).apply {
                color = Color.GREEN
                textSize = 40f
            }
            canvas.drawText(text, x, y, capturingPaint)

            textPaint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawFingerprintROIs(canvas: Canvas) {
        if (results == null || fingerprintROIs.isEmpty()) return

        for ((fingerType, roi) in fingerprintROIs) {
            // Scale ROI to canvas coordinates
            val scaledRect = RectF(
                roi.left * scaleFactor,
                roi.top * scaleFactor,
                roi.right * scaleFactor,
                roi.bottom * scaleFactor
            )

            // Choose paint based on overall hand status, not just quality
            // GREEN boxes = PERFECT (ready to capture)
            // RED boxes = Problems detected (show user what's wrong)
            val paint = if (handPositionStatus == HandPositionStatus.PERFECT) {
                roiPaint  // Green - all conditions met!
            } else {
                roiPaintLowQuality  // Red dashed - problems detected
            }

            // Make boxes thicker when PERFECT to show confidence
            paint.strokeWidth = if (handPositionStatus == HandPositionStatus.PERFECT) 6f else 4f

            // Draw rectangle
            canvas.drawRect(scaledRect, paint)

            // Draw finger label with quality score
            val quality = fingerQualityScores[fingerType] ?: 0f
            val label = "${fingerType.name} ${quality.toInt()}%"
            val textX = scaledRect.left + 5
            val textY = scaledRect.top - 5

            // Draw background for text
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            canvas.drawRect(
                textX - 2,
                textY - textBounds.height() - 2,
                textX + textBounds.width() + 2,
                textY + 2,
                textBackgroundPaint
            )

            // Draw text with color matching box
            val labelPaint = Paint(textPaint).apply {
                color = if (handPositionStatus == HandPositionStatus.PERFECT) Color.GREEN else Color.RED
            }
            canvas.drawText(label, textX, textY, labelPaint)
        }
    }

    private fun drawQualityIndicators(canvas: Canvas) {
        if (fingerQualityScores.isEmpty()) return

        // Draw overall quality score
        val avgQuality = fingerQualityScores.values.average()
        val qualityText = "Quality: ${avgQuality.toInt()}%"
        val statusText = if (avgQuality >= 85) "EXCELLENT" else if (avgQuality >= 70) "GOOD" else if (avgQuality >= 50) "FAIR" else "POOR"

        Log.d(TAG, "=== Overall Quality: $avgQuality | Status: $statusText ===")

        val x = 20f
        val y = 60f

        // Background
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds("$qualityText - $statusText", 0, "$qualityText - $statusText".length, textBounds)
        canvas.drawRect(
            x - 5,
            y - textBounds.height() - 5,
            x + textBounds.width() + 5,
            y + 5,
            textBackgroundPaint
        )

        // Text with color based on quality
        val qualityPaint = Paint(textPaint).apply {
            color = when {
                avgQuality >= 85 -> Color.GREEN  // Increased from 70
                avgQuality >= 70 -> Color.YELLOW  // Increased from 50
                else -> Color.RED
            }
        }
        canvas.drawText("$qualityText - $statusText", x, y, qualityPaint)

        // Draw individual finger quality
        var yOffset = y + 40f
        for ((finger, quality) in fingerQualityScores) {
            val fingerText = "${finger.name}: ${quality.toInt()}%"
            canvas.drawRect(
                x - 5,
                yOffset - 25,
                x + 150,
                yOffset + 5,
                textBackgroundPaint
            )

            qualityPaint.color = when {
                quality >= 85 -> Color.GREEN  // Increased from 70
                quality >= 70 -> Color.YELLOW  // Increased from 50
                else -> Color.RED
            }
            canvas.drawText(fingerText, x, yOffset, qualityPaint)
            yOffset += 35f
        }
    }

    // Method to check if capture should be triggered
    fun shouldTriggerCapture(): Boolean {
        if (fingerQualityScores.isEmpty()) return false

        // Require at least 4 fingers with good quality
        val goodQualityCount = fingerQualityScores.values.count { it >= 60 }
        val avgQuality = fingerQualityScores.values.average()

        Log.d(TAG, "shouldTriggerCapture: Good fingers: $goodQualityCount/5 | Avg: $avgQuality | Trigger: ${goodQualityCount >= 4 && avgQuality >= 45}")

        return goodQualityCount >= 4 && avgQuality >= 70
    }

    // Get current fingerprint ROIs for capture
    fun getFingerprintROIs(): Map<FingerprintExtractor.FingerType, RectF> {
        return fingerprintROIs.toMap()
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

            // Check if quality is good and hand is stable
            val avgQuality = if (fingerQualityScores.isNotEmpty()) {
                fingerQualityScores.values.average()
            } else {
                0.0
            }

            // Check hand position status (includes ALL checks: distance, stability, quality, position)
            val landmarks = handLandmarkerResult.landmarks()[0]
            val positionStatus = checkHandPositionStatus(landmarks)

            // CRITICAL: Only capture when status is PERFECT!
            // This means ALL conditions are met:
            // ✓ Hand close enough to camera
            // ✓ Fingers together (not spread)
            // ✓ Hand inside guide box
            // ✓ Hand completely stable
            // ✓ Quality score >= 85%
            if (positionStatus == HandPositionStatus.PERFECT && !isCapturing) {
                stableFrameCount++
                Log.d(TAG, "✓ PERFECT! Stable frames: $stableFrameCount/$REQUIRED_STABLE_FRAMES | Quality: $avgQuality")

                if (stableFrameCount >= REQUIRED_STABLE_FRAMES) {
                    // All conditions perfect for sufficient time - CAPTURE NOW!
                    isCapturing = true
                    onReadyToCapture?.invoke()
                    Log.d(TAG, "🎯 CAPTURING - All quality checks passed!")
                }
            } else {
                // Reset if ANY condition fails
                if (stableFrameCount > 0) {
                    Log.d(TAG, "Reset: Status=$positionStatus (need PERFECT)")
                }
                stableFrameCount = 0
            }

        } else {
            fingerprintROIs.clear()
            fingerQualityScores.clear()
            stableFrameCount = 0
        }

        // Store for next frame comparison
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

        // Define finger landmark indices (same as in FingerprintExtractor)
        // Thumb excluded - unreliable for horizontal hands
        val fingerLandmarks = mapOf(
            FingerprintExtractor.FingerType.INDEX to listOf(5, 6, 7, 8),
            FingerprintExtractor.FingerType.MIDDLE to listOf(9, 10, 11, 12),
            FingerprintExtractor.FingerType.RING to listOf(13, 14, 15, 16),
            FingerprintExtractor.FingerType.PINKY to listOf(17, 18, 19, 20)
        )

        // Calculate ROI for each finger
        for ((fingerType, landmarkIndices) in fingerLandmarks) {
            try {
                val roi = calculateFingerROI(landmarks, landmarkIndices, imageWidth, imageHeight)
                fingerprintROIs[fingerType] = roi

                // Use hand detection confidence instead of z-values
                val handConfidence = if (results!!.handedness().isNotEmpty()) {
                    results!!.handedness()[0][0].score() * 100f  // Convert to percentage
                } else {
                    50f  // Default fallback
                }

                fingerQualityScores[fingerType] = handConfidence

                Log.d(TAG, "Finger: $fingerType | Hand Confidence: ${handConfidence}%")
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating ROI for $fingerType", e)
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
        private const val LANDMARK_STROKE_WIDTH = 8F
        private const val TAG = "OverlayView"
    }
}