package com.example.spendmgr.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.spendmgr.data.local.entity.PendingExpenseEntity

@Dao
interface PendingExpensesDao {

    /**
     * 未送信の経費をローカルに保存する。
     * @return 自動生成されたID
     */
    @Insert
    suspend fun save(entity: PendingExpenseEntity): Long

    /**
     * 未送信の経費をすべて取得する。
     */
    @Query("SELECT * FROM pending_expenses ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingExpenseEntity>

    /**
     * 指定IDの未送信経費を削除する（送信成功後に呼び出す）。
     */
    @Query("DELETE FROM pending_expenses WHERE id = :id")
    suspend fun deleteById(id: Long)
}
