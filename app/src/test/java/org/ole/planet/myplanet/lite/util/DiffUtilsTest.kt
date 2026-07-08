package org.ole.planet.myplanet.lite.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffUtilsTest {

    data class TestItem(val id: Int, val content: String)

    @Test
    fun `itemCallback uses areItemsTheSame lambda correctly`() {
        val callback = DiffUtils.itemCallback<TestItem>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id }
        )

        val item1 = TestItem(1, "A")
        val item2 = TestItem(1, "B")
        val item3 = TestItem(2, "A")

        assertTrue(callback.areItemsTheSame(item1, item2))
        assertFalse(callback.areItemsTheSame(item1, item3))
    }

    @Test
    fun `itemCallback uses default areContentsTheSame lambda correctly`() {
        val callback = DiffUtils.itemCallback<TestItem>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id }
        )

        val item1 = TestItem(1, "A")
        val item2 = TestItem(1, "A")
        val item3 = TestItem(1, "B")

        assertTrue(callback.areContentsTheSame(item1, item2))
        assertFalse(callback.areContentsTheSame(item1, item3))
    }

    @Test
    fun `itemCallback uses custom areContentsTheSame lambda correctly`() {
        val callback = DiffUtils.itemCallback<TestItem>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            areContentsTheSame = { oldItem, newItem -> oldItem.content == newItem.content }
        )

        val item1 = TestItem(1, "A")
        val item2 = TestItem(2, "A")
        val item3 = TestItem(1, "B")

        assertTrue(callback.areContentsTheSame(item1, item2))
        assertFalse(callback.areContentsTheSame(item1, item3))
    }

    @Test
    fun `itemCallback uses default getChangePayload lambda correctly`() {
        val callback = DiffUtils.itemCallback<TestItem>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id }
        )

        val item1 = TestItem(1, "A")
        val item2 = TestItem(1, "B")

        assertNull(callback.getChangePayload(item1, item2))
    }

    @Test
    fun `itemCallback uses custom getChangePayload lambda correctly`() {
        val callback = DiffUtils.itemCallback<TestItem>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            getChangePayload = { oldItem, newItem -> if (oldItem.content != newItem.content) "ContentChanged" else null }
        )

        val item1 = TestItem(1, "A")
        val item2 = TestItem(1, "B")
        val item3 = TestItem(1, "A")

        assertEquals("ContentChanged", callback.getChangePayload(item1, item2))
        assertNull(callback.getChangePayload(item1, item3))
    }
    @Test
    fun `calculateDiff delegates to itemCallback correctly`() {
        val oldList = listOf(TestItem(1, "A"), TestItem(2, "B"))
        val newList = listOf(TestItem(1, "A"), TestItem(2, "C"), TestItem(3, "D"))

        val callback = DiffUtils.itemCallback<TestItem>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            areContentsTheSame = { oldItem, newItem -> oldItem.content == newItem.content },
            getChangePayload = { oldItem, newItem -> if (oldItem.content != newItem.content) "Changed" else null }
        )

        val result = DiffUtils.calculateDiff(oldList, newList, callback)

        // Let's assert on the results of the diff by dispatching updates
        var insertedCount = 0
        var changedCount = 0

        result.dispatchUpdatesTo(object : androidx.recyclerview.widget.ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                insertedCount += count
            }
            override fun onRemoved(position: Int, count: Int) {}
            override fun onMoved(fromPosition: Int, toPosition: Int) {}
            override fun onChanged(position: Int, count: Int, payload: Any?) {
                changedCount += count
                assertEquals("Changed", payload)
            }
        })

        // Item 1 is the same. Item 2 changed content ("B" -> "C"). Item 3 is inserted.
        assertEquals(1, insertedCount)
        assertEquals(1, changedCount)
    }
}
