package com.example.spendmgr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.spendmgr.domain.model.PieChartData
import com.example.spendmgr.domain.util.AmountFormatter
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * ドーナツグラフを描画するコンポーネント。
 * - 各カテゴリを扇形セグメントとして描画し、中央に合計金額を表示する
 * - スライスをタップすると拡大ハイライト＋フローティングラベルを表示する
 * - タップ判定は pointerInput の size（実際のCanvasサイズpx）を使って計算する
 */
@Composable
fun PieChart(
    data: PieChartData,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(data) { mutableIntStateOf(-1) }
    var floatingOffset by remember { mutableStateOf(Offset.Zero) }

    val strokeWidthDp = 60.dp
    val selectedStrokeWidthDp = 72.dp

    BoxWithConstraints(
        modifier = modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(data) {
                    // pointerInput の size は実際のCanvasレイアウトサイズ（px）
                    // ここで全ての計算を行い、外部変数に依存しない
                    detectTapGestures { tapOffset ->
                        val canvasW = size.width.toFloat()
                        val canvasH = size.height.toFloat()
                        val canvasSize = minOf(canvasW, canvasH)

                        // 描画の topLeft と完全に一致する中心点
                        // topLeft.x = (canvasW - canvasSize) / 2f
                        // topLeft.y = (canvasH - canvasSize) / 2f
                        // 円の中心 = topLeft + canvasSize/2
                        val drawCenterX = (canvasW - canvasSize) / 2f + canvasSize / 2f  // = canvasW/2
                        val drawCenterY = (canvasH - canvasSize) / 2f + canvasSize / 2f  // = canvasH/2

                        val strokeWidthPx = strokeWidthDp.toPx()
                        // ストローク中心線半径 = canvasSize / 2f
                        // 外周 = 中心線半径 + strokeWidth/2
                        // 内周 = 中心線半径 - strokeWidth/2
                        val strokeCenterRadius = canvasSize / 2f
                        val outerRadius = strokeCenterRadius + strokeWidthPx / 2f
                        val innerRadius = strokeCenterRadius - strokeWidthPx / 2f

                        val dx = tapOffset.x - drawCenterX
                        val dy = tapOffset.y - drawCenterY
                        val distance = sqrt(dx * dx + dy * dy)

                        // 内周より内側（穴）はタップ不可、外周より外側は不問
                        if (distance < innerRadius) {
                            selectedIndex = -1
                            return@detectTapGestures
                        }

                        // 角度計算（上が0度、時計回り）
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                        if (angle < 0f) angle += 360f
                        if (angle >= 360f) angle -= 360f

                        // スライス判定
                        var startAngle = 0f
                        var hitIndex = -1
                        for (i in data.categories.indices) {
                            val sweepAngle = if (data.totalAmount > 0) {
                                (data.categories[i].amount.toFloat() / data.totalAmount.toFloat()) * 360f - 1f
                            } else 0f
                            val endAngle = startAngle + sweepAngle + 1f
                            if (angle >= startAngle && angle < endAngle) {
                                hitIndex = i
                                break
                            }
                            startAngle = endAngle
                        }

                        selectedIndex = if (hitIndex == selectedIndex) -1 else hitIndex
                        if (hitIndex >= 0) floatingOffset = tapOffset
                    }
                }
        ) {
            // 描画スコープ内でも size（実際のCanvasサイズpx）を使う
            val canvasSize = size.minDimension
            val strokeWidthPx = strokeWidthDp.toPx()
            val selectedStrokeWidthPx = selectedStrokeWidthDp.toPx()
            val topLeft = Offset(
                x = (size.width - canvasSize) / 2f,
                y = (size.height - canvasSize) / 2f
            )

            var startAngle = -90f

            for (i in data.categories.indices) {
                val category = data.categories[i]
                val isSelected = i == selectedIndex
                val sweepAngle = if (data.totalAmount > 0) {
                    (category.amount.toFloat() / data.totalAmount.toFloat()) * 360f - 1f
                } else 0f

                val color = when {
                    selectedIndex == -1 -> category.color
                    isSelected -> category.color
                    else -> category.color.copy(alpha = 0.3f)
                }
                val strokeWidth = if (isSelected) selectedStrokeWidthPx else strokeWidthPx

                val offsetPx = if (isSelected) 6.dp.toPx() else 0f
                val adjustedTopLeft = if (isSelected) {
                    val midAngle = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
                    Offset(
                        topLeft.x - (offsetPx * kotlin.math.cos(midAngle)).toFloat(),
                        topLeft.y - (offsetPx * kotlin.math.sin(midAngle)).toFloat()
                    )
                } else topLeft

                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = adjustedTopLeft,
                    size = Size(canvasSize, canvasSize),
                    style = Stroke(width = strokeWidth)
                )

                startAngle += sweepAngle + 1f
            }
        }

        // 中央に合計金額を表示
        Text(
            text = AmountFormatter.formatForDisplay(data.totalAmount.toString()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // 選択中スライスのフローティングラベル
        if (selectedIndex >= 0 && selectedIndex < data.categories.size) {
            val selected = data.categories[selectedIndex]
            val density = LocalDensity.current
            Card(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (floatingOffset.x - with(density) { 60.dp.toPx() }).toInt(),
                            y = (floatingOffset.y - with(density) { 80.dp.toPx() }).toInt()
                        )
                    }
                    .wrapContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "${selected.name}\n${AmountFormatter.formatForDisplay(selected.amount.toString())} (${"%.1f".format(selected.percentage)}%)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
