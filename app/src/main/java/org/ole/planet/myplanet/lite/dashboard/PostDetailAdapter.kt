package org.ole.planet.myplanet.lite.dashboard

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import org.ole.planet.myplanet.lite.R

class PostDetailAdapter(
    private val markwon: Markwon,
    private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
    private val imageBinder: (ImageView, String) -> Unit,
    private val onImageClicked: (List<String>, Int) -> Unit,
    private val onDeleteClicked: () -> Unit,
    private val onShareClicked: (PostDetailItem.Header) -> Unit,
    private val onEditClicked: (PostDetailItem.Header) -> Unit,
    private val onReplyClicked: () -> Unit,
    private val onCommentEditClicked: (PostDetailItem.Comment) -> Unit,
    private val onCommentDeleteClicked: (PostDetailItem.Comment) -> Unit
) : ListAdapter<PostDetailItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PostDetailItem.Header -> VIEW_TYPE_HEADER
            is PostDetailItem.Comment -> VIEW_TYPE_COMMENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_dashboard_post_detail_header, parent, false)
                HeaderViewHolder(
                    view,
                    markwon,
                    avatarBinder,
                    imageBinder,
                    onImageClicked,
                    onDeleteClicked,
                    onShareClicked,
                    onEditClicked,
                    onReplyClicked
                )
            }
            else -> {
                val view = inflater.inflate(R.layout.item_dashboard_comment, parent, false)
                CommentViewHolder(
                    view,
                    markwon,
                    avatarBinder,
                    imageBinder,
                    onImageClicked,
                    onCommentEditClicked,
                    onCommentDeleteClicked
                )
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when {
            holder is HeaderViewHolder && item is PostDetailItem.Header -> holder.bind(item)
            holder is CommentViewHolder && item is PostDetailItem.Comment -> {
                val isLast = position == itemCount - 1
                holder.bind(item, isLast)
            }
        }
    }

    private class HeaderViewHolder(
        view: View,
        private val markwon: Markwon,
        private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
        private val imageBinder: (ImageView, String) -> Unit,
        private val onImageClicked: (List<String>, Int) -> Unit,
        private val onDeleteClicked: () -> Unit,
        private val onShareClicked: (PostDetailItem.Header) -> Unit,
        private val onEditClicked: (PostDetailItem.Header) -> Unit,
        private val onReplyClicked: () -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val authorView: TextView = view.findViewById(R.id.postDetailAuthor)
        private val metadataView: TextView = view.findViewById(R.id.postDetailMetadata)
        private val bodyView: TextView = view.findViewById(R.id.postDetailBody)
        private val avatarView: ImageView = view.findViewById(R.id.postDetailAvatar)
        private val imagesContainer: LinearLayout = view.findViewById(R.id.postDetailImagesContainer)
        private val commentsLabel: TextView = view.findViewById(R.id.postDetailCommentsLabel)
        private val commentsEmpty: TextView = view.findViewById(R.id.postDetailCommentsEmpty)
        private val dividerView: View = view.findViewById(R.id.postDetailDivider)
        private val overflowMenu: View = view.findViewById(R.id.postDetailOverflowMenu)

        fun bind(item: PostDetailItem.Header) {
            authorView.text = item.author
            val relativeTime = org.ole.planet.myplanet.lite.util.DateUtils.formatRelativeTime(itemView.context, item.timestamp)
            metadataView.text = org.ole.planet.myplanet.lite.util.DateUtils.buildMetadata(item.username, relativeTime)
            if (item.message.isNullOrBlank()) {
                bodyView.isVisible = false
                bodyView.text = ""
            } else {
                bodyView.isVisible = true
                val renderedMessage = transformCommentMarkdownForDisplay(item.message)
                markwon.setMarkdown(bodyView, renderedMessage)
            }
            avatarBinder(avatarView, item.username, item.hasAvatar)
            bindImages(item)
            val count = item.commentCount.coerceAtLeast(0)
            commentsLabel.isVisible = true
            commentsEmpty.isVisible = !item.isLoadingComments && count == 0
            dividerView.isVisible = count > 0
            bindActions(item)
        }

        @SuppressLint("RestrictedApi")
        private fun bindActions(item: PostDetailItem.Header) {
            val hasActions = item.canEdit || item.canDelete || item.canShare || item.canReply
            overflowMenu.isVisible = hasActions
            if (!hasActions) {
                overflowMenu.setOnClickListener(null)
                return
            }
            overflowMenu.setOnClickListener {
                val themedContext = ContextThemeWrapper(itemView.context, R.style.Widget_MyPlanet_PopupMenu)
                val popup = PopupMenu(themedContext, overflowMenu)
                popup.menuInflater.inflate(R.menu.menu_dashboard_post_actions, popup.menu)
                popup.menu.findItem(R.id.action_reply).isVisible = item.canReply
                popup.menu.findItem(R.id.action_edit).isVisible = item.canEdit
                popup.menu.findItem(R.id.action_delete).isVisible = item.canDelete
                popup.menu.findItem(R.id.action_share).isVisible = item.canShare
                if (popup.menu is MenuBuilder) {
                    (popup.menu as MenuBuilder).setOptionalIconsVisible(true)
                }
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_reply -> {
                            onReplyClicked()
                            true
                        }
                        R.id.action_edit -> {
                            onEditClicked(item)
                            true
                        }
                        R.id.action_delete -> {
                            onDeleteClicked()
                            true
                        }
                        R.id.action_share -> {
                            onShareClicked(item)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }

        private fun bindImages(item: PostDetailItem.Header) {
            if (item.imagePaths.isEmpty()) {
                imagesContainer.isVisible = false
                imagesContainer.removeAllViews()
                return
            }
            val context = imagesContainer.context
            val spacing = context.resources.getDimensionPixelSize(R.dimen.dashboard_post_image_spacing)
            imagesContainer.removeAllViews()
            imagesContainer.isVisible = true
            item.imagePaths.forEachIndexed { index, path ->
                val imageView = AppCompatImageView(context)
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                if (index > 0) {
                    params.topMargin = spacing
                }
                imageView.layoutParams = params
                imageView.adjustViewBounds = true
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                imageView.setBackgroundResource(R.drawable.dashboard_post_image_placeholder)
                imageView.contentDescription = context.getString(R.string.dashboard_post_image_content_description)
                imagesContainer.addView(imageView)
                imageBinder(imageView, path)
                imageView.setOnClickListener { onImageClicked(item.imagePaths, index) }
            }
        }
    }

    private class CommentViewHolder(
        view: View,
        private val markwon: Markwon,
        private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
        private val imageBinder: (ImageView, String) -> Unit,
        private val onImageClicked: (List<String>, Int) -> Unit,
        private val onEditClicked: (PostDetailItem.Comment) -> Unit,
        private val onDeleteClicked: (PostDetailItem.Comment) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val authorView: TextView = view.findViewById(R.id.commentAuthor)
        private val metadataView: TextView = view.findViewById(R.id.commentMetadata)
        private val bodyView: TextView = view.findViewById(R.id.commentBody)
        private val avatarView: ImageView = view.findViewById(R.id.commentAvatar)
        private val imagesContainer: LinearLayout = view.findViewById(R.id.commentImagesContainer)
        private val dividerView: View = view.findViewById(R.id.commentDivider)
        private val overflowMenu: View = view.findViewById(R.id.commentOverflowMenu)

        fun bind(item: PostDetailItem.Comment, isLast: Boolean) {
            authorView.text = item.author
            val relativeTime = org.ole.planet.myplanet.lite.util.DateUtils.formatRelativeTime(itemView.context, item.timestamp)
            metadataView.text = org.ole.planet.myplanet.lite.util.DateUtils.buildMetadata(item.username, relativeTime)
            if (item.message.isNullOrBlank()) {
                bodyView.isVisible = false
                bodyView.text = ""
            } else {
                bodyView.isVisible = true
                val renderedMessage = transformCommentMarkdownForDisplay(item.message)
                markwon.setMarkdown(bodyView, renderedMessage)
            }
            if (item.imagePaths.isEmpty()) {
                imagesContainer.isVisible = false
                imagesContainer.removeAllViews()
            } else {
                val context = imagesContainer.context
                val spacing = context.resources.getDimensionPixelSize(R.dimen.dashboard_post_image_spacing)
                imagesContainer.removeAllViews()
                item.imagePaths.forEachIndexed { index, path ->
                    val imageView = AppCompatImageView(context)
                    val params = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    if (index > 0) {
                        params.topMargin = spacing
                    }
                    imageView.layoutParams = params
                    imageView.adjustViewBounds = true
                    imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                    imageView.setBackgroundResource(R.drawable.dashboard_post_image_placeholder)
                    imageView.contentDescription = context.getString(R.string.dashboard_post_image_content_description)
                    imagesContainer.addView(imageView)
                    imageBinder(imageView, path)
                    imageView.setOnClickListener { onImageClicked(item.imagePaths, index) }
                }
                imagesContainer.isVisible = true
            }
            avatarBinder(avatarView, item.username, item.hasAvatar)
            bindActions(item)
            dividerView.isVisible = !isLast
        }

        @SuppressLint("RestrictedApi")
        private fun bindActions(item: PostDetailItem.Comment) {
            val hasActions = item.canEdit || item.canDelete
            overflowMenu.isVisible = hasActions
            if (!hasActions) {
                overflowMenu.setOnClickListener(null)
                return
            }
            overflowMenu.setOnClickListener {
                val themedContext = ContextThemeWrapper(itemView.context, R.style.Widget_MyPlanet_PopupMenu)
                val popup = PopupMenu(themedContext, overflowMenu)
                popup.menuInflater.inflate(R.menu.menu_dashboard_comment_actions, popup.menu)
                popup.menu.findItem(R.id.action_edit).isVisible = item.canEdit
                popup.menu.findItem(R.id.action_delete).isVisible = item.canDelete
                if (popup.menu is MenuBuilder) {
                    (popup.menu as MenuBuilder).setOptionalIconsVisible(true)
                }
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_edit -> {
                            onEditClicked(item)
                            true
                        }

                        R.id.action_delete -> {
                            onDeleteClicked(item)
                            true
                        }

                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_COMMENT = 1

        private val DIFF_CALLBACK = org.ole.planet.myplanet.lite.util.DiffUtils.itemCallback<PostDetailItem>({ oldItem, newItem ->
            when {
                oldItem is PostDetailItem.Header && newItem is PostDetailItem.Header -> oldItem.id == newItem.id
                oldItem is PostDetailItem.Comment && newItem is PostDetailItem.Comment -> oldItem.id == newItem.id
                else -> false
            }
        })
    }
}
