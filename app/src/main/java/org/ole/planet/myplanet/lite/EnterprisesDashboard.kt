package org.ole.planet.myplanet.lite

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.FrameLayout
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.AvatarUpdateNotifier
import org.ole.planet.myplanet.lite.profile.ProfileActivity
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.enableDrag

class EnterprisesDashboard : BaseActivity(), CreateTeamDialogFragment.Listener {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var avatarView: ImageView
    private lateinit var drawerAvatar: ImageView
    private lateinit var drawerName: TextView
    private lateinit var drawerUsername: TextView
    private lateinit var enterprisesContent: FrameLayout
    private lateinit var teamsContainer: FrameLayout
    private lateinit var voicesContainer: FrameLayout
    private lateinit var tasksContainer: FrameLayout
    private lateinit var financeContainer: FrameLayout
    private lateinit var enterprisesIcon: ImageView
    private lateinit var teamsIcon: ImageView
    private lateinit var voicesIcon: ImageView
    private lateinit var addVoiceFab: FloatingActionButton
    private lateinit var createEnterpriseFab: FloatingActionButton
    private lateinit var tasksIcon: ImageView
    private lateinit var financeIcon: ImageView
    private var currentSection = Section.ENTERPRISES
    private var avatarUpdateListener: AvatarUpdateNotifier.Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        enableEdgeToEdge()
        setContentView(R.layout.activity_enterprises_dashboard)

        drawerLayout = findViewById(R.id.enterprisesDashboardDrawerLayout)
        avatarView = findViewById(R.id.enterprisesDashboardAvatar)
        val settingsButton: ImageButton = findViewById(R.id.enterprisesDashboardSettings)
        val profileDrawer: NavigationView = findViewById(R.id.enterprisesProfileDrawer)
        val settingsDrawer: NavigationView = findViewById(R.id.enterprisesSettingsDrawer)
        val drawerHeader = profileDrawer.getHeaderView(0)
        drawerAvatar = drawerHeader.findViewById(R.id.drawerProfileAvatar)
        drawerName = drawerHeader.findViewById(R.id.drawerProfileName)
        drawerUsername = drawerHeader.findViewById(R.id.drawerProfileUsername)
        enterprisesContent = findViewById(R.id.enterprisesDashboardContent)
        teamsContainer = findViewById(R.id.enterprisesTeamsContainer)
        voicesContainer = findViewById(R.id.enterprisesVoicesContainer)
        tasksContainer = findViewById(R.id.enterprisesTasksContainer)
        financeContainer = findViewById(R.id.enterprisesFinanceContainer)
        enterprisesIcon = findViewById(R.id.enterprisesNavigationIcon)
        teamsIcon = findViewById(R.id.enterprisesTeamsNavigationIcon)
        voicesIcon = findViewById(R.id.enterprisesVoicesNavigationIcon)
        addVoiceFab = findViewById(R.id.enterprisesAddVoiceFab)
        createEnterpriseFab = findViewById(R.id.enterprisesCreateFab)
        tasksIcon = findViewById(R.id.enterprisesTasksNavigationIcon)
        financeIcon = findViewById(R.id.enterprisesFinanceNavigationIcon)

        currentSection = savedInstanceState?.getString(STATE_SECTION)
            ?.let { saved -> Section.entries.firstOrNull { it.name == saved } }
            ?: Section.ENTERPRISES

