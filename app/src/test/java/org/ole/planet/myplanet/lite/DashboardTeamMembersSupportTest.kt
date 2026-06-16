package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.imageview.ShapeableImageView
import android.widget.ImageButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import android.view.View
import android.widget.LinearLayout
import org.ole.planet.myplanet.lite.databinding.ItemTeamMemberBinding
import org.ole.planet.myplanet.lite.dashboard.TeamMemberDetails

import org.junit.Assert.assertNotNull
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
        context = ApplicationProvider.getApplicationContext<Context>().apply {
            setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight)
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
    fun testTeamMemberViewHolderBind() {
        val binding = ItemTeamMemberBinding.inflate(
            android.view.LayoutInflater.from(context)
        )

        val member = TeamMemberDetails(
            username = "testuser",
            fullName = "Test User",
            hasAvatar = true,
            isLeader = true,
            membership = null
        )

        var avatarBinderCalled = false
        val avatarBinder: (ImageView, String?, Boolean) -> Unit = { imageView, username, hasAvatar ->
            assertEquals(binding.teamMemberAvatar, imageView)
            assertEquals("testuser", username)
            assertTrue(hasAvatar)
            avatarBinderCalled = true
        }

        var onMemberClickedCalled = false
        val onMemberClicked: (TeamMemberDetails) -> Unit = { m ->
            assertEquals(member, m)
            onMemberClickedCalled = true
        }

        var onRemoveMemberClickedCalled = false
        val onRemoveMemberClicked: (TeamMemberDetails) -> Unit = { m ->
            assertEquals(member, m)
            onRemoveMemberClickedCalled = true
        }

        val viewHolder = TeamMemberViewHolder(
            binding = binding,
            avatarBinder = avatarBinder,
            onMemberClicked = onMemberClicked,
            onRemoveMemberClicked = onRemoveMemberClicked
        )

        viewHolder.bind(member = member, showRemoveAction = true, currentUsername = "otheruser")

        assertTrue(avatarBinderCalled)
        assertEquals("Test User", binding.teamMemberName.text)
        assertEquals(context.getString(R.string.dashboard_team_member_profile_username_format, "testuser"), binding.teamMemberUsername.text) // Restore it

        assertTrue(binding.teamMemberRemoveButton.visibility == View.VISIBLE)
        assertEquals(context.getString(R.string.dashboard_team_members_leader_role), binding.teamMemberRole.text)


        // Test clicks
        binding.root.performClick()
        assertTrue(onMemberClickedCalled)
        onMemberClickedCalled = false

        binding.teamMemberRemoveButton.performClick()
        assertTrue(onRemoveMemberClickedCalled)
    }


    @Test
    fun testTeamMemberViewHolderBind_nonLeaderAndIsCurrentUser() {
        val binding = ItemTeamMemberBinding.inflate(
            android.view.LayoutInflater.from(context)
        )

        val member = TeamMemberDetails(
            username = "testuser",
            fullName = "", // blank full name to fallback to username
            hasAvatar = false,
            isLeader = false,
            membership = null
        )

        val viewHolder = TeamMemberViewHolder(
            binding = binding,
            avatarBinder = { _, _, _ -> },
            onMemberClicked = {},
            onRemoveMemberClicked = {}
        )

        viewHolder.bind(member = member, showRemoveAction = true, currentUsername = "testuser")

        // Name should fallback to username since fullName is blank
        assertEquals("testuser", binding.teamMemberName.text)

        // Role should be member role
        assertEquals(context.getString(R.string.dashboard_team_members_member_role), binding.teamMemberRole.text)

        // Remove button should be invisible because currentUsername == member.username
        assertFalse(binding.teamMemberRemoveButton.visibility == View.VISIBLE)
    }


    @Test
    fun testTeamMemberViewHolderBind_noUsernameOrFullName() {
        val binding = ItemTeamMemberBinding.inflate(
            android.view.LayoutInflater.from(context)
        )

        val member = TeamMemberDetails(
            username = null,
            fullName = null,
            hasAvatar = false,
            isLeader = false,
            membership = null
        )

        val viewHolder = TeamMemberViewHolder(
            binding = binding,
            avatarBinder = { _, _, _ -> },
            onMemberClicked = {},
            onRemoveMemberClicked = {}
        )

        viewHolder.bind(member = member, showRemoveAction = true, currentUsername = "testuser")

        assertEquals(context.getString(R.string.dashboard_team_members_unknown), binding.teamMemberName.text)
        assertEquals(context.getString(R.string.dashboard_team_members_unknown_username), binding.teamMemberUsername.text)
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
