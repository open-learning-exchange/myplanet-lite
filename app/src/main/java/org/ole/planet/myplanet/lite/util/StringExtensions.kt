package org.ole.planet.myplanet.lite.util

fun String?.nullIfBlank(): String? = if (this.isNullOrBlank()) null else this
