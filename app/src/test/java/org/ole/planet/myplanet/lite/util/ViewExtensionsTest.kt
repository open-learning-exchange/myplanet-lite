package org.ole.planet.myplanet.lite.util

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ViewExtensionsTest {

    private lateinit var context: Context
    private lateinit var parent: ViewGroup
    private lateinit var view: View

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        parent = spy(FrameLayout(context))
        // Set parent size
        parent.layout(0, 0, 1000, 1000)

        view = spy(View(context))
        // Set initial view position and size
        view.layout(0, 0, 100, 100)
        parent.addView(view)

        view.enableDrag()
    }

    @Test
    fun testActionDown() {
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        val handled = view.dispatchTouchEvent(event)

        assertTrue(handled)
        verify(parent).requestDisallowInterceptTouchEvent(true)
        event.recycle()
    }

    @Test
    fun testActionMove() {
        // ACTION_DOWN first to initialize dX, dY
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        view.dispatchTouchEvent(downEvent)

        // Move by 20, 30. New raw coordinates 70, 80.
        // dX = 0 - 50 = -50
        // dY = 0 - 50 = -50
        // newX = 70 + (-50) = 20
        // newY = 80 + (-50) = 30
        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 70f, 80f, 0)
        val handled = view.dispatchTouchEvent(moveEvent)

        assertTrue(handled)
        assertEquals(20f, view.x)
        assertEquals(30f, view.y)

        downEvent.recycle()
        moveEvent.recycle()
    }

    @Test
    fun testActionMoveBoundaryConstraints() {
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        view.dispatchTouchEvent(downEvent)

        // Move way out of bounds (negative)
        val moveEventNeg = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, -100f, -100f, 0)
        view.dispatchTouchEvent(moveEventNeg)
        assertEquals(0f, view.x)
        assertEquals(0f, view.y)

        // Move way out of bounds (positive)
        // Parent is 1000x1000, View is 100x100. Max X/Y should be 900.
        // dX is -50. To get newX = 1500, rawX must be 1550.
        val moveEventPos = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 1550f, 1550f, 0)
        view.dispatchTouchEvent(moveEventPos)
        assertEquals(900f, view.x)
        assertEquals(900f, view.y)

        downEvent.recycle()
        moveEventNeg.recycle()
        moveEventPos.recycle()
    }

    @Test
    fun testActionUpPerformsClick() {
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        view.dispatchTouchEvent(downEvent)

        // Small move within tolerance (10)
        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 55f, 55f, 0)
        view.dispatchTouchEvent(moveEvent)

        val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 55f, 55f, 0)
        val handled = view.dispatchTouchEvent(upEvent)

        assertTrue(handled)
        verify(parent).requestDisallowInterceptTouchEvent(false)
        verify(view).performClick()

        downEvent.recycle()
        moveEvent.recycle()
        upEvent.recycle()
    }

    @Test
    fun testActionUpNoClickIfDragged() {
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        view.dispatchTouchEvent(downEvent)

        // Move beyond tolerance (e.g., 20 pixels)
        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 70f, 70f, 0)
        view.dispatchTouchEvent(moveEvent)

        val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 70f, 70f, 0)
        val handled = view.dispatchTouchEvent(upEvent)

        assertTrue(handled)
        verify(parent).requestDisallowInterceptTouchEvent(false)
        verify(view, never()).performClick()

        downEvent.recycle()
        moveEvent.recycle()
        upEvent.recycle()
    }

    @Test
    fun testActionCancel() {
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        view.dispatchTouchEvent(downEvent)

        val cancelEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 50f, 50f, 0)
        val handled = view.dispatchTouchEvent(cancelEvent)

        assertTrue(handled)
        verify(parent).requestDisallowInterceptTouchEvent(false)

        downEvent.recycle()
        cancelEvent.recycle()
    }

    @Test
    fun testNoParent() {
        val viewNoParent = View(context)
        viewNoParent.enableDrag()

        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        val handled = viewNoParent.dispatchTouchEvent(event)

        assertFalse(handled)
        event.recycle()
    }
}
