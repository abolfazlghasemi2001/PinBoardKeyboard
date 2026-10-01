package com.example.pinboardkeyboard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pinboardkeyboard.data.PinCategory
import com.example.pinboardkeyboard.data.PinItem
import com.example.pinboardkeyboard.data.PinQuery
import com.example.pinboardkeyboard.data.PinRepository
import com.example.pinboardkeyboard.data.PinSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** UI state for the manager screen. */
data class ManagerUiState(
    val pins: List<PinItem> = emptyList(),
    val categories: List<PinCategory> = emptyList(),
    val selectedCategoryId: String = PinQuery.ALL_CATEGORIES,
    val query: String = "",
    val sort: PinSort = PinSort.RECENT
) {
    val isEmpty: Boolean get() = pins.isEmpty()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PinRepository.get(application)

    private val selectedCategory = MutableStateFlow(PinQuery.ALL_CATEGORIES)
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(PinSort.RECENT)

    val uiState: StateFlow<ManagerUiState> = combine(
        repository.state,
        selectedCategory,
        query,
        sort
    ) { data, category, text, order ->
        ManagerUiState(
            pins = PinQuery.filterAndSort(data.pins, category, text, order),
            categories = data.categories.sortedBy { it.name },
            selectedCategoryId = category,
            query = text,
            sort = order
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagerUiState())

    fun selectCategory(categoryId: String) { selectedCategory.value = categoryId }

    fun search(text: String) { query.value = text }

    fun setSort(order: PinSort) { sort.value = order }

    fun savePin(pin: PinItem) = repository.upsertPin(pin)

    fun deletePin(pinId: String) = repository.deletePin(pinId)

    fun restorePin(pin: PinItem, position: Int) = repository.restorePin(pin, position)

    fun togglePinned(pinId: String) = repository.togglePinned(pinId)

    fun saveCategory(category: PinCategory) = repository.upsertCategory(category)

    fun deleteCategory(categoryId: String) {
        if (selectedCategory.value == categoryId) selectedCategory.value = PinQuery.ALL_CATEGORIES
        repository.deleteCategory(categoryId)
    }

    fun categoryName(categoryId: String?): String? = repository.categoryName(categoryId)
}
