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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.ole.planet.myplanet.lite.dashboard.DashboardAvatarLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository
import org.ole.planet.myplanet.lite.dashboard.JoinRequestDocument
import org.ole.planet.myplanet.lite.databinding.ItemInviteMemberBinding
import android.view.LayoutInflater
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
        context = androidx.appcompat.view.ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            com.google.android.material.R.style.Theme_MaterialComponents_DayNight
        )

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
    fun testInviteMemberViewHolder_bind() {
        val binding = ItemInviteMemberBinding.inflate(LayoutInflater.from(context))
        val avatarLoader = mock<DashboardAvatarLoader>()
        var clickedCandidate: InviteCandidate? = null
        val onAddClicked: (InviteCandidate) -> Unit = { clickedCandidate = it }

        val holder = InviteMemberViewHolder(binding, avatarLoader, onAddClicked)
        val candidate = InviteCandidate(
            name = "Test Name",
            username = "testuser",
            planetCode = "planet",
            hasAvatar = true,
            colorRes = R.color.blueOle
        )

        holder.bind(candidate, isDisabled = false)

        assertEquals("Test Name", binding.inviteMemberName.text.toString())
        assertEquals("@testuser", binding.inviteMemberUsername.text.toString())
        assertTrue(binding.inviteMemberAdd.isEnabled)
        assertEquals(1f, binding.inviteMemberAdd.alpha)

        verify(avatarLoader).bind(binding.inviteMemberAvatar, "testuser", true)

        binding.inviteMemberAdd.performClick()
        assertEquals(candidate, clickedCandidate)
    }

    @Test
    fun testInviteMemberViewHolder_bind_disabled() {
        val binding = ItemInviteMemberBinding.inflate(LayoutInflater.from(context))
        val avatarLoader = mock<DashboardAvatarLoader>()
        var clickedCandidate: InviteCandidate? = null
        val onAddClicked: (InviteCandidate) -> Unit = { clickedCandidate = it }

        val holder = InviteMemberViewHolder(binding, avatarLoader, onAddClicked)
        val candidate = InviteCandidate(
            name = "Test Name Disabled",
            username = "testuser_disabled",
            planetCode = "planet",
            hasAvatar = false,
            colorRes = R.color.blueOle
        )

        holder.bind(candidate, isDisabled = true)

        assertEquals("Test Name Disabled", binding.inviteMemberName.text.toString())
        assertEquals("@testuser_disabled", binding.inviteMemberUsername.text.toString())
        assertFalse(binding.inviteMemberAdd.isEnabled)
        assertEquals(0.5f, binding.inviteMemberAdd.alpha)

        verify(avatarLoader).bind(binding.inviteMemberAvatar, "testuser_disabled", true)

        binding.inviteMemberAdd.performClick()
        assertEquals(null, clickedCandidate)
    }
}
