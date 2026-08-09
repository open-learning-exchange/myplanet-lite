/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-08-09
 */

package org.ole.planet.myplanet.lite

import android.icu.text.CompactDecimalFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import org.ole.planet.myplanet.lite.util.DiffUtils
import java.util.Locale

internal class DashboardNewsAdapter(
    private val markwon: Markwon,
    private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
    private val imageBinder: (ImageView, String) -> Unit,
    private val onImageClicked: (DashboardNewsItem, Int) -> Unit,
    private val onPostClicked: (DashboardNewsItem) -> Unit,
    private val onDeleteClicked: (DashboardNewsItem) -> Unit,
    private val onShareClicked: (DashboardNewsItem) -> Unit,
    private val onEditClicked: (DashboardNewsItem) -> Unit,
    private val onAuthorClicked: (DashboardNewsItem) -> Unit,
) : ListAdapter<DashboardNewsItem, DashboardNewsViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DashboardNewsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dashboard_post, parent, false)
        return DashboardNewsViewHolder(
            view,
            markwon,
            avatarBinder,
            imageBinder,
            onImageClicked,
            onPostClicked,
            onDeleteClicked,
            onShareClicked,
            onEditClicked,
            onAuthorClicked,
        )
    }

    override fun onBindViewHolder(holder: DashboardNewsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private var usernameIndex: Map<String, List<Int>> = emptyMap()

    override fun onCurrentListChanged(
        previousList: List<DashboardNewsItem>,
        currentList: List<DashboardNewsItem>,
    ) {
        super.onCurrentListChanged(previousList, currentList)
        usernameIndex =
            currentList
                .mapIndexedNotNull { index, item -> item.username?.let { it.lowercase() to index } }
                .groupBy({ it.first }, { it.second })
    }

    fun getPositionsForUsername(username: String): List<Int> = usernameIndex[username.lowercase()] ?: emptyList()

    fun isIndexAvailable(): Boolean = usernameIndex.isNotEmpty()

    fun notifyAvatarUpdated(recyclerView: RecyclerView, username: String) {
        val positions =
            if (isIndexAvailable()) {
                getPositionsForUsername(username)
            } else {
                currentList.mapIndexedNotNull { index, item ->
                    index.takeIf { item.username?.equals(username, ignoreCase = true) == true }
                }
            }
        recyclerView.post { positions.forEach(::notifyItemChanged) }
    }

    companion object {
        private val DIFF_CALLBACK =
            DiffUtils.itemCallback<DashboardNewsItem>({ oldItem, newItem ->
                oldItem.id == newItem.id
            })
    }
}

internal object DashboardVoicesRecyclerBinder {
    fun bind(
        recyclerView: RecyclerView,
        markwon: Markwon,
        shouldLoadMore: () -> Boolean,
        loadMore: () -> Unit,
        avatarBinder: (ImageView, String?, Boolean) -> Unit,
        imageBinder: (ImageView, String) -> Unit,
        onImageClicked: (DashboardNewsItem, Int) -> Unit,
        onPostClicked: (DashboardNewsItem) -> Unit,
        onDeleteClicked: (DashboardNewsItem) -> Unit,
        onShareClicked: (DashboardNewsItem) -> Unit,
        onEditClicked: (DashboardNewsItem) -> Unit,
        onAuthorClicked: (DashboardNewsItem) -> Unit,
    ): DashboardNewsAdapter {
        val adapter =
            DashboardNewsAdapter(
                markwon,
                avatarBinder,
                imageBinder,
                onImageClicked,
                onPostClicked,
                onDeleteClicked,
                onShareClicked,
                onEditClicked,
                onAuthorClicked,
            )
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(loadMoreListener(shouldLoadMore, loadMore))
        return adapter
    }

    private fun loadMoreListener(
        shouldLoadMore: () -> Boolean,
        loadMore: () -> Unit,
    ): RecyclerView.OnScrollListener =
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || !shouldLoadMore()) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val visibleItemCount = layoutManager.childCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                if (
                    layoutManager.itemCount > 0 &&
                    firstVisibleItemPosition + visibleItemCount >= layoutManager.itemCount - LOAD_MORE_THRESHOLD
                ) {
                    loadMore()
                }
            }
        }

    private const val LOAD_MORE_THRESHOLD = 3
}

