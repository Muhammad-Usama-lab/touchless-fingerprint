package com.biometrics

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.biometrics.contract.BiometricsContract
import com.biometrics.viewmodel.BiometricsSharedViewModel

public class BiometricsActivity : AppCompatActivity() {

    private val sharedViewModel: BiometricsSharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bsdk_activity_biometrics)

        // Pass token to ViewModel
        sharedViewModel.token = intent.getStringExtra(BiometricsContract.EXTRA_TOKEN)

        // Observe result from ViewModel
        sharedViewModel.result.observe(this) { result ->
            val resultIntent = Intent().apply {
                putExtra(BiometricsContract.EXTRA_RESULT, result)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}