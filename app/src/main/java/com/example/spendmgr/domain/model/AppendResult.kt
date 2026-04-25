package com.example.spendmgr.domain.model

data class AppendResult(
    val spreadsheetId: String,
    val sheetId: Int,       // シートのID（行削除APIで使用）
    val sheetName: String,
    val rowIndex: Int       // 追記された行のインデックス（始まり1）
)
