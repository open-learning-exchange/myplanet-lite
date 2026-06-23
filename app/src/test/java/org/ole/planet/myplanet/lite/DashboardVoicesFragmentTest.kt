package org.ole.planet.myplanet.lite

import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.core.app.ApplicationProvider
import io.noties.markwon.Markwon
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository.NewsDocument
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], instrumentedPackages = ["androidx.loader.content"])
class DashboardVoicesFragmentTest {

    @After
    fun tearDown() {
        UserProfileDatabase.resetForTesting(ApplicationProvider.getApplicationContext())
        SecurePreferencesProvider.resetForTesting()
    }

    @Test
    fun `newInstanceForTeam creates fragment with correct arguments`() {
        val teamId = "team_123"
        val teamName = "Test Team"

        val fragment = DashboardVoicesFragment.newInstanceForTeam(teamId, teamName)

        val args = fragment.arguments
        assertNotNull(args)
        assertEquals(teamId, args?.getString("arg_team_id"))
        assertEquals(teamName, args?.getString("arg_team_name"))
    }

    private fun mockPreferencesProviderAndLaunch(
        args: Bundle?,
        block: (DashboardVoicesFragment, SharedPreferences) -> Unit
    ) {
        val mockPrefs = Mockito.mock(SharedPreferences::class.java)
        val mockEditor = Mockito.mock(SharedPreferences.Editor::class.java)

        Mockito.`when`(
            mockPrefs.getString(
                Mockito.anyString(),
                Mockito.nullable(String::class.java)
            )
        ).thenReturn(null)

        Mockito.`when`(
            mockPrefs.getBoolean(
                Mockito.eq("survey_translations_enabled"),
                Mockito.anyBoolean()
            )
        ).thenReturn(true)

        Mockito.`when`(
            mockPrefs.getInt(
                Mockito.eq("voice_page_size"),
                Mockito.anyInt()
            )
        ).thenReturn(20)

        Mockito.`when`(mockPrefs.edit()).thenReturn(mockEditor)

        SecurePreferencesProvider.injectedPreferences = mockPrefs

        FragmentScenario.launchInContainer(
            DashboardVoicesFragment::class.java,
            args,
            R.style.Theme_MyPlanetLite
        ).use { scenario ->
            scenario.onFragment { fragment ->
                block(fragment, mockPrefs)
            }

            ShadowLooper.idleMainLooper()
        }

        SecurePreferencesProvider.injectedPreferences = null
    }

    private fun mockPreferencesProviderAndLaunch(
        args: Bundle?,
        block: (DashboardVoicesFragment) -> Unit
    ) {
        mockPreferencesProviderAndLaunch(args) { fragment, _ ->
            block(fragment)
        }
    }

    @Test
    fun `loadMore returns early if baseUrl is null`() {
        mockPreferencesProviderAndLaunch(null) { fragment ->
            // In setup baseUrl is initially null
            fragment.loadMore(10)

            val isLoadingField = fragment.javaClass.getDeclaredField("isLoading")
            isLoadingField.isAccessible = true
            assertFalse(isLoadingField.getBoolean(fragment))
        }
    }

    @Test
    fun `loadMore returns early if isLoading is true`() {
        mockPreferencesProviderAndLaunch(null) { fragment ->
            val baseUrlField = fragment.javaClass.getDeclaredField("baseUrl")
            baseUrlField.isAccessible = true
            baseUrlField.set(fragment, "http://example.com")

            val isLoadingField = fragment.javaClass.getDeclaredField("isLoading")
            isLoadingField.isAccessible = true
            isLoadingField.setBoolean(fragment, true)

            fragment.loadMore(10)

            val fetchJobField = fragment.javaClass.getDeclaredField("fetchJob")
            fetchJobField.isAccessible = true
            val job = fetchJobField.get(fragment)

            org.junit.Assert.assertNull(job)
        }
    }

    @Test
    fun `loadMore returns early if hasMore is false`() {
        mockPreferencesProviderAndLaunch(null) { fragment ->
            val baseUrlField = fragment.javaClass.getDeclaredField("baseUrl")
            baseUrlField.isAccessible = true
            baseUrlField.set(fragment, "http://example.com")

            val hasMoreField = fragment.javaClass.getDeclaredField("hasMore")
            hasMoreField.isAccessible = true
            hasMoreField.setBoolean(fragment, false)

            fragment.loadMore(10)

            val fetchJobField = fragment.javaClass.getDeclaredField("fetchJob")
            fetchJobField.isAccessible = true
            val job = fetchJobField.get(fragment)

            org.junit.Assert.assertNull(job)

            val isLoadingField = fragment.javaClass.getDeclaredField("isLoading")
            isLoadingField.isAccessible = true
            assertFalse(isLoadingField.getBoolean(fragment))
        }
    }

    @Test
    fun `isTeamFeedFor returns true when both id and name match`() {
        val teamId = "team_123"
        val teamName = "Test Team"

        val args = Bundle().apply {
            putString("arg_team_id", teamId)
            putString("arg_team_name", teamName)
        }

        mockPreferencesProviderAndLaunch(args) { fragment ->
            assertTrue(fragment.isTeamFeedFor(teamId, teamName))
        }
    }

