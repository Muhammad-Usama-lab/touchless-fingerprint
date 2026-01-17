package com.rmst.biometrics.dialog

import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.rmst.biometrics.R
import kotlinx.parcelize.Parcelize

/**
 * Data class representing an image in the gallery
 */
@Parcelize
data class GalleryImage(
    val imageUrl: String,
    val title: String,
    val imageType: String,  // "Captured" or "Processed"
    val downloadFilename: String
) : Parcelable

/**
 * Full-screen image gallery viewer with swipe navigation and download capability
 */
class ImageViewerDialog : DialogFragment() {

    private var images: ArrayList<GalleryImage> = arrayListOf()
    private var startPosition: Int = 0

    private lateinit var viewPager: ViewPager2
    private lateinit var titleText: TextView
    private lateinit var pageIndicatorText: TextView
    private lateinit var imageTypeText: TextView
    private lateinit var swipeHintText: TextView
    private lateinit var downloadButton: ImageButton

    companion object {
        private const val ARG_IMAGES = "images"
        private const val ARG_START_POSITION = "start_position"

        /**
         * Create a new instance with multiple images for gallery mode
         */
        fun newInstance(
            images: List<GalleryImage>,
            startPosition: Int = 0
        ): ImageViewerDialog {
            return ImageViewerDialog().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_IMAGES, ArrayList(images))
                    putInt(ARG_START_POSITION, startPosition)
                }
            }
        }

        /**
         * Create a new instance with a single image
         */
        fun newInstance(
            imageUrl: String,
            title: String,
            imageType: String,
            downloadFilename: String
        ): ImageViewerDialog {
            val image = GalleryImage(imageUrl, title, imageType, downloadFilename)
            return newInstance(listOf(image), 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        arguments?.let {
            images = it.getParcelableArrayList(ARG_IMAGES) ?: arrayListOf()
            startPosition = it.getInt(ARG_START_POSITION, 0)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_image_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        viewPager = view.findViewById(R.id.imageViewPager)
        titleText = view.findViewById(R.id.titleText)
        pageIndicatorText = view.findViewById(R.id.pageIndicatorText)
        imageTypeText = view.findViewById(R.id.imageTypeText)
        swipeHintText = view.findViewById(R.id.swipeHintText)
        downloadButton = view.findViewById(R.id.downloadButton)
        val closeButton: ImageButton = view.findViewById(R.id.closeButton)

        // Setup ViewPager2
        val adapter = GalleryAdapter(images) { dismiss() }
        viewPager.adapter = adapter
        viewPager.setCurrentItem(startPosition, false)

        // Page change listener
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUI(position)
            }
        })

        // Initial UI update
        updateUI(startPosition)

        // Close button
        closeButton.setOnClickListener {
            dismiss()
        }

        // Download button
        downloadButton.setOnClickListener {
            val currentPosition = viewPager.currentItem
            if (currentPosition < images.size) {
                downloadImage(images[currentPosition])
            }
        }

        // Hide swipe hint if only one image
        if (images.size <= 1) {
            swipeHintText.visibility = View.GONE
        }
    }

    private fun updateUI(position: Int) {
        if (position >= images.size) return

        val currentImage = images[position]

        titleText.text = currentImage.title
        pageIndicatorText.text = "${position + 1} / ${images.size}"
        imageTypeText.text = currentImage.imageType

        // Update swipe hint based on position
        swipeHintText.text = when {
            images.size <= 1 -> ""
            position == 0 -> "Swipe left for more →"
            position == images.size - 1 -> "← Swipe right for more"
            else -> "← Swipe to navigate →"
        }
    }

    private fun downloadImage(image: GalleryImage) {
        try {
            val request = DownloadManager.Request(Uri.parse(image.imageUrl))
                .setTitle(image.downloadFilename)
                .setDescription("Downloading ${image.imageType.lowercase()}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, image.downloadFilename)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(
                requireContext(),
                "Downloading ${image.downloadFilename}...",
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

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
    }

    /**
     * Adapter for ViewPager2 gallery
     */
    private inner class GalleryAdapter(
        private val images: List<GalleryImage>,
        private val onImageClick: () -> Unit
    ) : RecyclerView.Adapter<GalleryAdapter.ImageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_gallery_image, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            holder.bind(images[position])
        }

        override fun getItemCount(): Int = images.size

        inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val galleryImage: ImageView = itemView.findViewById(R.id.galleryImage)
            private val loadingProgress: ProgressBar = itemView.findViewById(R.id.loadingProgress)
            private val errorContainer: LinearLayout = itemView.findViewById(R.id.errorContainer)

            fun bind(image: GalleryImage) {
                loadingProgress.visibility = View.VISIBLE
                errorContainer.visibility = View.GONE
                galleryImage.visibility = View.VISIBLE

                Glide.with(itemView.context)
                    .load(image.imageUrl)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            loadingProgress.visibility = View.GONE
                            galleryImage.visibility = View.GONE
                            errorContainer.visibility = View.VISIBLE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            loadingProgress.visibility = View.GONE
                            galleryImage.visibility = View.VISIBLE
                            errorContainer.visibility = View.GONE
                            return false
                        }
                    })
                    .into(galleryImage)

                // Tap to close
                galleryImage.setOnClickListener {
                    onImageClick()
                }
            }
        }
    }
}
