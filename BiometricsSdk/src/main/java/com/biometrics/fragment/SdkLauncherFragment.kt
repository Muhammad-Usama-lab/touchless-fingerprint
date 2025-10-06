package com.biometrics.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.biometrics.R
import com.biometrics.databinding.FragmentSdkLauncherBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class SdkLauncherFragment : Fragment() {

    private var _binding: FragmentSdkLauncherBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSdkLauncherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val token = activity?.intent?.getStringExtra("auth_token")

        if (token.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Error: Token is missing", Toast.LENGTH_LONG).show()
            activity?.finish()
        } else {
            verifyToken(token)
        }
    }

    private fun verifyToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://demo.rmstservices.com/biometrics/verifyToken?token=$token"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val jsonObject = JSONObject(responseBody)
                    if (jsonObject.getBoolean("success")) {
                        withContext(Dispatchers.Main) {
                            findNavController().navigate(R.id.action_sdkLauncherFragment_to_cameraFragment)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Invalid token", Toast.LENGTH_LONG).show()
                            activity?.finish()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error: ${response.message}", Toast.LENGTH_LONG).show()
                        activity?.finish()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error verifying token: ${e.message}", Toast.LENGTH_LONG).show()
                    activity?.finish()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
