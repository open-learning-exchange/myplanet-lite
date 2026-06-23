/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-06-10
 */

package org.ole.planet.myplanet.lite

import android.graphics.Rect
import android.text.InputFilter
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.ole.planet.myplanet.lite.util.BirthDateConstraints

internal fun SignupActivity.initializeViews() {
    scrollView = findViewById(R.id.signupScroll)
    imeSpacer = findViewById(R.id.signupImeSpacer)
    backIcon = findViewById(R.id.signupBackIcon)
    backButton = findViewById(R.id.signupBackButton)
    nextButton = findViewById(R.id.signupNextButton)
    titleView = findViewById(R.id.signupTitle)
    subtitleView = findViewById(R.id.signupSubtitle)

    usernameLayout = findViewById(R.id.signupUsernameInputLayout)
    usernameInput = findViewById(R.id.signupUsernameInput)
    firstNameLayout = findViewById(R.id.signupFirstNameInputLayout)
    firstNameInput = findViewById(R.id.signupFirstNameInput)
    middleNameInput = findViewById(R.id.signupMiddleNameInput)
    lastNameLayout = findViewById(R.id.signupLastNameInputLayout)
    lastNameInput = findViewById(R.id.signupLastNameInput)
    birthDateLayout = findViewById(R.id.signupBirthDateInputLayout)
    birthDateInput = findViewById(R.id.signupBirthDateInput)
    genderGroup = findViewById(R.id.signupGenderGroup)
    genderErrorView = findViewById(R.id.signupGenderError)
    emailLayout = findViewById(R.id.signupEmailInputLayout)
    emailInput = findViewById(R.id.signupEmailInput)
    phoneLayout = findViewById(R.id.signupPhoneInputLayout)
    phoneInput = findViewById(R.id.signupPhoneInput)
    passwordLayout = findViewById(R.id.signupPasswordInputLayout)
    passwordInput = findViewById(R.id.signupPasswordInput)
    confirmPasswordLayout = findViewById(R.id.signupConfirmPasswordInputLayout)
    confirmPasswordInput = findViewById(R.id.signupConfirmPasswordInput)
    languageLayout = findViewById(R.id.signupLanguageInputLayout)
    languageInput = findViewById(R.id.signupLanguageInput)
    levelLayout = findViewById(R.id.signupLevelInputLayout)
    levelInput = findViewById(R.id.signupLevelInput)
    autoLoginCheck = findViewById(R.id.signupAutoLoginCheck)

    setupLanguageAndLevelInputs()
}

internal fun SignupActivity.setupLanguageAndLevelInputs() {
    languageInput.keyListener = null
    languageInput.setTextIsSelectable(false)
    languageInput.isLongClickable = false
    languageInput.setOnClickListener {
        if (!languageInput.isPopupShowing) {
            languageInput.showDropDown()
        }
    }

    levelInput.keyListener = null
    levelInput.setTextIsSelectable(false)
    levelInput.isLongClickable = false
    levelInput.setOnClickListener {
        if (!levelInput.isPopupShowing) {
            levelInput.showDropDown()
        }
    }
}

internal fun SignupActivity.setupWindowInsets() {
    val root = findViewById<View>(R.id.signupRoot)
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        insets
    }

    val originalPaddingStart = scrollView.paddingStart
    val originalPaddingTop = scrollView.paddingTop
    val originalPaddingEnd = scrollView.paddingEnd
    val originalPaddingBottom = scrollView.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val bottomPadding = originalPaddingBottom + systemInsets.bottom

        imeSpacer.updateLayoutParams<android.widget.LinearLayout.LayoutParams> {
            height = imeInsets.bottom
        }

        imeInsetBottom = imeInsets.bottom

        v.setPaddingRelative(
            originalPaddingStart,
            originalPaddingTop,
            originalPaddingEnd,
            bottomPadding
        )

        currentFocus?.let { focused ->
            ensureVisible(scrollView, focused)
        }
        insets
    }

    ViewCompat.requestApplyInsets(scrollView)
}

internal fun SignupActivity.setupStepViews() {
    val usernameStep = findViewById<View>(R.id.signupStepUsername)
    val namesStep = findViewById<View>(R.id.signupStepNames)
    val birthDateStep = findViewById<View>(R.id.signupStepBirthDate)
    val genderStep = findViewById<View>(R.id.signupStepGender)
    val contactStep = findViewById<View>(R.id.signupStepContact)
    val passwordStep = findViewById<View>(R.id.signupStepPassword)
    val languageStep = findViewById<View>(R.id.signupStepLanguage)
    val licenseStep = findViewById<View>(R.id.signupStepLicense)

    stepViews = mapOf(
        SignupStep.USERNAME to usernameStep,
        SignupStep.NAMES to namesStep,
        SignupStep.BIRTH_DATE to birthDateStep,
        SignupStep.GENDER to genderStep,
        SignupStep.CONTACT to contactStep,
        SignupStep.PASSWORD to passwordStep,
        SignupStep.LANGUAGE to languageStep,
        SignupStep.LICENSE to licenseStep
    )
}

internal fun SignupActivity.setupUsernameFilter() {
    val usernameFilter = InputFilter { source, start, end, dest, dstart, dend ->
        if (start == end) {
            return@InputFilter null
        }
        val replacement = source.subSequence(start, end).toString()
        val prospective = StringBuilder(dest)
        prospective.replace(dstart, dend, replacement)
        val resultText = prospective.toString()
        if (resultText.isEmpty() || SignupActivity.USERNAME_PATTERN.matches(resultText)) {
            null
        } else {
            ""
        }
    }
    usernameInput.filters = arrayOf(usernameFilter)
}

