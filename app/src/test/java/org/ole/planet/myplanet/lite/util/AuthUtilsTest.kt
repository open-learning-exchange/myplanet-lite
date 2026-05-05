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

    @Test
    fun isSecureAndTrustedUrl_noHost_returnsFalse() {
        val base = "https://example.com"
        val url = "file:///path/to/local/file"
        assertFalse(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_mixedCaseHttps_returnsTrue() {
        val base = "https://example.com"
        val url = "HTTPS://example.com/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_invalidPrivateIpClassA_returnsFalse() {
        // 10.999.999.999 fails to parse as a valid URI, so URI.create throws and the catch block returns false
        val base = "http://10.999.999.999"
        val url = "http://10.999.999.999/db/resources"
        assertFalse(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_invalidPrivateIpClassB_returnsFalse() {
        // 172.32.0.0 is outside the 172.16-31 range.
        val base = "http://172.32.0.0"
        val url = "http://172.32.0.0/db/resources"
        assertFalse(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_invalidPrivateIpClassC_returnsFalse() {
        // 192.168.256.0 fails to parse as a valid URI, throwing an exception and returning false
        val base = "http://192.168.256.0"
        val url = "http://192.168.256.0/db/resources"
        assertFalse(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_malformedBaseUrl_returnsFalse() {
        val base = "not_a_valid_url"
        val url = "https://example.com/db/resources"
        assertFalse(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_baseNoHost_returnsFalse() {
        val base = "file:///path/to/local/file"
        val url = "https://example.com/db/resources"
        assertFalse(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_urlNoScheme_returnsFalse() {
        val base = "http://10.0.2.2:5984"
        val url = "10.0.2.2:5984/db/resources"
        assertFalse(AuthUtils.isSecureAndTrustedUrl(url, base))
    }

    @Test
    fun isSecureAndTrustedUrl_privateIpClassABoundaries_returnsTrue() {
        val base1 = "http://10.0.0.0:5984"
        val url1 = "http://10.0.0.0:5984/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url1, base1))

        val base2 = "http://10.255.255.255:5984"
        val url2 = "http://10.255.255.255:5984/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url2, base2))
    }

    @Test
    fun isSecureAndTrustedUrl_privateIpClassBBoundaries_returnsTrue() {
        val base1 = "http://172.16.0.0:5984"
        val url1 = "http://172.16.0.0:5984/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url1, base1))

        val base2 = "http://172.31.255.255:5984"
        val url2 = "http://172.31.255.255:5984/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url2, base2))
    }

    @Test
    fun isSecureAndTrustedUrl_privateIpClassCBoundaries_returnsTrue() {
        val base1 = "http://192.168.0.0:5984"
        val url1 = "http://192.168.0.0:5984/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url1, base1))

        val base2 = "http://192.168.255.255:5984"
        val url2 = "http://192.168.255.255:5984/db/resources"
        assertTrue(AuthUtils.isSecureAndTrustedUrl(url2, base2))
    }
}
