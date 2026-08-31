/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.AvatarUpdateNotifier
import org.ole.planet.myplanet.lite.profile.ProfileActivity
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.ole.planet.myplanet.lite.dashboard.CreateVoiceActivity
import org.ole.planet.myplanet.lite.util.AppNavigator
import org.ole.planet.myplanet.lite.util.NetworkUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.ole.planet.myplanet.lite.util.enableDrag

class DashboardActivity : BaseActivity() {
    internal lateinit var avatarView: ImageView
    internal lateinit var drawerAvatar: ImageView
    internal lateinit var drawerName: TextView
    internal lateinit var drawerUsername: TextView
    internal lateinit var homeIcon: ImageView
    internal lateinit var surveysIcon: ImageView
    internal lateinit var coursesIcon: ImageView
    internal lateinit var resourcesIcon: ImageView
    internal lateinit var teamMembersIcon: ImageView
    internal lateinit var addVoiceFab: FloatingActionButton
    internal lateinit var surveysContainer: FrameLayout
    internal lateinit var coursesContainer: FrameLayout
    internal lateinit var resourcesContainer: FrameLayout
    internal lateinit var teamMembersContainer: FrameLayout
    internal lateinit var drawerLayout: DrawerLayout
    internal lateinit var viewPager: ViewPager2
    internal lateinit var tabLayout: TabLayout
    internal var avatarUpdateListener: AvatarUpdateNotifier.Listener? = null
    internal var deepLinkHandled = false
    internal var currentSection = DashboardSection.HOME
    internal val connectivityManager by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
    internal val serverPreferences by lazy {
        SecurePreferencesProvider.getServerPreferences(applicationContext)
    }
    internal var isHandlingSurveyTranslationToggle = false
    internal var surveyTranslationToggle: SwitchCompat? = null
    internal var isOfflineMode = false
    internal var isOfflineForcedByLaunch = false
    internal val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    applyConnectivityState(isConnected = true, showMessages = true)
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    applyConnectivityState(isConnected = false, showMessages = true)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        deepLinkHandled = savedInstanceState?.getBoolean(STATE_DEEP_LINK_HANDLED) ?: false
        val restoredSection = savedInstanceState?.getString(STATE_CURRENT_SECTION)
        currentSection = DashboardSection.entries.firstOrNull { it.name == restoredSection } ?: DashboardSection.HOME
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        setupViews()
        setupViewPagerAndTabs()
        setupBottomNavigation()
        setupProfileAndNetwork()
    }

    internal fun setupViews() {
        drawerLayout = findViewById(R.id.dashboardDrawerLayout)
        val root: View = findViewById(R.id.dashboardRoot)
        val appBar: AppBarLayout = findViewById(R.id.dashboardAppBar)
        val bottomNavigation: View = findViewById(R.id.dashboardBottomNavigation)
        val topBar: View = findViewById(R.id.dashboardTopBar)
        tabLayout = findViewById(R.id.dashboardTabs)
        viewPager = findViewById(R.id.dashboardViewPager)
        val settingsButton: ImageButton = findViewById(R.id.dashboardSettings)
        avatarView = findViewById(R.id.dashboardAvatar)
        val profileDrawer: NavigationView = findViewById(R.id.dashboardProfileDrawer)
        val settingsDrawer: NavigationView = findViewById(R.id.dashboardSettingsDrawer)
        val surveyTranslationMenuItem = settingsDrawer.menu.findItem(R.id.menu_settings_survey_translation)
        surveyTranslationToggle = surveyTranslationMenuItem.actionView?.findViewById(R.id.menuToggle)
        val drawerHeader = profileDrawer.getHeaderView(0)
        drawerAvatar = drawerHeader.findViewById(R.id.drawerProfileAvatar)
        drawerName = drawerHeader.findViewById(R.id.drawerProfileName)
        drawerUsername = drawerHeader.findViewById(R.id.drawerProfileUsername)
        surveysContainer = findViewById(R.id.dashboardSurveysContainer)
        coursesContainer = findViewById(R.id.dashboardCoursesContainer)
        resourcesContainer = findViewById(R.id.dashboardResourcesContainer)
        teamMembersContainer = findViewById(R.id.dashboardTeamMembersContainer)
        homeIcon = findViewById(R.id.dashboardHomeIcon)
        surveysIcon = findViewById(R.id.dashboardSurveysIcon)
        coursesIcon = findViewById(R.id.dashboardCoursesIcon)
        resourcesIcon = findViewById(R.id.dashboardResourcesIcon)
        teamMembersIcon = findViewById(R.id.dashboardTeamMembersIcon)
        addVoiceFab = findViewById(R.id.dashboardAddVoiceFab)
        addVoiceFab.setOnClickListener {
            val intent = Intent(this, CreateVoiceActivity::class.java)
            startActivity(intent)
        }
        addVoiceFab.enableDrag()

        setupWindowInsets(root, appBar, bottomNavigation)
        setupDrawers(settingsButton, profileDrawer, settingsDrawer, surveyTranslationMenuItem)
        setupAppBarBehavior(appBar, topBar)
    }

    internal fun setupWindowInsets(
        root: View,
        appBar: AppBarLayout,
        bottomNavigation: View,
    ) {
        val appBarInitialPadding = Padding(appBar.paddingLeft, appBar.paddingTop, appBar.paddingRight, appBar.paddingBottom)
        val bottomInitialPadding =
            Padding(
                bottomNavigation.paddingLeft,
                bottomNavigation.paddingTop,
                bottomNavigation.paddingRight,
                bottomNavigation.paddingBottom,
            )
        val viewPagerInitialPadding =
            Padding(
                viewPager.paddingLeft,
                viewPager.paddingTop,
                viewPager.paddingRight,
                viewPager.paddingBottom,
            )

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.updatePadding(
                left = appBarInitialPadding.left + systemBars.left,
                top = appBarInitialPadding.top + systemBars.top,
                right = appBarInitialPadding.right + systemBars.right,
                bottom = appBarInitialPadding.bottom,
            )
            bottomNavigation.updatePadding(
                left = bottomInitialPadding.left + systemBars.left,
                top = bottomInitialPadding.top,
                right = bottomInitialPadding.right + systemBars.right,
                bottom = bottomInitialPadding.bottom + systemBars.bottom,
            )
            viewPager.updatePadding(
                left = viewPagerInitialPadding.left + systemBars.left,
                top = viewPagerInitialPadding.top,
                right = viewPagerInitialPadding.right + systemBars.right,
                bottom = viewPagerInitialPadding.bottom,
            )
            insets
        }
    }

    internal fun setupViewPagerAndTabs() {
        viewPager.adapter = DashboardPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text =
                when (position) {
                    0 -> getString(R.string.dashboard_voices_title)
                    else -> getString(R.string.dashboard_teams_title)
                }
        }.attach()

        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateFabVisibility()
                }
            },
        )
    }

    internal fun setupBottomNavigation() {
        homeIcon.setOnClickListener {
            if (isOfflineMode) {
                showOfflineModeMessage()
                return@setOnClickListener
            }
            showHomeSection()
        }

        surveysIcon.setOnClickListener {
            showSurveysSection()
        }

        coursesIcon.setOnClickListener {
            showCoursesSection()
        }

        resourcesIcon.setOnClickListener {
            showResourcesSection()
        }

        teamMembersIcon.setOnClickListener {
            if (isOfflineMode) {
                showOfflineModeMessage()
                return@setOnClickListener
            }
            showTeamMembersSection()
        }

        updateBottomNavigationState()
    }

    internal fun setupDrawers(
        settingsButton: ImageButton,
        profileDrawer: NavigationView,
        settingsDrawer: NavigationView,
        surveyTranslationMenuItem: MenuItem,
    ) {
        settingsButton.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            drawerLayout.openDrawer(GravityCompat.END)
        }

        avatarView.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        setupProfileDrawer(profileDrawer)
        setupSettingsDrawer(settingsDrawer, surveyTranslationMenuItem)
    }

    internal fun setupProfileDrawer(profileDrawer: NavigationView) {
        profileDrawer.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_learning -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }

                R.id.menu_profile -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    drawerLayout.post {
                        startActivity(Intent(this, ProfileActivity::class.java))
                    }
                    true
                }

                R.id.menu_teams -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    drawerLayout.post {
                        startActivity(Intent(this, TeamsActivity::class.java))
                    }
                    true
                }

                R.id.menu_enterprises -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    drawerLayout.post {
                        startActivity(Intent(this, EnterprisesDashboard::class.java))
                    }
                    true
                }

                R.id.menu_privacy_policy -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    drawerLayout.post {
                        startActivity(Intent(this, PrivacyPolicyActivity::class.java))
                    }
                    true
                }

                R.id.menu_logout -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    performLogout()
                    true
                }

                else -> {
                    false
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AvatarUpdateNotifier.unregister(avatarUpdateListener)
        avatarUpdateListener = null
        connectivityManager?.unregisterNetworkCallback(networkCallback)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkHandled = false
        handleDeepLinkNavigation()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_DEEP_LINK_HANDLED, deepLinkHandled)
        outState.putString(STATE_CURRENT_SECTION, currentSection.name)
    }

    @androidx.annotation.VisibleForTesting
    internal fun isSurveyTranslationEnabled(): Boolean =
        serverPreferences.getBoolean(KEY_SURVEY_TRANSLATIONS_ENABLED, DEFAULT_SURVEY_TRANSLATION_ENABLED)

    internal fun isSurveyTranslationConsentAccepted(): Boolean = serverPreferences.getBoolean(KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, false)

    internal fun isSurveyTranslationActive(): Boolean = isSurveyTranslationEnabled() && isSurveyTranslationConsentAccepted()

    internal fun getVoicePageSize(): Int = getVoicePageSizePreference(this)

    internal fun normalizeVoicePageSize(value: Int): Int = VOICE_PAGE_SIZE_OPTIONS.firstOrNull { it == value } ?: DEFAULT_VOICE_PAGE_SIZE

    internal class DashboardPagerAdapter(
        activity: AppCompatActivity,
    ) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int) =
            when (position) {
                0 -> DashboardVoicesFragment()
                else -> DashboardTeamsFragment()
            }
    }

    internal data class Padding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    internal enum class DashboardSection {
        HOME,
        SURVEYS,
        COURSES,
        RESOURCES,
        TEAM_MEMBERS,
    }

    companion object {
        const val EXTRA_DEEP_LINK_POST_ID = "extra_deep_link_post_id"
        internal const val STATE_DEEP_LINK_HANDLED = "state_deep_link_handled"
        internal const val STATE_CURRENT_SECTION = "state_current_section"
        internal const val PREFS_NAME = "server_preferences"
        internal const val KEY_VOICE_PAGE_SIZE = "voice_page_size"

        @androidx.annotation.VisibleForTesting const val KEY_SURVEY_TRANSLATIONS_ENABLED = "survey_translations_enabled"

        @androidx.annotation.VisibleForTesting const val KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED = "survey_translation_consent_accepted"
        internal const val DEFAULT_VOICE_PAGE_SIZE = 20
        internal const val DEFAULT_SURVEY_TRANSLATION_ENABLED = true
        const val EXTRA_OFFLINE_MODE = "extra_offline_mode"
        val VOICE_PAGE_SIZE_OPTIONS = listOf(10, 20, 40)

        fun getVoicePageSizePreference(context: Context): Int {
            val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
            val stored = prefs.getInt(KEY_VOICE_PAGE_SIZE, DEFAULT_VOICE_PAGE_SIZE)
            return VOICE_PAGE_SIZE_OPTIONS.firstOrNull { it == stored } ?: DEFAULT_VOICE_PAGE_SIZE
        }

        fun isSurveyTranslationEnabled(context: Context): Boolean {
            val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
            return prefs.getBoolean(KEY_SURVEY_TRANSLATIONS_ENABLED, DEFAULT_SURVEY_TRANSLATION_ENABLED)
        }

        fun isSurveyTranslationConsentAccepted(context: Context): Boolean {
            val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
            return prefs.getBoolean(KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, false)
        }

        fun isSurveyTranslationActive(context: Context): Boolean =
            isSurveyTranslationEnabled(context) && isSurveyTranslationConsentAccepted(context)
    }

    fun isOfflineModeActive(): Boolean = isOfflineMode
}