internal fun TextView.clearErrorOnTextChange(layout: TextInputLayout) {
    doAfterTextChanged {
        layout.error = null
    }
}

internal fun SignupActivity.setupFocusAndValidationListeners() {
    setupGeneralFocusListeners()
    setupTextChangeListeners()
    setupSpecialInputListeners()
}

internal fun SignupActivity.setupGeneralFocusListeners() {
    val focusableInputs = listOf(
        usernameInput,
        firstNameInput,
        middleNameInput,
        lastNameInput,
        birthDateInput,
        emailInput,
        phoneInput,
        passwordInput,
        confirmPasswordInput,
        languageInput,
        levelInput
    )

    focusableInputs.forEach { input ->
        input.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                ensureVisible(scrollView, v)
                if (v === languageInput && !languageInput.isPopupShowing) {
                    languageInput.showDropDown()
                } else if (v === levelInput && !levelInput.isPopupShowing) {
                    levelInput.showDropDown()
                }
            }
        }
    }
}

internal fun SignupActivity.setupTextChangeListeners() {
    levelInput.clearErrorOnTextChange(levelLayout)
    usernameInput.clearErrorOnTextChange(usernameLayout)
    usernameInput.doAfterTextChanged {
        verifyServerAvailability(SignupStep.USERNAME, force = true)
    }

    firstNameInput.clearErrorOnTextChange(firstNameLayout)
    lastNameInput.clearErrorOnTextChange(lastNameLayout)
    emailInput.clearErrorOnTextChange(emailLayout)
    phoneInput.clearErrorOnTextChange(phoneLayout)
}

internal fun SignupActivity.setupSpecialInputListeners() {
    genderGroup.setOnCheckedChangeListener { _, _ ->
        genderErrorView.visibility = View.GONE
        ensureVisible(scrollView, genderGroup)
    }

    birthDateInput.keyListener = null
    birthDateInput.setOnClickListener {
        ensureVisible(scrollView, birthDateInput)
        showBirthDatePicker()
    }
    birthDateInput.setOnFocusChangeListener { v, hasFocus ->
        if (hasFocus) {
            ensureVisible(scrollView, v)
            showBirthDatePicker()
        }
    }
    birthDateSelection?.let { selection ->
        birthDateInput.setText(formatBirthDate(selection))
    }

    passwordInput.setOnFocusChangeListener { v, hasFocus ->
        if (hasFocus) {
            ensureVisible(scrollView, v)
        } else {
            updatePasswordErrorState()
        }
    }

    confirmPasswordInput.setOnFocusChangeListener { v, hasFocus ->
        if (hasFocus) {
            ensureVisible(scrollView, v)
        } else {
            updatePasswordErrorState()
        }
    }
}

internal fun SignupActivity.setupClickListeners() {
    backIcon.setOnClickListener {
        navigateBack()
    }

    backButton.setOnClickListener {
        navigateBack()
    }

    nextButton.setOnClickListener {
        handleNextButtonClick()
    }
}

internal fun SignupActivity.showBirthDatePicker() {
    if (supportFragmentManager.findFragmentByTag(SignupActivity.BIRTH_DATE_PICKER_TAG) != null) {
        return
    }

    val picker = MaterialDatePicker.Builder.datePicker()
        .setTitleText(getString(R.string.signup_birth_date_picker_title))
        .setCalendarConstraints(BirthDateConstraints.calendarConstraints())
        .apply {
            setSelection(BirthDateConstraints.coerceSelection(birthDateSelection))
        }
        .build()

    picker.addOnPositiveButtonClickListener { selection ->
        if (BirthDateConstraints.isFuture(selection)) {
            return@addOnPositiveButtonClickListener
        }
        birthDateSelection = selection
        birthDateInput.setText(formatBirthDate(selection))
        birthDateLayout.error = null
    }

    picker.addOnDismissListener {
        birthDateInput.clearFocus()
    }

    picker.show(supportFragmentManager, SignupActivity.BIRTH_DATE_PICKER_TAG)
}

internal fun SignupActivity.formatBirthDate(selection: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return formatter.format(Date(selection))
}

internal fun SignupActivity.ensureVisible(scrollView: ScrollView, view: View) {
    scrollView.post {
        if (!isDescendantOf(scrollView, view)) {
            return@post
        }
        val rect = Rect()
        view.getDrawingRect(rect)
        scrollView.offsetDescendantRectToMyCoords(view, rect)

        val scrollY = scrollView.scrollY
        val scrollViewHeight = scrollView.height
        val visibleBottom = scrollY + scrollViewHeight - imeInsetBottom
        val top = rect.top
        val bottom = rect.bottom

        val scrollDelta = when {
            bottom > visibleBottom -> bottom - visibleBottom
            top < scrollY -> top - scrollY
            else -> 0
        }

        if (scrollDelta != 0) {
            scrollView.smoothScrollBy(0, scrollDelta)
        }
    }
}

internal fun SignupActivity.isDescendantOf(parent: View, child: View): Boolean {
    var current: View? = child
    while (current != null && current != parent) {
        val currentParent = current.parent
        current = currentParent as? View
    }
    return current == parent
}
