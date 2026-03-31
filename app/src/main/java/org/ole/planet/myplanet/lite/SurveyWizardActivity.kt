/**
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

        document = intent.extras?.let { extras ->
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

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.surveyWizardFragmentContainer,
                    SurveyWizardFragment.newInstance(
                        survey,
                        intent.getStringExtra(EXTRA_TEAM_ID),
                        intent.getStringExtra(EXTRA_TEAM_NAME),
                        intent.getStringExtra(EXTRA_COURSE_ID),
                        intent.getBooleanExtra(EXTRA_IS_EXAM, false),
                    ),
                )
                .commit()
        }
    }

    companion object {
        const val EXTRA_DOCUMENT = "extra_document"
        private const val EXTRA_TEAM_ID = "extra_team_id"
        private const val EXTRA_TEAM_NAME = "extra_team_name"
        private const val EXTRA_COURSE_ID = "extra_course_id"
        private const val EXTRA_IS_EXAM = "extra_is_exam"

        fun newIntent(
            context: Context,
            document: SurveyDocument,
            teamId: String?,
            teamName: String?,
            courseId: String? = null,
            isExam: Boolean = false,
        ): Intent {
            return Intent(context, SurveyWizardActivity::class.java).apply {
                putExtra(EXTRA_DOCUMENT, document)
                putExtra(EXTRA_TEAM_ID, teamId)
                putExtra(EXTRA_TEAM_NAME, teamName)
                putExtra(EXTRA_COURSE_ID, courseId)
                putExtra(EXTRA_IS_EXAM, isExam)
            }
        }
    }
}
