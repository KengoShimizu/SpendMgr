package com.example.spendmgr.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_expenses")
data class PendingExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Int,
    val date: String,           // "yyyy-MM-dd" 形式
    val category: String,
    val isCreditCard: Boolean = true,
    val splitCount: Int = 1,    // 割り勘人数
    val createdAt: Long = System.currentTimeMillis()
)
