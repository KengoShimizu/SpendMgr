package com.example.spendmgr.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 金額入力テキストフィールド。
 * - OS標準の数値キーボード (KeyboardType.Number)
 * - 起動時に自動フォーカス (FocusRequester)
 * - "¥" はprefixとして固定表示し、カーソルは常に末尾に置く
 * - ユーザーが入力した数字のみを onAmountChange に渡す（¥・カンマなし）
 * - IMEアクション Next でカテゴリ入力にフォーカス移動
 *
 * Requirements: 2.1, 2.2, 2.4, 2.5, 2.6
 */
@Composable
fun AmountInputTextField(
    amount: String,          // ViewModel から渡される表示用文字列（例: "¥10,000" or ""）
    onAmountChange: (String) -> Unit,  // 数字のみを返す（例: "10000"）
    focusRequester: FocusRequester,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null
) {
    // TextFieldValue でカーソル位置を制御する
    // amount が "" のときは空、それ以外は末尾にカーソルを置く
    var textFieldValue by remember(amount) {
        mutableStateOf(
            TextFieldValue(
                text = amount,
                selection = TextRange(amount.length)
            )
        )
    }

    // 起動時に自動フォーカスを取得する (Req 2.4)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            // ユーザーが入力した文字列から数字のみ抽出
            val digits = newValue.text.filter { it.isDigit() }
            // ViewModel に数字のみを通知（ViewModel 側でフォーマットして amountText を更新）
            onAmountChange(digits)
            // textFieldValue は remember(amount) で amount 変化時に再生成されるため、
            // ここでは直接更新しない（amount の更新を待つ）
        },
        label = { Text("金額") },
        placeholder = { Text("¥0") },
        isError = errorMessage != null,
        supportingText = if (errorMessage != null) {
            { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
        } else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number, // Req 2.1
            imeAction = ImeAction.Next          // Req 2.5
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() }               // Req 2.6
        ),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}
