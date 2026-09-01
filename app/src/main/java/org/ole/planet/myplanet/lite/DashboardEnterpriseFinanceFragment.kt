package org.ole.planet.myplanet.lite

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterpriseFinanceRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardImagePreviewActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardPostImageLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardEnterpriseSelectionPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.CurrencyFormatPreferences
import org.ole.planet.myplanet.lite.dashboard.FinanceAccessDeniedException
import org.ole.planet.myplanet.lite.dashboard.FinanceConflictException
import org.ole.planet.myplanet.lite.dashboard.FinanceSnapshot
import org.ole.planet.myplanet.lite.dashboard.FinanceTransaction
import org.ole.planet.myplanet.lite.dashboard.NewFinanceReceipt
import org.ole.planet.myplanet.lite.dashboard.ImageOptionAdapter
import org.ole.planet.myplanet.lite.dashboard.SaveFinanceTransaction
import org.ole.planet.myplanet.lite.dashboard.TransactionType
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.DiffUtils
import org.ole.planet.myplanet.lite.util.enableDrag

class DashboardEnterpriseFinanceFragment : Fragment(R.layout.fragment_dashboard_enterprise_finance) {
    private val repository = DashboardEnterpriseFinanceRepository()
    private var snapshot: FinanceSnapshot? = null
    private var credentials: StoredCredentials? = null
    private var baseUrl: String? = null
    private var sessionCookie: String? = null
    private var selectedEnterpriseId: String? = null
    private var startDate: Long? = null
    private var endDate: Long? = null
    private var loadJob: Job? = null
    private lateinit var list: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var empty: TextView
    private lateinit var credits: TextView
    private lateinit var debits: TextView
    private lateinit var balance: TextView
    private lateinit var startButton: Button
    private lateinit var endButton: Button
    private lateinit var addButton: FloatingActionButton
    private val adapter = FinanceAdapter(::showEditor, ::confirmArchive, ::openReceipts)
    private var receiptSelectionTarget: ((List<Uri>) -> Unit)? = null
    private val receiptPicker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(2)) {
        receiptSelectionTarget?.invoke(it.take(2))
        receiptSelectionTarget = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        list = view.findViewById(R.id.enterpriseFinanceList)
        loading = view.findViewById(R.id.enterpriseFinanceLoading)
        empty = view.findViewById(R.id.enterpriseFinanceEmpty)
        credits = view.findViewById(R.id.enterpriseFinanceCredits)
        debits = view.findViewById(R.id.enterpriseFinanceDebits)
        balance = view.findViewById(R.id.enterpriseFinanceBalance)
        startButton = view.findViewById(R.id.enterpriseFinanceStartDate)
        endButton = view.findViewById(R.id.enterpriseFinanceEndDate)
        addButton = view.findViewById(R.id.enterpriseFinanceAddReceipt)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        addButton.enableDrag()
        addButton.setOnClickListener { showEditor(null) }
        startButton.setOnClickListener { pickFilterDate(true) }
        endButton.setOnClickListener { pickFilterDate(false) }
        view.findViewById<ImageButton>(R.id.enterpriseFinanceResetDates).setOnClickListener {
            startDate = null
            endDate = null
            updateFilterLabels()
            applyFilters()
        }
        loadFinance()
    }

    override fun onResume() {
        super.onResume()
        val selected = DashboardEnterpriseSelectionPreferences.getSelectedEnterpriseId(requireContext())
        if (selected != selectedEnterpriseId) loadFinance()
    }

    private fun loadFinance() {
        val context = requireContext().applicationContext
        val enterpriseId = DashboardEnterpriseSelectionPreferences.getSelectedEnterpriseId(context)
        selectedEnterpriseId = enterpriseId
        if (enterpriseId.isNullOrBlank()) return showMessage(R.string.dashboard_enterprise_finance_select_enterprise)
        val base = DashboardServerPreferences.getServerBaseUrl(context)
        val planet = DashboardServerPreferences.getServerCode(context)
        val creds = ProfileCredentialsStore.getStoredCredentials(context)
        if (base.isNullOrBlank() || planet.isNullOrBlank() || creds == null) {
            return showMessage(R.string.dashboard_enterprise_finance_error)
        }
        baseUrl = base
        credentials = creds
        showLoading(true)
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            sessionCookie = withContext(Dispatchers.IO) {
                AuthDependencies.provideAuthService(context, base).getStoredToken()
            }
            repository.fetch(
                base, creds, sessionCookie, enterpriseId,
                "org.couchdb.user:${creds.username}", planet,
            ).onSuccess {
                snapshot = it
                addButton.isVisible = it.canManage
                showLoading(false)
                applyFilters()
            }.onFailure {
                showMessage(
                    if (it is FinanceAccessDeniedException) R.string.dashboard_enterprise_finance_access_denied
                    else R.string.dashboard_enterprise_finance_error,
                )
            }
        }
    }

    private fun applyFilters() {
        val all = snapshot?.transactions.orEmpty()
        val filtered = all.filter { transaction ->
            transaction.date >= (startDate ?: Long.MIN_VALUE) && transaction.date <= (endDate ?: Long.MAX_VALUE)
        }
        adapter.canManage = snapshot?.canManage == true
        adapter.submitList(filtered)
        val totalCredits = filtered.filter { it.type == TransactionType.CREDIT }.sumOf { it.amount }
        val totalDebits = filtered.filter { it.type == TransactionType.DEBIT }.sumOf { it.amount }
        credits.text = getString(R.string.dashboard_enterprise_finance_total_credits, CurrencyFormatPreferences.format(requireContext(), totalCredits))
        debits.text = getString(R.string.dashboard_enterprise_finance_total_debits, CurrencyFormatPreferences.format(requireContext(), totalDebits))
        balance.text = getString(R.string.dashboard_enterprise_finance_total_balance, CurrencyFormatPreferences.format(requireContext(), totalCredits - totalDebits))
        empty.isVisible = filtered.isEmpty()
        empty.setText(
            if (all.isEmpty()) R.string.dashboard_enterprise_finance_empty
            else R.string.dashboard_enterprise_finance_filter_empty,
        )
    }

    fun refreshCurrencyFormat() {
        if (!isAdded || view == null) return
        applyFilters()
        adapter.notifyDataSetChanged()
    }

    private fun pickFilterDate(isStart: Boolean) {
        val calendar = Calendar.getInstance().apply { timeInMillis = if (isStart) startDate ?: System.currentTimeMillis() else endDate ?: System.currentTimeMillis() }
        DatePickerDialog(requireContext(), { _, year, month, day ->
            calendar.set(year, month, day, if (isStart) 0 else 23, if (isStart) 0 else 59, if (isStart) 0 else 59)
            calendar.set(Calendar.MILLISECOND, if (isStart) 0 else 999)
            if (isStart) startDate = calendar.timeInMillis else endDate = calendar.timeInMillis
            updateFilterLabels()
            applyFilters()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateFilterLabels() {
        val format = DateFormat.getDateInstance()
        startButton.text = startDate?.let { format.format(Date(it)) } ?: getString(R.string.dashboard_enterprise_finance_start_date)
        endButton.text = endDate?.let { format.format(Date(it)) } ?: getString(R.string.dashboard_enterprise_finance_end_date)
    }

    private fun showEditor(original: FinanceTransaction?) {
        val current = snapshot ?: return
        if (!current.canManage) return
        val content = layoutInflater.inflate(R.layout.dialog_enterprise_finance_transaction, null)
        val type = content.findViewById<Spinner>(R.id.financeDialogType)
        val description = content.findViewById<EditText>(R.id.financeDialogDescription)
        val amount = content.findViewById<EditText>(R.id.financeDialogAmount)
        val dateButton = content.findViewById<Button>(R.id.financeDialogDate)
        val receiptsButton = content.findViewById<Button>(R.id.financeDialogReceipts)
        val receiptPreviews = content.findViewById<LinearLayout>(R.id.financeDialogReceiptPreviews)
        val progress = content.findViewById<ProgressBar>(R.id.financeDialogProgress)
        type.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.dashboard_enterprise_finance_credit), getString(R.string.dashboard_enterprise_finance_debit)),
        )
        type.setSelection(if (original?.type == TransactionType.DEBIT) 1 else 0)
        description.setText(original?.description.orEmpty())
        original?.let { amount.setText(it.amount.toString()) }
        var transactionDate = original?.date ?: startOfToday()
        val existingReceipts = original?.receipts.orEmpty().toMutableList()
        val newReceipts = mutableListOf<NewFinanceReceipt>()
        val thumbnailLoader = DashboardPostImageLoader(baseUrl.orEmpty(), sessionCookie, viewLifecycleOwner.lifecycleScope)
        fun updateDate() { dateButton.text = DateFormat.getDateInstance().format(Date(transactionDate)) }
        fun showLocalReceipt(receipt: NewFinanceReceipt) {
            val image = ImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageURI(receipt.uri)
                setPadding(24, 24, 24, 24)
            }
            MaterialAlertDialogBuilder(requireContext()).setTitle(receipt.filename).setView(image)
                .setPositiveButton(android.R.string.ok, null).show()
        }
        fun showReceiptOptions(title: String, onView: () -> Unit, onDelete: () -> Unit) {
            val options = arrayOf(
                getString(R.string.create_voice_image_option_view),
                getString(R.string.create_voice_image_option_delete),
            )
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setAdapter(
                    ImageOptionAdapter(
                        requireContext(),
                        options,
                        intArrayOf(R.drawable.icon_image_view, R.drawable.ic_dashboard_delete_24),
                    ),
                ) { dialog, which ->
                    if (which == 0) onView() else onDelete()
                    dialog.dismiss()
                }.setNegativeButton(android.R.string.cancel, null).show()
        }
        fun renderReceipts() {
            val count = existingReceipts.size + newReceipts.size
            receiptsButton.text = getString(
                R.string.dashboard_enterprise_finance_receipts_selected,
                count,
            )
            receiptPreviews.removeAllViews()
            receiptPreviews.isVisible = count > 0
            val size = (80 * resources.displayMetrics.density).toInt()
            val spacing = (8 * resources.displayMetrics.density).toInt()
            fun createThumbnail(name: String): ImageView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = spacing }
                contentDescription = name
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = ContextCompat.getDrawable(requireContext(), R.drawable.dashboard_post_image_placeholder)
            }
            existingReceipts.toList().forEach { receipt ->
                val thumbnail = createThumbnail(receipt)
                val path = receiptPath(original?.id.orEmpty(), receipt)
                thumbnailLoader.bind(thumbnail, path)
                thumbnail.setOnClickListener {
                    showReceiptOptions(
                        receipt,
                        onView = { openReceiptPaths(listOf(path)) },
                        onDelete = { existingReceipts.remove(receipt); renderReceipts() },
                    )
                }
                receiptPreviews.addView(thumbnail)
            }
            newReceipts.toList().forEach { receipt ->
                val thumbnail = createThumbnail(receipt.filename).apply { setImageURI(receipt.uri) }
                thumbnail.setOnClickListener {
                    showReceiptOptions(
                        receipt.filename,
                        onView = { showLocalReceipt(receipt) },
                        onDelete = { newReceipts.remove(receipt); renderReceipts() },
                    )
                }
                receiptPreviews.addView(thumbnail)
            }
        }
        updateDate(); renderReceipts()
        dateButton.setOnClickListener {
            val calendar = Calendar.getInstance().apply { timeInMillis = transactionDate }
            DatePickerDialog(requireContext(), { _, year, month, day ->
                calendar.set(year, month, day, 0, 0, 0); calendar.set(Calendar.MILLISECOND, 0)
                transactionDate = calendar.timeInMillis; updateDate()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        receiptsButton.setOnClickListener {
            val available = 2 - existingReceipts.size - newReceipts.size
            if (available <= 0) {
                Toast.makeText(requireContext(), R.string.dashboard_enterprise_finance_receipt_limit, Toast.LENGTH_SHORT).show()
            } else {
                receiptSelectionTarget = { uris ->
                    uris.take(available).forEach { uri ->
                        val mime = requireContext().contentResolver.getType(uri)
                        if (mime in DashboardEnterpriseFinanceRepository.ALLOWED_RECEIPT_TYPES) {
                            val extension = when (mime) {
                                "image/png" -> "png"
                                "image/webp" -> "webp"
                                else -> "jpg"
                            }
                            newReceipts += NewFinanceReceipt(uri, "${UUID.randomUUID()}.$extension")
                        }
                    }
                    renderReceipts()
                }
                receiptPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (original == null) R.string.dashboard_enterprise_finance_add else R.string.dashboard_enterprise_finance_edit)
            .setView(content).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dashboard_enterprise_tasks_save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsedAmount = amount.text?.toString()?.toDoubleOrNull()
                if (description.text.isNullOrBlank() || parsedAmount == null || parsedAmount <= 0.0) {
                    Toast.makeText(requireContext(), R.string.dashboard_enterprise_finance_validation, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                progress.isVisible = true
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                saveTransaction(
                    dialog, current,
                    SaveFinanceTransaction(
                        if (type.selectedItemPosition == 0) TransactionType.CREDIT else TransactionType.DEBIT,
                        description.text.toString(), parsedAmount, transactionDate, original,
                        existingReceipts.toList(), newReceipts.toList(),
                    ),
                )
            }
        }
        dialog.setOnDismissListener { receiptSelectionTarget = null }
        dialog.show()
    }

    private fun saveTransaction(dialog: AlertDialog, current: FinanceSnapshot, input: SaveFinanceTransaction) {
        val base = baseUrl ?: return
        val creds = credentials ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            repository.save(base, creds, sessionCookie, current, input, requireContext().contentResolver)
                .onSuccess { dialog.dismiss(); loadFinance() }
                .onFailure {
                    dialog.dismiss()
                    if (it is FinanceConflictException) loadFinance()
                    Toast.makeText(
                        requireContext(),
                        if (it is FinanceConflictException) R.string.dashboard_enterprise_finance_conflict else R.string.dashboard_enterprise_finance_save_error,
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    private fun confirmArchive(transaction: FinanceTransaction) {
        AlertDialog.Builder(requireContext()).setTitle(R.string.dashboard_post_action_delete)
            .setMessage(R.string.dashboard_enterprise_finance_archive_confirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dashboard_post_action_delete) { _, _ -> archive(transaction) }.show()
    }

    private fun openReceipts(transaction: FinanceTransaction) {
        if (transaction.receipts.isEmpty()) return
        val paths = transaction.receipts.map { receipt -> receiptPath(transaction.id, receipt) }
        openReceiptPaths(paths)
    }

    private fun receiptPath(transactionId: String, receipt: String): String =
        "teams/${Uri.encode(transactionId)}/${Uri.encode(receipt)}"

    private fun openReceiptPaths(paths: List<String>, startIndex: Int = 0) {
        if (paths.isEmpty()) return
        startActivity(
            Intent(requireContext(), DashboardImagePreviewActivity::class.java).apply {
                putStringArrayListExtra(DashboardImagePreviewActivity.EXTRA_IMAGE_PATHS, ArrayList(paths))
                putExtra(DashboardImagePreviewActivity.EXTRA_START_INDEX, startIndex)
            },
        )
    }

    private fun archive(transaction: FinanceTransaction) {
        val base = baseUrl ?: return
        val creds = credentials ?: return
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            repository.archive(base, creds, sessionCookie, transaction)
                .onSuccess { loadFinance() }
                .onFailure { showLoading(false); Toast.makeText(requireContext(), R.string.dashboard_enterprise_finance_save_error, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun showLoading(value: Boolean) {
        loading.isVisible = value
        list.isVisible = !value
        if (value) empty.isVisible = false
    }

    private fun showMessage(message: Int) {
        snapshot = null
        adapter.submitList(emptyList())
        addButton.isVisible = false
        showLoading(false)
        empty.setText(message)
        empty.isVisible = true
    }

    private fun startOfToday() = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    override fun onDestroyView() {
        loadJob?.cancel()
        receiptSelectionTarget = null
        super.onDestroyView()
    }
}

private class FinanceAdapter(
    private val onEdit: (FinanceTransaction) -> Unit,
    private val onArchive: (FinanceTransaction) -> Unit,
    private val onReceipts: (FinanceTransaction) -> Unit,
) : ListAdapter<FinanceTransaction, FinanceViewHolder>(DiffUtils.itemCallback({ old, new -> old.id == new.id })) {
    var canManage = false
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = FinanceViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_enterprise_finance_transaction, parent, false),
    )
    override fun onBindViewHolder(holder: FinanceViewHolder, position: Int) = holder.bind(getItem(position), canManage, onEdit, onArchive, onReceipts)
}

private class FinanceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    @SuppressLint("RestrictedApi")
    fun bind(
        item: FinanceTransaction,
        canManage: Boolean,
        onEdit: (FinanceTransaction) -> Unit,
        onArchive: (FinanceTransaction) -> Unit,
        onReceipts: (FinanceTransaction) -> Unit,
    ) {
        val context = itemView.context
        val amount = CurrencyFormatPreferences.format(context, item.amount)
        val runningBalance = CurrencyFormatPreferences.format(context, item.runningBalance)
        val semanticColor = ContextCompat.getColor(
            context,
            if (item.type == TransactionType.CREDIT) R.color.greenOleLogo else R.color.dashboard_reject_red,
        )
        itemView.findViewById<ImageView>(R.id.financeTransactionTypeIcon).apply {
            setImageResource(
                if (item.type == TransactionType.CREDIT) R.drawable.ic_finance_income_24
                else R.drawable.ic_finance_expense_24,
            )
            clearColorFilter()
        }
        itemView.findViewById<TextView>(R.id.financeTransactionDescription).text = item.description
        itemView.findViewById<TextView>(R.id.financeTransactionDate).text = DateFormat.getDateInstance().format(Date(item.date))
        itemView.findViewById<TextView>(R.id.financeTransactionAmount).apply {
            text = context.getString(
                if (item.type == TransactionType.CREDIT) R.string.dashboard_enterprise_finance_credit_format else R.string.dashboard_enterprise_finance_debit_format,
                amount,
            )
            setTextColor(semanticColor)
        }
        itemView.findViewById<TextView>(R.id.financeTransactionBalance).text = runningBalance
        itemView.findViewById<TextView>(R.id.financeTransactionReceipts).apply {
            isVisible = item.receipts.isNotEmpty()
            text = context.resources.getQuantityString(R.plurals.dashboard_enterprise_finance_receipt_count, item.receipts.size, item.receipts.size)
            isClickable = item.receipts.isNotEmpty()
            setOnClickListener(if (item.receipts.isNotEmpty()) View.OnClickListener { onReceipts(item) } else null)
        }
        itemView.findViewById<View>(R.id.financeTransactionActions).apply {
            isVisible = canManage
            setOnClickListener(if (canManage) View.OnClickListener {
                val themedContext = ContextThemeWrapper(context, R.style.Widget_MyPlanet_PopupMenu)
                PopupMenu(themedContext, this).apply {
                    menuInflater.inflate(R.menu.menu_enterprise_finance_transaction_actions, menu)
                    if (menu is MenuBuilder) (menu as MenuBuilder).setOptionalIconsVisible(true)
                    setOnMenuItemClickListener { selected ->
                        when (selected.itemId) {
                            R.id.action_edit -> { onEdit(item); true }
                            R.id.action_delete -> { onArchive(item); true }
                            else -> false
                        }
                    }
                }.show()
            } else null)
        }
    }
}
