package com.example.spendmgr.di

import com.example.spendmgr.data.TokenProvider
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.sheets.v4.Sheets
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Google Sheets API v4 および Google Drive API v3 クライアントのプロバイダ。
 *
 * GoogleAccountCredential の代わりに TokenProvider を使用する。
 * TokenProvider は SettingsRepository に保存されたアクセストークンを
 * 各リクエストのAuthorizationヘッダーに付与する。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val APP_NAME = "SpendMgr"

    @Provides
    @Singleton
    fun provideSheetsService(
        tokenProvider: TokenProvider
    ): Sheets {
        return Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            tokenProvider as HttpRequestInitializer
        )
            .setApplicationName(APP_NAME)
            .build()
    }

    @Provides
    @Singleton
    fun provideDriveService(
        tokenProvider: TokenProvider
    ): Drive {
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            tokenProvider as HttpRequestInitializer
        )
            .setApplicationName(APP_NAME)
            .build()
    }
}
