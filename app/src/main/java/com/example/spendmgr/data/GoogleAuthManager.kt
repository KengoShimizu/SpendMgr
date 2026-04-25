package com.example.spendmgr.data

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.spendmgr.BuildConfig
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google OAuth認証マネージャー
 *
 * Credential Manager APIを使用してGoogle Sign-Inを実装する。
 * 認証成功後、GoogleAuthUtil.getToken() でアクセストークンを取得し
 * TokenProvider に設定することで Google API 呼び出しを可能にする。
 */
class GoogleAuthManager(
    private val settingsRepository: SettingsRepository,
    private val tokenProvider: TokenProvider
) {

    companion object {
        private const val TAG = "GoogleAuthManager"

        // Google Drive + Sheets のスコープ
        private const val SCOPES =
            "oauth2:https://www.googleapis.com/auth/spreadsheets " +
            "https://www.googleapis.com/auth/drive.file"

        val WEB_CLIENT_ID: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID
    }

    /**
     * Google Sign-Inを実行し、認証結果を返す。
     * @param context Activity の Context（Credential Manager UIの表示に必要）
     */
    suspend fun signIn(context: Context): AuthResult {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val email = googleIdTokenCredential.id

                    // GoogleAuthUtil でアクセストークンを取得する
                    val accessToken = withContext(Dispatchers.IO) {
                        try {
                            val account = Account(email, "com.google")
                            GoogleAuthUtil.getToken(context, account, SCOPES)
                        } catch (e: UserRecoverableAuthException) {
                            // スコープの同意が必要 → 同意画面を起動する
                            Log.d(TAG, "スコープ同意が必要です。同意画面を起動します。")
                            e.intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let {
                                context.startActivity(it)
                            }
                            null
                        }
                    }

                    if (accessToken == null) {
                        // 同意画面を表示した。ユーザーが承認後に再度サインインが必要
                        return AuthResult.Error("Googleアカウントのアクセス許可が必要です。許可後に再度サインインしてください。")
                    }

                    // TokenProvider にアクセストークンとメールアドレスを設定する
                    tokenProvider.setAccessToken(accessToken)
                    tokenProvider.setAccountEmail(email)

                    // 認証状態・メールアドレスを永続化する
                    settingsRepository.saveAuthenticated(true)
                    settingsRepository.saveAccountEmail(email)
                    settingsRepository.saveAuthToken(accessToken)

                    Log.d(TAG, "Google Sign-In 成功: ${googleIdTokenCredential.displayName} ($email)")
                    AuthResult.Success(idToken = googleIdTokenCredential.idToken)

                } catch (e: GoogleIdTokenParsingException) {
                    Log.e(TAG, "IDトークンのパースに失敗しました", e)
                    AuthResult.Error("IDトークンのパースに失敗しました: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "アクセストークンの取得に失敗しました", e)
                    AuthResult.Error("アクセストークンの取得に失敗しました: ${e.message}")
                }
            } else {
                Log.e(TAG, "予期しないCredentialタイプ: ${credential.type}")
                AuthResult.Error("予期しないCredentialタイプです")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Google Sign-Inがキャンセルされました")
            AuthResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.e(TAG, "利用可能なCredentialがありません", e)
            AuthResult.Error("Googleアカウントが見つかりません。デバイスにGoogleアカウントを追加してください。")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential取得エラー", e)
            AuthResult.Error("認証に失敗しました: ${e.message}")
        }
    }

    /**
     * サインアウトし、保存された認証情報をクリアする。
     */
    suspend fun signOut() {
        settingsRepository.saveAuthenticated(false)
        settingsRepository.saveAuthToken("")
        settingsRepository.saveAccountEmail("")
        tokenProvider.clearAccessToken()
        Log.d(TAG, "サインアウト完了")
    }

    /**
     * 現在の認証状態を確認する。
     * アプリ再起動後は保存済みのアクセストークンを TokenProvider に再設定する。
     */
    suspend fun isAuthenticated(): Boolean {
        val authenticated = settingsRepository.isAuthenticated()
        if (authenticated) {
            // アプリ再起動後に TokenProvider にアクセストークンとメールを再設定する
            val token = settingsRepository.getAuthToken()
            val email = settingsRepository.getAccountEmail()
            if (token != null && tokenProvider.getAccessToken() == null) {
                tokenProvider.setAccessToken(token)
            }
            if (email != null) {
                tokenProvider.setAccountEmail(email)
            }
        }
        return authenticated
    }
}

/**
 * Google Sign-Inの結果を表すシールドクラス。
 */
sealed class AuthResult {
    data class Success(val idToken: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
    object Cancelled : AuthResult()
}
