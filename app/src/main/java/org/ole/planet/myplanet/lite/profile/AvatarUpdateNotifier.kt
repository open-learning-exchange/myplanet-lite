/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-17
 */

package org.ole.planet.myplanet.lite.profile

import java.util.concurrent.CopyOnWriteArraySet

object AvatarUpdateNotifier {
    fun interface Listener {
        fun onAvatarUpdated(username: String)
    }

    private val listeners = CopyOnWriteArraySet<Listener>()

    fun register(listener: Listener): Listener {
        listeners.add(listener)
        return listener
    }

    fun unregister(listener: Listener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    fun notifyAvatarUpdated(username: String?) {
        val normalized = username?.trim()
        if (normalized.isNullOrEmpty()) {
            return
        }
        listeners.forEach { listener ->
            listener.onAvatarUpdated(normalized)
        }
    }
}
