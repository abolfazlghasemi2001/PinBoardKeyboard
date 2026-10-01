package com.example.pinboardkeyboard.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Single source of truth shared by the manager activity and the IME service.
 *
 * Both components live in the same process, so an in-memory [StateFlow] keeps them in sync
 * instantly: adding a pin in the manager is visible on the keyboard without a restart.
 * Disk writes happen off the main thread.
 */
class PinRepository private constructor(context: Context) {

    private val store = PinStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PinData())
    internal val state: StateFlow<PinData> = _state.asStateFlow()

    val pins: StateFlow<List<PinItem>> = _state.mapState { it.pins }
    val categories: StateFlow<List<PinCategory>> = _state.mapState { it.categories }

    init {
        _state.value = store.load()
    }

    // region pins

    fun upsertPin(pin: PinItem) = update { data ->
        val index = data.pins.indexOfFirst { it.id == pin.id }
        val pins = if (index >= 0) {
            data.pins.toMutableList().apply { this[index] = pin.copy(updatedAt = now()) }
        } else {
            data.pins + pin
        }
        data.copy(pins = pins)
    }

    fun deletePin(pinId: String) = update { data ->
        data.copy(pins = data.pins.filterNot { it.id == pinId })
    }

    fun togglePinned(pinId: String) = update { data ->
        data.copy(
            pins = data.pins.map { if (it.id == pinId) it.copy(pinned = !it.pinned) else it }
        )
    }

    /** Records an insertion so the "most used" ordering stays meaningful. */
    fun recordUsage(pinId: String) = update { data ->
        data.copy(
            pins = data.pins.map { if (it.id == pinId) it.copy(usageCount = it.usageCount + 1) else it }
        )
    }

    /** Restores a deleted pin (used by the undo snackbar). */
    fun restorePin(pin: PinItem, position: Int) = update { data ->
        val pins = data.pins.toMutableList()
        pins.add(position.coerceIn(0, pins.size), pin)
        data.copy(pins = pins)
    }

    // endregion

    // region categories

    fun upsertCategory(category: PinCategory) = update { data ->
        val index = data.categories.indexOfFirst { it.id == category.id }
        val categories = if (index >= 0) {
            data.categories.toMutableList().apply { this[index] = category }
        } else {
            data.categories + category
        }
        data.copy(categories = categories)
    }

    /** Deletes a category and detaches (never deletes) the pins that referenced it. */
    fun deleteCategory(categoryId: String) = update { data ->
        data.copy(
            categories = data.categories.filterNot { it.id == categoryId },
            pins = data.pins.map { if (it.categoryId == categoryId) it.copy(categoryId = null) else it }
        )
    }

    fun categoryName(categoryId: String?): String? =
        categoryId?.let { id -> _state.value.categories.firstOrNull { it.id == id }?.name }

    // endregion

    private fun update(transform: (PinData) -> PinData) {
        val updated = transform(_state.value)
        _state.value = updated
        scope.launch { store.save(updated) }
    }

    private fun now() = System.currentTimeMillis()

    private fun <T> MutableStateFlow<PinData>.mapState(selector: (PinData) -> T): StateFlow<T> {
        val derived = MutableStateFlow(selector(value))
        scope.launch { map(selector).collect { derived.value = it } }
        return derived.asStateFlow()
    }

    companion object {
        @Volatile
        private var instance: PinRepository? = null

        fun get(context: Context): PinRepository =
            instance ?: synchronized(this) {
                instance ?: PinRepository(context.applicationContext).also { instance = it }
            }
    }
}
