package com.example.spendmgr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.spendmgr.data.local.dao.CategoryHistoryDao
import com.example.spendmgr.data.local.dao.PendingExpensesDao
import com.example.spendmgr.data.local.entity.CategoryHistoryEntity
import com.example.spendmgr.data.local.entity.PendingExpenseEntity

@Database(
    entities = [CategoryHistoryEntity::class, PendingExpenseEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SpendMgrDatabase : RoomDatabase() {

    abstract fun categoryHistoryDao(): CategoryHistoryDao

    abstract fun pendingExpensesDao(): PendingExpensesDao

    companion object {
        @Volatile
        private var INSTANCE: SpendMgrDatabase? = null

        fun getInstance(context: Context): SpendMgrDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpendMgrDatabase::class.java,
                    "spendmgr_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE pending_expenses ADD COLUMN isCreditCard INTEGER NOT NULL DEFAULT 1"
                )
            }
        }
    }
}
