package org.ole.planet.myplanet.lite

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.EnterpriseTaskDocument
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardEnterpriseTasksAdapterTest {
    class HostActivity : AppCompatActivity()

    @Test fun `cards keep alternating after edits reorder and deleting the first task`() {
        val controller = Robolectric.buildActivity(HostActivity::class.java)
        controller.get().setTheme(R.style.Theme_MyPlanetLite)
        controller.setup().visible()
        try {
            val activity = controller.get()
            val adapter = DashboardEnterpriseTasksAdapter({}, {}, {})
            val list = RecyclerView(activity).apply {
                layoutManager = LinearLayoutManager(activity)
                itemAnimator = null
                this.adapter = adapter
            }
            activity.setContentView(list)
            fun render(tasks: List<EnterpriseTaskDocument>) {
                var committed = false
                adapter.submitList(tasks) { committed = true }
                for (attempt in 0 until 200) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
                    if (committed) break
                    Thread.sleep(5)
                }
                assertTrue("Task update did not finish", committed)
                list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(6000, View.MeasureSpec.EXACTLY))
                list.layout(0, 0, 1080, 6000)
                shadowOf(Looper.getMainLooper()).idle()
                val normal = activity.resources.getDimensionPixelSize(R.dimen.enterprise_task_normal_margin)
                val offset = activity.resources.getDimensionPixelSize(R.dimen.enterprise_task_offset_margin)
                for (position in tasks.indices) {
                    val holder = list.findViewHolderForAdapterPosition(position)
                    assertNotNull("Missing task at $position", holder)
                    val margins = holder!!.itemView.layoutParams as ViewGroup.MarginLayoutParams
                    assertEquals("start margin at $position", if (position % 2 == 0) normal else offset, margins.marginStart)
                    assertEquals("end margin at $position", if (position % 2 == 0) offset else normal, margins.marginEnd)
                }
            }
            val tasks = (1..4).map { EnterpriseTaskDocument("task-$it", "1-rev", "Task $it", "Description",
                1791800000000, false, emptyList(), "{}") }
            render(tasks)
            render(tasks.drop(1))
            val edited = tasks.drop(1).map { if (it.id == "task-3") it.copy(title = "Edited", revision = "2-rev") else it }
            render(edited.reversed())
            render(edited.take(2))
        } finally { controller.pause().stop().destroy() }
    }
}
