package com.example.spendmgr.data

import com.example.spendmgr.domain.model.AppendResult
import com.example.spendmgr.domain.model.ExpenseRecord
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.BooleanCondition
import com.google.api.services.sheets.v4.model.DataValidationRule
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest
import com.google.api.services.sheets.v4.model.DimensionRange
import com.google.api.services.sheets.v4.model.GridRange
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.SetDataValidationRequest
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

class GoogleSheetsRepositoryImpl(
    private val sheetsService: Sheets
) : GoogleSheetsRepository {

    private val dateFormatter = DateTimeFormatter.ofPattern("M/d")

    // 月シート名リスト（1月〜12月）
    private val monthSheetNames = (1..12).map { "${it}月" }

    override suspend fun appendExpense(
        spreadsheetId: String,
        sheetName: String,
        expense: ExpenseRecord
    ): Result<AppendResult> = withContext(Dispatchers.IO) {
        runCatching {
            val dateFormatted = expense.date.format(dateFormatter)
            // D列: TRUE/FALSE（スプレッドシート上でチェックボックスとして表示）
            val creditCardValue = expense.isCreditCard
            val values = listOf(
                listOf(dateFormatted, expense.amount, expense.category, creditCardValue)
            )
            val body = ValueRange().setValues(values)
            val range = "${sheetName}!A:D"

            val response = sheetsService.spreadsheets().values()
                .append(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("OVERWRITE")
                .execute()

            // updatedRange から行番号を抽出（例: "4月!A5:C5" → 5）
            val updatedRange = response.updates?.updatedRange ?: ""
            val rowIndex = parseRowIndexFromRange(updatedRange)

            // sheetId を取得するためにスプレッドシートのメタデータを取得
            val spreadsheet = sheetsService.spreadsheets()
                .get(spreadsheetId)
                .execute()
            val sheetId = spreadsheet.sheets
                ?.firstOrNull { it.properties?.title == sheetName }
                ?.properties?.sheetId
                ?: 0

            // --- ここからチェックボックス化の処理を追加 ---
            if (rowIndex > 0) {
                val checkboxRequest = Request().apply {
                    setDataValidation = SetDataValidationRequest().apply {
                        this.range = GridRange().apply {
                            this.sheetId = sheetId
                            this.startRowIndex = rowIndex - 1 // 0-based index
                            this.endRowIndex = rowIndex
                            this.startColumnIndex = 3 // D列 (0-based: 3)
                            this.endColumnIndex = 4
                        }
                        this.rule = DataValidationRule().apply {
                            condition = BooleanCondition().apply {
                                type = "BOOLEAN"
                            }
                            showCustomUi = true
                        }
                    }
                }

                val batchUpdateBody = BatchUpdateSpreadsheetRequest().apply {
                    requests = listOf(checkboxRequest)
                }

                sheetsService.spreadsheets()
                    .batchUpdate(spreadsheetId, batchUpdateBody)
                    .execute()
            }
            // --- ここまで ---

            AppendResult(
                spreadsheetId = spreadsheetId,
                sheetId = sheetId,
                sheetName = sheetName,
                rowIndex = rowIndex
            )
        }
    }

    /**
     * レンジ文字列から行番号を抽出する。
     * 例: "4月!A5:C5" → 5、"Sheet1!A2:C2" → 2
     */
    private fun parseRowIndexFromRange(range: String): Int {
        // "SheetName!A{row}:C{row}" 形式から行番号を取得
        // 感嘆符以降の部分を取り、最初のセル参照から数字を抽出
        val afterExclamation = if (range.contains("!")) range.substringAfter("!") else range
        // "A5:C5" → "5"
        val rowStr = afterExclamation.filter { it.isDigit() }.let {
            // 複数の数字グループがある場合は最初のグループを使用
            Regex("\\d+").find(afterExclamation)?.value
        }
        return rowStr?.toIntOrNull() ?: 1
    }

    override suspend fun deleteRow(
        spreadsheetId: String,
        sheetId: Int,
        rowIndex: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // rowIndex は 1-based なので 0-based に変換
            val startIndex = rowIndex - 1
            val endIndex = rowIndex

            val deleteRequest = DeleteDimensionRequest().apply {
                range = DimensionRange().apply {
                    this.sheetId = sheetId
                    dimension = "ROWS"
                    this.startIndex = startIndex
                    this.endIndex = endIndex
                }
            }

            val batchRequest = BatchUpdateSpreadsheetRequest().apply {
                requests = listOf(
                    Request().apply { deleteDimension = deleteRequest }
                )
            }

            sheetsService.spreadsheets()
                .batchUpdate(spreadsheetId, batchRequest)
                .execute()

            Unit
        }
    }

    override suspend fun createYearlySpreadsheet(
        name: String,
        folderId: String
        // Note: folderId はこのクラスでは使用しない。
        // 呼び出し元（SpreadsheetResolver）が Drive API でフォルダへの移動を行う。
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // シート構成: まとめ + 1月〜12月
            val sheets = mutableListOf<Sheet>()

            // まとめシート（index 0）
            sheets.add(
                Sheet().apply {
                    properties = SheetProperties().apply {
                        title = "まとめ"
                        index = 0
                    }
                }
            )

            // 月別シート（1月〜12月）
            monthSheetNames.forEachIndexed { i, monthName ->
                sheets.add(
                    Sheet().apply {
                        properties = SheetProperties().apply {
                            title = monthName
                            index = i + 1
                        }
                    }
                )
            }

            val spreadsheet = Spreadsheet().apply {
                properties = SpreadsheetProperties().apply {
                    title = name
                }
                this.sheets = sheets
            }

            val created = sheetsService.spreadsheets()
                .create(spreadsheet)
                .execute()

            val spreadsheetId = created.spreadsheetId

            // 各月シートにヘッダー行を設定（D列: カード払い を追加）
            val headerValues = listOf(listOf("日付", "金額", "カテゴリ", "カード払い"))
            val valueRanges = monthSheetNames.map { monthName ->
                ValueRange()
                    .setRange("${monthName}!A1:D1")
                    .setValues(headerValues)
            }

            val batchUpdateRequest = BatchUpdateValuesRequest().apply {
                data = valueRanges
                valueInputOption = "USER_ENTERED"
            }

            sheetsService.spreadsheets().values()
                .batchUpdate(spreadsheetId, batchUpdateRequest)
                .execute()

            //// 各月シートのD列（D2:D1000）にチェックボックスの入力規則を設定
            //val spreadsheetMeta = sheetsService.spreadsheets().get(spreadsheetId).execute()
            //val checkboxRequests = spreadsheetMeta.sheets
            //    ?.filter { it.properties?.title != "まとめ" }
            //    ?.map { sheet ->
            //        val sheetId = sheet.properties.sheetId
            //        Request().apply {
            //            setDataValidation = SetDataValidationRequest().apply {
            //                range = GridRange().apply {
            //                    this.sheetId = sheetId
            //                    startRowIndex = 1      // 0-based: 2行目から
            //                    endRowIndex = 1000
            //                    startColumnIndex = 3   // D列
            //                    endColumnIndex = 4
            //                }
            //                rule = DataValidationRule().apply {
            //                    condition = BooleanCondition().apply {
            //                        type = "BOOLEAN"
            //                    }
            //                    showCustomUi = true
            //                }
            //            }
            //        }
            //    } ?: emptyList()
//
            //if (checkboxRequests.isNotEmpty()) {
            //    sheetsService.spreadsheets()
            //        .batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().apply {
            //            requests = checkboxRequests
            //        })
            //        .execute()
            //}

            spreadsheetId
        }
    }

    override suspend fun setupSummarySheet(
        spreadsheetId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // まとめシートのデータを構築
            // Row 1: ヘッダー（月、合計）
            // Rows 2-13: 各月の名前と SUM 関数
            val summaryData = mutableListOf<List<Any>>()

            // ヘッダー行
            summaryData.add(listOf("月", "合計"))

            // 各月の行（1月〜12月）
            monthSheetNames.forEach { monthName ->
                // ヘッダー行（B1）を除いた B2:B 範囲を SUM
                val sumFormula = "=SUM('${monthName}'!B2:B)"
                summaryData.add(listOf(monthName, sumFormula))
            }

            val valueRange = ValueRange()
                .setRange("まとめ!A1:B13")
                .setValues(summaryData)

            val batchUpdateRequest = BatchUpdateValuesRequest().apply {
                data = listOf(valueRange)
                valueInputOption = "USER_ENTERED"
            }

            sheetsService.spreadsheets().values()
                .batchUpdate(spreadsheetId, batchUpdateRequest)
                .execute()

            Unit
        }
    }

    override suspend fun fetchYearlyTotal(
        spreadsheetId: String
    ): Result<Int?> = withContext(Dispatchers.IO) {
        runCatching {
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "まとめ!B2:B13")
                .execute()

            val values = response.getValues()
            if (values.isNullOrEmpty()) return@runCatching null

            var total = 0
            for (row in values) {
                if (row.isNotEmpty()) {
                    val cellValue = row[0]?.toString()
                    val amount = cellValue?.toDoubleOrNull()?.toInt()
                    if (amount != null) {
                        total += amount
                    }
                }
            }
            total
        }
    }

    override suspend fun fetchMonthlyTotal(
        spreadsheetId: String,
        sheetName: String
    ): Result<Int?> = withContext(Dispatchers.IO) {
        runCatching {
            // B2:B でヘッダー行（B1）をスキップして金額列を取得
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "${sheetName}!B2:B")
                .execute()

            val values = response.getValues()
            if (values.isNullOrEmpty()) return@runCatching null

            var total = 0
            for (row in values) {
                if (row.isNotEmpty()) {
                    val cellValue = row[0]?.toString()
                    val amount = cellValue?.toDoubleOrNull()?.toInt()
                    if (amount != null) {
                        total += amount
                    }
                }
            }
            total
        }
    }

    override suspend fun fetchCategoryAmounts(
        spreadsheetId: String,
        sheetName: String
    ): Result<List<Pair<String, Int>>> = withContext(Dispatchers.IO) {
        runCatching {
            // B2:C でヘッダー行（1行目）をスキップして金額列（B）とカテゴリ列（C）を取得
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "${sheetName}!B2:C")
                .execute()

            val values = response.getValues()
            if (values.isNullOrEmpty()) return@runCatching emptyList()

            val result = mutableListOf<Pair<String, Int>>()
            for (row in values) {
                // B列（index 0）: 金額、C列（index 1）: カテゴリ
                if (row.size < 2) continue
                val amountStr = row[0]?.toString()
                val category = row[1]?.toString()

                if (amountStr.isNullOrBlank() || category.isNullOrBlank()) continue

                val amount = amountStr.toDoubleOrNull()?.toInt() ?: continue
                result.add(Pair(category, amount))
            }
            result
        }
    }

    override suspend fun fetchCreditCardTotal(
        spreadsheetId: String,
        sheetName: String
    ): Result<Int?> = withContext(Dispatchers.IO) {
        runCatching {
            // B2:D でヘッダー行をスキップして金額列（B）とカード払い列（D）を取得
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "${sheetName}!B2:D")
                .execute()

            val values = response.getValues()
            if (values.isNullOrEmpty()) return@runCatching null

            var total = 0
            for (row in values) {
                // B列（index 0）: 金額、D列（index 2）: カード払い
                if (row.isEmpty()) continue
                val amountStr = row[0]?.toString()
                val amount = amountStr?.toDoubleOrNull()?.toInt() ?: continue

                // D列が存在しない（旧データ）場合は集計から除外
                if (row.size < 3) continue
                val cardValue = row[2]?.toString()
                // TRUE または ○ の場合にカード払いとみなす
                val isCreditCard = cardValue == "TRUE" || cardValue == "○"
                if (isCreditCard) {
                    total += amount
                }
            }
            total
        }
    }
}
