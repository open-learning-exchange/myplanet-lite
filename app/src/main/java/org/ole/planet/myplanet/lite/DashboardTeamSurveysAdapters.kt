package org.ole.planet.myplanet.lite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import io.noties.markwon.Markwon
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyOutboxStore.OutboxEntry
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyDraftStore.DraftEntry
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyStatusStore
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.SurveyStatus

internal class SurveysPagerAdapter(
    private val teamEmptyMessage: String,
    private val adoptedEmptyMessage: String,
    private val outboxEmptyMessage: String,
    private val draftsEmptyMessage: String,
    private val statusStore: DashboardSurveyStatusStore,
    private val onSurveySelected: (SurveyDocument) -> Unit,
    private val onSurveyDownloadRequested: (SurveyDocument) -> Unit,
    private val onOutboxSelected: (OutboxEntry) -> Unit,
    private val onDraftSelected: (DraftEntry) -> Unit,
    private val onDraftDeleted: (DraftEntry) -> Unit,
    private val onStatusChanged: () -> Unit,
) : RecyclerView.Adapter<SurveysPagerAdapter.PageViewHolder>() {

    private val pages = listOf(Page.TEAM, Page.ADOPTED, Page.OUTBOX, Page.DRAFTS)
    private var teamItems: List<SurveyItemUiModel> = emptyList()
    private var adoptedItems: List<SurveyItemUiModel> = emptyList()
    private var outboxItems: List<OutboxEntry> = emptyList()
    private var draftItems: List<DraftEntry> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_surveys_page, parent, false)
        return PageViewHolder(view)
    }

    override fun getItemCount(): Int = pages.size

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        when (pages[position]) {
            Page.TEAM -> holder.bindTeam(
                items = teamItems,
                emptyMessage = teamEmptyMessage,
                statusStore = statusStore,
                onStatusChanged = onStatusChanged,
                onSurveySelected = onSurveySelected,
                onDownloadRequested = onSurveyDownloadRequested,
            )
            Page.ADOPTED -> holder.bindTeam(
                items = adoptedItems,
                emptyMessage = adoptedEmptyMessage,
                statusStore = statusStore,
                onStatusChanged = onStatusChanged,
                onSurveySelected = onSurveySelected,
                onDownloadRequested = onSurveyDownloadRequested,
            )
            Page.OUTBOX -> holder.bindOutbox(
                items = outboxItems,
                emptyMessage = outboxEmptyMessage,
                onOutboxSelected = onOutboxSelected,
            )
            Page.DRAFTS -> holder.bindDrafts(
                items = draftItems,
                emptyMessage = draftsEmptyMessage,
                onDraftSelected = onDraftSelected,
                onDraftDeleted = onDraftDeleted,
            )
        }
    }

    fun submit(
        teamSurveys: List<SurveyDocument>,
        adoptedSurveys: List<SurveyDocument>,
        completionCounts: Map<String, Int>,
        savedSurveys: Set<String>,
        savedRevisions: Map<String, String?>,
        outboxItems: List<OutboxEntry>,
        draftItems: List<DraftEntry>,
    ) {
        teamItems = teamSurveys.map { it.toUiModel(completionCounts, savedSurveys, savedRevisions) }
        adoptedItems = adoptedSurveys.map { it.toUiModel(completionCounts, savedSurveys, savedRevisions) }
        this.outboxItems = outboxItems
        this.draftItems = draftItems
        notifyItemChanged(0)
        notifyItemChanged(1)
        notifyItemChanged(2)
        notifyItemChanged(3)
    }

    fun updateSavedSurveys(savedSurveys: Set<String>, savedRevisions: Map<String, String?>) {
        teamItems = teamItems.map {
            it.copy(
                isSaved = savedSurveys.contains(it.document.id.orEmpty()),
                savedRevision = savedRevisions[it.document.id.orEmpty()],
            )
        }
        adoptedItems = adoptedItems.map {
            it.copy(
                isSaved = savedSurveys.contains(it.document.id.orEmpty()),
                savedRevision = savedRevisions[it.document.id.orEmpty()],
            )
        }
        notifyItemChanged(0)
        notifyItemChanged(1)
    }

    private fun SurveyDocument.toUiModel(
        completionCounts: Map<String, Int>,
        savedSurveys: Set<String>,
        savedRevisions: Map<String, String?>,
    ): SurveyItemUiModel {
        val documentId = id.orEmpty()
        return SurveyItemUiModel(
            document = this,
            completionCount = completionCounts[documentId] ?: 0,
            isSaved = savedSurveys.contains(documentId),
            savedRevision = savedRevisions[documentId],
        )
    }

    private enum class Page { TEAM, ADOPTED, OUTBOX, DRAFTS }

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val emptyView: TextView = itemView.findViewById(R.id.dashboardSurveysListEmpty)
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.dashboardSurveysList)
        private var teamAdapter: SurveyItemsAdapter? = null
        private var outboxAdapter: SurveyOutboxAdapter? = null
        private var draftAdapter: SurveyDraftAdapter? = null
        private var currentPage: Page = Page.TEAM

        init {
            recyclerView.layoutManager = LinearLayoutManager(itemView.context)
        }

        fun bindTeam(
            items: List<SurveyItemUiModel>,
            emptyMessage: String,
            statusStore: DashboardSurveyStatusStore,
            onStatusChanged: () -> Unit,
            onSurveySelected: (SurveyDocument) -> Unit,
            onDownloadRequested: (SurveyDocument) -> Unit,
        ) {
            if (currentPage != Page.TEAM && currentPage != Page.ADOPTED) {
                recyclerView.adapter = null
            }
            if (recyclerView.adapter !is SurveyItemsAdapter) {
                teamAdapter = SurveyItemsAdapter(
                    statusStore = statusStore,
                    onStatusChanged = onStatusChanged,
                    onSurveySelected = onSurveySelected,
                    onDownloadRequested = onDownloadRequested,
                )
                recyclerView.adapter = teamAdapter
            }
            teamAdapter?.submitList(items)
            emptyView.text = emptyMessage
            emptyView.isVisible = items.isEmpty()
            recyclerView.isVisible = items.isNotEmpty()
            outboxAdapter = null
            draftAdapter = null
            currentPage = Page.TEAM
        }

        fun bindOutbox(
            items: List<OutboxEntry>,
            emptyMessage: String,
            onOutboxSelected: (OutboxEntry) -> Unit,
        ) {
            if (currentPage != Page.OUTBOX) {
                recyclerView.adapter = null
            }
            if (recyclerView.adapter !is SurveyOutboxAdapter) {
                outboxAdapter = SurveyOutboxAdapter(onOutboxSelected)
                recyclerView.adapter = outboxAdapter
            }
            outboxAdapter?.submitList(items)
            emptyView.text = emptyMessage
            emptyView.isVisible = items.isEmpty()
            recyclerView.isVisible = items.isNotEmpty()
            teamAdapter = null
            currentPage = Page.OUTBOX
        }

        fun bindDrafts(
            items: List<DraftEntry>,
            emptyMessage: String,
            onDraftSelected: (DraftEntry) -> Unit,
            onDraftDeleted: (DraftEntry) -> Unit,
        ) {
            if (currentPage != Page.DRAFTS) recyclerView.adapter = null
            if (recyclerView.adapter !is SurveyDraftAdapter) {
                draftAdapter = SurveyDraftAdapter(onDraftSelected, onDraftDeleted)
                recyclerView.adapter = draftAdapter
            }
            draftAdapter?.submitList(items)
            emptyView.text = emptyMessage
            emptyView.isVisible = items.isEmpty()
            recyclerView.isVisible = items.isNotEmpty()
            teamAdapter = null
            outboxAdapter = null
            currentPage = Page.DRAFTS
        }
    }
}

