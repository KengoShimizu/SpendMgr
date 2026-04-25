package com.example.spendmgr.domain.model

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class CategoryData(
    val name: String,
    val amount: Int,
    val percentage: Float,  // 0.0 〜 100.0
    val color: Color        // 表示用。DataStore には保存せず、復元時に CategoryColorAssigner で再計算する
)

data class PieChartData(
    val categories: List<CategoryData>,
    val totalAmount: Int,
    val creditCardTotal: Int? = null  // カード払い合計（家計立替）
) {
    /**
     * JSON 形式にシリアライズする。
     * color フィールドは保存しない（復元時に CategoryColorAssigner で再計算する）。
     */
    fun toJson(): String {
        val root = JSONObject()
        root.put("totalAmount", totalAmount)
        if (creditCardTotal != null) root.put("creditCardTotal", creditCardTotal)
        val array = JSONArray()
        for (cat in categories) {
            val obj = JSONObject()
            obj.put("name", cat.name)
            obj.put("amount", cat.amount)
            obj.put("percentage", cat.percentage.toDouble())
            array.put(obj)
        }
        root.put("categories", array)
        return root.toString()
    }

    companion object {
        /**
         * JSON 文字列からデシリアライズする。
         * color フィールドは Color.Unspecified をプレースホルダーとして設定する。
         * （復元後に CategoryColorAssigner.colorFor(name) で再計算すること）
         */
        fun fromJson(json: String): PieChartData? {
            return try {
                val root = JSONObject(json)
                val totalAmount = root.getInt("totalAmount")
                val creditCardTotal = if (root.has("creditCardTotal")) root.getInt("creditCardTotal") else null
                val array = root.getJSONArray("categories")
                val categories = mutableListOf<CategoryData>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val name = obj.getString("name")
                    val amount = obj.getInt("amount")
                    val percentage = obj.getDouble("percentage").toFloat()
                    categories.add(
                        CategoryData(
                            name = name,
                            amount = amount,
                            percentage = percentage,
                            color = Color.Unspecified
                        )
                    )
                }
                PieChartData(categories = categories, totalAmount = totalAmount, creditCardTotal = creditCardTotal)
            } catch (e: Exception) {
                null
            }
        }
    }
}
