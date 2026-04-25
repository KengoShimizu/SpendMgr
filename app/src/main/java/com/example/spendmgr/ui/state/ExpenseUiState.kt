package com.example.spendmgr.ui.state

import com.example.spendmgr.domain.model.ChartPeriod
import com.example.spendmgr.domain.model.PieChartData
import com.example.spendmgr.domain.model.UndoTarget
import com.example.spendmgr.domain.model.ValidationError
import java.time.LocalDate

data class ExpenseUiState(
    val amountText: String = "",
    val date: LocalDate = LocalDate.now(),
    val category: String = "",
    val isCreditCard: Boolean = true,   // クレジットカード払いか否か（デフォルト: カード払い）
    val suggestions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val message: String? = null,
    val error: ValidationError? = null,
    val spreadsheetUrl: String? = null,
    val undoTarget: UndoTarget? = null,
    val showUndoSnackbar: Boolean = false,
    val undoExpenseLabel: String = "",
    val yearlyTotal: Int? = null,
    val monthlyTotal: Int? = null,
    val isSummaryLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val allowanceAmount: Int? = null,
    val isRemainingMode: Boolean = false,
    val showAllowanceDialog: Boolean = false,
    // 円グラフ関連フィールド
    val pieChartData: PieChartData? = null,
    val selectedPeriod: ChartPeriod = ChartPeriod.Monthly(LocalDate.now().year, LocalDate.now().monthValue),
    val availableYears: List<Int> = emptyList(),
    val isPieChartLoading: Boolean = false,
    val pieChartError: Boolean = false
)
