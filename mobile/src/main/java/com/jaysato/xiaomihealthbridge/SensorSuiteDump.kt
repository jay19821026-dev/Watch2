package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

internal object SensorSuiteExportPolicy {
    data class EventCountExport(val standardCount: Int?, val reportedCount: Int?, val isKnown: Boolean)
    data class SampleRateExport(val sampleRateHz: Double?, val source: String)
    data class EventRateExport(val eventsPerSessionSecond: Double?, val source: String)
    data class JitterExport(val standardDeviationSeconds: Double?, val periodRatio: Double?, val relation: String)

    fun eventCountExport(normalizedCount: Int, reportedCount: Int?, known: Boolean): EventCountExport =
        if (known) {
            val count = normalizedCount.coerceAtLeast(0)
            EventCountExport(count, reportedCount?.coerceAtLeast(0) ?: count, true)
        } else {
            EventCountExport(null, reportedCount?.coerceAtLeast(0), false)
        }

    fun countsAsRawEvent(eventCount: Int, provenance: String, status: String): Boolean =
        eventCount > 0 && provenance == "hardware_event" &&
            status !in setOf("derived_estimate", "not_measured", "skipped_charging")

    fun hasRawData(samples: Int, provenance: String, status: String): Boolean =
        samples > 0 && provenance !in setOf("derived_estimate", "not_measured") &&
            status !in setOf("derived_estimate", "not_measured", "skipped_charging")

    fun sampleRateExport(reportedRateHz: Double?, halPeriodSeconds: Double?, reportingMode: String): SampleRateExport {
        if (reportingMode == "derived") return SampleRateExport(null, "not_applicable_derived")
        val period = halPeriodSeconds ?: return SampleRateExport(null, "unreported")
        if (!period.isFinite() || period !in 0.0001..1.0) return SampleRateExport(null, "invalid_hal_sample_period")
        val rate = reportedRateHz ?: return SampleRateExport(null, "hal_period_without_sample_rate")
        if (!rate.isFinite() || rate <= 0.0) return SampleRateExport(null, "invalid_reported_sample_rate")
        val expectedRate = 1.0 / period
        if (kotlin.math.abs(rate - expectedRate) / expectedRate > MAX_SAMPLE_RATE_RELATIVE_ERROR) {
            return SampleRateExport(null, "sample_rate_period_mismatch")
        }
        return SampleRateExport(rate, "sensor_hal_type_sampling")
    }

    fun eventRateExport(rate: Double?, source: String?): EventRateExport {
        if (rate == null) return EventRateExport(null, "unreported")
        if (!rate.isFinite() || rate < 0.0) return EventRateExport(null, "invalid_event_rate")
        if (source != EVENT_RATE_SOURCE) return EventRateExport(null, "unsupported_event_rate_source")
        return EventRateExport(rate, source)
    }

    fun jitterExport(periodSeconds: Double?, jitterSeconds: Double?): JitterExport {
        if (periodSeconds == null && jitterSeconds == null) return JitterExport(null, null, "unreported")
        val period = periodSeconds?.takeIf { it.isFinite() && it in 0.0001..1.0 }
            ?: return JitterExport(null, null, "invalid_or_missing_sample_period")
        val jitter = jitterSeconds?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return JitterExport(null, null, "invalid_or_missing_jitter_standard_deviation")
        val ratio = jitter / period
        val relation = when {
            ratio == 0.0 -> "reported_zero_standard_deviation"
            ratio <= 1.0 -> "standard_deviation_at_or_below_mean_period"
            else -> "standard_deviation_above_mean_period"
        }
        return JitterExport(jitter, ratio, relation)
    }

    fun countsAsFresh(state: SensorDisplayState): Boolean =
        state == SensorDisplayState.AVAILABLE || state == SensorDisplayState.SIGNAL_VALID_RESEARCH

    fun countsAsAcceptedQuality(state: SensorDisplayState, qualityScore: Int): Boolean =
        state == SensorDisplayState.SIGNAL_VALID_RESEARCH && qualityScore >= 50

    fun countsAsFreshRawData(hasRawData: Boolean, state: SensorDisplayState): Boolean =
        hasRawData && state !in setOf(
            SensorDisplayState.STALE,
            SensorDisplayState.FUTURE_TIMESTAMP,
            SensorDisplayState.TIMESTAMP_UNKNOWN
        )

    fun freshnessReason(
        state: SensorDisplayState,
        measuredAt: Long,
        kind: String,
        timestampSource: String?
    ): String = when (state) {
        SensorDisplayState.FUTURE_TIMESTAMP -> "future_timestamp"
        SensorDisplayState.STALE -> "expired"
        SensorDisplayState.TIMESTAMP_UNKNOWN -> when {
            !SensorDisplayState.hasTrustedTimestamp(kind, timestampSource) -> "timestamp_source_untrusted"
            measuredAt < 0L -> "invalid_timestamp"
            else -> "missing_timestamp"
        }
        SensorDisplayState.NOT_MEASURED -> "not_measured"
        SensorDisplayState.CONDITION_NOT_MET -> "measurement_conditions_not_met"
        else -> "within_window"
    }

    private const val MAX_SAMPLE_RATE_RELATIVE_ERROR = 0.01
    private const val EVENT_RATE_SOURCE = "event_count_over_elapsed_realtime_duration"
}

object SensorSuiteDump {
    const val FILE_NAME = "sensor-suite-latest.json"
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    private val partialCleanup = SensorSuitePartialCleanupScheduler(
        enqueue = { writer.execute(it) },
        cleanup = { directory -> cleanupPartials(directory) }
    )
    private var pendingContext: Context? = null
    private var writeScheduled = false
    private var writing = false
    private var dirtyWhileWriting = false
    private val writeTask = Runnable { dispatchWrite() }

    fun requestPartialCleanup(context: Context) {
        runCatching { partialCleanup.request(File(context.applicationContext.cacheDir, "exports")) }
            .onFailure { Log.w("SensorSuiteDump", "Partial cleanup scheduling failed", it) }
    }

    private fun cleanupPartials(directory: File) {
        val failures = SensorSuiteDumpRetention.cleanupExpiredPartials(directory, System.currentTimeMillis())
        if (failures > 0) Log.w("SensorSuiteDump", "Partial export cleanup failed count=$failures")
    }

    fun write(context: Context) {
        synchronized(lock) {
            pendingContext = context.applicationContext
            if (writing) {
                dirtyWhileWriting = true
                return
            }
            handler.removeCallbacks(writeTask)
            writeScheduled = true
            handler.postDelayed(writeTask, 500L)
        }
    }

