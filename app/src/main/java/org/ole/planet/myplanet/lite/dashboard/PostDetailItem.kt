package org.ole.planet.myplanet.lite.dashboard

import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository.NewsDocument

sealed class PostDetailItem {
    data class Header(
        val id: String,
        val author: String,
        val username: String?,
        val hasAvatar: Boolean,
        val message: String?,
        val imagePaths: List<String>,
        val timestamp: Long,
        val commentCount: Int,
        val isLoadingComments: Boolean,
        val canReply: Boolean,
        val canEdit: Boolean,
        val canDelete: Boolean,
        val canShare: Boolean
    ) : PostDetailItem()

    data class Comment(
        val id: String,
        val author: String,
        val username: String?,
        val hasAvatar: Boolean,
        val message: String?,
        val imagePaths: List<String>,
        val timestamp: Long,
        val canEdit: Boolean,
        val canDelete: Boolean,
        val document: NewsDocument?
    ) : PostDetailItem()
}
