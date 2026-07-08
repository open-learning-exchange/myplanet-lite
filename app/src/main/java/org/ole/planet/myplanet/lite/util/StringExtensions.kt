package org.ole.planet.myplanet.lite.util

/*
 * Returns the string if it is not null or blank, otherwise returns null.
 */
fun String?.nullIfBlank(): String? = if (this.isNullOrBlank()) null else this
