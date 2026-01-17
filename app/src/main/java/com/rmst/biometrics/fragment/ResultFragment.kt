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
import com.biometrics.model.ProcessedFile
import com.biometrics.model.ProcessResponse
import com.rmst.biometrics.dialog.GalleryImage
import com.rmst.biometrics.dialog.ImageViewerDialog

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
            onDownloadWsq = { file -> downloadFile(file.processed_wsq, file.getWsqFilename()) },
            onImageClick = { imageUrl, title, imageType, filename ->
                showImageViewer(imageUrl, title, imageType, filename)
            }
        )

        binding.fingerprintsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ResultFragment.adapter
        }
    }

    /**
     * Build a list of all gallery images from processed files
     */
    private fun buildGalleryImages(): List<GalleryImage> {
        val response = processResponse ?: return emptyList()
        val galleryImages = mutableListOf<GalleryImage>()

        response.processed.forEach { file ->
            val displayName = file.getDisplayName()

            // Add captured image if available
            file.captured_png?.let { capturedUrl ->
                galleryImages.add(
                    GalleryImage(
                        imageUrl = capturedUrl,
                        title = displayName,
                        imageType = "Captured",
                        downloadFilename = "${file.original_name.substringBeforeLast(".")}_captured.png"
                    )
                )
            }

            // Add processed image
            galleryImages.add(
                GalleryImage(
                    imageUrl = file.processed_png,
                    title = displayName,
                    imageType = "Processed",
                    downloadFilename = file.getPngFilename()
                )
            )
        }

        return galleryImages
    }

    private fun showImageViewer(imageUrl: String, title: String, imageType: String, filename: String) {
        val allImages = buildGalleryImages()

        // Find the starting position for the clicked image
        val startPosition = allImages.indexOfFirst { it.imageUrl == imageUrl }.coerceAtLeast(0)

        val dialog = ImageViewerDialog.newInstance(
            images = allImages,
            startPosition = startPosition
        )
        dialog.show(childFragmentManager, "ImageViewer")
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
        // Pop back to start fragment (works from both SDK flow and direct camera flow)
        if (!findNavController().popBackStack(R.id.start_biometrics_fragment, false)) {
            // If start fragment not in back stack, just pop current fragment
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
