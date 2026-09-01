package org.ole.planet.myplanet.lite

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterpriseSelectionPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterpriseTasksRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterpriseTasksRepository.EnterpriseTasksSnapshot
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterprisesRepository
import org.ole.planet.myplanet.lite.dashboard.EnterpriseTaskAssignee
import org.ole.planet.myplanet.lite.dashboard.EnterpriseTaskDocument
import org.ole.planet.myplanet.lite.dashboard.SaveEnterpriseTask
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.TeamMemberDetails
import org.ole.planet.myplanet.lite.databinding.DialogEnterpriseTaskBinding
import org.ole.planet.myplanet.lite.databinding.FragmentDashboardEnterpriseTasksBinding
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.enableDrag

class DashboardEnterpriseTasksFragment : Fragment() {
    private var _binding: FragmentDashboardEnterpriseTasksBinding? = null
    private val binding get() = _binding ?: error("Binding is only valid while the view exists")
    private val repository = DashboardEnterpriseTasksRepository()
    private val enterprisesRepository = DashboardEnterprisesRepository()
    private var snapshot: EnterpriseTasksSnapshot.Success? = null
    private var tasks: List<EnterpriseTaskDocument> = emptyList()
    private var members: List<TeamMemberDetails> = emptyList()
    private var baseUrl: String? = null
    private var credentials: StoredCredentials? = null
    private var sessionCookie: String? = null
    private var userPlanetCode: String? = null
    private var selectedEnterpriseId: String? = null
    private var loadJob: Job? = null

    private val adapter = DashboardEnterpriseTasksAdapter(
        onEdit = { openTaskEditor(it) },
        onComplete = { setTaskCompleted(it) },
        onArchive = { confirmArchive(it) },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentDashboardEnterpriseTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun openTaskEditor(task: EnterpriseTaskDocument) {
        val base = baseUrl ?: return
        val creds = credentials ?: return
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            repository.fetchTask(base, creds, sessionCookie, task.id)
                .onSuccess { currentTask ->
                    showLoading(false)
                    showTaskDialog(currentTask)
                }
                .onFailure {
                    showLoading(false)
                    showTransientMessage(R.string.dashboard_enterprise_tasks_error_loading)
                }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.enterpriseTasksList.layoutManager = LinearLayoutManager(requireContext())
        binding.enterpriseTasksList.adapter = adapter
        binding.enterpriseTasksRefresh.setOnRefreshListener { loadTasks() }
        binding.enterpriseTasksSearch.addTextChangedListener { applyFilters() }
        binding.enterpriseTasksFilter.addOnButtonCheckedListener { _, _, checked -> if (checked) applyFilters() }
        binding.enterpriseTasksAdd.setOnClickListener { showTaskDialog(null) }
        binding.enterpriseTasksAdd.enableDrag()
        loadTasks()
    }

    override fun onResume() {
        super.onResume()
        val selected = DashboardEnterpriseSelectionPreferences.getSelectedEnterpriseId(requireContext())
        if (selected != selectedEnterpriseId) loadTasks()
    }

    private fun loadTasks() {
        val context = requireContext().applicationContext
        val enterpriseId = DashboardEnterpriseSelectionPreferences.getSelectedEnterpriseId(context)
        selectedEnterpriseId = enterpriseId
        if (enterpriseId.isNullOrBlank()) {
            showMessage(R.string.dashboard_enterprise_tasks_select_hint)
            return
        }
        baseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        userPlanetCode = DashboardServerPreferences.getServerCode(context)
        credentials = ProfileCredentialsStore.getStoredCredentials(context)
        val base = baseUrl
        val planet = userPlanetCode
        val creds = credentials
        if (base.isNullOrBlank() || planet.isNullOrBlank() || creds == null) {
            showMessage(R.string.dashboard_enterprise_tasks_error_loading)
            return
        }
        showLoading(true)
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val auth = AuthDependencies.provideAuthService(context, base)
            sessionCookie = withContext(Dispatchers.IO) { auth.getStoredToken() }
            val result = repository.fetchTasks(
                base, creds, sessionCookie, enterpriseId,
                "org.couchdb.user:${creds.username}", planet,
            ).getOrElse {
                showMessage(R.string.dashboard_enterprise_tasks_error_loading)
                return@launch
            }
            when (result) {
                EnterpriseTasksSnapshot.AccessDenied -> showMessage(R.string.dashboard_enterprise_tasks_access_denied)
                is EnterpriseTasksSnapshot.Success -> {
                    snapshot = result
                    tasks = result.tasks
                    adapter.canManage = result.canManage
                    binding.enterpriseTasksAdd.isVisible = result.canManage
                    if (result.canManage) loadMembers(base, creds, enterpriseId, planet)
                    showLoading(false)
                    applyFilters()
                }
            }
        }
    }

