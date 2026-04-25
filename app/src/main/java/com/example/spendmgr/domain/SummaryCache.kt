package com.example.spendmgr.domain

import com.example.spendmgr.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 合計額のローカルキャッシュを管理する。
 *
 * - DataStore に永続化するため、アプリkill後も値が保持される
 * - 起動時はDataStoreから即座に値を復元して表示
 * - 記録成功後・取り消し成功時はローカルで加減算し、API呼び出しを最小化する
 * - アプリ初回起動またはkill後の起動時はスプレッドシートから再取得する
 *
 * Requirements: 13.3, 13.4
 */
class SummaryCache(
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _yearlyTotal = MutableStateFlow<Int?>(null)
    private val _monthlyTotal = MutableStateFlow<Int?>(null)

    val yearlyTotal: StateFlow<Int?> get() = _yearlyTotal
    val monthlyTotal: StateFlow<Int?> get() = _monthlyTotal

    init {
        // DataStore から保存済みの値を即座に復元する
        scope.launch {
            _yearlyTotal.value = settingsRepository.getYearlyTotal()
            _monthlyTotal.value = settingsRepository.getMonthlyTotal()
        }
    }

    /**
     * スプレッドシートから取得した合計額でキャッシュを更新し、DataStoreに永続化する。
     * アプリ起動時とプルトゥリフレッシュ時に呼び出される。
     */
    fun update(yearlyTotal: Int?, monthlyTotal: Int?) {
        _yearlyTotal.value = yearlyTotal
        _monthlyTotal.value = monthlyTotal
        // DataStore に永続化する
        scope.launch {
            settingsRepository.saveYearlyTotal(yearlyTotal)
            settingsRepository.saveMonthlyTotal(monthlyTotal)
        }
    }

    /**
     * 新規記録または取り消し時にキャッシュを加減算し、DataStoreに永続化する。
     * 正の値で加算（記録成功時）、負の値で減算（取り消し成功時）。
     * adjustYearly: 今年の合計を更新するか
     * adjustMonthly: 今月の合計を更新するか
     * キャッシュがnullの場合は何もしない。
     */
    fun adjust(delta: Int, adjustYearly: Boolean = true, adjustMonthly: Boolean = true) {
        val newYearly = if (adjustYearly) _yearlyTotal.value?.let { it + delta } else _yearlyTotal.value
        val newMonthly = if (adjustMonthly) _monthlyTotal.value?.let { it + delta } else _monthlyTotal.value
        _yearlyTotal.value = newYearly
        _monthlyTotal.value = newMonthly
        // DataStore に永続化する
        scope.launch {
            settingsRepository.saveYearlyTotal(newYearly)
            settingsRepository.saveMonthlyTotal(newMonthly)
        }
    }

    /**
     * キャッシュをクリアする（nullにリセット）。
     */
    fun clear() {
        _yearlyTotal.value = null
        _monthlyTotal.value = null
        scope.launch {
            settingsRepository.saveYearlyTotal(null)
            settingsRepository.saveMonthlyTotal(null)
        }
    }
}
