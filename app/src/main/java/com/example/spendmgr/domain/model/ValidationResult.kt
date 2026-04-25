package com.example.spendmgr.domain.model

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val error: ValidationError) : ValidationResult()
}

enum class ValidationError {
    AMOUNT_EMPTY,
    CATEGORY_EMPTY,
    NOT_AUTHENTICATED
}
