package org.ole.planet.myplanet.lite.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object ApplicationScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
