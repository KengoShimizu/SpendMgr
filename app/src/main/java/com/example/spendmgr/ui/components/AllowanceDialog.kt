package com.example.spendmgr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

/**
 * お小遣い設定ダイアログ。
 * - 最初から編集可能（編集ボタン廃止）
 * - 現在の設定値を ¥10,000 形式で表示
 * - 「決定」で保存・ダイアログ閉じる (Req 15.5)
 * - 金額空で「決定」→ null 保存（お小遣い無制限）(Req 15.6)
 *
 * Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6
 */
@Composable
fun AllowanceDialog(
    currentAmount: Int?,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    // 入力フィールドの値（数字のみ保持）
    var rawDigits by remember {
        mutableStateOf(currentAmount?.toString() ?: "")
    }

    // TextFieldValue でカーソルを末尾に固定
    // 初期表示も ¥1,000 形式にする
    var textFieldValue by remember {
        val initial = if (currentAmount != null) {
            val formatter = NumberFormat.getNumberInstance(Locale.JAPAN)
            "¥${formatter.format(currentAmount)}"
        } else ""
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(initial.length)))
    }

    // 数字文字列を ¥10,000 形式に変換する
    fun formatAmount(digits: String): String {
        if (digits.isEmpty()) return ""
        val number = digits.toLongOrNull() ?: return digits
        val formatter = NumberFormat.getNumberInstance(Locale.JAPAN)
        return "¥${formatter.format(number)}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("お小遣い設定") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("毎月のお小遣い額を設定します。")
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        // 数字のみ受け付ける
                        val digits = newValue.text.filter { it.isDigit() }
                        rawDigits = digits
                        val formatted = formatAmount(digits)
                        textFieldValue = TextFieldValue(
                            text = formatted,
                            selection = TextRange(formatted.length)
                        )
                    },
                    label = { Text("金額（円）") },
                    placeholder = { Text("未設定") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = rawDigits.toIntOrNull()
                    onConfirm(amount)
                }
            ) {
                Text("決定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
