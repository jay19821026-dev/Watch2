package com.jaysato.xiaomihealthbridge

internal object HeartRateRestingReferencePolicy {
    enum class Status { BELOW_REFERENCE, WITHIN_REFERENCE, ABOVE_REFERENCE, UNKNOWN }

    data class Assessment(val status: Status, val label: String)

    fun assess(bpm: Double?, ageYears: Int?): Assessment {
        if (ageYears !in 18..100) {
            return Assessment(Status.UNKNOWN, "年齡未確認；不套用成人靜息參考")
        }
        if (bpm == null || !bpm.isFinite() || bpm !in 30.0..220.0) {
            return Assessment(Status.UNKNOWN, "讀值無效；參考狀態未知")
        }
        return when {
            bpm < 60.0 -> Assessment(Status.BELOW_REFERENCE, "低於成人靜息參考（60–100 bpm）")
            bpm > 100.0 -> Assessment(Status.ABOVE_REFERENCE, "高於成人靜息參考（60–100 bpm）")
            else -> Assessment(Status.WITHIN_REFERENCE, "在成人靜息參考範圍（60–100 bpm）")
        }
    }
}