    private suspend fun loadMembers(base: String, creds: StoredCredentials, enterpriseId: String, planet: String) {
        val result = enterprisesRepository.fetchEnterpriseMembers(
            base, creds, sessionCookie, enterpriseId,
            "org.couchdb.user:${creds.username}", planet,
        ).getOrNull()
        members = (result as? DashboardEnterprisesRepository.EnterpriseMembersResult.Success)?.members.orEmpty()
    }

    private fun applyFilters() {
        val query = binding.enterpriseTasksSearch.text?.toString().orEmpty().trim()
        val mineOnly = binding.enterpriseTasksFilter.checkedButtonId == R.id.enterpriseTasksMine
        val currentUserId = credentials?.username?.let { "org.couchdb.user:$it" }
        val currentPlanetCode = userPlanetCode
        val filtered = tasks.filter { task ->
            val assignee = task.assignee
            (!mineOnly || (assignee?.userId == currentUserId && assignee?.userPlanetCode == currentPlanetCode)) &&
                (query.isEmpty() || task.title.contains(query, true) || task.description.contains(query, true))
        }
        adapter.submitList(filtered)
        binding.enterpriseTasksEmpty.isVisible = filtered.isEmpty()
        binding.enterpriseTasksEmpty.setText(
            if (tasks.isEmpty()) R.string.dashboard_enterprise_tasks_empty else R.string.dashboard_enterprise_tasks_search_empty,
        )
    }

