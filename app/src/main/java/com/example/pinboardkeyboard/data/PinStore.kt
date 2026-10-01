package com.example.pinboardkeyboard.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

/**
 * Persistence layer.
 *
 * Pins frequently hold sensitive text (IDs, card numbers, passwords), therefore the payload is
 * written to [EncryptedSharedPreferences] when the device keystore is usable, with a transparent
 * fallback to plain preferences on devices where the keystore is broken.
 *
 * It also migrates the legacy v1 format (`pin_db` with numeric ids) on first run.
 */
internal class PinStore(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()

    private val prefs: SharedPreferences by lazy { createPreferences() }

    private fun createPreferences(): SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as SharedPreferences
    }.getOrElse { error ->
        Log.w(TAG, "Falling back to plain preferences", error)
        appContext.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
    }

    fun load(): PinData {
        migrateLegacyIfNeeded()
        val pins = read<PinItem>(KEY_PINS)
        val categories = read<PinCategory>(KEY_CATEGORIES)
        val knownIds = categories.mapTo(HashSet()) { it.id }
        // Drop dangling references left behind by deleted categories.
        val sanitized = pins.map { pin ->
            if (pin.categoryId != null && pin.categoryId !in knownIds) pin.copy(categoryId = null) else pin
        }
        return PinData(sanitized, categories)
    }

    fun save(data: PinData) {
        prefs.edit()
            .putString(KEY_PINS, gson.toJson(data.pins))
            .putString(KEY_CATEGORIES, gson.toJson(data.categories))
            .apply()
    }

    private inline fun <reified T> read(key: String): List<T> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<T>>() {}.type
        return runCatching { gson.fromJson<List<T>>(json, type) ?: emptyList() }
            .onFailure { Log.e(TAG, "Corrupted payload for $key", it) }
            .getOrDefault(emptyList())
    }

    /** Converts v1 records (`id: Long`, `categoryId: 0`) into the UUID based schema. */
    private fun migrateLegacyIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val legacyPins = legacy.getString("pins", null)
        val legacyCategories = legacy.getString("categories", null)

        if (legacyPins == null && legacyCategories == null) {
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            return
        }

        runCatching {
            val idMap = mutableMapOf<String, String>()
            val categories = parseArray(legacyCategories).map { element ->
                val obj = element.asJsonObject
                val oldId = obj.get("id")?.asString.orEmpty()
                val category = PinCategory(
                    name = obj.get("name")?.asString.orEmpty().ifBlank { "دسته" },
                    color = obj.get("color")?.asInt ?: PinCategory.DEFAULT_COLOR
                )
                idMap[oldId] = category.id
                category
            }
            val pins = parseArray(legacyPins).map { element ->
                val obj = element.asJsonObject
                val legacyCategoryId = obj.get("categoryId")?.asString
                PinItem(
                    title = obj.get("title")?.asString.orEmpty(),
                    content = obj.get("content")?.asString.orEmpty(),
                    categoryId = if (legacyCategoryId == null || legacyCategoryId == "0") {
                        null
                    } else {
                        idMap[legacyCategoryId]
                    },
                    createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis()
                )
            }.filter { it.title.isNotBlank() || it.content.isNotBlank() }

            save(PinData(pins, categories))
            Log.i(TAG, "Migrated ${pins.size} pins and ${categories.size} categories from v1")
        }.onFailure { Log.e(TAG, "Legacy migration failed", it) }

        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        legacy.edit().clear().apply()
    }

    private fun parseArray(json: String?): List<com.google.gson.JsonElement> {
        if (json.isNullOrBlank()) return emptyList()
        val parsed = JsonParser.parseString(json)
        return if (parsed.isJsonArray) parsed.asJsonArray.toList() else emptyList()
    }

    private companion object {
        const val TAG = "PinStore"
        const val SECURE_PREFS = "pin_db_secure"
        const val FALLBACK_PREFS = "pin_db_v2"
        const val LEGACY_PREFS = "pin_db"
        const val KEY_PINS = "pins"
        const val KEY_CATEGORIES = "categories"
        const val KEY_MIGRATED = "migrated_v2"
    }
}

/** Immutable snapshot of everything the app persists. */
internal data class PinData(
    val pins: List<PinItem> = emptyList(),
    val categories: List<PinCategory> = emptyList()
)
