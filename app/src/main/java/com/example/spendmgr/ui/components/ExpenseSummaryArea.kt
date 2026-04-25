package com.example.spendmgr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.spendmgr.domain.util.AmountFormatter

/**
 * 今年・今月の合計経費表示エリア。
 * - 通常表示: 「今年: ¥(合計額)」「今月: ¥(合計額)」(Req 14.3)
 * - 残額表示: 「今年: ¥(お小遣い×12 − 合計額)」「今月: ¥(お小遣い − 合計額)」(Req 14.4, 14.5)
 * - ローディング中はスピナー表示
 * - 取得失敗時は「—」プレースホルダー (Req 13.7, 13.8, 13.9)
 * - お小遣い設定時のみタップでトグル切り替え (Req 14.2, 14.6)
 *
 * Requirements: 13.1, 14.2, 14.3, 14.4, 14.5, 14.6
 */
@Composable
fun ExpenseSummaryArea(
    yearlyTotal: Int?,
    monthlyTotal: Int?,
    isLoading: Boolean,
    isToggleEnabled: Boolean,
    isRemainingMode: Boolean,
    allowanceAmount: Int?,
    onToggleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clickableModifier = if (isToggleEnabled) {
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggleClick)
    } else {
        modifier.fillMaxWidth()
    }

    Card(
        modifier = clickableModifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 今年の表示
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isRemainingMode) "今年の残額" else "今年",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isRemainingMode)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatSummaryValue(
                            total = yearlyTotal,
                            isRemainingMode = isRemainingMode,
                            allowanceAmount = allowanceAmount,
                            isYearly = true
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isRemainingMode)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 今月の表示
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isRemainingMode) "今月の残額" else "今月",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isRemainingMode)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatSummaryValue(
                            total = monthlyTotal,
                            isRemainingMode = isRemainingMode,
                            allowanceAmount = allowanceAmount,
                            isYearly = false
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isRemainingMode)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 合計額または残額の表示文字列を返す。
 * - total が null の場合は「—」を返す (Req 13.7)
 * - 通常表示: 「¥(合計額)」
 * - 残額表示: 「¥(お小遣い − 合計額)」または「¥(お小遣い×12 − 合計額)」
 */
private fun formatSummaryValue(
    total: Int?,
    isRemainingMode: Boolean,
    allowanceAmount: Int?,
    isYearly: Boolean
): String {
    if (total == null) return "—"

    return if (isRemainingMode && allowanceAmount != null) {
        // 残額表示 (Req 14.4, 14.5)
        val remaining = if (isYearly) {
            allowanceAmount * 12 - total
        } else {
            allowanceAmount - total
        }
        // 負の残額も正しく表示できるよう Long 経由でフォーマット
        AmountFormatter.formatLong(remaining.toLong())
    } else {
        // 通常表示 (Req 14.3)
        AmountFormatter.formatForDisplay(total.toString())
    }
}
