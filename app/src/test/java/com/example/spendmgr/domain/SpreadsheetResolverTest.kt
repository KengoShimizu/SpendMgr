package com.example.spendmgr.domain

import com.example.spendmgr.data.GoogleDriveRepository
import com.example.spendmgr.data.GoogleSheetsRepository
import com.example.spendmgr.data.SettingsRepository
import com.example.spendmgr.domain.model.SpreadsheetTarget
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SpreadsheetResolverTest {

    private lateinit var driveRepository: GoogleDriveRepository
    private lateinit var sheetsRepository: GoogleSheetsRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var resolver: SpreadsheetResolver

    @BeforeEach
    fun setUp() {
        driveRepository = mockk()
        sheetsRepository = mockk()
        settingsRepository = mockk()
        resolver = SpreadsheetResolver(driveRepository, sheetsRepository, settingsRepository)
    }

    // ---- resolve() ----

    @Test
    fun `resolve returns correct sheetName for given date`() = runTest {
        val date = LocalDate.of(2026, 4, 22)
        coEvery { settingsRepository.getFolderId() } returns "folder-id"
        coEvery { settingsRepository.getSpreadsheetId(2026) } returns "sheet-id"

        val result = resolver.resolve(date)

        assertEquals(SpreadsheetTarget(spreadsheetId = "sheet-id", sheetName = "4月"), result)
    }

    @Test
    fun `resolve sheetName uses month value without zero padding`() = runTest {
        val date = LocalDate.of(2026, 12, 1)
        coEvery { settingsRepository.getFolderId() } returns "folder-id"
        coEvery { settingsRepository.getSpreadsheetId(2026) } returns "sheet-id"

        val result = resolver.resolve(date)

        assertEquals("12月", result.sheetName)
    }

    @Test
    fun `resolve creates folder when not cached and not found on Drive`() = runTest {
        val date = LocalDate.of(2026, 4, 22)
        coEvery { settingsRepository.getFolderId() } returns null
        coEvery { driveRepository.findFolder("SpendMgr") } returns null
        coEvery { driveRepository.createFolder("SpendMgr") } returns "new-folder-id"
        coEvery { settingsRepository.saveFolderId("new-folder-id") } returns Unit
        coEvery { settingsRepository.getSpreadsheetId(2026) } returns null
        coEvery { driveRepository.findSpreadsheet("new-folder-id", "2026") } returns null
        coEvery { sheetsRepository.createYearlySpreadsheet("2026", "new-folder-id") } returns Result.success("new-sheet-id")
        coEvery { sheetsRepository.setupSummarySheet("new-sheet-id") } returns Result.success(Unit)
        coEvery { driveRepository.moveFile("new-sheet-id", "new-folder-id") } returns Unit
        coEvery { settingsRepository.saveSpreadsheetId(2026, "new-sheet-id") } returns Unit

        val result = resolver.resolve(date)

        assertEquals("new-sheet-id", result.spreadsheetId)
        coVerify { driveRepository.createFolder("SpendMgr") }
        coVerify { settingsRepository.saveFolderId("new-folder-id") }
    }

    @Test
    fun `resolve reuses existing folder from Drive`() = runTest {
        val date = LocalDate.of(2026, 4, 22)
        coEvery { settingsRepository.getFolderId() } returns null
        coEvery { driveRepository.findFolder("SpendMgr") } returns "existing-folder-id"
        coEvery { settingsRepository.saveFolderId("existing-folder-id") } returns Unit
        coEvery { settingsRepository.getSpreadsheetId(2026) } returns "existing-sheet-id"

        val result = resolver.resolve(date)

        assertEquals("existing-sheet-id", result.spreadsheetId)
        coVerify(exactly = 0) { driveRepository.createFolder(any()) }
    }

    @Test
    fun `resolve creates spreadsheet when not found`() = runTest {
        val date = LocalDate.of(2026, 4, 22)
        coEvery { settingsRepository.getFolderId() } returns "folder-id"
        coEvery { settingsRepository.getSpreadsheetId(2026) } returns null
        coEvery { driveRepository.findSpreadsheet("folder-id", "2026") } returns null
        coEvery { sheetsRepository.createYearlySpreadsheet("2026", "folder-id") } returns Result.success("new-sheet-id")
        coEvery { sheetsRepository.setupSummarySheet("new-sheet-id") } returns Result.success(Unit)
        coEvery { driveRepository.moveFile("new-sheet-id", "folder-id") } returns Unit
        coEvery { settingsRepository.saveSpreadsheetId(2026, "new-sheet-id") } returns Unit

        val result = resolver.resolve(date)

        assertEquals("new-sheet-id", result.spreadsheetId)
        coVerify { sheetsRepository.createYearlySpreadsheet("2026", "folder-id") }
        coVerify { sheetsRepository.setupSummarySheet("new-sheet-id") }
    }

    @Test
    fun `resolve reuses existing spreadsheet from Drive`() = runTest {
        val date = LocalDate.of(2026, 4, 22)
        coEvery { settingsRepository.getFolderId() } returns "folder-id"
        coEvery { settingsRepository.getSpreadsheetId(2026) } returns null
        coEvery { driveRepository.findSpreadsheet("folder-id", "2026") } returns "existing-sheet-id"
        coEvery { settingsRepository.saveSpreadsheetId(2026, "existing-sheet-id") } returns Unit

        val result = resolver.resolve(date)

        assertEquals("existing-sheet-id", result.spreadsheetId)
        coVerify(exactly = 0) { sheetsRepository.createYearlySpreadsheet(any(), any()) }
    }

    // ---- ensureFolder() ----

    @Test
    fun `ensureFolder returns cached folder ID without Drive call`() = runTest {
        coEvery { settingsRepository.getFolderId() } returns "cached-folder-id"

        val result = resolver.ensureFolder()

        assertEquals("cached-folder-id", result)
        coVerify(exactly = 0) { driveRepository.findFolder(any()) }
    }

    @Test
    fun `ensureFolder finds existing folder on Drive`() = runTest {
        coEvery { settingsRepository.getFolderId() } returns null
        coEvery { driveRepository.findFolder("SpendMgr") } returns "drive-folder-id"
        coEvery { settingsRepository.saveFolderId("drive-folder-id") } returns Unit

        val result = resolver.ensureFolder()

        assertEquals("drive-folder-id", result)
        coVerify(exactly = 0) { driveRepository.createFolder(any()) }
    }

    @Test
    fun `ensureFolder creates folder when not found on Drive`() = runTest {
        coEvery { settingsRepository.getFolderId() } returns null
        coEvery { driveRepository.findFolder("SpendMgr") } returns null
        coEvery { driveRepository.createFolder("SpendMgr") } returns "created-folder-id"
        coEvery { settingsRepository.saveFolderId("created-folder-id") } returns Unit

        val result = resolver.ensureFolder()

        assertEquals("created-folder-id", result)
        coVerify { driveRepository.createFolder("SpendMgr") }
    }

    // ---- getCurrentYearSpreadsheetUrl() ----

    @Test
    fun `getCurrentYearSpreadsheetUrl returns null when folder not set`() = runTest {
        coEvery { settingsRepository.getSpreadsheetId(any()) } returns null
        coEvery { settingsRepository.getFolderId() } returns null

        val result = resolver.getCurrentYearSpreadsheetUrl()

        assertNull(result)
    }

    @Test
    fun `getCurrentYearSpreadsheetUrl returns correct URL format`() = runTest {
        val year = LocalDate.now().year
        coEvery { settingsRepository.getSpreadsheetId(year) } returns "abc123"

        val result = resolver.getCurrentYearSpreadsheetUrl()

        assertEquals("https://docs.google.com/spreadsheets/d/abc123", result)
    }

    @Test
    fun `getCurrentYearSpreadsheetUrl returns null when spreadsheet not found on Drive`() = runTest {
        val year = LocalDate.now().year
        coEvery { settingsRepository.getSpreadsheetId(year) } returns null
        coEvery { settingsRepository.getFolderId() } returns "folder-id"
        coEvery { driveRepository.findSpreadsheet("folder-id", year.toString()) } returns null

        val result = resolver.getCurrentYearSpreadsheetUrl()

        assertNull(result)
    }
}
