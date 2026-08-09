/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.os.BundleCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument

class SurveyWizardActivity : AppCompatActivity() {
    private var document: SurveyDocument? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(R.layout.activity_survey_wizard)

        document =
            intent.extras?.let { extras ->
                BundleCompat.getSerializable(extras, EXTRA_DOCUMENT, SurveyDocument::class.java)
            }

        val survey = document
        if (survey == null) {
            finish()
            return
        }

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        val toolbar: MaterialToolbar = findViewById(R.id.surveyWizardToolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val wizard = supportFragmentManager.findFragmentById(R.id.surveyWizardFragmentContainer) as? SurveyWizardFragment
                    wizard?.handleExitRequest { finish() } ?: finish()
                }
            },
        )

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.surveyWizardFragmentContainer,
                    SurveyWizardFragment.newInstance(
                        survey,
                        intent.getStringExtra(EXTRA_TEAM_ID),
                        intent.getStringExtra(EXTRA_TEAM_NAME),
                        intent.getStringExtra(EXTRA_COURSE_ID),
                        intent.getBooleanExtra(EXTRA_IS_EXAM, false),
                        intent.getStringExtra(EXTRA_BASE_URL),
                        intent.getBooleanExtra(EXTRA_INCLUDE_USER_CONTEXT, true),
                        intent.getStringExtra(EXTRA_DRAFT_KEY),
                        intent.getBooleanExtra(EXTRA_OFFLINE_MODE, false),
                    ),
                ).commit()
        }
    }

    companion object {
        const val EXTRA_DOCUMENT = "extra_document"
        private const val EXTRA_TEAM_ID = "extra_team_id"
        private const val EXTRA_TEAM_NAME = "extra_team_name"
        private const val EXTRA_COURSE_ID = "extra_course_id"
        private const val EXTRA_IS_EXAM = "extra_is_exam"
        private const val EXTRA_BASE_URL = "extra_base_url"
        private const val EXTRA_INCLUDE_USER_CONTEXT = "extra_include_user_context"
        private const val EXTRA_DRAFT_KEY = "extra_draft_key"
        private const val EXTRA_OFFLINE_MODE = "extra_offline_mode"

        fun newIntent(
            context: Context,
            document: SurveyDocument,
            teamId: String?,
            teamName: String?,
            courseId: String? = null,
            isExam: Boolean = false,
            baseUrl: String? = null,
            includeUserContext: Boolean = true,
            draftKey: String? = null,
            offlineMode: Boolean = false,
        ): Intent =
            Intent(context, SurveyWizardActivity::class.java).apply {
                putExtra(EXTRA_DOCUMENT, document)
                putExtra(EXTRA_TEAM_ID, teamId)
                putExtra(EXTRA_TEAM_NAME, teamName)
                putExtra(EXTRA_COURSE_ID, courseId)
                putExtra(EXTRA_IS_EXAM, isExam)
                putExtra(EXTRA_BASE_URL, baseUrl)
                putExtra(EXTRA_INCLUDE_USER_CONTEXT, includeUserContext)
                putExtra(EXTRA_DRAFT_KEY, draftKey)
                putExtra(EXTRA_OFFLINE_MODE, offlineMode)
            }
    }
}