    private fun dispatchWrite() {
        val context = synchronized(lock) {
            writeScheduled = false
            val appContext = pendingContext ?: return
            writing = true
            appContext
        }
        writer.execute {
            try {
                runCatching { writeNow(context) }
                    .onFailure { Log.e("SensorSuiteDump", "Sensor export failed", it) }
            } finally {
                synchronized(lock) {
                    writing = false
                    if (dirtyWhileWriting) {
                        dirtyWhileWriting = false
                        writeScheduled = true
                        handler.postDelayed(writeTask, 500L)
                    }
                }
            }
        }
    }

    private fun writeNow(context: Context) {
        val raw = RawSensorStore.open(context)
        val feature = context.getSharedPreferences(WearDataProcessor.FEATURE_PREFS, Context.MODE_PRIVATE)
        val control = context.getSharedPreferences(HeartRateDataListenerService.PREFS, Context.MODE_PRIVATE)
        val acquisition = context.getSharedPreferences("sensor_acquisition_diagnostics", Context.MODE_PRIVATE)
        val exportedAt = System.currentTimeMillis()
        val controlStatus = control.getString("control_status", "").orEmpty()
        val controlStatusTime = control.getLong("control_status_time", 0L)
        val controlStatusStale = ControlStatusFreshness.isStale(
            controlStatusTime, exportedAt, SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS
        )
        val latestSuiteStartedAt = raw.getLong(SensorSuiteSessionPolicy.SESSION_KEY, 0L)
        val latestSuiteId = raw.getString(SensorSuiteSessionPolicy.SUITE_ID_KEY, "").orEmpty()
        val root = JSONObject().apply {
            put("schema", "xiaomi-health-bridge.sensor-suite.v5")
            put("exported_at_epoch_ms", exportedAt)
            put("device", "Xiaomi Watch 2 Pro")
            put("medical_status", "Research/raw sensor data only; not a medical diagnosis")
            put("limitations", JSONArray(listOf(
                "ECG lacks lead and clinical calibration",
                "BIOZ has no OEM impedance conversion formula",
                "PPG quality analysis is not disease diagnosis",
                "AF screening needs validated RR intervals and clinical validation"
            )))
            put("control_status", if (controlStatusStale && controlStatus.isNotBlank()) "上次控制回報已過期" else controlStatus)
            put("control_status_raw", controlStatus)
            put("control_status_time", controlStatusTime)
            put("control_status_age_ms", if (controlStatusTime > 0L) exportedAt - controlStatusTime else JSONObject.NULL)
            put("control_status_is_stale", controlStatusStale)
            put("sensor_suite_session_started_at_epoch_ms", latestSuiteStartedAt)
            put("sensor_suite_session_id", latestSuiteId)
            put("sensor_suite_run_state", SensorSuiteSessionPolicy.displayRunState(
                raw.getString(SensorSuiteSessionPolicy.RUN_STATE_KEY, "unknown").orEmpty(),
                latestSuiteStartedAt,
                exportedAt
            ))
            put("quality_count_scope", "fresh_signal_valid_research_samples_with_quality_score_at_least_50")
            put("sync_diagnostics", JSONObject().apply {
                put("raw_snapshot_rejection_count", control.getInt(WearDataProcessor.KEY_RAW_SNAPSHOT_REJECTION_COUNT, 0))
                put("raw_snapshot_rejection_count_scope", "final_snapshots_only_including_expected_feature_raw_overlap")
                put("raw_snapshot_duplicate_rejection_count_semantics", "duplicate_delivery_or_timestamp_collision_not_unique_measurement_loss")
                put("raw_snapshot_ordering_rejection_count", control.getInt(WearDataProcessor.KEY_RAW_SNAPSHOT_ORDERING_REJECTION_COUNT, 0))
                put("raw_snapshot_payload_rejection_count", control.getInt(WearDataProcessor.KEY_RAW_SNAPSHOT_PAYLOAD_REJECTION_COUNT, 0))
                put("raw_snapshot_duplicate_rejection_count", control.getInt(WearDataProcessor.KEY_RAW_SNAPSHOT_DUPLICATE_REJECTION_COUNT, 0))
                put("last_rejected_kind", control.getString(WearDataProcessor.KEY_RAW_SNAPSHOT_LAST_REJECTED_KIND, ""))
                put("last_rejection_reason", control.getString(WearDataProcessor.KEY_RAW_SNAPSHOT_LAST_REJECTION_REASON, ""))
                put("last_rejection_epoch_ms", control.getLong(WearDataProcessor.KEY_RAW_SNAPSHOT_LAST_REJECTION_TIME, 0L))
                val rejectedKind = control.getString(WearDataProcessor.KEY_RAW_SNAPSHOT_LAST_REJECTED_KIND, "").orEmpty()
                put("last_rejected_payload_serialized_bytes", control.getInt("${rejectedKind}_last_rejected_payload_serialized_bytes", 0))
                put("last_rejected_sample_at_ms", control.getLong("${rejectedKind}_last_rejected_sample_at_ms", 0L))
                put("last_rejected_timestamp_source", control.getString("${rejectedKind}_last_rejected_timestamp_source", ""))
                put("last_rejected_event_count", control.getInt("${rejectedKind}_last_rejected_event_count", 0))
                put("last_rejected_sample_count", control.getInt("${rejectedKind}_last_rejected_sample_count", 0))
                put("last_rejected_producer_sequence", control.getLong("${rejectedKind}_last_rejected_producer_sequence", 0L))
                put("last_rejected_session_started_at_ms", control.getLong("${rejectedKind}_last_rejected_session_started_at_ms", 0L))
                put("last_rejected_published_at_ms", control.getLong("${rejectedKind}_last_rejected_published_at_ms", 0L))
                put("last_rejected_enqueue_requested_at_ms", control.getLong("${rejectedKind}_last_rejected_enqueue_requested_at_ms", 0L))
                put("last_rejected_payload_timestamp_source", control.getString("${rejectedKind}_last_rejected_payload_timestamp_source", ""))
            })
            put("feature_snapshot", JSONObject().apply {
                put("timestamp_epoch_ms", feature.getLong("timestamp", 0L))
                if (feature.contains("worn")) {
                    val timestamp = feature.getLong("worn_timestamp", 0L)
                    val source = feature.getString("worn_timestamp_source", null)
                    val state = FeatureSnapshotExportPolicy.freshnessState(
                        timestamp, source, exportedAt, HeartRatePresentationPolicy.WEAR_STATE_FRESHNESS_MILLIS
                    )
                    put("worn_state", state)
                    put("worn", if (state == "fresh") feature.getBoolean("worn", false) else JSONObject.NULL)
                    put("worn_timestamp_epoch_ms", timestamp.takeIf { it > 0L } ?: JSONObject.NULL)
                    put("worn_timestamp_source", source ?: "timestamp_source_unreported")
                    put("worn_age_ms", if (timestamp > 0L) exportedAt - timestamp else JSONObject.NULL)
                }
                if (feature.getBoolean("worn", false) && feature.contains("whs_heart_rate")) {
                    val timestamp = feature.getLong("whs_heart_rate_timestamp", 0L)
                    val source = feature.getString("whs_heart_rate_timestamp_source", null)
                    val state = FeatureSnapshotExportPolicy.measurementFreshnessState(
                        timestamp, source, exportedAt, HealthAnalysisFreshnessPolicy.RECENT_SENSOR_INPUT_MILLIS
                    )
                    put("whs_heart_rate_state", state)
                    put("whs_heart_rate_bpm", if (state == "fresh") feature.getFloat("whs_heart_rate", 0f).toDouble() else JSONObject.NULL)
                    put("whs_heart_rate_timestamp_epoch_ms", timestamp.takeIf { it > 0L } ?: JSONObject.NULL)
                    put("whs_heart_rate_timestamp_source", source ?: "timestamp_source_unreported")
                    put("whs_heart_rate_age_ms", if (timestamp > 0L) exportedAt - timestamp else JSONObject.NULL)
                }
                if (feature.contains("temperature")) {
                    val temperatureTimestamp = feature.getLong("temperature_timestamp", 0L)
                    val temperatureSource = feature.getString("temperature_timestamp_source", null)
                    val temperatureState = FeatureSnapshotExportPolicy.freshnessState(
                        temperatureTimestamp, temperatureSource, exportedAt
                    )
                    put("temperature_state", temperatureState)
                    put("temperature_timestamp_epoch_ms", temperatureTimestamp.takeIf { it > 0L } ?: JSONObject.NULL)
                    put("temperature_timestamp_source", temperatureSource ?: "timestamp_source_unreported")
                    put("temperature_age_ms", if (temperatureTimestamp > 0L) exportedAt - temperatureTimestamp else JSONObject.NULL)
                    if (temperatureState == "fresh") {
                        put("temperature_device_raw_value", feature.getFloat("temperature", 0f).toDouble())
                        put("temperature_unit_status", "unverified")
                        put("temperature_source", feature.getString("temperature_source", "來源未標示"))
                        put("temperature_aggregation_method", feature.getString("temperature_aggregation_method", "unreported"))
                        put("temperature_aggregation_sample_count", feature.getInt("temperature_aggregation_sample_count", 0))
                    } else {
                        put("temperature_device_raw_value", JSONObject.NULL)
                        put("temperature_unit_status", "unverified")
                    }
                }
                listOf(
                    "heading" to "heading_deg",
                    "altitude" to "altitude_m",
                    "light" to "ambient_light_lx"
                ).forEach { (field, exportKey) ->
                    if (!feature.contains(field)) return@forEach
                    val timestamp = feature.getLong("${field}_timestamp", 0L)
                    val source = feature.getString("${field}_timestamp_source", null)
                    val state = FeatureSnapshotExportPolicy.freshnessState(timestamp, source, exportedAt)
                    put("${field}_state", state)
                    put("${field}_timestamp_epoch_ms", timestamp.takeIf { it > 0L } ?: JSONObject.NULL)
                    put("${field}_timestamp_source", source ?: "timestamp_source_unreported")
                    put("${field}_age_ms", if (timestamp > 0L) exportedAt - timestamp else JSONObject.NULL)
                    put(exportKey, if (state == "fresh") feature.getFloat(field, 0f).toDouble() else JSONObject.NULL)
                }
            })
            val sensors = JSONArray()
            var withData = 0
            var withFreshData = 0
            var withFreshRawData = 0
            var withStaleData = 0
            var withFutureTimestamp = 0
            var acceptedCount = 0
            var derivedCount = 0
            var withRawEvents = 0
            var currentSessionWithData = 0
            var currentSessionWithRawEvents = 0
            var currentSessionFresh = 0
            var currentSessionFreshRaw = 0
            var currentSessionStale = 0
            var currentSessionFutureTimestamp = 0
            var historicalWithData = 0
            WatchSensorCatalog.items.forEach { item ->
                val samples = raw.getInt("${item.kind}_samples", 0)
                val countContractVersion = raw.getInt("${item.kind}_count_contract_version", 0)
                val rawValueCount = raw.getInt("${item.kind}_raw_value_count", -1).takeIf { it >= 0 }
                val eventCountExport = SensorSuiteExportPolicy.eventCountExport(
                    normalizedCount = raw.getInt("${item.kind}_event_count", 0),
                    reportedCount = raw.getInt("${item.kind}_reported_event_count", -1).takeIf { it >= 0 },
                    known = raw.getInt("${item.kind}_event_count_known", 0) == 1
                )
                val exportedSampleCount = RawSensorPayloadPolicy.exportSampleCount(
                    RawSensorPayloadPolicy.isBiomedicalKind(item.kind),
                    countContractVersion,
                    rawValueCount,
                    samples
                )
                val presenceCount = RawSensorPayloadPolicy.displayPresenceCount(
                    countContractVersion,
                    rawValueCount,
                    raw.getInt("${item.kind}_event_count", 0),
                    raw.getInt("${item.kind}_preview_point_count", 0),
                    samples
                )
                val status = raw.getString("${item.kind}_status", "not_measured").orEmpty()
                val provenance = raw.getString("${item.kind}_data_provenance", "").orEmpty()
                val eventCount = raw.getInt("${item.kind}_event_count", 0).coerceAtLeast(0)
                val hasRawEvents = SensorSuiteExportPolicy.countsAsRawEvent(eventCount, provenance, status)
                val sessionStartedAt = raw.getLong("${item.kind}_session_started_at_ms", 0L)
                val rowSuiteId = raw.getString("${item.kind}_suite_id", "").orEmpty()
                val inLatestSuite = SensorSuiteSessionPolicy.belongsToLatestSuite(
                    sessionStartedAt, latestSuiteStartedAt, rowSuiteId, latestSuiteId
                )
                if (hasRawEvents) {
                    withRawEvents++
                    if (inLatestSuite) currentSessionWithRawEvents++
                }
                val measuredAt = raw.getLong("${item.kind}_time", 0L)
                val timestampSource = raw.getString("${item.kind}_timestamp_source", null)
                val timelineEvidence = SignalTimelineLabel.evidence(measuredAt, timestampSource)
                val state = SensorDisplayState.from(
                    samples = presenceCount,
                    status = status,
                    measuredAt = measuredAt,
                    now = exportedAt,
                    freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS,
                    kind = item.kind,
                    timestampSource = timestampSource
                )
                val hasRawData = SensorSuiteExportPolicy.hasRawData(presenceCount, provenance, status)
                if (hasRawData) {
                    withData++
                    if (inLatestSuite) currentSessionWithData++ else historicalWithData++
                    if (state == SensorDisplayState.STALE) withStaleData++
                    if (state == SensorDisplayState.FUTURE_TIMESTAMP) withFutureTimestamp++
                    if (SensorSuiteExportPolicy.countsAsFresh(state)) withFreshData++
                    if (inLatestSuite && state == SensorDisplayState.STALE) currentSessionStale++
                    if (inLatestSuite && state == SensorDisplayState.FUTURE_TIMESTAMP) currentSessionFutureTimestamp++
                    if (inLatestSuite && SensorSuiteExportPolicy.countsAsFresh(state)) currentSessionFresh++
                    if (SensorSuiteExportPolicy.countsAsFreshRawData(hasRawData, state)) withFreshRawData++
                    if (inLatestSuite && SensorSuiteExportPolicy.countsAsFreshRawData(hasRawData, state)) currentSessionFreshRaw++
                }
                if (inLatestSuite && SensorSuiteExportPolicy.countsAsAcceptedQuality(
                        state, raw.getInt("${item.kind}_quality_score", 0)
                    )) acceptedCount++
                if (inLatestSuite && samples > 0 && status == "derived_estimate") derivedCount++
                val waveform = SensorSuiteCsvParser.parseFloatsWithGapMarkers(
                    raw.getString("${item.kind}_waveform", "").orEmpty(), RawSensorPayloadPolicy.MAX_WAVEFORM_POINTS
                )
                val sourceWaveform = SensorSuiteCsvParser.parseFloatsWithGapMarkers(
                    raw.getString("${item.kind}_source_waveform", "").orEmpty(), RawSensorPayloadPolicy.MAX_WAVEFORM_POINTS
                )
                sensors.put(JSONObject().apply {
                    put("kind", item.kind)
                    put("label", raw.getString("${item.kind}_label", item.label))
                    put("group", item.group)
                    put("sensor_name", raw.getString("${item.kind}_source_sensor", raw.getString("${item.kind}_sensor", "")))
                    put("vendor", raw.getString("${item.kind}_vendor", ""))
                    put("status", raw.getString("${item.kind}_status", "not_measured"))
                    if (item.kind == "ppg") {
                        put("motion_evidence", JSONObject().apply {
                            put("capture_status", raw.getString("ppg_motion_capture_status", "not_recorded"))
                            put("status", raw.getString("ppg_motion_status", "not_recorded"))
                            put("algorithm_version", raw.getString("ppg_motion_algorithm_version", "not_recorded"))
                            put("event_count", raw.getInt("ppg_motion_event_count", 0))
                            put("paired_observation_count", raw.getInt("ppg_motion_paired_count", 0))
                            put("timestamp_source", raw.getString("ppg_motion_timestamp_source", "not_recorded"))
                            put("maximum_pair_skew_ns", raw.getLong("ppg_motion_maximum_pair_skew_ns", 0L))
                            put("matched_coverage", raw.getFloat("ppg_motion_matched_coverage", Float.NaN)
                                .takeIf(Float::isFinite) ?: JSONObject.NULL)
                            put("dynamic_acceleration_rms_mps2", raw.getFloat("ppg_motion_dynamic_rms_mps2", Float.NaN)
                                .takeIf(Float::isFinite) ?: JSONObject.NULL)
                            put("motion_activity_index", raw.getFloat("ppg_motion_activity_index", Float.NaN)
                                .takeIf(Float::isFinite) ?: JSONObject.NULL)
                            put("timestamps_ns", JSONArray(SensorSuiteCsvParser.parseLongs(
                                raw.getString("ppg_motion_timestamps_ns", "").orEmpty(), 512
                            )))
                            listOf("x_mps2", "y_mps2", "z_mps2").forEach { axis ->
                                put(axis, JSONArray(SensorSuiteCsvParser.parse(
                                    raw.getString("ppg_motion_$axis", "").orEmpty(), 512
                                )))
                            }
                            put("meaning", "synchronized wrist movement context only; not a validated PPG artifact score")
                        })
                    }
                    put("raw_event_count", eventCount)
                    put("has_raw_events", hasRawEvents)
                    val timestampValidity = SensorSuiteCsvParser.parse(
                        raw.getString("${item.kind}_event_timestamp_validity", "").orEmpty()
                    )
                    put("event_source_timestamp_validity", JSONArray().apply {
                        timestampValidity.forEach { put(it.toInt()) }
                    })
                    put("event_source_timestamp_validity_semantics", raw.getString(
                        "${item.kind}_event_timestamp_validity_semantics", "not_recorded"
                    ))
                    if (item.kind == "ecg") put("event_sample_count_semantics", raw.getString(
                        "ecg_event_sample_counts_semantics", "not_recorded"
                    ))
                    put("in_latest_suite", inLatestSuite)
                    put("suite_id", rowSuiteId.ifBlank { JSONObject.NULL })
                    if (item.kind in setOf("ppg", "ecg")) {
                        SensorEventTimingDiagnostics.fromCsv(
                            raw.getString("${item.kind}_event_timestamps_ns", "").orEmpty()
                        )?.let { timing ->
                            put("event_timing", JSONObject().apply {
                                put("event_count", timing.eventCount)
                                put("invalid_timestamp_count", timing.invalidTimestampCount)
                                put("out_of_order_count", timing.outOfOrderCount)
                                put("min_interval_ms", timing.minIntervalMs ?: JSONObject.NULL)
                                put("median_interval_ms", timing.medianIntervalMs ?: JSONObject.NULL)
                                put("max_interval_ms", timing.maxIntervalMs ?: JSONObject.NULL)
                                put("interpretation", "SensorEvent event cadence only; not ADC waveform sample rate")
                            })
                        }
                        SensorEventTimingDiagnostics.fromCsv(
                            raw.getString("${item.kind}_event_callback_elapsed_realtime_ns", "").orEmpty()
                        )?.let { timing ->
                            put("callback_delivery_timing", JSONObject().apply {
                                put("event_count", timing.eventCount)
                                put("invalid_timestamp_count", timing.invalidTimestampCount)
                                put("out_of_order_count", timing.outOfOrderCount)
                                put("min_interval_ms", timing.minIntervalMs ?: JSONObject.NULL)
                                put("median_interval_ms", timing.medianIntervalMs ?: JSONObject.NULL)
                                put("max_interval_ms", timing.maxIntervalMs ?: JSONObject.NULL)
                                put("interpretation", "App callback receipt cadence only; not sensor event time, ADC rate, or physiological beat interval")
                            })
                        }
                    }
                    val watchStatusSession = raw.getLong("${item.kind}_watch_status_session_started_at_ms", 0L)
                    val watchStatusCurrent = SensorSuiteProvenancePolicy.belongsToSession(sessionStartedAt, watchStatusSession)
                    put("watch_measurement", JSONObject().apply {
                        if (watchStatusCurrent) {
                            put("status", raw.getString("${item.kind}_watch_status", "unknown"))
                            put("reason", raw.getString("${item.kind}_watch_reason", ""))
                            put("wear_status_session_started_at_epoch_ms", watchStatusSession)
                            put("suite_wear_gate_qualified", if (raw.contains("${item.kind}_wear_qualified")) {
                                raw.getBoolean("${item.kind}_wear_qualified", false)
                            } else JSONObject.NULL)
                            put("sensor_on_body", if (raw.contains("${item.kind}_worn")) {
                                raw.getBoolean("${item.kind}_worn", false)
                            } else JSONObject.NULL)
                        } else {
                            put("status", JSONObject.NULL)
                            put("reason", JSONObject.NULL)
                            put("wear_status_session_started_at_epoch_ms", JSONObject.NULL)
                            put("suite_wear_gate_qualified", JSONObject.NULL)
                            put("sensor_on_body", JSONObject.NULL)
                        }
                    })
                    put("data_provenance", when {
                        !provenance.isNullOrBlank() -> provenance
                        hasRawData -> "hardware_event"
                        samples > 0 && status == "derived_estimate" -> "derived_estimate"
                        else -> "not_available"
                    })
                    put("reason", raw.getString("${item.kind}_reason", ""))
                    put("summary", raw.getString("${item.kind}_summary", "not measured"))
                    put("sample_count", exportedSampleCount ?: JSONObject.NULL)
                    put("reported_sample_count", samples)
                    put("event_count", eventCountExport.standardCount ?: JSONObject.NULL)
                    put("watch_event_count", eventCountExport.standardCount ?: JSONObject.NULL)
                    put("reported_event_count", eventCountExport.reportedCount ?: JSONObject.NULL)
                    put("raw_value_count", rawValueCount ?: JSONObject.NULL)
                    put("preview_point_count", raw.getInt("${item.kind}_preview_point_count", 0))
                    put("source_preview_point_count", raw.getInt("${item.kind}_source_preview_point_count", 0))
                    put("count_contract_version", countContractVersion)
                    put("event_count_is_known", eventCountExport.isKnown)
                    put("acquisition_diagnostics", JSONObject().apply {
                        val registration = acquisition.getString("${item.kind}_watch_registration", "unknown").orEmpty()
                        val watchSession = acquisition.getLong("${item.kind}_watch_session_started_at", 0L)
                        val watchEventCount = acquisition.getInt("${item.kind}_watch_event_count", 0)
                        val phoneReceivedCount = acquisition.getInt("${item.kind}_phone_received_count", 0)
                        val phoneSession = acquisition.getLong("${item.kind}_phone_session_started_at", 0L)
                        val diagnosticSuiteId = acquisition.getString("${item.kind}_phone_suite_id", "").orEmpty()
                        val diagnosticsMatchRow = SensorAcquisitionDiagnosticExportPolicy.matchesRowSession(
                            watchSession, sessionStartedAt, diagnosticSuiteId, rowSuiteId
                        )
                        put("diagnostic_session_started_at_epoch_ms", watchSession)
                        put("session_matches_sensor_row", diagnosticsMatchRow)
                        put("registration", registration.takeIf { diagnosticsMatchRow } ?: JSONObject.NULL)
                        put("first_sample_at_epoch_ms", if (diagnosticsMatchRow) acquisition.getLong("${item.kind}_watch_first_sample_at", 0L) else JSONObject.NULL)
                        put("last_sample_at_epoch_ms", if (diagnosticsMatchRow) acquisition.getLong("${item.kind}_watch_last_sample_at", 0L) else JSONObject.NULL)
                        put("sample_timestamp_source", if (diagnosticsMatchRow) acquisition.getString("${item.kind}_watch_sample_timestamp_source", "unknown") else JSONObject.NULL)
                        put("watch_event_count", if (diagnosticsMatchRow) watchEventCount else JSONObject.NULL)
                        put("phone_received_count", if (diagnosticsMatchRow) phoneReceivedCount else JSONObject.NULL)
                        put("phone_received_count_scope", "accepted snapshots in latest received sensor session")
                        put("phone_session_started_at_epoch_ms", if (diagnosticsMatchRow) phoneSession else JSONObject.NULL)
                        put("phone_last_received_at_epoch_ms", if (diagnosticsMatchRow) acquisition.getLong("${item.kind}_phone_last_received_at", 0L) else JSONObject.NULL)
                        put("producer_enqueue_requested_at_epoch_ms", if (diagnosticsMatchRow) acquisition.getLong("${item.kind}_producer_enqueue_requested_at", 0L) else JSONObject.NULL)
                        put("stop_reason", if (diagnosticsMatchRow) acquisition.getString("${item.kind}_watch_stop_reason", "unknown") else JSONObject.NULL)
                        put("failure_stage", SensorAcquisitionDiagnosticState.classify(
                            registration,
                            watchSession,
                            sessionStartedAt,
                            watchEventCount,
                            phoneReceivedCount,
                            phoneSession,
                            diagnosticSuiteId,
                            rowSuiteId
                        ).exportValue)
                    })
                    put("measurement_state", state.exportValue)
                    put("timestamp_epoch_ms", measuredAt)
                    put("published_at_epoch_ms", raw.getLong("${item.kind}_published_at", 0L))
                    put("timestamp_source", timestampSource ?: "legacy_or_unknown")
                    put("analysis_timeline_source", timelineEvidence.source)
                    put("estimated_timeline", timelineEvidence.estimated)
                    put("timeline_interpretation", when (timelineEvidence.source) {
                        "SENSOR_TIME" -> "source_sensor_timestamp"
                        "MEASUREMENT_TIME" -> "health_services_measurement_time"
                        "ESTIMATED_TIMELINE" -> "estimated_not_sensor_clock"
                        else -> "normalized_sample_index_no_time_scale"
                    })
                    put("age_ms", if (measuredAt > 0L) exportedAt - measuredAt else JSONObject.NULL)
                    put("is_stale", state == SensorDisplayState.STALE)
                    put("freshness_reason", SensorSuiteExportPolicy.freshnessReason(
                        state, measuredAt, item.kind, timestampSource
                    ))
                    put(
                        "quality_score",
                        if (raw.contains("${item.kind}_quality_score")) {
                            raw.getInt("${item.kind}_quality_score", 0)
                        } else JSONObject.NULL
                    )
                    put(
                        "quality_score_state",
                        if (raw.contains("${item.kind}_quality_score")) "scored" else "unscored"
                    )
                    put("quality_score_interpretation", when {
                        item.kind == "ppg" && status == "research_signal_unscaled" -> "unscaled_signal_heuristic_not_validated_sqi"
                        status == "valid_signal" -> "research_signal_quality_heuristic_not_clinical_validation"
                        else -> "not_a_clinical_measurement"
                    })
                    put("calibration", raw.getString("${item.kind}_calibration", ""))
                    if (item.kind in setOf("ppg", "bioz")) {
                        val confidence = raw.getInt("${item.kind}_confidence_score", -1)
                        put("research_evidence_confidence_score", confidence.takeIf { it in 1..100 } ?: JSONObject.NULL)
                        put("research_evidence_confidence_interpretation", "evidence_completeness_index_not_accuracy_or_probability")
                    }
                    if (item.kind == "bioz") {
                        val calibration = raw.getString("bioz_calibration", "").orEmpty()
                        val position = raw.getInt("bioz_waveform_median_position_index", -1)
                            .takeIf { it in 0..100 }
                            ?: Regex("(?:^|;)\\s*waveform_median_position=([0-9]+)(?:;|$)")
                                .find(calibration)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        put("waveform_median_position_index", position ?: JSONObject.NULL)
                        put("waveform_median_position_meaning", "within_session_relative_position_not_impedance_or_cross_session_trend")
                        put("relative_change_percent", raw.getFloat("bioz_relative_change_percent", Float.NaN)
                            .takeIf(Float::isFinite) ?: JSONObject.NULL)
                        val biozVersion = raw.getString("bioz_algorithm_version", "not_recorded")
                        val dispersion = raw.getInt("bioz_stability_score", -1).takeIf {
                            it in 0..100 && biozVersion == BiomedicalCalibration.BIOZ_RESEARCH_VERSION
                        }
                        put("relative_dispersion_percent", dispersion ?: JSONObject.NULL)
                        put("relative_dispersion_meaning", "waveform_sd_over_full_range_percent_not_time_stability_contact_or_physiology")
                        put("stability_score", JSONObject.NULL)
                        put("timeline_source", raw.getString("bioz_timeline_source", "not_recorded"))
                        put("algorithm_version", biozVersion)
                        val rawChannels = BiozRawChannelEvidence.decode(calibration)
                        put("raw_channel_values", JSONArray().apply {
                            rawChannels.forEach { (index, value) ->
                                put(JSONObject().apply {
                                    put("channel_index", index)
                                    put("raw_value", value ?: JSONObject.NULL)
                                })
                            }
                        })
                        put("raw_channel_values_meaning", "HAL_device_codes_unit_unverified_not_ohms_or_body_composition")
                    }
                    if (item.kind == "ppg") {
                        val period = raw.getInt("ppg_dominant_period_samples", 0).takeIf { it > 0 }
                        val correlation = raw.getString("ppg_period_correlation", null)
                            ?.takeIf { it.length <= 32 }
                            ?.toDoubleOrNull()
                            ?.takeIf { it.isFinite() && it in -1.0..1.0 }
                        put("dominant_period_samples", period ?: JSONObject.NULL)
                        put("period_autocorrelation", correlation ?: JSONObject.NULL)
                        put("periodicity_timeline", if (period != null) "NORMALIZED_INDEX" else JSONObject.NULL)
                        put("periodicity_interpretation", "index_domain_waveform_periodicity_not_seconds_or_pulse_rate")
                    }
                    val halPeriod = raw.getFloat("${item.kind}_hal_sample_period_s", 0f).toDouble()
                        .takeIf { raw.contains("${item.kind}_hal_sample_period_s") }
                    val sampleRateEvidence = SensorSuiteExportPolicy.sampleRateExport(
                        raw.getFloat("${item.kind}_sample_rate", 0f).toDouble()
                            .takeIf { raw.contains("${item.kind}_sample_rate") },
                        halPeriod,
                        raw.getString("${item.kind}_reporting_mode", "").orEmpty()
                    )
                    put("sample_rate_hz", sampleRateEvidence.sampleRateHz ?: JSONObject.NULL)
                    put("sample_rate_source", sampleRateEvidence.source)
                    val eventRateEvidence = SensorSuiteExportPolicy.eventRateExport(
                        raw.getFloat("${item.kind}_event_rate_per_session_second", 0f).toDouble()
                            .takeIf { raw.contains("${item.kind}_event_rate_per_session_second") },
                        raw.getString("${item.kind}_event_rate_source", null)
                    )
                    put("event_rate_per_session_second", eventRateEvidence.eventsPerSessionSecond ?: JSONObject.NULL)
                    put("event_rate_source", eventRateEvidence.source)
                    val jitterEvidence = SensorSuiteExportPolicy.jitterExport(
                        halPeriod,
                        raw.getFloat("${item.kind}_hal_sample_jitter_s", 0f).toDouble()
                            .takeIf { raw.contains("${item.kind}_hal_sample_jitter_s") }
                    )
                    put("hal_sample_jitter_standard_deviation_s", jitterEvidence.standardDeviationSeconds ?: JSONObject.NULL)
                    put("hal_sample_jitter_to_mean_period_ratio", jitterEvidence.periodRatio ?: JSONObject.NULL)
                    put("hal_sample_jitter_relation", jitterEvidence.relation)
                    if (raw.contains("${item.kind}_hal_additional_info_supported")) {
                        put("hal_additional_info_supported", raw.getBoolean("${item.kind}_hal_additional_info_supported", false))
                    }
                    if (raw.contains("${item.kind}_hal_sample_period_s")) {
                        val reportedPeriod = raw.getFloat("${item.kind}_hal_sample_period_s", 0f).toDouble()
                        put("hal_reported_sample_period_s", reportedPeriod.takeIf { it.isFinite() && it in 0.0001..1.0 } ?: JSONObject.NULL)
                    }
                    if (raw.contains("${item.kind}_hal_sample_jitter_s")) {
                        val reportedJitter = raw.getFloat("${item.kind}_hal_sample_jitter_s", 0f).toDouble()
                        put("hal_reported_sample_jitter_s", reportedJitter.takeIf { it.isFinite() && it >= 0.0 } ?: JSONObject.NULL)
                    }
                    put("source_sensor", raw.getString("${item.kind}_source_sensor", raw.getString("${item.kind}_sensor", "")))
                    put("sensor_candidates_count", raw.getInt("${item.kind}_candidate_count", 0))
                    put("sensor_candidates", raw.getString("${item.kind}_candidate_summary", ""))
                    put("channels", raw.getInt("${item.kind}_channels", 0))
                    put("session_started_at_epoch_ms", raw.getLong("${item.kind}_session_started_at_ms", 0L))
                    put("final_snapshot", raw.getBoolean("${item.kind}_final", false))
                    put("waveform_points", waveform.size)
                    put("waveform_encoding", raw.getString("${item.kind}_waveform_encoding", "legacy_unverified"))
                    put("source_waveform_points", sourceWaveform.size)
                    put("source_waveform_encoding", raw.getString("${item.kind}_source_waveform_encoding", "not_preserved"))
                    if (item.kind in setOf("body_temperature_raw", "thermometer", "board_temperature")) {
                        val temperatureSource = raw.getString(
                            "${item.kind}_source_sensor",
                            raw.getString("${item.kind}_sensor", "UNKNOWN_SOURCE")
                        ).orEmpty()
                        val temperatureSummary = TemperatureResearchSummary.calculate(
                            waveform,
                            sourceEvidence = ResearchSourceEvidence.score(temperatureSource),
                            timeEvidence = if (timelineEvidence.source in setOf("SENSOR_TIME", "MEASUREMENT_TIME")) 100 else 0
                        )
                        put("temperature_research_summary_version", "temperature-research-summary-v1")
                        put("temperature_raw_unit_status", "unverified")
                        put("temperature_research_summary_status", if (temperatureSummary == null) "no_finite_samples" else "available")
                        put("temperature_finite_sample_count", temperatureSummary?.finiteSampleCount ?: 0)
                        put("temperature_total_waveform_point_count", waveform.size)
                        put("temperature_median_raw", temperatureSummary?.medianRaw ?: JSONObject.NULL)
                        put("temperature_minimum_raw", temperatureSummary?.minimumRaw ?: JSONObject.NULL)
                        put("temperature_maximum_raw", temperatureSummary?.maximumRaw ?: JSONObject.NULL)
                        put("temperature_last_sequence_value_raw", temperatureSummary?.lastSequenceValueRaw ?: JSONObject.NULL)
                        put("temperature_last_minus_median_raw", temperatureSummary?.lastMinusMedianRaw ?: JSONObject.NULL)
                        put("temperature_normalized_position_0_to_1", temperatureSummary?.normalizedPosition ?: JSONObject.NULL)
                        put("temperature_evidence_completeness_score",
                            temperatureSummary?.evidenceIndex?.score ?: JSONObject.NULL)
                        put("temperature_metrics_interpretation", "raw_channel_statistics_unit_and_calibration_unverified")
                    }
                    if (item.kind == "ecg") {
                        val researchEncoding = raw.getString("ecg_research_waveform_encoding", "not_recorded")
                        val researchStatus = raw.getString("ecg_research_decode_status", "not_recorded")
                        val researchInterpretationAllowed = EcgPayloadInterpretationPolicy
                            .canUsePersistedResearchWaveform(researchEncoding, researchStatus)
                        val researchWaveform = if (researchInterpretationAllowed) SensorSuiteCsvParser.parseFloatsWithGapMarkers(
                            raw.getString("ecg_research_waveform", "").orEmpty(), RawSensorPayloadPolicy.MAX_WAVEFORM_POINTS
                        ) else emptyList()
                        put("research_waveform_encoding", researchEncoding)
                        put("research_waveform_decode_status", researchStatus)
                        put("research_waveform_interpretation_allowed", researchInterpretationAllowed)
                        put("legacy_research_waveform_ignored", !researchInterpretationAllowed &&
                            raw.getString("ecg_research_waveform", "").orEmpty().isNotBlank())
                        put("research_waveform_analysis_point_count", if (researchInterpretationAllowed)
                            raw.getInt("ecg_research_analysis_point_count", researchWaveform.size).coerceAtLeast(0) else JSONObject.NULL)
                        put("research_waveform_exported_point_count", if (researchInterpretationAllowed) researchWaveform.size else JSONObject.NULL)
                        put("research_waveform_finite_sample_count", if (researchInterpretationAllowed)
                            raw.getInt("ecg_research_decoded_value_count", researchWaveform.count(Float::isFinite)).coerceAtLeast(0) else JSONObject.NULL)
                        put("research_waveform_missing_sample_count", if (researchInterpretationAllowed)
                            raw.getInt("ecg_research_missing_sample_count", researchWaveform.count { !it.isFinite() }).coerceAtLeast(0) else JSONObject.NULL)
                        val rawWaveform = SensorSuiteCsvParser.parse(raw.getString("ecg_raw_waveform", "").orEmpty())
                        val timestamps = SensorSuiteCsvParser.parseLongs(
                            raw.getString("ecg_event_timestamps_ns", "").orEmpty(), 512
                        )
                        val sampleCounts = SensorSuiteCsvParser.parseInts(
                            raw.getString("ecg_event_sample_counts", "").orEmpty(), 512
                        )
                        val sourceEncoding = raw.getString("ecg_source_waveform_encoding", "")
                        val rawEventRows = SensorSuiteCsvParser.parseRows(
                            raw.getString("ecg_event_raw_values_rows", "").orEmpty(),
                            512,
                            if (sourceEncoding == "hal_event_values_full_unmapped_preview") {
                                SensorSuiteCsvParser.MAX_ECG_EVENT_ROW_CHARS
                            } else {
                                48 * 16
                            }
                        )
                        val waveformEncoding = raw.getString("ecg_waveform_encoding", "")
                        val rawValuesStatus = raw.getString("ecg_event_raw_values_status", "legacy_app_split_unverified")
                        put("raw_event_values_capture_status", rawValuesStatus)
                        put("raw_event_value_row_count", rawEventRows.size)
                        put("raw_event_values_encoding", sourceEncoding)
                        val rawAlignment = EcgRawValueAlignmentPolicy.evaluate(
                            sourceEncoding = sourceEncoding.orEmpty(),
                            rawValuesStatus = rawValuesStatus.orEmpty(),
                            rawRowCount = rawEventRows.size,
                            eventTimestampsNs = timestamps
                        )
                        put("raw_event_values_retained", rawAlignment.valuesRetained)
                        put("raw_event_rows_index_aligned", rawAlignment.rowsIndexAligned)
                        put("sensor_event_timestamps_valid_monotonic", rawAlignment.sensorTimestampsValidMonotonic)
                        put("raw_event_values_time_aligned", rawAlignment.valuesTimeAligned)
                        put("raw_event_values_aligned", rawAlignment.valuesTimeAligned)
                        put(
                            "raw_event_values_alignment_semantics",
                            "raw rows may be index-aligned while time_aligned is false; false does not mean raw values were not retained"
                        )
                        put("decoded_sample_timeline_aligned", waveformEncoding == "verified_ecg_samples_v1" &&
                            timestamps.isNotEmpty() && timestamps.size == sampleCounts.size && sampleCounts.sum() > 0 &&
                            sampleCounts.sum() == rawWaveform.size)
                        EcgResearchCalibration.estimatePackedPointRateNs(
                            timestamps,
                            sampleCounts,
                            waveformEncoding
                        ).hz?.let {
                            put("estimated_packed_point_rate_hz", it)
                        }
                        EcgResearchCalibration.estimateEventRateNs(timestamps).hz?.let {
                            put("hal_event_rate_hz", it)
                        }
                    }
                    if (item.kind == "ppg") {
                        val sourceTimestampsCsv = raw.getString("ppg_event_timestamps_ns", "").orEmpty()
                        val sourceTimestamps = SensorSuiteCsvParser.parseLongs(sourceTimestampsCsv, 512)
                        val callbackTimes = SensorSuiteCsvParser.parseLongs(
                            raw.getString("ppg_event_callback_elapsed_realtime_ns", "").orEmpty(), 512
                        )
                        val sampleCounts = SensorSuiteCsvParser.parseLongs(
                            raw.getString("ppg_event_sample_counts", "").orEmpty(), 512
                        )
                        put("event_timing_diagnostics", JSONObject().apply {
                            put("source_timestamp_count", sourceTimestamps.size)
                            put("callback_receipt_time_count", callbackTimes.size)
                            put("sample_count_rows", sampleCounts.size)
                            put("arrays_aligned", sourceTimestamps.size == callbackTimes.size && sourceTimestamps.size == sampleCounts.size)
                            put("source_time_basis", "SensorEvent.timestamp nanoseconds; preserved without substitution")
                            put("callback_time_basis", "elapsedRealtimeNanos at callback receipt; diagnostic only, not sample time")
                            val summary = SensorEventTimingDiagnostics.fromCsv(sourceTimestampsCsv)
                            put("source_timestamp_diagnostics_available", summary != null)
                            if (summary != null) {
                                put("invalid_source_timestamp_count", summary.invalidTimestampCount)
                                put("out_of_order_source_timestamp_count", summary.outOfOrderCount)
                            }
                        })
                    }
                })
            }
            put("sensors_with_data", withData)
            put("sensors_with_raw_events", withRawEvents)
            put("sensors_with_current_session_raw_events", currentSessionWithRawEvents)
            put("sensors_with_raw_events_scope", "hardware_event provenance and event_count > 0; all retained history")
            put("sensors_with_data_scope", "all_retained_history; use sensors_with_current_session_data for latest suite")
            put("sensors_with_current_session_data", currentSessionWithData)
            put("sensors_with_current_session_fresh_data", currentSessionFresh)
            put("sensors_with_current_session_stale_data", currentSessionStale)
            put("sensors_with_current_session_future_timestamps", currentSessionFutureTimestamp)
            put("sensors_with_historical_data", historicalWithData)
            put("sensors_with_fresh_data", withFreshData)
            put("sensors_with_fresh_raw_data", withFreshRawData)
            put("sensors_with_stale_data", withStaleData)
            put("sensors_with_future_timestamps", withFutureTimestamp)
            put("fresh_stale_count_scope", "fresh_data_counts_available_or_signal_valid; fresh_raw_data_counts_retained_hardware_events_by_timestamp; per-row in_latest_suite identifies latest scan")
            put("sensors_with_accepted_signal_quality", acceptedCount)
            put("sensors_with_current_session_fresh_raw_data", currentSessionFreshRaw)
            put("sensors_with_derived_estimates", derivedCount)
            put("sensors_total", WatchSensorCatalog.items.size)
            put("sensors", sensors)
            put("metric_status", SensorMetricStatusSummary.toJson(
                WatchSensorCatalog.items.map { item ->
                    SensorDisplayState.from(
                        samples = RawSensorPayloadPolicy.displayPresenceCount(
                            raw.getInt("${item.kind}_count_contract_version", 0),
                            raw.getInt("${item.kind}_raw_value_count", -1).takeIf { it >= 0 },
                            raw.getInt("${item.kind}_event_count", 0),
                            raw.getInt("${item.kind}_preview_point_count", 0),
                            raw.getInt("${item.kind}_samples", 0)
                        ),
                        status = raw.getString("${item.kind}_status", "not_measured").orEmpty(),
                        measuredAt = raw.getLong("${item.kind}_time", 0L),
                        now = exportedAt,
                        freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS,
                        kind = item.kind,
                        timestampSource = raw.getString("${item.kind}_timestamp_source", null)
                    )
                }
            ))
            put("blood_pressure", JSONObject().apply {
                val cuff = context.getSharedPreferences(OpticalBpActivity.CUFF_PREFS, Context.MODE_PRIVATE)
                put("source", "manual_validated_cuff_entry")
                if (cuff.contains("manual_cuff_sbp")) put("systolic_mmhg", cuff.getFloat("manual_cuff_sbp", 0f).toDouble())
                if (cuff.contains("manual_cuff_dbp")) put("diastolic_mmhg", cuff.getFloat("manual_cuff_dbp", 0f).toDouble())
                put("timestamp_epoch_ms", cuff.getLong("manual_cuff_time", 0L))
                put("diagnostic_status", "single_reading_not_diagnostic")
            })
        }
        val text = root.toString(2)
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_SUMMARY_BYTES) { "Sensor summary exceeds size limit" }
        val directory = File(context.cacheDir, "exports").also { it.mkdirs() }
        cleanupPartials(directory)
        val destination = File(directory, FILE_NAME)
        EngineeringExportRetention.writeAtomically(destination, text, MAX_SUMMARY_BYTES.toLong())
    }
    const val MAX_SUMMARY_BYTES = 512 * 1024
}

internal object SensorSuiteDumpRetention {
    const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L

    fun cleanupExpiredPartials(
        directory: File,
        now: Long,
        delete: (File) -> Boolean = { it.delete() }
    ): Int {
        if (!directory.exists()) return 0
        return runCatching {
            java.nio.file.Files.newDirectoryStream(directory.toPath()).use { files ->
                files.asSequence().take(256).map { it.toFile() }
                    .filter { it.isFile && isPartialName(it.name) }
                    .filter { file ->
                        val modified = file.lastModified()
                        modified > 0L && now >= modified && now - modified >= MAX_AGE_MILLIS
                    }
                    .take(64)
                    .count { file -> !runCatching { delete(file) || !file.exists() }.getOrDefault(false) }
            }
        }.getOrDefault(1)
    }

    private fun isPartialName(name: String): Boolean =
        name == "${SensorSuiteDump.FILE_NAME}.tmp" ||
            (name.startsWith(".${SensorSuiteDump.FILE_NAME}.") && name.endsWith(".tmp"))

    fun isAvailable(lastModified: Long, now: Long): Boolean =
        lastModified > 0L && now >= lastModified && now - lastModified <= MAX_AGE_MILLIS
}
