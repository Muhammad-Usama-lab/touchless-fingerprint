package com.rmst.biometrics.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import com.rmst.biometrics.model.ProcessedFile
import android.graphics.drawable.Drawable

/**
 * Adapter for displaying processed fingerprint images in a RecyclerView
 */
class ProcessedFingersAdapter(
    private val onDownloadPng: (ProcessedFile) -> Unit,
    private val onDownloadWsq: (ProcessedFile) -> Unit
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
        private val fingerprintImage: ImageView = itemView.findViewById(R.id.fingerprintImage)
        private val imageLoadingProgress: ProgressBar = itemView.findViewById(R.id.imageLoadingProgress)
        private val imageErrorContainer: View = itemView.findViewById(R.id.imageErrorContainer)
        private val fingerNameText: TextView = itemView.findViewById(R.id.fingerNameText)
        private val fileNameText: TextView = itemView.findViewById(R.id.fileNameText)
        private val downloadPngButton: MaterialButton = itemView.findViewById(R.id.downloadPngButton)
        private val downloadWsqButton: MaterialButton = itemView.findViewById(R.id.downloadWsqButton)

        fun bind(item: ProcessedFile) {
            // Set text fields
            fingerNameText.text = item.getDisplayName()
            fileNameText.text = item.original_name

            // Show loading state
            imageLoadingProgress.visibility = View.VISIBLE
            imageErrorContainer.visibility = View.GONE
            fingerprintImage.visibility = View.VISIBLE

            // Load image with Glide
            Glide.with(itemView.context)
                .load(item.processed_png)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        imageLoadingProgress.visibility = View.GONE
                        fingerprintImage.visibility = View.GONE
                        imageErrorContainer.visibility = View.VISIBLE
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        imageLoadingProgress.visibility = View.GONE
                        imageErrorContainer.visibility = View.GONE
                        fingerprintImage.visibility = View.VISIBLE
                        return false
                    }
                })
                .into(fingerprintImage)

            // Set click listeners
            downloadPngButton.setOnClickListener {
                onDownloadPng(item)
            }

            downloadWsqButton.setOnClickListener {
                onDownloadWsq(item)
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