internal data class SurveyItemUiModel(
    val document: SurveyDocument,
    val completionCount: Int,
    val isSaved: Boolean,
    val savedRevision: String?,
)

private class SurveyItemsAdapter(
    private val statusStore: DashboardSurveyStatusStore,
    private val onStatusChanged: () -> Unit,
    private val onSurveySelected: (SurveyDocument) -> Unit,
    private val onDownloadRequested: (SurveyDocument) -> Unit,
) : ListAdapter<SurveyItemUiModel, SurveyItemsAdapter.SurveyViewHolder>(
    org.ole.planet.myplanet.lite.util.DiffUtils.itemCallback({ oldItem, newItem -> oldItem.document.id == newItem.document.id })
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_survey, parent, false)
        return SurveyViewHolder(
            view,
            statusStore,
            onStatusChanged,
            onSurveySelected,
            onDownloadRequested,
        )
    }

    override fun onBindViewHolder(holder: SurveyViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SurveyViewHolder(
        itemView: View,
        private val statusStore: DashboardSurveyStatusStore,
        private val onStatusChanged: () -> Unit,
        private val onSurveySelected: (SurveyDocument) -> Unit,
        private val onDownloadRequested: (SurveyDocument) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.dashboardSurveyTitle)
        private val description: TextView = itemView.findViewById(R.id.dashboardSurveyDescription)
        private val statusIcon: ImageView = itemView.findViewById(R.id.dashboardSurveyStatusIcon)
        private val metadata: TextView = itemView.findViewById(R.id.dashboardSurveyMetadata)
        private val markwon: Markwon = Markwon.builder(itemView.context).build()
        private val downloadButton: MaterialButton = itemView.findViewById(R.id.dashboardSurveyDownloadButton)

        fun bind(uiModel: SurveyItemUiModel) {
            val document = uiModel.document
            markwon.setMarkdown(title, document.name.orEmpty().replace("\n", "  \n"))
            val detail = document.description?.takeIf { it.isNotBlank() }
            if (detail.isNullOrBlank()) {
                description.isVisible = false
                description.text = ""
            } else {
                description.isVisible = true
                markwon.setMarkdown(description, detail.replace("\n", "  \n"))
            }

            metadata.text = itemView.context.getString(
                R.string.dashboard_surveys_metadata,
                formatCreatedDate(document.createdDate),
                uiModel.completionCount,
            )
            metadata.isVisible = true

            bindDownloadButton(uiModel)
            bindStatus(document)
        }

        private fun bindDownloadButton(uiModel: SurveyItemUiModel) {
            val document = uiModel.document
            val latestRevision = document.rev
            val isOutdated = uiModel.isSaved &&
                !latestRevision.isNullOrBlank() &&
                uiModel.savedRevision != null &&
                uiModel.savedRevision != latestRevision
            val buttonEnabled = !uiModel.isSaved || isOutdated
            val iconRes = if (isOutdated) R.drawable.ic_sync_again_24 else R.drawable.ic_survey_download_24

            downloadButton.setIconResource(iconRes)
            downloadButton.isEnabled = buttonEnabled
            downloadButton.alpha = if (buttonEnabled) 1f else 0.7f
            downloadButton.contentDescription = itemView.context.getString(
                if (buttonEnabled) R.string.dashboard_survey_download else R.string.dashboard_survey_downloaded,
            )
            downloadButton.setOnClickListener {
                if (buttonEnabled) {
                    onDownloadRequested(document)
                }
            }
        }

        private fun bindStatus(document: SurveyDocument) {
            var status = statusStore.getStatus(document.id) ?: SurveyStatus.NEW
            statusIcon.setImageResource(status.iconRes)
            val openSurvey = {
                if (status != SurveyStatus.COMPLETED) {
                    statusStore.markViewed(document.id)
                    status = SurveyStatus.VIEWED
                    statusIcon.setImageResource(status.iconRes)
                    onStatusChanged()
                }
                onSurveySelected(document)
            }
            itemView.setOnClickListener { openSurvey() }
            title.setOnClickListener { openSurvey() }
            description.setOnClickListener { openSurvey() }
            itemView.setOnLongClickListener {
                statusStore.markCompleted(document.id)
                status = SurveyStatus.COMPLETED
                statusIcon.setImageResource(status.iconRes)
                onStatusChanged()
                true
            }
        }

        private fun formatCreatedDate(raw: String?): String {
            val context = itemView.context
            val millis = raw?.toLongOrNull() ?: return context.getString(
                R.string.dashboard_surveys_unknown_date,
            )
            return runCatching {
                DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                    .format(Date(millis))
            }.getOrElse {
                context.getString(R.string.dashboard_surveys_unknown_date)
            }
        }
    }
}

