package org.ole.planet.myplanet.lite.util

import android.content.Context
import android.content.Intent
import org.ole.planet.myplanet.lite.DashboardActivity
import org.ole.planet.myplanet.lite.MyPlanetLite
import org.ole.planet.myplanet.lite.SplashScreen
import org.ole.planet.myplanet.lite.SurveyWizardActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository

object AppNavigator {
    fun extractPostId(intent: Intent?): String? {
        val forwarded = intent?.getStringExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID)?.takeIf { it.isNotBlank() }
        if (forwarded != null) {
            return forwarded
        }
        return IntentUtils.extractDeepLinkPostId(intent)
    }

    fun navigateToDashboard(context: Context, postId: String?, isOfflineMode: Boolean = false) {
        val intent = Intent(context, DashboardActivity::class.java).apply {
            if (isOfflineMode) {
                putExtra(DashboardActivity.EXTRA_OFFLINE_MODE, true)
            }
            if (postId != null) {
                putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, postId)
            }
        }
        context.startActivity(intent)
    }

    fun navigateToLogin(context: Context, allowAutoLogin: Boolean = false, originalIntent: Intent? = null) {
        val intent = Intent(context, MyPlanetLite::class.java).apply {
            if (allowAutoLogin) {
                putExtra(MyPlanetLite.EXTRA_ALLOW_AUTO_LOGIN, true)
            }
            val forwardedPostId = originalIntent?.getStringExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID)?.takeIf { it.isNotBlank() }
            if (forwardedPostId != null) {
                putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, forwardedPostId)
            } else if (originalIntent?.action == Intent.ACTION_VIEW && originalIntent.data != null) {
                action = originalIntent.action
                data = originalIntent.data
            }
        }
        context.startActivity(intent)
    }

    fun navigateToSplash(context: Context, postId: String? = null) {
        val intent = Intent(context, SplashScreen::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (postId != null) {
                putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, postId)
            }
        }
        context.startActivity(intent)
    }

    fun navigateToPostDetail(context: Context, postId: String) {
        val intent = Intent(context, DashboardPostDetailActivity::class.java).apply {
            putExtra(DashboardPostDetailActivity.EXTRA_POST_ID, postId)
        }
        context.startActivity(intent)
    }

    fun navigateToSurvey(
        context: Context,
        document: DashboardSurveysRepository.SurveyDocument,
        teamId: String,
        teamName: String?,
        baseUrl: String,
        includeUserContext: Boolean
    ) {
        val intent = SurveyWizardActivity.newIntent(
            context = context,
            document = document,
            teamId = teamId,
            teamName = teamName,
            baseUrl = baseUrl,
            includeUserContext = includeUserContext
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
