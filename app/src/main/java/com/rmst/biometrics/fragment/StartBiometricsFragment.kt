package com.rmst.biometrics.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import android.content.Intent
import com.biometrics.BiometricsActivity
import com.rmst.biometrics.R
import androidx.navigation.Navigation

class StartBiometricsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_start_biometrics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val startBiometricsButton: Button = view.findViewById(R.id.start_biometrics_button)
        startBiometricsButton.setOnClickListener {
            Navigation.findNavController(view).navigate(R.id.action_start_biometrics_to_camera)
        }

        val launchSdkButton: Button = view.findViewById(R.id.launch_sdk_button)
        launchSdkButton.setOnClickListener {
            val intent = Intent(requireActivity(), BiometricsActivity::class.java)
            intent.putExtra("auth_token", "7b6b6da44c9b4adaa8a73dcca5667b3d92e44856b5e9340cdaa120a792f36543") // Replace with a real token for testing
            startActivity(intent)
        }
    }
}