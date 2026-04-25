package com.example.spendmgr.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        private val KEY_FOLDER_ID = stringPreferencesKey("folder_id")
        private val KEY_IS_AUTHENTICATED = booleanPreferencesKey("is_authenticated")
        private val KEY_ALLOWANCE_AMOUNT = intPreferencesKey("allowance_amount")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        private val KEY_YEARLY_TOTAL = intPreferencesKey("yearly_total")
        private val KEY_MONTHLY_TOTAL = intPreferencesKey("monthly_total")
        private const val ALLOWANCE_NULL_SENTINEL = -1
        private const val TOTAL_NULL_SENTINEL = Int.MIN_VALUE

        private fun spreadsheetKey(year: Int) = stringPreferencesKey("spreadsheet_id_$year")
    }

    override suspend fun getFolderId(): String? {
        return dataStore.data.first()[KEY_FOLDER_ID]
    }

    override suspend fun saveFolderId(id: String) {
        dataStore.edit { prefs ->
            prefs[KEY_FOLDER_ID] = id
        }
    }

    override suspend fun getSpreadsheetId(year: Int): String? {
        return dataStore.data.first()[spreadsheetKey(year)]
    }

    override suspend fun saveSpreadsheetId(year: Int, id: String) {
        dataStore.edit { prefs ->
            prefs[spreadsheetKey(year)] = id
        }
    }

    override suspend fun isAuthenticated(): Boolean {
        return dataStore.data.first()[KEY_IS_AUTHENTICATED] ?: false
    }

    override suspend fun saveAuthenticated(authenticated: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_AUTHENTICATED] = authenticated
        }
    }

    override suspend fun getAllowanceAmount(): Int? {
        val stored = dataStore.data.first()[KEY_ALLOWANCE_AMOUNT]
        return if (stored == null || stored == ALLOWANCE_NULL_SENTINEL) null else stored
    }

    override suspend fun saveAllowanceAmount(amount: Int?) {
        dataStore.edit { prefs ->
            prefs[KEY_ALLOWANCE_AMOUNT] = amount ?: ALLOWANCE_NULL_SENTINEL
        }
    }

    // ─── OAuth認証トークン ────────────────────────────────────────────────────

    /**
     * 保存されたOAuth認証トークン（Google IDトークン）を取得する。
     * 未保存または空文字列の場合はnullを返す。
     */
    override suspend fun getAuthToken(): String? {
        val token = dataStore.data.first()[KEY_AUTH_TOKEN]
        return if (token.isNullOrEmpty()) null else token
    }

    /**
     * OAuth認証トークン（Google IDトークン）をDataStoreに保存する。
     * 空文字列を渡すとトークンをクリアする。
     */
    override suspend fun saveAuthToken(token: String) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = token
        }
    }

    /**
     * 認証状態を保存する（saveAuthenticatedの別名）。
     */
    override suspend fun saveIsAuthenticated(isAuthenticated: Boolean) {
        saveAuthenticated(isAuthenticated)
    }

    override suspend fun saveAccountEmail(email: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCOUNT_EMAIL] = email
        }
    }

    override suspend fun getAccountEmail(): String? {
        val email = dataStore.data.first()[KEY_ACCOUNT_EMAIL]
        return if (email.isNullOrEmpty()) null else email
    }

    // ─── 合計額キャッシュ ─────────────────────────────────────────────────────

    override suspend fun saveYearlyTotal(total: Int?) {
        dataStore.edit { prefs ->
            prefs[KEY_YEARLY_TOTAL] = total ?: TOTAL_NULL_SENTINEL
        }
    }

    override suspend fun getYearlyTotal(): Int? {
        val stored = dataStore.data.first()[KEY_YEARLY_TOTAL]
        return if (stored == null || stored == TOTAL_NULL_SENTINEL) null else stored
    }

    override suspend fun saveMonthlyTotal(total: Int?) {
        dataStore.edit { prefs ->
            prefs[KEY_MONTHLY_TOTAL] = total ?: TOTAL_NULL_SENTINEL
        }
    }

    override suspend fun getMonthlyTotal(): Int? {
        val stored = dataStore.data.first()[KEY_MONTHLY_TOTAL]
        return if (stored == null || stored == TOTAL_NULL_SENTINEL) null else stored
    }

    // ─── 円グラフキャッシュ ───────────────────────────────────────────────────

    override suspend fun savePieChartCache(periodKey: String, data: String) {
        val key = stringPreferencesKey("pie_chart_cache_$periodKey")
        dataStore.edit { prefs ->
            prefs[key] = data
        }
    }

    override suspend fun getPieChartCache(periodKey: String): String? {
        val key = stringPreferencesKey("pie_chart_cache_$periodKey")
        return dataStore.data.first()[key]
    }

    override suspend fun clearAllPieChartCache() {
        dataStore.edit { prefs ->
            val keysToRemove = prefs.asMap().keys.filter { it.name.startsWith("pie_chart_cache_") }
            keysToRemove.forEach { prefs.remove(it) }
        }
    }

    // ─── カード払い合計キャッシュ ─────────────────────────────────────────────

    override suspend fun saveCreditCardTotal(periodKey: String, total: Int?) {
        val key = intPreferencesKey("credit_card_total_$periodKey")
        dataStore.edit { prefs ->
            prefs[key] = total ?: TOTAL_NULL_SENTINEL
        }
    }

    override suspend fun getCreditCardTotal(periodKey: String): Int? {
        val key = intPreferencesKey("credit_card_total_$periodKey")
        val stored = dataStore.data.first()[key]
        return if (stored == null || stored == TOTAL_NULL_SENTINEL) null else stored
    }
}
