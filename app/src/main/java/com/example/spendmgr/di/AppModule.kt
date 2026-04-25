package com.example.spendmgr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.spendmgr.data.CategoryHistoryRepository
import com.example.spendmgr.data.CategoryHistoryRepositoryImpl
import com.example.spendmgr.data.GoogleAuthManager
import com.example.spendmgr.data.GoogleDriveRepository
import com.example.spendmgr.data.GoogleDriveRepositoryImpl
import com.example.spendmgr.data.GoogleSheetsRepository
import com.example.spendmgr.data.GoogleSheetsRepositoryImpl
import com.example.spendmgr.data.PendingExpenseRepository
import com.example.spendmgr.data.PendingExpenseRepositoryImpl
import com.example.spendmgr.data.SettingsRepository
import com.example.spendmgr.data.SettingsRepositoryImpl
import com.example.spendmgr.data.TokenProvider
import com.example.spendmgr.data.local.SpendMgrDatabase
import com.example.spendmgr.data.local.dao.CategoryHistoryDao
import com.example.spendmgr.data.local.dao.PendingExpensesDao
import com.example.spendmgr.domain.CategorySuggestionEngine
import com.example.spendmgr.domain.ExpenseValidator
import com.example.spendmgr.domain.PieChartCache
import com.example.spendmgr.domain.PieChartFetcher
import com.example.spendmgr.domain.SpreadsheetResolver
import com.example.spendmgr.domain.SummaryCache
import com.example.spendmgr.domain.SummaryFetcher
import com.google.api.services.drive.Drive
import com.google.api.services.sheets.v4.Sheets
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spendmgr_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ─── Room Database ────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideSpendMgrDatabase(@ApplicationContext context: Context): SpendMgrDatabase {
        return SpendMgrDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideCategoryHistoryDao(database: SpendMgrDatabase): CategoryHistoryDao {
        return database.categoryHistoryDao()
    }

    @Provides
    @Singleton
    fun providePendingExpensesDao(database: SpendMgrDatabase): PendingExpensesDao {
        return database.pendingExpensesDao()
    }

    // ─── DataStore ────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    // ─── Repositories ─────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideCategoryHistoryRepository(
        dao: CategoryHistoryDao
    ): CategoryHistoryRepository {
        return CategoryHistoryRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun providePendingExpenseRepository(
        dao: PendingExpensesDao
    ): PendingExpenseRepository {
        return PendingExpenseRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: DataStore<Preferences>
    ): SettingsRepository {
        return SettingsRepositoryImpl(dataStore)
    }

    @Provides
    @Singleton
    fun provideTokenProvider(
        @ApplicationContext context: Context
    ): TokenProvider {
        return TokenProvider(context)
    }

    @Provides
    @Singleton
    fun provideGoogleAuthManager(
        settingsRepository: SettingsRepository,
        tokenProvider: TokenProvider
    ): GoogleAuthManager {
        return GoogleAuthManager(settingsRepository, tokenProvider)
    }

    @Provides
    @Singleton
    fun provideGoogleDriveRepository(
        driveService: Drive
    ): GoogleDriveRepository {
        return GoogleDriveRepositoryImpl(driveService)
    }

    @Provides
    @Singleton
    fun provideGoogleSheetsRepository(
        sheetsService: Sheets
    ): GoogleSheetsRepository {
        return GoogleSheetsRepositoryImpl(sheetsService)
    }

    // ─── Domain Services ──────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideExpenseValidator(): ExpenseValidator {
        return ExpenseValidator()
    }

    @Provides
    @Singleton
    fun provideCategorySuggestionEngine(
        categoryHistoryRepository: CategoryHistoryRepository
    ): CategorySuggestionEngine {
        return CategorySuggestionEngine(categoryHistoryRepository)
    }

    @Provides
    @Singleton
    fun provideSpreadsheetResolver(
        googleDriveRepository: GoogleDriveRepository,
        googleSheetsRepository: GoogleSheetsRepository,
        settingsRepository: SettingsRepository
    ): SpreadsheetResolver {
        return SpreadsheetResolver(googleDriveRepository, googleSheetsRepository, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideSummaryCache(
        settingsRepository: SettingsRepository
    ): SummaryCache {
        return SummaryCache(settingsRepository)
    }

    @Provides
    @Singleton
    fun provideSummaryFetcher(
        googleSheetsRepository: GoogleSheetsRepository,
        spreadsheetResolver: SpreadsheetResolver,
        settingsRepository: SettingsRepository,
        summaryCache: SummaryCache
    ): SummaryFetcher {
        return SummaryFetcher(googleSheetsRepository, spreadsheetResolver, settingsRepository, summaryCache)
    }

    @Provides
    @Singleton
    fun providePieChartCache(settingsRepository: SettingsRepository): PieChartCache {
        return PieChartCache(settingsRepository)
    }

    @Provides
    @Singleton
    fun providePieChartFetcher(
        googleSheetsRepository: GoogleSheetsRepository,
        googleDriveRepository: GoogleDriveRepository,
        settingsRepository: SettingsRepository,
        pieChartCache: PieChartCache
    ): PieChartFetcher {
        return PieChartFetcher(googleSheetsRepository, googleDriveRepository, settingsRepository, pieChartCache)
    }
}
