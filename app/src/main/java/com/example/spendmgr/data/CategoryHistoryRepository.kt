package com.example.spendmgr.data

interface CategoryHistoryRepository {
    suspend fun searchByPrefix(prefix: String): List<String>
    suspend fun save(category: String)
}
