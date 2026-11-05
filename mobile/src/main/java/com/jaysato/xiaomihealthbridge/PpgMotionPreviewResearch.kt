package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.PpgMotionEvidence
import com.jaysato.xiaomihealthbridge.engine.PpgMotionEvidenceEngine
import com.jaysato.xiaomihealthbridge.engine.TimedAccelerationObservation
import com.jaysato.xiaomihealthbridge.engine.TimedPpgObservation

data class PpgMotionPreviewReport(
    val status: String,
    val ppgObservationCount: Int,
    val motionEventCount: Int,
    val evidence: PpgMotionEvidence?
)

object PpgMotionPreviewResearch {
    const val CLOCK_DOMAIN = "wear_sensor_event_timestamp_elapsed_realtime_nanos"

    fun analyze(
        sessionId: String,
        ppgRows: List<RawSensorSampleEntity>,
        previewPointEventTimestampsNanos: List<Long>,
        previewPointTimestampValidity: List<Int>,
        ppgClockDomain: String,
        motionClockDomain: String,
        sameSession: Boolean,
        sameMonotonicClock: Boolean,
        maximumPairSkewNanos: Long,
        captureStatus: String,
        arrayAlignmentValid: Boolean,
        motionTimestampsNanos: List<Long>,
        motionX: List<Double>,
        motionY: List<Double>,
        motionZ: List<Double>
    ): PpgMotionPreviewReport {
        val motionCount = maxOf(motionTimestampsNanos.size, motionX.size, motionY.size, motionZ.size)
        val reason = when {
            sessionId.isBlank() || ppgRows.any { it.sessionId != sessionId } || !sameSession -> "SESSION_MISMATCH"
            ppgClockDomain != CLOCK_DOMAIN || motionClockDomain != CLOCK_DOMAIN || !sameMonotonicClock ->
                "COMMON_MONOTONIC_CLOCK_UNCONFIRMED"
            captureStatus != "captured" -> when (captureStatus) {
                "not_captured", "no_events", "sensor_not_exposed", "registration_failed" -> "MOTION_CAPTURE_$captureStatus"
                else -> "MOTION_CAPTURE_STATUS_UNRECOGNIZED"
            }
            !arrayAlignmentValid || motionTimestampsNanos.size != motionX.size ||
                motionX.size != motionY.size || motionY.size != motionZ.size -> "MOTION_ARRAY_ALIGNMENT_INVALID"
            previewPointEventTimestampsNanos.size != ppgRows.size ||
                previewPointTimestampValidity.isNotEmpty() && previewPointTimestampValidity.size != ppgRows.size ||
                previewPointTimestampValidity.any { it !in 0..1 } ->
                "PPG_TIMESTAMP_MAPPING_UNAVAILABLE"
            !ppgRows.indices.all { index ->
                val timestamp = previewPointEventTimestampsNanos[index]
                val valid = previewPointTimestampValidity.isEmpty() || previewPointTimestampValidity[index] == 1
                ppgRows[index].sampleIndex == index &&
                    ppgRows[index].eventIndex == index &&
                    ppgRows[index].sourceTimestampNanos == timestamp.takeIf { valid && it > 0L } &&
                    ppgRows[index].timestampSource == if (valid && timestamp > 0L) {
                        "SENSOR_EVENT_UNVERIFIED_CLOCK"
                    } else "UNKNOWN"
            } -> "PPG_TIMESTAMP_MAPPING_MISMATCH"
            ppgRows.any { it.timestampSource != "SENSOR_EVENT_UNVERIFIED_CLOCK" || it.sourceTimestampNanos == null } ->
                "PPG_SOURCE_TIMESTAMPS_UNAVAILABLE"
            ppgRows.any { it.rawEncoding.toDoubleOrNull()?.isFinite() != true } -> "PPG_VALUES_INVALID"
            else -> null
        }
        if (reason != null) return PpgMotionPreviewReport(reason, ppgRows.size, motionCount, null)

        val ppg = ppgRows.map { row ->
            TimedPpgObservation(row.sourceTimestampNanos!!, row.rawEncoding.toDouble())
        }
        val acceleration = motionTimestampsNanos.indices.map { index ->
            TimedAccelerationObservation(motionTimestampsNanos[index], motionX[index], motionY[index], motionZ[index])
        }
        val evidence = PpgMotionEvidenceEngine.evaluate(
            ppg,
            acceleration,
            sameSession = true,
            sameMonotonicClock = true,
            maximumPairSkewNanos = maximumPairSkewNanos
        )
        return PpgMotionPreviewReport(evidence.reasonCode, ppgRows.size, motionCount, evidence)
    }
}
