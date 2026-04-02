package org.ole.planet.myplanet.lite.util

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ViewExtensionsTest {

    private lateinit var context: Context
    private lateinit var parent: ViewGroup
    private lateinit var view: View
    private var clickPerformed = false

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        parent = mock(ViewGroup::class.java)
        `when`(parent.width).thenReturn(1000)
        `when`(parent.height).thenReturn(1000)

        view = View(context)
        view.layout(0, 0, 100, 100)
        shadowOf(view).setMyParent(parent)

        view.setOnClickListener { clickPerformed = true }
        clickPerformed = false

        view.enableDrag()
    }

    private fun mockEvent(action: Int, x: Float, y: Float): MotionEvent {
        val event = mock(MotionEvent::class.java)
        `when`(event.action).thenReturn(action)
        `when`(event.rawX).thenReturn(x)
        `when`(event.rawY).thenReturn(y)
        return event
    }

    private fun getListener(): View.OnTouchListener {
        return shadowOf(view).onTouchListener ?: error("Listener not set")
    }

    @Test
    fun testActionDown() {
        val event = mockEvent(MotionEvent.ACTION_DOWN, 50f, 50f)
        val handled = getListener().onTouch(view, event)

        assertTrue("Action Down should be handled", handled)
        verify(parent).requestDisallowInterceptTouchEvent(true)
    }

    @Test
    fun testActionMove() {
        val listener = getListener()
        listener.onTouch(view, mockEvent(MotionEvent.ACTION_DOWN, 50f, 50f))

        val moveEvent = mockEvent(MotionEvent.ACTION_MOVE, 70f, 80f)
        val handled = listener.onTouch(view, moveEvent)

        assertTrue("Action Move should be handled", handled)
        // dX = view.x(0) - rawX(50) = -50
        // newX = rawX(70) + dX(-50) = 20
        assertEquals(20f, view.x, 0.01f)
        assertEquals(30f, view.y, 0.01f)
    }

    @Test
    fun testActionMoveBoundaryConstraints() {
        val listener = getListener()
        listener.onTouch(view, mockEvent(MotionEvent.ACTION_DOWN, 50f, 50f))

        // Negative out of bounds
        listener.onTouch(view, mockEvent(MotionEvent.ACTION_MOVE, -100f, -100f))
        assertEquals(0f, view.x, 0.01f)
        assertEquals(0f, view.y, 0.01f)

        // Positive out of bounds (1000 - 100 = 900)
        listener.onTouch(view, mockEvent(MotionEvent.ACTION_MOVE, 2000f, 2000f))
        assertEquals(900f, view.x, 0.01f)
        assertEquals(900f, view.y, 0.01f)
    }

    @Test
    fun testActionUpPerformsClick() {
        val listener = getListener()
        listener.onTouch(view, mockEvent(MotionEvent.ACTION_DOWN, 50f, 50f))

        val upEvent = mockEvent(MotionEvent.ACTION_UP, 55f, 55f)
        val handled = listener.onTouch(view, upEvent)

        assertTrue("Action Up should be handled", handled)
        verify(parent).requestDisallowInterceptTouchEvent(false)
        assertTrue("Click should be performed", clickPerformed)
    }

    @Test
    fun testActionUpNoClickIfDragged() {
        val listener = getListener()
        listener.onTouch(view, mockEvent(MotionEvent.ACTION_DOWN, 50f, 50f))
        listener.onTouch(view, mockEvent(MotionEvent.ACTION_MOVE, 70f, 70f))

        val upEvent = mockEvent(MotionEvent.ACTION_UP, 70f, 70f)
        val handled = listener.onTouch(view, upEvent)

        assertTrue("Action Up should be handled", handled)
        verify(parent).requestDisallowInterceptTouchEvent(false)
        assertFalse("Click should NOT be performed", clickPerformed)
    }

    @Test
    fun testActionCancel() {
        val listener = getListener()
        listener.onTouch(view, mockEvent(MotionEvent.ACTION_DOWN, 50f, 50f))

        val cancelEvent = mockEvent(MotionEvent.ACTION_CANCEL, 50f, 50f)
        val handled = listener.onTouch(view, cancelEvent)

        assertTrue("Action Cancel should be handled", handled)
        verify(parent).requestDisallowInterceptTouchEvent(false)
    }

    @Test
    fun testNoParent() {
        val viewNoParent = View(context)
        viewNoParent.enableDrag()
        val listener = shadowOf(viewNoParent).onTouchListener!!

        val event = mockEvent(MotionEvent.ACTION_DOWN, 50f, 50f)
        val handled = listener.onTouch(viewNoParent, event)

        assertFalse("Should not handle touch if no parent", handled)
    }
}
