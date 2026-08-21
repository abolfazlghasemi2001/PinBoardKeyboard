package com.example.pinboardkeyboard.data

data class PinItem(
    val id: Long = System.currentTimeMillis(),
    var title: String,
    var content: String,
    var categoryId: Long = 0L,
    var createdAt: Long = System.currentTimeMillis()
)