    private fun showTaskDialog(original: EnterpriseTaskDocument?) {
        val current = snapshot ?: return
        if (!current.canManage) return
        val dialogBinding = DialogEnterpriseTaskBinding.inflate(layoutInflater)
        dialogBinding.enterpriseTaskDialogTitle.setText(original?.title.orEmpty())
        dialogBinding.enterpriseTaskDialogDescription.setText(original?.description.orEmpty())
        EnterpriseTaskMarkdownEditor(dialogBinding).bind()
        var deadline = original?.deadline ?: (System.currentTimeMillis() + DAY_MILLIS)
        fun updateDeadlineLabel() {
            dialogBinding.enterpriseTaskDialogDeadline.text = getString(
                R.string.dashboard_enterprise_tasks_deadline_format,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(deadline)),
            )
        }
        updateDeadlineLabel()
        dialogBinding.enterpriseTaskDialogDeadline.setOnClickListener {
            val calendar = Calendar.getInstance().apply { timeInMillis = deadline }
            DatePickerDialog(requireContext(), { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                TimePickerDialog(
                    requireContext(),
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        deadline = calendar.timeInMillis
                        updateDeadlineLabel()
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(requireContext()),
                ).show()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        val assigneeOptions = listOf<TeamMemberDetails?>(null) + members
        val labels = assigneeOptions.map { it?.fullName ?: it?.username ?: getString(R.string.dashboard_enterprise_tasks_unassigned) }
        dialogBinding.enterpriseTaskDialogAssignee.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
        val selectedIndex = assigneeOptions.indexOfFirst { it?.userId == original?.assignee?.userId }.coerceAtLeast(0)
        dialogBinding.enterpriseTaskDialogAssignee.setSelection(selectedIndex)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (original == null) R.string.dashboard_enterprise_tasks_add else R.string.dashboard_enterprise_tasks_edit)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dashboard_enterprise_tasks_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = dialogBinding.enterpriseTaskDialogTitle.text?.toString().orEmpty()
                if (title.isBlank()) {
                    dialogBinding.enterpriseTaskDialogTitle.error = getString(R.string.dashboard_enterprise_tasks_title_required)
                    return@setOnClickListener
                }
                val member = assigneeOptions[dialogBinding.enterpriseTaskDialogAssignee.selectedItemPosition]
                saveTask(dialog, current, original, title, dialogBinding.enterpriseTaskDialogDescription.text?.toString().orEmpty(), deadline, member)
            }
        }
        dialog.show()
    }

    private fun saveTask(
        dialog: AlertDialog,
        current: EnterpriseTasksSnapshot.Success,
        original: EnterpriseTaskDocument?,
        title: String,
        description: String,
        deadline: Long,
        member: TeamMemberDetails?,
    ) {
        val base = baseUrl ?: return
        val creds = credentials ?: return
        val assignee = member?.let {
            EnterpriseTaskAssignee(it.userId.orEmpty(), it.userPlanetCode.orEmpty(), it.username.orEmpty(), it.fullName.orEmpty())
        }
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.saveTask(
                base, creds, sessionCookie,
                SaveEnterpriseTask(
                    current.enterpriseId, current.enterpriseType, current.enterprisePlanetCode,
                    title, description, deadline, assignee,
                    "org.couchdb.user:${creds.username}", original,
                ),
            )
            result.onSuccess { dialog.dismiss(); loadTasks() }.onFailure {
                showLoading(false)
                showTransientMessage(
                    if (it is DashboardEnterpriseTasksRepository.TaskConflictException) {
                        R.string.dashboard_enterprise_tasks_conflict
                    } else R.string.dashboard_enterprise_tasks_save_error,
                )
            }
        }
    }

    private fun setTaskCompleted(task: EnterpriseTaskDocument) {
        if (task.completed) {
            updateTaskCompleted(task, completed = false)
            return
        }
        if (UNCHECKED_MARKDOWN_TASK.containsMatchIn(task.description)) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dashboard_enterprise_tasks_cannot_complete)
                .setMessage(R.string.dashboard_enterprise_tasks_pending_checklist)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dashboard_enterprise_tasks_mark_complete)
            .setMessage(R.string.dashboard_enterprise_tasks_complete_confirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dashboard_enterprise_tasks_mark_complete) { _, _ ->
                updateTaskCompleted(task, completed = true)
            }
            .show()
    }

    private fun updateTaskCompleted(task: EnterpriseTaskDocument, completed: Boolean) {
        val base = baseUrl ?: return
        val creds = credentials ?: return
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            repository.setCompleted(base, creds, sessionCookie, task, completed)
                .onSuccess { loadTasks() }
                .onFailure {
                    showLoading(false)
                    showTransientMessage(R.string.dashboard_enterprise_tasks_save_error)
                }
        }
    }

    private fun confirmArchive(task: EnterpriseTaskDocument) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dashboard_enterprise_tasks_archive)
            .setMessage(R.string.dashboard_enterprise_tasks_archive_confirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dashboard_enterprise_tasks_archive) { _, _ -> archiveTask(task) }
            .show()
    }

    private fun archiveTask(task: EnterpriseTaskDocument) {
        val base = baseUrl ?: return
        val creds = credentials ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            repository.archiveTask(base, creds, sessionCookie, task)
                .onSuccess { loadTasks() }
                .onFailure { showTransientMessage(R.string.dashboard_enterprise_tasks_save_error) }
        }
    }

    private fun showTransientMessage(messageRes: Int) =
        android.widget.Toast.makeText(requireContext(), messageRes, android.widget.Toast.LENGTH_SHORT).show()

    private fun showLoading(loading: Boolean) {
        binding.enterpriseTasksLoading.isVisible = loading
        binding.enterpriseTasksRefresh.isRefreshing = false
    }

    private fun showMessage(messageRes: Int) {
        snapshot = null
        tasks = emptyList()
        adapter.submitList(emptyList())
        binding.enterpriseTasksAdd.isVisible = false
        binding.enterpriseTasksEmpty.setText(messageRes)
        binding.enterpriseTasksEmpty.isVisible = true
        showLoading(false)
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        val UNCHECKED_MARKDOWN_TASK = Regex("(?m)^\\s*[-*+]\\s+\\[\\s]\\s+")
    }
}
