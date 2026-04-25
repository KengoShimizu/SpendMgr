package com.example.spendmgr.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import com.example.spendmgr.domain.model.ValidationError
import com.example.spendmgr.ui.components.AllowanceDialog
import com.example.spendmgr.ui.components.AmountInputTextField
import com.example.spendmgr.ui.components.CategoryInput
import com.example.spendmgr.ui.components.DatePickerField
import com.example.spendmgr.ui.components.ExpenseSummaryArea
import com.example.spendmgr.ui.components.PieChartArea
import com.example.spendmgr.viewmodel.ExpenseViewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

/**
 * 経費入力メイン画面。
 * 全 UI コンポーネントを配置・接続する。
 *
 * Requirements: 1.1, 1.2, 11.1, 13.5, 15.1
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEntryScreen(
    viewModel: ExpenseViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // FocusRequester: Amount → Category の順にフォーカスを移動する
    val amountFocusRequester = remember { FocusRequester() }
    val categoryFocusRequester = remember { FocusRequester() }

    // Undo Snackbar 用の SnackbarHostState
    val snackbarHostState = remember { SnackbarHostState() }

    // スプレッドシート URL が設定されたらSheetsアプリ優先で開く (Req 11.2)
    LaunchedEffect(uiState.spreadsheetUrl) {
        val url = uiState.spreadsheetUrl
        if (url != null) {
            // まずSheetsアプリで開こうとする
            val sheetsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.google.android.apps.docs.editors.sheets")
            }
            // Sheetsアプリがない場合はDriveアプリ、それもない場合はブラウザにフォールバック
            val driveIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.google.android.apps.docs")
            }
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

            when {
                sheetsIntent.resolveActivity(context.packageManager) != null ->
                    context.startActivity(sheetsIntent)
                driveIntent.resolveActivity(context.packageManager) != null ->
                    context.startActivity(driveIntent)
                else ->
                    context.startActivity(browserIntent)
            }
            viewModel.onSpreadsheetUrlConsumed()
        }
    }

    // 一般メッセージを Snackbar で表示する
    // 未認証メッセージの場合は自動的にサインインフローを開始する
    LaunchedEffect(uiState.message) {
        val msg = uiState.message
        if (msg != null) {
            // 未認証メッセージの場合はサインインフローを開始
            if (msg.contains("Googleアカウントを連携してください")) {
                viewModel.onSignIn(context)
            } else {
                snackbarHostState.showSnackbar(msg)
            }
            viewModel.onMessageConsumed()
        }
    }

    // Undo Snackbar の表示制御
    // showUndoSnackbar が true になったら Snackbar を表示する
    // ActionPerformed（取り消しボタン）→ onUndoClick()
    // Dismissed（5秒タイムアウトまたはスワイプ）→ onSnackbarDismiss()
    LaunchedEffect(uiState.showUndoSnackbar) {
        if (uiState.showUndoSnackbar) {
            val result = snackbarHostState.showSnackbar(
                message = uiState.undoExpenseLabel,
                actionLabel = "取り消し",
                duration = androidx.compose.material3.SnackbarDuration.Indefinite
            )
            when (result) {
                androidx.compose.material3.SnackbarResult.ActionPerformed -> {
                    viewModel.onUndoClick()
                }
                androidx.compose.material3.SnackbarResult.Dismissed -> {
                    viewModel.onSnackbarDismiss()
                }
            }
        } else {
            // showUndoSnackbar が false になったら（5秒タイマー or undo完了）Snackbarを消す
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SpendMgr") },
                actions = {
                    // Open_Spreadsheet_Icon（スプレッドシートを開くアイコン）(Req 11.1)
                    IconButton(onClick = { viewModel.onOpenSpreadsheetClick() }) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "スプレッドシートを開く"
                        )
                    }
                    // Settings_Icon（右上ギアアイコン）(Req 15.1)
                    IconButton(onClick = { viewModel.onSettingsIconClick() }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "お小遣い設定"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            // Scaffold の snackbarHost に配置することで、
            // Record_Button / Open_Spreadsheet_Button / Amount_Input に重ならない (Req 12.1)
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->

        // プルトゥリフレッシュ (Req 13.5)
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.onPullToRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // 合計経費エリア (Req 13.1)
                ExpenseSummaryArea(
                    yearlyTotal = uiState.yearlyTotal,
                    monthlyTotal = uiState.monthlyTotal,
                    isLoading = uiState.isSummaryLoading,
                    isToggleEnabled = uiState.allowanceAmount != null,
                    isRemainingMode = uiState.isRemainingMode,
                    allowanceAmount = uiState.allowanceAmount,
                    onToggleClick = { viewModel.onSummaryAreaClick() }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 金額入力 (Req 2.1, 2.2, 2.4, 2.5, 2.6)
                AmountInputTextField(
                    amount = uiState.amountText,
                    onAmountChange = { viewModel.onAmountChange(it) },
                    focusRequester = amountFocusRequester,
                    onNext = { categoryFocusRequester.requestFocus() },
                    errorMessage = when (uiState.error) {
                        ValidationError.AMOUNT_EMPTY -> "金額を入力してください"
                        else -> null
                    }
                )

                // 日付入力 (Req 3.1, 3.2, 3.3)
                DatePickerField(
                    date = uiState.date,
                    onDateChange = { viewModel.onDateChange(it) }
                )

                // カテゴリ入力 (Req 4.1, 4.2, 4.3, 4.5, 4.6)
                CategoryInput(
                    category = uiState.category,
                    currentSuggestions = uiState.suggestions,
                    onCategoryChange = { viewModel.onCategoryChange(it) },
                    onSuggestionSelect = { viewModel.onSuggestionSelect(it) },
                    focusRequester = categoryFocusRequester,
                    errorMessage = when (uiState.error) {
                        ValidationError.CATEGORY_EMPTY -> "カテゴリを入力してください"
                        else -> null
                    }
                )

                // クレジットカード払いチェックボックス
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 0.dp)
                        .offset(x = (-12).dp)  // Checkbox の内部パディング分を相殺
                ) {
                    Checkbox(
                        checked = uiState.isCreditCard,
                        onCheckedChange = { viewModel.onCreditCardChange(it) }
                    )
                    Text(
                        text = "クレジットカード払い（家計立替）",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                }

                // Record_Button (Req 5.1)
                Button(
                    onClick = { viewModel.onRecordClick() },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isLoading) "記録中..." else "記録")
                }

                // 円グラフエリア (Req 1.1)
                PieChartArea(
                    pieChartData = uiState.pieChartData,
                    selectedPeriod = uiState.selectedPeriod,
                    availableYears = uiState.availableYears,
                    isLoading = uiState.isPieChartLoading,
                    isAuthenticated = uiState.isAuthenticated,
                    hasData = uiState.isAuthenticated,  // if authenticated, assume data may exist
                    pieChartError = uiState.pieChartError,
                    onPeriodSelect = { viewModel.onPeriodSelect(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // お小遣い設定ダイアログ (Req 15.2, 15.3, 15.4, 15.5, 15.6)
    if (uiState.showAllowanceDialog) {
        AllowanceDialog(
            currentAmount = uiState.allowanceAmount,
            onConfirm = { amount -> viewModel.onAllowanceChange(amount) },
            onDismiss = { viewModel.onAllowanceDialogDismiss() }
        )
    }
}
