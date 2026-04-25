package com.example.spendmgr.domain

import com.example.spendmgr.data.GoogleSheetsRepository
import com.example.spendmgr.data.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SummaryFetcherTest {

    private lateinit var sheetsRepository: GoogleSheetsRepository
    private lateinit var spreadsheetResolver: SpreadsheetResolver
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var summaryCache: SummaryCache
    private lateinit var fetcher: SummaryFetcher

    @BeforeEach
    fun setUp() {
        sheetsRepository = mockk()
        spreadsheetResolver = mockk()
        settingsRepository = mockk()
        summaryCache = SummaryCache(mockk(relaxed = true))
        fetcher = SummaryFetcher(sheetsRepository, spreadsheetResolver, settingsRepository, summaryCache)
    }

    @Test
    fun `fetchSummary returns null totals when not authenticated`() = runTest {
        coEvery { settingsRepository.isAuthenticated() } returns false

        val result = fetcher.fetchSummary()

        assertNull(result.yearlyTotal)
        assertNull(result.monthlyTotal)
        assertNull(summaryCache.yearlyTotal.value)
        assertNull(summaryCache.monthlyTotal.value)
        coVerify(exactly = 0) { sheetsRepository.fetchYearlyTotal(any()) }
    }

    @Test
    fun `fetchSummary returns null totals when folder not set`() = runTest {
        coEvery { settingsRepository.isAuthenticated() } returns true
        coEvery { settingsRepository.getSpreadsheetId(any()) } returns null
        coEvery { settingsRepository.getFolderId() } returns null

        val result = fetcher.fetchSummary()

        assertNull(result.yearlyTotal)
        assertNull(result.monthlyTotal)
    }

    @Test
    fun `fetchSummary returns null totals when spreadsheet not found on Drive`() = runTest {
        val year = LocalDate.now().year
        coEvery { settingsRepository.isAuthenticated() } returns true
        coEvery { settingsRepository.getSpreadsheetId(year) } returns null
        coEvery { settingsRepository.getFolderId() } returns "folder-id"
        coEvery { spreadsheetResolver.getCurrentYearSpreadsheetUrl() } returns null

        val result = fetcher.fetchSummary()

        assertNull(result.yearlyTotal)
        assertNull(result.monthlyTotal)
    }

    @Test
    fun `fetchSummary resolves spreadsheet ID via Drive when not cached`() = runTest {
        val year = LocalDate.now().year
        val month = LocalDate.now().monthValue
        val sheetName = "${month}月"
        // 1回目はnull、getCurrentYearSpreadsheetUrl()後の2回目はIDを返す
        coEvery { settingsRepository.isAuthenticated() } returns true
        coEvery { settingsRepository.getSpreadsheetId(year) } returnsMany listOf(null, "sheet-id")
        coEvery { settingsRepository.getFolderId() } returns "folder-id"
        coEvery { spreadsheetResolver.getCurrentYearSpreadsheetUrl() } returns "https://docs.google.com/spreadsheets/d/sheet-id"
        coEvery { sheetsRepository.fetchYearlyTotal("sheet-id") } returns Result.success(100000)
        coEvery { sheetsRepository.fetchMonthlyTotal("sheet-id", sheetName) } returns Result.success(10000)

        val result = fetcher.fetchSummary()

        assertEquals(100000, result.yearlyTotal)
        assertEquals(10000, result.monthlyTotal)
    }

    @Test
    fun `fetchSummary returns null totals when yearly fetch fails`() = runTest {
        val year = LocalDate.now().year
        coEvery { settingsRepository.isAuthenticated() } returns true
        coEvery { settingsRepository.getSpreadsheetId(year) } returns "sheet-id"
        coEvery { sheetsRepository.fetchYearlyTotal("sheet-id") } returns Result.failure(Exception("Network error"))

        val result = fetcher.fetchSummary()

        assertNull(result.yearlyTotal)
        assertNull(result.monthlyTotal)
        assertNull(summaryCache.yearlyTotal.value)
    }

    @Test
    fun `fetchSummary returns null totals when monthly fetch fails`() = runTest {
        val year = LocalDate.now().year
        val month = LocalDate.now().monthValue
        val sheetName = "${month}月"
        coEvery { settingsRepository.isAuthenticated() } returns true
        coEvery { settingsRepository.getSpreadsheetId(year) } returns "sheet-id"
        coEvery { sheetsRepository.fetchYearlyTotal("sheet-id") } returns Result.success(50000)
        coEvery { sheetsRepository.fetchMonthlyTotal("sheet-id", sheetName) } returns Result.failure(Exception("Network error"))

        val result = fetcher.fetchSummary()

        assertNull(result.yearlyTotal)
        assertNull(result.monthlyTotal)
    }

    @Test
    fun `fetchSummary returns totals and updates cache on success`() = runTest {
        val year = LocalDate.now().year
        val month = LocalDate.now().monthValue
        val sheetName = "${month}月"
        coEvery { settingsRepository.isAuthenticated() } returns true
        coEvery { settingsRepository.getSpreadsheetId(year) } returns "sheet-id"
        coEvery { sheetsRepository.fetchYearlyTotal("sheet-id") } returns Result.success(120000)
        coEvery { sheetsRepository.fetchMonthlyTotal("sheet-id", sheetName) } returns Result.success(15000)

        val result = fetcher.fetchSummary()

        assertEquals(120000, result.yearlyTotal)
        assertEquals(15000, result.monthlyTotal)
        assertEquals(120000, summaryCache.yearlyTotal.value)
        assertEquals(15000, summaryCache.monthlyTotal.value)
    }

    @Test
    fun `fetchSummary handles null totals from sheets API`() = runTest {
        val year = LocalDate.now().year
        val month = LocalDate.now().monthValue
        val sheetName = "${month}月"
        coEvery { settingsRepository.isAuthenticated() } returns true
        coEvery { settingsRepository.getSpreadsheetId(year) } returns "sheet-id"
        coEvery { sheetsRepository.fetchYearlyTotal("sheet-id") } returns Result.success(null)
        coEvery { sheetsRepository.fetchMonthlyTotal("sheet-id", sheetName) } returns Result.success(null)

        val result = fetcher.fetchSummary()

        assertNull(result.yearlyTotal)
        assertNull(result.monthlyTotal)
        assertNull(summaryCache.yearlyTotal.value)
        assertNull(summaryCache.monthlyTotal.value)
    }

    @Test
    fun `fetchSummary uses correct monthly sheet name`() = runTest {
        val year = LocalDate.now().year
        val month = LocalDate.now().monthValue
        val expectedSheetName = "${month}月"
        coEvery { settingsRepository.isAuthenticated() } returns true
        coEvery { settingsRepository.getSpreadsheetId(year) } returns "sheet-id"
        coEvery { sheetsRepository.fetchYearlyTotal("sheet-id") } returns Result.success(0)
        coEvery { sheetsRepository.fetchMonthlyTotal("sheet-id", expectedSheetName) } returns Result.success(0)

        fetcher.fetchSummary()

        coVerify { sheetsRepository.fetchMonthlyTotal("sheet-id", expectedSheetName) }
    }
}
