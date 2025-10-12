package com.biometrics.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

public sealed class BiometricsResult : Parcelable {
    @Parcelize
    data class Success(val transactionId: String) : BiometricsResult()

    @Parcelize
    data class Error(val message: String) : BiometricsResult()

    @Parcelize
    object Cancelled : BiometricsResult()
}
