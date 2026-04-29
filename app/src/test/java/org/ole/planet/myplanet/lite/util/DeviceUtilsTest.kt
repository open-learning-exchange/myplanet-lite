package org.ole.planet.myplanet.lite.util

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class DeviceUtilsTest {

    @Test
    fun getDeviceName_returnsManufacturerAndModel() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Sony")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Xperia Z5")

        assertEquals("Sony Xperia Z5", DeviceUtils.getDeviceName())
    }

    @Test
    fun getDeviceName_manufacturerIsEmpty_returnsModel() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Pixel 6")

        assertEquals("Pixel 6", DeviceUtils.getDeviceName())
    }

    @Test
    fun getDeviceName_modelIsEmpty_returnsManufacturer() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Google")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "")

        assertEquals("Google", DeviceUtils.getDeviceName())
    }

    @Test
    fun getDeviceName_manufacturerAndModelAreEmpty_returnsDevice() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "")
        ReflectionHelpers.setStaticField(Build::class.java, "DEVICE", "Custom Device")

        assertEquals("Custom Device", DeviceUtils.getDeviceName())
    }

    @Test
    fun getDeviceName_manufacturerModelAndDeviceAreEmpty_returnsDefault() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "")
        ReflectionHelpers.setStaticField(Build::class.java, "DEVICE", "")

        assertEquals("Android Device", DeviceUtils.getDeviceName())
    }

    @Test
    fun getDeviceName_modelStartsWithManufacturer_returnsModel() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Samsung")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Samsung Galaxy S22")

        assertEquals("Samsung Galaxy S22", DeviceUtils.getDeviceName())
    }

    @Test
    fun getDeviceName_modelStartsWithManufacturerIgnoreCase_returnsModel() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "samsung")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Samsung Galaxy S22")

        assertEquals("Samsung Galaxy S22", DeviceUtils.getDeviceName())
    }

    @Test
    fun getDeviceName_trimsWhitespace() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "  Google  ")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "  Pixel 6  ")

        assertEquals("Google Pixel 6", DeviceUtils.getDeviceName())
    }

    @Test
    fun getDeviceName_nullManufacturerAndModel_returnsDefault() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", null)
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", null)
        ReflectionHelpers.setStaticField(Build::class.java, "DEVICE", null)

        assertEquals("Android Device", DeviceUtils.getDeviceName())
    }
}
