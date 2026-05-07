package org.ole.planet.myplanet.lite

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import androidx.core.net.toUri
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

internal class ResourceDownloadService(
    private val context: Context,
    private val prefsName: String,
    private val downloadedResourcesKey: String
) {

    private val securePrefs by lazy {
        SecurePreferencesProvider.getEncryptedPreferences(
            context = context,
            prefsName = "encrypted_$prefsName",
            legacyPrefsName = prefsName
        )
    }

    private var cachedDownloadedResources: JSONArray? = null

    fun saveDownloadedResourceFile(item: ResourceUi, bytes: ByteArray): File? {
        return runCatching {
            val normalizedResourceFolder = item.id.takeIf { it.isNotBlank() } ?: "_no_id"
            val tabFolder = if (item.isTeamResource) "team" else "community"
            val dir = File(context.filesDir, "resources/$tabFolder/$normalizedResourceFolder").apply { mkdirs() }
            File(dir, item.filename).apply { writeBytes(bytes) }
        }.getOrNull()
    }

    fun loadDownloadedResources(): List<ResourceUi> {
        val raw = securePrefs
            .getString(downloadedResourcesKey, "[]")
            .orEmpty()

        val json = cachedDownloadedResources ?: runCatching {
            JSONArray(raw).also { cachedDownloadedResources = it }
        }.getOrElse {
            JSONArray().also { cachedDownloadedResources = it }
        }
        val items = mutableListOf<ResourceUi>()

        for (i in 0 until json.length()) {
            val obj = json.optJSONObject(i) ?: continue

            val id = obj.optString("id").orEmpty()
            val filename = obj.optString("filename").orEmpty()
            val isTeamResource = obj.optBoolean("isTeamResource", false)

            val local = findLocalResourceFile(id, filename, isTeamResource)
            if (local?.exists() != true) continue

            items.add(
                ResourceUi(
                    id = id,
                    filename = filename,
                    name = obj.optString("name").ifBlank { filename },
                    type = obj.optString("type").ifBlank { "PDF" },
                    date = obj.optString("date").ifBlank { "-" },
                    createdDate = obj.optLong("createdDate").takeIf { it > 0L },
                    isDownloaded = true,
                    isDownloadable = true,
                    isTeamResource = isTeamResource
                )
            )
        }

        return items
    }

    fun upsertDownloadedResource(item: ResourceUi) {
        val prefs = securePrefs

        val existing = cachedDownloadedResources ?: runCatching {
            JSONArray(prefs.getString(downloadedResourcesKey, "[]").orEmpty()).also { cachedDownloadedResources = it }
        }.getOrElse {
            JSONArray().also { cachedDownloadedResources = it }
        }

        val filtered = mutableListOf<JSONObject>()

        for (i in 0 until existing.length()) {
            val obj = existing.optJSONObject(i) ?: continue

            val key = storedResourceKey(
                id = obj.optString("id"),
                filename = obj.optString("filename"),
                isTeamResource = obj.optBoolean("isTeamResource", false)
            )

            if (key != item.uniqueKey()) filtered.add(obj)
        }

        filtered.add(
            JSONObject().apply {
                put("id", item.id)
                put("filename", item.filename)
                put("name", item.name)
                put("type", item.type)
                put("date", item.date)
                put("createdDate", item.createdDate)
                put("isTeamResource", item.isTeamResource)
            }
        )

        val next = JSONArray(filtered)
        cachedDownloadedResources = next

        prefs.edit { putString(downloadedResourcesKey, next.toString()) }
    }

    fun removeDownloadedResource(item: ResourceUi) {
        val prefs = securePrefs

        val existing = cachedDownloadedResources ?: runCatching {
            JSONArray(prefs.getString(downloadedResourcesKey, "[]").orEmpty()).also { cachedDownloadedResources = it }
        }.getOrElse {
            JSONArray().also { cachedDownloadedResources = it }
        }

        val next = JSONArray()

        for (i in 0 until existing.length()) {
            val obj = existing.optJSONObject(i) ?: continue

            val key = storedResourceKey(
                id = obj.optString("id"),
                filename = obj.optString("filename"),
                isTeamResource = obj.optBoolean("isTeamResource", false)
            )

            if (key != item.uniqueKey()) next.put(obj)
        }

        cachedDownloadedResources = next
        prefs.edit { putString(downloadedResourcesKey, next.toString()) }
    }

    fun findLocalResourceFile(resourceId: String, filename: String, isTeamResource: Boolean): File? {
        if (filename.isBlank()) return null

        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val tabFolder = if (isTeamResource) "team" else "community"

        val candidates = mutableListOf<File>()

        if (resourceId.isNotBlank()) {
            candidates += File(context.filesDir, "resources/$tabFolder/$resourceId/$filename")
            candidates += File(context.filesDir, "resources/$resourceId/$filename")
        } else {
            candidates += File(context.filesDir, "resources/$tabFolder/_no_id/$filename")
            candidates += File(context.filesDir, "resources/_no_id/$filename")
            candidates += File(context.filesDir, "resources/$filename")
        }

        candidates += File(publicDownloads, filename)

        context.getExternalFilesDir(null)?.let {
            if (resourceId.isNotBlank()) {
                candidates.add(File(it, "resources/$tabFolder/$resourceId/$filename"))
                candidates.add(File(it, "resources/$resourceId/$filename"))
            } else {
                candidates.add(File(it, "resources/$tabFolder/_no_id/$filename"))
                candidates.add(File(it, "resources/_no_id/$filename"))
            }
        }

        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
            if (resourceId.isNotBlank()) {
                candidates.add(File(it, "resources/$tabFolder/$resourceId/$filename"))
                candidates.add(File(it, "resources/$resourceId/$filename"))
            } else {
                candidates.add(File(it, "resources/$tabFolder/_no_id/$filename"))
                candidates.add(File(it, "resources/_no_id/$filename"))
            }
        }

        return candidates.firstOrNull { it.exists() }
    }

    fun resolveResourceUri(baseUrl: String?, item: ResourceUi): String? {
        val localFile = findLocalResourceFile(item.id, item.filename, item.isTeamResource)
        if (localFile?.exists() == true) return localFile.toURI().toString()

        val trimmedBase = baseUrl?.trim()?.trimEnd('/').orEmpty()
        if (trimmedBase.isBlank() || item.id.isBlank() || item.filename.isBlank()) return null

        return trimmedBase.toUri().buildUpon()
            .appendPath("db")
            .appendPath("resources")
            .appendPath(item.id)
            .appendPath(item.filename)
            .build()
            .toString()
    }
}