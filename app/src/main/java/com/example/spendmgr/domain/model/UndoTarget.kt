package com.example.spendmgr.domain.model

data class UndoTarget(
    val spreadsheetId: String,
    val sheetId: Int,
    val sheetName: String,
    val rowIndex: Int,
    val expense: ExpenseRecord
)
