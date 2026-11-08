package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.jaysato.xiaomihealthbridge.engine.EcgRawEventDecoder

/** Publish a raw sensor snapshot from any dedicated wear page to the phone. */
object WearRawSensorPublisher {
    data class FormulaExtras(
        val piPercent: Double? = null,
        val relativeAcDcRatio: Double? = null,
        val pulseBpm: Double? = null,
        val ratioR: Double? = null,
        val rmssdMs: Double? = null,
        val nRmssd: Double? = null,
        val sdnnMs: Double? = null,
        val pnn50: Double? = null,
        val cvrr: Double? = null,
        val rrCount: Int? = null,
        val altitudeM: Double? = null,
        val pressureHpa: Double? = null,
        val stressScore: Int? = null,
        val note: String? = null,
        val citations: String? = null
    )

    fun publish(
        context: Context,
        snapshot: RawVitalsProbe.Snapshot,
        final: Boolean = true,
        chipPart: String? = null,
        featurePage: String? = null,
        formulas: FormulaExtras? = null
    ) {
        if (snapshot.samples <= 0 && !final) return
        val now = System.currentTimeMillis()
        val sequence = SnapshotSequence.next()
        val path = RawSensorDataItemPath.create(snapshot.kind.wireName, now)
        val mapRequest = PutDataMapRequest.create(path).apply {
            dataMap.putString("kind", snapshot.kind.wireName)
            dataMap.putString("label", snapshot.kind.label)
            dataMap.putString("sensor", snapshot.sensorName)
            dataMap.putString("vendor", snapshot.vendor)
            dataMap.putString("reporting_mode", snapshot.reportingMode)
            dataMap.putString(
                "summary",
                if (snapshot.kind == RawVitalsProbe.Kind.ECG && snapshot.eventCount > 0) {
                    "已收到 ECG Sensor API 事件；事件值格式未映射，不代表可解讀波形"
                } else snapshot.summary()
            )
            dataMap.putString("status", snapshot.status)
            dataMap.putString(
                "reason",
                if (snapshot.kind == RawVitalsProbe.Kind.ECG && snapshot.eventCount > 0) {
                    "保留未解碼 SensorEvent.values 預覽；不計算 RR、心率或 HRV"
                } else snapshot.reason
            )
            RawSensorCountContract.fields(
                snapshot.samples, snapshot.finiteValueCount, snapshot.waveform.size, snapshot.eventCount
            ).forEach(dataMap::putInt)
            dataMap.putInt("channels", snapshot.channels)
            dataMap.putInt("accuracy", snapshot.accuracy)
            dataMap.putDouble("sample_rate", snapshot.sampleRateHz)
            dataMap.putLong("session_started_at_ms", snapshot.sessionStartedAtEpochMs)
            dataMap.putLong("session_started_elapsed_realtime_ns", snapshot.sessionStartedElapsedRealtimeNs)
            dataMap.putLong("first_sample_at_ms", snapshot.firstSampleAtEpochMs)
            dataMap.putLong("last_sample_at_ms", snapshot.lastSampleAtEpochMs)
            dataMap.putLong("last_callback_received_at_ms", snapshot.lastCallbackReceivedAtEpochMs)
            dataMap.putString("last_sample_timestamp_source", snapshot.sampleTimestampSource)
            dataMap.putString("evidence_flags", snapshot.evidenceFlags().joinToString(","))
            dataMap.putInt("evidence_flags_version", 1)
            dataMap.putString("registration_result", snapshot.registrationResult)
            dataMap.putString("acquisition_stop_reason", snapshot.acquisitionStopReason)
            snapshot.minimum?.let { dataMap.putDouble("minimum", it) }
            snapshot.maximum?.let { dataMap.putDouble("maximum", it) }
            snapshot.average?.let { dataMap.putDouble("average", it) }
            dataMap.putString("latest", snapshot.latest.joinToString(","))
            dataMap.putBoolean("final", final)
            dataMap.putFloatArray("waveform", snapshot.waveform)
            if (final && WearSensorEventMetadataPolicy.supports(snapshot.kind)) {
                dataMap.putLongArray("event_timestamps_ns", snapshot.eventTimestampsNs)
                dataMap.putLongArray("event_timestamp_validity", snapshot.eventTimestampValidity.map(Int::toLong).toLongArray())
                dataMap.putLongArray("event_callback_elapsed_realtime_ns", snapshot.eventCallbackElapsedRealtimeNs)
                dataMap.putLongArray("event_sample_counts", snapshot.eventSampleCounts.map(Int::toLong).toLongArray())
                dataMap.putBoolean("hal_additional_info_supported", snapshot.halAdditionalInfoSupported)
                dataMap.putString("event_raw_values_rows", snapshot.eventRawValueRows.joinToString(";"))
                dataMap.putString("event_raw_values_status", snapshot.eventRawValuesStatus)
                snapshot.halSamplePeriodSeconds?.let { dataMap.putDouble("hal_sample_period_s", it) }
                snapshot.halSampleJitterSeconds?.let { dataMap.putDouble("hal_sample_jitter_s", it) }
            }
            dataMap.putString(
                "waveform_encoding",
                when {
                    snapshot.kind == RawVitalsProbe.Kind.ECG -> "hal_event_values_full_unmapped_preview"
                    snapshot.channels > 1 -> "hal_event_values_unmapped"
                    else -> "hal_scalar_values_unscaled"
                }
            )
            if (snapshot.kind == RawVitalsProbe.Kind.ECG) {
                dataMap.putString(
                    "sensor_profile_fingerprint",
                    EcgSensorProfileFingerprint.create(snapshot)
                )
            }
            snapshot.worn?.let { dataMap.putBoolean("worn", it) }
            chipPart?.let { dataMap.putString("chip_part", it) }
            featurePage?.let { dataMap.putString("feature_page", it) }
            if (snapshot.kind == RawVitalsProbe.Kind.PPG || snapshot.kind == RawVitalsProbe.Kind.MAX_HEART_RATE) {
                val check = ChipCalibrationProfiles.assessOpticalWaveform(snapshot.waveform.toList())
                dataMap.putBoolean("optical_valid", check.valid)
                dataMap.putDouble("optical_fill_ratio", check.fillRatio)
                dataMap.putFloat("optical_span", check.span)
                dataMap.putString("chip_calibration_note", check.advice)
            }
            formulas?.let { f ->
                f.piPercent?.let { dataMap.putDouble("formula_pi_percent", it) }
                f.relativeAcDcRatio?.takeIf(Double::isFinite)
                    ?.let { dataMap.putDouble("formula_relative_ac_dc", it) }
                f.pulseBpm?.let { dataMap.putDouble("formula_pulse_bpm", it) }
                f.ratioR?.let { dataMap.putDouble("formula_ratio_r", it) }
                f.rmssdMs?.let { dataMap.putDouble("formula_rmssd_ms", it) }
                f.nRmssd?.let { dataMap.putDouble("formula_nrmssd", it) }
                f.sdnnMs?.let { dataMap.putDouble("formula_sdnn_ms", it) }
                f.pnn50?.let { dataMap.putDouble("formula_pnn50", it) }
                f.cvrr?.let { dataMap.putDouble("formula_cvrr", it) }
                f.rrCount?.let { dataMap.putInt("formula_rr_count", it) }
                f.altitudeM?.let { dataMap.putDouble("formula_altitude_m", it) }
                f.pressureHpa?.let { dataMap.putDouble("formula_pressure_hpa", it) }
                f.stressScore?.let { dataMap.putInt("formula_stress_score", it) }
                f.note?.let { dataMap.putString("formula_note", it) }
                f.citations?.let { dataMap.putString("formula_citations", it) }
            }
            dataMap.putLong("timestamp", now)
            dataMap.putLong("producer_sequence", sequence)
            dataMap.putLong("producer_enqueue_requested_at_ms", now)
        }
        WearSerializedSensorPayloadGuard.compactOversizedSnapshot(mapRequest)
        val request = mapRequest.asPutDataRequest().let { request ->
            if (WearDataSyncPolicy.shouldMarkStandaloneSnapshotUrgent(final)) request.setUrgent() else request
        }
        val outboxId = if (final) {
            val (result, id) = WearSnapshotOutbox.enqueue(context, path, mapRequest.dataMap, request.isUrgent)
            if (result != WearSnapshotOutbox.StoreResult.STORED) {
                Log.e(TAG, "sensor_snapshot_outbox_${result.name.lowercase()} kind=${snapshot.kind.wireName}")
            }
            id
        } else null
        if (outboxId != null) WearSnapshotOutbox.scheduleContinuation(context)
        try {
            val publishTask = Wearable.getDataClient(context).putDataItem(request)
            if (final) {
                publishTask.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        outboxId?.let {
                            if (!WearSnapshotOutbox.remove(context, it)) {
                                WearSnapshotOutbox.scheduleContinuation(context)
                            }
                        }
                    } else {
                        Log.e(TAG, "sensor_snapshot_publish_failed kind=${snapshot.kind.wireName} samples=${snapshot.samples} error=${task.exception?.javaClass?.simpleName ?: "unknown"}")
                        WearSnapshotOutbox.scheduleContinuation(context)
                    }
                }
            } else {
                publishTask
                    .addOnSuccessListener {
                        Log.i(TAG, "sensor_snapshot_enqueued kind=${snapshot.kind.wireName} samples=${snapshot.samples} events=${snapshot.eventCount} final=false")
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "sensor_snapshot_enqueue_failed kind=${snapshot.kind.wireName} samples=${snapshot.samples} final=false", error)
                    }
            }
        } catch (error: Exception) {
            Log.e(TAG, "sensor_snapshot_publish_start_failed kind=${snapshot.kind.wireName} error=${error.javaClass.simpleName}")
            if (final) WearSnapshotOutbox.scheduleContinuation(context)
        }
    }

    fun chipFor(kind: RawVitalsProbe.Kind): String = when (kind) {
        RawVitalsProbe.Kind.PPG, RawVitalsProbe.Kind.ECG, RawVitalsProbe.Kind.BIOZ,
        RawVitalsProbe.Kind.BIOZ_RESPIRATION, RawVitalsProbe.Kind.SPO2,
        RawVitalsProbe.Kind.MAX_HEART_RATE, RawVitalsProbe.Kind.ADT,
        RawVitalsProbe.Kind.HARDWARE_ADT -> "MAX86178 channel family; watch BOM/config unverified"
        RawVitalsProbe.Kind.BODY_TEMPERATURE, RawVitalsProbe.Kind.BOARD_TEMPERATURE,
        RawVitalsProbe.Kind.THERMOMETER, RawVitalsProbe.Kind.TEMPERATURE_ALARM -> "型號未確認"
        RawVitalsProbe.Kind.ACCELEROMETER, RawVitalsProbe.Kind.ACCELEROMETER_UNCALIBRATED,
        RawVitalsProbe.Kind.GYROSCOPE, RawVitalsProbe.Kind.GYROSCOPE_UNCALIBRATED -> "BOSCH bmi26x family; SKU unverified"
        RawVitalsProbe.Kind.STEP_COUNTER, RawVitalsProbe.Kind.STEP_DETECTOR -> "Step sensor endpoint; vendor unverified"
        RawVitalsProbe.Kind.MAGNETOMETER, RawVitalsProbe.Kind.MAGNETOMETER_UNCALIBRATED ->
            "AKM ak0991x family; SKU unverified"
        RawVitalsProbe.Kind.ORIENTATION, RawVitalsProbe.Kind.ROTATION_VECTOR,
        RawVitalsProbe.Kind.GAME_ROTATION, RawVitalsProbe.Kind.GRAVITY,
        RawVitalsProbe.Kind.LINEAR_ACCELERATION, RawVitalsProbe.Kind.GEOMAGNETIC_ROTATION ->
            "Android fused/virtual sensor; components unverified"
        RawVitalsProbe.Kind.PRESSURE -> "TDK icp101xx family; SKU unverified"
        RawVitalsProbe.Kind.LIGHT -> "Lite-On ltr311 family; SKU unverified"
        RawVitalsProbe.Kind.LIFE_Q_OTT -> "LifeQ endpoint; device calibration unverified"
        RawVitalsProbe.Kind.HEART_RATE -> "Android heart-rate endpoint; producer per runtime metadata"
        RawVitalsProbe.Kind.OFF_BODY -> "Off-body logical endpoint; producer unverified"
        else -> "HAL"
    }

    private const val TAG = "WearRawSensorPublisher"
}

internal object EcgSensorProfileFingerprint {
    fun create(
        snapshot: RawVitalsProbe.Snapshot,
        decoderVersion: String = EcgRawEventDecoder.VERSION
    ): String {
        val profile = listOf(
            Build.MANUFACTURER,
            Build.MODEL,
            Build.DEVICE,
            Build.FINGERPRINT,
            snapshot.sensorName,
            snapshot.vendor,
            snapshot.reportingMode,
            snapshot.channels.toString(),
            snapshot.sampleRateHz.toString(),
            snapshot.halSamplePeriodSeconds?.toString() ?: "hal_sample_period_unknown",
            "hal_event_values_full_unmapped_preview",
            decoderVersion
        ).joinToString("\u0000")
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(profile.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
