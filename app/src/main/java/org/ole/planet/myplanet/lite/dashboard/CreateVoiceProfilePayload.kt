/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-07
 */

package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfile
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.DeviceUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import java.util.ArrayList

internal suspend fun CreateVoiceActivity.buildImageResourceContext(credentials: StoredCredentials): VoiceImageResourceContext {
    val preferences = SecurePreferencesProvider.getServerPreferences(applicationContext)
    val androidId = preferences.getString(CreateVoiceActivity.KEY_DEVICE_ANDROID_ID, null)?.takeIf { it.isNotBlank() }
    val customDeviceName = preferences.getString(CreateVoiceActivity.KEY_DEVICE_CUSTOM_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }
    val storedServerCode = preferences.getString(CreateVoiceActivity.KEY_SERVER_CODE, null)?.takeIf { it.isNotBlank() }
    val storedParentCode = preferences.getString(CreateVoiceActivity.KEY_SERVER_PARENT_CODE, null)?.takeIf { it.isNotBlank() }
    val profile = loadCachedProfile()
    val parsedCodes = parseCodesFromProfile(profile?.rawDocument)
    val resolvedResideOn =
        serverCode?.takeIf { it.isNotBlank() }
            ?: storedServerCode
            ?: parsedCodes?.planetCode
    val resolvedParent = storedParentCode ?: parsedCodes?.parentCode
    return VoiceImageResourceContext(
        username = credentials.username,
        resideOn = resolvedResideOn,
        sourcePlanet = resolvedParent,
        androidId = androidId,
        deviceName = DeviceUtils.getDeviceName(),
        customDeviceName = customDeviceName,
    )
}

internal fun CreateVoiceActivity.parseCodesFromProfile(rawDocument: String?): ProfileCodes? {
    if (rawDocument.isNullOrBlank()) {
        return null
    }
    return runCatching {
        val json = JSONObject(rawDocument)
        val planetCode = json.optString("planetCode").takeIf { it.isNotBlank() }
        val parentCode = json.optString("parentCode").takeIf { it.isNotBlank() }
        ProfileCodes(planetCode, parentCode)
    }.getOrNull()
}

internal suspend fun CreateVoiceActivity.loadCachedProfile(): UserProfile? {
    val existing = cachedProfile
    if (existing != null) {
        return existing
    }
    val profile =
        withContext(Dispatchers.IO) {
            UserProfileDatabase.getInstance(applicationContext).getProfile()
        }
    cachedProfile = profile
    return profile
}

internal fun CreateVoiceActivity.resolvePostingCodes(profile: UserProfile?): ProfileCodes? {
    val preferences = SecurePreferencesProvider.getServerPreferences(applicationContext)
    val storedParentCode = preferences.getString(CreateVoiceActivity.KEY_SERVER_PARENT_CODE, null)?.takeIf { it.isNotBlank() }
    val storedServerCode = preferences.getString(CreateVoiceActivity.KEY_SERVER_CODE, null)?.takeIf { it.isNotBlank() }
    val parsedCodes = parseCodesFromProfile(profile?.rawDocument)
    val resolvedPlanet =
        serverCode?.takeIf { it.isNotBlank() }
            ?: storedServerCode
            ?: parsedCodes?.planetCode
    val resolvedParent = storedParentCode ?: parsedCodes?.parentCode
    if (resolvedPlanet.isNullOrBlank() && resolvedParent.isNullOrBlank()) {
        return null
    }
    return ProfileCodes(resolvedPlanet, resolvedParent)
}

internal fun CreateVoiceActivity.buildUserPayload(
    profile: UserProfile?,
    credentials: StoredCredentials,
    codes: ProfileCodes?,
): VoicesComposerRepository.UserPayload {
    val fallbackId = "org.couchdb.user:${credentials.username}"
    val rawDocument = profile?.rawDocument
    if (rawDocument.isNullOrBlank()) {
        return VoicesComposerRepository.UserPayload(
            id = fallbackId,
            name = credentials.username,
            firstName = profile?.firstName,
            middleName = profile?.middleName,
            lastName = profile?.lastName,
            email = profile?.email,
            language = profile?.language,
            phoneNumber = profile?.phoneNumber,
            planetCode = codes?.planetCode,
            parentCode = codes?.parentCode,
            roles = null,
            joinDate = null,
            attachments = null,
        )
    }
    val json = JSONObject(rawDocument)
    val attachments = parseAttachmentPayloads(json.optJSONObject("_attachments"))
    val roles =
        json.optJSONArray("roles")?.let { array ->
            val length = array.length()
            val collected = ArrayList<String>(length)
            for (index in 0 until length) {
                val s = array.optString(index)
                if (s.isNotBlank()) {
                    collected.add(s)
                }
            }
            collected.takeIf { it.isNotEmpty() }
        }
    val joinDate = if (json.has("joinDate")) json.optLong("joinDate") else null
    val nonNullProfile = requireNotNull(profile)
    return VoicesComposerRepository.UserPayload(
        id = json.optString("_id").takeIf { it.isNotBlank() } ?: fallbackId,
        name = json.optString("name").takeIf { it.isNotBlank() } ?: credentials.username,
        firstName = json.optString("firstName").takeIf { it.isNotBlank() } ?: nonNullProfile.firstName,
        middleName = json.optString("middleName").takeIf { it.isNotBlank() } ?: nonNullProfile.middleName,
        lastName = json.optString("lastName").takeIf { it.isNotBlank() } ?: nonNullProfile.lastName,
        email = json.optString("email").takeIf { it.isNotBlank() } ?: nonNullProfile.email,
        language = json.optString("language").takeIf { it.isNotBlank() } ?: nonNullProfile.language,
        phoneNumber = json.optString("phoneNumber").takeIf { it.isNotBlank() } ?: nonNullProfile.phoneNumber,
        planetCode = json.optString("planetCode").takeIf { it.isNotBlank() } ?: codes?.planetCode,
        parentCode = json.optString("parentCode").takeIf { it.isNotBlank() } ?: codes?.parentCode,
        roles = roles,
        joinDate = joinDate,
        attachments = attachments,
    )
}

internal fun CreateVoiceActivity.parseAttachmentPayloads(
    attachmentsObject: JSONObject?,
): Map<String, VoicesComposerRepository.AttachmentPayload>? {
    attachmentsObject ?: return null
    val iterator = attachmentsObject.keys()
    if (!iterator.hasNext()) {
        return null
    }
    val result = mutableMapOf<String, VoicesComposerRepository.AttachmentPayload>()
    while (iterator.hasNext()) {
        val key = iterator.next()
        val attachment = attachmentsObject.optJSONObject(key) ?: continue
        val contentType = attachment.optString("content_type").takeIf { it.isNotBlank() }
        val revpos = if (attachment.has("revpos")) attachment.optInt("revpos") else null
        val digest = attachment.optString("digest").takeIf { it.isNotBlank() }
        val length = if (attachment.has("length")) attachment.optInt("length") else null
        val stub = if (attachment.has("stub")) attachment.optBoolean("stub") else null
        val data = attachment.optString("data").takeIf { attachment.has("data") && it.isNotBlank() }
        result[key] =
            VoicesComposerRepository.AttachmentPayload(
                contentType = contentType,
                revpos = revpos,
                digest = digest,
                length = length,
                stub = stub,
                data = data,
            )
    }
    return result.takeIf { it.isNotEmpty() }
}
