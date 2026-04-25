package com.example.spendmgr.domain.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AmountFormatterTest {

    @Test
    fun `formatForDisplay converts digits to yen format`() {
        assertEquals("¥10,000", AmountFormatter.formatForDisplay("10000"))
    }

    @Test
    fun `formatForDisplay returns empty string for empty input`() {
        assertEquals("", AmountFormatter.formatForDisplay(""))
    }

    @Test
    fun `formatForDisplay handles single digit`() {
        assertEquals("¥5", AmountFormatter.formatForDisplay("5"))
    }

    @Test
    fun `formatForDisplay handles large number`() {
        assertEquals("¥1,000,000", AmountFormatter.formatForDisplay("1000000"))
    }

    @Test
    fun `parseToInt converts display string to int`() {
        assertEquals(10000, AmountFormatter.parseToInt("¥10,000"))
    }

    @Test
    fun `parseToInt returns null for invalid input`() {
        assertNull(AmountFormatter.parseToInt("abc"))
    }

    @Test
    fun `parseToInt handles plain number string`() {
        assertEquals(500, AmountFormatter.parseToInt("500"))
    }

    @Test
    fun `onAmountInput filters non-digits and formats`() {
        assertEquals("¥1,000", AmountFormatter.onAmountInput("1000"))
    }

    @Test
    fun `onAmountInput returns empty for empty input`() {
        assertEquals("", AmountFormatter.onAmountInput(""))
    }

    @Test
    fun `extractDigits removes yen and commas`() {
        assertEquals("10000", AmountFormatter.extractDigits("¥10,000"))
    }
}
