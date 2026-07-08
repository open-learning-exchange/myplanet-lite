/*
 * Author: Walfre Lopez Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-08
 */

package org.ole.planet.myplanet.lite.profile

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.ole.planet.myplanet.lite.BuildConfig
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.auth.AuthResult
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.util.nullIfBlank
import java.security.MessageDigest

private const val AVATAR_ATTACHMENT_KEY = "img"
private const val AVATAR_CONTENT_TYPE = "image/jpeg"

internal suspend fun ProfileActivity.refreshProfileFromServerImpl(): Boolean {
    val storedBaseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
    val sanitizedBase = (storedBaseUrl ?: BuildConfig.PLANET_BASE_URL).trim()
    if (sanitizedBase.isEmpty()) {
        return false
    }
    val normalizedBase = sanitizedBase.trimEnd('/')

    val authService = AuthDependencies.provideAuthService(this, sanitizedBase)
    val storedCredentials = ProfileCredentialsStore.getStoredCredentials(applicationContext)
    var username = storedCredentials?.username
    if (username.isNullOrBlank()) {
        username =
            withContext(Dispatchers.IO) {
                userProfileDatabase.getProfile()?.username
            }
    }

    var sessionCookie = authService.getStoredToken()

    if (storedCredentials != null) {
        when (val loginResult = authService.login(storedCredentials.username, storedCredentials.password)) {
            is AuthResult.Success -> {
                sessionCookie = loginResult.response.sessionCookie ?: sessionCookie
                if (username.isNullOrBlank()) {
                    username = loginResult.response.name ?: storedCredentials.username
                }
            }

            is AuthResult.Error, is AuthResult.Failure -> {}
        }
    }

    if (username.isNullOrBlank() || sessionCookie.isNullOrBlank()) {
        return false
    }

    return userProfileSync.refreshProfile(normalizedBase, username.orEmpty(), sessionCookie)
}

internal suspend fun ProfileActivity.executeProfileUpdateRequestImpl(
    normalizedBase: String,
    username: String,
    nonNullCookie: String,
    document: JSONObject,
    avatarUploadBytes: ByteArray?,
    avatarBytesToPersist: ByteArray?,
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val requestBody =
                document
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            val requestBuilder =
                Request
                    .Builder()
                    .url("$normalizedBase/db/_users/org.couchdb.user:$username")
                    .put(requestBody)
                    .addHeader("Cookie", nonNullCookie)
                    .addHeader("Content-Type", "application/json")

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext false
                }
                val responseBody = response.body.string()
                val newRevision =
                    responseBody
                        .nullIfBlank()
                        ?.let { JSONObject(it) }
                        ?.optString("rev")
                        .nullIfBlank()
                newRevision?.let { document.put("_rev", it) }
                if (avatarUploadBytes != null) {
                    replaceAvatarAttachmentWithStub(document, avatarUploadBytes, newRevision)
                }
                saveUpdatedProfileLocally(document, avatarBytesToPersist, username)
                true
            }
        } catch (error: Exception) {
            false
        }
    }
}

private suspend fun ProfileActivity.resolveProfileUpdateContext(): ProfileUpdateContext? {
    val storedProfile = withContext(Dispatchers.IO) { userProfileDatabase.getProfile() }
    val storedBaseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
    val sanitizedBase = (storedBaseUrl ?: BuildConfig.PLANET_BASE_URL).trim()
    if (sanitizedBase.isEmpty()) {
        return null
    }
    val normalizedBase = sanitizedBase.trimEnd('/')
    var username = storedProfile?.username
    val storedCredentials = ProfileCredentialsStore.getStoredCredentials(applicationContext)
    if (username.isNullOrBlank()) {
        username = storedCredentials?.username
    }
    if (username.isNullOrBlank()) {
        return null
    }

    val authService = AuthDependencies.provideAuthService(this, sanitizedBase)
    var sessionCookie = authService.getStoredToken()
    if (storedCredentials != null) {
        when (val loginResult = authService.login(storedCredentials.username, storedCredentials.password)) {
            is AuthResult.Success -> {
                sessionCookie = loginResult.response.sessionCookie ?: sessionCookie
            }

            is AuthResult.Error, is AuthResult.Failure -> {
                if (sessionCookie.isNullOrBlank()) {
                    return null
                }
            }
        }
    }

    val nonNullCookie = sessionCookie.nullIfBlank() ?: return null

    return ProfileUpdateContext(storedProfile, normalizedBase, username, nonNullCookie)
}

internal suspend fun ProfileActivity.submitProfileUpdatesImpl(formValues: ProfileFormValues): Boolean {
    val context = resolveProfileUpdateContext() ?: return false
    val avatarUploadBytes = pendingAvatarUpload
    val document =
        buildUpdatedProfileDocument(
            context.normalizedBase,
            context.username,
            context.sessionCookie,
            formValues,
            context.storedProfile,
            avatarUploadBytes,
        ) ?: return false

    val avatarBytesToPersist = avatarUploadBytes ?: context.storedProfile?.avatarImage

    val success =
        executeProfileUpdateRequest(
            context.normalizedBase,
            context.username,
            context.sessionCookie,
            document,
            avatarUploadBytes,
            avatarBytesToPersist,
        )

    if (success && avatarUploadBytes != null) {
        pendingAvatarUpload = null
        AvatarUpdateNotifier.notifyAvatarUpdated(context.username)
    }

    return success
}

