package com.example.spendmgr.domain

import com.example.spendmgr.domain.model.ValidationError
import com.example.spendmgr.domain.model.ValidationResult

class ExpenseValidator {
    /**
     * バリデーション優先順位: 金額 → カテゴリ → 認証状態
     * - 金額が空文字列または"¥"のみの場合: AMOUNT_EMPTY
     * - カテゴリが空文字列の場合: CATEGORY_EMPTY
     * - 認証未完了の場合: NOT_AUTHENTICATED
     */
    fun validate(amount: String, category: String, isAuthenticated: Boolean): ValidationResult {
        // 1. 金額チェック: 空文字列 or "¥"のみ
        val strippedAmount = amount.replace("¥", "").replace(",", "").trim()
        if (strippedAmount.isEmpty()) {
            return ValidationResult.Invalid(ValidationError.AMOUNT_EMPTY)
        }

        // 2. カテゴリチェック: 空文字列
        if (category.trim().isEmpty()) {
            return ValidationResult.Invalid(ValidationError.CATEGORY_EMPTY)
        }

        // 3. 認証状態チェック
        if (!isAuthenticated) {
            return ValidationResult.Invalid(ValidationError.NOT_AUTHENTICATED)
        }

        return ValidationResult.Valid
    }
}
