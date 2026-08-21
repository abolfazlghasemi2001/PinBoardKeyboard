package com.example.pinboardkeyboard.data

data class PinCategory(
    val id: Long = System.currentTimeMillis(),
    var name: String,
    var color: Int = 0xFF6200EE.toInt()
)
