import re

file_path3 = "app/src/test/java/org/ole/planet/myplanet/lite/dashboard/DashboardResourcesRepositoryTest.kt"
with open(file_path3, "r") as f:
    content3 = f.read()

new_test = """
    @Test
    fun createAndUploadResourceSequence_success() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\\"id\\": \\"res1\\", \\"rev\\": \\"1-abc\\"}")) // Create
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\\"id\\": \\"res1\\", \\"rev\\": \\"2-def\\"}")) // Update
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\\"ok\\": true}")) // Upload
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\\"id\\": \\"team1\\", \\"rev\\": \\"1-ghi\\"}")) // Team link

        val payload = JSONObject().put("title", "Test Resource")
        val request = DashboardResourcesRepository.CreateAndUploadResourceRequest(
            baseUrl = server.url("/").toString(),
            sessionCookie = "test-cookie",
            credentials = null,
            payload = payload,
            fileExtension = "pdf",
            mimeType = "application/pdf",
            bytes = byteArrayOf(1, 2, 3),
            teamId = "team-abc",
            planetCode = "planet-xyz"
        )

        val result = repository.createAndUploadResourceSequence(request)
        assertEquals(true, result.isSuccess)

        // Verify Create Request
        val createReq = server.takeRequest()
        assertEquals("/db/resources", createReq.path)
        assertEquals("POST", createReq.method)

        // Verify Update Request
        val updateReq = server.takeRequest()
        assertEquals("/db/resources/res1", updateReq.path)
        assertEquals("PUT", updateReq.method)
        val updateBody = JSONObject(updateReq.body.readUtf8())
        assertEquals("res1.pdf", updateBody.getString("filename"))
        assertEquals("application/pdf", updateBody.getString("mediaType"))

        // Verify Upload Request
        val uploadReq = server.takeRequest()
        assertEquals("/db/resources/res1/res1.pdf?rev=2-def", uploadReq.path)
        assertEquals("PUT", uploadReq.method)
        assertEquals("application/pdf", uploadReq.getHeader("Content-Type"))

        // Verify Team Link Request
        val teamReq = server.takeRequest()
        assertEquals("/db/teams", teamReq.path)
        assertEquals("POST", teamReq.method)
        val teamBody = JSONObject(teamReq.body.readUtf8())
        assertEquals("resourceLink", teamBody.getString("docType"))
        assertEquals("res1", teamBody.getString("resourceId"))
        assertEquals("team-abc", teamBody.getString("teamId"))
    }

    @Test
    fun createAndUploadResourceSequence_ignoresTeamLinkFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\\"id\\": \\"res1\\", \\"rev\\": \\"1-abc\\"}")) // Create
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\\"id\\": \\"res1\\", \\"rev\\": \\"2-def\\"}")) // Update
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\\"ok\\": true}")) // Upload
        server.enqueue(MockResponse().setResponseCode(500).setBody("{\\"error\\": \\"server_error\\"}")) // Team link fails

        val payload = JSONObject().put("title", "Test Resource")
        val request = DashboardResourcesRepository.CreateAndUploadResourceRequest(
            baseUrl = server.url("/").toString(),
            sessionCookie = "test-cookie",
            credentials = null,
            payload = payload,
            fileExtension = "pdf",
            mimeType = "application/pdf",
            bytes = byteArrayOf(1, 2, 3),
            teamId = "team-abc",
            planetCode = "planet-xyz"
        )

        val result = repository.createAndUploadResourceSequence(request)
        assertEquals(true, result.isSuccess) // Should still succeed because team link is ignored

        server.takeRequest() // Create
        server.takeRequest() // Update
        server.takeRequest() // Upload
        server.takeRequest() // Team link
    }
}"""
content3 = content3.replace("}\n", new_test + "\n")

with open(file_path3, "w") as f:
    f.write(content3)
