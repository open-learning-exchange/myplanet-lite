/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite.dashboard

import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.BaseActivity
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.survey.DashboardLocalSurveyRepository
import org.ole.planet.myplanet.lite.survey.ResendOutcome
import org.ole.planet.myplanet.lite.util.NetworkUtils
import org.ole.planet.myplanet.lite.util.DateUtils
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class DashboardOutboxDetailActivity : BaseActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var teamView: TextView
    private lateinit var savedAtView: TextView
    private lateinit var introView: TextView
    private lateinit var answersContainer: LinearLayout
    private lateinit var sendButton: MaterialButton
    private lateinit var deleteButton: MaterialButton
    private lateinit var emptyView: TextView
    private val outboxStore by lazy { DashboardSurveyOutboxStore.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_outbox_detail)
        toolbar = findViewById(R.id.outboxDetailToolbar)
        teamView = findViewById(R.id.outboxDetailTeam)
        savedAtView = findViewById(R.id.outboxDetailSavedAt)
        introView = findViewById(R.id.outboxDetailIntro)
        answersContainer = findViewById(R.id.outboxDetailAnswers)
        sendButton = findViewById(R.id.outboxDetailSendButton)
        deleteButton = findViewById(R.id.outboxDetailDeleteButton)
        emptyView = findViewById(R.id.outboxDetailEmptyAnswers)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        toolbar.setNavigationOnClickListener { finish() }

        val entryId = intent?.getLongExtra(EXTRA_OUTBOX_ID, -1L) ?: -1L
        if (entryId <= 0) {
            finish()
            return
        }

        loadEntry(entryId)
    }

    private fun loadEntry(entryId: Long) {
        lifecycleScope.launch {
            val entry = outboxStore.getEntry(entryId)
            if (entry == null) {
                Toast.makeText(this@DashboardOutboxDetailActivity, R.string.dashboard_outbox_missing_entry, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            toolbar.title = entry.surveyName.orEmpty().ifBlank { getString(R.string.dashboard_outbox_preview_title) }
            teamView.text = entry.teamName.orEmpty().ifBlank { getString(R.string.dashboard_outbox_unknown_team) }
            val formattedDate =
                runCatching {
                    DateFormat
                        .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                        .format(Date(entry.createdAt))
                }.getOrNull().orEmpty()
            savedAtView.text = getString(R.string.dashboard_outbox_saved_at, formattedDate)

            introView.text = getString(R.string.dashboard_outbox_detail_intro)
            answersContainer.removeAllViews()
            val questions =
                entry.submission.parent.questions
                    .orEmpty()
            val answers = entry.submission.answers
            val hasBasics = renderBasicDetails(entry)
            if ((questions.isEmpty() || answers.isEmpty()) && !hasBasics) {
                emptyView.isVisible = true
            } else {
                emptyView.isVisible = false
                questions.forEachIndexed { index, question ->
                    val answer = answers.getOrNull(index)
                    val itemView = layoutInflater.inflate(R.layout.item_outbox_answer, answersContainer, false)
                    val questionView = itemView.findViewById<TextView>(R.id.outboxAnswerQuestion)
                    val answerView = itemView.findViewById<TextView>(R.id.outboxAnswerValue)
                    val optionsContainer = itemView.findViewById<LinearLayout>(R.id.outboxAnswerOptions)
                    questionView.text = question.body.orEmpty().ifBlank { getString(R.string.dashboard_outbox_unknown_question, index + 1) }
                    renderAnswer(
                        question.type.orEmpty(),
                        question.choices.orEmpty(),
                        question.hasOtherOption,
                        answer?.value,
                        answerView,
                        optionsContainer,
                    )
                    answersContainer.addView(itemView)
                }
            }

            sendButton.setOnClickListener {
                attemptSend(entry)
            }
            sendButton.isEnabled = NetworkUtils.isDeviceOnline(this@DashboardOutboxDetailActivity)
            deleteButton.setOnClickListener {
                confirmDelete(entry)
            }
        }
    }

    private fun formatAnswerValue(value: Any?): String =
        when (value) {
            null -> getString(R.string.dashboard_outbox_no_answer)
            is String -> value.ifBlank { getString(R.string.dashboard_outbox_no_answer) }
            is Number, is Boolean -> value.toString()
            is List<*> -> value.joinToString(separator = ", ") { item -> item?.toString().orEmpty() }
            is Map<*, *> -> value.entries.joinToString(separator = ", ") { "${it.key}: ${it.value}" }
            else -> value.toString()
        }

    private fun renderBasicDetails(entry: DashboardSurveyOutboxStore.OutboxEntry): Boolean {
        val user = entry.submission.user
        val details =
            listOfNotNull(
                user.name.takeIf { it?.isNotBlank() == true },
                user.birthDate.takeIf { it?.isNotBlank() == true }?.let { rawDate ->
                    getString(
                        R.string.dashboard_outbox_birthdate_format,
                        DateUtils.formatBirthDate(rawDate, rawDate),
                    )
                },
                user.age?.takeIf { it > 0 }?.let { getString(R.string.dashboard_outbox_age_format, it) },
                user.gender.takeIf { it?.isNotBlank() == true }?.let { getString(R.string.dashboard_outbox_gender_format, it) },
                user.language.takeIf { it?.isNotBlank() == true }?.let { getString(R.string.dashboard_outbox_language_format, it) },
                user.level.takeIf { it?.isNotBlank() == true }?.let { getString(R.string.dashboard_outbox_level_format, it) },
                user.phoneNumber.takeIf { it?.isNotBlank() == true }?.let { getString(R.string.dashboard_outbox_phone_format, it) },
                user.email.takeIf { it?.isNotBlank() == true }?.let { getString(R.string.dashboard_outbox_email_format, it) },
            )
        if (details.isNotEmpty()) {
            val headerView = layoutInflater.inflate(R.layout.item_outbox_answer, answersContainer, false)
            headerView.findViewById<TextView>(R.id.outboxAnswerQuestion).text = getString(R.string.dashboard_outbox_participant_details)
            headerView.findViewById<TextView>(R.id.outboxAnswerValue).text = details.joinToString(separator = "\n")
            headerView.findViewById<LinearLayout>(R.id.outboxAnswerOptions).isVisible = false
            answersContainer.addView(headerView, 0)
            return true
        }
        return false
    }

    private fun renderAnswer(
        questionType: String,
        choices: List<DashboardSurveysRepository.SurveyChoice>,
        hasOtherOption: Boolean,
        rawValue: Any?,
        answerView: TextView,
        optionsContainer: LinearLayout,
    ) {
        val selectedOptions = parseSelectedOptions(rawValue)
        if (questionType == "select" || questionType == "selectMultiple") {
            answerView.isVisible = false
            optionsContainer.isVisible = true
            optionsContainer.removeAllViews()
            if (questionType == "select") {
                val group = RadioGroup(this).apply { isEnabled = false }
                choices.forEach { choice ->
                    val button =
                        RadioButton(this).apply {
                            text = choice.text.orEmpty()
                            isEnabled = false
                            isChecked = selectedOptions.any { it.matches(choice) }
                        }
                    group.addView(button)
                }
                if (hasOtherOption) {
                    val otherButton =
                        RadioButton(this).apply {
                            val other = selectedOptions.firstOrNull { it.isOther }
                            val otherLabel = getString(R.string.dashboard_survey_wizard_other_option)
                            val otherText = other?.text?.takeIf { !it.isNullOrBlank() }
                            text = if (otherText != null) "$otherLabel: $otherText" else otherLabel
                            isEnabled = false
                            isChecked = other != null
                            contentDescription = text
                        }
                    group.addView(otherButton)
                }
                optionsContainer.addView(group)
            } else {
                choices.forEach { choice ->
                    val checkbox =
                        CheckBox(this).apply {
                            text = choice.text.orEmpty()
                            isEnabled = false
                            isChecked = selectedOptions.any { it.matches(choice) }
                        }
                    optionsContainer.addView(checkbox)
                }
                if (hasOtherOption) {
                    val otherCheck =
                        CheckBox(this).apply {
                            val other = selectedOptions.firstOrNull { it.isOther }
                            val otherLabel = getString(R.string.dashboard_survey_wizard_other_option)
                            val otherText = other?.text?.takeIf { !it.isNullOrBlank() }
                            text = if (otherText != null) "$otherLabel: $otherText" else otherLabel
                            isEnabled = false
                            isChecked = other != null
                            contentDescription = text
                        }
                    optionsContainer.addView(otherCheck)
                }
            }
        } else {
            optionsContainer.isVisible = false
            answerView.isVisible = true
            answerView.text = formatAnswerValue(rawValue)
        }
    }

    private fun parseSelectedOptions(rawValue: Any?): List<SubmissionOptionValue> =
        when (rawValue) {
            null -> {
                emptyList()
            }

            is Map<*, *> -> {
                listOf(mapToOption(rawValue))
            }

            is List<*> -> {
                rawValue.mapNotNull { element ->
                    when (element) {
                        is Map<*, *> -> mapToOption(element)
                        is SubmissionOptionValue -> element
                        else -> null
                    }
                }
            }

            is SubmissionOptionValue -> {
                listOf(rawValue)
            }

            else -> {
                listOf(SubmissionOptionValue(id = null, text = rawValue.toString(), isOther = false))
            }
        }

    private fun mapToOption(map: Map<*, *>): SubmissionOptionValue {
        val id = map["id"] as? String
        val text = map["text"] as? String ?: id
        val isOther = (map["isOther"] as? Boolean) ?: false
        return SubmissionOptionValue(id = id, text = text, isOther = isOther)
    }

    private data class SubmissionOptionValue(
        val id: String?,
        val text: String?,
        val isOther: Boolean,
    ) {
        fun matches(choice: DashboardSurveysRepository.SurveyChoice): Boolean =
            (id != null && id == choice.id) ||
                (!text.isNullOrBlank() && text.equals(choice.text, ignoreCase = true))
    }

    companion object {
        const val EXTRA_OUTBOX_ID = "extra_outbox_id"
    }

    private fun attemptSend(entry: DashboardSurveyOutboxStore.OutboxEntry) {
        lifecycleScope.launch {
            val baseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
            val credentials = ProfileCredentialsStore.getStoredCredentials(applicationContext)
            val sessionCookie =
                baseUrl?.let {
                    AuthDependencies
                        .provideAuthService(
                            this@DashboardOutboxDetailActivity,
                            it,
                        ).getStoredToken()
                }
            val localSurveyRepository = DashboardLocalSurveyRepository(this@DashboardOutboxDetailActivity)
            val outcome = try {
                localSurveyRepository.resendOutboxEntry(
                    entry = entry,
                    baseUrl = baseUrl,
                    username = credentials?.username,
                    password = credentials?.password,
                    sessionCookie = sessionCookie
                )
            } finally {
                localSurveyRepository.close()
            }

            when (outcome) {
                ResendOutcome.SUCCESS -> {
                    Toast.makeText(this@DashboardOutboxDetailActivity, R.string.dashboard_outbox_send_success, Toast.LENGTH_SHORT).show()
                    finish()
                }
                ResendOutcome.REVISION_MISMATCH -> {
                    showRevMismatchDialog(entry)
                }
                ResendOutcome.REVISION_UNVERIFIABLE -> {
                    Toast.makeText(this@DashboardOutboxDetailActivity, R.string.dashboard_outbox_unable_to_verify_rev, Toast.LENGTH_SHORT).show()
                }
                ResendOutcome.MISSING_SERVER -> {
                    Toast.makeText(this@DashboardOutboxDetailActivity, R.string.dashboard_surveys_missing_server, Toast.LENGTH_SHORT).show()
                }
                ResendOutcome.OFFLINE -> {
                    Toast.makeText(this@DashboardOutboxDetailActivity, R.string.dashboard_outbox_offline_cannot_send, Toast.LENGTH_SHORT).show()
                }
                ResendOutcome.FAILED -> {
                    Toast.makeText(this@DashboardOutboxDetailActivity, R.string.dashboard_outbox_send_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    private fun confirmDelete(entry: DashboardSurveyOutboxStore.OutboxEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dashboard_outbox_delete_submission)
            .setMessage(R.string.dashboard_outbox_delete_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dashboard_outbox_delete_submission) { _, _ ->
                lifecycleScope.launch {
                    val deleted = outboxStore.deleteEntry(entry.id)
                    if (deleted) {
                        Toast
                            .makeText(
                                this@DashboardOutboxDetailActivity,
                                R.string.dashboard_outbox_deleted,
                                Toast.LENGTH_SHORT,
                            ).show()
                        finish()
                    } else {
                        Toast
                            .makeText(
                                this@DashboardOutboxDetailActivity,
                                R.string.dashboard_outbox_delete_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            }.show()
    }



    private fun showRevMismatchDialog(entry: DashboardSurveyOutboxStore.OutboxEntry) {
        androidx.appcompat.app.AlertDialog
            .Builder(this)
            .setTitle(R.string.dashboard_outbox_rev_mismatch_title)
            .setMessage(R.string.dashboard_outbox_rev_mismatch_message)
            .setPositiveButton(R.string.dashboard_outbox_delete_submission) { dialog, _ ->
                lifecycleScope.launch {
                    outboxStore.deleteEntry(entry.id)
                    Toast.makeText(this@DashboardOutboxDetailActivity, R.string.dashboard_outbox_deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }
                dialog.dismiss()
            }.setNegativeButton(R.string.dashboard_outbox_keep_submission) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
