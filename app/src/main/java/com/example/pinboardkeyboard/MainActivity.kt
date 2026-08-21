package com.example.pinboardkeyboard

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pinboardkeyboard.adapter.PinAdapter
import com.example.pinboardkeyboard.data.PinCategory
import com.example.pinboardkeyboard.data.PinDatabase
import com.example.pinboardkeyboard.data.PinItem
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private lateinit var database: PinDatabase
    private lateinit var pinAdapter: PinAdapter
    private var pins = mutableListOf<PinItem>()
    private var categories = mutableListOf<PinCategory>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = PinDatabase(this)
        loadData()

        val recyclerView = findViewById<RecyclerView>(R.id.mainRecyclerView)
        val addButton = findViewById<FloatingActionButton>(R.id.addPinButton)
        val addCategoryButton = findViewById<Button>(R.id.addCategoryButton)

        pinAdapter = PinAdapter(
            pins,
            onClick = { showEditPinDialog(it) },
            onLongClick = { confirmDeletePin(it) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = pinAdapter

        addButton.setOnClickListener { showEditPinDialog(null) }
        addCategoryButton.setOnClickListener { showCategoryDialog() }
    }

    private fun loadData() {
        pins = database.loadPins().toMutableList()
        categories = database.loadCategories().toMutableList()
    }

    private fun showEditPinDialog(pin: PinItem?) {
        val view = layoutInflater.inflate(R.layout.dialog_pin_edit, null)
        val titleInput = view.findViewById<EditText>(R.id.pinTitleInput)
        val contentInput = view.findViewById<EditText>(R.id.pinContentInput)
        val spinner = view.findViewById<Spinner>(R.id.categorySpinner)

        val names = listOf("بدون دسته") + categories.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        if (pin != null) {
            titleInput.setText(pin.title)
            contentInput.setText(pin.content)
            val index = categories.indexOfFirst { it.id == pin.categoryId }
            spinner.setSelection(if (index >= 0) index + 1 else 0)
        }

        AlertDialog.Builder(this)
            .setTitle(if (pin == null) "افزودن پین جدید" else "ویرایش پین")
            .setView(view)
            .setPositiveButton("ذخیره") { _, _ ->
                val title = titleInput.text.toString().trim()
                val content = contentInput.text.toString()

                if (title.isEmpty() || content.isBlank()) {
                    Toast.makeText(this, "عنوان و متن را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val categoryId = if (spinner.selectedItemPosition == 0) {
                    0L
                } else {
                    categories[spinner.selectedItemPosition - 1].id
                }

                if (pin == null) {
                    pins.add(PinItem(title = title, content = content, categoryId = categoryId))
                } else {
                    val index = pins.indexOfFirst { it.id == pin.id }
                    if (index >= 0) {
                        pins[index] = pin.copy(
                            title = title,
                            content = content,
                            categoryId = categoryId
                        )
                    }
                }

                database.savePins(pins)
                pinAdapter.updatePins(pins)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showCategoryDialog() {
        val input = EditText(this).apply {
            hint = "مثلاً: کاری، شخصی، پزشکی"
        }

        AlertDialog.Builder(this)
            .setTitle("افزودن دسته")
            .setView(input)
            .setPositiveButton("افزودن") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    categories.add(PinCategory(name = name))
                    database.saveCategories(categories)
                    Toast.makeText(this, "دسته اضافه شد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun confirmDeletePin(pin: PinItem) {
        AlertDialog.Builder(this)
            .setTitle("حذف پین")
            .setMessage("«${pin.title}» حذف شود؟")
            .setPositiveButton("حذف") { _, _ ->
                pins.removeAll { it.id == pin.id }
                database.savePins(pins)
                pinAdapter.updatePins(pins)
                Toast.makeText(this, "پین حذف شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
}
