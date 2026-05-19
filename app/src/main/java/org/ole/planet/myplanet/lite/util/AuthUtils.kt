package org.ole.planet.myplanet.lite.util

import java.net.InetAddress
import java.net.URI

object AuthUtils {

    /**
     * Checks if a URL is secure (HTTPS) or a trusted local/private network address,
     * and ensures it matches the expected base host.
     * This prevents credential leakage when requesting external or unencrypted resources (e.g. Markdown images).
     */
    fun isSecureAndTrustedUrl(url: String, base: String): Boolean {
        return try {
            val uri = URI.create(url)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host ?: return false

            val baseUri = URI.create(base)
            val baseHost = baseUri.host

            if (host != baseHost) {
                return false
            }

            if (scheme == "https") {
                return true
            }

            isLocalOrPrivateIp(host)
        } catch (e: Exception) {
            false
        }
    }

    private fun isLocalOrPrivateIp(host: String): Boolean {
        return try {
            val address = InetAddress.getByName(host)
            address.isLoopbackAddress || address.isSiteLocalAddress
        } catch (e: Exception) {
            false
        }
    }
}
