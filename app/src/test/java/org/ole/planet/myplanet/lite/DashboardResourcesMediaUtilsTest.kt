/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-07
 */

package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardResourcesMediaUtilsTest {

    @Test
    fun sanitizeResourceName_validName_noChanges() {
        val input = "valid_name-123"
        val result = DashboardResourcesMediaUtils.sanitizeResourceName(input)
        assertEquals("valid_name-123", result)
    }

    @Test
    fun sanitizeResourceName_replacesSpacesWithUnderscores() {
        val input = "file name with spaces"
        val result = DashboardResourcesMediaUtils.sanitizeResourceName(input)
        assertEquals("file_name_with_spaces", result)
    }

    @Test
    fun sanitizeResourceName_removesSpecialCharacters() {
        val input = "file!@#$%^&*()name"
        val result = DashboardResourcesMediaUtils.sanitizeResourceName(input)
        assertEquals("file_name", result)
    }

    @Test
    fun sanitizeResourceName_collapsesMultipleUnderscores() {
        val input = "file___name---with___multiple___underscores"
        val result = DashboardResourcesMediaUtils.sanitizeResourceName(input)
        assertEquals("file_name---with_multiple_underscores", result)
    }

    @Test
    fun sanitizeResourceName_removesLeadingAndTrailingUnderscores() {
        val input = "___file_name___"
        val result = DashboardResourcesMediaUtils.sanitizeResourceName(input)
        assertEquals("file_name", result)
    }

    @Test
    fun sanitizeResourceName_emptyString_defaultsToResourcePrefix() {
        val input = ""
        val result = DashboardResourcesMediaUtils.sanitizeResourceName(input)
        assertTrue(result.startsWith("resource_"))
    }

    @Test
    fun sanitizeResourceName_blankString_defaultsToResourcePrefix() {
        val input = "   "
        val result = DashboardResourcesMediaUtils.sanitizeResourceName(input)
        assertTrue(result.startsWith("resource_"))
    }
}
