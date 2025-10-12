package com.biometrics

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.biometrics.contract.BiometricsContract
import com.biometrics.model.BiometricsResult

public class BiometricsLauncher(
    private val registry: ActivityResultRegistry,
    private val onResult: (BiometricsResult) -> Unit
) : DefaultLifecycleObserver {

    private lateinit var launcher: ActivityResultLauncher<String>

    override fun onCreate(owner: LifecycleOwner) {
        launcher = registry.register(
            "com.biometrics.launcher",
            owner,
            BiometricsContract()
        ) { result ->
            onResult(result ?: BiometricsResult.Cancelled) // Handle null result as Cancelled
        }
    }

    fun launch(token: String) {
        launcher.launch(token)
    }
}
