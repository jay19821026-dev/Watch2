package com.jaysato.xiaomihealthbridge

internal object PpgArchiveInputPolicy {
    data class PpgPointTime(
        val sourceTimestampNanos: Long?,
        val callbackTimestampNanos: Long?,
        val timestampSource: String,
        val eventIndex: Int
    )

    fun eventSampleCount(value: Long): Int {
        require(value in 0L..Int.MAX_VALUE.toLong()) { "Event sample count outside supported range" }
        return value.toInt()
    }

    fun validateMetadata(encoding: String?, timestampSource: String?) {
        require(encoding == null || encoding.length <= 256) { "Source encoding too long" }
        require(timestampSource == null || timestampSource.length <= 64) { "Timestamp source too long" }
    }

    fun mapAlignedPpgPreviewTimes(
        valueCount: Int,
        eventTimestamps: List<Long>,
        eventTimestampValidity: List<Int>,
        callbackTimestamps: List<Long>
    ): List<PpgPointTime> {
        require(valueCount >= 0 && valueCount <= 100_000)
        require(eventTimestamps.size <= 100_000 && callbackTimestamps.size <= 100_000)
        require(eventTimestampValidity.isEmpty() || eventTimestampValidity.size == eventTimestamps.size)
        require(eventTimestampValidity.all { it in 0..1 })
        val points = MutableList(valueCount) { PpgPointTime(null, null, "UNKNOWN", -1) }
        if (eventTimestamps.isEmpty() || valueCount != eventTimestamps.size ||
            (eventTimestampValidity.isNotEmpty() && eventTimestampValidity.size != valueCount)
        ) return points
        val callbacksAligned = callbackTimestamps.size == eventTimestamps.size
        var lastTrustedSourceTimestamp = 0L
        eventTimestamps.indices.forEach { eventIndex ->
            val timestamp = eventTimestamps[eventIndex]
            val hasSourceTime = (eventTimestampValidity.isEmpty() || eventTimestampValidity[eventIndex] == 1) &&
                timestamp > lastTrustedSourceTimestamp
            if (hasSourceTime) lastTrustedSourceTimestamp = timestamp
            points[eventIndex] = PpgPointTime(
                sourceTimestampNanos = timestamp.takeIf { hasSourceTime },
                callbackTimestampNanos = callbackTimestamps.getOrNull(eventIndex)
                    ?.takeIf { callbacksAligned && it > 0L },
                timestampSource = if (hasSourceTime) "SENSOR_EVENT_UNVERIFIED_CLOCK" else "UNKNOWN",
                eventIndex = eventIndex
            )
        }
        return points
    }

    fun mapSingleSamplePerEventTimes(
        valueCount: Int,
        eventTimestamps: List<Long>,
        eventTimestampValidity: List<Int>,
        callbackTimestamps: List<Long>,
        eventSampleCounts: List<Int>
    ): List<PpgPointTime> {
        require(valueCount >= 0 && valueCount <= 100_000)
        require(eventTimestamps.size <= 100_000 && callbackTimestamps.size <= 100_000)
        require(eventSampleCounts.size <= 100_000 && eventSampleCounts.all { it >= 0 })
        require(eventTimestampValidity.isEmpty() || eventTimestampValidity.size == eventTimestamps.size)
        require(eventTimestampValidity.all { it in 0..1 })
        val points = MutableList(valueCount) { PpgPointTime(null, null, "UNKNOWN", -1) }
        if (valueCount != eventTimestamps.size || valueCount != eventSampleCounts.size ||
            eventSampleCounts.any { it != 1 } ||
            (eventTimestampValidity.isNotEmpty() && eventTimestampValidity.size != valueCount)
        ) return points
        val callbacksAligned = callbackTimestamps.size == valueCount
        var lastTrustedSourceTimestamp = 0L
        eventTimestamps.indices.forEach { eventIndex ->
            val timestamp = eventTimestamps[eventIndex]
            val hasSourceTime = (eventTimestampValidity.isEmpty() || eventTimestampValidity[eventIndex] == 1) &&
                timestamp > lastTrustedSourceTimestamp
            if (hasSourceTime) lastTrustedSourceTimestamp = timestamp
            points[eventIndex] = PpgPointTime(
                sourceTimestampNanos = timestamp.takeIf { hasSourceTime },
                callbackTimestampNanos = callbackTimestamps.getOrNull(eventIndex)
                    ?.takeIf { callbacksAligned && it > 0L },
                timestampSource = if (hasSourceTime) "SENSOR_EVENT_UNVERIFIED_CLOCK" else "UNKNOWN",
                eventIndex = eventIndex
            )
        }
        return points
    }
}
