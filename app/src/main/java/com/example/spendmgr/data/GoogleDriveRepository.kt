package com.example.spendmgr.data

/**
 * Google Drive上のフォルダ・スプレッドシートを操作するリポジトリインターフェース。
 */
interface GoogleDriveRepository {
    /**
     * Google Driveのトップレベルで指定名のフォルダを検索する。
     * @return フォルダID（存在しない場合はnull）
     */
    suspend fun findFolder(name: String): String?

    /**
     * Google Driveのトップレベルにフォルダを作成する。
     * @return 作成されたフォルダID
     */
    suspend fun createFolder(name: String): String

    /**
     * 指定フォルダ内で指定名のスプレッドシートを検索する。
     * @return スプレッドシートID（存在しない場合はnull）
     */
    suspend fun findSpreadsheet(folderId: String, name: String): String?

    /**
     * 指定ファイルを指定フォルダに移動する（親フォルダを変更する）。
     * Sheets APIで作成されたスプレッドシートをSpendMgrフォルダに移動するために使用する。
     * @param fileId 移動するファイルのID
     * @param newParentFolderId 移動先フォルダのID
     */
    suspend fun moveFile(fileId: String, newParentFolderId: String)

    /**
     * 指定フォルダ内のスプレッドシート名のリストを取得する。
     * SpendMgr フォルダ内の年別スプレッドシート（例: "2025", "2024"）を列挙するために使用する。
     * 名前が4桁の数字のスプレッドシートのみを対象とする（年別スプレッドシートのフィルタリング）。
     *
     * @param folderId フォルダID
     * @return スプレッドシート名のリスト（数字4桁のもののみ）
     */
    suspend fun listSpreadsheetNames(folderId: String): Result<List<String>>
}
