/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-24
 */

package org.ole.planet.myplanet.lite

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.noties.markwon.Markwon
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardOutboxDetailActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyOutboxStore.OutboxEntry
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyStatusStore
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepositoryProvider
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.dashboard.SurveyStatus
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.survey.DashboardLocalSurveyRepository

class DashboardTeamSurveysFragment : Fragment(R.layout.fragment_dashboard_team_surveys) {

    private var teamId: String? = null
    private var teamName: String? = null

    private lateinit var titleView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var loadingView: ProgressBar
    private lateinit var errorView: TextView
    private lateinit var tabs: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var tabMediator: TabLayoutMediator? = null
    private lateinit var pagerAdapter: SurveysPagerAdapter
    private lateinit var statusStore: DashboardSurveyStatusStore
    private lateinit var localSurveyRepository: DashboardLocalSurveyRepository

    private var teamSurveys: List<SurveyDocument> = emptyList()
    private var adoptedSurveys: List<SurveyDocument> = emptyList()
    private val completionCounts: MutableMap<String, Int> = mutableMapOf()
    private var savedSurveyIds: Set<String> = emptySet()
    private var savedSurveyRevisions: Map<String, String?> = emptyMap()
    private var outboxEntries: List<OutboxEntry> = emptyList()

    private val repository = DashboardSurveysRepositoryProvider.getRepository()
    private var baseUrl: String? = null
    private var sessionCookie: String? = null
    private var credentials: StoredCredentials? = null
    private var username: String? = null
    private var hasLoadedOnce: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            teamId = bundle.getString(ARG_TEAM_ID)
            teamName = bundle.getString(ARG_TEAM_NAME)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.dashboardSurveysTitle)
        descriptionView = view.findViewById(R.id.dashboardSurveysDescription)
        loadingView = view.findViewById(R.id.dashboardSurveysLoading)
        errorView = view.findViewById(R.id.dashboardSurveysError)
        tabs = view.findViewById(R.id.dashboardSurveysTabs)
        swipeRefresh = view.findViewById(R.id.dashboardSurveysSwipeRefresh)
        viewPager = view.findViewById(R.id.dashboardSurveysPager)
        val appContext = requireContext().applicationContext
        username = ProfileCredentialsStore.getStoredCredentials(appContext)?.username
        statusStore = DashboardSurveyStatusStore(appContext, username)
        localSurveyRepository = DashboardLocalSurveyRepository(appContext)

        titleView.text = getString(R.string.dashboard_surveys_header_title)
        descriptionView.text = getString(R.string.dashboard_surveys_header_description)

        pagerAdapter = SurveysPagerAdapter(
            teamEmptyMessage = getString(R.string.dashboard_surveys_empty_team),
            adoptedEmptyMessage = getString(R.string.dashboard_surveys_empty_adopted),
            outboxEmptyMessage = getString(R.string.dashboard_surveys_outbox_empty),
            statusStore = statusStore,
            onSurveySelected = { document ->
                openSurveyWizard(document)
            },
            onSurveyDownloadRequested = { document ->
                downloadSurvey(document)
            },
            onOutboxSelected = { entry ->
                startActivity(
                    Intent(requireContext(), DashboardOutboxDetailActivity::class.java).apply {
                        putExtra(DashboardOutboxDetailActivity.EXTRA_OUTBOX_ID, entry.id)
                    },
                )
            },
        ) {
            updateTabBadges()
        }
        viewPager.adapter = pagerAdapter
        tabMediator = TabLayoutMediator(tabs, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.dashboard_surveys_tab_team)
                1 -> getString(R.string.dashboard_surveys_tab_adopted)
                else -> getString(R.string.dashboard_surveys_tab_outbox)
            }
        }.also { it.attach() }

        swipeRefresh.setOnRefreshListener {
            loadSurveys(isSwipeRefresh = true)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            initializeSession()
            loadSurveys()
        }
    }

    override fun onDestroyView() {
        tabMediator?.detach()
        tabMediator = null
        viewPager.adapter = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        if (hasLoadedOnce) {
            loadSurveys(isSwipeRefresh = false)
        }
    }

    fun isSurveyFeedFor(id: String, name: String): Boolean {
        return id == teamId && name == teamName
    }

    private suspend fun initializeSession() {
        val context = requireContext().applicationContext
        baseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        credentials = ProfileCredentialsStore.getStoredCredentials(context)
        baseUrl?.let { base ->
            val authService = AuthDependencies.provideAuthService(context, base)
            sessionCookie = authService.getStoredToken()
        }
    }

    private fun loadSurveys(isSwipeRefresh: Boolean = false) {
        val base = baseUrl
        val team = teamId
        val creds = credentials
        val offlineMode = (activity as? DashboardActivity)?.isOfflineModeActive() == true
        if (base.isNullOrBlank()) {
            showError(getString(R.string.dashboard_surveys_missing_server))
            swipeRefresh.isRefreshing = false
            return
        }
        if (team.isNullOrBlank()) {
            showError(getString(R.string.dashboard_surveys_missing_team))
            swipeRefresh.isRefreshing = false
            return
        }
        if (creds == null) {
            showError(getString(R.string.dashboard_surveys_missing_credentials))
            swipeRefresh.isRefreshing = false
            return
        }

        if (isSwipeRefresh) {
            swipeRefresh.isRefreshing = true
        } else {
            showLoading(true)
        }
        errorView.isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val documents = if (offlineMode) {
                    localSurveyRepository.getSavedSurveysForTeam(team)
                } else {
                    val result = repository.fetchTeamSurveys(base, creds, sessionCookie, team)
                    result.getOrElse {
                        val cached = localSurveyRepository.getSavedSurveysForTeam(team)
                        if (cached.isEmpty()) {
                            showError(getString(R.string.dashboard_surveys_error_loading))
                            swipeRefresh.isRefreshing = false
                            return@launch
                        }
                        cached
                    }
                }
                statusStore.ensureNewDefaults(documents.map { it.id })
                adoptedSurveys = documents.filter { !it.sourceSurveyId.isNullOrBlank() }
                teamSurveys = documents.filter { it.sourceSurveyId.isNullOrBlank() }
                if (offlineMode) {
                    completionCounts.clear()
                    documents.mapNotNull { it.id }.forEach { id -> completionCounts[id] = 0 }
                } else {
                    fetchCompletionCounts(base, team, documents)
                }
                savedSurveyIds = localSurveyRepository.getSavedSurveyIds()
                savedSurveyRevisions = localSurveyRepository.getSavedSurveyRevisions()
                outboxEntries = localSurveyRepository.getPendingForTeam(team)
                pagerAdapter.submit(
                    teamSurveys,
                    adoptedSurveys,
                    completionCounts,
                    savedSurveyIds,
                    savedSurveyRevisions,
                    outboxEntries,
                )
                updateTabBadges()
                showLoading(false)
                swipeRefresh.isRefreshing = false
            } finally {
                hasLoadedOnce = true
            }
        }
    }

    private suspend fun fetchCompletionCounts(
        base: String,
        team: String,
        documents: List<SurveyDocument>,
    ) {
        withContext(Dispatchers.IO) {
            completionCounts.clear()
            val ids = documents.mapNotNull { it.id }
            completionCounts.putAll(ids.associateWith { 0 })
            if (ids.isNotEmpty()) {
                val result = repository.fetchSurveyCompletionCountsBatched(base, credentials, sessionCookie, team, ids)
                completionCounts.putAll(result.getOrDefault(emptyMap()))
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        loadingView.isVisible = loading
        viewPager.isVisible = !loading
        if (!loading) {
            swipeRefresh.isRefreshing = false
        }
    }

    private fun showError(message: String) {
        showLoading(false)
        errorView.text = message
        viewPager.isVisible = false
        errorView.isVisible = true
        swipeRefresh.isRefreshing = false
    }

    private fun updateTabBadges() {
        val teamNew = teamSurveys.count { (statusStore.getStatus(it.id) ?: SurveyStatus.NEW) == SurveyStatus.NEW }
        val adoptedNew = adoptedSurveys.count { (statusStore.getStatus(it.id) ?: SurveyStatus.NEW) == SurveyStatus.NEW }
        tabs.getTabAt(0)?.let { tab ->
            if (teamNew > 0) {
                tab.ensureOffsetBadge(teamNew)
            } else {
                tab.removeBadge()
            }
        }
        tabs.getTabAt(1)?.let { tab ->
            if (adoptedNew > 0) {
                tab.ensureOffsetBadge(adoptedNew)
            } else {
                tab.removeBadge()
            }
        }
    }

    companion object {
        private const val ARG_TEAM_ID = "arg_team_id"
        private const val ARG_TEAM_NAME = "arg_team_name"

        fun newInstanceForTeam(teamId: String, teamName: String): DashboardTeamSurveysFragment {
            val fragment = DashboardTeamSurveysFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_TEAM_ID, teamId)
                putString(ARG_TEAM_NAME, teamName)
            }
            return fragment
        }
    }

    private fun openSurveyWizard(document: SurveyDocument) {
        val questions: List<SurveyQuestion> = document.questions.orEmpty()
        if (questions.isEmpty()) {
            view?.let { root ->
                android.widget.Toast.makeText(
                    root.context,
                    getString(R.string.dashboard_survey_wizard_empty_questions),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }

        document.id?.let { statusStore.markViewed(it) }
        startActivity(
            SurveyWizardActivity.newIntent(
                requireContext(),
                document,
                teamId,
                teamName,
            ),
        )
    }

    private fun downloadSurvey(document: SurveyDocument) {
        val surveyId = document.id
        if (surveyId.isNullOrBlank()) {
            view?.let { root ->
                android.widget.Toast.makeText(
                    root.context,
                    getString(R.string.dashboard_survey_download_error),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                localSurveyRepository.saveSurvey(document, teamId)
            }
            if (saved) {
                savedSurveyIds = savedSurveyIds + surveyId
                savedSurveyRevisions = savedSurveyRevisions + (surveyId to document.rev)
                pagerAdapter.updateSavedSurveys(savedSurveyIds, savedSurveyRevisions)
                view?.let { root ->
                    android.widget.Toast.makeText(
                        root.context,
                        getString(R.string.dashboard_survey_download_success),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            } else {
                view?.let { root ->
                    android.widget.Toast.makeText(
                        root.context,
                        getString(R.string.dashboard_survey_download_error),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }
}

private class SurveysPagerAdapter(
    private val teamEmptyMessage: String,
    private val adoptedEmptyMessage: String,
    private val outboxEmptyMessage: String,
    private val statusStore: DashboardSurveyStatusStore,
    private val onSurveySelected: (SurveyDocument) -> Unit,
    private val onSurveyDownloadRequested: (SurveyDocument) -> Unit,
    private val onOutboxSelected: (OutboxEntry) -> Unit,
    private val onStatusChanged: () -> Unit,
) : RecyclerView.Adapter<SurveysPagerAdapter.PageViewHolder>() {

    private val pages = listOf(Page.TEAM, Page.ADOPTED, Page.OUTBOX)
    private var teamItems: List<SurveyItemUiModel> = emptyList()
    private var adoptedItems: List<SurveyItemUiModel> = emptyList()
    private var outboxItems: List<OutboxEntry> = emptyList()

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PageViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_surveys_page, parent, false)
        return PageViewHolder(view)
    }

    override fun getItemCount(): Int = pages.size

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]
        when (page) {
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
        }
    }

    fun submit(
        teamSurveys: List<SurveyDocument>,
        adoptedSurveys: List<SurveyDocument>,
        completionCounts: Map<String, Int>,
        savedSurveys: Set<String>,
        savedRevisions: Map<String, String?>,
        outboxItems: List<OutboxEntry>,
    ) {
        teamItems = teamSurveys.map { it.toUiModel(completionCounts, savedSurveys, savedRevisions) }
        adoptedItems = adoptedSurveys.map { it.toUiModel(completionCounts, savedSurveys, savedRevisions) }
        this.outboxItems = outboxItems
        notifyItemChanged(0)
        notifyItemChanged(1)
        notifyItemChanged(2)
    }

    fun updateSavedSurveys(savedSurveys: Set<String>, savedRevisions: Map<String, String?>) {
        teamItems = teamItems.map { it.copy(isSaved = savedSurveys.contains(it.document.id.orEmpty()), savedRevision = savedRevisions[it.document.id.orEmpty()]) }
        adoptedItems = adoptedItems.map { it.copy(isSaved = savedSurveys.contains(it.document.id.orEmpty()), savedRevision = savedRevisions[it.document.id.orEmpty()]) }
        notifyItemChanged(0)
        notifyItemChanged(1)
    }

    private fun SurveyDocument.toUiModel(
        completionCounts: Map<String, Int>,
        savedSurveys: Set<String>,
        savedRevisions: Map<String, String?>
    ): SurveyItemUiModel {
        val documentId = id.orEmpty()
        return SurveyItemUiModel(
            document = this,
            completionCount = completionCounts[documentId] ?: 0,
            isSaved = savedSurveys.contains(documentId),
            savedRevision = savedRevisions[documentId],
        )
    }

    private enum class Page { TEAM, ADOPTED, OUTBOX }

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val emptyView: TextView = itemView.findViewById(R.id.dashboardSurveysListEmpty)
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.dashboardSurveysList)
        private var teamAdapter: SurveyItemsAdapter? = null
        private var outboxAdapter: SurveyOutboxAdapter? = null
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
    }
}

private data class SurveyItemUiModel(
    val document: SurveyDocument,
    val completionCount: Int,
    val isSaved: Boolean,
    val savedRevision: String?,
)

private class SurveyItemUiModelDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<SurveyItemUiModel>() {
    override fun areItemsTheSame(oldItem: SurveyItemUiModel, newItem: SurveyItemUiModel): Boolean = oldItem.document.id == newItem.document.id
    override fun areContentsTheSame(oldItem: SurveyItemUiModel, newItem: SurveyItemUiModel): Boolean = oldItem == newItem
}

private class SurveyItemsAdapter(
    private val statusStore: DashboardSurveyStatusStore,
    private val onStatusChanged: () -> Unit,
    private val onSurveySelected: (SurveyDocument) -> Unit,
    private val onDownloadRequested: (SurveyDocument) -> Unit,
) : androidx.recyclerview.widget.ListAdapter<SurveyItemUiModel, SurveyItemsAdapter.SurveyViewHolder>(SurveyItemUiModelDiffCallback()) {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SurveyViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
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
        private val statusIcon: android.widget.ImageView = itemView.findViewById(R.id.dashboardSurveyStatusIcon)
        private val metadata: TextView = itemView.findViewById(R.id.dashboardSurveyMetadata)
        private val markwon: Markwon = Markwon.builder(itemView.context).build()
        private val downloadButton: com.google.android.material.button.MaterialButton =
            itemView.findViewById(R.id.dashboardSurveyDownloadButton)

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

            val created = formatCreatedDate(document.createdDate)
            val completions = uiModel.completionCount
            metadata.text = itemView.context.getString(
                R.string.dashboard_surveys_metadata,
                created,
                completions,
            )
            metadata.isVisible = true

            val isSaved = uiModel.isSaved
            val savedRevision = uiModel.savedRevision
            val latestRevision = document.rev
            val isOutdated = isSaved && !latestRevision.isNullOrBlank() && savedRevision != null && savedRevision != latestRevision
            val context = itemView.context
            val buttonEnabled = !isSaved || isOutdated
            val iconRes = if (isOutdated) R.drawable.ic_sync_again_24 else R.drawable.ic_survey_download_24
            downloadButton.setIconResource(iconRes)
            downloadButton.isEnabled = buttonEnabled
            downloadButton.alpha = if (buttonEnabled) 1f else 0.7f
            downloadButton.contentDescription = context.getString(
                if (buttonEnabled) R.string.dashboard_survey_download else R.string.dashboard_survey_downloaded,
            )
            downloadButton.setOnClickListener {
                if (buttonEnabled) {
                    onDownloadRequested(document)
                }
            }

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

private class OutboxEntryDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<OutboxEntry>() {
    override fun areItemsTheSame(oldItem: OutboxEntry, newItem: OutboxEntry): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: OutboxEntry, newItem: OutboxEntry): Boolean = oldItem == newItem
}

private class SurveyOutboxAdapter(
    private val onOutboxSelected: (OutboxEntry) -> Unit,
) : androidx.recyclerview.widget.ListAdapter<OutboxEntry, SurveyOutboxAdapter.OutboxViewHolder>(OutboxEntryDiffCallback()) {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): OutboxViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
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
            title.text = entry.surveyName.orEmpty().ifBlank { itemView.context.getString(R.string.dashboard_outbox_unknown_survey) }
            subtitle.text = entry.teamName.orEmpty().ifBlank { itemView.context.getString(R.string.dashboard_outbox_unknown_team) }
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

private fun TabLayout.Tab.ensureOffsetBadge(count: Int) {
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
