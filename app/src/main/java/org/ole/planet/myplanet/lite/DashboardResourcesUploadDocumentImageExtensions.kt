package org.ole.planet.myplanet.lite

import android.app.AlertDialog
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.slider.Slider
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore

internal fun DashboardResourcesPageFragment.showPdfMetadataPopup(uri: Uri) {
        val context = requireContext()
        val fileName = DashboardResourcesMediaUtils.resolveFileName(requireContext(), uri).ifBlank { "document.pdf" }
        val defaultTitle = fileName.replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "").ifBlank { fileName }
        val languageOptions = resources.getStringArray(R.array.signup_language_options).toList()
        val credentials = ProfileCredentialsStore.getStoredCredentials(context.applicationContext)
        val username = credentials?.username.orEmpty()
        val planetCode = DashboardServerPreferences.getServerCode(context).orEmpty()

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val titleInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_title)
            setText(defaultTitle)
        }
        val descriptionInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_description)
            setText("")
            minLines = 3
        }
        val languageLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_language)
        }
        val languageSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, languageOptions)
            setSelection(DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(requireContext(), resources, languageOptions))
        }
        val isDownloadableCheck = CheckBox(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_downloadable)
            isChecked = true
        }
        content.addView(titleInput)
        content.addView(descriptionInput)
        content.addView(languageLabel)
        content.addView(languageSpinner)
        content.addView(isDownloadableCheck)

        val scroll = ScrollView(context).apply {
            addView(content)
        }

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.dashboard_resources_add_pdf))
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.server_configuration_save), null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val rawTitle = titleInput.text?.toString()?.trim().orEmpty()
                        val title = DashboardResourcesMediaUtils.sanitizeResourceName(rawTitle)
                        val description = descriptionInput.text?.toString()?.trim().orEmpty()
                        if (title.isBlank()) {
                            titleInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        if (description.isBlank()) {
                            descriptionInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        val payload = JSONObject()
                            .put("title", title)
                            .put("description", description)
                            .put("subject", JSONArray().put(getString(R.string.server_planet_learning)))
                            .put("level", JSONArray().put(getString(R.string.server_planet_early_education)))
                            .put("language", languageSpinner.selectedItem?.toString().orEmpty())
                            .put("addedBy", username)
                            .put("sourcePlanet", planetCode)
                            .put("resideOn", planetCode)
                            .put("isDownloadable", isDownloadableCheck.isChecked)
                            .put("private", false)
                        val bytesProvider: suspend () -> ByteArray? = {
                            DashboardResourcesMediaUtils.readBytesFromUri(requireContext(), uri)
                        }
                        performResourceCreateAndUpload(
                            payload = payload,
                            fileExtension = "pdf",
                            mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "application/pdf" },
                            credentials = credentials,
                            bytesProvider = bytesProvider,
                            onSuccess = {
                                Toast.makeText(context, getString(R.string.dashboard_resources_upload_success), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        )
                    }
                }
                dialog.setOnDismissListener {
                    if (uri.scheme == "content" && uri.authority == "${context.packageName}.fileprovider") {
                        lifecycleScope.launch(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                                        val name = cursor.getString(nameIndex)
                                        if (name.startsWith("captured_photo_")) {
                                            val file = File(context.cacheDir, "shared_images/$name")
                                            if (file.exists()) {
                                                file.delete()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

internal fun DashboardResourcesPageFragment.showImageMetadataPopup(uri: Uri) {
        val context = requireContext()
        val fileName = DashboardResourcesMediaUtils.resolveFileName(requireContext(), uri).ifBlank { "image.jpg" }
        val defaultTitle = fileName.substringBeforeLast('.').ifBlank { fileName }
        val languageOptions = resources.getStringArray(R.array.signup_language_options).toList()
        val credentials = ProfileCredentialsStore.getStoredCredentials(context.applicationContext)
        val username = credentials?.username.orEmpty()
        val planetCode = DashboardServerPreferences.getServerCode(context).orEmpty()
        val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
        val imageSize = DashboardResourcesMediaUtils.resolveImageSize(requireContext(), uri) ?: (0 to 0)
        var isUploaded = false

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val titleInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_title)
            setText(defaultTitle)
        }
        val descriptionInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_description)
            setText("")
            minLines = 3
        }
        val languageLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_language)
        }
        val languageSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, languageOptions)
            setSelection(DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(requireContext(), resources, languageOptions))
        }
        val isDownloadableCheck = CheckBox(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_downloadable)
            isChecked = true
        }
        val previewImage = ImageView(context).apply {
            val previewHeight = (200 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                previewHeight
            ).apply {
                setMargins(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            Glide.with(this).load(uri).into(this)
        }
        val resolutionLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_image_resolution)
        }
        val initialSliderValue = 90f
        val resolutionValue = TextView(context).apply {
            val factor = initialSliderValue / 100f
            val width = max(1, (imageSize.first * factor).roundToInt())
            val height = max(1, (imageSize.second * factor).roundToInt())
            text = getString(
                R.string.dashboard_resources_image_resolution_preview,
                initialSliderValue.roundToInt(),
                width,
                height
            )
        }
        val resolutionSlider = Slider(context).apply {
            contentDescription = getString(R.string.slider_desc)
            valueFrom = 40f
            valueTo = 100f
            value = initialSliderValue
            addOnChangeListener { _, value, _ ->
                val factor = value / 100f
                val width = max(1, (imageSize.first * factor).roundToInt())
                val height = max(1, (imageSize.second * factor).roundToInt())
                resolutionValue.text = getString(
                    R.string.dashboard_resources_image_resolution_preview,
                    value.roundToInt(),
                    width,
                    height
                )
            }
        }
        content.addView(titleInput)
        content.addView(descriptionInput)
        content.addView(languageLabel)
        content.addView(languageSpinner)
        content.addView(isDownloadableCheck)
        content.addView(resolutionLabel)
        content.addView(resolutionValue)
        content.addView(resolutionSlider)
        content.addView(previewImage)

        val scroll = ScrollView(context).apply {
            addView(content)
        }

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.dashboard_resources_add_image))
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.server_configuration_save), null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val rawTitle = titleInput.text?.toString()?.trim().orEmpty()
                        val title = DashboardResourcesMediaUtils.sanitizeResourceName(rawTitle)
                        val description = descriptionInput.text?.toString()?.trim().orEmpty()
                        if (title.isBlank()) {
                            titleInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        if (description.isBlank()) {
                            descriptionInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        val payload = JSONObject()
                            .put("title", title)
                            .put("description", description)
                            .put("subject", JSONArray().put(getString(R.string.server_planet_learning)))
                            .put("level", JSONArray().put(getString(R.string.server_planet_early_education)))
                            .put("language", languageSpinner.selectedItem?.toString().orEmpty())
                            .put("addedBy", username)
                            .put("sourcePlanet", planetCode)
                            .put("resideOn", planetCode)
                            .put("isDownloadable", isDownloadableCheck.isChecked)
                            .put("private", false)
                        val bytesProvider: suspend () -> ByteArray? = {
                            DashboardResourcesMediaUtils.buildResizedImageBytes(requireContext(), uri, resolutionSlider.value, mimeType)
                        }
                        performResourceCreateAndUpload(
                            payload = payload,
                            fileExtension = DashboardResourcesMediaUtils.extensionForImageMimeType(mimeType),
                            mimeType = mimeType,
                            credentials = credentials,
                            bytesProvider = bytesProvider,
                            onSuccess = {
                                isUploaded = true
                                Toast.makeText(context, getString(R.string.dashboard_resources_upload_success), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        )
                    }
                }
                dialog.show()
                dialog.setOnDismissListener {
                    if (uri.authority == "${context.packageName}.fileprovider" || isUploaded) {
                        runCatching {
                            File(context.cacheDir, "shared_images/${uri.lastPathSegment}").delete()
                        }
                    }
                }
            }
    }
