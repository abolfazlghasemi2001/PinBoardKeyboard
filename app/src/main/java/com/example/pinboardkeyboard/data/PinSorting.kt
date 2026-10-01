package com.example.pinboardkeyboard.data

/** Ordering options exposed in the manager screen. */
enum class PinSort {
    RECENT,
    MOST_USED,
    ALPHABETICAL
}

/**
 * Pure, side effect free query helpers.
 *
 * Kept free of Android APIs on purpose so they can be covered by fast JVM unit tests.
 */
object PinQuery {

    /** Sentinel used by the UI to mean "every category". */
    const val ALL_CATEGORIES: String = "__all__"

    /** Sentinel used by the UI to mean "pins without a category". */
    const val NO_CATEGORY: String = "__none__"

    fun filter(
        pins: List<PinItem>,
        categoryId: String? = ALL_CATEGORIES,
        query: String = ""
    ): List<PinItem> {
        val normalized = query.trim()
        return pins.filter { pin ->
            val categoryMatches = when (categoryId) {
                ALL_CATEGORIES, null -> true
                NO_CATEGORY -> pin.categoryId == null
                else -> pin.categoryId == categoryId
            }
            val queryMatches = normalized.isEmpty() ||
                pin.title.contains(normalized, ignoreCase = true) ||
                pin.content.contains(normalized, ignoreCase = true)
            categoryMatches && queryMatches
        }
    }

    fun sort(pins: List<PinItem>, sort: PinSort): List<PinItem> = when (sort) {
        PinSort.RECENT -> pins.sortedWith(
            compareByDescending<PinItem> { it.pinned }.thenByDescending { it.updatedAt }
        )

        PinSort.MOST_USED -> pins.sortedWith(
            compareByDescending<PinItem> { it.pinned }
                .thenByDescending { it.usageCount }
                .thenByDescending { it.updatedAt }
        )

        PinSort.ALPHABETICAL -> pins.sortedWith(
            compareByDescending<PinItem> { it.pinned }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
    }

    fun filterAndSort(
        pins: List<PinItem>,
        categoryId: String? = ALL_CATEGORIES,
        query: String = "",
        sort: PinSort = PinSort.RECENT
    ): List<PinItem> = sort(filter(pins, categoryId, query), sort)
}
