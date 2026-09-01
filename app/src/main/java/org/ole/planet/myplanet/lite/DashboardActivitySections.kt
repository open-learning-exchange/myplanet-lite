/*
Author: Walfre López Prado
Email: loppra@plataformasinformaticas.com
Creation date: 2026-08-09
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
import org.ole.planet.myplanet.lite.util.AppNavigator
import org.ole.planet.myplanet.lite.util.NetworkUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

internal typealias DashboardSection = DashboardActivity.DashboardSection

internal fun DashboardActivity.setupSettingsDrawer(
        settingsDrawer: NavigationView,
        surveyTranslationMenuItem: MenuItem,
    ) {
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
                        LanguagePreferences.showLanguageSelectionDialog(this@setupSettingsDrawer)
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

                R.id.menu_settings_currency -> {
                    drawerLayout.closeDrawer(GravityCompat.END)
                    drawerLayout.post {
                        CurrencySettingsDialog.show(this@setupSettingsDrawer)
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

                else -> {
                    false
                }
            }
        }
    }


internal fun DashboardActivity.setupAppBarBehavior(
        appBar: AppBarLayout,
        topBar: View,
    ) {
        val hideOvershoot = resources.getDimensionPixelOffset(R.dimen.dashboard_top_bar_hide_overshoot)
        val tabsHideBuffer = resources.getDimensionPixelOffset(R.dimen.dashboard_tabs_hide_buffer)

        appBar.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
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
            },
        )
    }


internal fun DashboardActivity.setupProfileAndNetwork() {
        refreshProfileSummary()
        avatarUpdateListener =
            AvatarUpdateNotifier.register(
                AvatarUpdateNotifier.Listener {
                    refreshProfileSummary()
                },
            )

        isOfflineForcedByLaunch = intent?.getBooleanExtra(DashboardActivity.EXTRA_OFFLINE_MODE, false) == true
        isOfflineMode = isOfflineForcedByLaunch
        handleDeepLinkNavigation()
        val initialConnectivity = NetworkUtils.isDeviceOnline(this)
        applyConnectivityState(
            isConnected = initialConnectivity,
            showMessages = isOfflineMode || !initialConnectivity,
        )
        if (initialConnectivity || currentSection == DashboardSection.SURVEYS) {
            showSection(currentSection)
        }
        registerConnectivityCallback()
    }


internal fun DashboardActivity.showSection(section: DashboardSection) {
        when (section) {
            DashboardSection.HOME -> showHomeSection()
            DashboardSection.SURVEYS -> showSurveysSection()
            DashboardSection.COURSES -> showCoursesSection()
            DashboardSection.RESOURCES -> showResourcesSection()
            DashboardSection.TEAM_MEMBERS -> showTeamMembersSection()
        }
    }


internal fun DashboardActivity.performLogout() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val baseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
                val authService =
                    AuthDependencies.provideAuthService(
                        this@performLogout,
                        baseUrl ?: BuildConfig.PLANET_BASE_URL,
                    )
                runCatching { authService.logout() }
                runCatching {
                    UserProfileDatabase.getInstance(applicationContext).clearProfile()
                }
            }

            val intent =
                Intent(this@performLogout, MyPlanetLite::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            startActivity(intent)
            finish()
        }
    }


internal fun DashboardActivity.showHomeSection() {
        currentSection = DashboardSection.HOME
        surveysContainer.isVisible = false
        coursesContainer.isVisible = false
        resourcesContainer.isVisible = false
        teamMembersContainer.isVisible = false
        viewPager.isVisible = true
        tabLayout.isVisible = true
        updateBottomNavigationState()
    }


internal fun DashboardActivity.showSurveysSection() {
        currentSection = DashboardSection.SURVEYS
        viewPager.isVisible = false
        surveysContainer.isVisible = true
        coursesContainer.isVisible = false
        resourcesContainer.isVisible = false
        teamMembersContainer.isVisible = false
        tabLayout.isVisible = false

        val fragment = supportFragmentManager.findFragmentById(R.id.dashboardSurveysContainer)
        if (fragment !is DashboardSurveysFragment) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.dashboardSurveysContainer, DashboardSurveysFragment())
                .commit()
        }

        updateBottomNavigationState()
    }


internal fun DashboardActivity.showTeamMembersSection() {
        currentSection = DashboardSection.TEAM_MEMBERS
        viewPager.isVisible = false
        surveysContainer.isVisible = false
        coursesContainer.isVisible = false
        resourcesContainer.isVisible = false
        teamMembersContainer.isVisible = true
        tabLayout.isVisible = false

        val fragment = supportFragmentManager.findFragmentById(R.id.dashboardTeamMembersContainer)
        if (fragment !is DashboardTeamMembersFragment) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.dashboardTeamMembersContainer, DashboardTeamMembersFragment())
                .commit()
        }

        updateBottomNavigationState()
    }


internal fun DashboardActivity.showCoursesSection() {
        currentSection = DashboardSection.COURSES
        viewPager.isVisible = false
        surveysContainer.isVisible = false
        coursesContainer.isVisible = true
        resourcesContainer.isVisible = false
        teamMembersContainer.isVisible = false
        tabLayout.isVisible = false

        val fragment = supportFragmentManager.findFragmentById(R.id.dashboardCoursesContainer)
        if (fragment !is DashboardCoursesFragment) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.dashboardCoursesContainer, DashboardCoursesFragment())
                .commit()
        }

        updateBottomNavigationState()
    }


internal fun DashboardActivity.showResourcesSection() {
        currentSection = DashboardSection.RESOURCES
        viewPager.isVisible = false
        surveysContainer.isVisible = false
        coursesContainer.isVisible = false
        resourcesContainer.isVisible = true
        teamMembersContainer.isVisible = false
        tabLayout.isVisible = false

        val fragment = supportFragmentManager.findFragmentById(R.id.dashboardResourcesContainer)
        if (fragment !is DashboardResourcesFragment) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.dashboardResourcesContainer, DashboardResourcesFragment())
                .commit()
        }

        updateBottomNavigationState()
    }


internal fun DashboardActivity.updateBottomNavigationState() {
        val homeActive = currentSection == DashboardSection.HOME && !isOfflineMode
        val coursesActive = currentSection == DashboardSection.COURSES
        val resourcesActive = currentSection == DashboardSection.RESOURCES
        val teamActive = currentSection == DashboardSection.TEAM_MEMBERS && !isOfflineMode

        homeIcon.alpha =
            if (isOfflineMode) {
                0.3f
            } else if (homeActive) {
                1f
            } else {
                0.5f
            }
        surveysIcon.alpha = if (currentSection == DashboardSection.SURVEYS) 1f else 0.5f
        coursesIcon.alpha = if (coursesActive) 1f else 0.5f
        resourcesIcon.alpha = if (resourcesActive) 1f else 0.5f
        teamMembersIcon.alpha =
            if (isOfflineMode) {
                0.3f
            } else if (teamActive) {
                1f
            } else {
                0.5f
            }
        homeIcon.isEnabled = !isOfflineMode
        coursesIcon.isEnabled = true
        resourcesIcon.isEnabled = true
        teamMembersIcon.isEnabled = !isOfflineMode
        viewPager.isUserInputEnabled = !isOfflineMode
        tabLayout.isEnabled = !isOfflineMode
        tabLayout.alpha = if (isOfflineMode) 0.5f else 1f
        updateFabVisibility()
    }

internal fun DashboardActivity.updateFabVisibility() {
    val isVoicesTab = currentSection == DashboardSection.HOME && viewPager.currentItem == 0 && !isOfflineMode
    addVoiceFab.isVisible = isVoicesTab
}
