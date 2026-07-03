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
    }
}