private val SurveyStatus.iconRes: Int
    get() = when (this) {
        SurveyStatus.NEW -> R.drawable.ic_survey_new_24
        SurveyStatus.VIEWED -> R.drawable.ic_survey_viewed_24
        SurveyStatus.COMPLETED -> R.drawable.ic_survey_completed_24
    }

private class SurveyOutboxAdapter(
    private val onOutboxSelected: (OutboxEntry) -> Unit,
) : ListAdapter<OutboxEntry, SurveyOutboxAdapter.OutboxViewHolder>(
    org.ole.planet.myplanet.lite.util.DiffUtils.itemCallback({ oldItem, newItem -> oldItem.id == newItem.id })
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OutboxViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_survey_outbox, parent, false)
        return OutboxViewHolder(view, onOutboxSelected)
    }

    override fun onBindViewHolder(holder: OutboxViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class OutboxViewHolder(
        itemView: View,
        private val onOutboxSelected: (OutboxEntry) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.dashboardOutboxTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.dashboardOutboxSubtitle)
        private val timestamp: TextView = itemView.findViewById(R.id.dashboardOutboxTimestamp)

        fun bind(entry: OutboxEntry) {
            title.text = entry.surveyName.orEmpty().ifBlank {
                itemView.context.getString(R.string.dashboard_outbox_unknown_survey)
            }
            subtitle.text = entry.teamName.orEmpty().ifBlank {
                itemView.context.getString(R.string.dashboard_outbox_unknown_team)
            }
            val formattedDate = runCatching {
                DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                    Locale.getDefault(),
                ).format(Date(entry.createdAt))
            }.getOrElse {
                ""
            }
            timestamp.text = itemView.context.getString(R.string.dashboard_outbox_saved_at, formattedDate)
            timestamp.isVisible = formattedDate.isNotBlank()
            itemView.setOnClickListener { onOutboxSelected(entry) }
        }
    }
}

