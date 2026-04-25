package com.example.spendmgr.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendmgr.domain.model.ChartPeriod
import java.time.LocalDate

/**
 * 「xx年」プルダウンと「xx月/年間」プルダウンの2つを横に並べた期間選択コンポーネント。
 */
@Composable
fun ChartPeriodSelector(
    selectedPeriod: ChartPeriod,
    availableYears: List<Int>,
    onPeriodSelect: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    var yearExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }

    val currentYear = LocalDate.now().year
    val years = if (availableYears.contains(currentYear)) {
        availableYears.sortedDescending()
    } else {
        (availableYears + currentYear).sortedDescending()
    }

    val selectedYear = when (selectedPeriod) {
        is ChartPeriod.Monthly -> selectedPeriod.year
        is ChartPeriod.Yearly -> selectedPeriod.year
    }
    val selectedMonth = when (selectedPeriod) {
        is ChartPeriod.Monthly -> selectedPeriod.month
        is ChartPeriod.Yearly -> null
    }
    val monthLabel = if (selectedMonth != null) "${selectedMonth}月" else "年間"

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 年プルダウン
        Box(modifier = Modifier.wrapContentSize()) {
            Surface(
                onClick = { yearExpanded = true },
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${selectedYear}年",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(
                expanded = yearExpanded,
                onDismissRequest = { yearExpanded = false }
            ) {
                years.forEach { year ->
                    DropdownMenuItem(
                        text = { Text("${year}年") },
                        onClick = {
                            val newPeriod = when (selectedPeriod) {
                                is ChartPeriod.Monthly -> ChartPeriod.Monthly(year, selectedPeriod.month)
                                is ChartPeriod.Yearly -> ChartPeriod.Yearly(year)
                            }
                            onPeriodSelect(newPeriod)
                            yearExpanded = false
                        }
                    )
                }
            }
        }

        // 月プルダウン
        Box(modifier = Modifier.wrapContentSize()) {
            Surface(
                onClick = { monthExpanded = true },
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = monthLabel,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(
                expanded = monthExpanded,
                onDismissRequest = { monthExpanded = false }
            ) {
                for (month in 1..12) {
                    DropdownMenuItem(
                        text = { Text("${month}月") },
                        onClick = {
                            onPeriodSelect(ChartPeriod.Monthly(selectedYear, month))
                            monthExpanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("年間") },
                    onClick = {
                        onPeriodSelect(ChartPeriod.Yearly(selectedYear))
                        monthExpanded = false
                    }
                )
            }
        }
    }
}
