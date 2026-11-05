package com.jaysato.xiaomihealthbridge.engine

data class RawSensorCounts(
    val samples: Int,
    val rawValueCount: Int?,
    val previewPointCount: Int?,
    val contractVersion: Int,
    val eventCount: Int?
)

object RawSensorCountContract {
    const val VERSION = 2

    fun encode(
        samples: Int,
        rawValueCount: Int?,
        previewPointCount: Int,
        eventCount: Int?
    ): Map<String, Int> = buildMap {
        put("samples", samples.coerceAtLeast(0))
        rawValueCount?.let { put("raw_value_count", it.coerceAtLeast(0)) }
        put("preview_point_count", previewPointCount.coerceAtLeast(0))
        put("count_contract_version", VERSION)
        eventCount?.let { put("event_count", it.coerceAtLeast(0)) }
    }

    fun decode(
        samples: Int?,
        rawValueCount: Int?,
        previewPointCount: Int?,
        contractVersion: Int?,
        eventCount: Int?
    ): RawSensorCounts = RawSensorCounts(
        samples = samples?.coerceAtLeast(0) ?: 0,
        rawValueCount = rawValueCount?.coerceAtLeast(0),
        previewPointCount = previewPointCount?.coerceAtLeast(0),
        contractVersion = contractVersion?.coerceAtLeast(0) ?: 0,
        eventCount = eventCount?.coerceAtLeast(0)
    )

    fun trustedEventCount(counts: RawSensorCounts): Int? =
        counts.eventCount?.takeIf { counts.contractVersion in 0..VERSION }
}
