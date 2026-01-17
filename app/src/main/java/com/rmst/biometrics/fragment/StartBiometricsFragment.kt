package com.rmst.biometrics.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import com.biometrics.Biometrics
import com.biometrics.BiometricsLauncher
import com.biometrics.model.BiometricsResult
import com.biometrics.model.ProcessResponse
import com.rmst.biometrics.R

class StartBiometricsFragment : Fragment() {

    private lateinit var biometricsLauncher: BiometricsLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricsLauncher = Biometrics.register(this) { result ->
            when (result) {
                is BiometricsResult.Success -> {
                    // SDK returned successfully with processed fingerprints
                    val message = "Biometrics Success! Batch ID: ${result.batchId}, Files: ${result.processedFiles.size}"
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

                    // Create ProcessResponse from the result to pass to ResultFragment
                    val processResponse = ProcessResponse(
                        success = true,
                        message = "Fingerprints processed successfully",
                        processed = result.processedFiles,
                        batch_id = result.batchId,
                        parameters = result.parameters
                    )

                    // Navigate to ResultFragment to display the results
                    val bundle = bundleOf(ResultFragment.ARG_PROCESS_RESPONSE to processResponse)
                    findNavController().navigate(R.id.action_start_biometrics_to_result, bundle)
                }
                is BiometricsResult.Error -> {
                    val message = "Biometrics Error: ${result.message}"
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
                is BiometricsResult.Cancelled -> {
                    Toast.makeText(requireContext(), "Biometrics cancelled by user", Toast.LENGTH_SHORT).show()
                }
                null -> {
                    Toast.makeText(requireContext(), "Biometrics returned null result", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
            // Replace with a real token for testing
            biometricsLauncher.launch("7b6b6da44c9b4adaa8a73dcca5667b3d92e44856b5e9340cdaa120a792f36543")
        }
    }
}
