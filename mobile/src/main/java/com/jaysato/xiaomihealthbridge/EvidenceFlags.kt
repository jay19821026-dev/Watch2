package com.jaysato.xiaomihealthbridge

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object EvidenceFlags {
    const val VERSION = 1

    fun build(
        sourceTimestamp: Long,
        publishedAt: Long,
        timestampSource: String?,
        reportedRate: Double?,
        rawAvailable: Boolean
    ): String {
        val sourceKnown = sourceTimestamp > 0L &&
            HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(timestampSource)
        val parts = mutableListOf(
            "raw=${if (rawAvailable) "available" else "missing"}",
            "source_time=${if (sourceKnown) "trusted" else "unconfirmed"}",
            "publish_time=${if (publishedAt > 0L) "available" else "missing"}",
            "sample_rate=${if (reportedRate?.isFinite() == true && reportedRate > 0.0) "reported" else "unknown"}",
            "received_date=${receivedDate(publishedAt.takeIf { it > 0L } ?: System.currentTimeMillis())}"
        )
        return parts.joinToString(";")
    }

    private fun receivedDate(timeMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timeMillis))
}
