package com.example.spendmgr.domain

import com.example.spendmgr.data.GoogleSheetsRepository
import com.example.spendmgr.data.SettingsRepository
import com.example.spendmgr.domain.model.SummaryResult
import java.time.LocalDate

/**
 * 給料日サイクル（毎月25日〜翌月24日）の合計額を取得するドメインサービス。
 * アプリ起動時とプルトゥリフレッシュ時のみスプレッドシートからフェッチし、SummaryCacheに保存する。
 *
 * 取得失敗時はキャッシュをそのまま保持する（DataStoreの値を上書きしない）。
 * これによりアプリkill後の再起動時も前回の値が表示される。
 *
 * Requirements: 13.1, 13.2, 13.5, 13.6, 13.7, 13.8, 13.9
 */
class SummaryFetcher(
    private val googleSheetsRepository: GoogleSheetsRepository,
    private val spreadsheetResolver: SpreadsheetResolver,
    private val settingsRepository: SettingsRepository,
    private val summaryCache: SummaryCache
) {

    suspend fun fetchSummary(): SummaryResult {
        // 認証チェック (Req 13.9) — 未認証時はキャッシュを保持したままnullを返す
        if (!settingsRepository.isAuthenticated()) {
            return SummaryResult(yearlyTotal = null, monthlyTotal = null)
        }

        val today = LocalDate.now()
        val year = today.year

        // スプレッドシートIDの確認
        val spreadsheetId = settingsRepository.getSpreadsheetId(year)
            ?: run {
                try {
                    settingsRepository.getFolderId()
                        ?: spreadsheetResolver.ensureFolder()
                    spreadsheetResolver.getCurrentYearSpreadsheetUrl()
                        ?: return SummaryResult(yearlyTotal = null, monthlyTotal = null)
                    settingsRepository.getSpreadsheetId(year)
                        ?: return SummaryResult(yearlyTotal = null, monthlyTotal = null)
                } catch (e: Exception) {
                    return SummaryResult(yearlyTotal = null, monthlyTotal = null)
                }
            }

        val yearlyTotalResult = googleSheetsRepository.fetchYearlyTotal(spreadsheetId)
        if (yearlyTotalResult.isFailure) {
            return SummaryResult(yearlyTotal = null, monthlyTotal = null)
        }

        // 給料日サイクル集計: 25日以降なら今月25日〜翌月24日、24日以前なら前月25日〜今月24日
        val monthlyTotal = fetchSalaryCycleTotal(today, spreadsheetId)
            ?: return SummaryResult(yearlyTotal = null, monthlyTotal = null)

        val yearlyTotal = yearlyTotalResult.getOrNull()

        // 取得成功時のみキャッシュを更新・永続化する
        summaryCache.update(yearlyTotal, monthlyTotal)

        return SummaryResult(yearlyTotal = yearlyTotal, monthlyTotal = monthlyTotal)
    }

    /**
     * 給料日サイクル（25日〜翌月24日）の合計額を計算する。
     * 今日が25日以降: 今月25日〜翌月24日
     * 今日が24日以前: 前月25日〜今月24日
     *
     * スプレッドシートのA列は "M/d" 形式（例: "4/25"）で保存されている。
     * 年をまたぐ場合（12月25日〜1月24日）は前年スプレッドシートも参照する。
     */
    private suspend fun fetchSalaryCycleTotal(today: LocalDate, currentSpreadsheetId: String): Int? {
        // サイクルの開始・終了日を決定
        val cycleStart: LocalDate
        val cycleEnd: LocalDate
        if (today.dayOfMonth >= 25) {
            cycleStart = today.withDayOfMonth(25)
            cycleEnd = today.plusMonths(1).withDayOfMonth(24)
        } else {
            cycleStart = today.minusMonths(1).withDayOfMonth(25)
            cycleEnd = today.withDayOfMonth(24)
        }

        var total = 0

        // cycleStart と cycleEnd が同じ年かどうかで処理を分岐
        if (cycleStart.year == cycleEnd.year) {
            // 同一年内: cycleStart.month と cycleEnd.month の2シートを参照
            val months = if (cycleStart.monthValue == cycleEnd.monthValue) {
                listOf(cycleStart.monthValue)
            } else {
                listOf(cycleStart.monthValue, cycleEnd.monthValue)
            }
            for (month in months) {
                val sheetName = "${month}月"
                val rows = googleSheetsRepository.fetchRawExpenses(currentSpreadsheetId, sheetName)
                    .getOrNull() ?: return null
                for ((dateStr, amount, splitCount) in rows) {
                    val date = parseDateInYear(dateStr, cycleStart.year) ?: continue
                    if (!date.isBefore(cycleStart) && !date.isAfter(cycleEnd)) {
                        total += amount / splitCount
                    }
                }
            }
        } else {
            // 年をまたぐ: cycleStart は前年12月、cycleEnd は今年1月
            // 前年スプレッドシートから12月分
            val prevYear = cycleStart.year
            val prevSpreadsheetId = settingsRepository.getSpreadsheetId(prevYear)
            if (prevSpreadsheetId != null) {
                val rows = googleSheetsRepository.fetchRawExpenses(prevSpreadsheetId, "12月")
                    .getOrNull() ?: return null
                for ((dateStr, amount, splitCount) in rows) {
                    val date = parseDateInYear(dateStr, prevYear) ?: continue
                    if (!date.isBefore(cycleStart) && !date.isAfter(cycleEnd)) {
                        total += amount / splitCount
                    }
                }
            }
            // 今年スプレッドシートから1月分
            val rows = googleSheetsRepository.fetchRawExpenses(currentSpreadsheetId, "1月")
                .getOrNull() ?: return null
            for ((dateStr, amount, splitCount) in rows) {
                val date = parseDateInYear(dateStr, cycleEnd.year) ?: continue
                if (!date.isBefore(cycleStart) && !date.isAfter(cycleEnd)) {
                    total += amount / splitCount
                }
            }
        }

        return total
    }

    /**
     * "M/d" 形式の日付文字列を指定年の LocalDate に変換する。
     * 例: "4/25", year=2026 → LocalDate(2026, 4, 25)
     */
    private fun parseDateInYear(dateStr: String, year: Int): LocalDate? {
        return try {
            val parts = dateStr.split("/")
            if (parts.size != 2) return null
            val month = parts[0].toInt()
            val day = parts[1].toInt()
            LocalDate.of(year, month, day)
        } catch (e: Exception) {
            null
        }
    }
}
