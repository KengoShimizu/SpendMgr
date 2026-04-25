package com.example.spendmgr.domain

import com.example.spendmgr.data.GoogleSheetsRepository
import com.example.spendmgr.data.SettingsRepository
import com.example.spendmgr.domain.model.SummaryResult
import java.time.LocalDate

/**
 * Summary_SheetおよびMonthly_Sheetから合計額を取得するドメインサービス。
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

        val currentMonthSheetName = "${today.monthValue}月"

        val yearlyTotalResult = googleSheetsRepository.fetchYearlyTotal(spreadsheetId)
        if (yearlyTotalResult.isFailure) {
            // 失敗時はキャッシュを保持（DataStoreを上書きしない）
            return SummaryResult(yearlyTotal = null, monthlyTotal = null)
        }

        val monthlyTotalResult = googleSheetsRepository.fetchMonthlyTotal(
            spreadsheetId = spreadsheetId,
            sheetName = currentMonthSheetName
        )
        if (monthlyTotalResult.isFailure) {
            return SummaryResult(yearlyTotal = null, monthlyTotal = null)
        }

        val yearlyTotal = yearlyTotalResult.getOrNull()
        val monthlyTotal = monthlyTotalResult.getOrNull()

        // 取得成功時のみキャッシュを更新・永続化する
        summaryCache.update(yearlyTotal, monthlyTotal)

        return SummaryResult(yearlyTotal = yearlyTotal, monthlyTotal = monthlyTotal)
    }
}
