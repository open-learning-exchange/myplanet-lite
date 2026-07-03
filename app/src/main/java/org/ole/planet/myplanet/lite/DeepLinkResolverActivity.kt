/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-01-20
 */

package org.ole.planet.myplanet.lite

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.dashboard.DashboardServerCatalog
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository
import org.ole.planet.myplanet.lite.dashboard.SharedBitmapDependencies
import org.ole.planet.myplanet.lite.util.IntentUtils

class DeepLinkResolverActivity : ComponentActivity() {
    private val surveysRepository = DashboardSurveysRepository(client = SharedBitmapDependencies.client)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        finish()
    }

    private fun openPost(postId: String) {
        val nextIntent = Intent(this, SplashScreen::class.java).apply {
            putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, postId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(nextIntent)
        finish()
    }

    private fun openSurvey(link: IntentUtils.DeepLinkSurvey) {
        lifecycleScope.launch {
            val storedBaseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
            val includeUserContext = IntentUtils.sameServer(storedBaseUrl, link.baseUrl)
            val result = surveysRepository.fetchPublicSurvey(
                baseUrl = link.baseUrl,
                teamId = link.teamId,
                surveyId = link.surveyId,
            )
            val publicSurvey = result.getOrNull()
            val document = publicSurvey?.survey
            if (document == null) {
                Toast.makeText(
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
            val nextIntent = SurveyWizardActivity.newIntent(
                context = this@DeepLinkResolverActivity,
                document = document,
                teamId = link.teamId,
                teamName = publicSurvey.team?.name,
                baseUrl = link.baseUrl,
                includeUserContext = includeUserContext,
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(nextIntent)
            finish()
        }
    }
}
