//package com.biometrics.utils
//
//import android.content.Context
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.graphics.ImageFormat
//import android.graphics.Matrix
//import android.graphics.Rect
//import android.graphics.YuvImage
//import android.util.Log
//import androidx.camera.core.ImageProxy
//import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
//import java.io.ByteArrayOutputStream
//import java.io.File
//import java.io.FileOutputStream
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//
//class FingertipExtractor(private val context: Context) {
//
//    companion object {
//        private const val TAG = "FingertipExtractor"
//
//        // MediaPipe hand landmark indices for fingertips
//        const val THUMB_TIP = 4
//        const val INDEX_TIP = 8
//        const val MIDDLE_TIP = 12
//        const val RING_TIP = 16
//        const val PINKY_TIP = 20
//
//        // Crop size around fingertip (in pixels)
//        private const val CROP_SIZE = 300
//
//        // Minimum confidence to process fingertip
//        private const val MIN_CONFIDENCE = 0.7f
//    }
//
//    /**
//     * Data class to hold extracted fingertip information
//     */
//    data class FingertipImage(
//        val fingerName: String,
//        val bitmap: Bitmap,
//        val filePath: String
//    )
//
//    /**
//     * Extract fingertips from ImageProxy using hand landmarks
//     * This works in the same coordinate space as the landmarks
//     */
//    fun extractFingertips(
//        imageProxy: ImageProxy,
//        handResult: HandLandmarkerResult,
//        isFrontCamera: Boolean
//    ): List<FingertipImage> {
//
//        if (handResult.landmarks().isEmpty()) {
//            return emptyList()
//        }
//
//        val fingertipImages = mutableListOf<FingertipImage>()
//
//        try {
//            // Convert ImageProxy to Bitmap
//            val bitmap = imageProxyToBitmap(imageProxy, isFrontCamera)
//
//            // Get the first detected hand
//            val landmarks = handResult.landmarks().first()
//
//            // Extract each fingertip
//            val fingertips = mapOf(
//                "thumb" to THUMB_TIP,
//                "index" to INDEX_TIP,
//                "middle" to MIDDLE_TIP,
//                "ring" to RING_TIP,
//                "pinky" to PINKY_TIP
//            )
//
//            fingertips.forEach { (name, index) ->
//                val landmark = landmarks[index]
//
//                // Convert normalized coordinates to pixel coordinates
//                val x = (landmark.x() * bitmap.width).toInt()
//                val y = (landmark.y() * bitmap.height).toInt()
//
//                // Crop fingertip region
//                val croppedBitmap = cropFingertip(bitmap, x, y, CROP_SIZE)
//
//                if (croppedBitmap != null) {
//                    // Save to device
//                    val filePath = saveFingertipToDevice(croppedBitmap, name)
//
//                    if (filePath != null) {
//                        fingertipImages.add(FingertipImage(name, croppedBitmap, filePath))
//                        Log.d(TAG, "Saved $name fingertip to: $filePath")
//                    }
//                }
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error extracting fingertips", e)
//        }
//
//        return fingertipImages
//    }
//
//    /**
//     * Convert ImageProxy to Bitmap
//     * Handles rotation and mirroring for front camera
//     */
//    private fun imageProxyToBitmap(imageProxy: ImageProxy, isFrontCamera: Boolean): Bitmap {
//        val yBuffer = imageProxy.planes[0].buffer
//        val uBuffer = imageProxy.planes[1].buffer
//        val vBuffer = imageProxy.planes[2].buffer
//
//        val ySize = yBuffer.remaining()
//        val uSize = uBuffer.remaining()
//        val vSize = vBuffer.remaining()
//
//        val nv21 = ByteArray(ySize + uSize + vSize)
//
//        yBuffer.get(nv21, 0, ySize)
//        vBuffer.get(nv21, ySize, vSize)
//        uBuffer.get(nv21, ySize + vSize, uSize)
//
//        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
//        val out = ByteArrayOutputStream()
//        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
//        val imageBytes = out.toByteArray()
//
//        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
//
//        // Handle rotation
//        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
//        if (rotationDegrees != 0 || isFrontCamera) {
//            val matrix = Matrix()
//            matrix.postRotate(rotationDegrees.toFloat())
//            if (isFrontCamera) {
//                matrix.postScale(-1f, 1f) // Mirror for front camera
//            }
//            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
//        }
//
//        return bitmap
//    }
//
//    /**
//     * Crop a region around the fingertip
//     */
//    private fun cropFingertip(bitmap: Bitmap, centerX: Int, centerY: Int, size: Int): Bitmap? {
//        try {
//            val halfSize = size / 2
//
//            // Calculate crop bounds with boundary checks
//            val left = maxOf(0, centerX - halfSize)
//            val top = maxOf(0, centerY - halfSize)
//            val right = minOf(bitmap.width, centerX + halfSize)
//            val bottom = minOf(bitmap.height, centerY + halfSize)
//
//            val width = right - left
//            val height = bottom - top
//
//            // Ensure we have a valid crop region
//            if (width <= 0 || height <= 0) {
//                Log.w(TAG, "Invalid crop region: width=$width, height=$height")
//                return null
//            }
//
//            return Bitmap.createBitmap(bitmap, left, top, width, height)
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error cropping fingertip", e)
//            return null
//        }
//    }
//
//    /**
//     * Save fingertip bitmap to device storage
//     */
//    private fun saveFingertipToDevice(bitmap: Bitmap, fingerName: String): String? {
//        try {
//            // Create directory for fingertips
//            val fingerprintsDir = File(context.getExternalFilesDir(null), "fingerprints")
//            if (!fingerprintsDir.exists()) {
//                fingerprintsDir.mkdirs()
//            }
//
//            // Create filename with timestamp
//            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
//            val filename = "${fingerName}_${timestamp}.jpg"
//            val file = File(fingerprintsDir, filename)
//
//            // Save bitmap
//            FileOutputStream(file).use { out ->
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
//            }
//
//            return file.absolutePath
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error saving fingertip to device", e)
//            return null
//        }
//    }
//
//    /**
//     * Get all saved fingerprint files
//     */
//    fun getSavedFingerprints(): List<File> {
//        val fingerprintsDir = File(context.getExternalFilesDir(null), "fingerprints")
//        if (!fingerprintsDir.exists()) {
//            return emptyList()
//        }
//        return fingerprintsDir.listFiles()?.toList() ?: emptyList()
//    }
//
//    /**
//     * Clear all saved fingerprints
//     */
//    fun clearSavedFingerprints() {
//        val fingerprintsDir = File(context.getExternalFilesDir(null), "fingerprints")
//        fingerprintsDir.listFiles()?.forEach { it.delete() }
//    }
//}



