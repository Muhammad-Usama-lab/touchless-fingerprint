package com.rmst.biometrics.api

import android.graphics.Bitmap
import android.util.Log
import com.rmst.biometrics.model.ProcessResponse
import com.rmst.biometrics.model.ProcessedFile
import com.rmst.biometrics.model.ProcessingParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * API Service for fingerprint image processing
 */
object FingerprintApiService {

    private const val TAG = "FingerprintApiService"
    private const val BASE_URL = "http://demo.rmstservices.com:8001"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Upload fingerprint images for processing
     *
     * @param fingerprints Map of finger name to bitmap (e.g., "RIGHT_INDEX" to Bitmap)
     * @param thickness Ridge thickness: 1=thin, 2=medium, 3=thick
     * @param margin Edge mask margin in pixels
     * @param crop Whether to crop output to fingerprint region
     * @param dpi Output DPI
     * @return ProcessResponse with URLs to processed files
     */
    suspend fun processFingerprints(
        fingerprints: Map<String, Bitmap>,
        thickness: Int = 2,
        margin: Int = 20,
        crop: Boolean = true,
        dpi: Int = 500
    ): Result<ProcessResponse> = withContext(Dispatchers.IO) {
        try {
            if (fingerprints.isEmpty()) {
                return@withContext Result.failure(Exception("No fingerprints to process"))
            }

            Log.d(TAG, "Processing ${fingerprints.size} fingerprints...")

            // Build multipart request body
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)

            // Add each fingerprint image
            fingerprints.forEach { (fingerName, bitmap) ->
                val byteArray = bitmapToByteArray(bitmap)
                val filename = "${fingerName}.png"

                Log.d(TAG, "Adding file: $filename (${byteArray.size} bytes)")

                multipartBuilder.addFormDataPart(
                    "files",
                    filename,
                    byteArray.toRequestBody("image/png".toMediaType())
                )
            }

            val requestBody = multipartBuilder.build()

            // Build URL with query parameters
            val url = "$BASE_URL/process-images?thickness=$thickness&margin=$margin&crop=$crop&dpi=$dpi"
            Log.d(TAG, "Request URL: $url")

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            // Execute request
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "Response code: ${response.code}")
            Log.d(TAG, "Response body: $responseBody")

            if (!response.isSuccessful) {
                val errorMessage = parseErrorMessage(responseBody) ?: "Server error: ${response.code}"
                return@withContext Result.failure(Exception(errorMessage))
            }

            if (responseBody == null) {
                return@withContext Result.failure(Exception("Empty response from server"))
            }

            // Parse response
            val processResponse = parseProcessResponse(responseBody)
            Result.success(processResponse)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing fingerprints", e)
            Result.failure(e)
        }
    }

    /**
     * Check server health
     */
    suspend fun checkHealth(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed", e)
            Result.failure(e)
        }
    }

    /**
     * Convert Bitmap to PNG byte array
     */
    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    /**
     * Parse JSON response to ProcessResponse object
     */
    private fun parseProcessResponse(json: String): ProcessResponse {
        val jsonObject = JSONObject(json)

        val processedArray = jsonObject.getJSONArray("processed")
        val processedFiles = mutableListOf<ProcessedFile>()

        for (i in 0 until processedArray.length()) {
            val fileObj = processedArray.getJSONObject(i)
            processedFiles.add(
                ProcessedFile(
                    original_name = fileObj.getString("original_name"),
                    processed_png = fileObj.getString("processed_png"),
                    processed_wsq = fileObj.getString("processed_wsq")
                )
            )
        }

        var params: ProcessingParams? = null
        if (jsonObject.has("parameters")) {
            val paramsObj = jsonObject.getJSONObject("parameters")
            params = ProcessingParams(
                thickness = paramsObj.optInt("thickness", 2),
                margin = paramsObj.optInt("margin", 20),
                crop = paramsObj.optBoolean("crop", true),
                dpi = paramsObj.optInt("dpi", 500),
                block_size = paramsObj.optInt("block_size", 16)
            )
        }

        return ProcessResponse(
            success = jsonObject.getBoolean("success"),
            message = jsonObject.getString("message"),
            processed = processedFiles,
            batch_id = jsonObject.getString("batch_id"),
            parameters = params
        )
    }

    /**
     * Parse error message from response body
     */
    private fun parseErrorMessage(responseBody: String?): String? {
        return try {
            responseBody?.let {
                val json = JSONObject(it)
                json.optString("detail") ?: json.optString("message")
            }
        } catch (e: Exception) {
            null
        }
    }
}
