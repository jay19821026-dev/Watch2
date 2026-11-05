package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.EcgRawEventDecoder
import com.jaysato.xiaomihealthbridge.engine.EcgCandidateLayoutComparison

internal object EcgHalEventResearchDecoder {
    const val DECODE_STATE_NO_EVENTS = "NO_EVENTS"
    const val DECODE_STATE_EVENTS_PRESENT_ZERO_POINTS = "EVENTS_PRESENT_ZERO_POINTS"
    const val DECODE_STATE_POINTS_DECODED_UNVERIFIED = "POINTS_DECODED_UNVERIFIED"
    const val ENCODING = EcgRawEventDecoder.ENCODING
    const val CANDIDATE_FIELD_COUNT = EcgRawEventDecoder.CANDIDATE_FIELD_COUNT
    const val OBSERVED_ROW_WIDTH = EcgRawEventDecoder.OBSERVED_ROW_WIDTH

    data class Result(
        val waveform: List<Double>,
        val eventCount: Int,
        val estimatedSampleRateHz: Float?,
        val status: String,
        val missingSampleCount: Int = 0,
        val decodedSampleCount: Int = 0,
        val candidateValueCount: Int = 0,
        val inspectedFieldValueCount: Int = 0,
        val layoutComparison: EcgCandidateLayoutComparison? = null,
        val decodeState: String = DECODE_STATE_NO_EVENTS
    )

    fun decode(rawRows: String, eventTimestampsNs: List<Long>): Result {
        val rows = SensorSuiteCsvParser.parseRows(
            rawRows,
            EcgEventRowExportPolicy.MAX_EVENT_ROWS,
            SensorSuiteCsvParser.MAX_ECG_EVENT_ROW_CHARS
        )
        if (rows.isEmpty()) return Result(emptyList(), 0, null, "no_rows", decodeState = DECODE_STATE_NO_EVENTS)

        val result = EcgRawEventDecoder.decode(rows)
        return Result(
            waveform = emptyList(),
            eventCount = rows.size,
            estimatedSampleRateHz = null,
            status = result.status,
            missingSampleCount = 0,
            decodedSampleCount = 0,
            candidateValueCount = result.candidateValueCount,
            inspectedFieldValueCount = result.inspectedFieldValueCount,
            layoutComparison = result.layoutComparison,
            decodeState = DECODE_STATE_EVENTS_PRESENT_ZERO_POINTS
        )
    }
}
