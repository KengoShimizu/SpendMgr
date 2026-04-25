package com.example.spendmgr.domain

import com.example.spendmgr.data.GoogleDriveRepository
import com.example.spendmgr.data.GoogleSheetsRepository
import com.example.spendmgr.data.SettingsRepository
import com.example.spendmgr.domain.model.CategoryData
import com.example.spendmgr.domain.model.ChartPeriod
import com.example.spendmgr.domain.model.PieChartData
import java.time.LocalDate

/**
 * Google スプレッドシートから指定期間のカテゴリ別経費データを取得し、集計するドメインサービス。
 * キャッシュファーストの戦略を採用し、キャッシュが存在する場合は即座に返す。
 */
class PieChartFetcher(
    private val googleSheetsRepository: GoogleSheetsRepository,
    private val googleDriveRepository: GoogleDriveRepository,
    private val settingsRepository: SettingsRepository,
    private val pieChartCache: PieChartCache
) {

    /**
     * 指定期間のカテゴリ別経費データを取得する。
     * キャッシュが存在する場合は即座に返す。
     * キャッシュが存在しない場合はスプレッドシートから取得し、キャッシュに保存して返す。
     *
     * @param period 取得する期間（Monthly, Yearly, PastYear）
     * @return カテゴリ別経費データ。未認証またはスプレッドシート未存在の場合はnull
     */
    suspend fun fetchPieChartData(period: ChartPeriod): Result<PieChartData?> {
        // キャッシュから即座に返す
        val cached = pieChartCache.get(period)
        if (cached != null) {
            return Result.success(cached)
        }

        // 認証チェック
        if (!settingsRepository.isAuthenticated()) {
            return Result.success(null)
        }

        // スプレッドシートIDの解決
        val spreadsheetId = resolveSpreadsheetId(period)
            ?: return Result.success(null)
        // スプレッドシートからデータを取得して集計
        return try {
            val categoryMap = when (period) {
                is ChartPeriod.Monthly -> {
                    val sheetName = "${period.month}月"
                    aggregateMonthlyData(spreadsheetId, sheetName)
                }
                is ChartPeriod.Yearly -> {
                    aggregateYearlyData(spreadsheetId)
                }
            }

            if (categoryMap.isEmpty()) {
                return Result.success(null)
            }

            // カード払い合計を取得
            val creditCardTotal = fetchCreditCardTotalForPeriod(spreadsheetId, period)

            val pieChartData = buildPieChartData(categoryMap, creditCardTotal)
            pieChartCache.put(period, pieChartData)
            Result.success(pieChartData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Google Drive の SpendMgr フォルダ内に存在する年別スプレッドシートの年のリストを取得する。
     * 現在の年は除外される。
     *
     * @return 過去年のリスト（降順）。取得失敗時は空リスト
     */
    suspend fun fetchAvailableYears(): Result<List<Int>> {
        // キャッシュ済みフォルダIDを確認、なければDriveで検索
        val cachedFolderId = settingsRepository.getFolderId()
        android.util.Log.d("PieChartFetcher", "fetchAvailableYears: cachedFolderId=$cachedFolderId")
        val folderId = cachedFolderId
            ?: googleDriveRepository.findFolder("SpendMgr")
                .also { android.util.Log.d("PieChartFetcher", "fetchAvailableYears: findFolder result=$it") }
            ?: return Result.success(emptyList())

        val namesResult = googleDriveRepository.listSpreadsheetNames(folderId)
        android.util.Log.d("PieChartFetcher", "fetchAvailableYears: namesResult=$namesResult")
        if (namesResult.isFailure) {
            return Result.success(emptyList())
        }

        val currentYear = LocalDate.now().year
        val years = namesResult.getOrDefault(emptyList())
            .mapNotNull { name -> name.toIntOrNull() }
            .let { list ->
                // 現在の年を含む全年リスト（降順）
                if (list.contains(currentYear)) list.sortedDescending()
                else (list + currentYear).sortedDescending()
            }

        android.util.Log.d("PieChartFetcher", "fetchAvailableYears: years=$years")
        return Result.success(years)
    }

    /**
     * 指定期間のカード払い合計を取得する。
     */
    private suspend fun fetchCreditCardTotalForPeriod(spreadsheetId: String, period: ChartPeriod): Int? {
        return when (period) {
            is ChartPeriod.Monthly -> {
                val sheetName = "${period.month}月"
                googleSheetsRepository.fetchCreditCardTotal(spreadsheetId, sheetName).getOrNull()
            }
            is ChartPeriod.Yearly -> {
                // 全月のカード払いを合算
                var total = 0
                for (month in 1..12) {
                    val sheetName = "${month}月"
                    val result = googleSheetsRepository.fetchCreditCardTotal(spreadsheetId, sheetName)
                    total += result.getOrNull() ?: 0
                }
                total
            }
        }
    }

    /**
     * 指定年の全月シートからカテゴリ別経費データを集計する。
     * 失敗した月はスキップする。
     *
     * @param spreadsheetId スプレッドシートID
     * @return カテゴリ名をキー、合計金額を値とするマップ
     */
    private suspend fun aggregateYearlyData(spreadsheetId: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for (month in 1..12) {
            val sheetName = "${month}月"
            val monthResult = googleSheetsRepository.fetchCategoryAmounts(spreadsheetId, sheetName)
            if (monthResult.isSuccess) {
                val pairs = monthResult.getOrDefault(emptyList())
                for ((category, amount) in pairs) {
                    result[category] = (result[category] ?: 0) + amount
                }
            }
            // 失敗した月はスキップ
        }
        return result
    }

    /**
     * 指定月シートからカテゴリ別経費データを集計する。
     *
     * @param spreadsheetId スプレッドシートID
     * @param sheetName シート名（例: "4月"）
     * @return カテゴリ名をキー、合計金額を値とするマップ
     */
    private suspend fun aggregateMonthlyData(spreadsheetId: String, sheetName: String): Map<String, Int> {
        val fetchResult = googleSheetsRepository.fetchCategoryAmounts(spreadsheetId, sheetName)
        if (fetchResult.isFailure) return emptyMap()

        val result = mutableMapOf<String, Int>()
        for ((category, amount) in fetchResult.getOrDefault(emptyList())) {
            result[category] = (result[category] ?: 0) + amount
        }
        return result
    }

    /**
     * カテゴリ別金額マップから PieChartData を構築する。
     * 各カテゴリのパーセンテージを計算し、金額降順でソートする。
     *
     * @param categoryMap カテゴリ名をキー、合計金額を値とするマップ
     * @return 構築された PieChartData
     */
    private fun buildPieChartData(categoryMap: Map<String, Int>, creditCardTotal: Int? = null): PieChartData {
        val totalAmount = categoryMap.values.sum()
        val categories = categoryMap.entries
            .map { (name, amount) ->
                val percentage = if (totalAmount > 0) {
                    (amount.toFloat() / totalAmount.toFloat()) * 100f
                } else {
                    0f
                }
                CategoryData(
                    name = name,
                    amount = amount,
                    percentage = percentage,
                    color = CategoryColorAssigner.colorFor(name)
                )
            }
            .sortedByDescending { it.amount }

        return PieChartData(categories = categories, totalAmount = totalAmount, creditCardTotal = creditCardTotal)
    }

    /**
     * 指定期間に対応するスプレッドシートIDを解決する。
     *
     * @param period 期間
     * @return スプレッドシートID。存在しない場合はnull
     */
    private suspend fun resolveSpreadsheetId(period: ChartPeriod): String? {
        val year = when (period) {
            is ChartPeriod.Monthly -> period.year
            is ChartPeriod.Yearly -> period.year
        }
        // キャッシュ済みIDを確認
        settingsRepository.getSpreadsheetId(year)?.let { return it }

        // キャッシュにない場合はDriveで検索（再インストール後対応）
        val folderId = settingsRepository.getFolderId()
            ?: googleDriveRepository.findFolder("SpendMgr")
                ?.also { settingsRepository.saveFolderId(it) }
            ?: return null

        return googleDriveRepository.findSpreadsheet(folderId, year.toString())
            ?.also { id -> settingsRepository.saveSpreadsheetId(year, id) }
    }
}
