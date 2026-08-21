package com.example.pinboardkeyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pinboardkeyboard.adapter.PinAdapter
import com.example.pinboardkeyboard.data.PinCategory
import com.example.pinboardkeyboard.data.PinDatabase
import com.example.pinboardkeyboard.data.PinItem

class PinBoardIME : InputMethodService() {
    private lateinit var keyboardView: View
    private lateinit var database: PinDatabase
    private lateinit var pinAdapter: PinAdapter

    private var pins = mutableListOf<PinItem>()
    private var categories = mutableListOf<PinCategory>()
    private var currentCategoryId = 0L

    override fun onCreate() {
        super.onCreate()
        database = PinDatabase(this)
        loadData()
    }

    override fun onCreateInputView(): View {
        keyboardView = LayoutInflater.from(this)
            .inflate(R.layout.keyboard_view, null)
        setupKeyboardView()
        return keyboardView
    }

    private fun setupKeyboardView() {
        val recyclerView = keyboardView.findViewById<RecyclerView>(R.id.pinRecyclerView)
        val categorySpinner = keyboardView.findViewById<Spinner>(R.id.categorySpinner)
        val normalKeyboardButton = keyboardView.findViewById<Button>(R.id.switchToLettersBtn)
        val managerButton = keyboardView.findViewById<Button>(R.id.openManagerBtn)

        pinAdapter = PinAdapter(pins, onClick = { commitText(it.content) })

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = pinAdapter

        val names = listOf("همه") + categories.map { it.name }
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            names
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = spinnerAdapter

        categorySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    currentCategoryId =
                        if (position == 0) 0L else categories[position - 1].id
                    updatePins()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        normalKeyboardButton.setOnClickListener {
            switchToNextKeyboard()
        }

        managerButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    private fun loadData() {
        pins = database.loadPins().toMutableList()
        categories = database.loadCategories().toMutableList()
    }

    private fun updatePins() {
        val filtered = if (currentCategoryId == 0L) {
            pins
        } else {
            pins.filter { it.categoryId == currentCategoryId }
        }
        pinAdapter.updatePins(filtered)
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun switchToNextKeyboard() {
        val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        window?.window?.attributes?.token?.let { token ->
            manager.switchToNextInputMethod(token, false)
        }
    }
}
