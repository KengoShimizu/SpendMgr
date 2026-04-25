package com.example.spendmgr.domain

import com.example.spendmgr.data.CategoryHistoryRepository

/**
 * 過去の入力履歴から前方一致でカテゴリ候補を提示するドメインサービス。
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4
 */
class CategorySuggestionEngine(
    private val categoryHistoryRepository: CategoryHistoryRepository
) {

    /**
     * 入力プレフィックスに前方一致するカテゴリ候補を返す。
     * プレフィックスが空の場合は空リストを返す。
     *
     * @param prefix ユーザーが入力した文字列
     * @return 前方一致するカテゴリのリスト（一致なしの場合は空リスト）
     */
    suspend fun suggest(prefix: String): List<String> {
        if (prefix.isEmpty()) return emptyList()
        return categoryHistoryRepository.searchByPrefix(prefix)
    }

    /**
     * カテゴリを履歴に記録する。
     * 経費記録成功時に呼び出す。
     *
     * @param category 記録するカテゴリ文字列
     */
    suspend fun recordCategory(category: String) {
        if (category.isNotBlank()) {
            categoryHistoryRepository.save(category)
        }
    }
}