internal class DashboardNewsViewHolder(
    view: View,
    private val markwon: Markwon,
    private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
    private val imageBinder: (ImageView, String) -> Unit,
    private val onImageClicked: (DashboardNewsItem, Int) -> Unit,
    private val onPostClicked: (DashboardNewsItem) -> Unit,
    private val onDeleteClicked: (DashboardNewsItem) -> Unit,
    private val onShareClicked: (DashboardNewsItem) -> Unit,
    private val onEditClicked: (DashboardNewsItem) -> Unit,
    private val onAuthorClicked: (DashboardNewsItem) -> Unit,
) : RecyclerView.ViewHolder(view) {
    private val authorView: TextView = view.findViewById(R.id.postAuthor)
    private val metadataView: TextView = view.findViewById(R.id.postMetadata)
    private val bodyView: TextView = view.findViewById(R.id.postBody)
    private val avatarView: ImageView = view.findViewById(R.id.postAvatar)
    private val imagesContainer: LinearLayout = view.findViewById(R.id.postImagesContainer)
    private val commentCountView: TextView = view.findViewById(R.id.postCommentsCount)
    private val commentsIconView: ImageView = view.findViewById(R.id.postCommentsIcon)
    private val commentsContainer: View = view.findViewById(R.id.postCommentsContainer)
    private val editAction: View = view.findViewById(R.id.postActionEdit)
    private val deleteAction: View = view.findViewById(R.id.postActionDelete)
    private val shareAction: View = view.findViewById(R.id.postActionShare)

    fun bind(item: DashboardNewsItem) {
        authorView.text = item.author
        metadataView.text = item.metadata
        bindMessage(item)
        avatarBinder(avatarView, item.username, item.hasAvatar)
        avatarView.setOnClickListener { onAuthorClicked(item) }
        authorView.setOnClickListener { onAuthorClicked(item) }
        metadataView.setOnClickListener { onAuthorClicked(item) }
        bindImages(item)
        commentCountView.text = formatCount(item.commentCount)
        commentsIconView.setOnClickListener { onPostClicked(item) }
        commentCountView.setOnClickListener { onPostClicked(item) }
        commentsContainer.setOnClickListener { onPostClicked(item) }
        bindActions(item)
    }

    private fun bindMessage(item: DashboardNewsItem) {
        if (item.message.isNullOrBlank()) {
            bodyView.isVisible = false
            bodyView.text = ""
            bodyView.setOnClickListener(null)
        } else {
            bodyView.isVisible = true
            markwon.setMarkdown(bodyView, item.message)
            bodyView.setOnClickListener { onPostClicked(item) }
        }
    }

    private fun bindActions(item: DashboardNewsItem) {
        setupAction(editAction, item.canEdit) { onEditClicked(item) }
        setupAction(deleteAction, item.canDelete) { onDeleteClicked(item) }
        setupAction(shareAction, item.canShare) { onShareClicked(item) }
    }

    private fun setupAction(view: View, enabled: Boolean, onClick: () -> Unit) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else DISABLED_ACTION_ALPHA
        view.setOnClickListener(if (enabled) View.OnClickListener { onClick() } else null)
    }

    private fun bindImages(item: DashboardNewsItem) {
        if (item.imagePaths.isEmpty()) {
            imagesContainer.isVisible = false
            imagesContainer.removeAllViews()
            return
        }
        imagesContainer.isVisible = true
        imagesContainer.removeAllViews()
        val context = imagesContainer.context
        val spacing = context.resources.getDimensionPixelSize(R.dimen.dashboard_post_image_spacing)
        val height = context.resources.getDimensionPixelSize(R.dimen.dashboard_card_image_height)
        item.imagePaths.forEachIndexed { index, path ->
            val imageView = AppCompatImageView(context)
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            if (index > 0) params.topMargin = spacing
            imageView.layoutParams = params
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setBackgroundResource(R.drawable.dashboard_post_image_placeholder)
            imageView.contentDescription = context.getString(R.string.dashboard_post_image_content_description)
            imagesContainer.addView(imageView)
            imageBinder(imageView, path)
            imageView.setOnClickListener { onImageClicked(item, index) }
        }
    }

    private fun formatCount(count: Int): String = COMPACT_DECIMAL_FORMAT.format(count.coerceAtLeast(0))

    companion object {
        private val COMPACT_DECIMAL_FORMAT =
            CompactDecimalFormat.getInstance(Locale.US, CompactDecimalFormat.CompactStyle.SHORT)
        private const val DISABLED_ACTION_ALPHA = 0.4f
    }
}
