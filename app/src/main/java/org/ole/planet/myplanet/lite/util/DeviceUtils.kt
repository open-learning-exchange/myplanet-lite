package org.ole.planet.myplanet.lite.util

import android.os.Build

object DeviceUtils {
    private const val DEFAULT_DEVICE_NAME = "Android Device"

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()

        return when {
            manufacturer.isEmpty() && model.isEmpty() -> {
                val device = Build.DEVICE?.trim().orEmpty()
                if (device.isNotEmpty()) device else DEFAULT_DEVICE_NAME
            }
            manufacturer.isEmpty() -> model
            model.isEmpty() -> manufacturer
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }
}
