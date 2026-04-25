package com.example.spendmgr.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendmgr.data.CategoryHistoryRepository
import com.example.spendmgr.data.GoogleAuthManager
import com.example.spendmgr.data.GoogleSheetsRepository
import com.example.spendmgr.data.PendingExpenseRepository
import com.example.spendmgr.data.SettingsRepository
import com.example.spendmgr.domain.CategorySuggestionEngine
import com.example.spendmgr.domain.ExpenseValidator
import com.example.spendmgr.domain.PieChartCache
import com.example.spendmgr.domain.PieChartFetcher
import com.example.spendmgr.domain.SpreadsheetResolver
import com.example.spendmgr.domain.SummaryCache
import com.example.spendmgr.domain.SummaryFetcher
import com.example.spendmgr.domain.model.ChartPeriod
import com.example.spendmgr.domain.model.ExpenseRecord
import com.example.spendmgr.domain.model.UndoTarget
import com.example.spendmgr.domain.model.ValidationError
import com.example.spendmgr.domain.model.ValidationResult
import com.example.spendmgr.domain.util.AmountFormatter
import com.example.spendmgr.domain.util.DateFormatter
import com.example.spendmgr.ui.state.ExpenseUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val expenseValidator: ExpenseValidator,
    private val categorySuggestionEngine: CategorySuggestionEngine,
    private val spreadsheetResolver: SpreadsheetResolver,
    private val googleSheetsRepository: GoogleSheetsRepository,
    private val categoryHistoryRepository: CategoryHistoryRepository,
    private val pendingExpenseRepository: PendingExpenseRepository,
    private val settingsRepository: SettingsRepository,
    private val summaryFetcher: SummaryFetcher,
    private val summaryCache: SummaryCache,
    private val googleAuthManager: GoogleAuthManager,
    private val pieChartFetcher: PieChartFetcher,
    private val pieChartCache: PieChartCache
) : ViewModel() {

    // Internal raw digits (no "¥" or commas)
    private var rawAmountDigits: String = ""

    // Job for auto-dismiss of Undo Snackbar after 5 seconds
    private var autoDismissJob: Job? = null
    // Job for pie chart fetching (cancellable to avoid stale results)
    private var pieChartFetchJob: Job? = null

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    init {
        // Load initial auth state and allowance amount
        viewModelScope.launch {
            val isAuthenticated = googleAuthManager.isAuthenticated()
            val allowanceAmount = settingsRepository.getAllowanceAmount()
            _uiState.update { it.copy(isAuthenticated = isAuthenticated, allowanceAmount = allowanceAmount) }
            // 認証状態確認後に年リストとグラフを取得（認証済みの場合のみ）
            if (isAuthenticated) {
                fetchAvailableYears()
                val today = LocalDate.now()
                fetchPieChartForPeriod(ChartPeriod.Monthly(today.year, today.monthValue))
            }
        }

        // Collect SummaryCache StateFlows and reflect in uiState
        viewModelScope.launch {
            combine(summaryCache.yearlyTotal, summaryCache.monthlyTotal) { yearly, monthly ->
                Pair(yearly, monthly)
            }.collect { (yearly, monthly) ->
                _uiState.update { it.copy(yearlyTotal = yearly, monthlyTotal = monthly) }
            }
        }

        // Fetch summary on init (Task 7.6)
        viewModelScope.launch {
            _uiState.update { it.copy(isSummaryLoading = true) }
            try {
                summaryFetcher.fetchSummary()
            } catch (e: Exception) {
                // On failure, summaryCache already set to null inside fetchSummary
            } finally {
                _uiState.update { it.copy(isSummaryLoading = false) }
            }
        }

        // Retry pending expenses on init (Task 7.2)
        viewModelScope.launch {
            retryPendingExpenses()
        }

        // Fetch available years and initial pie chart data は上の launch ブロックで実行済み
    }

    // ─── Task 7.1: Core input handlers ───────────────────────────────────────

    /**
     * Called when the user types in the amount field.
     * Receives digits-only string from AmountInputTextField.
     * Formats with "¥" prefix and comma separators for display.
     */
    fun onAmountChange(digits: String) {
        // digits はすでに数字のみ（AmountInputTextField 側でフィルタ済み）
        rawAmountDigits = digits
        val formatted = AmountFormatter.formatForDisplay(rawAmountDigits)
        _uiState.update { it.copy(amountText = formatted, error = null) }
    }

    /**
     * Called when the user changes the date.
     */
    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    /**
     * Called when the user toggles the credit card checkbox.
     */
    fun onCreditCardChange(isCreditCard: Boolean) {
        _uiState.update { it.copy(isCreditCard = isCreditCard) }
    }

    /**
     * Called when the user types in the category field.
     * Triggers suggestion lookup.
     */
    fun onCategoryChange(text: String) {
        _uiState.update { it.copy(category = text, error = null) }
        viewModelScope.launch {
            val suggestions = categorySuggestionEngine.suggest(text)
            _uiState.update { it.copy(suggestions = suggestions) }
        }
    }

    /**
     * Called when the user selects a suggestion from the list.
     */
    fun onSuggestionSelect(category: String) {
        _uiState.update { it.copy(category = category, suggestions = emptyList()) }
    }

    // ─── Task 7.2: Record flow ────────────────────────────────────────────────

    /**
     * Called when the user taps the Record button.
     */
    fun onRecordClick() {
        val state = _uiState.value
        val validationResult = expenseValidator.validate(
            amount = rawAmountDigits,
            category = state.category,
            isAuthenticated = state.isAuthenticated
        )

        when (validationResult) {
            is ValidationResult.Invalid -> {
                when (validationResult.error) {
                    ValidationError.NOT_AUTHENTICATED -> {
                        _uiState.update {
                            it.copy(
                                error = validationResult.error,
                                message = "Googleアカウントを連携してください"
                            )
                        }
                        // Note: onSignIn() requires Activity Context, which must be called from UI layer
                    }
                    else -> {
                        _uiState.update { it.copy(error = validationResult.error) }
                    }
                }
                return
            }
            is ValidationResult.Valid -> {
                // Proceed with recording
            }
        }

        val amountInt = rawAmountDigits.toIntOrNull() ?: return
        val expense = ExpenseRecord(
            amount = amountInt,
            date = state.date,
            category = state.category,
            isCreditCard = state.isCreditCard
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Save to PendingExpenseRepository first (local-first), capture generated ID
            val pendingId = pendingExpenseRepository.save(expense)

            try {
                // Resolve spreadsheet target
                val target = spreadsheetResolver.resolve(expense.date)

                // Append to spreadsheet
                val appendResult = googleSheetsRepository.appendExpense(
                    spreadsheetId = target.spreadsheetId,
                    sheetName = target.sheetName,
                    expense = expense
                )

                if (appendResult.isSuccess) {
                    val result = appendResult.getOrThrow()

                    // Delete from PendingExpenseRepository using the generated ID
                    pendingExpenseRepository.delete(pendingId)

                    // Record category in history
                    categorySuggestionEngine.recordCategory(expense.category)

                    // Build undo target
                    val undoTarget = UndoTarget(
                        spreadsheetId = result.spreadsheetId,
                        sheetId = result.sheetId,
                        sheetName = result.sheetName,
                        rowIndex = result.rowIndex,
                        expense = expense
                    )

                    // Build undo label: "{M/d} ¥{amount} {category}"
                    val dateLabel = DateFormatter.formatMd(expense.date)
                    val amountLabel = AmountFormatter.formatForDisplay(expense.amount.toString())
                    val undoExpenseLabel = "$dateLabel $amountLabel ${expense.category}"

                    // Update summary cache (今月・今年のデータのみ)
                    val today = LocalDate.now()
                    val isThisYear = expense.date.year == today.year
                    val isThisMonth = isThisYear && expense.date.monthValue == today.monthValue
                    if (isThisYear) {
                        summaryCache.adjust(expense.amount, adjustYearly = true, adjustMonthly = isThisMonth)
                    }

                    // Update pie chart cache
                    updatePieChartCacheOnRecord(expense)

                    // Clear inputs and show Undo Snackbar
                    rawAmountDigits = ""
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            amountText = "",
                            category = "",
                            isCreditCard = true,
                            date = LocalDate.now(),
                            suggestions = emptyList(),
                            error = null,
                            message = null,
                            undoTarget = undoTarget,
                            showUndoSnackbar = true,
                            undoExpenseLabel = undoExpenseLabel
                        )
                    }

                    // Start 5-second auto-dismiss
                    startAutoDismiss()

                } else {
                    val errorMsg = appendResult.exceptionOrNull()?.message ?: "記録に失敗しました"
                    _uiState.update {
                        it.copy(isLoading = false, message = errorMsg)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ExpenseViewModel", "記録失敗", e)
                _uiState.update {
                    it.copy(isLoading = false, message = "記録に失敗しました: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    /**
     * Retry sending any pending expenses in background.
     */
    private suspend fun retryPendingExpenses() {
        try {
            val pending = pendingExpenseRepository.getAll()
            for (expense in pending) {
                try {
                    val target = spreadsheetResolver.resolve(expense.date)
                    val result = googleSheetsRepository.appendExpense(
                        spreadsheetId = target.spreadsheetId,
                        sheetName = target.sheetName,
                        expense = expense
                    )
                    if (result.isSuccess) {
                        pendingExpenseRepository.delete(expense.id)
                    }
                } catch (e: Exception) {
                    // Silently ignore individual retry failures
                }
            }
        } catch (e: Exception) {
            // Silently ignore retry failures
        }
    }

    // ─── Task 7.3: Undo ───────────────────────────────────────────────────────

    /**
     * Called when the user taps the Undo button in the Snackbar.
     */
    fun onUndoClick() {
        val undoTarget = _uiState.value.undoTarget ?: return

        viewModelScope.launch {
            try {
                val result = googleSheetsRepository.deleteRow(
                    spreadsheetId = undoTarget.spreadsheetId,
                    sheetId = undoTarget.sheetId,
                    rowIndex = undoTarget.rowIndex
                )

                if (result.isSuccess) {
                    // Adjust summary cache (今月・今年のデータのみ)
                    val today = LocalDate.now()
                    val isThisYear = undoTarget.expense.date.year == today.year
                    val isThisMonth = isThisYear && undoTarget.expense.date.monthValue == today.monthValue
                    if (isThisYear) {
                        summaryCache.adjust(-undoTarget.expense.amount, adjustYearly = true, adjustMonthly = isThisMonth)
                    }

                    // Update pie chart cache
                    updatePieChartCacheOnUndo(undoTarget.expense)

                    // Cancel auto-dismiss job
                    autoDismissJob?.cancel()
                    autoDismissJob = null

                    // Clear undo state
                    _uiState.update {
                        it.copy(
                            undoTarget = null,
                            showUndoSnackbar = false,
                            undoExpenseLabel = ""
                        )
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "取り消しに失敗しました"
                    _uiState.update { it.copy(message = errorMsg) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "取り消しに失敗しました: ${e.message}") }
            }
        }
    }

    /**
     * Called when the Snackbar is dismissed (e.g., swiped away or timed out).
     */
    fun onSnackbarDismiss() {
        autoDismissJob?.cancel()
        autoDismissJob = null
        _uiState.update {
            it.copy(
                undoTarget = null,
                showUndoSnackbar = false,
                undoExpenseLabel = ""
            )
        }
    }

    /**
     * Starts a 5-second auto-dismiss coroutine for the Undo Snackbar.
     * Cancels any existing auto-dismiss job first.
     */
    private fun startAutoDismiss() {
        autoDismissJob?.cancel()
        autoDismissJob = viewModelScope.launch {
            delay(5_000L)
            onSnackbarDismiss()
        }
    }

    // ─── Task 7.6: Summary display & refresh ─────────────────────────────────

    /**
     * Fetches totals from spreadsheet and updates summaryCache.
     */
    fun refreshSummary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSummaryLoading = true) }
            try {
                summaryFetcher.fetchSummary()
                // summaryCache is updated inside fetchSummary(); StateFlow collection handles uiState update
            } catch (e: Exception) {
                // On failure, summaryCache already set to null inside fetchSummary
            } finally {
                _uiState.update { it.copy(isSummaryLoading = false) }
            }
        }
    }

    /**
     * Called when the user performs a pull-to-refresh gesture.
     */
    fun onPullToRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                summaryFetcher.fetchSummary()
                pieChartCache.clearAll()
                fetchPieChartForPeriod(_uiState.value.selectedPeriod)
            } catch (e: Exception) {
                // summaryCache already handles null on failure
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // ─── Task 7.8: Allowance settings & remaining toggle ─────────────────────

    /**
     * Called when the user taps the Settings (gear) icon.
     */
    fun onSettingsIconClick() {
        _uiState.update { it.copy(showAllowanceDialog = true) }
    }

    /**
     * Called when the Allowance dialog is dismissed without saving.
     */
    fun onAllowanceDialogDismiss() {
        _uiState.update { it.copy(showAllowanceDialog = false) }
    }

    /**
     * Called when the user confirms a new allowance amount.
     * Saves via settingsRepository and updates state.
     */
    fun onAllowanceChange(amount: Int?) {
        viewModelScope.launch {
            settingsRepository.saveAllowanceAmount(amount)
            _uiState.update {
                it.copy(
                    allowanceAmount = amount,
                    showAllowanceDialog = false,
                    // Reset remaining mode if allowance is cleared
                    isRemainingMode = if (amount == null) false else it.isRemainingMode
                )
            }
        }
    }

    /**
     * Called when the user taps the summary area.
     * Toggles isRemainingMode only if allowanceAmount is set.
     */
    fun onSummaryAreaClick() {
        val state = _uiState.value
        if (state.allowanceAmount != null) {
            _uiState.update { it.copy(isRemainingMode = !it.isRemainingMode) }
        }
        // If allowanceAmount is null, do nothing
    }

    // ─── Task 7.11: Spreadsheet navigation ───────────────────────────────────

    /**
     * Called when the user taps the Open Spreadsheet button.
     */
    fun onOpenSpreadsheetClick() {
        val state = _uiState.value
        if (!state.isAuthenticated) {
            _uiState.update { it.copy(message = "Googleアカウントを連携してください") }
            // Note: onSignIn() requires Activity Context, which must be called from UI layer
            return
        }

        viewModelScope.launch {
            try {
                val url = spreadsheetResolver.getCurrentYearSpreadsheetUrl()
                if (url == null) {
                    _uiState.update {
                        it.copy(message = "スプレッドシートがまだ作成されていません。経費を記録すると自動作成されます")
                    }
                } else {
                    _uiState.update { it.copy(spreadsheetUrl = url) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "スプレッドシートの取得に失敗しました: ${e.message}") }
            }
        }
    }

    /**
     * Called to initiate Google Sign-In.
     * @param context Activity Context (required for Credential Manager UI)
     */
    fun onSignIn(context: Context) {
        viewModelScope.launch {
            try {
                val result = googleAuthManager.signIn(context)
                when (result) {
                    is com.example.spendmgr.data.AuthResult.Success -> {
                        _uiState.update { it.copy(isAuthenticated = true, message = null) }
                        // Refresh summary and pie chart after sign-in
                        refreshSummary()
                        fetchAvailableYears()
                        fetchPieChartForPeriod(_uiState.value.selectedPeriod)
                    }                    is com.example.spendmgr.data.AuthResult.Error -> {
                        _uiState.update { it.copy(message = result.message) }
                    }
                    is com.example.spendmgr.data.AuthResult.Cancelled -> {
                        // User cancelled, do nothing
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "認証に失敗しました: ${e.message}") }
            }
        }
    }

    /**
     * Clears the spreadsheet URL after it has been consumed by the UI (browser opened).
     */
    fun onSpreadsheetUrlConsumed() {
        _uiState.update { it.copy(spreadsheetUrl = null) }
    }

    /**
     * Clears the current message after it has been shown to the user.
     */
    fun onMessageConsumed() {
        _uiState.update { it.copy(message = null) }
    }

    // ─── Pie chart methods ────────────────────────────────────────────────────

    fun onPeriodSelect(period: ChartPeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        viewModelScope.launch {
            // キャッシュがあれば即座に表示（ローディング表示なし）
            val cached = pieChartCache.get(period)
            if (cached != null) {
                _uiState.update { it.copy(pieChartData = cached, isPieChartLoading = false) }
            } else {
                // キャッシュがない場合は即座にローディング表示
                _uiState.update { it.copy(pieChartData = null, isPieChartLoading = true) }
            }
            // バックグラウンドで最新データを取得
            fetchPieChartForPeriod(period)
        }
    }

    private fun fetchPieChartForPeriod(period: ChartPeriod) {
        // 前のフェッチジョブをキャンセルして新しいジョブを起動
        pieChartFetchJob?.cancel()
        pieChartFetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isPieChartLoading = true) }
            val result = pieChartFetcher.fetchPieChartData(period)
            if (result.isSuccess) {
                _uiState.update { it.copy(pieChartData = result.getOrNull(), isPieChartLoading = false, pieChartError = false) }
            } else {
                _uiState.update { it.copy(isPieChartLoading = false, pieChartError = true) }
            }
        }
    }

    private fun fetchAvailableYears() {
        viewModelScope.launch {
            // 未認証時はスキップ（Drive API 呼び出しを防ぐ）
            if (!_uiState.value.isAuthenticated) return@launch
            val result = pieChartFetcher.fetchAvailableYears()
            if (result.isSuccess) {
                val years = result.getOrDefault(emptyList())
                // 常に更新する（現在の年は ChartPeriodSelector 側で補完する）
                _uiState.update { it.copy(availableYears = years) }
            }
        }
    }

    private suspend fun updatePieChartCacheOnRecord(expense: ExpenseRecord) {
        val today = LocalDate.now()
        if (expense.date.year == today.year && expense.date.monthValue == today.monthValue) {
            pieChartCache.addExpense(ChartPeriod.Monthly(today.year, today.monthValue), expense.category, expense.amount, expense.isCreditCard)
        }
        if (expense.date.year == today.year) {
            pieChartCache.addExpense(ChartPeriod.Yearly(today.year), expense.category, expense.amount, expense.isCreditCard)
        }
        val period = _uiState.value.selectedPeriod
        val updated = pieChartCache.get(period)
        _uiState.update { it.copy(pieChartData = updated) }
    }

    private suspend fun updatePieChartCacheOnUndo(expense: ExpenseRecord) {
        val today = LocalDate.now()
        if (expense.date.year == today.year && expense.date.monthValue == today.monthValue) {
            pieChartCache.removeExpense(ChartPeriod.Monthly(today.year, today.monthValue), expense.category, expense.amount, expense.isCreditCard)
        }
        if (expense.date.year == today.year) {
            pieChartCache.removeExpense(ChartPeriod.Yearly(today.year), expense.category, expense.amount, expense.isCreditCard)
        }
        val period = _uiState.value.selectedPeriod
        val updated = pieChartCache.get(period)
        _uiState.update { it.copy(pieChartData = updated) }
    }
}
