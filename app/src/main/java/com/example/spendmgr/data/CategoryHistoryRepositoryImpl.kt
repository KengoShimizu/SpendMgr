package com.example.spendmgr.data

import com.example.spendmgr.data.local.dao.CategoryHistoryDao
import com.example.spendmgr.data.local.entity.CategoryHistoryEntity

class CategoryHistoryRepositoryImpl(
    private val dao: CategoryHistoryDao
) : CategoryHistoryRepository {

    override suspend fun searchByPrefix(prefix: String): List<String> {
        return dao.searchByPrefix(prefix).map { it.category }
    }

    override suspend fun save(category: String) {
        dao.save(CategoryHistoryEntity(category = category, lastUsed = System.currentTimeMillis()))
    }
}
