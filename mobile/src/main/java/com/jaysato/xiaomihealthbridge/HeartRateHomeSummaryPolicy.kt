package com.jaysato.xiaomihealthbridge

import java.time.Instant

internal object HeartRateHomeSummaryPolicy {
    data class Sample(val source: String, val time: Instant, val bpm: Double)

    data class Summary(
        val value: String,
        val detail: String,
        val trendValues: List<Double>,
        val measuredAt: Instant? = null
    )

    data class WatchCandidate(
        val bpm: Float,
        val measuredAtMillis: Long,
        val sourceKind: String,
        val timestampSource: String?,
        val status: String
    )

    fun resolve(
        samples: List<Sample>,
        aggregation: SourceIntervalAggregation.Assessment,
        now: Instant = Instant.now()
    ): Summary {
        val cutoff = now.minusMillis(HeartRatePresentationPolicy.HOME_HISTORY_WINDOW_MILLIS)
        val usable = samples.filter {
            it.source.isNotBlank() && it.bpm.isFinite() && it.bpm in 30.0..220.0 &&
                !it.time.isBefore(cutoff) && !it.time.isAfter(now)
        }
        val rejectedDetail = (samples.size - usable.size).takeIf { it > 0 }
            ?.let { " · 排除 $it 筆超出 24 小時、時間無效或數值超出 30–220 bpm 的讀值" }.orEmpty()
        if (usable.isEmpty()) return Summary("待量測", "近 24 小時沒有有效心率紀錄$rejectedDetail", emptyList())

        val latest = usable.maxBy { it.time }
        val trend = if (aggregation.eligible) usable.sortedBy { it.time }.map { it.bpm } else listOf(latest.bpm)
        val reason = when (aggregation.rejection) {
            SourceIntervalAggregation.Rejection.MULTIPLE_DATA_ORIGINS -> "多來源未合併"
            SourceIntervalAggregation.Rejection.OVERLAPPING_INTERVALS -> "區間重疊未合併"
            SourceIntervalAggregation.Rejection.INVALID_INTERVAL -> "時段無效，未合併"
            SourceIntervalAggregation.Rejection.INVALID_VALUE -> "部分數值無效，未合併"
            else -> "趨勢條件不足"
        }
        val trendDetail = if (aggregation.eligible) "24 小時趨勢 · ${trend.size} 筆" else "24 小時趨勢未合併（$reason）"
        return Summary(
            "${"%.0f".format(latest.bpm)}",
            "${latest.source} · 最近紀錄 ${HeartRatePresentationPolicy.homeTimestampLabel(latest.time.toEpochMilli(), now.toEpochMilli())} · $trendDetail · 活動狀態未知，不套用靜息高低範圍 · 訊號品質：來源未提供$rejectedDetail",
            trend,
            latest.time
        )
    }

    fun withRecentWatchReading(summary: Summary, candidates: List<WatchCandidate>, nowMillis: Long): Summary {
        val latest = candidates.mapNotNull { candidate ->
            if (candidate.status !in setOf("has_data", "valid_signal")) return@mapNotNull null
            if (!HealthAnalysisFreshnessPolicy.isFresh(
                    candidate.measuredAtMillis,
                    nowMillis,
                    HeartRatePresentationPolicy.WEAR_STATE_FRESHNESS_MILLIS
                )
            ) return@mapNotNull null
            val visibleBpm = when (candidate.sourceKind) {
                "whs_heart_rate" -> HeartRatePresentationPolicy.visibleHealthServicesBpm(
                    candidate.bpm,
                    candidate.measuredAtMillis,
                    candidate.timestampSource,
                    nowMillis,
                    HeartRatePresentationPolicy.WEAR_STATE_FRESHNESS_MILLIS
                )
                "heart_rate_raw" -> HeartRatePresentationPolicy.visibleRawSensorBpm(
                    candidate.bpm,
                    candidate.status,
                    candidate.measuredAtMillis,
                    candidate.timestampSource,
                    nowMillis
                )
                else -> null
            } ?: return@mapNotNull null
            candidate.copy(bpm = visibleBpm)
        }.maxByOrNull { it.measuredAtMillis } ?: return summary
        val detailTime = HeartRatePresentationPolicy.detailTimeDescription(
            latest.sourceKind,
            latest.measuredAtMillis,
            latest.timestampSource,
            nowMillis
        )
        val trendSourceNote = if (summary.measuredAt != null) {
            " · 圖表為 Health Connect 近 24 小時歷史趨勢，與手錶讀值分開"
        } else {
            ""
        }
        return Summary(
            "${latest.bpm.toInt()} bpm",
            "近 5 分鐘手錶讀值 · $detailTime$trendSourceNote · 活動狀態未知，不套用靜息高低範圍 · 訊號品質評分：來源未提供",
            if (summary.trendValues.isNotEmpty()) summary.trendValues else listOf(latest.bpm.toDouble()),
            Instant.ofEpochMilli(latest.measuredAtMillis)
        )
    }
}
