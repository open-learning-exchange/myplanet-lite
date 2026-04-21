package org.ole.planet.myplanet.lite.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUtilsTest {

    @Test
    fun isSecureAndTrustedUrl_httpsHostMatch_returnsTrue() {
        val base = "https://example.com"
        val url = "https://example.com/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_httpsHostMismatch_returnsFalse() {
        val base = "https://example.com"
        val url = "https://attacker.com/image.png"
        assertFalse(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_httpLocalhost_returnsTrue() {
        val base = "http://localhost:3000"
        val url = "http://localhost:3000/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_http127_0_0_1_returnsTrue() {
        val base = "http://127.0.0.1:3000"
        val url = "http://127.0.0.1:3000/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_httpPrivateClassA_returnsTrue() {
        val base = "http://10.0.2.2:5984"
        val url = "http://10.0.2.2:5984/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_httpPrivateClassC_returnsTrue() {
        val base = "http://192.168.1.100:5984"
        val url = "http://192.168.1.100:5984/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_httpPrivateClassB_returnsTrue() {
        val base = "http://172.16.5.5:5984"
        val url = "http://172.16.5.5:5984/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_httpPublicIp_returnsFalse() {
        val base = "http://8.8.8.8"
        val url = "http://8.8.8.8/db/resources"
        assertFalse(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_httpExternalDomain_returnsFalse() {
        val base = "http://example.com"
        val url = "http://example.com/db/resources"
        assertFalse(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_spoofedIpDomain_returnsFalse() {
        val base = "http://10.attacker.com"
        val url = "http://10.attacker.com/db/resources"
        assertFalse(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_malformedUrl_returnsFalse() {
        val base = "https://example.com"
        val url = "not_a_valid_url"
        assertFalse(AuthUtils.isSecureAndTrustedUrl(url, base))
    }
}
