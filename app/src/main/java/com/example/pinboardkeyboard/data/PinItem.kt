package com.example.pinboardkeyboard.data

import java.util.UUID

/**
 * A single saved snippet of text.
 *
 * @param id stable unique identifier (UUID, never time based).
 * @param categoryId id of the owning [PinCategory] or `null` when uncategorised.
 * @param usageCount number of times the pin was inserted; used for the "most used" ordering.
 */
data class PinItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val categoryId: String? = null,
    val pinned: Boolean = false,
    val usageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val preview: String
        get() = content.replace(Regex("\\s+"), " ").trim()
}
