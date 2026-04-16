/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
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
import org.ole.planet.myplanet.lite.util.NetworkUtils
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

class DashboardActivity : BaseActivity() {

    private lateinit var avatarView: ImageView
    private lateinit var drawerAvatar: ImageView
    private lateinit var drawerName: TextView
    private lateinit var drawerUsername: TextView
    private lateinit var homeIcon: ImageView
    private lateinit var surveysIcon: ImageView
    private lateinit var coursesIcon: ImageView
    private lateinit var teamMembersIcon: ImageView
    private lateinit var surveysContainer: FrameLayout
    private lateinit var coursesContainer: FrameLayout
    private lateinit var teamMembersContainer: FrameLayout
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private var avatarUpdateListener: AvatarUpdateNotifier.Listener? = null
    private var deepLinkHandled = false
    private var currentSection = DashboardSection.HOME
    private val connectivityManager by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
    private val serverPreferences by lazy {
        SecurePreferencesProvider.getServerPreferences(applicationContext)
    }
    private var isHandlingSurveyTranslationToggle = false
    private var surveyTranslationToggle: SwitchCompat? = null
    private var isOfflineMode = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
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
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        setupViews()
        setupViewPagerAndTabs()
        setupBottomNavigation()
        setupProfileAndNetwork()
    }

    private fun setupViews() {
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
        teamMembersContainer = findViewById(R.id.dashboardTeamMembersContainer)
        homeIcon = findViewById(R.id.dashboardHomeIcon)
        surveysIcon = findViewById(R.id.dashboardSurveysIcon)
        coursesIcon = findViewById(R.id.dashboardCoursesIcon)
        teamMembersIcon = findViewById(R.id.dashboardTeamMembersIcon)

        setupWindowInsets(root, appBar, bottomNavigation)
        setupDrawers(settingsButton, profileDrawer, settingsDrawer, surveyTranslationMenuItem)
        setupAppBarBehavior(appBar, topBar)
    }

    private fun setupWindowInsets(root: View, appBar: AppBarLayout, bottomNavigation: View) {
        val appBarInitialPadding = Padding(appBar.paddingLeft, appBar.paddingTop, appBar.paddingRight, appBar.paddingBottom)
        val bottomInitialPadding = Padding(
            bottomNavigation.paddingLeft,
            bottomNavigation.paddingTop,
            bottomNavigation.paddingRight,
            bottomNavigation.paddingBottom
        )
        val viewPagerInitialPadding = Padding(
            viewPager.paddingLeft,
            viewPager.paddingTop,
            viewPager.paddingRight,
            viewPager.paddingBottom
        )

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.updatePadding(
                left = appBarInitialPadding.left + systemBars.left,
                top = appBarInitialPadding.top + systemBars.top,
                right = appBarInitialPadding.right + systemBars.right,
                bottom = appBarInitialPadding.bottom
            )
            bottomNavigation.updatePadding(
                left = bottomInitialPadding.left + systemBars.left,
                top = bottomInitialPadding.top,
                right = bottomInitialPadding.right + systemBars.right,
                bottom = bottomInitialPadding.bottom + systemBars.bottom
            )
            viewPager.updatePadding(
                left = viewPagerInitialPadding.left + systemBars.left,
                top = viewPagerInitialPadding.top,
                right = viewPagerInitialPadding.right + systemBars.right,
                bottom = viewPagerInitialPadding.bottom
            )
            insets
        }
    }

    private fun setupViewPagerAndTabs() {
        viewPager.adapter = DashboardPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.dashboard_voices_title)
                else -> getString(R.string.dashboard_teams_title)
            }
        }.attach()
    }

    private fun setupBottomNavigation() {
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

        teamMembersIcon.setOnClickListener {
            if (isOfflineMode) {
                showOfflineModeMessage()
                return@setOnClickListener
            }
            showTeamMembersSection()
        }

        updateBottomNavigationState()
    }

    private fun setupDrawers(
        settingsButton: ImageButton,
        profileDrawer: NavigationView,
        settingsDrawer: NavigationView,
        surveyTranslationMenuItem: MenuItem
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

        profileDrawer.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
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
                else -> false
            }
        }

        val initialSurveyTranslationEnabled = isSurveyTranslationActive()
        surveyTranslationMenuItem.isChecked = initialSurveyTranslationEnabled
        surveyTranslationToggle?.apply {
            isChecked = initialSurveyTranslationEnabled
            setOnCheckedChangeListener { _, isChecked ->
                if (isHandlingSurveyTranslationToggle) return@setOnCheckedChangeListener

                if (isChecked) {
                    isHandlingSurveyTranslationToggle = true
                    this.isChecked = false
                    surveyTranslationMenuItem.isChecked = false
                    isHandlingSurveyTranslationToggle = false
                    showSurveyTranslationConsentDialog(surveyTranslationMenuItem, requestedEnabled = true)
                } else {
                    applySurveyTranslationPreference(isChecked, surveyTranslationMenuItem, showToast = true)
                }
            }
        }

        settingsDrawer.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_settings_language -> {
                    drawerLayout.closeDrawer(GravityCompat.END)
                    drawerLayout.post {
                        LanguagePreferences.showLanguageSelectionDialog(this@DashboardActivity)
                    }
                    true
                }
                R.id.menu_settings_survey_translation -> {
                    drawerLayout.closeDrawer(GravityCompat.END)
                    val currentActive = surveyTranslationToggle?.isChecked ?: isSurveyTranslationActive()
                    val enableRequest = !currentActive
                    if (enableRequest) {
                        showSurveyTranslationConsentDialog(surveyTranslationMenuItem, requestedEnabled = true)
                    } else {
                        surveyTranslationToggle?.let { toggle ->
                            isHandlingSurveyTranslationToggle = true
                            toggle.isChecked = false
                            isHandlingSurveyTranslationToggle = false
                        }
                        applySurveyTranslationPreference(false, surveyTranslationMenuItem, showToast = true)
                    }
                    true
                }
                R.id.menu_settings_voice_batch_size -> {
                    drawerLayout.closeDrawer(GravityCompat.END)
                    drawerLayout.post {
                        showVoiceBatchSizeDialog()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupAppBarBehavior(appBar: AppBarLayout, topBar: View) {
        val hideOvershoot = resources.getDimensionPixelOffset(R.dimen.dashboard_top_bar_hide_overshoot)
        val tabsHideBuffer = resources.getDimensionPixelOffset(R.dimen.dashboard_tabs_hide_buffer)

        appBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            val tabsHeight = tabLayout.height
            if (tabsHeight == 0) {
                topBar.translationY = 0f
                return@OnOffsetChangedListener
            }
            val totalScroll = -verticalOffset
            val hideThreshold = tabsHeight + tabsHideBuffer
            val pinnedUntilTabs = totalScroll.coerceAtMost(hideThreshold)
            val extraScroll = (totalScroll - hideThreshold).coerceAtLeast(0)
            val overshoot = extraScroll.coerceAtMost(hideOvershoot)
            topBar.translationY = (pinnedUntilTabs - overshoot).toFloat()
        })
    }

    private fun setupProfileAndNetwork() {
        refreshProfileSummary()
        avatarUpdateListener = AvatarUpdateNotifier.register(AvatarUpdateNotifier.Listener {
            refreshProfileSummary()
        })

        handleDeepLinkNavigation()
        val initialConnectivity = NetworkUtils.isDeviceOnline(this)
        applyConnectivityState(isConnected = initialConnectivity, showMessages = intent?.getBooleanExtra(EXTRA_OFFLINE_MODE, false) == true || !initialConnectivity)
        registerConnectivityCallback()
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
    }

    private fun performLogout() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val baseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
                val authService = AuthDependencies.provideAuthService(
                    this@DashboardActivity,
                    baseUrl ?: BuildConfig.PLANET_BASE_URL
                )
                runCatching { authService.logout() }
                runCatching {
                    UserProfileDatabase.getInstance(applicationContext).clearProfile()
                }
            }

            val intent = Intent(this@DashboardActivity, MyPlanetLite::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun showHomeSection() {
        currentSection = DashboardSection.HOME
        surveysContainer.isVisible = false
        coursesContainer.isVisible = false
        teamMembersContainer.isVisible = false
        viewPager.isVisible = true
        tabLayout.isVisible = true
        updateBottomNavigationState()
    }

    private fun showSurveysSection() {
        currentSection = DashboardSection.SURVEYS
        viewPager.isVisible = false
        surveysContainer.isVisible = true
        coursesContainer.isVisible = false
        teamMembersContainer.isVisible = false
        tabLayout.isVisible = false

        val fragment = supportFragmentManager.findFragmentById(R.id.dashboardSurveysContainer)
        if (fragment !is DashboardSurveysFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.dashboardSurveysContainer, DashboardSurveysFragment())
                .commit()
        }

        updateBottomNavigationState()
    }

    private fun showTeamMembersSection() {
        currentSection = DashboardSection.TEAM_MEMBERS
        viewPager.isVisible = false
        surveysContainer.isVisible = false
        coursesContainer.isVisible = false
        teamMembersContainer.isVisible = true
        tabLayout.isVisible = false

        val fragment = supportFragmentManager.findFragmentById(R.id.dashboardTeamMembersContainer)
        if (fragment !is DashboardTeamMembersFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.dashboardTeamMembersContainer, DashboardTeamMembersFragment())
                .commit()
        }

        updateBottomNavigationState()
    }

    private fun showCoursesSection() {
        currentSection = DashboardSection.COURSES
        viewPager.isVisible = false
        surveysContainer.isVisible = false
        coursesContainer.isVisible = true
        teamMembersContainer.isVisible = false
        tabLayout.isVisible = false

        val fragment = supportFragmentManager.findFragmentById(R.id.dashboardCoursesContainer)
        if (fragment !is DashboardCoursesFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.dashboardCoursesContainer, DashboardCoursesFragment())
                .commit()
        }

        updateBottomNavigationState()
    }

    private fun updateBottomNavigationState() {
        val homeActive = currentSection == DashboardSection.HOME && !isOfflineMode
        val coursesActive = currentSection == DashboardSection.COURSES
        val teamActive = currentSection == DashboardSection.TEAM_MEMBERS && !isOfflineMode

        homeIcon.alpha = if (isOfflineMode) 0.3f else if (homeActive) 1f else 0.5f
        surveysIcon.alpha = if (currentSection == DashboardSection.SURVEYS) 1f else 0.5f
        coursesIcon.alpha = if (coursesActive) 1f else 0.5f
        teamMembersIcon.alpha = if (isOfflineMode) 0.3f else if (teamActive) 1f else 0.5f
        homeIcon.isEnabled = !isOfflineMode
        coursesIcon.isEnabled = true
        teamMembersIcon.isEnabled = !isOfflineMode
        viewPager.isUserInputEnabled = !isOfflineMode
        tabLayout.isEnabled = !isOfflineMode
        tabLayout.alpha = if (isOfflineMode) 0.5f else 1f
    }

    private fun refreshProfileSummary() {
        lifecycleScope.launch {
            val (profile, avatarBitmap) = withContext(Dispatchers.IO) {
                val profile = UserProfileDatabase.getInstance(applicationContext).getProfile()
                val avatarBytes = profile?.avatarImage
                val bitmap = if (avatarBytes != null && avatarBytes.isNotEmpty()) {
                    BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.size)
                } else {
                    null
                }
                profile to bitmap
            }
            val displayName = profile?.let {
                listOfNotNull(it.firstName, it.middleName, it.lastName)
                    .map { name -> name.trim() }
                    .filter { name -> name.isNotEmpty() }
                    .joinToString(" ")
                    .ifEmpty { it.username }
            } ?: getString(R.string.dashboard_profile_name_placeholder)

            val usernameLabel = profile?.let {
                getString(R.string.dashboard_profile_username_format, it.username)
            } ?: getString(R.string.dashboard_profile_username_placeholder)

            drawerName.text = displayName
            drawerUsername.text = usernameLabel

            if (avatarBitmap != null) {
                avatarView.setImageBitmap(avatarBitmap)
                drawerAvatar.setImageBitmap(avatarBitmap)
            } else {
                avatarView.setImageDrawable(null)
                drawerAvatar.setImageDrawable(null)
            }
        }
    }

    private fun handleDeepLinkNavigation() {
        if (deepLinkHandled) {
            return
        }
        val postId = intent?.getStringExtra(EXTRA_DEEP_LINK_POST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val detailIntent = Intent(this, DashboardPostDetailActivity::class.java).apply {
            putExtra(DashboardPostDetailActivity.EXTRA_POST_ID, postId)
        }
        startActivity(detailIntent)
        deepLinkHandled = true
    }

    private fun showVoiceBatchSizeDialog() {
        val options = VOICE_PAGE_SIZE_OPTIONS
        val optionLabels = options.map { getString(R.string.dashboard_settings_voice_batch_size_option, it) }
            .toTypedArray()
        val current = getVoicePageSize()
        val currentIndex = options.indexOf(current).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle(R.string.dashboard_settings_voice_batch_size_title)
            .setSingleChoiceItems(optionLabels, currentIndex) { dialog, which ->
                val selected = options[which]
                val changed = setVoicePageSize(selected)
                dialog.dismiss()
                if (changed) {
                    notifyVoicePageSizeChanged()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applySurveyTranslationPreference(
        enabled: Boolean,
        menuItem: MenuItem,
        showToast: Boolean
    ) {
        setSurveyTranslationEnabled(enabled)
        menuItem.isChecked = enabled
        if (showToast) {
            val messageRes = if (enabled) {
                R.string.dashboard_settings_survey_translation_enabled
            } else {
                R.string.dashboard_settings_survey_translation_disabled
            }
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isSurveyTranslationEnabled(): Boolean {
        return serverPreferences.getBoolean(KEY_SURVEY_TRANSLATIONS_ENABLED, DEFAULT_SURVEY_TRANSLATION_ENABLED)
    }

    private fun isSurveyTranslationConsentAccepted(): Boolean {
        return serverPreferences.getBoolean(KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, false)
    }

    private fun setSurveyTranslationConsentAccepted(accepted: Boolean) {
        serverPreferences.edit()
            .putBoolean(KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, accepted)
            .apply()
    }

    private fun isSurveyTranslationActive(): Boolean {
        return isSurveyTranslationEnabled() && isSurveyTranslationConsentAccepted()
    }

    private fun setSurveyTranslationEnabled(enabled: Boolean) {
        serverPreferences.edit()
            .putBoolean(KEY_SURVEY_TRANSLATIONS_ENABLED, enabled)
            .apply()
    }

    private fun getVoicePageSize(): Int {
        return getVoicePageSizePreference(this)
    }

    private fun setVoicePageSize(pageSize: Int): Boolean {
        val normalized = normalizeVoicePageSize(pageSize)
        val current = getVoicePageSize()
        if (current == normalized) {
            return false
        }
        serverPreferences.edit()
            .putInt(KEY_VOICE_PAGE_SIZE, normalized)
            .apply()
        return true
    }

    private fun notifyVoicePageSizeChanged() {
        val voicesFragment = supportFragmentManager.findFragmentByTag("f0") as? DashboardVoicesFragment
        voicesFragment?.onPageSizeChanged(getVoicePageSize())
    }

    private fun showSurveyTranslationConsentDialog(
        menuItem: MenuItem,
        requestedEnabled: Boolean = isSurveyTranslationEnabled()
    ) {
        if (!serverPreferences.contains(KEY_SURVEY_TRANSLATIONS_ENABLED)) {
            setSurveyTranslationEnabled(DEFAULT_SURVEY_TRANSLATION_ENABLED)
        }

        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_survey_translation_consent, null, false)
        val consentCheckBox = dialogView.findViewById<MaterialCheckBox>(R.id.surveyTranslationConsentCheckBox)
        val policyLink = dialogView.findViewById<TextView>(R.id.surveyTranslationPolicyLink)

        consentCheckBox.isChecked = requestedEnabled

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.login_survey_translation_consent_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dashboard_survey_translation_consent_accept) { alertDialog, _ ->
                val translationsEnabled = consentCheckBox.isChecked
                setSurveyTranslationConsentAccepted(true)
                applySurveyTranslationPreference(translationsEnabled, menuItem, showToast = true)
                surveyTranslationToggle?.let { toggle ->
                    isHandlingSurveyTranslationToggle = true
                    toggle.isChecked = translationsEnabled
                    isHandlingSurveyTranslationToggle = false
                }
                alertDialog.dismiss()
            }
            .setNegativeButton(R.string.dashboard_survey_translation_consent_cancel) { alertDialog, _ ->
                setSurveyTranslationConsentAccepted(false)
                applySurveyTranslationPreference(false, menuItem, showToast = false)
                surveyTranslationToggle?.let { toggle ->
                    isHandlingSurveyTranslationToggle = true
                    toggle.isChecked = false
                    isHandlingSurveyTranslationToggle = false
                }
                alertDialog.dismiss()
            }
            .create()

        policyLink.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        dialog.show()
    }

    private fun normalizeVoicePageSize(value: Int): Int {
        return VOICE_PAGE_SIZE_OPTIONS.firstOrNull { it == value } ?: DEFAULT_VOICE_PAGE_SIZE
    }

    private class DashboardPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int) = when (position) {
            0 -> DashboardVoicesFragment()
            else -> DashboardTeamsFragment()
        }
    }

    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private enum class DashboardSection {
        HOME,
        SURVEYS,
        COURSES,
        TEAM_MEMBERS
    }

    companion object {
        const val EXTRA_DEEP_LINK_POST_ID = "extra_deep_link_post_id"
        private const val STATE_DEEP_LINK_HANDLED = "state_deep_link_handled"
        private const val KEY_VOICE_PAGE_SIZE = "voice_page_size"
        private const val KEY_SURVEY_TRANSLATIONS_ENABLED = "survey_translations_enabled"
        private const val KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED = "survey_translation_consent_accepted"
        private const val DEFAULT_VOICE_PAGE_SIZE = 20
        private const val DEFAULT_SURVEY_TRANSLATION_ENABLED = true
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

        fun isSurveyTranslationActive(context: Context): Boolean {
            return isSurveyTranslationEnabled(context) && isSurveyTranslationConsentAccepted(context)
        }
    }



    private fun registerConnectivityCallback() {
        connectivityManager?.let { manager ->
            runCatching { manager.registerDefaultNetworkCallback(networkCallback) }
        }
    }



    private fun applyConnectivityState(isConnected: Boolean, showMessages: Boolean = false) {
        if (isConnected) {
            if (isOfflineMode) {
                isOfflineMode = false
                updateBottomNavigationState()
                if (showMessages) {
                    Toast.makeText(this, R.string.dashboard_online_mode_restored, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            if (!isOfflineMode && showMessages) {
                Toast.makeText(this, R.string.dashboard_offline_mode_only_surveys, Toast.LENGTH_SHORT).show()
            }
            isOfflineMode = true
            showSurveysSection()
            updateBottomNavigationState()
        }
    }

    private fun showOfflineModeMessage() {
        Toast.makeText(this, R.string.dashboard_offline_mode_only_surveys, Toast.LENGTH_SHORT).show()
    }
}
