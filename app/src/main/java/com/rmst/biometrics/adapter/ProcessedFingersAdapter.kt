package com.rmst.biometrics.adapter

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.button.MaterialButton
import com.rmst.biometrics.R
import com.biometrics.model.ProcessedFile

/**
 * Adapter for displaying processed fingerprint images in a RecyclerView
 * Shows both captured (original) and processed images side by side
 */
class ProcessedFingersAdapter(
    private val onDownloadPng: (ProcessedFile) -> Unit,
    private val onDownloadWsq: (ProcessedFile) -> Unit,
    private val onImageClick: (imageUrl: String, title: String, imageType: String, filename: String) -> Unit
) : ListAdapter<ProcessedFile, ProcessedFingersAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_processed_finger, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Captured image views
        private val capturedImageContainer: FrameLayout = itemView.findViewById(R.id.capturedImageContainer)
        private val capturedImage: ImageView = itemView.findViewById(R.id.capturedImage)
        private val capturedLoadingProgress: ProgressBar = itemView.findViewById(R.id.capturedLoadingProgress)
        private val capturedPlaceholder: LinearLayout = itemView.findViewById(R.id.capturedPlaceholder)

        // Processed image views
        private val processedImageContainer: FrameLayout = itemView.findViewById(R.id.processedImageContainer)
        private val processedImage: ImageView = itemView.findViewById(R.id.processedImage)
        private val processedLoadingProgress: ProgressBar = itemView.findViewById(R.id.processedLoadingProgress)
        private val processedErrorContainer: LinearLayout = itemView.findViewById(R.id.processedErrorContainer)

        // Text views
        private val fingerNameText: TextView = itemView.findViewById(R.id.fingerNameText)
        private val fileNameText: TextView = itemView.findViewById(R.id.fileNameText)

        // Buttons
        private val downloadPngButton: MaterialButton = itemView.findViewById(R.id.downloadPngButton)
        private val downloadWsqButton: MaterialButton = itemView.findViewById(R.id.downloadWsqButton)

        fun bind(item: ProcessedFile) {
            val displayName = item.getDisplayName()

            // Set text fields
            fingerNameText.text = displayName
            fileNameText.text = item.original_name

            // === Load Captured Image ===
            loadCapturedImage(item, displayName)

            // === Load Processed Image ===
            loadProcessedImage(item, displayName)

            // Set download click listeners
            downloadPngButton.setOnClickListener {
                onDownloadPng(item)
            }

            downloadWsqButton.setOnClickListener {
                onDownloadWsq(item)
            }
        }

        private fun loadCapturedImage(item: ProcessedFile, displayName: String) {
            val capturedUrl = item.captured_png

            if (capturedUrl.isNullOrEmpty()) {
                // No captured image available - show placeholder
                capturedImage.visibility = View.GONE
                capturedLoadingProgress.visibility = View.GONE
                capturedPlaceholder.visibility = View.VISIBLE

                capturedImageContainer.setOnClickListener(null)
            } else {
                // Load captured image
                capturedImage.visibility = View.VISIBLE
                capturedLoadingProgress.visibility = View.VISIBLE
                capturedPlaceholder.visibility = View.GONE

                Glide.with(itemView.context)
                    .load(capturedUrl)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            capturedLoadingProgress.visibility = View.GONE
                            capturedImage.visibility = View.GONE
                            capturedPlaceholder.visibility = View.VISIBLE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            capturedLoadingProgress.visibility = View.GONE
                            capturedImage.visibility = View.VISIBLE
                            capturedPlaceholder.visibility = View.GONE
                            return false
                        }
                    })
                    .into(capturedImage)

                // Click to view full screen
                capturedImageContainer.setOnClickListener {
                    onImageClick(
                        capturedUrl,
                        displayName,
                        "Captured Image",
                        "${item.original_name.substringBeforeLast(".")}_captured.png"
                    )
                }
            }
        }

        private fun loadProcessedImage(item: ProcessedFile, displayName: String) {
            // Show loading state
            processedImage.visibility = View.VISIBLE
            processedLoadingProgress.visibility = View.VISIBLE
            processedErrorContainer.visibility = View.GONE

            Glide.with(itemView.context)
                .load(item.processed_png)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        processedLoadingProgress.visibility = View.GONE
                        processedImage.visibility = View.GONE
                        processedErrorContainer.visibility = View.VISIBLE
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        processedLoadingProgress.visibility = View.GONE
                        processedImage.visibility = View.VISIBLE
                        processedErrorContainer.visibility = View.GONE
                        return false
                    }
                })
                .into(processedImage)

            // Click to view full screen
            processedImageContainer.setOnClickListener {
                onImageClick(
                    item.processed_png,
                    displayName,
                    "Processed Image",
                    item.getPngFilename()
                )
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ProcessedFile>() {
        override fun areItemsTheSame(oldItem: ProcessedFile, newItem: ProcessedFile): Boolean {
            return oldItem.original_name == newItem.original_name
        }

        override fun areContentsTheSame(oldItem: ProcessedFile, newItem: ProcessedFile): Boolean {
            return oldItem == newItem
        }
    }
}
