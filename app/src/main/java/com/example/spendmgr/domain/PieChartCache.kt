package com.example.spendmgr.domain

import com.example.spendmgr.data.SettingsRepository
import com.example.spendmgr.domain.model.CategoryData
import com.example.spendmgr.domain.model.ChartPeriod
import com.example.spendmgr.domain.model.PieChartData

/**
 * カテゴリ別経費データを DataStore に永続化するキャッシュ。
 * ChartPeriod をキーとして保持する。
 */
class PieChartCache(private val settingsRepository: SettingsRepository) {

    /**
     * 指定期間のキャッシュデータを取得する。
     * JSON からデシリアライズし、各カテゴリの色を CategoryColorAssigner で再計算する。
     *
     * @param period 取得する期間
     * @return キャッシュされたデータ。未キャッシュの場合はnull
     */
    suspend fun get(period: ChartPeriod): PieChartData? {
        val json = settingsRepository.getPieChartCache(period.toKey()) ?: return null
        val data = PieChartData.fromJson(json) ?: return null
        // 色を CategoryColorAssigner で再計算して補完する
        val categoriesWithColors = data.categories.map { cat ->
            cat.copy(color = CategoryColorAssigner.colorFor(cat.name))
        }
        return data.copy(categories = categoriesWithColors)
    }

    /**
     * 指定期間のデータをキャッシュに保存し、DataStore に永続化する。
     *
     * @param period 保存する期間
     * @param data 保存するデータ
     */
    suspend fun put(period: ChartPeriod, data: PieChartData) {
        val json = data.toJson()
        settingsRepository.savePieChartCache(period.toKey(), json)
    }

    /**
     * 指定期間のキャッシュに経費を加算する。
     * カテゴリがキャッシュに存在しない場合（新カテゴリ）は新規エントリとして追加する。
     * キャッシュ自体が存在しない場合（未取得）は何もしない。
     *
     * @param period 更新する期間
     * @param category カテゴリ名
     * @param amount 加算する金額
     */
    suspend fun addExpense(period: ChartPeriod, category: String, amount: Int, isCreditCard: Boolean = true) {
        val current = get(period) ?: return

        val updatedCategories = current.categories.toMutableList()
        val existingIndex = updatedCategories.indexOfFirst { it.name == category }

        if (existingIndex >= 0) {
            val existing = updatedCategories[existingIndex]
            updatedCategories[existingIndex] = existing.copy(amount = existing.amount + amount)
        } else {
            updatedCategories.add(
                CategoryData(
                    name = category,
                    amount = amount,
                    percentage = 0f,
                    color = CategoryColorAssigner.colorFor(category)
                )
            )
        }

        val newCreditCardTotal = if (isCreditCard) {
            (current.creditCardTotal ?: 0) + amount
        } else {
            current.creditCardTotal
        }

        val updated = recalculatePercentages(
            PieChartData(categories = updatedCategories, totalAmount = current.totalAmount, creditCardTotal = newCreditCardTotal)
        )
        put(period, updated)
    }

    /**
     * 指定期間のキャッシュから経費を減算する。
     * カテゴリがキャッシュに存在しない場合は何もしない。
     * 減算後の金額が0以下になった場合はそのカテゴリをキャッシュから削除する。
     * キャッシュ自体が存在しない場合（未取得）は何もしない。
     *
     * @param period 更新する期間
     * @param category カテゴリ名
     * @param amount 減算する金額
     */
    suspend fun removeExpense(period: ChartPeriod, category: String, amount: Int, isCreditCard: Boolean = true) {
        val current = get(period) ?: return

        val updatedCategories = current.categories.toMutableList()
        val existingIndex = updatedCategories.indexOfFirst { it.name == category }

        if (existingIndex < 0) return

        val existing = updatedCategories[existingIndex]
        val newAmount = existing.amount - amount

        if (newAmount <= 0) {
            updatedCategories.removeAt(existingIndex)
        } else {
            updatedCategories[existingIndex] = existing.copy(amount = newAmount)
        }

        val newCreditCardTotal = if (isCreditCard) {
            current.creditCardTotal?.let { maxOf(0, it - amount) }
        } else {
            current.creditCardTotal
        }

        val updated = recalculatePercentages(
            PieChartData(categories = updatedCategories, totalAmount = current.totalAmount, creditCardTotal = newCreditCardTotal)
        )
        put(period, updated)
    }

    /**
     * 全期間のキャッシュをクリアする。
     * プルトゥリフレッシュ時に呼び出される。
     */
    suspend fun clearAll() {
        settingsRepository.clearAllPieChartCache()
    }

    /**
     * 各カテゴリのパーセンテージと合計金額を再計算する。
     *
     * @param data 再計算対象のデータ
     * @return パーセンテージと合計金額が更新されたデータ
     */
    private fun recalculatePercentages(data: PieChartData): PieChartData {
        val totalAmount = data.categories.sumOf { it.amount }
        val updatedCategories = if (totalAmount > 0) {
            data.categories.map { cat ->
                cat.copy(percentage = (cat.amount.toFloat() / totalAmount.toFloat()) * 100f)
            }
        } else {
            data.categories.map { cat -> cat.copy(percentage = 0f) }
        }
        return PieChartData(categories = updatedCategories, totalAmount = totalAmount, creditCardTotal = data.creditCardTotal)
    }
}
