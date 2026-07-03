package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardServerCatalog
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository
import org.ole.planet.myplanet.lite.util.IntentUtils

object AppNavigator {

    fun resolveDeepLink(
        context: Context,
        intent: Intent?,
        surveysRepository: DashboardSurveysRepository,
        scope: CoroutineScope,
        onFinished: () -> Unit
    ) {
        val postId = IntentUtils.extractDeepLinkPostId(intent)
        if (postId != null) {
            val nextIntent = Intent(context, SplashScreen::class.java).apply {
                putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, postId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(nextIntent)
            onFinished()
            return
        }

        val survey = IntentUtils.extractDeepLinkSurvey(intent)
        if (survey != null) {
            scope.launch {
                val storedBaseUrl = DashboardServerPreferences.getServerBaseUrl(context.applicationContext)
                val includeUserContext = IntentUtils.sameServer(storedBaseUrl, survey.baseUrl)
                val result = surveysRepository.fetchPublicSurvey(
                    baseUrl = survey.baseUrl,
                    teamId = survey.teamId,
                    surveyId = survey.surveyId,
                )
                val publicSurvey = result.getOrNull()
                val document = publicSurvey?.survey
                if (document == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.dashboard_surveys_error_loading),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onFinished()
                    return@launch
                }
                DashboardServerCatalog.addObservedServer(
                    context = context.applicationContext,
                    baseUrl = survey.baseUrl,
                    displayName = DashboardServerCatalog.displayNameFromBaseUrl(survey.baseUrl),
                )
                val nextIntent = SurveyWizardActivity.newIntent(
                    context = context,
                    document = document,
                    teamId = survey.teamId,
                    teamName = publicSurvey.team?.name,
                    baseUrl = survey.baseUrl,
                    includeUserContext = includeUserContext,
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(nextIntent)
                onFinished()
            }
            return
        }

        onFinished()
    }

    fun forwardDeepLinkIntent(sourceIntent: Intent?, targetIntent: Intent, delegatingToLogin: Boolean) {
        val forwardedPostId = sourceIntent?.getStringExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID)
            ?.takeIf { it.isNotBlank() }

        if (forwardedPostId != null) {
            targetIntent.putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, forwardedPostId)
        } else if (sourceIntent?.action == Intent.ACTION_VIEW && sourceIntent.data != null) {
            if (delegatingToLogin) {
                targetIntent.action = sourceIntent.action
                targetIntent.data = sourceIntent.data
            } else {
                val postId = IntentUtils.extractDeepLinkPostId(sourceIntent)
                if (postId != null) {
                    targetIntent.putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, postId)
                }
            }
        }
    }

    fun extractForwardedPostId(intent: Intent?): String? {
        return intent?.getStringExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: IntentUtils.extractDeepLinkPostId(intent)
    }

    fun applyForwardedPostId(targetIntent: Intent, postId: String?) {
        postId?.let {
            targetIntent.putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, it)
        }
    }

    fun handleDashboardDeepLink(context: Context, intent: Intent?): Boolean {
        val postId = intent?.getStringExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return false

        val detailIntent = Intent(context, DashboardPostDetailActivity::class.java).apply {
            putExtra(DashboardPostDetailActivity.EXTRA_POST_ID, postId)
        }
        context.startActivity(detailIntent)
        return true
    }
}
