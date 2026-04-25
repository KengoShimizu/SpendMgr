package com.example.spendmgr.domain

import com.example.spendmgr.domain.model.ValidationError
import com.example.spendmgr.domain.model.ValidationResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpenseValidatorTest {
    private val validator = ExpenseValidator()

    @Test
    fun `empty amount returns AMOUNT_EMPTY`() {
        val result = validator.validate("", "食費", true)
        assertEquals(ValidationResult.Invalid(ValidationError.AMOUNT_EMPTY), result)
    }

    @Test
    fun `yen-only amount returns AMOUNT_EMPTY`() {
        val result = validator.validate("¥", "食費", true)
        assertEquals(ValidationResult.Invalid(ValidationError.AMOUNT_EMPTY), result)
    }

    @Test
    fun `empty category returns CATEGORY_EMPTY`() {
        val result = validator.validate("¥1,000", "", true)
        assertEquals(ValidationResult.Invalid(ValidationError.CATEGORY_EMPTY), result)
    }

    @Test
    fun `not authenticated returns NOT_AUTHENTICATED`() {
        val result = validator.validate("¥1,000", "食費", false)
        assertEquals(ValidationResult.Invalid(ValidationError.NOT_AUTHENTICATED), result)
    }

    @Test
    fun `valid input returns Valid`() {
        val result = validator.validate("¥1,000", "食費", true)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `amount check takes priority over category`() {
        val result = validator.validate("", "", true)
        assertEquals(ValidationResult.Invalid(ValidationError.AMOUNT_EMPTY), result)
    }

    @Test
    fun `category check takes priority over auth`() {
        val result = validator.validate("¥1,000", "", false)
        assertEquals(ValidationResult.Invalid(ValidationError.CATEGORY_EMPTY), result)
    }
}
