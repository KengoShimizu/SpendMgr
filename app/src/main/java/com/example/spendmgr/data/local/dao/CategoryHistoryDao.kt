package com.example.spendmgr.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spendmgr.data.local.entity.CategoryHistoryEntity

@Dao
interface CategoryHistoryDao {

    /**
     * プレフィックスで前方一致検索し、最終使用日時の降順で返す。
     */
    @Query("SELECT * FROM category_history WHERE category LIKE :prefix || '%' ORDER BY lastUsed DESC")
    suspend fun searchByPrefix(prefix: String): List<CategoryHistoryEntity>

    /**
     * カテゴリを保存または更新する（同一カテゴリは上書き）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: CategoryHistoryEntity)
}
