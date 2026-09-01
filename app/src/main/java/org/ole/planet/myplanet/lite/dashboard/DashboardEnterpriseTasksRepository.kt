package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
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

class DashboardEnterpriseTasksRepository(
    private val client: OkHttpClient = SharedBitmapDependencies.client,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun fetchTasks(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        enterpriseId: String,
        userId: String,
        userPlanetCode: String,
    ): Result<EnterpriseTasksSnapshot> = withContext(dispatcher) {
        runCatching {
            val enterprise = getJson(baseUrl, "tasks", "../teams/$enterpriseId", credentials, sessionCookie)
            if (enterprise.optString("type") != "enterprise" || enterprise.optString("status") != "active") {
                throw IOException("Enterprise is not active")
            }
            val membershipSelector = JSONObject()
                .put("teamId", enterpriseId)
                .put("userId", userId)
                .put("userPlanetCode", userPlanetCode)
                .put("docType", "membership")
            val isMember = find(baseUrl, "teams", membershipSelector, 1, credentials, sessionCookie).length() > 0
            if (!enterprise.optBoolean("public", false) && !isMember) {
                return@runCatching EnterpriseTasksSnapshot.AccessDenied
            }
            val taskSelector = JSONObject()
                .put("link.teams", enterpriseId)
                .put("$" + "or", JSONArray()
                    .put(JSONObject().put("status", JSONObject().put("$" + "exists", false)))
                    .put(JSONObject().put("status", JSONObject().put("$" + "ne", "archived"))))
            val docs = find(baseUrl, "tasks", taskSelector, 1000, credentials, sessionCookie)
            val tasks = (0 until docs.length()).mapNotNull { docs.optJSONObject(it)?.toTaskDocument() }
                .sortedWith(compareBy<EnterpriseTaskDocument> { it.completed }.thenBy { it.deadline })
            EnterpriseTasksSnapshot.Success(
                enterpriseId = enterpriseId,
                enterpriseType = enterprise.optString("teamType").ifBlank { "sync" },
                enterprisePlanetCode = enterprise.optString("teamPlanetCode"),
                canManage = isMember,
                tasks = tasks,
            )
        }
    }

    suspend fun saveTask(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        input: SaveEnterpriseTask,
    ): Result<EnterpriseTaskDocument> = withContext(dispatcher) {
        runCatching {
            require(input.title.isNotBlank()) { "Task title is required" }
            require(input.deadline > 0) { "Task deadline is required" }
            val json = input.original?.raw?.let(::JSONObject) ?: JSONObject()
            json.put("title", input.title.trim())
                .put("description", input.description.trim())
                .put("deadline", input.deadline)
                .put("completed", input.original?.completed ?: false)
                .put("assignee", input.assignee?.toJson() ?: "")
                .put("link", JSONObject().put("teams", input.enterpriseId))
                .put("sync", JSONObject().put("type", input.enterpriseType).put("planetCode", input.enterprisePlanetCode))
            val saved = postDocument(baseUrl, "tasks", json, credentials, sessionCookie)
            json.put("_id", saved.getString("id")).put("_rev", saved.getString("rev"))
            if (input.assignee != null && input.assignee.userId != input.currentUserId) {
                runCatching {
                    notifyAssignee(baseUrl, credentials, sessionCookie, input.enterpriseId, input.assignee)
                }
            }
            json.toTaskDocument() ?: throw IOException("Invalid saved task")
        }
    }

    suspend fun fetchTask(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        taskId: String,
    ): Result<EnterpriseTaskDocument> = withContext(dispatcher) {
        runCatching {
            getJson(baseUrl, "tasks", taskId, credentials, sessionCookie).toTaskDocument()
                ?: throw IOException("Invalid task response")
        }
    }

    suspend fun setCompleted(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        task: EnterpriseTaskDocument,
        completed: Boolean,
    ): Result<EnterpriseTaskDocument> = updateTask(baseUrl, credentials, sessionCookie, task) { json ->
        json.put("completed", completed)
        if (completed) json.put("completedTime", System.currentTimeMillis()) else json.remove("completedTime")
    }

    suspend fun archiveTask(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        task: EnterpriseTaskDocument,
    ): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val json = JSONObject(task.raw).put("status", "archived")
            postDocument(baseUrl, "tasks", json, credentials, sessionCookie)
            Unit
        }
    }

    suspend fun unassignMemberTasks(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        enterpriseId: String,
        userId: String,
    ): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val selector = JSONObject().put("assignee.userId", userId).put("link.teams", enterpriseId)
            val docs = find(baseUrl, "tasks", selector, 1000, credentials, sessionCookie)
            if (docs.length() == 0) return@runCatching Unit
            val updates = JSONArray()
            for (index in 0 until docs.length()) updates.put(docs.getJSONObject(index).put("assignee", ""))
            postJson(baseUrl, "tasks/_bulk_docs", JSONObject().put("docs", updates), credentials, sessionCookie)
            Unit
        }
    }

    private suspend fun updateTask(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        task: EnterpriseTaskDocument,
        mutate: (JSONObject) -> Unit,
    ): Result<EnterpriseTaskDocument> = withContext(dispatcher) {
        runCatching {
            val current = getJson(baseUrl, "tasks", task.id, credentials, sessionCookie)
            mutate(current)
            val saved = postDocument(baseUrl, "tasks", current, credentials, sessionCookie)
            current.put("_rev", saved.getString("rev"))
            current.toTaskDocument() ?: throw IOException("Invalid updated task")
        }
    }

    private fun notifyAssignee(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        enterpriseId: String,
        assignee: EnterpriseTaskAssignee,
    ) {
        val link = "/enterprises/view/$enterpriseId"
        val selector = JSONObject()
            .put("link", link).put("type", "newTask").put("status", "unread")
            .put("user", assignee.userId).put("userPlanetCode", assignee.userPlanetCode)
        if (find(baseUrl, "notifications", selector, 1, credentials, sessionCookie).length() > 0) return
        val notification = JSONObject()
            .put("user", assignee.userId).put("userPlanetCode", assignee.userPlanetCode)
            .put("message", "You were assigned a new task").put("link", link)
            .put("linkParams", JSONObject().put("activeTab", "taskTab"))
            .put("type", "newTask").put("priority", 1).put("status", "unread")
            .put("time", System.currentTimeMillis())
        postJson(baseUrl, "notifications", notification, credentials, sessionCookie)
    }

    private fun find(
        baseUrl: String,
        database: String,
        selector: JSONObject,
        limit: Int,
        credentials: StoredCredentials?,
        sessionCookie: String?,
    ): JSONArray = postJson(
        baseUrl,
        "$database/_find",
        JSONObject().put("selector", selector).put("limit", limit),
        credentials,
        sessionCookie,
    ).optJSONArray("docs") ?: JSONArray()

    private fun postDocument(
        baseUrl: String,
        database: String,
        document: JSONObject,
        credentials: StoredCredentials?,
        sessionCookie: String?,
    ): JSONObject = try {
        postJson(baseUrl, database, document, credentials, sessionCookie)
    } catch (error: HttpStatusException) {
        if (error.code == 409) throw TaskConflictException() else throw error
    }

    private fun postJson(
        baseUrl: String,
        path: String,
        json: JSONObject,
        credentials: StoredCredentials?,
        sessionCookie: String?,
    ): JSONObject = execute(baseUrl, path, credentials, sessionCookie, json.toString())

    private fun getJson(
        baseUrl: String,
        database: String,
        id: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
    ): JSONObject = execute(baseUrl, "$database/$id", credentials, sessionCookie, null)

    private fun execute(
        baseUrl: String,
        path: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        payload: String?,
    ): JSONObject {
        val normalized = baseUrl.trim().trimEnd('/')
        if (normalized.isEmpty()) throw IOException("Missing server base URL")
        val url = "$normalized/db/$path".replace("/tasks/../", "/")
        val builder = Request.Builder().url(url).header("Content-Type", "application/json")
        if (payload == null) builder.get() else builder.post(payload.toRequestBody(JSON_MEDIA_TYPE))
        credentials?.let { builder.header("Authorization", Credentials.basic(it.username, it.password)) }
        sessionCookie?.takeIf(String::isNotBlank)?.let { builder.header("Cookie", it) }
        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            JSONObject(response.body.string())
        }
    }

    private fun JSONObject.toTaskDocument(): EnterpriseTaskDocument? {
        val id = optString("_id").takeIf(String::isNotBlank) ?: return null
        val assigneeJson = optJSONObject("assignee")
        return EnterpriseTaskDocument(
            id = id,
            revision = optString("_rev"),
            title = optString("title"),
            description = optString("description"),
            deadline = optLong("deadline"),
            completed = optBoolean("completed"),
            assignee = assigneeJson?.toAssignee(),
            raw = toString(),
        )
    }

    private fun JSONObject.toAssignee() = EnterpriseTaskAssignee(
        userId = optString("userId"),
        userPlanetCode = optString("userPlanetCode"),
        name = optString("name"),
        fullName = optJSONObject("userDoc")?.optString("fullName").orEmpty(),
    )

    private fun EnterpriseTaskAssignee.toJson() = JSONObject()
        .put("userId", userId).put("userPlanetCode", userPlanetCode).put("name", name)
        .put("userDoc", JSONObject().put("fullName", fullName))

    sealed interface EnterpriseTasksSnapshot {
        data object AccessDenied : EnterpriseTasksSnapshot
        data class Success(
            val enterpriseId: String,
            val enterpriseType: String,
            val enterprisePlanetCode: String,
            val canManage: Boolean,
            val tasks: List<EnterpriseTaskDocument>,
        ) : EnterpriseTasksSnapshot
    }

    private class HttpStatusException(val code: Int) : IOException("Unexpected response $code")
    class TaskConflictException : IOException("Task changed on the server")

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

data class EnterpriseTaskDocument(
    val id: String,
    val revision: String,
    val title: String,
    val description: String,
    val deadline: Long,
    val completed: Boolean,
    val assignee: EnterpriseTaskAssignee?,
    val raw: String,
)

data class EnterpriseTaskAssignee(
    val userId: String,
    val userPlanetCode: String,
    val name: String,
    val fullName: String,
)

data class SaveEnterpriseTask(
    val enterpriseId: String,
    val enterpriseType: String,
    val enterprisePlanetCode: String,
    val title: String,
    val description: String,
    val deadline: Long,
    val assignee: EnterpriseTaskAssignee?,
    val currentUserId: String,
    val original: EnterpriseTaskDocument? = null,
)
