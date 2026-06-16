package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowAlertDialog
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.assertNotNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import android.content.SharedPreferences
import org.junit.After
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository
import org.ole.planet.myplanet.lite.dashboard.JoinRequestDocument
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class DashboardTeamMembersSupportTest {
    private lateinit var fragment: Fragment
    private lateinit var context: Context
    private lateinit var repository: DashboardTeamsRepository

    @Before
    fun setUp() {
        val mockSharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { mockSharedPreferences.getString(any(), any()) } returns "en"

        mockkObject(SecurePreferencesProvider)
        every { SecurePreferencesProvider.getServerPreferences(any()) } returns mockSharedPreferences

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

    @After
    fun tearDown() {
        unmockkObject(SecurePreferencesProvider)
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
    fun testConfirmAcceptJoinRequestDialog_showsAlertDialog() {
        val testActivity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
        val mockFragment = mock<Fragment> {
            on { requireContext() } doReturn testActivity
            on { getString(any(), any()) } doReturn "Are you sure you want to accept user Name?"
        }

        val request = TeamJoinRequestUiModel(
            id = "req_1",
            username = "username",
            fullName = "Name",
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

        var callbackTriggered = false
        confirmAcceptJoinRequestDialog(mockFragment, request) { result ->
            callbackTriggered = true
            assertEquals("req_1", result.id)
        }

        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as? androidx.appcompat.app.AlertDialog
        assertNotNull("AlertDialog should be shown", dialog)
        assertTrue("Dialog should be showing", dialog!!.isShowing)

        val button = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
        assertNotNull("Positive button should exist", button)

        // Trigger positive button and verify callback
        button.performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue("Callback should be triggered on accept", callbackTriggered)
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
}
