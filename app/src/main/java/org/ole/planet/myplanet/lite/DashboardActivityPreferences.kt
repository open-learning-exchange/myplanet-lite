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

internal fun DashboardActivity.refreshProfileSummary() {
        lifecycleScope.launch {
            val (profile, avatarBitmap) =
                withContext(Dispatchers.IO) {
                    val profile = UserProfileDatabase.getInstance(applicationContext).getProfile()
                    val avatarBytes = profile?.avatarImage
                    val bitmap =
                        if (avatarBytes != null && avatarBytes.isNotEmpty()) {
                            BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.size)
                        } else {
                            null
                        }
                    profile to bitmap
                }
            val displayName =
                profile?.let {
                    listOfNotNull(it.firstName, it.middleName, it.lastName)
                        .map { name -> name.trim() }
                        .filter { name -> name.isNotEmpty() }
                        .joinToString(" ")
                        .ifEmpty { it.username }
                } ?: getString(R.string.dashboard_profile_name_placeholder)

            val usernameLabel =
                profile?.let {
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


internal fun DashboardActivity.handleDeepLinkNavigation() {
        if (deepLinkHandled) {
            return
        }
        val postId = intent?.getStringExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID)?.takeIf { it.isNotBlank() } ?: return
        AppNavigator.navigateToPostDetail(this, postId)
        deepLinkHandled = true
    }


internal fun DashboardActivity.showVoiceBatchSizeDialog() {
        val options = DashboardActivity.VOICE_PAGE_SIZE_OPTIONS
        val optionLabels =
            options
                .map { getString(R.string.dashboard_settings_voice_batch_size_option, it) }
                .toTypedArray()
        val current = getVoicePageSize()
        val currentIndex = options.indexOf(current).takeIf { it >= 0 } ?: 0

        AlertDialog
            .Builder(this)
            .setTitle(R.string.dashboard_settings_voice_batch_size_title)
            .setSingleChoiceItems(optionLabels, currentIndex) { dialog, which ->
                val selected = options[which]
                val changed = setVoicePageSize(selected)
                dialog.dismiss()
                if (changed) {
                    notifyVoicePageSizeChanged()
                }
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }


internal fun DashboardActivity.setVoicePageSize(pageSize: Int): Boolean {
        val normalized = normalizeVoicePageSize(pageSize)
        val current = getVoicePageSize()
        if (current == normalized) {
            return false
        }
        serverPreferences
            .edit()
            .putInt(DashboardActivity.KEY_VOICE_PAGE_SIZE, normalized)
            .apply()
        return true
    }


internal fun DashboardActivity.notifyVoicePageSizeChanged() {
        val voicesFragment = supportFragmentManager.findFragmentByTag("f0") as? DashboardVoicesFragment
        voicesFragment?.onPageSizeChanged(getVoicePageSize())
    }


internal fun DashboardActivity.registerConnectivityCallback() {
        connectivityManager?.let { manager ->
            runCatching { manager.registerDefaultNetworkCallback(networkCallback) }
        }
    }


internal fun DashboardActivity.applyConnectivityState(
        isConnected: Boolean,
        showMessages: Boolean = false,
    ) {
        if (isConnected && !isOfflineForcedByLaunch) {
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


internal fun DashboardActivity.showOfflineModeMessage() {
        Toast.makeText(this, R.string.dashboard_offline_mode_only_surveys, Toast.LENGTH_SHORT).show()
    }
