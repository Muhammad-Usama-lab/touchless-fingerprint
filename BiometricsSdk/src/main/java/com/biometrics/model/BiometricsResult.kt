package com.biometrics.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Result returned from the Biometrics SDK to the parent app.
 *
 * Usage:
 * ```
 * when (result) {
 *     is BiometricsResult.Success -> {
 *         val batchId = result.batchId
 *         val files = result.processedFiles  // List of PNG + WSQ URLs
 *     }
 *     is BiometricsResult.Error -> {
 *         val errorMessage = result.message
 *     }
 *     is BiometricsResult.Cancelled -> {
 *         // User cancelled
 *     }
 * }
 * ```
 */
public sealed class BiometricsResult : Parcelable {

    /**
     * Fingerprint capture and processing succeeded.
     *
     * @param batchId Unique identifier for this capture batch
     * @param processedFiles List of processed fingerprint files with PNG and WSQ URLs
     * @param parameters Processing parameters used (thickness, margin, dpi, etc.)
     */
    @Parcelize
    data class Success(
        val batchId: String,
        val processedFiles: List<ProcessedFile>,
        val parameters: ProcessingParams? = null
    ) : BiometricsResult()

    @Parcelize
    data class Error(val message: String) : BiometricsResult()

    @Parcelize
    object Cancelled : BiometricsResult()
}
