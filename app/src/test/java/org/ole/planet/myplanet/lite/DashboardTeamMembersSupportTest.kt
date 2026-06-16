package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.assertEquals
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository
import org.ole.planet.myplanet.lite.dashboard.JoinRequestDocument
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class DashboardTeamMembersSupportTest {
    private lateinit var fragment: Fragment
    private lateinit var context: Context
    private lateinit var repository: DashboardTeamsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>().apply {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        }

        val lifecycleOwner = mock<LifecycleOwner>()
        val lifecycle = LifecycleRegistry(lifecycleOwner)
        lifecycle.currentState = Lifecycle.State.RESUMED
        whenever(lifecycleOwner.lifecycle).thenReturn(lifecycle)

        fragment = mock {
            on { requireContext() } doReturn context
            on { getString(any()) } doReturn "Mock Error"
            on { getString(any(), any()) } doReturn "Mock Error with arg"
            on { viewLifecycleOwner } doReturn lifecycleOwner
        }
        repository = mock()
    }

    @Test
    fun testRunAcceptJoinRequest_invalidInputsShowsToast() {
        val request = TeamJoinRequestUiModel(
            id = "id",
            username = "user",
            fullName = "User Name",
            hasAvatar = false,
            request = JoinRequestDocument(
                id = null,
                revision = null,
                docType = null,
                teamId = null,
                teamType = null,
                teamPlanetCode = null,
                userId = null,
                userPlanetCode = null
            )
        )

        runAcceptJoinRequest(
            AcceptJoinRequestParams(
                fragment = fragment,
                repository = repository,
                baseUrl = null,
                credentials = null,
                sessionCookie = null,
                request = request,
                currentTeamPlanetCode = null,
                serverPlanetCode = null,
                onReload = {}
            )
        )

        val latestToast = ShadowToast.getLatestToast()
        assertNotNull(latestToast)
        verifyNoInteractions(repository)
    }

    @Test
    fun testRunRejectJoinRequest_invalidInputsShowsToast() {
        val request = TeamJoinRequestUiModel(
            id = "id",
            username = "user",
            fullName = "User Name",
            hasAvatar = false,
            request = JoinRequestDocument(
                id = null,
                revision = null,
                docType = null,
                teamId = null,
                teamType = null,
                teamPlanetCode = null,
                userId = null,
                userPlanetCode = null
            )
        )

        val context = DashboardTeamActionContext(
            fragment = fragment,
            repository = repository,
            baseUrl = null,
            credentials = null,
            sessionCookie = null
        )

        runRejectJoinRequest(
            context = context,
            request = request,
            onReload = {}
        )

        val latestToast = ShadowToast.getLatestToast()
        assertNotNull(latestToast)
        verifyNoInteractions(repository)
    }

    @Test
    fun testRunRemoveTeamMember_invalidInputsShowsToast() {
        val member = org.ole.planet.myplanet.lite.dashboard.TeamMemberDetails(
            username = "user",
            fullName = "User",
            hasAvatar = false,
            isLeader = false,
            membership = null
        )

        val context = DashboardTeamActionContext(
            fragment = fragment,
            repository = repository,
            baseUrl = null,
            credentials = null,
            sessionCookie = null
        )

        runRemoveTeamMember(
            context = context,
            teamId = null,
            member = member,
            displayName = "User",
            onStart = {},
            onStop = {},
            onReload = {}
        )

        val latestToast = ShadowToast.getLatestToast()
        assertNotNull(latestToast)
        verifyNoInteractions(repository)
    }

    @Test
    fun testAdaptersInstantiation() {
        val membersAdapter = TeamMembersAdapter(
            avatarBinder = { _, _, _ -> },
            onMemberClicked = {},
            onRemoveMemberClicked = {}
        )
        assertNotNull(membersAdapter)

        val requestsAdapter = TeamJoinRequestsAdapter(
            avatarBinder = { _, _, _ -> },
            onAcceptClicked = {},
            onRejectClicked = {}
        )
        assertNotNull(requestsAdapter)
    }

    @Test
    fun testConfirmMemberRemovalDialog() {
        val member = org.ole.planet.myplanet.lite.dashboard.TeamMemberDetails(
            username = "user1",
            fullName = "Test User",
            hasAvatar = false,
            isLeader = false,
            membership = null
        )

        var removedMember: org.ole.planet.myplanet.lite.dashboard.TeamMemberDetails? = null
        var removedName: String? = null

        val onRemove: (org.ole.planet.myplanet.lite.dashboard.TeamMemberDetails, String) -> Unit = { mem, name ->
            removedMember = mem
            removedName = name
        }

        // Action
        confirmMemberRemovalDialog(fragment, member, onRemove)

        // Verify Dialog
        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)

        // Click Positive Button
        dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals(member, removedMember)
        assertEquals("Test User", removedName)
    }
}