private suspend fun ProfileActivity.buildUpdatedProfileDocument(
    serverBaseUrl: String,
    username: String,
    sessionCookie: String,
    formValues: ProfileFormValues,
    storedProfile: UserProfile?,
    avatarUploadBytes: ByteArray?,
): JSONObject? {
    val baseDocument =
        storedProfile?.rawDocument?.nullIfBlank()?.let { JSONObject(it) }
            ?: fetchRemoteProfileDocumentImpl(serverBaseUrl, username, sessionCookie)
            ?: return null

    val latestRevision = storedProfile?.revision ?: baseDocument.optString("_rev").nullIfBlank()
    latestRevision?.let { baseDocument.put("_rev", it) }
    val derivedKey = storedProfile?.derivedKey ?: baseDocument.optString("derived_key").nullIfBlank()
    derivedKey?.let { baseDocument.put("derived_key", it) }
    baseDocument.put("_id", baseDocument.optString("_id").nullIfBlank() ?: "org.couchdb.user:$username")
    baseDocument.put("name", username)
    baseDocument.put("type", baseDocument.optString("type").nullIfBlank() ?: "user")

    applyFormValuesToDocument(baseDocument, formValues)

    if (avatarUploadBytes != null) {
        val attachments = baseDocument.optJSONObject("_attachments") ?: JSONObject()
        val avatarAttachment =
            JSONObject().apply {
                put("content_type", AVATAR_CONTENT_TYPE)
                put("data", Base64.encodeToString(avatarUploadBytes, Base64.NO_WRAP))
            }
        attachments.put(AVATAR_ATTACHMENT_KEY, avatarAttachment)
        baseDocument.put("_attachments", attachments)
    }

    return baseDocument
}

internal suspend fun ProfileActivity.fetchRemoteProfileDocumentImpl(
    serverBaseUrl: String,
    username: String,
    sessionCookie: String,
): JSONObject? {
    val profileUrl = "$serverBaseUrl/db/_users/org.couchdb.user:$username"
    return withContext(Dispatchers.IO) {
        try {
            val request =
                Request
                    .Builder()
                    .url(profileUrl)
                    .get()
                    .addHeader("Cookie", sessionCookie)
                    .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }
                val body = response.body.string()
                if (body.isNullOrBlank()) {
                    return@withContext null
                }
                JSONObject(body)
            }
        } catch (error: Exception) {
            null
        }
    }
}

private fun applyFormValuesToDocument(
    document: JSONObject,
    values: ProfileFormValues,
) {
    document.put("middleName", values.middleName.orEmpty())

    val properties =
        mapOf(
            "firstName" to values.firstName,
            "lastName" to values.lastName,
            "email" to values.email,
            "phoneNumber" to values.phoneNumber,
            "gender" to values.gender,
            "language" to values.language,
            "level" to values.level,
            "birthDate" to values.birthDateIso,
            "birthYear" to values.birthYear,
            "age" to values.age,
        )

    properties.forEach { (key, value) ->
        document.putOrRemove(key, value)
    }
}

private suspend fun ProfileActivity.saveUpdatedProfileLocally(
    document: JSONObject,
    avatarBytes: ByteArray?,
    usernameFallback: String,
) {
    val resolvedUsername = document.optString("name").nullIfBlank() ?: usernameFallback
    val updatedProfile =
        UserProfile(
            username = resolvedUsername,
            firstName = document.optString("firstName").nullIfBlank(),
            middleName = document.optString("middleName").nullIfBlank(),
            lastName = document.optString("lastName").nullIfBlank(),
            email = document.optString("email").nullIfBlank(),
            language = document.optString("language").nullIfBlank(),
            phoneNumber = document.optString("phoneNumber").nullIfBlank(),
            birthDate = document.optString("birthDate").nullIfBlank(),
            gender = document.optString("gender").nullIfBlank(),
            level = document.optString("level").nullIfBlank(),
            avatarImage = avatarBytes,
            revision = document.optString("_rev").nullIfBlank(),
            derivedKey = document.optString("derived_key").nullIfBlank(),
            rawDocument = document.toString(),
            isUserAdmin = document.optBoolean("isUserAdmin", false),
        )

    withContext(Dispatchers.IO) {
        userProfileDatabase.saveProfile(updatedProfile)
    }
}

private fun replaceAvatarAttachmentWithStub(
    document: JSONObject,
    avatarBytes: ByteArray,
    revision: String?,
) {
    val attachments = document.optJSONObject("_attachments") ?: JSONObject()
    val stubInfo =
        JSONObject().apply {
            put("content_type", AVATAR_CONTENT_TYPE)
            put("length", avatarBytes.size)
            put("digest", avatarBytes.toMd5DigestHeader())
            put("stub", true)
            revision?.substringBefore('-')?.toIntOrNull()?.let { put("revpos", it) }
        }
    attachments.put(AVATAR_ATTACHMENT_KEY, stubInfo)
    document.put("_attachments", attachments)
}

private fun ByteArray.toMd5DigestHeader(): String {
    val digest = MessageDigest.getInstance("MD5").digest(this)
    val encoded = Base64.encodeToString(digest, Base64.NO_WRAP)
    return "md5-$encoded"
}

private fun JSONObject.putOrRemove(
    key: String,
    value: String?,
) {
    if (value.isNullOrBlank()) {
        remove(key)
    } else {
        put(key, value)
    }
}