private class SurveyDraftAdapter(
    private val onSelected: (DraftEntry) -> Unit,
    private val onDeleted: (DraftEntry) -> Unit,
) : ListAdapter<DraftEntry, SurveyDraftAdapter.DraftViewHolder>(
    org.ole.planet.myplanet.lite.util.DiffUtils.itemCallback({ oldItem, newItem -> oldItem.key == newItem.key }),
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DraftViewHolder =
        DraftViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_dashboard_survey_draft, parent, false),
            onSelected,
            onDeleted,
        )

    override fun onBindViewHolder(holder: DraftViewHolder, position: Int) = holder.bind(getItem(position))

    class DraftViewHolder(
        itemView: View,
        private val onSelected: (DraftEntry) -> Unit,
        private val onDeleted: (DraftEntry) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.dashboardDraftTitle)
        private val team: TextView = itemView.findViewById(R.id.dashboardDraftTeam)
        private val updated: TextView = itemView.findViewById(R.id.dashboardDraftUpdatedAt)
        private val delete: ImageButton = itemView.findViewById(R.id.dashboardDraftDelete)

        fun bind(entry: DraftEntry) {
            title.text = entry.document.name.orEmpty().ifBlank {
                itemView.context.getString(R.string.dashboard_outbox_unknown_survey)
            }
            team.text = entry.teamName.orEmpty().ifBlank {
                itemView.context.getString(R.string.dashboard_outbox_unknown_team)
            }
            val formatted = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.updatedAt))
            updated.text = itemView.context.getString(R.string.dashboard_survey_draft_last_edited, formatted)
            itemView.setOnClickListener { onSelected(entry) }
            delete.setOnClickListener { onDeleted(entry) }
        }
    }
}

internal fun TabLayout.Tab.ensureOffsetBadge(count: Int) {
    val badge = orCreateBadge
    badge.number = count
    badge.badgeGravity = BadgeDrawable.TOP_END
    val resources = view.resources
    badge.horizontalOffset = resources.getDimensionPixelOffset(
        R.dimen.dashboard_surveys_tab_badge_horizontal_offset,
    )
    badge.verticalOffset = resources.getDimensionPixelOffset(
        R.dimen.dashboard_surveys_tab_badge_vertical_offset,
    )
}
