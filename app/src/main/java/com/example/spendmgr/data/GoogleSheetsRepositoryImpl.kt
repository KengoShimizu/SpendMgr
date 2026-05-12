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
            // E列: 割り勘人数
            val splitCount = expense.splitCount
            val values = listOf(
                listOf(dateFormatted, expense.amount, expense.category, creditCardValue, splitCount)
            )
            val body = ValueRange().setValues(values)
            val range = "${sheetName}!A:E"

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

            // 各月シートにヘッダー行を設定（E列: 割り勘人数 を追加）
            val headerValues = listOf(listOf("日付", "金額", "カテゴリ", "カード払い", "割り勘人数"))
            val valueRanges = monthSheetNames.map { monthName ->
                ValueRange()
                    .setRange("${monthName}!A1:E1")
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
                // E列が存在しない旧データは splitCount=1 として扱うため IFERROR で保護
                // =SUMPRODUCT(B2:B / IF(E2:E="", 1, IFERROR(E2:E, 1)))
                val sumFormula = "=SUMPRODUCT(IF('${monthName}'!B2:B=\"\",0,'${monthName}'!B2:B/IF('${monthName}'!E2:E=\"\",1,IFERROR(VALUE('${monthName}'!E2:E),1))))"
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
            // まとめシートのSUM式は旧データ（割り勘前）の可能性があるため、
            // 各月シートから直接 B列・E列を取得して割り勘後の金額を合計する
            var total = 0
            for (month in 1..12) {
                val sheetName = "${month}月"
                val response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "${sheetName}!B2:E")
                    .execute()
                val values = response.getValues() ?: continue
                for (row in values) {
                    if (row.isEmpty()) continue
                    val amount = row[0]?.toString()?.toDoubleOrNull()?.toInt() ?: continue
                    val splitCount = if (row.size >= 4) {
                        row[3]?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    } else 1
                    total += amount / splitCount
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
            // B2:E でヘッダー行（B1）をスキップして金額列（B）と割り勘人数列（E）を取得
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "${sheetName}!B2:E")
                .execute()

            val values = response.getValues()
            if (values.isNullOrEmpty()) return@runCatching null

            var total = 0
            for (row in values) {
                if (row.isEmpty()) continue
                val cellValue = row[0]?.toString()
                val amount = cellValue?.toDoubleOrNull()?.toInt() ?: continue
                // E列（index 3）: 割り勘人数。存在しない旧データは 1 として扱う
                val splitCount = if (row.size >= 4) {
                    row[3]?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                } else 1
                total += amount / splitCount
            }
            total
        }
    }

    override suspend fun fetchCategoryAmounts(
        spreadsheetId: String,
        sheetName: String
    ): Result<List<Pair<String, Int>>> = withContext(Dispatchers.IO) {
        runCatching {
            // B2:E でヘッダー行（1行目）をスキップして金額列（B）、カテゴリ列（C）、割り勘人数列（E）を取得
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "${sheetName}!B2:E")
                .execute()

            val values = response.getValues()
            if (values.isNullOrEmpty()) return@runCatching emptyList()

            val result = mutableListOf<Pair<String, Int>>()
            for (row in values) {
                // B列（index 0）: 金額、C列（index 1）: カテゴリ、E列（index 3）: 割り勘人数
                if (row.size < 2) continue
                val amountStr = row[0]?.toString()
                val category = row[1]?.toString()

                if (amountStr.isNullOrBlank() || category.isNullOrBlank()) continue

                val amount = amountStr.toDoubleOrNull()?.toInt() ?: continue
                val splitCount = if (row.size >= 4) {
                    row[3]?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                } else 1
                result.add(Pair(category, amount / splitCount))
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
            // 家計立替は割り勘前の金額を使用するため E列（割り勘人数）は参照しない
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

    override suspend fun fetchRawExpenses(
        spreadsheetId: String,
        sheetName: String
    ): Result<List<Triple<String, Int, Int>>> = withContext(Dispatchers.IO) {
        runCatching {
            // A2:E でヘッダー行をスキップして日付（A）、金額（B）、割り勘人数（E）を取得
            val response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "${sheetName}!A2:E")
                .execute()

            val values = response.getValues()
            if (values.isNullOrEmpty()) return@runCatching emptyList()

            val result = mutableListOf<Triple<String, Int, Int>>()
            for (row in values) {
                if (row.size < 2) continue
                val dateStr = row[0]?.toString() ?: continue
                val amountStr = row[1]?.toString() ?: continue
                val amount = amountStr.toDoubleOrNull()?.toInt() ?: continue
                val splitCount = if (row.size >= 5) {
                    row[4]?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                } else 1
                result.add(Triple(dateStr, amount, splitCount))
            }
            result
        }
    }
}
