package com.example.pinboardkeyboard.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PinDatabase(context: Context) {
    private val prefs = context.getSharedPreferences("pin_db", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun savePins(pins: List<PinItem>) {
        prefs.edit().putString("pins", gson.toJson(pins)).apply()
    }

    fun loadPins(): List<PinItem> {
        val json = prefs.getString("pins", "[]") ?: "[]"
        val type = object : TypeToken<List<PinItem>>() {}.type
        return runCatching { gson.fromJson<List<PinItem>>(json, type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    fun saveCategories(categories: List<PinCategory>) {
        prefs.edit().putString("categories", gson.toJson(categories)).apply()
    }

    fun loadCategories(): List<PinCategory> {
        val json = prefs.getString("categories", "[]") ?: "[]"
        val type = object : TypeToken<List<PinCategory>>() {}.type
        return runCatching { gson.fromJson<List<PinCategory>>(json, type) ?: emptyList() }
            .getOrDefault(emptyList())
    }
}
