package com.example.spendmgr.domain.model

data class ExpenseRecord(
    val id: Long = 0,
    val amount: Int,        // 円単位の整数
    val date: java.time.LocalDate,
    val category: String,
    val isCreditCard: Boolean = true,  // クレジットカード支払いか否か（デフォルト: カード払い）
    val splitCount: Int = 1            // 割り勘人数（デフォルト: 1人）
)
