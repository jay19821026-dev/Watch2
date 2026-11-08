package com.jaysato.xiaomihealthbridge

import java.time.Instant

internal object HeartRateSampleTrendPolicy {
    data class Sample(val origin: String, val time: Instant, val bpm: Double)

    fun assess(samples: List<Sample>, start: Instant, end: Instant): SourceIntervalAggregation.Assessment {
        val inRange = samples.filter { !it.time.isBefore(start) && it.time.isBefore(end) }
        if (inRange.isEmpty()) return SourceIntervalAggregation.Assessment(
            eligible = false, rejection = SourceIntervalAggregation.Rejection.NO_RECORDS
        )
        val valid = inRange.filter { it.origin.isNotBlank() && it.bpm.isFinite() && it.bpm in 30.0..220.0 }
        if (valid.isEmpty()) return SourceIntervalAggregation.Assessment(
            eligible = false, rejection = SourceIntervalAggregation.Rejection.INVALID_VALUE
        )
        if (valid.map { it.origin }.distinct().size > 1) return SourceIntervalAggregation.Assessment(
            eligible = false, rejection = SourceIntervalAggregation.Rejection.MULTIPLE_DATA_ORIGINS
        )
        if (valid.map { it.time }.distinct().size != valid.size) return SourceIntervalAggregation.Assessment(
            eligible = false, rejection = SourceIntervalAggregation.Rejection.DUPLICATE_SAMPLE_TIMESTAMPS
        )
        return SourceIntervalAggregation.Assessment(eligible = true)
    }
}
