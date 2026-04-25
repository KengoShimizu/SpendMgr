package com.example.spendmgr.data

import com.example.spendmgr.data.local.dao.PendingExpensesDao
import com.example.spendmgr.data.local.entity.PendingExpenseEntity
import com.example.spendmgr.domain.model.ExpenseRecord
import java.time.LocalDate

class PendingExpenseRepositoryImpl(
    private val dao: PendingExpensesDao
) : PendingExpenseRepository {

    override suspend fun save(expense: ExpenseRecord): Long {
        val entity = PendingExpenseEntity(
            id = expense.id,
            amount = expense.amount,
            date = expense.date.toString(),  // LocalDate.toString() → "yyyy-MM-dd"
            category = expense.category,
            isCreditCard = expense.isCreditCard
        )
        return dao.save(entity)
    }

    override suspend fun getAll(): List<ExpenseRecord> {
        return dao.getAll().map { entity ->
            ExpenseRecord(
                id = entity.id,
                amount = entity.amount,
                date = LocalDate.parse(entity.date),  // "yyyy-MM-dd" → LocalDate
                category = entity.category,
                isCreditCard = entity.isCreditCard
            )
        }
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}
