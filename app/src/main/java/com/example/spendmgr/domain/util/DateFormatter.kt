package com.example.spendmgr.domain.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 日付フォーマットユーティリティ
 * - LocalDate → 「M/d」形式変換（先頭ゼロなし）
 */
object DateFormatter {

    // 先頭ゼロなしの M/d フォーマット
    private val MD_FORMATTER = DateTimeFormatter.ofPattern("M/d")

    /**
     * LocalDateを「M/d」形式（先頭ゼロなし）の文字列に変換する。
     * 例: LocalDate(2026, 4, 22) → "4/22"
     *     LocalDate(2026, 12, 1) → "12/1"
     */
    fun formatMd(date: LocalDate): String {
        return date.format(MD_FORMATTER)
    }

    /**
     * 「M/d」形式の文字列をLocalDateに変換する（年は指定年を使用）。
     * 例: "4/22", 2026 → LocalDate(2026, 4, 22)
     */
    fun parseMd(mdString: String, year: Int = LocalDate.now().year): LocalDate {
        val parts = mdString.split("/")
        require(parts.size == 2) { "Invalid M/d format: $mdString" }
        val month = parts[0].toInt()
        val day = parts[1].toInt()
        return LocalDate.of(year, month, day)
    }
}
