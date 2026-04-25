package com.example.spendmgr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spendmgr.domain.model.PieChartData
import com.example.spendmgr.domain.util.AmountFormatter

/**
 * 各カテゴリの色・名前・金額・割合を金額降順で表示する凡例コンポーネント。
 *
 * Requirements: 3.4, 7.4
 */
@Composable
fun ChartLegend(
    data: PieChartData,
    modifier: Modifier = Modifier
) {
    val sortedCategories = data.categories.sortedByDescending { it.amount }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (category in sortedCategories) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Colored square
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(category.color)
                )

                // Category name (takes remaining space)
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Amount formatted as ¥X,XXX
                Text(
                    text = AmountFormatter.formatForDisplay(category.amount.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Percentage formatted as XX.X%
                Text(
                    text = "${"%.1f".format(category.percentage)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
