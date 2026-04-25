package com.example.spendmgr.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import com.example.spendmgr.data.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

class SummaryCacheTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var cache: SummaryCache

    @BeforeEach
    fun setUp() {
        settingsRepository = mockk(relaxed = true)
        // DataStore から null を返すようにモック（init ブロックの非同期ロードが値を上書きしないように）
        coEvery { settingsRepository.getYearlyTotal() } returns null
        coEvery { settingsRepository.getMonthlyTotal() } returns null
        cache = SummaryCache(settingsRepository)
    }

    @Test
    fun `initial state is null`() = runTest {
        assertNull(cache.yearlyTotal.value)
        assertNull(cache.monthlyTotal.value)
    }

    @Test
    fun `update sets yearly and monthly totals`() = runTest {
        cache.update(100000, 10000)
        assertEquals(100000, cache.yearlyTotal.value)
        assertEquals(10000, cache.monthlyTotal.value)
    }

    @Test
    fun `update with null clears totals`() = runTest {
        cache.update(100000, 10000)
        cache.update(null, null)
        assertNull(cache.yearlyTotal.value)
        assertNull(cache.monthlyTotal.value)
    }

    @Test
    fun `adjust adds delta to both totals`() = runTest {
        cache.update(100000, 10000)
        cache.adjust(1500)
        assertEquals(101500, cache.yearlyTotal.value)
        assertEquals(11500, cache.monthlyTotal.value)
    }

    @Test
    fun `adjust with negative delta subtracts from both totals`() = runTest {
        cache.update(100000, 10000)
        cache.adjust(-1500)
        assertEquals(98500, cache.yearlyTotal.value)
        assertEquals(8500, cache.monthlyTotal.value)
    }

    @Test
    fun `adjust then reverse returns to original value`() = runTest {
        cache.update(100000, 10000)
        cache.adjust(1500)
        cache.adjust(-1500)
        assertEquals(100000, cache.yearlyTotal.value)
        assertEquals(10000, cache.monthlyTotal.value)
    }

    @Test
    fun `adjust does nothing when cache is null`() = runTest {
        cache.adjust(1500)
        assertNull(cache.yearlyTotal.value)
        assertNull(cache.monthlyTotal.value)
    }

    @Test
    fun `clear resets totals to null`() = runTest {
        cache.update(100000, 10000)
        cache.clear()
        assertNull(cache.yearlyTotal.value)
        assertNull(cache.monthlyTotal.value)
    }
}
