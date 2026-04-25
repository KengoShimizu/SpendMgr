package com.example.spendmgr.domain

import androidx.compose.ui.graphics.Color

/**
 * カテゴリ名から決定論的に色を割り当てるユーティリティ。
 * カテゴリ名の hashCode を使用して、事前定義された Material 3 カラーパレットから色を選択する。
 */
object CategoryColorAssigner {

    /**
     * Material 3 カラーシステムから選定した視覚的に区別しやすい 16 色のパレット。
     * ARGB 値はハードコードされており、外部依存なしに使用できる。
     */
    private val palette: List<Color> = listOf(
        Color(0xFF6750A4.toInt()), // MD3 Primary (Purple)
        Color(0xFF0061A4.toInt()), // MD3 Blue
        Color(0xFF006E1C.toInt()), // MD3 Green
        Color(0xFFBA1A1A.toInt()), // MD3 Red / Error
        Color(0xFF7D5260.toInt()), // MD3 Tertiary (Pink-Purple)
        Color(0xFF006A6A.toInt()), // MD3 Teal
        Color(0xFF8B5000.toInt()), // MD3 Orange-Brown
        Color(0xFF4A6741.toInt()), // MD3 Sage Green
        Color(0xFF00629D.toInt()), // MD3 Cyan-Blue
        Color(0xFF984061.toInt()), // MD3 Deep Pink
        Color(0xFF006B54.toInt()), // MD3 Emerald
        Color(0xFF6B5778.toInt()), // MD3 Lavender
        Color(0xFF8B4513.toInt()), // MD3 Saddle Brown
        Color(0xFF1B6CA8.toInt()), // MD3 Cobalt Blue
        Color(0xFF5C6200.toInt()), // MD3 Olive
        Color(0xFF9C4146.toInt()), // MD3 Crimson
    )

    /**
     * カテゴリ名から決定論的に色を割り当てる。
     * 同じカテゴリ名には常に同じ色が返される。
     *
     * @param category カテゴリ名
     * @return Material 3 カラーパレットからの色
     */
    fun colorFor(category: String): Color {
        val index = Math.floorMod(category.hashCode(), palette.size)
        return palette[index]
    }
}
