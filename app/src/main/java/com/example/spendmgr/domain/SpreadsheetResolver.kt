package com.example.spendmgr.domain

import com.example.spendmgr.data.GoogleDriveRepository
import com.example.spendmgr.data.GoogleSheetsRepository
import com.example.spendmgr.data.SettingsRepository
import com.example.spendmgr.domain.model.SpreadsheetTarget
import java.time.LocalDate

/**
 * 経費記録の日付に基づいて、対応するスプレッドシートIDとシート名を解決するドメインサービス。
 * SpendMgrフォルダや年別スプレッドシートが存在しない場合は自動作成する。
 *
 * Requirements: 5.1, 8.1, 8.2, 8.3, 9.1, 9.2, 9.6, 11.2
 */
class SpreadsheetResolver(
    private val googleDriveRepository: GoogleDriveRepository,
    private val googleSheetsRepository: GoogleSheetsRepository,
    private val settingsRepository: SettingsRepository
) {

    companion object {
        private const val SPENDMGR_FOLDER_NAME = "SpendMgr"
        private const val SPREADSHEET_BASE_URL = "https://docs.google.com/spreadsheets/d/"
    }

    /**
     * 指定された日付に対応するスプレッドシートIDとシート名を返す。
     * SpendMgrフォルダや年別スプレッドシートが存在しない場合は自動作成する。
     *
     * @param date 経費の日付
     * @return SpreadsheetTarget（spreadsheetId と sheetName を含む）
     */
    suspend fun resolve(date: LocalDate): SpreadsheetTarget {
        val folderId = ensureFolder()
        val year = date.year
        val spreadsheetId = ensureSpreadsheet(folderId, year)
        val sheetName = "${date.monthValue}月"
        return SpreadsheetTarget(spreadsheetId = spreadsheetId, sheetName = sheetName)
    }

    /**
     * SpendMgrフォルダの存在確認・作成を行い、フォルダIDを返す。
     * 既存フォルダがある場合はそのIDを返す（冪等性を保証）。
     *
     * Requirements: 8.1, 8.2, 8.3
     *
     * @return SpendMgrフォルダのID
     */
    suspend fun ensureFolder(): String {
        // まずキャッシュ済みのフォルダIDを確認
        val cachedFolderId = settingsRepository.getFolderId()
        if (cachedFolderId != null) return cachedFolderId

        // Drive上で検索
        val existingFolderId = googleDriveRepository.findFolder(SPENDMGR_FOLDER_NAME)
        if (existingFolderId != null) {
            settingsRepository.saveFolderId(existingFolderId)
            return existingFolderId
        }

        // 存在しない場合は新規作成
        val newFolderId = googleDriveRepository.createFolder(SPENDMGR_FOLDER_NAME)
        settingsRepository.saveFolderId(newFolderId)
        return newFolderId
    }

    /**
     * 現在の年に対応するYearly_SpreadsheetのURLを返す。
     * スプレッドシートが存在しない場合はnullを返す。
     *
     * Requirements: 11.2
     *
     * @return スプレッドシートのURL、または存在しない場合はnull
     */
    suspend fun getCurrentYearSpreadsheetUrl(): String? {
        val year = LocalDate.now().year
        val spreadsheetId = settingsRepository.getSpreadsheetId(year)
            ?: run {
                // キャッシュにない場合はDriveで検索
                val folderId = settingsRepository.getFolderId() ?: return null
                googleDriveRepository.findSpreadsheet(folderId, year.toString())
                    ?.also { id -> settingsRepository.saveSpreadsheetId(year, id) }
            }
        return spreadsheetId?.let { "$SPREADSHEET_BASE_URL$it" }
    }

    /**
     * 指定年のスプレッドシートの存在確認・作成を行い、スプレッドシートIDを返す。
     * 既存スプレッドシートがある場合はそのIDを返す（冪等性を保証）。
     *
     * Requirements: 9.1, 9.2, 9.6
     *
     * @param folderId SpendMgrフォルダのID
     * @param year 対象年
     * @return スプレッドシートID
     */
    private suspend fun ensureSpreadsheet(folderId: String, year: Int): String {
        // まずキャッシュ済みのスプレッドシートIDを確認
        val cachedId = settingsRepository.getSpreadsheetId(year)
        if (cachedId != null) return cachedId

        // Drive上で検索
        val existingId = googleDriveRepository.findSpreadsheet(folderId, year.toString())
        if (existingId != null) {
            settingsRepository.saveSpreadsheetId(year, existingId)
            // 既存スプレッドシートのまとめシートにSUM関数を再設定する
            // （初回作成時に失敗した場合や、古いバージョンのスプレッドシートに対応）
            try {
                googleSheetsRepository.setupSummarySheet(existingId)
            } catch (e: Exception) {
                // SUM関数の設定失敗は致命的ではないので無視する
            }
            return existingId
        }

        // 存在しない場合は新規作成
        val newId = googleSheetsRepository.createYearlySpreadsheet(
            name = year.toString(),
            folderId = folderId
        ).getOrThrow()

        // まとめシートにSUM関数を設定
        googleSheetsRepository.setupSummarySheet(newId).getOrThrow()

        // Drive APIでスプレッドシートをSpendMgrフォルダに移動する (Req 8.1, 9.2)
        // Sheets APIで作成されたスプレッドシートはrootに作成されるため、
        // Drive APIを使用してSpendMgrフォルダに移動する。
        googleDriveRepository.moveFile(newId, folderId)

        settingsRepository.saveSpreadsheetId(year, newId)
        return newId
    }
}
