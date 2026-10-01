package com.example.pinboardkeyboard.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.GridLayoutManager
import com.example.pinboardkeyboard.R
import com.example.pinboardkeyboard.data.PinItem
import com.example.pinboardkeyboard.data.PinQuery
import com.example.pinboardkeyboard.data.PinRepository
import com.example.pinboardkeyboard.databinding.KeyboardViewBinding
import com.example.pinboardkeyboard.ui.MainActivity
import com.example.pinboardkeyboard.ui.PinListAdapter
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The PinBoard input method.
 *
 * Highlights compared to v1:
 *  * data is observed reactively, so edits made in the manager appear immediately;
 *  * real editing keys (backspace with auto-repeat, space, enter, clear);
 *  * robust IME switching that works on every supported API level;
 *  * category chips and most-used ordering directly on the keyboard.
 */
class PinBoardIME : InputMethodService() {

    private var binding: KeyboardViewBinding? = null
    private lateinit var repository: PinRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    private var selectedCategoryId: String = PinQuery.ALL_CATEGORIES

    private val adapter by lazy {
        PinListAdapter(
            compact = true,
            onClick = ::insertPin,
            onLongClick = ::previewPin
        )
    }

    override fun onCreate() {
        super.onCreate()
        repository = PinRepository.get(this)
    }

    override fun onCreateInputView(): View {
        val inflater = LayoutInflater.from(this)
        val binding = KeyboardViewBinding.inflate(inflater).also { this.binding = it }

        binding.pinRecyclerView.layoutManager = GridLayoutManager(this, spanCount())
        binding.pinRecyclerView.adapter = adapter
        binding.pinRecyclerView.setHasFixedSize(true)

        binding.switchToLettersBtn.setOnClickListener { switchToNextKeyboard() }
        binding.openManagerBtn.setOnClickListener { openManager() }
        binding.spaceBtn.setOnClickListener { currentInputConnection?.commitText(" ", 1) }
        binding.enterBtn.setOnClickListener { performEnter() }
        binding.backspaceBtn.setOnRepeatableClickListener { deleteBackwards() }

        return binding.root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Re-subscribe each time the keyboard is shown so the content is always fresh.
        collectJob?.cancel()
        collectJob = scope.launch {
            repository.state.collect { render() }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        collectJob?.cancel()
        collectJob = null
    }

    override fun onDestroy() {
        scope.cancel()
        binding = null
        super.onDestroy()
    }

    private fun spanCount(): Int =
        resources.getInteger(R.integer.keyboard_span_count)

    private fun render() {
        val binding = binding ?: return
        val data = repository.state.value

        adapter.submitList(
            PinQuery.filterAndSort(
                pins = data.pins,
                categoryId = selectedCategoryId,
                sort = com.example.pinboardkeyboard.data.PinSort.MOST_USED
            )
        )

        val group = binding.categoryChipGroup
        val expected = listOf(PinQuery.ALL_CATEGORIES, PinQuery.NO_CATEGORY) + data.categories.map { it.id }
        val currentIds = (0 until group.childCount).map { group.getChildAt(it).tag as? String }
        if (currentIds != expected) {
            group.removeAllViews()
            fun addChip(id: String, label: String) {
                val chip = LayoutInflater.from(this)
                    .inflate(R.layout.item_category_chip, group, false) as Chip
                chip.text = label
                chip.tag = id
                chip.isChecked = selectedCategoryId == id
                chip.setOnClickListener {
                    selectedCategoryId = id
                    render()
                }
                group.addView(chip)
            }
            addChip(PinQuery.ALL_CATEGORIES, getString(R.string.category_all))
            addChip(PinQuery.NO_CATEGORY, getString(R.string.category_none))
            data.categories.sortedBy { it.name }.forEach { addChip(it.id, it.name) }
        } else {
            (0 until group.childCount).forEach { index ->
                val chip = group.getChildAt(index) as Chip
                chip.isChecked = chip.tag == selectedCategoryId
            }
        }

        binding.emptyState.visibility =
            if (adapter.currentList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun insertPin(pin: PinItem) {
        currentInputConnection?.commitText(pin.content, 1)
        repository.recordUsage(pin.id)
    }

    private fun previewPin(pin: PinItem) {
        binding?.previewText?.apply {
            text = pin.preview
            visibility = View.VISIBLE
            postDelayed({ visibility = View.GONE }, PREVIEW_DURATION_MS)
        }
    }

    private fun deleteBackwards() {
        val connection = currentInputConnection ?: return
        val selected = connection.getSelectedText(0)
        if (selected.isNullOrEmpty()) {
            connection.deleteSurroundingText(1, 0)
        } else {
            connection.commitText("", 1)
        }
    }

    private fun performEnter() {
        val connection = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action != null && action != EditorInfo.IME_ACTION_NONE &&
            currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
        ) {
            connection.performEditorAction(action)
        } else {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun switchToNextKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (switchToNextInputMethod(false)) return
        }
        val manager = getSystemService<InputMethodManager>() ?: return
        val token = window?.window?.attributes?.token
        @Suppress("DEPRECATION")
        val switched = token != null && manager.switchToNextInputMethod(token, false)
        if (!switched) {
            // Last resort: let the user pick a keyboard explicitly.
            manager.showInputMethodPicker()
        }
    }

    private fun openManager() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private companion object {
        const val PREVIEW_DURATION_MS = 2_000L
    }
}
