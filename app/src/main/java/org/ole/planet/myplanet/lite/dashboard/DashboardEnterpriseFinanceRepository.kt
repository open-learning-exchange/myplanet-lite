package org.ole.planet.myplanet.lite.dashboard

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class DashboardEnterpriseFinanceRepository(
    private val client: OkHttpClient = SharedBitmapDependencies.client,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun fetch(
        baseUrl: String,
        credentials: StoredCredentials,
        sessionCookie: String?,
        enterpriseId: String,
        userId: String,
        userPlanetCode: String,
    ): Result<FinanceSnapshot> = withContext(dispatcher) {
        runCatching {
            val enterprise = getJson(baseUrl, "teams/$enterpriseId", credentials, sessionCookie)
            require(enterprise.optString("type") == "enterprise")
            val planetCode = enterprise.optString("teamPlanetCode").takeIf(String::isNotBlank)
                ?: throw IOException("Enterprise planet code is missing")
            val membershipSelector = JSONObject()
                .put("teamId", enterpriseId)
                .put("userId", userId)
                .put("userPlanetCode", userPlanetCode)
                .put("docType", "membership")
            val isMember = find(baseUrl, membershipSelector, 1, credentials, sessionCookie).length() > 0
            if (!enterprise.optBoolean("public", false) && !isMember) throw FinanceAccessDeniedException()
            val selector = JSONObject()
                .put("teamId", enterpriseId)
                .put("teamPlanetCode", planetCode)
                .put("docType", "transaction")
                .put("$" + "or", JSONArray()
                    .put(JSONObject().put("status", JSONObject().put("$" + "exists", false)))
                    .put(JSONObject().put("status", JSONObject().put("$" + "ne", "archived"))))
            val docs = find(baseUrl, selector, 1000, credentials, sessionCookie)
            val chronological = (0 until docs.length()).mapNotNull { docs.optJSONObject(it)?.toTransaction() }
                .sortedBy(FinanceTransaction::date)
            var balance = 0.0
            val withBalances = chronological.map { transaction ->
                balance += if (transaction.type == TransactionType.CREDIT) transaction.amount else -transaction.amount
                transaction.copy(runningBalance = balance)
            }.asReversed()
            FinanceSnapshot(
                enterpriseId = enterpriseId,
                enterpriseType = enterprise.optString("teamType").ifBlank { "sync" },
                enterprisePlanetCode = planetCode,
                canManage = isMember,
                transactions = withBalances,
            )
        }
    }

    suspend fun save(
        baseUrl: String,
        credentials: StoredCredentials,
        sessionCookie: String?,
        snapshot: FinanceSnapshot,
        input: SaveFinanceTransaction,
        contentResolver: ContentResolver,
    ): Result<FinanceTransaction> = withContext(dispatcher) {
        runCatching {
            require(input.description.isNotBlank() && input.amount > 0.0 && input.date > 0L)
            val document = input.original?.let {
                getJson(baseUrl, "teams/${it.id}", credentials, sessionCookie)
            } ?: JSONObject()
            document.optJSONObject("_attachments")?.let { attachments ->
                attachments.keys().asSequence().toList()
                    .filterNot(input.existingReceipts::contains)
                    .forEach(attachments::remove)
                if (attachments.length() == 0) document.remove("_attachments")
            }
            document.put("type", input.type.value)
                .put("description", input.description.trim())
                .put("amount", input.amount)
                .put("date", input.date)
                .put("docType", "transaction")
                .put("teamId", snapshot.enterpriseId)
                .put("teamType", snapshot.enterpriseType)
                .put("teamPlanetCode", snapshot.enterprisePlanetCode)
            val saved = postJson(baseUrl, "teams", document, credentials, sessionCookie)
            val id = saved.getString("id")
            var revision = saved.getString("rev")
            input.newReceipts.take((2 - input.existingReceipts.size).coerceAtLeast(0)).forEach { receipt ->
                val mime = contentResolver.getType(receipt.uri)?.takeIf(ALLOWED_RECEIPT_TYPES::contains)
                    ?: throw IOException("Unsupported receipt type")
                val bytes = contentResolver.openInputStream(receipt.uri)?.use { it.readBytes() }
                    ?: throw IOException("Unable to read receipt")
                revision = uploadReceipt(
                    baseUrl, id, revision, receipt.filename, mime, bytes, credentials, sessionCookie,
                )
            }
            getJson(baseUrl, "teams/$id", credentials, sessionCookie).toTransaction()
                ?: throw IOException("Invalid saved transaction")
        }
    }

    suspend fun archive(
        baseUrl: String,
        credentials: StoredCredentials,
        sessionCookie: String?,
        transaction: FinanceTransaction,
    ): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val current = getJson(baseUrl, "teams/${transaction.id}", credentials, sessionCookie)
            current.put("status", "archived")
            postJson(baseUrl, "teams", current, credentials, sessionCookie)
            Unit
        }
    }

    private fun uploadReceipt(
        baseUrl: String,
        id: String,
        revision: String,
        filename: String,
        mime: String,
        bytes: ByteArray,
        credentials: StoredCredentials,
        sessionCookie: String?,
    ): String {
        val encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20")
        val request = requestBuilder(baseUrl, "teams/$id/$encoded?rev=$revision", credentials, sessionCookie)
            .put(bytes.toRequestBody(mime.toMediaType())).build()
        return execute(request).getString("rev")
    }

    private fun JSONObject.toTransaction(): FinanceTransaction? {
        val id = optString("_id").takeIf(String::isNotBlank) ?: return null
        val type = TransactionType.from(optString("type")) ?: return null
        val attachments = optJSONObject("_attachments")
        val receiptNames = attachments?.keys()?.asSequence()?.filter { name ->
            attachments.optJSONObject(name)?.optString("content_type") in ALLOWED_RECEIPT_TYPES
        }?.toList().orEmpty()
        return FinanceTransaction(
            id, optString("_rev"), type, optString("description"), optDouble("amount"),
            optLong("date"), 0.0, receiptNames,
        )
    }

    private fun find(
        baseUrl: String,
        selector: JSONObject,
        limit: Int,
        credentials: StoredCredentials,
        sessionCookie: String?,
    ): JSONArray = postJson(
        baseUrl, "teams/_find", JSONObject().put("selector", selector).put("limit", limit),
        credentials, sessionCookie,
    ).optJSONArray("docs") ?: JSONArray()

    private fun getJson(baseUrl: String, path: String, credentials: StoredCredentials, cookie: String?) =
        execute(requestBuilder(baseUrl, path, credentials, cookie).get().build())

    private fun postJson(
        baseUrl: String,
        path: String,
        json: JSONObject,
        credentials: StoredCredentials,
        cookie: String?,
    ) = execute(
        requestBuilder(baseUrl, path, credentials, cookie)
            .post(json.toString().toRequestBody(JSON_MEDIA_TYPE)).build(),
    )

    private fun requestBuilder(
        baseUrl: String,
        path: String,
        credentials: StoredCredentials,
        cookie: String?,
    ): Request.Builder {
        val normalized = baseUrl.trim().trimEnd('/').ifBlank { throw IOException("Missing server URL") }
        return Request.Builder().url("$normalized/db/$path")
            .header("Authorization", Credentials.basic(credentials.username, credentials.password))
            .apply { cookie?.takeIf(String::isNotBlank)?.let { header("Cookie", it) } }
    }

    private fun execute(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            if (response.code == 409) throw FinanceConflictException()
            throw IOException("Unexpected response ${response.code}")
        }
        JSONObject(response.body.string())
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val ALLOWED_RECEIPT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}

enum class TransactionType(val value: String) {
    CREDIT("credit"), DEBIT("debit");
    companion object { fun from(value: String) = entries.firstOrNull { it.value == value } }
}

data class FinanceTransaction(
    val id: String,
    val revision: String,
    val type: TransactionType,
    val description: String,
    val amount: Double,
    val date: Long,
    val runningBalance: Double,
    val receipts: List<String>,
)

data class FinanceSnapshot(
    val enterpriseId: String,
    val enterpriseType: String,
    val enterprisePlanetCode: String,
    val canManage: Boolean,
    val transactions: List<FinanceTransaction>,
)

data class SaveFinanceTransaction(
    val type: TransactionType,
    val description: String,
    val amount: Double,
    val date: Long,
    val original: FinanceTransaction?,
    val existingReceipts: List<String>,
    val newReceipts: List<NewFinanceReceipt>,
)

data class NewFinanceReceipt(val uri: Uri, val filename: String)

class FinanceAccessDeniedException : IOException("Finance access denied")
class FinanceConflictException : IOException("Transaction changed on server")
