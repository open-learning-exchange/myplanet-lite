package org.ole.planet.myplanet.lite.util

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
        if (host == "localhost" || host == "127.0.0.1") {
            return true
        }

        // Check for private IPv4 ranges:
        // 10.0.0.0 - 10.255.255.255
        if (host.matches(Regex("^10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
            return true
        }

        // 192.168.0.0 - 192.168.255.255
        if (host.matches(Regex("^192\\.168\\.\\d{1,3}\\.\\d{1,3}$"))) {
            return true
        }

        // 172.16.0.0 - 172.31.255.255
        if (host.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\.\\d{1,3}\\.\\d{1,3}$"))) {
            return true
        }

        return false
    }
}
