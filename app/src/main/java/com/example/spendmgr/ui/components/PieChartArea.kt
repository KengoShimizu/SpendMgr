package com.example.spendmgr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spendmgr.domain.model.ChartPeriod
import com.example.spendmgr.domain.model.PieChartData
import com.example.spendmgr.domain.util.AmountFormatter

@Composable
fun PieChartArea(
    pieChartData: PieChartData?,
    selectedPeriod: ChartPeriod,
    availableYears: List<Int>,
    isLoading: Boolean,
    isAuthenticated: Boolean,
    hasData: Boolean,
    pieChartError: Boolean,
    onPeriodSelect: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 期間セレクタ + 家計立替を同じ行に
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartPeriodSelector(
                    selectedPeriod = selectedPeriod,
                    availableYears = availableYears,
                    onPeriodSelect = onPeriodSelect
                )

                // 家計立替（データがある場合のみ）
                val creditCardTotal = pieChartData?.creditCardTotal
                if (isAuthenticated && creditCardTotal != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "家計立替",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = AmountFormatter.formatLong(creditCardTotal.toLong()),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // セレクタとグラフの間の余白
            Spacer(modifier = Modifier.height(36.dp))

            // グラフコンテンツ
            when {
                !isAuthenticated -> {
                    Text(
                        text = "Googleアカウントを連携してください",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                isLoading && pieChartData == null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                pieChartError && pieChartData == null -> {
                    Text(
                        text = "データの取得に失敗しました",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                pieChartData != null && pieChartData.categories.isEmpty() -> {
                    Text(
                        text = "この期間の経費データがありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                pieChartData != null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        PieChart(data = pieChartData)
                    }
                    Spacer(modifier = Modifier.height(36.dp))
                    ChartLegend(
                        data = pieChartData,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                !hasData -> {
                    Text(
                        text = "データがありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
