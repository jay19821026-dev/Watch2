package com.jaysato.xiaomihealthbridge.engine

import kotlin.math.abs

data class EcgRawEventDecodeResult(
    val candidateValues: List<Double>,
    val eventCount: Int,
    val status: String,
    val candidateValueCount: Int = 0,
    val inspectedFieldValueCount: Int = 0,
    val layoutComparison: EcgCandidateLayoutComparison? = null
)

data class EcgCandidateLayoutComparison(
    val withinRowMedianAbsoluteDelta: Double?,
    val rowBoundaryMedianAbsoluteDelta: Double?,
    val rowMajorMedianAbsoluteDelta: Double?,
    val columnMajorMedianAbsoluteDelta: Double?,
    val counterColumnIndexHypothesis: Int,
    val counterPlusTenTransitions: Int,
    val counterResetTransitions: Int,
    val otherCounterTransitions: Int,
    val mappingVerified: Boolean = false,
    val fieldSummaries: List<EcgRawEventFieldSummary> = emptyList()
)

data class EcgRawEventFieldSummary(
    val fieldIndex: Int,
    val finiteValueCount: Int,
    val zeroValueCount: Int,
    val minimum: Double,
    val maximum: Double,
    val mean: Double,
    val changedTransitionCount: Int,
    val transitionCount: Int,
    val normalizedEventIndexPreview: List<Double?> = emptyList()
)

object EcgRawEventDecoder {
    const val VERSION = "ecg-raw-event-candidate-inspector-v4"
    const val ENCODING = "hal_event_values_unverified_candidate_inspection_v2"
    const val CANDIDATE_FIELD_COUNT = 10
    const val OBSERVED_ROW_WIDTH = 16
    const val MAX_EVENTS = 60_000
    const val MAX_ROW_CHARS = 512
    const val MAX_PREVIEW_POINTS = 512

    fun decode(rows: List<String>): EcgRawEventDecodeResult {
        if (rows.isEmpty()) return EcgRawEventDecodeResult(emptyList(), 0, "no_rows")
        if (rows.size > MAX_EVENTS) return EcgRawEventDecodeResult(emptyList(), 0, "event_limit_exceeded")
        val parsedRows = ArrayList<List<Double>>(rows.size)
        for (row in rows) {
            if (row.length > MAX_ROW_CHARS) return EcgRawEventDecodeResult(emptyList(), 0, "row_limit_exceeded")
            val fields = row.split(',')
            if (fields.size != OBSERVED_ROW_WIDTH) return EcgRawEventDecodeResult(emptyList(), 0, "row_shape_mismatch")
            val values = fields.map { it.trim().toDoubleOrNull() ?: return EcgRawEventDecodeResult(emptyList(), 0, "invalid_numeric_field") }
            if (values.any { !it.isFinite() }) return EcgRawEventDecodeResult(emptyList(), 0, "non_finite_field")
            parsedRows.add(values)
        }
        val candidates = parsedRows.flatMap { it.take(CANDIDATE_FIELD_COUNT) }
        val fieldMajorCandidates = (0 until CANDIDATE_FIELD_COUNT).flatMap { fieldIndex ->
            parsedRows.map { it[fieldIndex] }
        }
        val columnCounterDeltas = parsedRows.zipWithNext { previous, current -> current[11] - previous[11] }
        val fieldSummaries = (0 until OBSERVED_ROW_WIDTH).map { fieldIndex ->
            val values = parsedRows.map { it[fieldIndex] }
            EcgRawEventFieldSummary(
                fieldIndex = fieldIndex,
                finiteValueCount = values.size,
                zeroValueCount = values.count { it == 0.0 },
                minimum = values.minOrNull() ?: 0.0,
                maximum = values.maxOrNull() ?: 0.0,
                mean = values.average(),
                changedTransitionCount = values.zipWithNext().count { (previous, current) -> previous != current },
                transitionCount = (values.size - 1).coerceAtLeast(0),
                normalizedEventIndexPreview = normalizedPreview(values)
            )
        }
        val layout = EcgCandidateLayoutComparison(
            withinRowMedianAbsoluteDelta = medianAbsoluteDelta(parsedRows.flatMap { row ->
                row.take(CANDIDATE_FIELD_COUNT).zipWithNext { left, right -> right - left }
            }),
            rowBoundaryMedianAbsoluteDelta = medianAbsoluteDelta(parsedRows.zipWithNext { previous, current ->
                current.first() - previous[CANDIDATE_FIELD_COUNT - 1]
            }),
            rowMajorMedianAbsoluteDelta = medianAbsoluteDelta(candidates.zipWithNext { left, right -> right - left }),
            columnMajorMedianAbsoluteDelta = medianAbsoluteDelta(
                fieldMajorCandidates.zipWithNext { left, right -> right - left }
            ),
            counterColumnIndexHypothesis = 11,
            counterPlusTenTransitions = columnCounterDeltas.count { it == CANDIDATE_FIELD_COUNT.toDouble() },
            counterResetTransitions = columnCounterDeltas.count { it <= 0.0 },
            otherCounterTransitions = columnCounterDeltas.count { it > 0.0 && it != CANDIDATE_FIELD_COUNT.toDouble() },
            fieldSummaries = fieldSummaries
        )
        return EcgRawEventDecodeResult(
            candidates,
            rows.size,
            "candidate_values_unverified",
            candidates.size,
            parsedRows.size * OBSERVED_ROW_WIDTH,
            layout
        )
    }

    private fun medianAbsoluteDelta(deltas: List<Double>): Double? {
        if (deltas.isEmpty()) return null
        val sorted = deltas.map(::abs).sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun normalizedPreview(values: List<Double>): List<Double?> {
        if (values.isEmpty()) return emptyList()
        val minimum = values.minOrNull() ?: return emptyList()
        val maximum = values.maxOrNull() ?: return emptyList()
        val count = values.size.coerceAtMost(MAX_PREVIEW_POINTS)
        return List(count) { previewIndex ->
            val sourceIndex = if (count == 1) 0 else {
                (previewIndex.toLong() * (values.lastIndex) / (count - 1)).toInt()
            }
            if (maximum == minimum) 0.5 else (values[sourceIndex] - minimum) / (maximum - minimum)
        }
    }
}
