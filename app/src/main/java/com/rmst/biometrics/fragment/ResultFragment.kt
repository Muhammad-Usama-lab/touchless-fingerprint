package com.rmst.biometrics.fragment

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.rmst.biometrics.R
import com.rmst.biometrics.adapter.ProcessedFingersAdapter
import com.rmst.biometrics.databinding.FragmentResultBinding
import com.rmst.biometrics.model.ProcessedFile
import com.rmst.biometrics.model.ProcessResponse

/**
 * Fragment to display processed fingerprint results with download options
 */
class ResultFragment : Fragment() {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProcessedFingersAdapter
    private var processResponse: ProcessResponse? = null

    companion object {
        const val ARG_PROCESS_RESPONSE = "process_response"

        fun newInstance(response: ProcessResponse): ResultFragment {
            return ResultFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PROCESS_RESPONSE, response)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processResponse = arguments?.getParcelable(ARG_PROCESS_RESPONSE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        displayResults()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            navigateBack()
        }
    }

    private fun setupRecyclerView() {
        adapter = ProcessedFingersAdapter(
            onDownloadPng = { file -> downloadFile(file.processed_png, file.getPngFilename()) },
            onDownloadWsq = { file -> downloadFile(file.processed_wsq, file.getWsqFilename()) }
        )

        binding.fingerprintsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ResultFragment.adapter
        }
    }

    private fun setupButtons() {
        binding.doneButton.setOnClickListener {
            navigateBack()
        }
    }

    private fun displayResults() {
        val response = processResponse ?: return

        // Update success message
        val count = response.processed.size
        binding.successMessage.text = "$count fingerprint image${if (count > 1) "s" else ""}"
        binding.batchIdText.text = response.batch_id

        // Submit list to adapter
        adapter.submitList(response.processed)
    }

    private fun downloadFile(url: String, filename: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(filename)
                .setDescription("Downloading fingerprint file")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(
                requireContext(),
                "Downloading $filename...",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Download failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun navigateBack() {
        // Pop back to start fragment
        findNavController().popBackStack(R.id.start_biometrics_fragment, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
