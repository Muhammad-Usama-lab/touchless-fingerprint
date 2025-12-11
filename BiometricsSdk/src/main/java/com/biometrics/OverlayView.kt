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
    private val REQUIRED_STABLE_FRAMES = 15  // ~0.5 seconds at 30fps
    private var isCapturing = false


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
        return averageDistance < 0.005f  // Stability threshold
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
        results?.let { handLandmarkerResult ->
            val fingerNames = mapOf(
                4 to "Thumb",
                8 to "Index Finger",
                12 to "Middle Finger",
                16 to "Ring Finger",
                20 to "Pinky Finger"
            )

            for ((handIndex, landmark) in handLandmarkerResult.landmarks().withIndex()) {
                var handNearRightEdge = false
                for (normalizedLandmark in landmark) {
                    val x = normalizedLandmark.x() * imageWidth * scaleFactor
                    if (x > width * 0.8) {
                        handNearRightEdge = true
                        break
                    }
                }

                var isStable = false
                previousResults?.let { prevResult ->
                    if (prevResult.landmarks().size > handIndex) {
                        val prevLandmark = prevResult.landmarks()[handIndex]
                        var totalDistance = 0f
                        for (i in landmark.indices) {
                            val dx = landmark[i].x() - prevLandmark[i].x()
                            val dy = landmark[i].y() - prevLandmark[i].y()
                            totalDistance += sqrt(dx * dx + dy * dy)
                        }
                        val averageDistance = totalDistance / landmark.size
                        if (averageDistance < 0.008) { // Stability threshold
                            isStable = true
                        }
                    }
                }

                if (handNearRightEdge && isStable) {
                    if (!captureTriggered) {
                        captureListener?.onCapture(handLandmarkerResult)
                        captureTriggered = true
                    }
                }

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


            drawCapturingIndicator(canvas)
        }
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

            // Choose paint based on quality
            val quality = fingerQualityScores[fingerType] ?: 0f
            val paint = if (quality >= 60) roiPaint else roiPaintLowQuality

            // Draw rectangle
            canvas.drawRect(scaledRect, paint)

            // Draw finger label
            val label = fingerType.name
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

            // Draw text
            canvas.drawText(label, textX, textY, textPaint)
        }
    }

    private fun drawQualityIndicators(canvas: Canvas) {
        if (fingerQualityScores.isEmpty()) return

        // Draw overall quality score
        val avgQuality = fingerQualityScores.values.average()
        val qualityText = "Quality: ${avgQuality.toInt()}%"
        val statusText = if (avgQuality >= 70) "GOOD" else if (avgQuality >= 50) "FAIR" else "POOR"

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
                avgQuality >= 70 -> Color.GREEN
                avgQuality >= 50 -> Color.YELLOW
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
                quality >= 70 -> Color.GREEN
                quality >= 50 -> Color.YELLOW
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

            val isGoodQuality = avgQuality >= 70
            val isStable = checkStability()

            if (isGoodQuality && isStable && !isCapturing) {
                stableFrameCount++
                Log.d(TAG, "Stable frames: $stableFrameCount/$REQUIRED_STABLE_FRAMES | Quality: $avgQuality")

                if (stableFrameCount >= REQUIRED_STABLE_FRAMES) {
                    // Hand is stable and quality is good!
                    isCapturing = true
                    onReadyToCapture?.invoke()
                    Log.d(TAG, "✓ READY TO CAPTURE!")
                }
            } else {
                // Reset if quality drops or hand moves
                if (stableFrameCount > 0) {
                    Log.d(TAG, "Reset: Quality=$isGoodQuality, Stable=$isStable")
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
        val fingerLandmarks = mapOf(
            FingerprintExtractor.FingerType.THUMB to listOf(1, 2, 3, 4),
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