    @Test
    fun `isTeamFeedFor returns true with case insensitive match`() {
        val teamId = "team_123"
        val teamName = "Test Team"

        val args = Bundle().apply {
            putString("arg_team_id", teamId)
            putString("arg_team_name", teamName)
        }

        mockPreferencesProviderAndLaunch(args) { fragment ->
            assertTrue(fragment.isTeamFeedFor("TEAM_123", "test team"))
        }
    }

    @Test
    fun `isTeamFeedFor returns false when id does not match`() {
        val teamId = "team_123"
        val teamName = "Test Team"

        val args = Bundle().apply {
            putString("arg_team_id", teamId)
            putString("arg_team_name", teamName)
        }

        mockPreferencesProviderAndLaunch(args) { fragment ->
            assertFalse(fragment.isTeamFeedFor("different_id", teamName))
        }
    }

    @Test
    fun `isTeamFeedFor returns false when name does not match`() {
        val teamId = "team_123"
        val teamName = "Test Team"

        val args = Bundle().apply {
            putString("arg_team_id", teamId)
            putString("arg_team_name", teamName)
        }

        mockPreferencesProviderAndLaunch(args) { fragment ->
            assertFalse(fragment.isTeamFeedFor(teamId, "Different Name"))
        }
    }

    @Test
    fun `isTeamFeedFor returns false when fragment has no team arguments`() {
        mockPreferencesProviderAndLaunch(null) { fragment ->
            assertFalse(fragment.isTeamFeedFor("team_123", "Test Team"))
        }
    }

    @Test
    fun `onPageSizeChanged updates pageSize and calls loadInitial when size changes`() {
        mockPreferencesProviderAndLaunch(null) { fragment, mockPrefs ->
            fragment.pageSize = 20

            val dummyDoc = DashboardNewsRepository.NewsDocument(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null
            )

            val dummyItem = DashboardVoicesFragment.DashboardNewsItem(
                "id",
                "author",
                null,
                "meta",
                null,
                false,
                emptyList(),
                0,
                0L,
                false,
                false,
                false,
                dummyDoc
            )

            fragment.items.add(dummyItem)

            val newPageSize = 40

            Mockito.`when`(
                mockPrefs.getInt(
                    Mockito.eq("voice_page_size"),
                    Mockito.anyInt()
                )
            ).thenReturn(newPageSize)

            fragment.onPageSizeChanged(newPageSize)

            assertEquals(newPageSize, fragment.pageSize)
            assertTrue(fragment.items.isEmpty())
        }
    }

    @Test
    fun `onPageSizeChanged does nothing when size is unchanged`() {
        mockPreferencesProviderAndLaunch(null) { fragment ->
            fragment.pageSize = 20

            val dummyDoc = DashboardNewsRepository.NewsDocument(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null
            )

            val dummyItem = DashboardVoicesFragment.DashboardNewsItem(
                "id",
                "author",
                null,
                "meta",
                null,
                false,
                emptyList(),
                0,
                0L,
                false,
                false,
                false,
                dummyDoc
            )

            fragment.items.add(dummyItem)

            fragment.onPageSizeChanged(20)

            assertFalse(fragment.items.isEmpty())
        }
    }

    @Test
    fun `adapter getPositionsForUsername returns correct indices`() {
        val mockMarkwon = Mockito.mock(Markwon::class.java)

        val adapter = DashboardVoicesFragment.DashboardNewsAdapter(
            markwon = mockMarkwon,
            avatarBinder = { _, _, _ -> },
            imageBinder = { _, _ -> },
            onImageClicked = { _, _ -> },
            onPostClicked = {},
            onDeleteClicked = {},
            onShareClicked = {},
            onEditClicked = {},
            onAuthorClicked = {}
        )

        val createDummyItem = { id: String, username: String? ->
            DashboardVoicesFragment.DashboardNewsItem(
                id = id,
                author = "Author $id",
                username = username,
                metadata = "Meta",
                message = "Msg",
                hasAvatar = false,
                imagePaths = emptyList(),
                commentCount = 0,
                timestamp = 0L,
                canEdit = false,
                canDelete = false,
                canShare = false,
                document = NewsDocument(
                    id = id,
                    revision = null,
                    docType = null,
                    time = null,
                    createdOn = null,
                    parentCode = null,
                    user = null,
                    replyTo = null,
                    viewIn = null,
                    messageType = null,
                    messagePlanetCode = null,
                    message = null,
                    images = null,
                    updatedDate = null,
                    isDeleted = null
                )
            )
        }

        val items = listOf(
            createDummyItem("1", "alice"),
            createDummyItem("2", "Bob"),
            createDummyItem("3", "ALICE"),
            createDummyItem("4", null),
            createDummyItem("5", "bob")
        )

        adapter.submitList(items)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertTrue(adapter.isIndexAvailable())

        val alicePositions = adapter.getPositionsForUsername("alice")
        assertNotNull(alicePositions)
        assertEquals(listOf(0, 2), alicePositions)

        val bobPositions = adapter.getPositionsForUsername("BOB")
        assertNotNull(bobPositions)
        assertEquals(listOf(1, 4), bobPositions)

        val charliePositions = adapter.getPositionsForUsername("charlie")
        assertEquals(emptyList<Int>(), charliePositions)

        val nullPositions = adapter.getPositionsForUsername("null")
        assertEquals(emptyList<Int>(), nullPositions)
    }
}
