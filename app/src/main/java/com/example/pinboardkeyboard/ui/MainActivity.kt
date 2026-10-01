package com.example.pinboardkeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pinboardkeyboard.R
import com.example.pinboardkeyboard.data.PinCategory
import com.example.pinboardkeyboard.data.PinItem
import com.example.pinboardkeyboard.data.PinQuery
import com.example.pinboardkeyboard.data.PinSort
import com.example.pinboardkeyboard.databinding.ActivityMainBinding
import com.example.pinboardkeyboard.databinding.DialogPinEditBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Manager screen: create, edit, categorise, search and reorder pins. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val adapter by lazy {
        PinListAdapter(
            categoryNameProvider = viewModel::categoryName,
            onClick = { showPinEditor(it) },
            onLongClick = { showPinActions(it) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupList()
        setupSearch()

        binding.addPinButton.setOnClickListener { showPinEditor(null) }
        binding.enableKeyboardButton.setOnClickListener { openKeyboardSettings() }

        observeState()
    }

    private fun setupList() {
        binding.mainRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.mainRecyclerView.adapter = adapter
        binding.mainRecyclerView.setHasFixedSize(true)
        attachSwipeToDelete()
    }

    private fun attachSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.START or ItemTouchHelper.END
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val pin = adapter.currentList[position]
                viewModel.deletePin(pin.id)
                Snackbar
                    .make(binding.root, getString(R.string.pin_deleted, pin.title), Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.addPinButton)
                    .setAction(R.string.undo) { viewModel.restorePin(pin, position) }
                    .show()
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.mainRecyclerView)
    }

    private fun setupSearch() {
        binding.searchInput.doAfterTextChanged { viewModel.search(it?.toString().orEmpty()) }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.pins)
                    renderCategoryChips(state)
                    binding.emptyState.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
                    binding.emptyStateText.setText(
                        if (state.query.isBlank()) R.string.empty_pins else R.string.empty_search
                    )
                }
            }
        }
    }

    private fun renderCategoryChips(state: ManagerUiState) {
        val group = binding.categoryChipGroup
        val expected = listOf(PinQuery.ALL_CATEGORIES, PinQuery.NO_CATEGORY) +
            state.categories.map { it.id }
        val current = (0 until group.childCount).map { group.getChildAt(it).tag as? String }
        if (current == expected) {
            (0 until group.childCount).forEach { index ->
                val chip = group.getChildAt(index) as Chip
                chip.isChecked = chip.tag == state.selectedCategoryId
            }
            return
        }
        group.removeAllViews()

        fun addChip(id: String, label: String) {
            val chip = LayoutInflater.from(this)
                .inflate(R.layout.item_category_chip, group, false) as Chip
            chip.text = label
            chip.tag = id
            chip.isChecked = state.selectedCategoryId == id
            chip.setOnClickListener { viewModel.selectCategory(id) }
            group.addView(chip)
        }

        addChip(PinQuery.ALL_CATEGORIES, getString(R.string.category_all))
        addChip(PinQuery.NO_CATEGORY, getString(R.string.category_none))
        state.categories.forEach { addChip(it.id, it.name) }
    }

    // region menu

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_sort -> { showSortDialog(); true }
        R.id.action_categories -> { showCategoryManager(); true }
        R.id.action_keyboard_settings -> { openKeyboardSettings(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showSortDialog() {
        val options = PinSort.entries.toTypedArray()
        val labels = options.map { getString(sortLabel(it)) }.toTypedArray()
        val current = options.indexOf(viewModel.uiState.value.sort)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                viewModel.setSort(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sortLabel(sort: PinSort) = when (sort) {
        PinSort.RECENT -> R.string.sort_recent
        PinSort.MOST_USED -> R.string.sort_most_used
        PinSort.ALPHABETICAL -> R.string.sort_alphabetical
    }

    // endregion

    // region pin editing

    private fun showPinEditor(pin: PinItem?) {
        val state = viewModel.uiState.value
        val dialogBinding = DialogPinEditBinding.inflate(layoutInflater)

        val categories = state.categories
        val names = listOf(getString(R.string.category_none)) + categories.map { it.name }
        dialogBinding.categoryDropdown.setSimpleItems(names.toTypedArray())

        var selectedIndex = pin?.categoryId
            ?.let { id -> categories.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
        dialogBinding.categoryDropdown.setText(names[selectedIndex], false)
        dialogBinding.categoryDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position
        }

        pin?.let {
            dialogBinding.pinTitleInput.setText(it.title)
            dialogBinding.pinContentInput.setText(it.content)
            dialogBinding.pinPinnedSwitch.isChecked = it.pinned
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (pin == null) R.string.add_pin else R.string.edit_pin)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val title = dialogBinding.pinTitleInput.text?.toString()?.trim().orEmpty()
                val content = dialogBinding.pinContentInput.text?.toString().orEmpty()

                dialogBinding.pinTitleLayout.error =
                    if (title.isEmpty()) getString(R.string.error_title_required) else null
                dialogBinding.pinContentLayout.error =
                    if (content.isBlank()) getString(R.string.error_content_required) else null
                if (title.isEmpty() || content.isBlank()) return@setOnClickListener

                val categoryId = categories.getOrNull(selectedIndex - 1)?.id
                val updated = pin?.copy(
                    title = title,
                    content = content,
                    categoryId = categoryId,
                    pinned = dialogBinding.pinPinnedSwitch.isChecked
                ) ?: PinItem(
                    title = title,
                    content = content,
                    categoryId = categoryId,
                    pinned = dialogBinding.pinPinnedSwitch.isChecked
                )
                viewModel.savePin(updated)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showPinActions(pin: PinItem) {
        val actions = arrayOf(
            getString(if (pin.pinned) R.string.action_unpin else R.string.action_pin),
            getString(R.string.edit),
            getString(R.string.delete)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(pin.title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> viewModel.togglePinned(pin.id)
                    1 -> showPinEditor(pin)
                    2 -> confirmDeletePin(pin)
                }
            }
            .show()
    }

    private fun confirmDeletePin(pin: PinItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_pin)
            .setMessage(getString(R.string.delete_pin_message, pin.title))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deletePin(pin.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // endregion

    // region categories

    private fun showCategoryManager() {
        val categories = viewModel.uiState.value.categories
        val labels = categories.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.manage_categories)
            .setItems(labels) { _, which -> showCategoryActions(categories[which]) }
            .setPositiveButton(R.string.add_category) { _, _ -> showCategoryEditor(null) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showCategoryActions(category: PinCategory) {
        MaterialAlertDialogBuilder(this)
            .setTitle(category.name)
            .setItems(arrayOf(getString(R.string.rename), getString(R.string.delete))) { _, which ->
                when (which) {
                    0 -> showCategoryEditor(category)
                    1 -> confirmDeleteCategory(category)
                }
            }
            .show()
    }

    private fun showCategoryEditor(category: PinCategory?) {
        val container = LayoutInflater.from(this).inflate(R.layout.dialog_category_edit, null)
        val layout = container.findViewById<TextInputLayout>(R.id.categoryNameLayout)
        val input = container.findViewById<TextInputEditText>(R.id.categoryNameInput)
        input.setText(category?.name.orEmpty())

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (category == null) R.string.add_category else R.string.rename_category)
            .setView(container)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    layout.error = getString(R.string.error_name_required)
                    return@setOnClickListener
                }
                viewModel.saveCategory(category?.copy(name = name) ?: PinCategory(name = name))
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDeleteCategory(category: PinCategory) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_category)
            .setMessage(getString(R.string.delete_category_message, category.name))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteCategory(category.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // endregion

    private fun openKeyboardSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            .onFailure {
                Snackbar.make(binding.root, R.string.error_open_settings, Snackbar.LENGTH_LONG).show()
            }
    }
}
