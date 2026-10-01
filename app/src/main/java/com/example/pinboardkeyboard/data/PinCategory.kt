package com.example.pinboardkeyboard.data

import java.util.UUID

/** A user defined group of [PinItem]s. */
data class PinCategory(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Int = DEFAULT_COLOR,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_COLOR: Int = 0xFF6200EE.toInt()
    }
}
