package com.example.spendmgr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * カテゴリ入力フィールド。
 * - テキスト入力 (KeyboardType.Text、日本語入力キーボード)
 * - IMEアクション Done (Req 4.5)
 * - 入力に応じた前方一致候補リスト表示 (Req 4.1)
 * - 候補選択時にフィールドに反映 (Req 4.2)
 * - 候補なしの場合はリスト非表示 (Req 4.3)
 *
 * Requirements: 4.1, 4.2, 4.3, 4.5, 4.6
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryInput(
    category: String,
    currentSuggestions: List<String>,
    onCategoryChange: (String) -> Unit,
    onSuggestionSelect: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onDone: () -> Unit = {}
) {
    // 候補リストが存在する場合のみドロップダウンを展開する (Req 4.3)
    val expanded = currentSuggestions.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { /* 候補の表示はViewModelが制御するため、ここでは何もしない */ },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = category,
            onValueChange = onCategoryChange,
            label = { Text("カテゴリ") },
            isError = errorMessage != null,
            supportingText = if (errorMessage != null) {
                { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text, // Req 4.5, 4.6 (日本語テキスト入力)
                imeAction = ImeAction.Done        // Req 4.5
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone() }
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )

        // 候補リスト (Req 4.1, 4.2, 4.3)
        if (expanded) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { /* ViewModelが制御 */ }
            ) {
                currentSuggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = { onSuggestionSelect(suggestion) }
                    )
                }
            }
        }
    }
}
