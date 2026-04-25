package com.example.spendmgr.domain.model

data class SummaryResult(
    val yearlyTotal: Int?,  // 今年の合計額（円）。取得失敗時はnull
    val monthlyTotal: Int?  // 今月の合計額（円）。取得失敗時はnull
)
