package com.example.spendmgr.data

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Drive API v3を使用したGoogleDriveRepositoryの実装。
 *
 * @param driveService 認証済みのGoogle Drive APIサービスクライアント
 */
class GoogleDriveRepositoryImpl(
    private val driveService: Drive
) : GoogleDriveRepository {

    /**
     * Google Driveのトップレベル（root）で指定名のフォルダを検索する。
     * @return フォルダID（存在しない場合またはエラー時はnull）
     */
    override suspend fun findFolder(name: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = "mimeType='application/vnd.google-apps.folder'" +
                " and name='$name'" +
                " and 'root' in parents" +
                " and trashed=false"

            val result = driveService.files().list()
                .setQ(query)
                .setFields("files(id)")
                .execute()

            result.files?.firstOrNull()?.id
        } catch (e: Exception) {
            android.util.Log.e("DriveRepo", "findFolder failed: ${e.message}", e)
            null
        }
    }

    /**
     * Google Driveのトップレベル（root）にフォルダを作成する。
     * @return 作成されたフォルダID
     */
    override suspend fun createFolder(name: String): String = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            this.name = name
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf("root")
        }

        val created = driveService.files().create(metadata)
            .setFields("id")
            .execute()

        created.id ?: throw IllegalStateException("フォルダ作成に失敗しました: IDが取得できません")
    }

    /**
     * 指定フォルダ内で指定名のスプレッドシートを検索する。
     * @return スプレッドシートID（存在しない場合またはエラー時はnull）
     */
    override suspend fun findSpreadsheet(folderId: String, name: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val query = "mimeType='application/vnd.google-apps.spreadsheet'" +
                    " and name='$name'" +
                    " and '$folderId' in parents" +
                    " and trashed=false"

                val result = driveService.files().list()
                    .setQ(query)
                    .setFields("files(id)")
                    .execute()

                result.files?.firstOrNull()?.id
            } catch (e: Exception) {
                android.util.Log.e("DriveRepo", "findSpreadsheet failed: ${e.message}", e)
                null
            }
        }

    /**
     * 指定ファイルを指定フォルダに移動する（親フォルダを変更する）。
     * Sheets APIで作成されたスプレッドシートをSpendMgrフォルダに移動するために使用する。
     *
     * @param fileId 移動するファイルのID
     * @param newParentFolderId 移動先フォルダのID
     */
    override suspend fun moveFile(fileId: String, newParentFolderId: String): Unit =
        withContext(Dispatchers.IO) {
            // 現在の親フォルダを取得する
            val file = driveService.files().get(fileId)
                .setFields("parents")
                .execute()
            val previousParents = file.parents?.joinToString(",") ?: "root"

            // 親フォルダを変更する（旧親を削除し、新親を追加）
            driveService.files().update(fileId, null)
                .setAddParents(newParentFolderId)
                .setRemoveParents(previousParents)
                .setFields("id, parents")
                .execute()
        }

    /**
     * 指定フォルダ内のスプレッドシート名のリストを取得する。
     * 名前が4桁の数字（年別スプレッドシート）のもののみを返す。
     *
     * @param folderId フォルダID
     * @return スプレッドシート名のリスト（数字4桁のもののみ）
     */
    override suspend fun listSpreadsheetNames(folderId: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = "mimeType='application/vnd.google-apps.spreadsheet'" +
                    " and '$folderId' in parents" +
                    " and trashed=false"

                val result = driveService.files().list()
                    .setQ(query)
                    .setFields("files(name)")
                    .execute()

                val allNames = result.files?.mapNotNull { it.name } ?: emptyList()
                android.util.Log.d("DriveRepo", "listSpreadsheetNames folderId=$folderId allNames=$allNames")

                val yearPattern = Regex("^\\d{4}$")
                val filtered = allNames.filter { yearPattern.matches(it) }
                android.util.Log.d("DriveRepo", "listSpreadsheetNames filtered=$filtered")
                filtered
            }
        }
}
