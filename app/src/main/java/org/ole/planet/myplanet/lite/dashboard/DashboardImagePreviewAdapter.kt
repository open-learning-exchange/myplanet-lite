/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-15
 */

package org.ole.planet.myplanet.lite.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView
import org.ole.planet.myplanet.lite.R

class DashboardImagePreviewAdapter(
    private val imagePaths: List<String>,
    private val imageLoader: DashboardPostImageLoader,
    private val onDismissRequested: () -> Unit
) : RecyclerView.Adapter<DashboardImagePreviewAdapter.ImagePreviewViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImagePreviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_image_preview_page, parent, false)
        return ImagePreviewViewHolder(view, imageLoader, onDismissRequested)
    }

    override fun onBindViewHolder(holder: ImagePreviewViewHolder, position: Int) {
        holder.bind(imagePaths[position])
    }

    override fun getItemCount(): Int = imagePaths.size

    class ImagePreviewViewHolder(
        view: View,
        private val imageLoader: DashboardPostImageLoader,
        private val onDismissRequested: () -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val photoView: PhotoView = view.findViewById(R.id.previewPhotoView)
        private val progressBar: ProgressBar = view.findViewById(R.id.previewPageLoading)

        init {
            photoView.maximumScale = MAX_SCALE
            photoView.mediumScale = MEDIUM_SCALE
            photoView.minimumScale = MIN_SCALE
            photoView.setOnViewTapListener { _, _, _ -> onDismissRequested() }
            photoView.setOnOutsidePhotoTapListener { onDismissRequested() }
            photoView.setOnScaleChangeListener { _, _, _ ->
                if (photoView.scale <= DISMISS_SCALE_THRESHOLD) {
                    onDismissRequested()
                }
            }
        }

        fun bind(imagePath: String) {
            progressBar.isVisible = true
            photoView.isVisible = false
            photoView.setScale(1f, false)
            imageLoader.bind(photoView, imagePath) { success ->
                progressBar.isVisible = false
                photoView.isVisible = success
                if (!success) {
                    onDismissRequested()
                }
            }
        }

        companion object {
            private const val MAX_SCALE = 5f
            private const val MEDIUM_SCALE = 2.5f
            private const val MIN_SCALE = 0.6f
            private const val DISMISS_SCALE_THRESHOLD = 0.7f
        }
    }
}
