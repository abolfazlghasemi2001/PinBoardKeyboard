package com.example.pinboardkeyboard

import com.example.pinboardkeyboard.data.PinItem
import com.example.pinboardkeyboard.data.PinQuery
import com.example.pinboardkeyboard.data.PinSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinQueryTest {

    private val work = "cat-work"
    private val home = "cat-home"

    private val pins = listOf(
        PinItem(id = "1", title = "Email", content = "me@example.com", categoryId = work, usageCount = 5, updatedAt = 100),
        PinItem(id = "2", title = "Address", content = "Helsinki, Finland", categoryId = home, usageCount = 1, updatedAt = 300),
        PinItem(id = "3", title = "IBAN", content = "FI21 1234", categoryId = null, usageCount = 9, updatedAt = 200),
        PinItem(id = "4", title = "Signature", content = "Best regards", categoryId = work, pinned = true, usageCount = 0, updatedAt = 50)
    )

    @Test
    fun `filter by category returns only matching pins`() {
        val result = PinQuery.filter(pins, work)
        assertEquals(listOf("1", "4"), result.map { it.id })
    }

    @Test
    fun `filter by no-category sentinel returns uncategorised pins`() {
        val result = PinQuery.filter(pins, PinQuery.NO_CATEGORY)
        assertEquals(listOf("3"), result.map { it.id })
    }

    @Test
    fun `all categories sentinel keeps everything`() {
        assertEquals(pins.size, PinQuery.filter(pins, PinQuery.ALL_CATEGORIES).size)
    }

    @Test
    fun `search matches title and content case insensitively`() {
        assertEquals(listOf("2"), PinQuery.filter(pins, query = "helsinki").map { it.id })
        assertEquals(listOf("1"), PinQuery.filter(pins, query = "EXAMPLE.COM").map { it.id })
    }

    @Test
    fun `pinned items always come first`() {
        PinSort.entries.forEach { sort ->
            assertEquals("4", PinQuery.sort(pins, sort).first().id)
        }
    }

    @Test
    fun `most used ordering respects usage count`() {
        assertEquals(listOf("4", "3", "1", "2"), PinQuery.sort(pins, PinSort.MOST_USED).map { it.id })
    }

    @Test
    fun `recent ordering respects updatedAt`() {
        assertEquals(listOf("4", "2", "3", "1"), PinQuery.sort(pins, PinSort.RECENT).map { it.id })
    }

    @Test
    fun `alphabetical ordering is case insensitive`() {
        assertEquals(listOf("4", "2", "1", "3"), PinQuery.sort(pins, PinSort.ALPHABETICAL).map { it.id })
    }

    @Test
    fun `filter and sort compose`() {
        val result = PinQuery.filterAndSort(pins, work, "e", PinSort.MOST_USED)
        assertEquals(listOf("4", "1"), result.map { it.id })
    }

    @Test
    fun `ids are unique even when created in the same millisecond`() {
        val generated = (1..1_000).map { PinItem(title = "t", content = "c").id }
        assertEquals(generated.size, generated.toSet().size)
    }

    @Test
    fun `preview collapses whitespace`() {
        val pin = PinItem(title = "t", content = "  line one\n\nline  two \t")
        assertEquals("line one line two", pin.preview)
        assertNotEquals(pin.content, pin.preview)
        assertTrue(pin.preview.isNotBlank())
    }
}
