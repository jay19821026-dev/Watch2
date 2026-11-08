package com.jaysato.xiaomihealthbridge

internal object SensorFreshnessSummaryPolicy {
    fun displaySummary(runState: String, sessionStartedAt: Long, freshCount: Int, storedCount: Int): String = when {
        runState == "running" -> "手錶正在掃描，完成後可在下方查看各項結果。"
        sessionStartedAt <= 0L -> "尚未執行手錶掃描；點此開始。"
        freshCount > 0 -> "最近掃描已有資料；請在下方逐項查看讀值與說明。點此可重新掃描。"
        storedCount > 0 -> "有較早的資料，但目前沒有近期讀值；點此重新掃描。"
        else -> "這次沒有取得量測資料；確認佩戴後點此重新掃描。"
    }

    fun requiresAttention(
        freshCount: Int,
        staleCount: Int,
        sessionStartedAt: Long,
        timestampUnknownCount: Int = 0,
        futureTimestampCount: Int = 0
    ): Boolean = freshCount <= 0 || staleCount > 0 || timestampUnknownCount > 0 ||
        futureTimestampCount > 0 || sessionStartedAt <= 0L
}
