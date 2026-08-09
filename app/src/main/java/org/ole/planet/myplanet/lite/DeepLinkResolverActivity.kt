/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-01-20
 */

package org.ole.planet.myplanet.lite

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.dashboard.DashboardServerCatalog
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepositoryProvider
import org.ole.planet.myplanet.lite.util.AppNavigator
import org.ole.planet.myplanet.lite.util.IntentUtils

class DeepLinkResolverActivity : ComponentActivity() {
    private val surveysRepository = DashboardSurveysRepositoryProvider.getRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (maybeShowAppChooser()) return

        resolveDeepLink()
    }

    private fun maybeShowAppChooser(): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        if (intent.getBooleanExtra(EXTRA_SKIP_APP_CHOOSER, false)) return false
        val data = intent.data ?: return false
        if (data.scheme != "http" && data.scheme != "https") return false
        val mainAppInfo = try {
            packageManager.getApplicationInfo(MAIN_APP_PACKAGE, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
        val prefs = getSharedPreferences(CHOOSER_PREFS_NAME, MODE_PRIVATE)
        when (prefs.getString(KEY_PREFERRED_APP, null)) {
            packageName -> return false
            MAIN_APP_PACKAGE -> {
                forwardToMainApp(data)
                return true
            }
        }
        val mainLabel = mainAppInfo.loadLabel(packageManager).toString()
        val ownLabel = applicationInfo.loadLabel(packageManager).toString()
        var selected = -1
        val applyChoice = { remember: Boolean ->
            if (remember) {
                val target = if (selected == 0) MAIN_APP_PACKAGE else packageName
                prefs.edit().putString(KEY_PREFERRED_APP, target).apply()
            }
            if (selected == 0) forwardToMainApp(data) else resolveDeepLink()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.open_link_with)
            .setSingleChoiceItems(arrayOf(mainLabel, ownLabel), -1) { d, which ->
                selected = which
                (d as? AlertDialog)?.let {
                    it.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    it.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                }
            }
            .setPositiveButton(R.string.always) { _, _ -> applyChoice(true) }
            .setNegativeButton(R.string.just_once) { _, _ -> applyChoice(false) }
            .setOnCancelListener { finish() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
        }
        dialog.show()
        return true
    }

    private fun forwardToMainApp(data: Uri) {
        val forward = Intent(Intent.ACTION_VIEW, data)
            .setPackage(MAIN_APP_PACKAGE)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .putExtra(EXTRA_SKIP_APP_CHOOSER, true)
        try {
            startActivity(forward)
            finish()
        } catch (e: ActivityNotFoundException) {
            resolveDeepLink()
        }
    }

    private fun resolveDeepLink() {
        val postId = IntentUtils.extractDeepLinkPostId(intent)
        if (postId != null) {
            openPost(postId)
            return
        }

        val survey = IntentUtils.extractDeepLinkSurvey(intent)
        if (survey != null) {
            openSurvey(survey)
            return
        }

        if (IntentUtils.isAppSectionDeepLink(intent)) {
            openApp()
            return
        }

        finish()
    }

    private fun openApp() {
        AppNavigator.navigateToSplash(this)
        finish()
    }

    private fun openPost(postId: String) {
        AppNavigator.navigateToSplash(this, postId)
        finish()
    }

    private fun openSurvey(link: IntentUtils.DeepLinkSurvey) {
        lifecycleScope.launch {
            val storedBaseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
            val includeUserContext = IntentUtils.sameServer(storedBaseUrl, link.baseUrl)
            val result =
                surveysRepository.fetchPublicSurvey(
                    baseUrl = link.baseUrl,
                    teamId = link.teamId,
                    surveyId = link.surveyId,
                )
            val publicSurvey = result.getOrNull()
            val document = publicSurvey?.survey
            if (document == null) {
                Toast
                    .makeText(
                        this@DeepLinkResolverActivity,
                        getString(R.string.dashboard_surveys_error_loading),
                        Toast.LENGTH_SHORT,
                    ).show()
                finish()
                return@launch
            }
            DashboardServerCatalog.addObservedServer(
                context = applicationContext,
                baseUrl = link.baseUrl,
                displayName = DashboardServerCatalog.displayNameFromBaseUrl(link.baseUrl),
            )
            AppNavigator.navigateToSurvey(
                context = this@DeepLinkResolverActivity,
                document = document,
                teamId = link.teamId,
                teamName = publicSurvey.team?.name,
                baseUrl = link.baseUrl,
                includeUserContext = includeUserContext
            )
            finish()
        }
    }

    companion object {
        private const val MAIN_APP_PACKAGE = "org.ole.planet.myplanet"
        private const val EXTRA_SKIP_APP_CHOOSER = "org.ole.planet.myplanet.extra.SKIP_APP_CHOOSER"
        private const val CHOOSER_PREFS_NAME = "deep_link_preferences"
        private const val KEY_PREFERRED_APP = "preferred_app"
    }
}
