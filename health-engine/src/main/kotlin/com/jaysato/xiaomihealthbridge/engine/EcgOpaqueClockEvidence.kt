package com.jaysato.xiaomihealthbridge.engine

data class EcgOpaqueClockEvidence(
    val status: Status,
    val eventCadenceHz: Double?,
    val candidateCounterUnitsPerSecond: Double?,
    val validTransitionCount: Int,
    val duplicateTimestampCount: Int,
    val backwardTimestampCount: Int,
    val candidateCounterStepCount: Int,
    val candidateCounterDiscontinuityCount: Int
) {
    enum class Status {
        INSUFFICIENT_EVIDENCE,
        EVENT_TIMELINE_INVALID,
        ORDERED_EVENT_TIMELINE,
        CANDIDATE_COUNTER_OBSERVED
    }

    val adcSampleClockVerified: Boolean = false
}

object EcgOpaqueClockEvidenceAnalyzer {
    fun analyze(
        eventTimestampsNanos: List<Long>,
        rows: List<String>
    ): EcgOpaqueClockEvidence {
        if (eventTimestampsNanos.size < 2 || rows.size != eventTimestampsNanos.size) {
            return empty(EcgOpaqueClockEvidence.Status.INSUFFICIENT_EVIDENCE)
        }

        val timestampDeltas = eventTimestampsNanos.zipWithNext { previous, current -> current - previous }
        val duplicateCount = timestampDeltas.count { it == 0L }
        val backwardCount = timestampDeltas.count { it < 0L }
        val positiveDeltas = timestampDeltas.filter { it > 0L }
        if (positiveDeltas.isEmpty() || duplicateCount > 0 || backwardCount > 0) {
            return empty(
                EcgOpaqueClockEvidence.Status.EVENT_TIMELINE_INVALID,
                duplicateCount,
                backwardCount
            )
        }

        val durationNanos = eventTimestampsNanos.last() - eventTimestampsNanos.first()
        val eventCadenceHz = if (durationNanos > 0L) {
            (eventTimestampsNanos.size - 1) * 1_000_000_000.0 / durationNanos
        } else null

        val counters = rows.mapNotNull(::candidateCounter)
        if (counters.size != rows.size) {
            return EcgOpaqueClockEvidence(
                EcgOpaqueClockEvidence.Status.ORDERED_EVENT_TIMELINE,
                eventCadenceHz,
                null,
                positiveDeltas.size,
                duplicateCount,
                backwardCount,
                0,
                0
            )
        }

        val counterDeltas = counters.zipWithNext { previous, current -> current - previous }
        val expectedSteps = counterDeltas.count { it == EcgRawEventDecoder.CANDIDATE_FIELD_COUNT.toLong() }
        val discontinuities = counterDeltas.size - expectedSteps
        val counterRate = if (durationNanos > 0L && counters.last() > counters.first()) {
            (counters.last() - counters.first()) * 1_000_000_000.0 / durationNanos
        } else null
        val status = if (expectedSteps > 0 && discontinuities == 0) {
            EcgOpaqueClockEvidence.Status.CANDIDATE_COUNTER_OBSERVED
        } else {
            EcgOpaqueClockEvidence.Status.ORDERED_EVENT_TIMELINE
        }
        return EcgOpaqueClockEvidence(
            status,
            eventCadenceHz,
            counterRate,
            positiveDeltas.size,
            duplicateCount,
            backwardCount,
            expectedSteps,
            discontinuities
        )
    }

    private fun candidateCounter(row: String): Long? {
        if (row.length > EcgRawEventDecoder.MAX_ROW_CHARS) return null
        val fields = row.split(',')
        if (fields.size != EcgRawEventDecoder.OBSERVED_ROW_WIDTH) return null
        val value = fields[11].trim().toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        val integer = value.toLong()
        return integer.takeIf { value == integer.toDouble() && integer in -(1L shl 53)..(1L shl 53) }
    }

    private fun empty(
        status: EcgOpaqueClockEvidence.Status,
        duplicateCount: Int = 0,
        backwardCount: Int = 0
    ) = EcgOpaqueClockEvidence(
        status,
        null,
        null,
        0,
        duplicateCount,
        backwardCount,
        0,
        0
    )
}
