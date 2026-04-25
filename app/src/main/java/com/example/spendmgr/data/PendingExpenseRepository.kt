package com.example.spendmgr.data

import com.example.spendmgr.domain.model.ExpenseRecord

interface PendingExpenseRepository {
    /**
     * 経費をローカルに保存し、自動生成されたIDを返す。
     * @return 保存されたレコードのID（Room autoGenerate）
     */
    suspend fun save(expense: ExpenseRecord): Long
    suspend fun getAll(): List<ExpenseRecord>
    suspend fun delete(id: Long)
}
