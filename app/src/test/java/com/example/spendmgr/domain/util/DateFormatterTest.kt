package com.example.spendmgr.domain.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DateFormatterTest {

    @Test
    fun `formatMd formats date without leading zeros`() {
        assertEquals("4/22", DateFormatter.formatMd(LocalDate.of(2026, 4, 22)))
    }

    @Test
    fun `formatMd formats single-digit month and day`() {
        assertEquals("1/5", DateFormatter.formatMd(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `formatMd formats double-digit month and day`() {
        assertEquals("12/31", DateFormatter.formatMd(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `formatMd formats first day of month`() {
        assertEquals("3/1", DateFormatter.formatMd(LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun `parseMd parses M slash d format`() {
        val date = DateFormatter.parseMd("4/22", 2026)
        assertEquals(LocalDate.of(2026, 4, 22), date)
    }

    @Test
    fun `parseMd round-trips with formatMd`() {
        val original = LocalDate.of(2026, 7, 15)
        val formatted = DateFormatter.formatMd(original)
        val parsed = DateFormatter.parseMd(formatted, 2026)
        assertEquals(original.monthValue, parsed.monthValue)
        assertEquals(original.dayOfMonth, parsed.dayOfMonth)
    }
}
