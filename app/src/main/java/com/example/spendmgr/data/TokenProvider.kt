package com.example.spendmgr.data

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpResponse
import com.google.api.client.http.HttpUnsuccessfulResponseHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google API リクエストに Bearer トークンを付与する HttpRequestInitializer。
 *
 * 401エラー時にトークンをサイレントリフレッシュしてリトライする。
 * これにより1時間のトークン有効期限切れ後も再ログイン不要で動作する。
 */
@Singleton
class TokenProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : HttpRequestInitializer {

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var accountEmail: String? = null

    companion object {
        private const val TAG = "TokenProvider"
        private const val SCOPES =
            "oauth2:https://www.googleapis.com/auth/spreadsheets " +
            "https://www.googleapis.com/auth/drive.file"
    }

    fun setAccessToken(token: String) {
        accessToken = token
    }

    fun setAccountEmail(email: String) {
        accountEmail = email
    }

    fun clearAccessToken() {
        accessToken = null
    }

    fun getAccessToken(): String? = accessToken

    override fun initialize(request: HttpRequest) {
        val token = accessToken
        if (token != null) {
            request.headers.authorization = "Bearer $token"
        }
        request.connectTimeout = 30_000
        request.readTimeout = 30_000

        // 401エラー時にトークンをリフレッシュしてリトライする
        request.unsuccessfulResponseHandler = HttpUnsuccessfulResponseHandler { req, resp, retry ->
            if (resp.statusCode == 401 && retry) {
                val email = accountEmail
                if (email != null) {
                    try {
                        // 期限切れトークンをキャッシュから削除
                        val account = Account(email, "com.google")
                        GoogleAuthUtil.clearToken(context, accessToken ?: "")
                        // 新しいトークンを取得
                        val newToken = GoogleAuthUtil.getToken(context, account, SCOPES)
                        accessToken = newToken
                        req.headers.authorization = "Bearer $newToken"
                        Log.d(TAG, "トークンをサイレントリフレッシュしました")
                        true // リトライする
                    } catch (e: Exception) {
                        Log.e(TAG, "トークンリフレッシュ失敗", e)
                        false
                    }
                } else {
                    false
                }
            } else {
                false
            }
        }
    }
}
