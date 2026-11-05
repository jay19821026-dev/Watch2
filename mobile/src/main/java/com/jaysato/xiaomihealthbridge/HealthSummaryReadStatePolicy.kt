package com.jaysato.xiaomihealthbridge

internal object HealthSummaryReadStatePolicy {
    data class CardState(
        val title: String,
        val value: String,
        val detail: String,
        val trend: String
    )

    private const val QUERY_FAILURE_DETAIL = "權限清單查詢失敗；本次未取得資料，前次摘要已清除"

    fun permissionQueryFailureCards(): Map<String, CardState> = failureCards(QUERY_FAILURE_DETAIL)

    fun dataQueryFailureCards(): Map<String, CardState> = failureCards("資料查詢失敗；本次未取得資料，前次摘要已清除")

    private fun failureCards(detail: String): Map<String, CardState> = mapOf(
        "weight" to card("體重", "kg"),
        "steps" to card("步數", "步"),
        "calories" to card("消耗的熱量", "大卡"),
        "sleep" to card("睡眠", "小時"),
        "heart_rate" to card("心率", "bpm"),
        "resting_heart_rate" to card("靜止心率", "bpm"),
        "oxygen" to card("血氧", "%"),
        "distance" to card("距離", "km"),
        "temperature" to card("體表溫度", "°C"),
        "elevation" to card("爬升", "m"),
        "floors" to card("樓層", "層"),
        "hydration" to card("補水", "mL")
    ).mapValues { (_, state) -> state.copy(detail = state.detail.replace(QUERY_FAILURE_DETAIL, detail)) }

    private fun card(title: String, unit: String) = CardState(
        title = title,
        value = "— —",
        detail = "$unit · $QUERY_FAILURE_DETAIL",
        trend = ""
    )
}
