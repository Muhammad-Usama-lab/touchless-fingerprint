package com.rmst.biometrics.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Response from POST /process-images API
 */
@Parcelize
data class ProcessResponse(
    val success: Boolean,
    val message: String,
    val processed: List<ProcessedFile>,
    val batch_id: String,
    val parameters: ProcessingParams? = null
) : Parcelable

/**
 * Individual processed fingerprint file info
 */
@Parcelize
data class ProcessedFile(
    val original_name: String,
    val processed_png: String,
    val processed_wsq: String
) : Parcelable {

    /**
     * Extract finger name from original filename
     * e.g., "RIGHT_INDEX.png" -> "Right Index"
     */
    fun getDisplayName(): String {
        return original_name
            .substringBeforeLast(".")
            .replace("_", " ")
            .lowercase()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /**
     * Get filename for PNG download
     */
    fun getPngFilename(): String {
        return original_name.substringBeforeLast(".") + "_processed.png"
    }

    /**
     * Get filename for WSQ download
     */
    fun getWsqFilename(): String {
        return original_name.substringBeforeLast(".") + ".wsq"
    }
}

/**
 * Processing parameters used by the API
 */
@Parcelize
data class ProcessingParams(
    val thickness: Int = 2,
    val margin: Int = 20,
    val crop: Boolean = true,
    val dpi: Int = 500,
    val block_size: Int = 16
) : Parcelable
