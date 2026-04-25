package com.example.spendmgr.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * 経費記録成功後に表示される Undo Snackbar。
 * - Material 3 Snackbar コンポーネントを使用 (Req 12.2)
 * - 記録内容を「{M/d} ¥{金額}（{カテゴリ}）」形式で表示 (Req 12.3)
 * - 「取り消し」ボタンを含む (Req 12.1)
 * - 5秒後に自動消去 (Req 12.6) — 自動消去はViewModelのautoDismissJobが担当
 *
 * 配置は ExpenseEntryScreen の Scaffold の snackbarHost に渡すことで、
 * Record_Button / Open_Spreadsheet_Button / Amount_Input に重ならない位置に表示される (Req 12.1)
 *
 * Requirements: 12.1, 12.2, 12.3, 12.6
 */
@Composable
fun UndoSnackbarHost(
    visible: Boolean,
    expenseLabel: String,
    onUndoClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // visible が true になったら Snackbar を表示する
    LaunchedEffect(visible, expenseLabel) {
        if (visible) {
            snackbarHostState.showSnackbar(
                message = expenseLabel,
                actionLabel = "取り消し",
                withDismissAction = false
            )
            // showSnackbar が返ったとき（アクションまたは自動消去）は dismiss 扱い
            onDismiss()
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier,
        snackbar = { snackbarData ->
            Snackbar(
                action = {
                    TextButton(
                        onClick = {
                            snackbarData.performAction()
                            onUndoClick()
                        }
                    ) {
                        Text("取り消し")
                    }
                }
            ) {
                Text(snackbarData.visuals.message)
            }
        }
    )
}