package com.biometrics.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FingertipExtractor(private val context: Context) {

    companion object {
        private const val TAG = "FingertipExtractor"

        // MediaPipe hand landmark indices for fingertips
        const val THUMB_TIP = 4
        const val INDEX_TIP = 8
        const val MIDDLE_TIP = 12
        const val RING_TIP = 16
        const val PINKY_TIP = 20

        // Crop size around fingertip (in pixels)
        private const val CROP_SIZE = 300

        // Minimum confidence to process fingertip
        private const val MIN_CONFIDENCE = 0.7f
    }

    data class FingertipImage(
        val fingerName: String,
        val bitmap: Bitmap,
        val filePath: String
    )

    /**
     * Extract fingertips from pre-converted Bitmap using hand landmarks
     * Use this when you've already converted ImageProxy to Bitmap
     */
    fun extractFingertipsFromBitmap(
        bitmap: Bitmap,
        handResult: HandLandmarkerResult,
        isFrontCamera: Boolean
    ): List<FingertipImage> {

        if (handResult.landmarks().isEmpty()) {
            return emptyList()
        }

        val fingertipImages = mutableListOf<FingertipImage>()

        try {
            // Get the first detected hand
            val landmarks = handResult.landmarks().first()

            // Extract each fingertip
            val fingertips = mapOf(
                "thumb" to THUMB_TIP,
                "index" to INDEX_TIP,
                "middle" to MIDDLE_TIP,
                "ring" to RING_TIP,
                "pinky" to PINKY_TIP
            )

            fingertips.forEach { (name, index) ->
                val landmark = landmarks[index]

                // Convert normalized coordinates to pixel coordinates
                val x = (landmark.x() * bitmap.width).toInt()
                val y = (landmark.y() * bitmap.height).toInt()

                Log.d(TAG, "Processing $name: x=$x, y=$y (bitmap: ${bitmap.width}x${bitmap.height})")

                // Crop fingertip region
                val croppedBitmap = cropFingertip(bitmap, x, y, CROP_SIZE)

                if (croppedBitmap != null) {
                    // Save to device
                    val filePath = saveFingertipToDevice(croppedBitmap, name)

                    if (filePath != null) {
                        fingertipImages.add(FingertipImage(name, croppedBitmap, filePath))
                        Log.d(TAG, "Saved $name fingertip to: $filePath")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting fingertips from bitmap", e)
        }

        return fingertipImages
    }

    /**
     * Extract fingertips from ImageProxy using hand landmarks
     * This works in the same coordinate space as the landmarks
     */
    fun extractFingertips(
        imageProxy: ImageProxy,
        handResult: HandLandmarkerResult,
        isFrontCamera: Boolean
    ): List<FingertipImage> {

        if (handResult.landmarks().isEmpty()) {
            return emptyList()
        }

        val fingertipImages = mutableListOf<FingertipImage>()

        try {
            // Convert ImageProxy to Bitmap
            val bitmap = imageProxyToBitmap(imageProxy, isFrontCamera)

            // Get the first detected hand
            val landmarks = handResult.landmarks().first()

            // Extract each fingertip
            val fingertips = mapOf(
                "thumb" to THUMB_TIP,
                "index" to INDEX_TIP,
                "middle" to MIDDLE_TIP,
                "ring" to RING_TIP,
                "pinky" to PINKY_TIP
            )

            fingertips.forEach { (name, index) ->
                val landmark = landmarks[index]

                // Convert normalized coordinates to pixel coordinates
                val x = (landmark.x() * bitmap.width).toInt()
                val y = (landmark.y() * bitmap.height).toInt()

                // Crop fingertip region
                val croppedBitmap = cropFingertip(bitmap, x, y, CROP_SIZE)

                if (croppedBitmap != null) {
                    // Save to device
                    val filePath = saveFingertipToDevice(croppedBitmap, name)

                    if (filePath != null) {
                        fingertipImages.add(FingertipImage(name, croppedBitmap, filePath))
                        Log.d(TAG, "Saved $name fingertip to: $filePath")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting fingertips", e)
        }

        return fingertipImages
    }

    /**
     * Convert ImageProxy to Bitmap
     * Handles rotation and mirroring for front camera
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy, isFrontCamera: Boolean): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val imageBytes = out.toByteArray()

        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Handle rotation
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees != 0 || isFrontCamera) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            if (isFrontCamera) {
                matrix.postScale(-1f, 1f) // Mirror for front camera
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        return bitmap
    }

    /**
     * Crop a region around the fingertip
     */
    private fun cropFingertip(bitmap: Bitmap, centerX: Int, centerY: Int, size: Int): Bitmap? {
        try {
            val halfSize = size / 2

            // Calculate crop bounds with boundary checks
            val left = maxOf(0, centerX - halfSize)
            val top = maxOf(0, centerY - halfSize)
            val right = minOf(bitmap.width, centerX + halfSize)
            val bottom = minOf(bitmap.height, centerY + halfSize)

            val width = right - left
            val height = bottom - top

            // Ensure we have a valid crop region
            if (width <= 0 || height <= 0) {
                Log.w(TAG, "Invalid crop region: width=$width, height=$height")
                return null
            }

            return Bitmap.createBitmap(bitmap, left, top, width, height)

        } catch (e: Exception) {
            Log.e(TAG, "Error cropping fingertip", e)
            return null
        }
    }

    /**
     * Save fingertip bitmap to device storage
     */
    private fun saveFingertipToDevice(bitmap: Bitmap, fingerName: String): String? {
        try {
            // Create directory for fingertips
            val fingerprintsDir = File(context.getExternalFilesDir(null), "fingerprints")
            if (!fingerprintsDir.exists()) {
                fingerprintsDir.mkdirs()
            }

            // Create filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "${fingerName}_${timestamp}.jpg"
            val file = File(fingerprintsDir, filename)

            // Save bitmap
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            return file.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Error saving fingertip to device", e)
            return null
        }
    }

    /**
     * Get all saved fingerprint files
     */
    fun getSavedFingerprints(): List<File> {
        val fingerprintsDir = File(context.getExternalFilesDir(null), "fingerprints")
        if (!fingerprintsDir.exists()) {
            return emptyList()
        }
        return fingerprintsDir.listFiles()?.toList() ?: emptyList()
    }

    /**
     * Clear all saved fingerprints
     */
    fun clearSavedFingerprints() {
        val fingerprintsDir = File(context.getExternalFilesDir(null), "fingerprints")
        fingerprintsDir.listFiles()?.forEach { it.delete() }
    }
}