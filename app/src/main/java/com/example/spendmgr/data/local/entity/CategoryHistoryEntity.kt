package com.example.spendmgr.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_history")
data class CategoryHistoryEntity(
    @PrimaryKey val category: String,
    val lastUsed: Long = System.currentTimeMillis()
)
