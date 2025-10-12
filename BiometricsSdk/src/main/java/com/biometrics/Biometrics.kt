package com.biometrics

import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import com.biometrics.model.BiometricsResult

object Biometrics {

    fun register(
        activity: ComponentActivity,
        onResult: (BiometricsResult) -> Unit
    ): BiometricsLauncher {
        val launcher = BiometricsLauncher(activity.activityResultRegistry, onResult)
        activity.lifecycle.addObserver(launcher)
        return launcher
    }

    fun register(
        fragment: Fragment,
        onResult: (BiometricsResult) -> Unit
    ): BiometricsLauncher {
        val launcher = BiometricsLauncher(fragment.requireActivity().activityResultRegistry, onResult)
        fragment.lifecycle.addObserver(launcher)
        return launcher
    }
}