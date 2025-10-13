package com.biometrics.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.biometrics.R
import com.biometrics.databinding.BsdkFragmentSdkLauncherBinding
import com.biometrics.model.BiometricsResult
import com.biometrics.viewmodel.BiometricsSharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log
import org.json.JSONObject

class SdkLauncherFragment : Fragment() {

    private var _binding: BsdkFragmentSdkLauncherBinding? = null
    private val binding get() = _binding!!
    private val sharedViewModel: BiometricsSharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BsdkFragmentSdkLauncherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val token = sharedViewModel.token

        if (token.isNullOrEmpty()) {
            Log.d("SdkLauncherFragment", "Token is missing or empty.")
            sharedViewModel.postResult(BiometricsResult.Error("Token is missing"))
        } else {
            Log.d("SdkLauncherFragment", "Token received: $token")
            verifyToken(token)
        }
    }

    private fun verifyToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://demo.rmstservices.com/biometrics/verifyToken?token=$token"
                Log.d("SdkLauncherFragment", "Verifying token with URL: $url")
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                val responseBody = response.body?.string()
                Log.d("SdkLauncherFragment", "API Response Code: ${response.code}, Message: ${response.message}")
                Log.d("SdkLauncherFragment", "API Response Body: $responseBody")

                if (response.isSuccessful && responseBody != null) {
                    val jsonObject = JSONObject(responseBody)
                    if (jsonObject.getBoolean("success")) {
                        Log.d("SdkLauncherFragment", "Token verification successful.")
                        withContext(Dispatchers.Main) {
                            findNavController().navigate(R.id.action_sdkLauncherFragment_to_cameraFragment)
                        }
                    } else {
                        sharedViewModel.postResult(BiometricsResult.Error("Invalid token"))
                    }
                } else {
                    sharedViewModel.postResult(BiometricsResult.Error("API Error: ${response.message}"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                sharedViewModel.postResult(BiometricsResult.Error("Exception: ${e.message}"))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}