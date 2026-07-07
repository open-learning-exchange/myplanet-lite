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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardOutboxDetailActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyOutboxStore.OutboxEntry
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyStatusStore
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
