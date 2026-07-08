package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.Robolectric
import org.ole.planet.myplanet.lite.databinding.ItemTeamMemberBinding
import org.ole.planet.myplanet.lite.dashboard.TeamMemberDetails
import org.junit.Assert.assertNotNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import android.content.SharedPreferences
import android.os.Looper
import org.junit.After
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import androidx.lifecycle.coroutineScope
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.dashboard.DashboardAvatarLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository
import org.ole.planet.myplanet.lite.databinding.ItemTeamJoinRequestBinding
import org.ole.planet.myplanet.lite.dashboard.JoinRequestDocument
import org.ole.planet.myplanet.lite.databinding.ItemInviteMemberBinding
import org.ole.planet.myplanet.lite.databinding.DialogInviteMembersBinding
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.dashboard.UserDocument
import org.robolectric.annotation.Config
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import androidx.appcompat.view.ContextThemeWrapper

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
        val member = TeamMemberDetails(
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
            LayoutInflater.from(context)
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
        assertEquals(
            context.getString(R.string.dashboard_team_member_profile_username_format, "testuser"),
            binding.teamMemberUsername.text
        )

        assertTrue(binding.teamMemberRemoveButton.visibility == View.VISIBLE)
        assertEquals(
            context.getString(R.string.dashboard_team_members_leader_role),
            binding.teamMemberRole.text
        )

        binding.root.performClick()
        assertTrue(onMemberClickedCalled)
        onMemberClickedCalled = false

        binding.teamMemberRemoveButton.performClick()
        assertTrue(onRemoveMemberClickedCalled)
    }

    @Test
    fun testTeamMemberViewHolderBind_nonLeaderAndIsCurrentUser() {
        val binding = ItemTeamMemberBinding.inflate(
            LayoutInflater.from(context)
        )

        val member = TeamMemberDetails(
            username = "testuser",
            fullName = "",
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

        assertEquals("testuser", binding.teamMemberName.text)
        assertEquals(
            context.getString(R.string.dashboard_team_members_member_role),
            binding.teamMemberRole.text
        )
        assertFalse(binding.teamMemberRemoveButton.visibility == View.VISIBLE)
    }

    @Test
    fun testTeamMemberViewHolderBind_noUsernameOrFullName() {
        val binding = ItemTeamMemberBinding.inflate(
            LayoutInflater.from(context)
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

        assertEquals(
            context.getString(R.string.dashboard_team_members_unknown),
            binding.teamMemberName.text
        )
        assertEquals(
            context.getString(R.string.dashboard_team_members_unknown_username),
            binding.teamMemberUsername.text
        )
    }

    @Test
    fun testConfirmAcceptJoinRequestDialog_showsAlertDialog() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            val testActivity = controller.get()
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

            val dialog =
                org.robolectric.shadows.ShadowDialog.getLatestDialog() as? androidx.appcompat.app.AlertDialog
            assertNotNull("AlertDialog should be shown", dialog)
            assertTrue("Dialog should be showing", dialog!!.isShowing)

            val button = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            assertNotNull("Positive button should exist", button)

            button.performClick()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Callback should be triggered on accept", callbackTriggered)
            dialog.dismiss()
        } finally {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            controller.pause().stop().destroy()
        }
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
    fun testConfirmRejectJoinRequestDialog() {
        val request = TeamJoinRequestUiModel(
            id = "id",
            username = "testuser",
            fullName = "Test User",
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

        var callbackCalled = false
        confirmRejectJoinRequestDialog(fragment, request) {
            callbackCalled = true
            assertEquals(request, it)
        }

        verify(fragment).getString(
            eq(R.string.dashboard_team_members_request_reject_confirm_message),
            eq("Test User")
        )

        val dialog = androidx.appcompat.app.AlertDialog::class.java.cast(
            org.robolectric.shadows.ShadowDialog.getLatestDialog()
        )
        assertNotNull(dialog)

        val button = dialog?.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
        assertNotNull(button)
        button?.performClick()

        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue(callbackCalled)
    }

    @Test
    fun testAnimateFabClickSupport() {
        val themedContext = android.view.ContextThemeWrapper(
            context,
            com.google.android.material.R.style.Theme_MaterialComponents
        )
        val fab = FloatingActionButton(themedContext)
        fab.rotation = 10f

        animateFabClickSupport(fab)

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(0f, fab.rotation)
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

    @Test
    fun testTeamJoinRequestViewHolder_bind() {
        val binding = ItemTeamJoinRequestBinding.inflate(LayoutInflater.from(context))

        val avatarBinder: (ImageView, String?, Boolean) -> Unit = mock()
        val onAcceptClicked: (TeamJoinRequestUiModel) -> Unit = mock()
        val onRejectClicked: (TeamJoinRequestUiModel) -> Unit = mock()

        val viewHolder = TeamJoinRequestViewHolder(
            binding = binding,
            avatarBinder = avatarBinder,
            onAcceptClicked = onAcceptClicked,
            onRejectClicked = onRejectClicked
        )

        val requestDocument = JoinRequestDocument(
            id = "doc1",
            revision = "rev1",
            docType = "join_request",
            teamId = "team1",
            teamType = "team",
            teamPlanetCode = "planet",
            userId = "user1",
            userPlanetCode = "planet"
        )

        val uiModel = TeamJoinRequestUiModel(
            id = "doc1",
            username = "testuser",
            fullName = "Test User",
            hasAvatar = true,
            request = requestDocument
        )

        viewHolder.bind(uiModel)

        assertEquals("Test User", binding.teamJoinRequestName.text.toString())
        assertEquals("@testuser", binding.teamJoinRequestUsername.text.toString())

        verify(avatarBinder).invoke(eq(binding.teamJoinRequestAvatar), eq("testuser"), eq(true))

        binding.teamJoinRequestAcceptButton.performClick()
        verify(onAcceptClicked).invoke(eq(uiModel))

        binding.teamJoinRequestRejectButton.performClick()
        verify(onRejectClicked).invoke(eq(uiModel))
    }

    @Test
    fun testDashboardTeamMembersInviteDialogController_show() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar)
        val lifecycleOwner = mock<LifecycleOwner>()
        val lifecycle = LifecycleRegistry(lifecycleOwner)
        lifecycle.currentState = Lifecycle.State.RESUMED
        whenever(lifecycleOwner.lifecycle).thenReturn(lifecycle)

        val fragment = mock<Fragment> {
            on { requireContext() } doReturn context
            on { viewLifecycleOwner } doReturn lifecycleOwner
            on { getString(any()) } doReturn "Mock Error"
            on { getString(any(), any()) } doReturn "Mock Error with arg"
        }

        val dialogBinding = DialogInviteMembersBinding.inflate(LayoutInflater.from(context))
        runBlocking {
            whenever(
                repository.fetchAllUsers(
                    any<org.ole.planet.myplanet.lite.dashboard.FetchUsersRequest>()
                )
            ).thenReturn(Result.success(emptyList<UserDocument>()))
        }

        val controller = DashboardTeamMembersInviteDialogController(
            fragment = fragment,
            dialogBinding = dialogBinding,
            repository = repository,
            lifecycleScope = lifecycle.coroutineScope,
            avatarLoader = null,
            base = "base",
            creds = mock<StoredCredentials>(),
            teamId = "teamId",
            teamPlanetCode = "planet",
            teamType = "type",
            sessionCookie = "cookie",
            serverPlanetCode = "server",
            serverParentCode = "parent",
            currentMembersProvider = { emptyList() },
            onReload = {}
        )

        controller.show()

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertNotNull(dialogBinding.inviteMembersList.adapter)
        assertNotNull(dialogBinding.inviteMembersList.layoutManager)
    }



    @Test
    fun testDashboardTeamMembersInviteDialogController_debounceSearch() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar)
        val lifecycleOwner = mock<LifecycleOwner>()
        val lifecycle = LifecycleRegistry(lifecycleOwner)
        lifecycle.currentState = Lifecycle.State.RESUMED
        whenever(lifecycleOwner.lifecycle).thenReturn(lifecycle)

        val fragment = mock<Fragment> {
            on { requireContext() } doReturn context
            on { viewLifecycleOwner } doReturn lifecycleOwner
            on { getString(any()) } doReturn "Mock Error"
            on { getString(any(), any()) } doReturn "Mock Error with arg"
        }

        val dialogBinding = DialogInviteMembersBinding.inflate(LayoutInflater.from(context))

        whenever(
            repository.fetchAllUsers(any<org.ole.planet.myplanet.lite.dashboard.FetchUsersRequest>())
        ).thenReturn(Result.success(emptyList<UserDocument>()))

        // Setup controller with custom lifecycleScope for the test
            // Mock LifecycleCoroutineScope to provide our test dispatcher
    val testScope = mock<androidx.lifecycle.LifecycleCoroutineScope> {
        on { coroutineContext } doReturn testDispatcher
    }
        val controller = DashboardTeamMembersInviteDialogController(
            fragment = fragment,
            dialogBinding = dialogBinding,
            repository = repository,
            lifecycleScope = testScope,
            avatarLoader = null,
            base = "base",
            creds = mock<StoredCredentials>(),
            teamId = "teamId",
            teamPlanetCode = "planet",
            teamType = "type",
            sessionCookie = "cookie",
            serverPlanetCode = "server",
            serverParentCode = "parent",
            currentMembersProvider = { emptyList() },
            onReload = {}
        )

        controller.show()

        // Let the initial fetch run
        runCurrent()
        advanceTimeBy(100)

        // Reset invocations so we can count the ones from searching
        org.mockito.kotlin.clearInvocations(repository)
        whenever(
            repository.fetchAllUsers(any<org.ole.planet.myplanet.lite.dashboard.FetchUsersRequest>())
        ).thenReturn(Result.success(emptyList<UserDocument>()))

        // Simulate typing
        dialogBinding.inviteMembersSearchInput.setText("a")
        runCurrent() // job launched, delay started
        advanceTimeBy(100)

        dialogBinding.inviteMembersSearchInput.setText("ab")
        runCurrent() // job replaced, delay started
        advanceTimeBy(100)

        dialogBinding.inviteMembersSearchInput.setText("abc")
        runCurrent() // job replaced, delay started

        // Advance time to surpass debounce
        advanceTimeBy(400)
        runCurrent()

        // Only one fetch should have happened (for "abc")
        verify(repository, org.mockito.kotlin.times(1)).fetchAllUsers(any())
    }

}
