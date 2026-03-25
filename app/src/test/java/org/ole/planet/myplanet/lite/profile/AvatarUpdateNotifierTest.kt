package org.ole.planet.myplanet.lite.profile

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

class AvatarUpdateNotifierTest {

    // Clean up listeners after each test to ensure test isolation
    @After
    fun tearDown() {
        // We use reflection to clear the internal listeners set to prevent tests from affecting each other
        val listenersField = AvatarUpdateNotifier::class.java.getDeclaredField("listeners")
        listenersField.isAccessible = true
        val listeners = listenersField.get(AvatarUpdateNotifier) as CopyOnWriteArraySet<*>
        listeners.clear()
    }

    @Test
    fun testRegisterAndNotify() {
        var notifiedUsername: String? = null
        val listener = AvatarUpdateNotifier.Listener { username ->
            notifiedUsername = username
        }

        AvatarUpdateNotifier.register(listener)
        AvatarUpdateNotifier.notifyAvatarUpdated("testUser")

        assertEquals("testUser", notifiedUsername)
    }

    @Test
    fun testNotifyAvatarUpdated_withSpaces() {
        var notifiedUsername: String? = null
        val listener = AvatarUpdateNotifier.Listener { username ->
            notifiedUsername = username
        }

        AvatarUpdateNotifier.register(listener)
        AvatarUpdateNotifier.notifyAvatarUpdated("  testUser  ")

        assertEquals("testUser", notifiedUsername)
    }

    @Test
    fun testNotifyWithNullOrBlankUsername() {
        var notified = false
        val listener = AvatarUpdateNotifier.Listener { _ ->
            notified = true
        }

        AvatarUpdateNotifier.register(listener)

        AvatarUpdateNotifier.notifyAvatarUpdated(null)
        assertFalse(notified)

        AvatarUpdateNotifier.notifyAvatarUpdated("")
        assertFalse(notified)

        AvatarUpdateNotifier.notifyAvatarUpdated("   ")
        assertFalse(notified)
    }

    @Test
    fun testUnregister() {
        var notified = false
        val listener = AvatarUpdateNotifier.Listener { _ ->
            notified = true
        }

        AvatarUpdateNotifier.register(listener)
        AvatarUpdateNotifier.unregister(listener)

        AvatarUpdateNotifier.notifyAvatarUpdated("testUser")
        assertFalse(notified)
    }

    @Test
    fun testUnregisterNull() {
        var notified = false
        val listener = AvatarUpdateNotifier.Listener { _ ->
            notified = true
        }

        AvatarUpdateNotifier.register(listener)
        // Should not throw and should not unregister the valid listener
        AvatarUpdateNotifier.unregister(null)

        AvatarUpdateNotifier.notifyAvatarUpdated("testUser")
        assertTrue(notified)
    }

    @Test
    fun testMultipleListeners() {
        var count1 = 0
        var count2 = 0

        val listener1 = AvatarUpdateNotifier.Listener { _ ->
            count1++
        }
        val listener2 = AvatarUpdateNotifier.Listener { _ ->
            count2++
        }

        AvatarUpdateNotifier.register(listener1)
        AvatarUpdateNotifier.register(listener2)

        AvatarUpdateNotifier.notifyAvatarUpdated("testUser")

        assertEquals(1, count1)
        assertEquals(1, count2)
    }
}
