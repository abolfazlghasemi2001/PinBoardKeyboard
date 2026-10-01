package com.example.pinboardkeyboard.ime

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.GridLayoutManager
import com.example.pinboardkeyboard.R
import com.example.pinboardkeyboard.data.PinItem
import com.example.pinboardkeyboard.data.PinQuery
import com.example.pinboardkeyboard.data.PinRepository
import com.example.pinboardkeyboard.data.PinSort
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
 *
 * Theming note: a [android.app.Service] context does **not** pick up the `android:theme`
 * declared on its `<service>` entry — an IME window always starts from the platform
 * `Theme.DeviceDefault.InputMethod`. Inflating Material components (MaterialButton, Chip,
 * MaterialCardView) with that raw service context throws
 * `IllegalArgumentException: The style on this component requires your app theme to be
 * Theme.MaterialComponents (or a descendant)`, which killed the keyboard as soon as it was
 * shown. Every inflation below therefore goes through [themedContext].
 */
class PinBoardIME : InputMethodService() {

    private var binding: KeyboardViewBinding? = null
    private var repository: PinRepository? = null
    private var adapter: PinListAdapter? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    private var selectedCategoryId: String = PinQuery.ALL_CATEGORIES

    /** Material-capable context used for every inflation done by this service. */
    private val themedContext: Context by lazy {
        ContextThemeWrapper(this, R.style.Theme_PinBoardKeyboard_Ime)
    }

    private val themedInflater: LayoutInflater
        get() = LayoutInflater.from(themedContext)

    override fun onCreate() {
        super.onCreate()
        repository = runCatching { PinRepository.get(this) }
            .onFailure { Log.e(TAG, "Could not open the pin storage", it) }
            .getOrNull()
    }

    /** The pin grid is compact; the fullscreen extract view would only get in the way. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        // Release the previous view (the system recreates it on configuration changes).
        binding?.pinRecyclerView?.adapter = null
        binding = null

        return runCatching { createKeyboardView() }
            .getOrElse { error ->
                Log.e(TAG, "Failed to inflate the keyboard view", error)
                createFallbackView()
            }
    }

    private fun createKeyboardView(): View {
        val binding = KeyboardViewBinding.inflate(themedInflater).also { this.binding = it }
        val adapter = PinListAdapter(
            compact = true,
            onClick = ::insertPin,
            onLongClick = ::previewPin
        ).also { this.adapter = it }

        binding.pinRecyclerView.layoutManager = GridLayoutManager(themedContext, spanCount())
        binding.pinRecyclerView.adapter = adapter
        binding.pinRecyclerView.setHasFixedSize(true)

        binding.switchToLettersBtn.setOnClickListener { switchToNextKeyboard() }
        binding.openManagerBtn.setOnClickListener { openManager() }
        binding.spaceBtn.setOnClickListener { currentInputConnection?.commitText(" ", 1) }
        binding.enterBtn.setOnClickListener { performEnter() }
        binding.backspaceBtn.setOnRepeatableClickListener { deleteBackwards() }

        render()
        return binding.root
    }

    /**
     * Minimal, dependency-free view shown if anything goes wrong while building the keyboard.
     * Without it a failure here would crash the IME and leave the user with no keyboard at all.
     */
    private fun createFallbackView(): View = TextView(themedContext).apply {
        setText(R.string.ime_load_error)
        gravity = android.view.Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        val padding = (24 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
        setOnClickListener { switchToNextKeyboard() }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Re-subscribe each time the keyboard is shown so the content is always fresh.
        val repository = repository ?: return
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
        binding?.pinRecyclerView?.adapter = null
        binding = null
        adapter = null
        super.onDestroy()
    }

    private fun spanCount(): Int =
        resources.getInteger(R.integer.keyboard_span_count)

    private fun render() {
        val binding = binding ?: return
        val adapter = adapter ?: return
        val data = repository?.state?.value ?: return

        adapter.submitList(
            PinQuery.filterAndSort(
                pins = data.pins,
                categoryId = selectedCategoryId,
                sort = PinSort.MOST_USED
            )
        ) {
            binding.emptyState.visibility =
                if (adapter.currentList.isEmpty()) View.VISIBLE else View.GONE
        }

        val group = binding.categoryChipGroup
        val expected = listOf(PinQuery.ALL_CATEGORIES, PinQuery.NO_CATEGORY) +
            data.categories.sortedBy { it.name }.map { it.id }
        val currentIds = (0 until group.childCount).map { group.getChildAt(it).tag as? String }
        if (currentIds != expected) {
            group.removeAllViews()
            fun addChip(id: String, label: String) {
                val chip = themedInflater
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
    }

    private fun insertPin(pin: PinItem) {
        currentInputConnection?.commitText(pin.content, 1)
        repository?.recordUsage(pin.id)
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
        val switched = runCatching {
            token != null && manager.switchToNextInputMethod(token, false)
        }.getOrDefault(false)
        if (!switched) {
            // Last resort: let the user pick a keyboard explicitly.
            manager.showInputMethodPicker()
        }
    }

    private fun openManager() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }
            .onFailure { Log.e(TAG, "Could not open the manager", it) }
    }

    private companion object {
        const val TAG = "PinBoardIME"
        const val PREVIEW_DURATION_MS = 2_000L
    }
}
