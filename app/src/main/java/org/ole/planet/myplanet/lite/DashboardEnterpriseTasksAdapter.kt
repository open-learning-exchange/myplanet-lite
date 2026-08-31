package org.ole.planet.myplanet.lite

import android.view.LayoutInflater
import android.view.ViewGroup
import android.util.TypedValue
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.text.DateFormat
import java.util.Date
import kotlin.random.Random
import org.ole.planet.myplanet.lite.dashboard.EnterpriseTaskDocument
import org.ole.planet.myplanet.lite.databinding.ItemEnterpriseTaskBinding

internal class DashboardEnterpriseTasksAdapter(
    private val onEdit: (EnterpriseTaskDocument) -> Unit,
    private val onComplete: (EnterpriseTaskDocument) -> Unit,
    private val onArchive: (EnterpriseTaskDocument) -> Unit,
) : ListAdapter<EnterpriseTaskDocument, DashboardEnterpriseTaskViewHolder>(
    org.ole.planet.myplanet.lite.util.DiffUtils.itemCallback(
        areItemsTheSame = { old, new -> old.id == new.id },
    ),
) {
    var canManage: Boolean = false
    private var stickerOrder = IntArray(0)
    private var decorations = emptyArray<StickerDecoration>()

    override fun onCurrentListChanged(
        previousList: MutableList<EnterpriseTaskDocument>,
        currentList: MutableList<EnterpriseTaskDocument>,
    ) {
        super.onCurrentListChanged(previousList, currentList)
        stickerOrder = buildStickerOrder(currentList.size)
        decorations = buildDecorations(currentList.size)
    }

    private fun buildDecorations(size: Int): Array<StickerDecoration> {
        val result = Array(size) {
            val hasFold = Random.nextFloat() < FOLD_PROBABILITY
            val hasTape = Random.nextFloat() < TAPE_PROBABILITY
            StickerDecoration(
                foldVisible = hasFold,
                foldOnLeft = Random.nextBoolean(),
                tornBottom = !hasFold && Random.nextFloat() < TORN_PROBABILITY,
                handwritten = Random.nextFloat() < HANDWRITTEN_PROBABILITY,
                tapeVisible = hasTape,
                tapeOnLeft = Random.nextBoolean(),
                attachment = if (hasTape) StickerAttachment.NONE else {
                    when (Random.nextInt(3)) {
                        0 -> StickerAttachment.NONE
                        1 -> StickerAttachment.PIN
                        else -> StickerAttachment.CLIP
                    }
                },
            )
        }
        if (result.isNotEmpty() && result.none { it.foldVisible }) {
            val index = Random.nextInt(result.size)
            result[index] = result[index].copy(
                foldVisible = true,
                foldOnLeft = Random.nextBoolean(),
                tornBottom = false,
            )
        }
        if (result.size > 1 && result.none { it.tornBottom }) {
            val candidates = result.indices.filter { !result[it].foldVisible }
            if (candidates.isNotEmpty()) {
                val index = candidates.random()
                result[index] = result[index].copy(tornBottom = true)
            }
        }
        if (result.isNotEmpty() && result.none { it.handwritten }) {
            val index = Random.nextInt(result.size)
            result[index] = result[index].copy(handwritten = true)
        }
        if (result.isNotEmpty() && result.none { it.tapeVisible }) {
            val index = Random.nextInt(result.size)
            result[index] = result[index].copy(
                tapeVisible = true,
                tapeOnLeft = Random.nextBoolean(),
                attachment = StickerAttachment.NONE,
            )
        }
        return result
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = DashboardEnterpriseTaskViewHolder(
        ItemEnterpriseTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onEdit,
        onComplete,
        onArchive,
    )

    override fun onBindViewHolder(holder: DashboardEnterpriseTaskViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            canManage,
            STICKER_COLORS[stickerOrder.getOrElse(position) { 0 }],
            decorations.getOrElse(position) {
                StickerDecoration(false, false, false, false, false, false, StickerAttachment.PIN)
            },
            marginOnRight = position % 2 == 0,
        )
    }

    private fun buildStickerOrder(size: Int): IntArray {
        if (size == 0) return IntArray(0)
        val result = ArrayList<Int>(size)
        var previousBlock: List<Int>? = null
        while (result.size < size) {
            var block: List<Int>
            do {
                block = STICKER_COLORS.indices.shuffled(Random.Default)
            } while (block == previousBlock || (result.isNotEmpty() && block.first() == result.last()))
            result.addAll(block)
            previousBlock = block
        }
        return result.take(size).toIntArray()
    }

    private companion object {
        const val FOLD_PROBABILITY = 0.6f
        const val TORN_PROBABILITY = 0.55f
        const val HANDWRITTEN_PROBABILITY = 0.35f
        const val TAPE_PROBABILITY = 0.4f
        val STICKER_COLORS = listOf(
            StickerColors(R.color.enterprise_sticker_lime_body, R.color.enterprise_sticker_lime_top, R.color.enterprise_sticker_lime_text),
            StickerColors(R.color.enterprise_sticker_gold_body, R.color.enterprise_sticker_gold_top, R.color.enterprise_sticker_gold_text),
            StickerColors(R.color.enterprise_sticker_pink_body, R.color.enterprise_sticker_pink_top, R.color.white),
            StickerColors(R.color.enterprise_sticker_green_body, R.color.enterprise_sticker_green_top, R.color.enterprise_sticker_green_text),
            StickerColors(R.color.enterprise_sticker_cyan_body, R.color.enterprise_sticker_cyan_top, R.color.enterprise_sticker_cyan_text),
            StickerColors(R.color.enterprise_sticker_yellow_body, R.color.enterprise_sticker_yellow_top, R.color.enterprise_sticker_yellow_text),
            StickerColors(R.color.enterprise_sticker_fuchsia_body, R.color.enterprise_sticker_fuchsia_top, R.color.white),
            StickerColors(R.color.enterprise_sticker_purple_body, R.color.enterprise_sticker_purple_top, R.color.enterprise_sticker_purple_text),
        )
    }
}

