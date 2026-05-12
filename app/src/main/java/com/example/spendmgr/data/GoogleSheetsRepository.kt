package com.example.spendmgr.data

import com.example.spendmgr.domain.model.AppendResult
import com.example.spendmgr.domain.model.ExpenseRecord

interface GoogleSheetsRepository {
    /**
     * 指定されたスプレッドシートの指定シートに経費を追記する。
     * 各Monthly_Sheetは1行目がヘッダー行（A列: 日付、B列: 金額、C列: カテゴリ）で、
     * データは2行目以降に追記される。
     * @return 追記された行番号を含むAppendResult
     */
    suspend fun appendExpense(
        spreadsheetId: String,
        sheetName: String,
        expense: ExpenseRecord
    ): Result<AppendResult>

    /**
     * 指定されたスプレッドシートの指定シートから指定行を削除する。
     * 取り消し機能で使用する。
     */
    suspend fun deleteRow(
        spreadsheetId: String,
        sheetId: Int,
        rowIndex: Int
    ): Result<Unit>

    /**
     * 新規スプレッドシートを作成し、まとめシート + 12ヶ月シートを初期化する。
     * 各月シートの1行目にはヘッダー行（A列: 日付、B列: 金額、C列: カテゴリ）を設定する。
     * Note: folderId はこのクラスでは使用しない。呼び出し元が Drive API でフォルダへの移動を行う。
     * @return 作成されたスプレッドシートのID
     */
    suspend fun createYearlySpreadsheet(
        name: String,
        folderId: String
    ): Result<String>

    /**
     * まとめシートにSUM関数を設定する。
     * 各月シートのB列（金額列）の合計を参照するSUM関数を設定する。
     */
    suspend fun setupSummarySheet(
        spreadsheetId: String
    ): Result<Unit>

    /**
     * まとめシートから今年の合計額を取得する。
     * 各月シートの金額（B列）を割り勘人数（E列）で割った値の合計を直接計算する。
     * まとめシートのSUM式は旧データとの互換性のため参照しない。
     * E列が空の旧データは割り勘人数1として扱う。
     * @return 今年の合計額（円）。取得失敗時はnull
     */
    suspend fun fetchYearlyTotal(
        spreadsheetId: String
    ): Result<Int?>

    /**
     * 指定月シートから今月の合計額を取得する。
     * @return 今月の合計額（円）。取得失敗時はnull
     */
    suspend fun fetchMonthlyTotal(
        spreadsheetId: String,
        sheetName: String
    ): Result<Int?>

    /**
     * 指定シートのカテゴリ列（C列）と金額列（B列）のデータを取得する。
     * ヘッダー行（1行目）は除外される。
     *
     * @param spreadsheetId スプレッドシートID
     * @param sheetName シート名（例: "4月"）
     * @return カテゴリと金額のペアのリスト
     */
    suspend fun fetchCategoryAmounts(
        spreadsheetId: String,
        sheetName: String
    ): Result<List<Pair<String, Int>>>

    /**
     * 指定シートのカード支払い（D列がTRUEまたは○）の合計額を取得する。
     * ヘッダー行（1行目）は除外される。
     *
     * @param spreadsheetId スプレッドシートID
     * @param sheetName シート名（例: "4月"）
     * @return カード支払い合計額（円）。取得失敗時はnull
     */
    suspend fun fetchCreditCardTotal(
        spreadsheetId: String,
        sheetName: String
    ): Result<Int?>

    /**
     * 指定シートから日付・金額・割り勘人数の生データを取得する。
     * 給料日サイクル集計（25日〜翌月24日）に使用する。
     * ヘッダー行（1行目）は除外される。
     *
     * @param spreadsheetId スプレッドシートID
     * @param sheetName シート名（例: "4月"）
     * @return Triple(日付文字列 "M/d", 金額, 割り勘人数) のリスト
     */
    suspend fun fetchRawExpenses(
        spreadsheetId: String,
        sheetName: String
    ): Result<List<Triple<String, Int, Int>>>
}
