package org.ole.planet.myplanet.lite

import java.util.Locale

internal object ResourceSearchEngine {
    fun filterByTabQueryAndType(
        resources: List<ResourceUi>,
        isTeamResourcesTab: Boolean,
        query: String,
        selectedMediaType: String?
    ): List<ResourceUi> {
        val normalizedQuery = query.trim()
        return resources.filter { item ->
            val matchesTab = item.isTeamResource == isTeamResourcesTab
            val matchesQuery = normalizedQuery.isBlank() ||
                item.name.contains(normalizedQuery, ignoreCase = true) ||
                item.filename.contains(normalizedQuery, ignoreCase = true)
            val matchesType = selectedMediaType.isNullOrBlank() ||
                item.type.equals(selectedMediaType, ignoreCase = true)
            matchesTab && matchesQuery && matchesType
        }
    }

    fun sortResources(items: MutableList<ResourceUi>, selectedSortBy: ResourceSortBy, isSortDescending: Boolean) {
        val comparator = when (selectedSortBy) {
            ResourceSortBy.DATE -> compareBy<ResourceUi> { it.createdDate ?: Long.MIN_VALUE }
            ResourceSortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        val ordered = if (isSortDescending) comparator.reversed() else comparator
        items.sortWith(ordered.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun parseIsDownloadable(raw: Any?): Boolean {
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> {
                val normalized = raw.trim().lowercase(Locale.ROOT)
                normalized == "true" || normalized == "1" || normalized == "yes"
            }
            else -> false
        }
    }
}
