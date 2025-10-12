package com.biometrics.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.biometrics.BiometricsActivity
import com.biometrics.model.BiometricsResult

class BiometricsContract : ActivityResultContract<String, BiometricsResult?>() {

    override fun createIntent(context: Context, input: String): Intent {
        return Intent(context, BiometricsActivity::class.java).apply {
            putExtra(EXTRA_TOKEN, input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): BiometricsResult? {
        return if (resultCode == Activity.RESULT_OK) {
            intent?.getParcelableExtra(EXTRA_RESULT)
        } else {
            null // Or a default error/cancelled result
        }
    }

    companion object {
        const val EXTRA_TOKEN = "com.biometrics.contract.EXTRA_TOKEN"
        const val EXTRA_RESULT = "com.biometrics.contract.EXTRA_RESULT"
    }
}
