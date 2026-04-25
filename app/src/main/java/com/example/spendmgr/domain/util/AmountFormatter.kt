package com.example.spendmgr.domain.util

import java.text.NumberFormat
import java.util.Locale

/**
 * 金額フォーマットユーティリティ
 * - 数字文字列 → "¥" 付きカンマ区切り表示変換
 * - 表示文字列 → 内部数値変換処理
 */
object AmountFormatter {

    /**
     * Long 値を "¥" 付きカンマ区切り表示文字列に変換する。
     * 負の値は "-¥5,000" の形式で返す。
     * 例: 10000L → "¥10,000"、-5000L → "-¥5,000"
     */
    fun formatLong(number: Long): String {
        val formatter = NumberFormat.getNumberInstance(Locale.JAPAN)
        val formatted = formatter.format(number)
        return if (number < 0) {
            "-¥${formatted.removePrefix("-")}"
        } else {
            "¥$formatted"
        }
    }

    /**
     * 数字文字列を "¥" 付きカンマ区切り表示文字列に変換する。
     * 例: "10000" → "¥10,000"、"-5000" → "¥-5,000"
     * 空文字列の場合は空文字列を返す。
     */
    fun formatForDisplay(digits: String): String {
        if (digits.isEmpty()) return ""
        val number = digits.toLongOrNull() ?: return digits
        val formatter = NumberFormat.getNumberInstance(Locale.JAPAN)
        val formatted = formatter.format(number)
        // 負の数の場合は "¥-5,000" ではなく "-¥5,000" の形式にする
        return if (number < 0) {
            "-¥${formatted.removePrefix("-")}"
        } else {
            "¥$formatted"
        }
    }

    /**
     * 表示文字列から内部数値（Int）に変換する。
     * 例: "¥10,000" → 10000
     * 変換できない場合はnullを返す。
     */
    fun parseToInt(displayText: String): Int? {
        val digits = displayText.replace("¥", "").replace(",", "").trim()
        return digits.toIntOrNull()
    }

    /**
     * ユーザー入力（数字のみ）を受け取り、表示用文字列を返す。
     * 数字以外の文字は除去する。
     * 例: "1000" → "¥1,000"
     */
    fun onAmountInput(input: String): String {
        val digits = input.filter { it.isDigit() }
        return formatForDisplay(digits)
    }

    /**
     * 表示用文字列から数字のみの文字列を取り出す。
     * 例: "¥10,000" → "10000"
     */
    fun extractDigits(displayText: String): String {
        return displayText.replace("¥", "").replace(",", "").trim()
    }
}
