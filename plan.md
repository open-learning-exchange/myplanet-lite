1. **Improve `DashboardLocalSurveyRepository.kt`**
    - Done.

2. **Migrate `SurveyWizardFragment.kt` and `DashboardCoursePageFragment.kt`**
    - `SurveyWizardFragment.kt`:
        - Use `replace_with_git_merge_diff` to replace `private var outboxStore: DashboardSurveyOutboxStore? = null` with `private val localSurveyRepository by lazy { DashboardLocalSurveyRepository(requireContext()) }` and remove `outboxStore` initialization.
        - Use `replace_with_git_merge_diff` to update `queueSubmissionForOutbox` to use `localSurveyRepository.saveSubmission`.
        - Use `replace_with_git_merge_diff` to update `flushPendingSurveySubmissions` to call `localSurveyRepository.flushPendingSurveyOutbox()`.
    - `DashboardCoursePageFragment.kt`:
        - Use `replace_with_git_merge_diff` to replace `private var surveyOutboxStore: DashboardSurveyOutboxStore? = null` with `private val localSurveyRepository by lazy { DashboardLocalSurveyRepository(requireContext()) }`.
        - Use `replace_with_git_merge_diff` to update `flushPendingSurveyOutbox` to call `localSurveyRepository.flushPendingSurveyOutbox()`.

3. **Migrate `DashboardTeamSurveysFragment.kt` and `CourseWizardActivity.kt`**
    - `DashboardTeamSurveysFragment.kt`:
        - Use `replace_with_git_merge_diff` to replace `offlineSurveyStore` and `outboxStore` with `localSurveyRepository: DashboardLocalSurveyRepository`.
        - Update usages to use `localSurveyRepository` (e.g. `getSavedSurveyIds`, `getSavedSurveysForTeam`, `saveSurvey`, `getPendingForTeam`).
    - `CourseWizardActivity.kt`:
        - Use `replace_with_git_merge_diff` to replace `surveyOutboxStore` with `localSurveyRepository: DashboardLocalSurveyRepository`.
        - Use `replace_with_git_merge_diff` to update `flushPendingExamSubmissions` to call `localSurveyRepository.flushPendingSurveyOutbox("exam")`.

4. **Verify functionality**
    - Verify code logic changes by reading files.
    - Run `./gradlew compileDebugKotlin` to verify the codebase compiles.
    - Run `./gradlew lint app:compileDebugKotlin` and `./gradlew lint app:testDebugUnitTest`. Wait, the reviewer asked to run the full test suite and linting tasks comprehensively. I'll just run `./gradlew testDebugUnitTest` and `./gradlew lint app:testDebugUnitTest`.

5. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**
    - Run `pre_commit_instructions` and follow steps before submitting.

6. **Submit changes**
    - Use the `submit` tool.