        avatarView.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        settingsButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            drawerLayout.openDrawer(GravityCompat.END)
        }
        setupProfileDrawer(profileDrawer)
        setupSettingsDrawer(settingsDrawer)
        enterprisesIcon.setOnClickListener { showEnterprisesSection() }
        teamsIcon.setOnClickListener { showTeamsSection() }
        voicesIcon.setOnClickListener { showVoicesSection() }
        addVoiceFab.setOnClickListener {
            (supportFragmentManager.findFragmentById(R.id.enterprisesVoicesContainer) as? DashboardVoicesFragment)
                ?.createVoice()
        }
        addVoiceFab.enableDrag()
        createEnterpriseFab.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag(CreateTeamDialogFragment.TAG) == null) {
                CreateTeamDialogFragment.newEnterpriseInstance()
                    .show(supportFragmentManager, CreateTeamDialogFragment.TAG)
            }
        }
        createEnterpriseFab.enableDrag()
        tasksIcon.setOnClickListener { showTasksSection() }
        financeIcon.setOnClickListener { showFinanceSection() }
        showSection(currentSection)
        refreshProfileSummary()
        avatarUpdateListener = AvatarUpdateNotifier.register(
            AvatarUpdateNotifier.Listener { refreshProfileSummary() },
        )
    }

    private fun setupProfileDrawer(profileDrawer: NavigationView) {
        profileDrawer.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_learning -> navigateTo(DashboardActivity::class.java, finishCurrent = true)
                R.id.menu_profile -> navigateTo(ProfileActivity::class.java)
                R.id.menu_teams -> navigateTo(TeamsActivity::class.java)
                R.id.menu_enterprises -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
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

    private fun setupSettingsDrawer(settingsDrawer: NavigationView) {
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
                        CurrencySettingsDialog.show(this) {
                            (supportFragmentManager.findFragmentById(R.id.enterprisesFinanceContainer)
                                as? DashboardEnterpriseFinanceFragment)?.refreshCurrencyFormat()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showSection(section: Section) {
        when (section) {
            Section.ENTERPRISES -> showEnterprisesSection()
            Section.MEMBERS -> showTeamsSection()
            Section.VOICES -> showVoicesSection()
            Section.TASKS -> showTasksSection()
            Section.FINANCE -> showFinanceSection()
        }
    }

    private fun showEnterprisesSection() {
        currentSection = Section.ENTERPRISES
        enterprisesContent.isVisible = true
        teamsContainer.isVisible = false
        voicesContainer.isVisible = false
        tasksContainer.isVisible = false
        financeContainer.isVisible = false
        addVoiceFab.isVisible = false
        createEnterpriseFab.isVisible = true
        if (supportFragmentManager.findFragmentById(R.id.enterprisesListContainer) !is TeamsFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.enterprisesListContainer, TeamsFragment.newEnterprisesInstance())
                .commit()
        }
        updateBottomNavigationState()
    }

    private fun showTeamsSection() {
        currentSection = Section.MEMBERS
        enterprisesContent.isVisible = false
        teamsContainer.isVisible = true
        voicesContainer.isVisible = false
        tasksContainer.isVisible = false
        financeContainer.isVisible = false
        addVoiceFab.isVisible = false
        createEnterpriseFab.isVisible = false
        if (supportFragmentManager.findFragmentById(R.id.enterprisesTeamsContainer) !is DashboardEnterpriseMembersFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.enterprisesTeamsContainer, DashboardEnterpriseMembersFragment())
                .commit()
        }
        updateBottomNavigationState()
    }

    private fun showTasksSection() {
        currentSection = Section.TASKS
        enterprisesContent.isVisible = false
        teamsContainer.isVisible = false
        voicesContainer.isVisible = false
        tasksContainer.isVisible = true
        financeContainer.isVisible = false
        addVoiceFab.isVisible = false
        createEnterpriseFab.isVisible = false
        if (supportFragmentManager.findFragmentById(R.id.enterprisesTasksContainer) !is DashboardEnterpriseTasksFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.enterprisesTasksContainer, DashboardEnterpriseTasksFragment())
                .commit()
        }
        updateBottomNavigationState()
    }

    private fun showVoicesSection() {
        currentSection = Section.VOICES
        enterprisesContent.isVisible = false
        teamsContainer.isVisible = false
        tasksContainer.isVisible = false
        financeContainer.isVisible = false
        voicesContainer.isVisible = true
        val context = applicationContext
        val enterpriseId = org.ole.planet.myplanet.lite.dashboard.DashboardEnterpriseSelectionPreferences
            .getSelectedEnterpriseId(context)
        addVoiceFab.isVisible = !enterpriseId.isNullOrBlank()
        createEnterpriseFab.isVisible = false
        val existing = supportFragmentManager.findFragmentById(R.id.enterprisesVoicesContainer)
            as? DashboardVoicesFragment
        if (!enterpriseId.isNullOrBlank() && existing?.isEnterpriseFeedFor(enterpriseId) != true) {
            val preferences = org.ole.planet.myplanet.lite.dashboard.DashboardEnterpriseSelectionPreferences
            supportFragmentManager.beginTransaction().replace(
                R.id.enterprisesVoicesContainer,
                DashboardVoicesFragment.newInstanceForEnterprise(
                    enterpriseId,
                    preferences.getSelectedEnterpriseName(context),
                    preferences.getSelectedEnterpriseType(context),
                    preferences.getSelectedEnterprisePlanetCode(context),
                ),
            ).commit()
        }
        updateBottomNavigationState()
    }

    private fun showFinanceSection() {
        currentSection = Section.FINANCE
        enterprisesContent.isVisible = false
        teamsContainer.isVisible = false
        voicesContainer.isVisible = false
        tasksContainer.isVisible = false
        financeContainer.isVisible = true
        addVoiceFab.isVisible = false
        createEnterpriseFab.isVisible = false
        if (supportFragmentManager.findFragmentById(R.id.enterprisesFinanceContainer)
            !is DashboardEnterpriseFinanceFragment
        ) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.enterprisesFinanceContainer, DashboardEnterpriseFinanceFragment())
                .commit()
        }
        updateBottomNavigationState()
    }

    private fun updateBottomNavigationState() {
        enterprisesIcon.alpha = if (currentSection == Section.ENTERPRISES) 1f else 0.5f
        teamsIcon.alpha = if (currentSection == Section.MEMBERS) 1f else 0.5f
        voicesIcon.alpha = if (currentSection == Section.VOICES) 1f else 0.5f
        tasksIcon.alpha = if (currentSection == Section.TASKS) 1f else 0.5f
        financeIcon.alpha = if (currentSection == Section.FINANCE) 1f else 0.5f
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
                    this@EnterprisesDashboard,
                    baseUrl ?: BuildConfig.PLANET_BASE_URL,
                )
                runCatching { authService.logout() }
                runCatching { UserProfileDatabase.getInstance(applicationContext).clearProfile() }
            }
            startActivity(
                Intent(this@EnterprisesDashboard, MyPlanetLite::class.java).apply {
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

    override fun onTeamCreated(teamId: String) {
        onEnterpriseSelectionChanged()
        val fragment = supportFragmentManager.findFragmentById(R.id.enterprisesListContainer) as? TeamsFragment
        fragment?.reloadTeams()
    }

    fun onEnterpriseSelectionChanged() {
        val dependentFragments = listOf(
            R.id.enterprisesVoicesContainer,
            R.id.enterprisesTeamsContainer,
            R.id.enterprisesTasksContainer,
            R.id.enterprisesFinanceContainer,
        ).mapNotNull(supportFragmentManager::findFragmentById)
        if (dependentFragments.isNotEmpty()) {
            supportFragmentManager.beginTransaction().apply {
                dependentFragments.forEach(::remove)
            }.commit()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SECTION, currentSection.name)
    }

    private enum class Section { ENTERPRISES, VOICES, MEMBERS, TASKS, FINANCE }

    private companion object {
        const val STATE_SECTION = "enterprises_dashboard_section"
    }
}
