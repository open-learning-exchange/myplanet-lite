package org.ole.planet.myplanet.lite

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.ole.planet.myplanet.lite.util.enableDrag
import org.ole.planet.myplanet.lite.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.profile.AvatarUpdateNotifier
import org.ole.planet.myplanet.lite.profile.ProfileActivity
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase

class TeamsDashboard : BaseActivity(), CreateTeamDialogFragment.Listener {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var avatarView: ImageView
    private lateinit var drawerAvatar: ImageView
    private lateinit var drawerName: TextView
    private lateinit var drawerUsername: TextView
    private var avatarUpdateListener: AvatarUpdateNotifier.Listener? = null

    private var selectedNavigationIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        enableEdgeToEdge()
        setContentView(R.layout.activity_teams_dashboard)

        drawerLayout = findViewById(R.id.teamsDashboardDrawerLayout)
        avatarView = findViewById(R.id.teamsDashboardAvatar)
        val settingsButton: ImageButton = findViewById(R.id.teamsDashboardSettings)
        val profileDrawer: NavigationView = findViewById(R.id.teamsProfileDrawer)
        val settingsDrawer: NavigationView = findViewById(R.id.teamsSettingsDrawer)
        val surveyTranslationMenuItem = settingsDrawer.menu.findItem(R.id.menu_settings_survey_translation)
        val drawerHeader = profileDrawer.getHeaderView(0)
        drawerAvatar = drawerHeader.findViewById(R.id.drawerProfileAvatar)
        drawerName = drawerHeader.findViewById(R.id.drawerProfileName)
        drawerUsername = drawerHeader.findViewById(R.id.drawerProfileUsername)

