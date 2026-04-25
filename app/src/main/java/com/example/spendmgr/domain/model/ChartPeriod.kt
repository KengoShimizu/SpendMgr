package com.example.spendmgr.domain.model

/**
 * 円グラフに表示するデータの期間を表す sealed class。
 * 年プルダウンと月プルダウンの組み合わせで決定される。
 */
sealed class ChartPeriod {
    /**
     * 特定の年の特定の月
     * @param year 対象年
     * @param month 対象月（1〜12）
     */
    data class Monthly(val year: Int, val month: Int) : ChartPeriod()

    /**
     * 特定の年の年間合計
     * @param year 対象年
     */
    data class Yearly(val year: Int) : ChartPeriod()

    /**
     * DataStore のキーとして使用する文字列を返す。
     */
    fun toKey(): String = when (this) {
        is Monthly -> "MONTHLY_${year}_${month}"
        is Yearly -> "YEARLY_$year"
    }
}