internal data class StickerColors(
    @ColorRes val body: Int,
    @ColorRes val top: Int,
    @ColorRes val foreground: Int,
)
internal data class StickerDecoration(
    val foldVisible: Boolean,
    val foldOnLeft: Boolean,
    val tornBottom: Boolean,
    val handwritten: Boolean,
    val tapeVisible: Boolean,
    val tapeOnLeft: Boolean,
    val attachment: StickerAttachment,
)

internal enum class StickerAttachment { NONE, PIN, CLIP }

internal class DashboardEnterpriseTaskViewHolder(
    private val binding: ItemEnterpriseTaskBinding,
    private val onEdit: (EnterpriseTaskDocument) -> Unit,
    private val onComplete: (EnterpriseTaskDocument) -> Unit,
    private val onArchive: (EnterpriseTaskDocument) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    private val defaultTitleTypeface = binding.enterpriseTaskTitle.typeface
    private val defaultDescriptionTypeface = binding.enterpriseTaskDescription.typeface
    private val defaultDeadlineTypeface = binding.enterpriseTaskDeadline.typeface
    private val defaultAssigneeTypeface = binding.enterpriseTaskAssignee.typeface
    private val defaultTitleSize = binding.enterpriseTaskTitle.textSize
    private val defaultDescriptionSize = binding.enterpriseTaskDescription.textSize
    private val defaultDeadlineSize = binding.enterpriseTaskDeadline.textSize
    private val defaultAssigneeSize = binding.enterpriseTaskAssignee.textSize
    private val handwrittenSizeIncrease = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        6f,
        binding.root.resources.displayMetrics,
    )
    private val handwrittenTypeface = ResourcesCompat.getFont(binding.root.context, R.font.caveat_variable)
        ?: defaultDescriptionTypeface
    private val markwon = Markwon.builder(binding.root.context)
        .usePlugin(TaskListPlugin.create(binding.root.context))
        .build()

    fun bind(
        task: EnterpriseTaskDocument,
        canManage: Boolean,
        stickerColors: StickerColors,
        decoration: StickerDecoration,
        marginOnRight: Boolean,
    ) {
        val context = binding.root.context
        val bodyColor = ContextCompat.getColor(context, stickerColors.body)
        val topColor = ContextCompat.getColor(context, stickerColors.top)
        val foregroundColor = ContextCompat.getColor(context, stickerColors.foreground)
        val normalMargin = context.resources.getDimensionPixelSize(R.dimen.enterprise_task_normal_margin)
        val offsetMargin = context.resources.getDimensionPixelSize(R.dimen.enterprise_task_offset_margin)
        binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = if (marginOnRight) normalMargin else offsetMargin
            marginEnd = if (marginOnRight) offsetMargin else normalMargin
        }
        binding.enterpriseTaskStickerBackground.bodyColor = bodyColor
        binding.enterpriseTaskStickerTop.setBackgroundColor(topColor)
        val foldColor = ColorUtils.blendARGB(bodyColor, topColor, 0.65f)
        binding.enterpriseTaskStickerBackground.foldVisible = decoration.foldVisible
        binding.enterpriseTaskStickerBackground.foldOnLeft = decoration.foldOnLeft
        binding.enterpriseTaskStickerBackground.foldColor = foldColor
        binding.enterpriseTaskActionsContainer.translationX = if (
            decoration.foldVisible && !decoration.foldOnLeft
        ) {
            -context.resources.getDimension(R.dimen.enterprise_task_right_fold_action_offset)
        } else {
            0f
        }
        binding.enterpriseTaskStickerBackground.tornBottom = decoration.tornBottom
        binding.enterpriseTaskStickerBackground.tapeVisible = decoration.tapeVisible
        binding.enterpriseTaskStickerBackground.tapeOnLeft = decoration.tapeOnLeft
        binding.enterpriseTaskStickerBackground.attachment = decoration.attachment
        binding.enterpriseTaskTitle.setTextColor(foregroundColor)
        binding.enterpriseTaskDescription.setTextColor(foregroundColor)
        binding.enterpriseTaskDeadline.setTextColor(foregroundColor)
        binding.enterpriseTaskAssignee.setTextColor(foregroundColor)
        binding.enterpriseTaskTitle.typeface = if (decoration.handwritten) handwrittenTypeface else defaultTitleTypeface
        binding.enterpriseTaskDescription.typeface = if (decoration.handwritten) handwrittenTypeface else defaultDescriptionTypeface
        binding.enterpriseTaskDeadline.typeface = if (decoration.handwritten) handwrittenTypeface else defaultDeadlineTypeface
        binding.enterpriseTaskAssignee.typeface = if (decoration.handwritten) handwrittenTypeface else defaultAssigneeTypeface
        binding.enterpriseTaskTitle.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            defaultTitleSize + if (decoration.handwritten) handwrittenSizeIncrease else 0f,
        )
        binding.enterpriseTaskDescription.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            defaultDescriptionSize + if (decoration.handwritten) handwrittenSizeIncrease else 0f,
        )
        binding.enterpriseTaskDeadline.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            defaultDeadlineSize + if (decoration.handwritten) handwrittenSizeIncrease else 0f,
        )
        binding.enterpriseTaskAssignee.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            defaultAssigneeSize + if (decoration.handwritten) handwrittenSizeIncrease else 0f,
        )
        binding.enterpriseTaskTitle.text = task.title
        binding.enterpriseTaskDescription.isVisible = task.description.isNotBlank()
        if (task.description.isBlank()) {
            binding.enterpriseTaskDescription.text = null
        } else {
            markwon.setMarkdown(binding.enterpriseTaskDescription, task.description)
        }
        binding.enterpriseTaskDeadline.text = context.getString(
            R.string.dashboard_enterprise_tasks_deadline_format,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(task.deadline)),
        )
        val assignee = task.assignee?.fullName?.ifBlank { task.assignee.name }
        binding.enterpriseTaskAssignee.text = assignee?.let {
            context.getString(R.string.dashboard_enterprise_tasks_assignee_format, it)
        } ?: context.getString(R.string.dashboard_enterprise_tasks_unassigned)
        binding.enterpriseTaskActionsContainer.isVisible = canManage
        binding.enterpriseTaskComplete.setImageResource(
            if (task.completed) R.drawable.ic_enterprise_task_completed_24
            else R.drawable.ic_enterprise_task_pending_24,
        )
        binding.enterpriseTaskComplete.contentDescription = context.getString(
            if (task.completed) R.string.dashboard_enterprise_tasks_mark_pending
            else R.string.dashboard_enterprise_tasks_mark_complete,
        )
        binding.enterpriseTaskTitle.alpha = if (task.completed) 0.55f else 1f
        binding.enterpriseTaskEdit.setOnClickListener { onEdit(task) }
        binding.enterpriseTaskComplete.setOnClickListener { onComplete(task) }
        binding.enterpriseTaskArchive.setOnClickListener { onArchive(task) }
    }
}