        avatarView.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        settingsButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            drawerLayout.openDrawer(GravityCompat.END)
        }
        selectedNavigationIndex = savedInstanceState?.getInt(STATE_NAVIGATION_INDEX) ?: if (intent.getBooleanExtra(EXTRA_OPEN_SURVEYS, false)) 2 else 0
        val createTeamFab: FloatingActionButton = findViewById(R.id.teamsDashboardCreateFab)
        createTeamFab.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag(CreateTeamDialogFragment.TAG) == null) {
                CreateTeamDialogFragment().show(supportFragmentManager, CreateTeamDialogFragment.TAG)
            }
        }
        createTeamFab.enableDrag()
        val addVoiceFab: FloatingActionButton = findViewById(R.id.teamsDashboardAddVoiceFab)
        addVoiceFab.setOnClickListener {
            (supportFragmentManager.findFragmentById(R.id.teamsDashboardVoicesContainer) as? DashboardVoicesFragment)
                ?.createVoice()
        }
        addVoiceFab.enableDrag()
        setupBottomNavigation()
        setupProfileDrawer(profileDrawer)
        setupSettingsDrawer(settingsDrawer, surveyTranslationMenuItem)
        refreshProfileSummary()
        avatarUpdateListener = AvatarUpdateNotifier.register(
            AvatarUpdateNotifier.Listener { refreshProfileSummary() },
        )
    }

    private fun setupBottomNavigation() {
        val icons = listOf<ImageView>(
            findViewById(R.id.teamsDashboardMyTeamsIcon),
            findViewById(R.id.teamsDashboardHomeIcon),
            findViewById(R.id.teamsDashboardSurveysIcon),
            findViewById(R.id.teamsDashboardTeamMembersIcon),
            findViewById(R.id.teamsDashboardCoursesIcon),
            findViewById(R.id.teamsDashboardResourcesIcon),
        )
        fun updateSelection() {
            showSelectedSection()
            icons.forEachIndexed { index, icon ->
                icon.alpha = if (index == selectedNavigationIndex) 1f else 0.5f
                icon.isSelected = index == selectedNavigationIndex
            }
        }
        icons.forEachIndexed { index, icon ->
            icon.setOnClickListener {
                selectedNavigationIndex = index
                updateSelection()
            }
        }
        updateSelection()
    }

    private fun showSelectedSection() {
        val isMyTeams = selectedNavigationIndex == 0
        val isVoices = selectedNavigationIndex == 1
        val isSurveys = selectedNavigationIndex == 2
        val isMembers = selectedNavigationIndex == 3
        val isCourses = selectedNavigationIndex == 4
        findViewById<View>(R.id.teamsDashboardCoursesContainer).isVisible = isCourses
        var courses = supportFragmentManager.findFragmentById(R.id.teamsDashboardCoursesContainer)
        if (isCourses && courses !is DashboardCoursesFragment) {
            courses = DashboardCoursesFragment.newTeamsInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.teamsDashboardCoursesContainer, courses)
                .commitNow()
        }
        courses?.let {
            supportFragmentManager.beginTransaction()
                .setMaxLifecycle(it, if (isCourses) androidx.lifecycle.Lifecycle.State.RESUMED else androidx.lifecycle.Lifecycle.State.STARTED)
                .commitNow()
        }
        val isResources = selectedNavigationIndex == 5
        val resourcesContainer = findViewById<View>(R.id.teamsDashboardResourcesContainer)
        resourcesContainer.isVisible = isResources
        if (isResources) {
            val teamId = DashboardTeamSelectionPreferences.getSelectedTeamId(this)
            val existing = supportFragmentManager.findFragmentById(R.id.teamsDashboardResourcesContainer)
            if (existing is DashboardResourcesPageFragment) {
                existing.refreshContent(forceRefresh = resourcesContainer.tag != teamId)
            } else {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.teamsDashboardResourcesContainer, DashboardResourcesPageFragment.newInstance(isTeamResources = true))
                    .commitNow()
            }
            resourcesContainer.tag = teamId
        }
        findViewById<View>(R.id.teamsDashboardMembersContainer).isVisible = isMembers
        if (isMembers) {
            val existing = supportFragmentManager.findFragmentById(R.id.teamsDashboardMembersContainer)
            if (existing is DashboardTeamMembersFragment) {
                existing.refreshSelectionState()
            } else {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.teamsDashboardMembersContainer, DashboardTeamMembersFragment())
                    .commitNow()
            }
        }
        findViewById<View>(R.id.teamsDashboardSurveysContainer).isVisible = isSurveys
        if (isSurveys) {
            val existing = supportFragmentManager.findFragmentById(R.id.teamsDashboardSurveysContainer)
            if (existing is DashboardSurveysFragment) {
                existing.refreshSelectionState(forceReload = false)
            } else {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.teamsDashboardSurveysContainer, DashboardSurveysFragment())
                    .commitNow()
            }
        }
        findViewById<View>(R.id.teamsDashboardContent).isVisible = !isMyTeams && !isVoices && !isSurveys && !isMembers && !isResources && !isCourses
        findViewById<View>(R.id.teamsDashboardVoicesSection).isVisible = isVoices
        findViewById<View>(R.id.teamsDashboardAddVoiceFab).isVisible = false
        if (isVoices) showTeamVoices()
        findViewById<View>(R.id.teamsDashboardMyTeamsContainer).isVisible = isMyTeams
        findViewById<View>(R.id.teamsDashboardCreateFab).isVisible = isMyTeams
        if (isMyTeams && supportFragmentManager.findFragmentById(R.id.teamsDashboardMyTeamsContainer) !is TeamsFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.teamsDashboardMyTeamsContainer, TeamsFragment())
                .commit()
        }
    }

    private fun showTeamVoices() {
        val teamId = DashboardTeamSelectionPreferences.getSelectedTeamId(this)
        val teamName = DashboardTeamSelectionPreferences.getSelectedTeamName(this)
        val hasSelection = !teamId.isNullOrBlank() && !teamName.isNullOrBlank()
        findViewById<View>(R.id.teamsDashboardVoicesEmpty).isVisible = !hasSelection
        findViewById<View>(R.id.teamsDashboardVoicesContainer).isVisible = hasSelection
        findViewById<View>(R.id.teamsDashboardAddVoiceFab).isVisible = hasSelection
        val existing = supportFragmentManager.findFragmentById(R.id.teamsDashboardVoicesContainer)
        if (!teamId.isNullOrBlank() && !teamName.isNullOrBlank()) {
            if (existing !is DashboardVoicesFragment || !existing.isTeamFeedFor(teamId, teamName)) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.teamsDashboardVoicesContainer, DashboardVoicesFragment.newInstanceForTeam(teamId, teamName))
                    .commitNow()
            }
        } else if (existing != null) {
            supportFragmentManager.beginTransaction().remove(existing).commitNow()
        }
    }

    override fun onResume() {
        super.onResume()
        if (selectedNavigationIndex == 1) showTeamVoices()
    }

    override fun onTeamCreated(teamId: String) {
        (supportFragmentManager.findFragmentById(R.id.teamsDashboardMyTeamsContainer) as? TeamsFragment)
            ?.reloadTeams()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_NAVIGATION_INDEX, selectedNavigationIndex)
    }

    private fun setupProfileDrawer(profileDrawer: NavigationView) {
        profileDrawer.highlightDashboardDestination(R.id.menu_teams_dashboard)
        profileDrawer.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_learning -> navigateTo(DashboardActivity::class.java, finishCurrent = true)
                R.id.menu_profile -> navigateTo(ProfileActivity::class.java)
                R.id.menu_teams_dashboard -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.menu_enterprises -> navigateTo(EnterprisesDashboard::class.java, finishCurrent = true)
                R.id.menu_privacy_policy -> navigateTo(PrivacyPolicyActivity::class.java)
                R.id.menu_logout -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    performLogout()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSettingsDrawer(
        settingsDrawer: NavigationView,
        surveyTranslationMenuItem: MenuItem,
    ) {
        val surveyTranslationSettings =
            SurveyTranslationSettingsController(this, surveyTranslationMenuItem).also { it.bind() }
        settingsDrawer.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_settings_language -> {
                    drawerLayout.closeDrawer(GravityCompat.END)
                    drawerLayout.post { LanguagePreferences.showLanguageSelectionDialog(this) }
                    true
                }
                R.id.menu_settings_currency -> {
                    drawerLayout.closeDrawer(GravityCompat.END)
                    drawerLayout.post {
                        CurrencySettingsDialog.show(this)
                    }
                    true
                }
                R.id.menu_settings_survey_translation -> {
                    drawerLayout.closeDrawer(GravityCompat.END)
                    surveyTranslationSettings.handleMenuSelection()
                    true
                }
                else -> false
            }
        }
    }


    private fun navigateTo(destination: Class<*>, finishCurrent: Boolean = false): Boolean {
        drawerLayout.closeDrawer(GravityCompat.START)
        drawerLayout.post {
            startActivity(Intent(this, destination))
            if (finishCurrent) finish()
        }
        return true
    }

    private fun refreshProfileSummary() {
        lifecycleScope.launch {
            val (profile, avatarBitmap) = withContext(Dispatchers.IO) {
                val profile = UserProfileDatabase.getInstance(applicationContext).getProfile()
                val bytes = profile?.avatarImage
                val bitmap = bytes?.takeIf { it.isNotEmpty() }?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }
                profile to bitmap
            }
            drawerName.text = profile?.let {
                listOfNotNull(it.firstName, it.middleName, it.lastName)
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .joinToString(" ")
                    .ifEmpty { it.username }
            } ?: getString(R.string.dashboard_profile_name_placeholder)
            drawerUsername.text = profile?.let {
                getString(R.string.dashboard_profile_username_format, it.username)
            } ?: getString(R.string.dashboard_profile_username_placeholder)
            avatarView.setImageBitmap(avatarBitmap)
            drawerAvatar.setImageBitmap(avatarBitmap)
        }
    }

    private fun performLogout() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val baseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
                val authService = AuthDependencies.provideAuthService(
                    this@TeamsDashboard,
                    baseUrl ?: BuildConfig.PLANET_BASE_URL,
                )
                runCatching { authService.logout() }
                runCatching { UserProfileDatabase.getInstance(applicationContext).clearProfile() }
            }
            startActivity(
                Intent(this@TeamsDashboard, MyPlanetLite::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
            )
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AvatarUpdateNotifier.unregister(avatarUpdateListener)
        avatarUpdateListener = null
    }

    fun isOfflineModeActive(): Boolean =
        intent.getBooleanExtra(DashboardActivity.EXTRA_OFFLINE_MODE, false) || !NetworkUtils.isDeviceOnline(this)

    companion object {
        const val EXTRA_OPEN_SURVEYS = "teams_open_surveys"
        const val STATE_NAVIGATION_INDEX = "teams_dashboard_navigation_index"
    }
}
