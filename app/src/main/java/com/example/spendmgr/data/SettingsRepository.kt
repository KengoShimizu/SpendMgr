package com.example.spendmgr.data

interface SettingsRepository {
    suspend fun getFolderId(): String?
    suspend fun saveFolderId(id: String)
    suspend fun getSpreadsheetId(year: Int): String?
    suspend fun saveSpreadsheetId(year: Int, id: String)
    suspend fun isAuthenticated(): Boolean
    suspend fun saveAuthenticated(authenticated: Boolean)
    suspend fun getAllowanceAmount(): Int?
    suspend fun saveAllowanceAmount(amount: Int?)

    // ─── OAuth認証トークン ────────────────────────────────────────────────────

    /**
     * 保存されたOAuth認証トークン（Google IDトークン）を取得する。
     * @return 認証トークン文字列。未保存または空の場合はnull
     */
    suspend fun getAuthToken(): String?

    /**
     * OAuth認証トークン（Google IDトークン）を保存する。
     * @param token 保存するトークン文字列（空文字列でクリア）
     */
    suspend fun saveAuthToken(token: String)

    /**
     * 認証状態を保存する（saveAuthenticatedの別名）。
     * @param isAuthenticated 認証済みの場合はtrue
     */
    suspend fun saveIsAuthenticated(isAuthenticated: Boolean)

    /**
     * サインインしたGoogleアカウントのメールアドレスを保存する。
     */
    suspend fun saveAccountEmail(email: String)

    /**
     * 保存されたGoogleアカウントのメールアドレスを取得する。
     */
    suspend fun getAccountEmail(): String?

    // ─── 合計額キャッシュ ─────────────────────────────────────────────────────

    /**
     * 今年の合計額をローカルに保存する。
     */
    suspend fun saveYearlyTotal(total: Int?)

    /**
     * 保存された今年の合計額を取得する。
     */
    suspend fun getYearlyTotal(): Int?

    /**
     * 今月の合計額をローカルに保存する。
     */
    suspend fun saveMonthlyTotal(total: Int?)

    /**
     * 保存された今月の合計額を取得する。
     */
    suspend fun getMonthlyTotal(): Int?

    /**
     * 現在選択中の期間のカード払い合計額をローカルに保存する。
     * periodKey: "MONTHLY_yyyy_M" または "YEARLY_yyyy"
     */
    suspend fun saveCreditCardTotal(periodKey: String, total: Int?)

    /**
     * 保存されたカード払い合計額を取得する。
     */
    suspend fun getCreditCardTotal(periodKey: String): Int?

    // ─── 円グラフキャッシュ ───────────────────────────────────────────────────

    /**
     * 円グラフキャッシュを保存する。
     * JSON 形式でシリアライズして DataStore に保存する。
     *
     * @param periodKey 期間のキー（例: "MONTHLY", "YEARLY", "PAST_YEAR_2024"）
     * @param data JSON 文字列
     */
    suspend fun savePieChartCache(periodKey: String, data: String)

    /**
     * 円グラフキャッシュを取得する。
     *
     * @param periodKey 期間のキー
     * @return JSON 文字列。未保存の場合はnull
     */
    suspend fun getPieChartCache(periodKey: String): String?

    /**
     * 全円グラフキャッシュをクリアする。
     */
    suspend fun clearAllPieChartCache()
}
