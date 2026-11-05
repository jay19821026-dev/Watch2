package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.HealthAlgorithmEngine as ClinicalSensorFormulas
import com.jaysato.xiaomihealthbridge.engine.ClockEvidence
import com.jaysato.xiaomihealthbridge.engine.EcgClockEvidencePolicy
import com.jaysato.xiaomihealthbridge.engine.PpgMotionEvidenceEngine
import com.jaysato.xiaomihealthbridge.engine.RawSensorCountContract
import com.jaysato.xiaomihealthbridge.engine.TimedAccelerationObservation
import com.jaysato.xiaomihealthbridge.engine.TimedPpgObservation

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val SENSOR_EVENT_METADATA_KINDS = setOf(
    "ecg", "ppg", "max_hr", "lifeq_ott", "bioz", "bioz_respiration",
    "spo2_raw", "mi_spo2", "body_temperature_raw", "thermometer"
)

object WearDataProcessor {
    fun process(context: Context, dataItem: DataItem, scope: CoroutineScope) {
        val uri = dataItem.uri
        val path = uri.path.orEmpty()
        val data = DataMapItem.fromDataItem(dataItem).dataMap
        val bpm = data.getLong(HeartRateDataListenerService.KEY_BPM)
        val timestamp = data.getLong(HeartRateDataListenerService.KEY_TIMESTAMP)
        val timestampSource = data.getString("timestamp_source")
        val metric = data.getString("metric").orEmpty()
        val value = data.getDouble("value")
        val start = data.getLong("start")
        val end = data.getLong("end")
        val routePoints = data.getStringArray("route_points") ?: emptyArray()
        if (path.startsWith(MobileSafetyAlerts.PATH)) {
            val type = data.getString("type").orEmpty()
            val eventTime = data.getLong("timestamp")
            val committed = DataItemDurabilityCommit.commitThenAcknowledge(
                commitCheckpoint = {
                    type.isBlank() || !eventTime.isPlausibleTimestamp() ||
                        MobileSafetyAlerts.receive(context, type, data.getDouble("value"), eventTime)
                },
                afterCommit = {},
                acknowledgeDataItem = { scope.launch { WearDataItemAckTracker.delete(context, uri) } }
            )
            if (!committed) Log.e(TAG, "safety_alert_persist_failed; data_item_retained")
            return
        }
        if (path.startsWith("/control-status/")) {
            scope.launch(Dispatchers.IO) {
            val message = data.getString("message").orEmpty()
            val timestamp = data.getLong("timestamp")
            val success = data.getBoolean("success", false)
            val incomingSuiteId = data.getString("suite_id").orEmpty()
            val suiteStartedAt = data.getLong("suite_session_started_at_ms", timestamp)
            val startsSuite = data.getBoolean("clear_sensor_snapshot", false) ||
                message.startsWith("全感測器掃描開始")
            val statusPrefs = context.getSharedPreferences(HeartRateDataListenerService.PREFS, Context.MODE_PRIVATE)
            val statusWriteSucceeded = if (ControlStatusOrdering.shouldAccept(timestamp, statusPrefs.getLong("control_status_time", 0L))) {
                statusPrefs.edit()
                    .putString("control_status", message)
                    .putLong("control_status_time", timestamp)
                    .putBoolean("control_status_success", success)
                    .commit()
            } else true
            val rawPrefs = RawSensorStore.open(context)
            val suiteSession = rawPrefs.getLong(SensorSuiteSessionPolicy.SESSION_KEY, 0L)
            val suiteWriteSucceeded = if (startsSuite && ControlStatusOrdering.shouldAccept(suiteStartedAt, suiteSession)) {
                val reportedRunState = data.getString("suite_run_state")
                    ?.takeIf { it == "running" || it in SensorSuiteSessionPolicy.TERMINAL_STATES }
                    ?: "running"
                val incomingRunState = SensorSuiteSessionPolicy.runStateForSuiteStart(
                    reportedRunState,
                    timestamp,
                    suiteStartedAt
                )
                rawPrefs.edit()
                    .putLong(SensorSuiteSessionPolicy.SESSION_KEY, suiteStartedAt)
                    .putLong("sensor_suite_session_started_at_ms", suiteStartedAt)
                    .putString(SensorSuiteSessionPolicy.SUITE_ID_KEY, incomingSuiteId)
                    .putString(SensorSuiteSessionPolicy.RUN_STATE_KEY, incomingRunState)
                    .putBoolean("control_status_success", success)
                    .apply {
                        if (incomingRunState in SensorSuiteSessionPolicy.TERMINAL_STATES) {
                            putLong("suite_session_ended_at_ms", timestamp)
                        } else remove("suite_session_ended_at_ms")
                    }
                    .commit()
            } else {
                val currentSuiteId = rawPrefs.getString(SensorSuiteSessionPolicy.SUITE_ID_KEY, "").orEmpty()
                val currentRunState = rawPrefs.getString(SensorSuiteSessionPolicy.RUN_STATE_KEY, "").orEmpty()
                val previousTerminalTimestamp = rawPrefs.getLong("suite_session_ended_at_ms", 0L)
                val terminalState = data.getString("suite_run_state")
                    ?.takeIf { it in SensorSuiteSessionPolicy.TERMINAL_STATES }
                    ?: SensorSuiteSessionPolicy.terminalState(message, success)
                if (terminalState != null && SensorSuiteSessionPolicy.shouldAcceptSuiteTerminal(
                        incomingSuiteId,
                        currentSuiteId,
                        timestamp,
                        suiteStartedAt,
                        suiteSession,
                        currentRunState,
                        previousTerminalTimestamp
                    )) {
                    rawPrefs.edit()
                        .putString(SensorSuiteSessionPolicy.RUN_STATE_KEY, terminalState)
                        .putLong(SensorSuiteSessionPolicy.SESSION_KEY, suiteStartedAt)
                        .putLong("sensor_suite_session_started_at_ms", suiteStartedAt)
                        .putString(SensorSuiteSessionPolicy.SUITE_ID_KEY, incomingSuiteId)
                        .putLong("suite_session_ended_at_ms", timestamp)
                        .putBoolean("control_status_success", success)
                        .commit()
                } else true
            }
            val statusCheckpointSucceeded = statusPrefs.edit()
                .putString("control_status_durability_checkpoint", UUID.randomUUID().toString())
                .commit()
            val rawCheckpointSucceeded = rawPrefs.edit()
                .putString("sensor_suite_durability_checkpoint", UUID.randomUUID().toString())
                .commit()
            if (!statusWriteSucceeded || !suiteWriteSucceeded ||
                !statusCheckpointSucceeded || !rawCheckpointSucceeded
            ) {
                Log.e(TAG, "control_status_persist_failed; data_item_retained")
                return@launch
            }
            SensorSuiteDump.write(context)
            WearDataItemAckTracker.delete(context, uri)
            }
            return
        }
        if (path.startsWith("/feature-snapshot/")) {
            val prefs = context.getSharedPreferences(FEATURE_PREFS, Context.MODE_PRIVATE)
            val rawPrefs = RawSensorStore.open(context)
            val incoming = data.getLong("timestamp")
            val committed = DataItemDurabilityCommit.commitThenAcknowledge(
                commitCheckpoint = {
                    synchronized(FEATURE_LOCK) {
                        val previous = prefs.getLong("timestamp", 0L)
                        prefs.edit().apply {
                            putLong("timestamp", incoming.coerceAtLeast(previous))
                            listOf("heading", "altitude", "light", "temperature", "whs_heart_rate").forEach { field ->
                                val fieldTimestampKey = "${field}_timestamp"
                                val fieldTimestamp = data.getLong(fieldTimestampKey, 0L)
                                val previousFieldTimestamp = prefs.getLong(fieldTimestampKey, 0L)
                                val measurement = if (data.containsKey(field)) FeatureSnapshotFieldPolicy.acceptedMeasurement(
                                    field,
                                    data.getDouble(field),
                                    fieldTimestamp,
                                    data.getString("${field}_timestamp_source"),
                                    previousFieldTimestamp,
                                    if (field == "whs_heart_rate") rawPrefs.getLong("whs_heart_rate_time", 0L) else 0L,
                                    System.currentTimeMillis()
                                ) else null
                                if (measurement != null) {
                                    putFloat(field, measurement.value)
                                    putLong(fieldTimestampKey, measurement.timestamp)
                                    putString("${field}_timestamp_source", measurement.timestampSource)
                                    if (field == "temperature") {
                                        val source = data.getString("temperature_source").orEmpty()
                                        if (source.isBlank()) remove("temperature_source")
                                        else putString("temperature_source", source)
                                        putString("temperature_unit_status", data.getString("temperature_unit_status") ?: "unverified")
                                        putString("temperature_aggregation_method", data.getString("temperature_aggregation_method") ?: "unreported")
                                        if (data.containsKey("temperature_aggregation_sample_count")) {
                                            putInt("temperature_aggregation_sample_count", data.getInt("temperature_aggregation_sample_count").coerceAtLeast(0))
                                        } else {
                                            remove("temperature_aggregation_sample_count")
                                        }
                                    }
                                }
                            }
                            val wornTimestamp = data.getLong("worn_timestamp", 0L)
                            val wornTimestampSource = data.getString("worn_timestamp_source")
                            val wearKnown = FeatureSnapshotOrdering.isFreshWearState(
                                data.getBoolean("worn_known", false),
                                data.containsKey("worn"),
                                wornTimestamp,
                                wornTimestampSource,
                                System.currentTimeMillis()
                            )
                            val worn = data.getBoolean("worn", false)
                            val currentWearState = FeatureSnapshotOrdering.currentWearState(wearKnown, worn)
                            if (currentWearState == null) {
                                remove("worn")
                                remove("worn_timestamp")
                                remove("worn_timestamp_source")
                            } else if (data.containsKey("worn") && FeatureSnapshotOrdering.shouldAccept(
                                    wornTimestamp, prefs.getLong("worn_timestamp", 0L), System.currentTimeMillis()
                                )) {
                                putBoolean("worn", currentWearState)
                                putLong("worn_timestamp", wornTimestamp)
                                putString("worn_timestamp_source", wornTimestampSource)
                            }
                        }.commit()
                    }
                },
                afterCommit = { SensorSuiteDump.write(context) },
                acknowledgeDataItem = { scope.launch { WearDataItemAckTracker.delete(context, uri) } }
            )
            if (!committed) {
                Log.e(TAG, "feature_snapshot_persist_failed; data_item_retained")
                return
            }
            return
        }
        if (path.startsWith("/raw-sensor/")) {
            val kind = data.getString("kind").orEmpty()
            if (kind.isNotBlank() && data.getBoolean("final")) {
                Log.i(TAG, "sensor_snapshot_received kind=${kind.take(64)} samples=${data.getInt("samples")}")
            }
            if (kind.isNotBlank()) synchronized(RAW_SNAPSHOT_PROCESS_LOCK) {
                val prefs = RawSensorStore.open(context)
                val receiptId = uri.toString()
                val receiptLedgerKey = "${kind}_raw_snapshot_receipt_ledger"
                val pendingFeatureLedgerKey = "${kind}_raw_snapshot_feature_pending_ledger"
                val sourceWaveform = data.getFloatArray("waveform")
                val motionTimestamps = data.getLongArray("motion_event_timestamps_ns")
                val motionX = data.getFloatArray("motion_x_meters_per_second_squared")
                val motionY = data.getFloatArray("motion_y_meters_per_second_squared")
                val motionZ = data.getFloatArray("motion_z_meters_per_second_squared")
                val sourceWaveformEncoding = data.getString("waveform_encoding")
                    ?.takeIf(String::isNotBlank) ?: "hal_raw_encoding_unverified"
                val sourceEventTimestamps = data.getLongArray("event_timestamps_ns")
                val sourceEventTimestampValidity = data.getLongArray("event_timestamp_validity")
                val sourceEventCallbackTimes = data.getLongArray("event_callback_elapsed_realtime_ns")
                val sourceEventSampleCounts = data.getLongArray("event_sample_counts")
                val sourceLatest = data.getString("latest").orEmpty()
                val sourceEventRawValues = data.getString("event_raw_values_rows")
                    ?: data.getString("event_metadata_rows").orEmpty()
                val sourceEventRawValuesStatus = data.getString("event_raw_values_status")
                    ?.takeIf(String::isNotBlank) ?: "metadata_status_missing"
                if (data.getBoolean("final") && kind in SENSOR_EVENT_METADATA_KINDS) {
                    Log.i(
                        TAG,
                        "sensor_event_metadata_received kind=$kind rows_key=${data.containsKey("event_raw_values_rows")} " +
                            "rows_chars=${sourceEventRawValues.length} raw_status=$sourceEventRawValuesStatus " +
                            "timestamps=${sourceEventTimestamps?.size ?: 0} " +
                            "timestamp_validity=${sourceEventTimestampValidity?.size ?: 0} " +
                            "callback_times=${sourceEventCallbackTimes?.size ?: 0}"
                    )
                }
                val sourceEventRawValueRowCount = if (data.containsKey("event_raw_values_rows") && sourceEventRawValues.isNotEmpty()) {
                    sourceEventRawValues.count { it == ';' } + 1
                } else if (data.containsKey("event_raw_values_rows")) 0 else null
                val payloadRejection = RawSensorPayloadPolicy.rejectionReason(
                    waveformPoints = sourceWaveform?.size ?: 0,
                    latestCsvChars = sourceLatest.length,
                    eventTimestampCount = sourceEventTimestamps?.size ?: 0,
                    eventSampleCountCount = sourceEventSampleCounts?.size ?: 0,
                    eventMetadataChars = sourceEventRawValues.length,
                    eventCallbackTimestampCount = sourceEventCallbackTimes?.size ?: 0,
                    eventCallbackMetadataPresent = data.containsKey("event_callback_elapsed_realtime_ns"),
                    eventRawValueRowCount = sourceEventRawValueRowCount,
                    eventRawValuesStatus = sourceEventRawValuesStatus,
                    eventTimestampValidityCount = sourceEventTimestampValidity?.size ?: 0,
                    eventTimestampValidityMetadataPresent = data.containsKey("event_timestamp_validity"),
                    eventTimestampValidityValues = sourceEventTimestampValidity?.take(512)?.toList(),
                    serializedDataMapBytes = data.toByteArray().size,
                    declaredPayloadRejection = data.getString("payload_rejection_reason")
                )
                val ppgMotionEvidence = if (kind == "ppg" && data.containsKey("motion_capture_status")) {
                    val eventTimes = data.getLongArray("preview_point_event_timestamps_ns") ?: longArrayOf()
                    val validity = data.getLongArray("preview_point_event_timestamp_validity") ?: longArrayOf()
                    val selectedWaveform = sourceWaveform ?: floatArrayOf()
                    val bounded = payloadRejection == null && eventTimes.size <= 1_024 &&
                        validity.size <= 1_024 && selectedWaveform.size <= RawSensorPayloadPolicy.MAX_WAVEFORM_POINTS
                    val motionCount = listOf(motionTimestamps?.size, motionX?.size, motionY?.size, motionZ?.size)
                        .distinct().singleOrNull()?.takeIf { it <= 512 } ?: 0
                    val ppgObservations: List<TimedPpgObservation> = if (bounded &&
                        validity.size == eventTimes.size && eventTimes.size == selectedWaveform.size &&
                        validity.all { it == 1L }) {
                        eventTimes.indices.map { index ->
                            TimedPpgObservation(eventTimes[index], selectedWaveform[index].toDouble())
                        }
                    } else emptyList()
                    val acceleration = if (motionCount > 0) (0 until motionCount).map { index ->
                        TimedAccelerationObservation(
                            motionTimestamps!![index], motionX!![index].toDouble(),
                            motionY!![index].toDouble(), motionZ!![index].toDouble()
                        )
                    } else emptyList()
                    PpgMotionEvidenceEngine.evaluate(
                        ppg = ppgObservations,
                        acceleration = acceleration,
                        sameSession = data.getBoolean("motion_same_session", false),
                        sameMonotonicClock = data.getBoolean("motion_same_monotonic_clock", false) &&
                            data.getString("ppg_preview_clock_domain") == WATCH_SENSOR_EVENT_CLOCK_DOMAIN &&
                            data.getString("motion_clock_domain") == WATCH_SENSOR_EVENT_CLOCK_DOMAIN,
                        maximumPairSkewNanos = data.getLong("motion_maximum_pair_skew_ns", 0L)
                    )
                } else null
                val publishedAt = data.getLong("timestamp")
                val sampleTime = SensorSnapshotTimestamp.sampleTime(
                    lastSampleAt = data.getLong("last_sample_at_ms", 0L),
                    hasLastSampleAt = data.containsKey("last_sample_at_ms")
                )
                val recoverableFeature = RawSnapshotFeatureRecovery.recover(
                    kind = kind,
                    isFinal = data.getBoolean("final"),
                    formulaPulseBpm = data.getDoubleOrNull("formula_pulse_bpm"),
                    averageBpm = data.getDoubleOrNull("average")?.takeIf { kind == "whs_heart_rate" },
                    timestamp = sampleTime,
                    sessionStartedAtMs = data.getLong("session_started_at_ms"),
                    producerSequence = data.getLong("producer_sequence", 0L),
                    timestampSource = data.getString("last_sample_timestamp_source")
                )
                val receipts = prefs.getString(receiptLedgerKey, "").orEmpty()
                val pendingFeatures = prefs.getString(pendingFeatureLedgerKey, "").orEmpty()
                val receiptKnown = RawSnapshotReceiptLedger.contains(receipts, receiptId)
                val featurePending = RawSnapshotReceiptLedger.contains(pendingFeatures, receiptId)
                val pendingFeatureLedgerUpdate = RawSnapshotReceiptLedger.reservePending(
                    encoded = pendingFeatures,
                    receiptId = receiptId,
                    isNewWork = recoverableFeature is RawSnapshotFeatureRecoveryOutcome.Recovered &&
                        !receiptKnown && !featurePending
                ) ?: run {
                    Log.e(TAG, "sensor_snapshot_feature_pending_full kind=$kind; retaining DataItem")
                    return
                }
                val retryingAfterPersistenceFailure = RawSnapshotReceiptRetryState.shouldRetry(kind, receiptId)
                val retryingCurrentSnapshot = RawSnapshotReceiptRetryState.isCurrentFailedSnapshot(
                    kind, receiptId, prefs.getString("${kind}_latest_snapshot_receipt_id", null)
                )
                val latestReceiptId = prefs.getString("${kind}_latest_snapshot_receipt_id", null)
                if (RawSnapshotReceiptRetryState.needsDurabilityFlushBeforeResolve(kind, receiptId, latestReceiptId)) {
                    val flushSucceeded = prefs.edit()
                        .putString("${kind}_receipt_flush_checkpoint", receiptId)
                        .commit()
                    if (!flushSucceeded) {
                        Log.e(TAG, "sensor_snapshot_receipt_flush_failed kind=$kind; retaining DataItem")
                        return
                    }
                    RawSnapshotReceiptRetryState.markKindPersisted(kind)
                }
                if (RawSnapshotReceiptLedger.shouldReplay(
                        receipts = receipts,
                        pending = pendingFeatures,
                        receiptId = receiptId,
                        retryingCurrentSnapshot = retryingCurrentSnapshot,
                        payloadRejected = payloadRejection != null
                    )) {
                    if (!persistRawSnapshotDiagnostics(
                            context = context,
                            kind = kind,
                            data = data,
                            receiptId = receiptId
                        )) {
                        Log.e(TAG, "sensor_snapshot_diagnostics_persist_failed kind=$kind; retaining DataItem")
                        return
                    }
                    val feature = (recoverableFeature as? RawSnapshotFeatureRecoveryOutcome.Recovered)?.feature
                    if (featurePending && feature == null) {
                        Log.e(TAG, "sensor_snapshot_feature_recovery_failed kind=$kind; retaining DataItem")
                        return
                    }
                    if (feature != null) {
                        val featurePrefs = context.getSharedPreferences(FEATURE_PREFS, Context.MODE_PRIVATE)
                        val isStrictlyNewer = HeartRateFeatureCommitPlanner.isStrictlyNewer(
                            timestamp = feature.timestamp,
                            sessionStartedAtMs = feature.sessionStartedAtMs,
                            producerSequence = feature.producerSequence,
                            previousTimestamp = featurePrefs.getLong("whs_heart_rate_timestamp", 0L),
                            previousSessionStartedAtMs = featurePrefs.getLong("whs_heart_rate_session_started_at_ms", 0L),
                            previousProducerSequence = featurePrefs.getLong("whs_heart_rate_producer_sequence", 0L),
                            now = System.currentTimeMillis()
                        )
                        if (HeartRateFeatureCommitPlanner.shouldPersistReceiptReplay(isStrictlyNewer, featurePending)) {
                            if (!updateHeartRateFeature(context, feature, requireDurableNoop = featurePending)) {
                                Log.e(TAG, "sensor_snapshot_feature_persist_failed kind=$kind; retaining DataItem")
                                return
                            }
                        }
                    }
                    val currentPendingFeatures = prefs.getString(pendingFeatureLedgerKey, "").orEmpty()
                    val replayCompleted = DataItemDurabilityCommit.commitThenAcknowledge(
                        commitCheckpoint = {
                            prefs.edit()
                                .putString(
                                    pendingFeatureLedgerKey,
                                    RawSnapshotReceiptLedger.remove(currentPendingFeatures, receiptId)
                                )
                                .putString("${kind}_receipt_replay_checkpoint", UUID.randomUUID().toString())
                                .commit()
                        },
                        afterCommit = {
                            writeSensorSuiteDumpSafely(context)
                            RawSnapshotReceiptRetryState.markResolved(kind, receiptId)
                        },
                        acknowledgeDataItem = {
                            scope.launch { acknowledgeArchivedSnapshot(context, uri, kind, data) }
                        }
                    )
                    if (!replayCompleted) {
                        Log.e(TAG, "sensor_snapshot_receipt_replay_commit_failed kind=$kind; retaining DataItem")
                        return
                    }
                    return
                }
                val incomingTime = data.getLong("timestamp")
                val diagnostics = context.getSharedPreferences("sensor_acquisition_diagnostics", Context.MODE_PRIVATE)
                val featurePrefs = context.getSharedPreferences(FEATURE_PREFS, Context.MODE_PRIVATE)
                val previousTime = prefs.getLong("${kind}_time", 0L)
                val incomingSession = data.getLong("session_started_at_ms")
                val incomingSequence = data.getLong("producer_sequence", 0L)
                val incomingSessionElapsedNs = data.getLong("session_started_elapsed_realtime_ns", 0L)
                val incomingSuiteId = data.getString("suite_id").orEmpty()
                val previousSession = prefs.getLong("${kind}_session_started_at_ms", 0L)
                val previousSessionElapsedNs = prefs.getLong("${kind}_session_started_elapsed_realtime_ns", 0L)
                val previousSuiteId = prefs.getString("${kind}_suite_id", "").orEmpty()
                val latestSuiteId = prefs.getString(SensorSuiteSessionPolicy.SUITE_ID_KEY, "").orEmpty()
                val newMeasurementSession = RawSnapshotOrdering.isNewMeasurementSession(
                    incomingSession, incomingSuiteId, previousSession, previousSuiteId, latestSuiteId,
                    incomingSessionElapsedNs, previousSessionElapsedNs
                )
                val heartRateRejection = if (kind == "whs_heart_rate" && !newMeasurementSession) {
                    HeartRateFeatureCommitPlanner.rejectionReason(
                        timestamp = incomingTime,
                        sessionStartedAtMs = incomingSession,
                        producerSequence = incomingSequence,
                        previousTimestamp = featurePrefs.getLong("whs_heart_rate_timestamp", 0L),
                        previousSessionStartedAtMs = featurePrefs.getLong("whs_heart_rate_session_started_at_ms", 0L),
                        previousProducerSequence = featurePrefs.getLong("whs_heart_rate_producer_sequence", 0L),
                        now = System.currentTimeMillis()
                    )
                } else {
                    null
                }
                val sampleTimeRejection = SensorSnapshotTimestamp.rejectionReason(
                    sampleTime = sampleTime,
                    previousSampleTime = if (newMeasurementSession) 0L else diagnostics.getLong("${kind}_watch_last_sample_at", 0L),
                    sessionStartedAt = incomingSession,
                    previousSessionStartedAt = diagnostics.getLong("${kind}_watch_session_started_at", 0L),
                    hasWatchSampleTime = data.containsKey("last_sample_at_ms")
                )
                val rejection = payloadRejection ?: if (!RawSnapshotReceiptRetryState.shouldApplyOrderingChecks(retryingCurrentSnapshot)) {
                    null
                } else {
                    heartRateRejection ?: sampleTimeRejection ?: RawSnapshotOrdering.rejectionReason(
                        incomingSession, incomingTime, incomingSequence, data.getBoolean("final"),
                        previousSession, previousTime, prefs.getLong("${kind}_producer_sequence", 0L),
                        prefs.getBoolean("${kind}_final", false),
                        incomingSessionElapsedNs, previousSessionElapsedNs,
                        incomingSuiteId, previousSuiteId, latestSuiteId
                    )
                }
                if (rejection != null) {
                    if (data.getBoolean("final")) recordRawSnapshotRejection(context, kind, rejection, data)
                    val pendingAtRejection = prefs.getString(pendingFeatureLedgerKey, "").orEmpty()
                    val resolvedPending = RawSnapshotReceiptLedger.pendingAfterRejectedReceipt(pendingAtRejection, receiptId)
                    if (resolvedPending != null) {
                        val rejectionCheckpoint = prefs.edit()
                            .putString(pendingFeatureLedgerKey, resolvedPending)
                            .putString("${kind}_receipt_rejection_checkpoint", UUID.randomUUID().toString())
                            .commit()
                        if (!rejectionCheckpoint) {
                            Log.e(TAG, "sensor_snapshot_rejection_commit_failed kind=$kind; retaining DataItem")
                            return
                        }
                    }
                    if (retryingAfterPersistenceFailure) RawSnapshotReceiptRetryState.markResolved(kind, receiptId)
                    scope.launch { WearDataItemAckTracker.delete(context, uri) }
                    return
                }
                val waveform = sourceWaveform?.toList().orEmpty()
                val sampleRate = data.getDouble("sample_rate").toFloat()
                val eventTimestamps = sourceEventTimestamps?.toList().orEmpty()
                val eventSampleCounts = sourceEventSampleCounts?.map(Long::toInt).orEmpty()
                val reportedSampleRate = sampleRate.takeIf { it.isFinite() && it > 0f } ?: 0f
                val counts = RawSensorCountContract.decode(
                    samples = data.getInt("samples", 0).takeIf { data.containsKey("samples") },
                    rawValueCount = data.getInt("raw_value_count", 0).takeIf { data.containsKey("raw_value_count") },
                    previewPointCount = data.getInt("preview_point_count", 0).takeIf { data.containsKey("preview_point_count") },
                    contractVersion = data.getInt("count_contract_version", 0).takeIf { data.containsKey("count_contract_version") },
                    eventCount = data.getInt("event_count", 0).takeIf { data.containsKey("event_count") }
                )
                val contractVersion = counts.contractVersion
                val trustedEventCount = RawSensorCountContract.trustedEventCount(counts)
                val eventCount = trustedEventCount ?: 0
                val reportedEventCount = counts.eventCount
                val sourceRawValueCount = if (RawSensorPayloadPolicy.isBiomedicalKind(kind)) {
                    RawSensorPayloadPolicy.sourceValueCount(contractVersion, counts.rawValueCount)
                } else counts.samples
                val previewPointCount = counts.previewPointCount?.let {
                    RawSensorPayloadPolicy.previewPointCount(it, waveform.size)
                } ?: waveform.size
                val latest = SensorSuiteCsvParser.parse(sourceLatest, 512)
                val ecgResearchDecode = if (
                    kind == "ecg" && sourceWaveformEncoding == "hal_event_values_full_unmapped_preview"
                ) {
                    EcgHalEventResearchDecoder.decode(sourceEventRawValues, eventTimestamps)
                } else null
                val analysisWaveform = if (kind == "ecg" && ecgResearchDecode?.waveform?.isNotEmpty() == true) {
                    ecgResearchDecode.waveform.map { it.toFloat() }
                } else {
                    waveform
                }
                val analysisSampleRate = if (reportedSampleRate > 0f) {
                    reportedSampleRate
                } else {
                    ecgResearchDecode?.estimatedSampleRateHz ?: 0f
                }
                val ecgClockEvidence = if (kind == "ecg") {
                    val sourceTimes = eventTimestamps
                    val callbackTimes = sourceEventCallbackTimes?.toList().orEmpty()
                    val sampleCounts = eventSampleCounts
                    val orderedSourceTimes = sourceTimes.size >= 2 &&
                        sourceTimes.all { it > 0L } &&
                        sourceTimes.zipWithNext().all { (before, after) -> after > before }
                    val eventOrderingVerified = EcgClockEvidencePolicy.eventOrderingVerified(sourceTimes, callbackTimes)
                    val halRateReported = reportedSampleRate > 0f &&
                        data.getDouble("hal_sample_period_s").let { it.isFinite() && it > 0.0 }
                    val decodedEventCount = ecgResearchDecode?.takeIf {
                        it.status == "decoded_research_unscaled"
                    }?.eventCount ?: 0
                    val decodedSampleCount = ecgResearchDecode?.takeIf {
                        it.status == "decoded_research_unscaled"
                    }?.decodedSampleCount ?: 0
                    ClockEvidence(
                        sourceTimestampsNanos = sourceTimes,
                        callbackTimestampsNanos = callbackTimes,
                        nominalSampleRateHz = reportedSampleRate.takeIf { halRateReported }?.toDouble(),
                        sampleRateSource = "sensor_hal_type_sampling".takeIf { halRateReported },
                        eventOrderingVerified = eventOrderingVerified,
                        samplesPerEventKnown = EcgClockEvidencePolicy.samplesPerEventKnown(
                            sampleCounts, sourceTimes.size, decodedEventCount, decodedSampleCount,
                            0
                        ),
                        sessionOrderingVerified = incomingSession > 0L && eventOrderingVerified,
                        sourceClockVerified = data.getString("last_sample_timestamp_source") ==
                            "android_sensor_event_elapsed_realtime" && orderedSourceTimes
                    )
                } else null
                val ecgDerivedRhythmAllowed = kind == "ecg" && EcgPayloadInterpretationPolicy.canUseDerivedRhythm(
                    sourceWaveformEncoding,
                    ecgClockEvidence?.sourceClockVerified == true,
                    reportedSampleRate,
                    data.getBoolean("channel_mapping_verified", false),
                    ecgClockEvidence?.samplesPerEventKnown == true,
                    EcgPayloadInterpretationPolicy.VERIFIED_ECG_EVENT_CONTRACT_AVAILABLE
                )
                val calibration = applyCalibration(
                    context, kind, analysisWaveform, analysisSampleRate, latest,
                    eventCount = eventCount,
                    sampleCount = sourceRawValueCount ?: 0,
                    measuredAtMillis = sampleTime,
                    timestampSource = data.getString("last_sample_timestamp_source", "UNKNOWN"),
                    channelMappingVerified = data.getBoolean("channel_mapping_verified", false),
                    wearQualified = data.getBoolean("worn", false),
                    ecgClockEvidence = ecgClockEvidence,
                    sensorProfileFingerprint = data.getString("sensor_profile_fingerprint"),
                    sourceEncoding = if (ecgResearchDecode?.waveform?.isNotEmpty() == true) {
                        EcgHalEventResearchDecoder.ENCODING
                    } else sourceWaveformEncoding
                )
                val formulaKeys = RawWaveformFormulaPolicy.sessionDerivedKeys
                val pointSamples = RawSensorPayloadPolicy.displayCount(
                    isBiomedical = RawSensorPayloadPolicy.isBiomedicalKind(kind),
                    contractVersion = contractVersion,
                    rawValueCount = sourceRawValueCount,
                    reportedSamples = data.getInt("samples", 0)
                )
                val hasCountForStatistics = RawSensorPayloadPolicy.hasStatisticSampleCount(
                    RawSensorPayloadPolicy.isBiomedicalKind(kind), sourceRawValueCount, pointSamples
                )
                var commitRejection: String? = null
                var persistenceFailure = false
                val snapshotStored = synchronized(FEATURE_LOCK) {
                    val currentHeartRateRejection = if (
                        kind == "whs_heart_rate" && !retryingCurrentSnapshot && !newMeasurementSession
                    ) {
                        FeatureSnapshotOrdering.rejectionReason(
                            incomingTime,
                            featurePrefs.getLong("whs_heart_rate_timestamp", 0L),
                            System.currentTimeMillis()
                        )
                    } else {
                        null
                    }
                    if (currentHeartRateRejection != null) {
                        commitRejection = "feature_already_committed_$currentHeartRateRejection"
                        false
                    } else {
                        val editor = prefs.edit().apply {
                    if (kind == "ecg" && !ecgDerivedRhythmAllowed) {
                        remove("ecg_rhythm_label")
                        remove("ecg_rhythm_title")
                        remove("ecg_rhythm_detail")
                        remove("ecg_rhythm_bpm")
                        remove("ecg_rhythm_rr_count")
                        remove("ecg_rhythm_research_only")
                        remove("ecg_research_rhythm_pattern_title")
                        remove("ecg_formula_pulse_bpm")
                        remove("ecg_formula_rr_count")
                        remove("ecg_formula_rmssd_ms")
                        remove("ecg_formula_pnn50")
                        remove("ecg_formula_cvrr")
                    } else if (kind == "ecg" && (newMeasurementSession || data.getBoolean("final"))) {
                        remove("ecg_rhythm_label")
                        remove("ecg_rhythm_title")
                        remove("ecg_rhythm_detail")
                        remove("ecg_rhythm_bpm")
                        remove("ecg_rhythm_rr_count")
                        remove("ecg_rhythm_research_only")
                        remove("ecg_research_rhythm_pattern_title")
                    }
                    if (kind == "ecg" && newMeasurementSession) {
                        remove("ecg_research_r_peak_indices")
                        remove("ecg_research_r_peak_index_distances_samples")
                        remove("ecg_research_timeline_source")
                        remove("ecg_calibration_profile_id")
                    }
                    if (newMeasurementSession) {
                        RawWaveformFormulaPolicy.keysToClearForNewSession(kind).forEach(::remove)
                    } else if (data.getBoolean("final")) {
                        formulaKeys.forEach { remove("${kind}_$it") }
                    }
                    if (data.containsKey("channel_mapping_verified")) {
                        putBoolean("${kind}_channel_mapping_verified", data.getBoolean("channel_mapping_verified"))
                    }
                    putString("${kind}_summary", calibration.summary ?: data.getString("summary"))
                    putString("${kind}_label", data.getString("label"))
                    putString("${kind}_sensor", data.getString("sensor"))
                    putString("${kind}_source_sensor", data.getString("sensor"))
                    putString("${kind}_vendor", data.getString("vendor"))
                    putString("${kind}_candidate_summary", data.getString("candidate_summary"))
                    putInt("${kind}_candidate_count", data.getInt("candidate_count"))
                    putString("${kind}_reporting_mode", data.getString("reporting_mode"))
                    putString("${kind}_status", calibration.status ?: data.getString("status"))
                    putString("${kind}_watch_status", data.getString("status").orEmpty())
                    putString("${kind}_watch_reason", data.getString("reason").orEmpty())
                    putLong("${kind}_watch_status_session_started_at_ms", incomingSession)
                    putString(
                        "${kind}_data_provenance",
                        SensorSuiteProvenancePolicy.dataProvenance(
                            data.getString("reporting_mode"), data.getString("data_provenance")
                        )
                    )
                    putString("${kind}_reason", calibration.reason ?: data.getString("reason"))
                    putString("${kind}_latest", sourceLatest)
                    putString("${kind}_waveform", calibration.waveform.joinToString(","))
                    putString("${kind}_waveform_encoding", calibration.encoding)
                    if (kind == "ppg") {
                        calibration.displayWaveform?.let { putString("ppg_display_waveform", it.joinToString(",")) }
                            ?: if (data.getBoolean("final")) remove("ppg_display_waveform") else Unit
                    }
                    if (kind == "bioz") {
                        calibration.biozDisplayWaveform?.let { putString("bioz_display_waveform", it.joinToString(",")) }
                            ?: if (data.getBoolean("final")) remove("bioz_display_waveform") else Unit
                    }
                    if (kind == "ecg") {
                        calibration.ecgRhythmPatternTitle?.let { putString("ecg_research_rhythm_pattern_title", it) }
                            ?: if (data.getBoolean("final")) remove("ecg_research_rhythm_pattern_title") else Unit
                        calibration.ecgRPeakIndices?.let { putString("ecg_research_r_peak_indices", it.joinToString(",")) }
                            ?: if (data.getBoolean("final")) remove("ecg_research_r_peak_indices") else Unit
                        calibration.ecgRPeakIndexDistancesSamples?.let {
                            putString("ecg_research_r_peak_index_distances_samples", it.joinToString(","))
                        } ?: if (data.getBoolean("final")) remove("ecg_research_r_peak_index_distances_samples") else Unit
                        calibration.ecgTimelineSource?.let { putString("ecg_research_timeline_source", it) }
                            ?: if (data.getBoolean("final")) remove("ecg_research_timeline_source") else Unit
                        EcgCalibrationSnapshotPolicy.activeProfileId(
                            calibration.ecgCalibrationProfileId,
                            calibration.encoding
                        )?.let { putString("ecg_calibration_profile_id", it) }
                            ?: remove("ecg_calibration_profile_id")
                    }
                    if (kind == "ppg" && (newMeasurementSession || data.getBoolean("final"))) {
                        calibration.ppgDominantPeriodSamples?.takeIf { it > 0 }
                            ?.let { putInt("ppg_dominant_period_samples", it) }
                            ?: remove("ppg_dominant_period_samples")
                        calibration.ppgPeriodCorrelation?.takeIf { it.isFinite() && it in -1.0..1.0 }
                            ?.let { putString("ppg_period_correlation", it.toString()) }
                            ?: remove("ppg_period_correlation")
                    }
                    if (sourceWaveform != null) {
                        putString("${kind}_source_waveform", sourceWaveform.joinToString(","))
                        putString("${kind}_source_waveform_encoding", sourceWaveformEncoding)
                    }
                    if (kind == "ecg") putString("ecg_raw_waveform", waveform.joinToString(","))
                    when (RawSnapshotSampleRatePersistencePolicy.action(data.containsKey("sample_rate"), newMeasurementSession)) {
                        RawSnapshotSampleRatePersistencePolicy.Action.WRITE ->
                            putFloat("${kind}_sample_rate", if (kind == "ecg") analysisSampleRate else reportedSampleRate)
                        RawSnapshotSampleRatePersistencePolicy.Action.CLEAR -> remove("${kind}_sample_rate")
                        RawSnapshotSampleRatePersistencePolicy.Action.KEEP -> Unit
                    }
                    if (data.containsKey("event_rate_per_session_second")) {
                        val eventRate = data.getDouble("event_rate_per_session_second")
                        if (eventRate.isFinite() && eventRate >= 0.0) {
                            putFloat("${kind}_event_rate_per_session_second", eventRate.toFloat())
                            putString("${kind}_event_rate_source", data.getString("event_rate_source") ?: "unknown")
                        } else {
                            remove("${kind}_event_rate_per_session_second")
                            remove("${kind}_event_rate_source")
                        }
                    } else {
                        remove("${kind}_event_rate_per_session_second")
                        remove("${kind}_event_rate_source")
                    }
                    if (data.containsKey("hal_additional_info_supported")) {
                        putBoolean("${kind}_hal_additional_info_supported", data.getBoolean("hal_additional_info_supported"))
                    }
                    if (data.containsKey("hal_sample_period_s")) {
                        val period = data.getDouble("hal_sample_period_s")
                        if (period.isFinite() && period in 0.0001..1.0) {
                            putFloat("${kind}_hal_sample_period_s", period.toFloat())
                        } else {
                            remove("${kind}_hal_sample_period_s")
                        }
                    } else {
                        remove("${kind}_hal_sample_period_s")
                    }
                    if (data.containsKey("hal_sample_jitter_s")) {
                        val jitter = data.getDouble("hal_sample_jitter_s")
                        if (jitter.isFinite() && jitter >= 0.0 && jitter <= Float.MAX_VALUE) {
                            putFloat("${kind}_hal_sample_jitter_s", jitter.toFloat())
                        } else {
                            remove("${kind}_hal_sample_jitter_s")
                        }
                    } else {
                        remove("${kind}_hal_sample_jitter_s")
                    }
                    if (kind == "ecg" && data.containsKey("event_timestamps_ns")) {
                        putBoolean("ecg_hal_additional_info_supported", data.getBoolean("hal_additional_info_supported"))
                        putString("ecg_event_timestamps_ns", sourceEventTimestamps?.joinToString(",").orEmpty())
                        putString("ecg_event_sample_counts", sourceEventSampleCounts?.joinToString(",").orEmpty())
                        if (data.containsKey("event_raw_values_rows")) {
                            putString("ecg_event_raw_values_rows", sourceEventRawValues)
                            putString("ecg_event_raw_values_status", sourceEventRawValuesStatus)
                            remove("ecg_event_metadata_rows")
                        } else {
                            putString("ecg_event_metadata_rows", sourceEventRawValues)
                            remove("ecg_event_raw_values_rows")
                            remove("ecg_event_raw_values_status")
                        }
                        if (data.containsKey("hal_sample_period_s")) {
                            val period = data.getDouble("hal_sample_period_s")
                            if (period.isFinite() && period in 0.0001..1.0) putFloat("ecg_hal_sample_period_s", period.toFloat())
                            else remove("ecg_hal_sample_period_s")
                        }
                        else remove("ecg_hal_sample_period_s")
                        if (data.containsKey("hal_sample_jitter_s")) {
                            val jitter = data.getDouble("hal_sample_jitter_s")
                            if (jitter.isFinite() && jitter >= 0.0 && jitter <= Float.MAX_VALUE) putFloat("ecg_hal_sample_jitter_s", jitter.toFloat())
                            else remove("ecg_hal_sample_jitter_s")
                        }
                        else remove("ecg_hal_sample_jitter_s")
                        if (ecgResearchDecode?.waveform?.isNotEmpty() == true) {
                            putString("ecg_research_waveform", ecgResearchDecode.waveform.joinToString(","))
                            putString("ecg_research_waveform_encoding", EcgHalEventResearchDecoder.ENCODING)
                            putInt("ecg_research_decoded_value_count", ecgResearchDecode.decodedSampleCount)
                            putInt("ecg_research_analysis_point_count", ecgResearchDecode.waveform.size)
                            putInt("ecg_research_decoded_event_count", ecgResearchDecode.eventCount)
                            putInt("ecg_research_missing_sample_count", ecgResearchDecode.missingSampleCount)
                            putString("ecg_research_decode_status", ecgResearchDecode.status)
                            putString("ecg_research_decode_state", ecgResearchDecode.decodeState)
                            ecgResearchDecode.estimatedSampleRateHz?.let {
                                putFloat("ecg_research_estimated_sample_rate_hz", it)
                            } ?: remove("ecg_research_estimated_sample_rate_hz")
                        } else {
                            remove("ecg_research_waveform")
                            remove("ecg_research_waveform_encoding")
                            remove("ecg_research_decoded_value_count")
                            remove("ecg_research_analysis_point_count")
                            remove("ecg_research_decoded_event_count")
                            remove("ecg_research_missing_sample_count")
                            putString("ecg_research_decode_status", ecgResearchDecode?.status ?: "not_attempted")
                            putString("ecg_research_decode_state", ecgResearchDecode?.decodeState ?: EcgHalEventResearchDecoder.DECODE_STATE_NO_EVENTS)
                            remove("ecg_research_estimated_sample_rate_hz")
                        }
                    }
                    if (kind in SENSOR_EVENT_METADATA_KINDS && data.containsKey("event_timestamps_ns")) {
                        putString("${kind}_event_timestamps_ns", eventTimestamps.joinToString(","))
                        putString("${kind}_event_sample_counts", eventSampleCounts.joinToString(","))
                        if (kind == "ecg") putString(
                            "ecg_event_sample_counts_semantics",
                            "wear_unpacker_emitted_points_zero_does_not_mean_no_raw_hal_values"
                        )
                    }
                    if (kind in SENSOR_EVENT_METADATA_KINDS && data.containsKey("event_timestamp_validity")) {
                        putString("${kind}_event_timestamp_validity", sourceEventTimestampValidity?.joinToString(",").orEmpty())
                        putString("${kind}_event_timestamp_validity_semantics", "source_timestamp_positive_only_not_clock_trust")
                    }
                    if (kind in SENSOR_EVENT_METADATA_KINDS && data.getBoolean("final")) {
                        putString("${kind}_event_raw_values_rows", sourceEventRawValues)
                        putString("${kind}_event_raw_values_status", sourceEventRawValuesStatus)
                    }
                    if (kind in SENSOR_EVENT_METADATA_KINDS && data.containsKey("event_callback_elapsed_realtime_ns")) {
                        val callbackTimesMatch = sourceEventCallbackTimes?.size == sourceEventTimestamps?.size
                        val callbackTimesCsv = if (callbackTimesMatch) {
                            sourceEventCallbackTimes?.joinToString(",").orEmpty()
                        } else ""
                        putString("${kind}_event_callback_elapsed_realtime_ns", callbackTimesCsv)
                    }
                    if (kind == "ppg" && data.containsKey("motion_capture_status")) {
                        putString("ppg_motion_capture_status", data.getString("motion_capture_status").orEmpty())
                        putInt("ppg_motion_event_count", motionTimestamps?.size ?: 0)
                        putString("ppg_motion_algorithm_version", ppgMotionEvidence?.algorithmVersion ?: "${PpgMotionEvidenceEngine.VERSION}")
                        putString("ppg_motion_status", ppgMotionEvidence?.reasonCode ?: "MOTION_EVIDENCE_UNAVAILABLE")
                        putInt("ppg_motion_paired_count", ppgMotionEvidence?.pairedObservationCount ?: 0)
                        ppgMotionEvidence?.matchedCoverage?.let { putFloat("ppg_motion_matched_coverage", it.toFloat()) }
                            ?: remove("ppg_motion_matched_coverage")
                        ppgMotionEvidence?.overlapOfPpgWindow?.let { putFloat("ppg_motion_overlap_of_ppg_window", it.toFloat()) }
                            ?: remove("ppg_motion_overlap_of_ppg_window")
                        ppgMotionEvidence?.overlapDurationNanos?.let { putLong("ppg_motion_overlap_duration_ns", it) }
                            ?: remove("ppg_motion_overlap_duration_ns")
                        ppgMotionEvidence?.dynamicAccelerationRmsMetersPerSecondSquared?.let {
                            putFloat("ppg_motion_dynamic_rms_mps2", it.toFloat())
                        } ?: remove("ppg_motion_dynamic_rms_mps2")
                        ppgMotionEvidence?.motionActivityIndex?.let { putFloat("ppg_motion_activity_index", it.toFloat()) }
                            ?: remove("ppg_motion_activity_index")
                        putString("ppg_motion_timestamps_ns", motionTimestamps?.joinToString(",").orEmpty())
                        putString("ppg_motion_x_mps2", motionX?.joinToString(",").orEmpty())
                        putString("ppg_motion_y_mps2", motionY?.joinToString(",").orEmpty())
                        putString("ppg_motion_z_mps2", motionZ?.joinToString(",").orEmpty())
                        putString("ppg_motion_timestamp_source", WATCH_SENSOR_EVENT_CLOCK_DOMAIN)
                        putLong("ppg_motion_maximum_pair_skew_ns", data.getLong("motion_maximum_pair_skew_ns", 0L))
                    } else if (kind == "ppg" && newMeasurementSession) {
                        listOf(
                            "ppg_motion_capture_status", "ppg_motion_status", "ppg_motion_algorithm_version",
                            "ppg_motion_timestamps_ns", "ppg_motion_x_mps2", "ppg_motion_y_mps2",
                            "ppg_motion_z_mps2", "ppg_motion_timestamp_source"
                        ).forEach(::remove)
                        listOf(
                            "ppg_motion_event_count", "ppg_motion_matched_coverage", "ppg_motion_overlap_of_ppg_window",
                            "ppg_motion_overlap_duration_ns", "ppg_motion_dynamic_rms_mps2",
                            "ppg_motion_activity_index", "ppg_motion_maximum_pair_skew_ns", "ppg_motion_paired_count"
                        ).forEach(::remove)
                    }
                    putString("${kind}_calibration", calibration.detail)
                    calibration.qualityScore?.let { putInt("${kind}_quality_score", it) }
                        ?: remove("${kind}_quality_score")
                    calibration.confidenceScore?.let { putInt("${kind}_confidence_score", it) }
                        ?: remove("${kind}_confidence_score")
                    if (kind in setOf("bioz", "bioz_respiration")) {
                        calibration.biozWaveformMedianPositionIndex?.let { putInt("${kind}_waveform_median_position_index", it) }
                            ?: remove("${kind}_waveform_median_position_index")
                        calibration.biozRelativeChangePercent?.takeIf(Double::isFinite)
                            ?.let { putFloat("${kind}_relative_change_percent", it.toFloat()) }
                            ?: remove("${kind}_relative_change_percent")
                        calibration.biozStabilityScore?.let { putInt("${kind}_stability_score", it) }
                            ?: remove("${kind}_stability_score")
                        putString("${kind}_timeline_source", "NORMALIZED_INDEX")
                        putString("${kind}_algorithm_version", BiomedicalCalibration.BIOZ_RESEARCH_VERSION)
                    }
                    listOf("minimum", "maximum", "average").forEach { statistic ->
                        val reading = data.getDouble(statistic).toFloat()
                        if (data.containsKey(statistic) && hasCountForStatistics && reading.isFinite()) {
                            putFloat("${kind}_$statistic", reading)
                        } else {
                            remove("${kind}_$statistic")
                        }
                    }
                    putLong("${kind}_time", sampleTime)
                    putLong("${kind}_published_at", incomingTime)
                    putString("${kind}_evidence_flags", data.getString("evidence_flags").orEmpty())
                    putInt("${kind}_evidence_flags_version", data.getInt("evidence_flags_version", 0))
                    putString(
                        "${kind}_timestamp_source",
                        data.getString("last_sample_timestamp_source")?.takeIf(String::isNotBlank)
                            ?: if (data.containsKey("last_sample_at_ms")) "watch_sample_timestamp_source_unreported"
                            else "watch_sample_timestamp_missing"
                    )
                    putLong("${kind}_session_started_at_ms", incomingSession)
                    putLong("${kind}_session_started_elapsed_realtime_ns", incomingSessionElapsedNs)
                    putLong("${kind}_producer_sequence", incomingSequence)
                    if (incomingSuiteId.isNotBlank()) putString("${kind}_suite_id", incomingSuiteId)
                    else remove("${kind}_suite_id")
                    putString("${kind}_source_sensor", data.getString("sensor"))
                    putInt("${kind}_event_count", eventCount)
                    putInt("${kind}_watch_event_count", eventCount)
                    putInt("${kind}_event_count_known", if (trustedEventCount != null) 1 else 0)
                    putBoolean("${kind}_final", data.getBoolean("final"))
                    putInt("${kind}_samples", pointSamples)
                    if (sourceRawValueCount != null) putInt("${kind}_raw_value_count", sourceRawValueCount)
                    else remove("${kind}_raw_value_count")
                    putInt("${kind}_source_raw_value_count", sourceRawValueCount ?: -1)
                    putInt("${kind}_preview_point_count", previewPointCount)
                    putInt("${kind}_source_preview_point_count", waveform.size)
                    putInt("${kind}_count_contract_version", contractVersion)
                    if (reportedEventCount != null) putInt("${kind}_reported_event_count", reportedEventCount)
                    else remove("${kind}_reported_event_count")
                    putInt("${kind}_channels", data.getInt("channels"))
                    putInt("${kind}_accuracy", data.getInt("accuracy"))
                    if (kind == "noise") {
                        if (data.containsKey("noise_dbfs")) {
                            putFloat("noise_dbfs", data.getDouble("noise_dbfs").toFloat())
                        } else if (data.containsKey("average")) {
                            putFloat("noise_dbfs", data.getDouble("average").toFloat())
                        }
                        if (data.containsKey("noise_dba")) {
                            putFloat("noise_dba", data.getDouble("noise_dba").toFloat())
                        }
                        putInt("noise_samples", data.getInt("samples"))
                        putLong("noise_time", sampleTime)
                    }
                    if (data.containsKey("chip_part")) putString("${kind}_chip_part", data.getString("chip_part"))
                    if (data.containsKey("feature_page")) putString("${kind}_feature_page", data.getString("feature_page"))
                    if (kind == "ecg") {
                        data.getString("sensor_profile_fingerprint")
                            ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                            ?.let { putString("ecg_sensor_profile_fingerprint", it) }
                            ?: remove("ecg_sensor_profile_fingerprint")
                    }
                    listOf(
                        "formula_pi_percent", "formula_relative_ac_dc", "formula_pulse_bpm", "formula_ratio_r",
                        "formula_rmssd_ms", "formula_nrmssd", "formula_sdnn_ms",
                        "formula_pnn50", "formula_cvrr", "formula_altitude_m", "formula_pressure_hpa",
                        "formula_sbp_mmhg", "formula_dbp_mmhg"
                    ).forEach { key ->
                        if (data.containsKey(key)) putFloat("${kind}_$key", data.getDouble(key).toFloat())
                    }
                    RawWaveformFormulaPolicy.keysToClearForSnapshot(kind, data.keySet()).forEach(::remove)
                    if (data.containsKey("formula_stress_score")) {
                        putInt("${kind}_formula_stress_score", data.getInt("formula_stress_score"))
                    } else remove("${kind}_formula_stress_score")
                    if (data.containsKey("formula_note")) putString("${kind}_formula_note", data.getString("formula_note"))
                    else remove("${kind}_formula_note")
                    if (data.containsKey("formula_citations")) putString("${kind}_formula_citations", data.getString("formula_citations"))
                    else remove("${kind}_formula_citations")
                    val featurePage = data.getString("feature_page").orEmpty()
                    val hrvSourceKinds = HrvResearchSnapshotPolicy.SOURCE_KINDS
                    val hrvFormulaKeys = HrvResearchSnapshotPolicy.FORMULA_KEYS
                    val hrvRrCount = data.getInt("formula_rr_count", data.getInt("rhythm_rr_count", 0))
                    val hrvCandidate = data.getBoolean("final") && kind in hrvSourceKinds &&
                        (data.containsKey("formula_rmssd_ms") || data.containsKey("formula_sdnn_ms")) &&
                        HrvSourceValidity.isFreshAndQualified(
                            sourceKind = kind,
                            measuredAt = sampleTime,
                            timestampSource = data.getString("last_sample_timestamp_source"),
                            now = System.currentTimeMillis(),
                            status = calibration.status.orEmpty(),
                            qualityScore = calibration.qualityScore ?: 0,
                            sampleRateHz = reportedSampleRate,
                            sampleCount = sourceRawValueCount ?: 0,
                            rrCount = hrvRrCount,
                            channelMappingVerified = data.getBoolean("channel_mapping_verified", false),
                            wearQualified = if (data.containsKey("wear_qualified")) data.getBoolean("wear_qualified") else null,
                            waveformEncoding = calibration.encoding
                        )
                    val acceptsHrvCandidate = hrvCandidate && HrvResearchSnapshotPolicy.shouldAcceptCandidate(
                        kind,
                        prefs.getString("hrv_research_source_kind", "").orEmpty(),
                        sampleTime,
                        prefs.getLong("hrv_research_time", 0L),
                        incomingSession,
                        prefs.getLong("hrv_research_session_started_at_ms", 0L),
                        incomingSessionElapsedNs,
                        prefs.getLong("hrv_research_session_started_elapsed_realtime_ns", 0L)
                    )
                    if (HrvResearchSnapshotPolicy.shouldClearExisting(
                            kind,
                            prefs.getString("hrv_research_source_kind", "").orEmpty(),
                            data.getBoolean("final"),
                            hrvCandidate,
                            sampleTime,
                            prefs.getLong("hrv_research_time", 0L),
                            incomingSession,
                            prefs.getLong("hrv_research_session_started_at_ms", 0L),
                            incomingSessionElapsedNs,
                            prefs.getLong("hrv_research_session_started_elapsed_realtime_ns", 0L)
                        )
                    ) {
                        HrvResearchSnapshotPolicy.keysToClear.forEach(::remove)
                    }
                    if (acceptsHrvCandidate) {
                        hrvFormulaKeys.forEach { key ->
                            if (data.containsKey(key)) putFloat("hrv_research_$key", data.getDouble(key).toFloat())
                            else remove("hrv_research_$key")
                        }
                        if (data.containsKey("formula_stress_score")) {
                            putInt("hrv_research_formula_stress_score", data.getInt("formula_stress_score"))
                        } else {
                            remove("hrv_research_formula_stress_score")
                        }
                        putInt("hrv_research_samples", sourceRawValueCount ?: 0)
                        putInt("hrv_research_rr_count", hrvRrCount)
                        putString("hrv_research_source_kind", kind)
                        putLong("hrv_research_time", sampleTime)
                        putString("hrv_research_timestamp_source", data.getString("last_sample_timestamp_source") ?: "watch_sample_timestamp_source_unreported")
                        putLong("hrv_research_session_started_at_ms", incomingSession)
                        putLong("hrv_research_session_started_elapsed_realtime_ns", incomingSessionElapsedNs)
                        putBoolean("hrv_research_final", true)
                        if (data.containsKey("formula_note")) putString("hrv_research_formula_note", data.getString("formula_note"))
                        else remove("hrv_research_formula_note")
                    }
                    // Phone-side formula enrichment when watch sent waveform without formula extras.
                    if (data.getBoolean("final") && kind in setOf("ppg", "max_hr", "max_heart_rate", "lifeq_ott", "ecg")) {
                        enrichFormulasFromWaveform(
                            editor = this,
                            kind = kind,
                            waveform = calibration.waveform,
                            sampleRateHz = reportedSampleRate.toDouble(),
                            alreadyHasPulse = data.containsKey("formula_pulse_bpm"),
                            allowInference = kind != "ppg" || RawWaveformFormulaPolicy.mayDerivePpgMetrics(
                                waveform = waveform,
                                sampleRateHz = reportedSampleRate.toDouble(),
                                timestampSource = data.getString("last_sample_timestamp_source", "UNKNOWN"),
                                channelMappingVerified = data.getBoolean("channel_mapping_verified", false),
                                wearQualified = data.getBoolean("wear_qualified", data.getBoolean("worn", false))
                            )
                        )
                    }
                    if (ecgDerivedRhythmAllowed) {
                        if (data.containsKey("rhythm_label")) putString("ecg_rhythm_label", data.getString("rhythm_label"))
                        if (data.containsKey("rhythm_title")) putString("ecg_rhythm_title", data.getString("rhythm_title"))
                        if (data.containsKey("rhythm_detail")) putString("ecg_rhythm_detail", data.getString("rhythm_detail"))
                        if (data.containsKey("rhythm_bpm")) putFloat("ecg_rhythm_bpm", data.getDouble("rhythm_bpm").toFloat())
                        if (data.containsKey("rhythm_rr_count")) putInt("ecg_rhythm_rr_count", data.getInt("rhythm_rr_count"))
                        putBoolean("ecg_rhythm_research_only", data.getBoolean("rhythm_research_only", true))
                    }
                    if (data.containsKey("wear_qualified")) putBoolean("${kind}_wear_qualified", data.getBoolean("wear_qualified")) else remove("${kind}_wear_qualified")
                    if (data.containsKey("worn")) putBoolean("${kind}_worn", data.getBoolean("worn")) else remove("${kind}_worn")
                    putString(
                        receiptLedgerKey,
                        RawSnapshotReceiptLedger.append(
                            prefs.getString(receiptLedgerKey, "").orEmpty(), receiptId
                        )
                    )
                    if (recoverableFeature is RawSnapshotFeatureRecoveryOutcome.Recovered) {
                        putString(pendingFeatureLedgerKey, pendingFeatureLedgerUpdate)
                    }
                    putString("${kind}_latest_snapshot_receipt_id", receiptId)
                        }
                        editor.commit().also {
                            persistenceFailure = !it
                            if (it) {
                                RawSnapshotReceiptRetryState.markKindPersisted(kind)
                            } else {
                                RawSnapshotReceiptRetryState.markCommitFailed(kind, receiptId)
                            }
                        }
                    }
                }
                if (!RawSnapshotCommitPolicy.canAcknowledgeDataItem(persistenceFailure)) {
                    Log.e(TAG, "sensor_snapshot_persist_failed kind=$kind; retaining DataItem for redelivery")
                    return
                }
                if (!snapshotStored) {
                    if (data.getBoolean("final")) {
                        recordRawSnapshotRejection(context, kind, commitRejection ?: "snapshot_commit_rejected")
                    }
                    scope.launch { WearDataItemAckTracker.delete(context, uri) }
                    return
                }
                if (!persistRawSnapshotDiagnostics(
                        context = context,
                        kind = kind,
                        data = data,
                        receiptId = receiptId
                    )) {
                    Log.e(TAG, "sensor_snapshot_diagnostics_persist_failed kind=$kind; retaining DataItem")
                    return
                }
                if (data.getBoolean("final")) {
                    Log.i(TAG, "sensor_snapshot_stored kind=$kind samples=$pointSamples status=${calibration.status ?: data.getString("status").orEmpty()}")
                    if (kind in SENSOR_EVENT_METADATA_KINDS) {
                        val storedRows = prefs.getString("${kind}_event_raw_values_rows", "").orEmpty()
                        Log.i(
                            TAG,
                            "sensor_event_metadata_persisted kind=$kind rows_chars=${storedRows.length} " +
                                "raw_status=${prefs.getString("${kind}_event_raw_values_status", "").orEmpty()} " +
                                "timestamp_chars=${prefs.getString("${kind}_event_timestamps_ns", "").orEmpty().length}"
                        )
                    }
                }
                // Keep Vitals feature snapshot fresh when optical / LifeQ publishes a plausible bpm.
                if (data.getBoolean("final")) {
                    val feature = (recoverableFeature as? RawSnapshotFeatureRecoveryOutcome.Recovered)?.feature
                    if (feature != null) {
                        if (!updateHeartRateFeature(context, feature, requireDurableNoop = true)) {
                            Log.e(TAG, "sensor_snapshot_feature_persist_failed kind=$kind; retaining DataItem")
                            return
                        }
                        val pendingFeatures = prefs.getString(pendingFeatureLedgerKey, "").orEmpty()
                        val pendingCleared = prefs.edit()
                            .putString(pendingFeatureLedgerKey, RawSnapshotReceiptLedger.remove(pendingFeatures, receiptId))
                            .commit()
                        if (!pendingCleared) {
                            Log.e(TAG, "sensor_snapshot_feature_pending_clear_failed kind=$kind; retaining DataItem")
                            return
                        }
                    }
                    writeSensorSuiteDumpSafely(context)
                }
            }
            scope.launch { acknowledgeArchivedSnapshot(context, uri, data.getString("kind").orEmpty(), data) }
            return
        }
        scope.launch {
            val isHeartRatePath = path == HeartRateDataListenerService.HEART_RATE_PATH ||
                path.startsWith("${HeartRateDataListenerService.HEART_RATE_PATH}/")
            var heartRateWriteDisposition: HeartRateWriter.WriteDisposition? = null
            val result = if (isHeartRatePath) {
                if (bpm !in 1..300 || !timestamp.isPlausibleTimestamp()) {
                    recordRejected(context, "無效心率封包：bpm=$bpm time=$timestamp")
                    WearDataItemAckTracker.delete(context, uri)
                    return@launch
                }
                val heartRatePrefs = RawSensorStore.open(context)
                val heartRateToWrite = if (timestamp >= heartRatePrefs.getLong("sensor_suite_session_started_at_ms", 0L)) {
                    try {
                        storeHealthServicesHeartRateSnapshot(context, bpm.toDouble(), timestamp, timestampSource)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.e(TAG, "health_services_snapshot_persist_failed; data_item_retained", error)
                        return@launch
                    }
                        ?: run {
                            WearDataItemAckTracker.delete(context, uri)
                            return@launch
                        }
                } else {
                    canonicalHeartRateForTimestamp(context, bpm, timestamp)
                }
                HeartRateWriter(context).write(heartRateToWrite, timestamp)
                    .onSuccess { heartRateWriteDisposition = it }
                    .map { Unit }
            } else if (path.startsWith(HeartRateDataListenerService.HEALTH_PATH_PREFIX)) {
                if (!WatchHealthMetricPolicy.isSupported(metric) || !value.isFinite() || value <= 0.0 ||
                    !start.isPlausibleTimestamp() || !end.isPlausibleTimestamp() || end <= start
                ) {
                    recordRejected(context, "無效健康封包：$metric value=$value start=$start end=$end")
                    WearDataItemAckTracker.delete(context, uri)
                    return@launch
                }
                StandardHealthWriter(context).write(metric, value, start, end, routePoints)
            } else return@launch
            val syncTime = if (path.startsWith(HeartRateDataListenerService.HEART_RATE_PATH)) timestamp else end
            result.exceptionOrNull()?.let { error ->
                context.getSharedPreferences(HeartRateDataListenerService.PREFS, Context.MODE_PRIVATE).edit()
                    .putString(HeartRateDataListenerService.KEY_LAST_ERROR, error.message ?: error.javaClass.simpleName)
                    .apply()
                return@launch
            }
            if (metric == "exercise" && routePoints.size >= 2) {
                val routePersisted = withContext(Dispatchers.IO) {
                    WorkoutRouteStore.save(context, start, end, routePoints)
                }
                if (!routePersisted) {
                    Log.e(TAG, "workout_route_persist_failed; data_item_retained")
                    return@launch
                }
            }
            if (path == HeartRateDataListenerService.HEART_RATE_PATH ||
                path.startsWith("${HeartRateDataListenerService.HEART_RATE_PATH}/")
            ) {
                SensorSuiteDump.write(context)
            }
            val syncPrefs = context.getSharedPreferences(HeartRateDataListenerService.PREFS, Context.MODE_PRIVATE)
            val syncCommitted = withContext(Dispatchers.IO) {
                syncPrefs.edit().apply {
                    if (isHeartRatePath) {
                        putLong(HeartRateDataListenerService.KEY_LAST_RECEIVED, timestamp)
                        if (heartRateWriteDisposition == HeartRateWriter.WriteDisposition.WRITTEN_TO_HEALTH_CONNECT) {
                            putLong(HeartRateDataListenerService.KEY_LAST_HEALTH_CONNECT_WRITE, System.currentTimeMillis())
                            putLong(HeartRateDataListenerService.KEY_LAST_SYNC, syncTime)
                        }
                    } else {
                        putLong(HeartRateDataListenerService.KEY_LAST_SYNC, syncTime)
                    }
                    remove(HeartRateDataListenerService.KEY_LAST_ERROR)
                }.commit()
            }
            if (!syncCommitted) {
                Log.e(TAG, "health_sync_status_persist_failed; data_item_retained")
                return@launch
            }
            WearDataItemAckTracker.delete(context, uri)
        }
    }

    private suspend fun acknowledgeArchivedSnapshot(
        context: Context,
        uri: android.net.Uri,
        kind: String,
        data: com.google.android.gms.wearable.DataMap
    ) {
        if (kind in setOf("ppg", "ecg", "bioz", "body_temperature_raw", "thermometer", "spo2_raw", "mi_spo2") && data.getBoolean("final")) {
            try {
                ArchiveAcknowledgmentGate.storeThenAcknowledge(store = {
                withContext(Dispatchers.IO) {
                    val values = data.getFloatArray("waveform")?.toList().orEmpty()
                    val counts = RawSensorCountContract.decode(
                        samples = data.getInt("samples", 0).takeIf { data.containsKey("samples") },
                        rawValueCount = data.getInt("raw_value_count", 0).takeIf { data.containsKey("raw_value_count") },
                        previewPointCount = data.getInt("preview_point_count", 0).takeIf { data.containsKey("preview_point_count") },
                        contractVersion = data.getInt("count_contract_version", 0).takeIf { data.containsKey("count_contract_version") },
                        eventCount = data.getInt("event_count", 0).takeIf { data.containsKey("event_count") }
                    )
                    val trustedEventCount = RawSensorCountContract.trustedEventCount(counts)
                    val reportedEventCount = counts.eventCount
                    val contractVersion = counts.contractVersion
                    PpgEvidenceArchive.appendPreview(
                        context.applicationContext, uri.toString(), values,
                        data.getString("waveform_encoding")?.takeIf(String::isNotBlank) ?: "hal_raw_encoding_unverified",
                        data.getLong("last_sample_at_ms"), data.getString("last_sample_timestamp_source"),
                        trustedEventCount,
                        RawSensorPayloadPolicy.sourceValueCount(counts.contractVersion, counts.rawValueCount),
                        counts.previewPointCount?.let { RawSensorPayloadPolicy.previewPointCount(it, values.size) } ?: values.size,
                        data.getLongArray("event_timestamps_ns")?.toList().orEmpty(),
                        data.getLongArray("event_sample_counts")?.map(PpgArchiveInputPolicy::eventSampleCount).orEmpty(),
                        eventTimestampValidity = data.getLongArray("event_timestamp_validity")
                            ?.map { it.toInt() }.orEmpty(),
                        kind = kind,
                        reportedEventCount = reportedEventCount,
                        countContractVersion = contractVersion,
                        publishedAt = data.getLong("timestamp"),
                        reportedRate = data.getDouble("sample_rate"),
                        evidenceFlags = data.getString("evidence_flags").orEmpty(),
                        evidenceFlagsVersion = data.getInt("evidence_flags_version", 0),
                        callbackTimestamps = data.getLongArray("event_callback_elapsed_realtime_ns")?.toList().orEmpty(),
                        previewPointEventTimestamps = data.getLongArray("preview_point_event_timestamps_ns")?.toList().orEmpty(),
                        previewPointEventTimestampValidity = data.getLongArray("preview_point_event_timestamp_validity")
                            ?.map { it.toInt() }.orEmpty(),
                        previewPointCallbackTimestamps = data.getLongArray("preview_point_callback_elapsed_realtime_ns")?.toList().orEmpty(),
                        motionCaptureStatus = data.getString("motion_capture_status"),
                        motionClockDomain = data.getString("motion_clock_domain"),
                        motionEventTimestamps = data.getLongArray("motion_event_timestamps_ns")?.toList().orEmpty(),
                        motionX = data.getFloatArray("motion_x_meters_per_second_squared")?.toList().orEmpty(),
                        motionY = data.getFloatArray("motion_y_meters_per_second_squared")?.toList().orEmpty(),
                        motionZ = data.getFloatArray("motion_z_meters_per_second_squared")?.toList().orEmpty(),
                        ppgPreviewClockDomain = data.getString("ppg_preview_clock_domain"),
                        motionSameSession = data.getBoolean("motion_same_session", false),
                        motionSameMonotonicClock = data.getBoolean("motion_same_monotonic_clock", false),
                        motionMaximumPairSkewNanos = data.getLong("motion_maximum_pair_skew_ns", 0L),
                        rawEventRows = data.getString("event_raw_values_rows").orEmpty(),
                        sourceSessionStartedAt = data.getLong("session_started_at_ms"),
                        sourceSessionElapsedNanos = data.getLong("session_started_elapsed_realtime_ns")
                    )
                }
                }, acknowledge = { WearDataItemAckTracker.delete(context, uri) })
                context.getSharedPreferences("ailisa_archive_status", Context.MODE_PRIVATE).edit()
                    .putString("${kind}_status", "stored_preview").putLong("updated_at", System.currentTimeMillis()).apply()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "$kind archive failed; retaining DataItem type=${error.javaClass.simpleName}")
                context.getSharedPreferences("ailisa_archive_status", Context.MODE_PRIVATE).edit()
                    .putString("${kind}_status", "archive_failed").putLong("updated_at", System.currentTimeMillis()).apply()
                return
            }
            return
        }
        WearDataItemAckTracker.delete(context, uri)
    }

    private fun Long.isPlausibleTimestamp(): Boolean {
        val now = System.currentTimeMillis()
        return this in EARLIEST_TIMESTAMP..(now + MAX_FUTURE_MILLIS)
    }

    private fun com.google.android.gms.wearable.DataMap.getDoubleOrNull(key: String): Double? =
        if (containsKey(key)) getDouble(key) else null

    private fun updateHeartRateFeature(
        context: Context,
        feature: HeartRateFeatureSnapshot,
        requireDurableNoop: Boolean
    ): Boolean {
        val prefs = context.getSharedPreferences(FEATURE_PREFS, Context.MODE_PRIVATE)
        return synchronized(FEATURE_LOCK) {
            val now = System.currentTimeMillis()
            val previous = prefs.getLong("whs_heart_rate_timestamp", 0L)
            val plan = HeartRateFeatureCommitPlanner.plan(
                bpm = feature.bpm,
                timestamp = feature.timestamp,
                previousTimestamp = previous,
                now = now,
                attemptId = UUID.randomUUID().toString(),
                sessionStartedAtMs = feature.sessionStartedAtMs,
                producerSequence = feature.producerSequence,
                previousSessionStartedAtMs = prefs.getLong("whs_heart_rate_session_started_at_ms", 0L),
                previousProducerSequence = prefs.getLong("whs_heart_rate_producer_sequence", 0L)
            ) ?: return@synchronized false
            val editor = prefs.edit()
            plan.measurement?.let {
                editor.putFloat("whs_heart_rate", it.bpm.toFloat())
                    .putLong("timestamp", maxOf(it.timestamp, prefs.getLong("timestamp", 0L)))
                    .putLong("whs_heart_rate_timestamp", it.timestamp)
                    .putString(
                        "whs_heart_rate_timestamp_source",
                        it.timestampSource?.takeIf { source -> HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(source) }
                            ?: "watch_sample_timestamp_source_unreported"
                    )
                    .putLong("whs_heart_rate_session_started_at_ms", it.sessionStartedAtMs)
                    .putLong("whs_heart_rate_producer_sequence", it.producerSequence)
            }
            if (plan.measurement == null && !requireDurableNoop) return@synchronized true
            editor.putString("whs_heart_rate_recovery_attempt_id", plan.attemptId).commit()
        }
    }

    private fun writeSensorSuiteDumpSafely(context: Context) {
        try {
            SensorSuiteDump.write(context)
        } catch (exception: Exception) {
            Log.e(TAG, "sensor_suite_dump_failed; persisted snapshot handling continues", exception)
        }
    }

    private fun storeHealthServicesHeartRateSnapshot(
        context: Context,
        bpm: Double,
        timestamp: Long,
        timestampSource: String?
    ): Long? {
        if (!bpm.isFinite() || bpm !in 30.0..220.0) return null
        if (!HealthAnalysisFreshnessPolicy.hasTrustedMeasurementTimestamp(timestampSource)) return null
        val featurePrefs = context.getSharedPreferences(FEATURE_PREFS, Context.MODE_PRIVATE)
        val rawPrefs = RawSensorStore.open(context)
        return synchronized(FEATURE_LOCK) {
            val now = System.currentTimeMillis()
            val previousFeatureTimestamp = featurePrefs.getLong("whs_heart_rate_timestamp", 0L)
            val previousSensorTimestamp = rawPrefs.getLong("whs_heart_rate_time", 0L)
            val canonicalBpm = FeatureSnapshotOrdering.canonicalHealthServicesHeartRateValue(
                incomingTimestamp = timestamp,
                previousFeatureTimestamp = previousFeatureTimestamp,
                previousSensorTimestamp = previousSensorTimestamp,
                incomingBpm = bpm,
                previousFeatureBpm = featurePrefs.getFloat("whs_heart_rate", Float.NaN).toDouble(),
                previousSensorBpm = rawPrefs.getFloat("whs_heart_rate_average", Float.NaN).toDouble(),
                now = now
            )
            if (canonicalBpm == null) return@synchronized null
            if (timestamp >= previousFeatureTimestamp) {
                val featureCommitted = featurePrefs.edit()
                    .putFloat("whs_heart_rate", canonicalBpm.toFloat())
                    .putLong("timestamp", maxOf(timestamp, featurePrefs.getLong("timestamp", 0L)))
                    .putLong("whs_heart_rate_timestamp", timestamp)
                    .putString("whs_heart_rate_timestamp_source", timestampSource)
                    .commit()
                check(featureCommitted) { "Health Services feature snapshot persistence failed." }
            }
            val rawCommitted = rawPrefs.edit()
                .putString("whs_heart_rate_label", "Health Services 心率")
                .putString("whs_heart_rate_sensor", "Wear Health Services")
                .putString("whs_heart_rate_source_sensor", "Wear Health Services")
                .putString("whs_heart_rate_vendor", "Google")
                .putString("whs_heart_rate_reporting_mode", "measure_client")
                .putString("whs_heart_rate_reason", "Health Services 單筆心率；非原始波形")
                .putString("whs_heart_rate_summary", "Health Services 心率：${"%.0f".format(canonicalBpm)} bpm")
                .putString("whs_heart_rate_status", "has_data")
                .putString("whs_heart_rate_data_provenance", "health_services")
                .putString("whs_heart_rate_latest", canonicalBpm.toString())
                .putString("whs_heart_rate_waveform", canonicalBpm.toString())
                .putString("whs_heart_rate_waveform_encoding", "scalar_health_services_bpm")
                .putString("whs_heart_rate_source_waveform", "")
                .putString("whs_heart_rate_source_waveform_encoding", "not_preserved")
                .putString("whs_heart_rate_candidate_summary", "")
                .putString("whs_heart_rate_calibration", "")
                .putFloat("whs_heart_rate_average", canonicalBpm.toFloat())
                .putFloat("whs_heart_rate_minimum", canonicalBpm.toFloat())
                .putFloat("whs_heart_rate_maximum", canonicalBpm.toFloat())
                .putFloat("whs_heart_rate_sample_rate", 0f)
                .putInt("whs_heart_rate_samples", 1)
                .putInt("whs_heart_rate_event_count", 1)
                .putInt("whs_heart_rate_channels", 1)
                .putInt("whs_heart_rate_accuracy", 0)
                .putInt("whs_heart_rate_quality_score", 0)
                .putInt("whs_heart_rate_candidate_count", 0)
                .putLong("whs_heart_rate_time", timestamp)
                .putString("whs_heart_rate_timestamp_source", timestampSource)
                .putLong("whs_heart_rate_session_started_at_ms", timestamp)
                .putBoolean("whs_heart_rate_final", true)
                .commit()
            check(rawCommitted) { "Health Services raw snapshot persistence failed." }
            canonicalBpm.toLong()
        }
    }

    private fun canonicalHeartRateForTimestamp(context: Context, bpm: Long, timestamp: Long): Long {
        val prefs = context.getSharedPreferences(FEATURE_PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong("whs_heart_rate_timestamp", 0L) != timestamp) return bpm
        return prefs.getFloat("whs_heart_rate", Float.NaN)
            .takeIf { it.isFinite() && it in 30f..220f }
            ?.toLong() ?: bpm
    }

    private fun recordRejected(context: Context, message: String) {
        context.getSharedPreferences(HeartRateDataListenerService.PREFS, Context.MODE_PRIVATE).edit()
            .putString(HeartRateDataListenerService.KEY_LAST_ERROR, message)
            .apply()
    }

    private fun recordRawSnapshotRejection(
        context: Context,
        kind: String,
        reason: String,
        rejectedData: com.google.android.gms.wearable.DataMap? = null
    ) {
        val prefs = context.getSharedPreferences(HeartRateDataListenerService.PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_RAW_SNAPSHOT_REJECTION_COUNT, 0)
        val category = RawSnapshotRejectionCategory.from(reason)
        val categoryKey = when (category) {
            RawSnapshotRejectionCategory.ORDERING -> KEY_RAW_SNAPSHOT_ORDERING_REJECTION_COUNT
            RawSnapshotRejectionCategory.PAYLOAD -> KEY_RAW_SNAPSHOT_PAYLOAD_REJECTION_COUNT
            RawSnapshotRejectionCategory.DUPLICATE -> KEY_RAW_SNAPSHOT_DUPLICATE_REJECTION_COUNT
        }
        val categoryCount = prefs.getInt(categoryKey, 0)
        prefs.edit()
            .putInt(KEY_RAW_SNAPSHOT_REJECTION_COUNT, if (count == Int.MAX_VALUE) count else count + 1)
            .putInt(categoryKey, if (categoryCount == Int.MAX_VALUE) categoryCount else categoryCount + 1)
            .putString(KEY_RAW_SNAPSHOT_LAST_REJECTED_KIND, kind.take(64))
            .putString(KEY_RAW_SNAPSHOT_LAST_REJECTION_REASON, reason)
            .putLong(KEY_RAW_SNAPSHOT_LAST_REJECTION_TIME, System.currentTimeMillis())
            .putInt("${kind}_last_rejected_payload_serialized_bytes", rejectedData?.getInt("payload_serialized_bytes", 0) ?: 0)
            .putLong("${kind}_last_rejected_sample_at_ms", rejectedData?.getLong("last_sample_at_ms", 0L) ?: 0L)
            .putString("${kind}_last_rejected_timestamp_source", rejectedData?.getString("last_sample_timestamp_source").orEmpty())
            .putInt("${kind}_last_rejected_event_count", rejectedData?.getInt("event_count", 0) ?: 0)
            .putInt("${kind}_last_rejected_sample_count", rejectedData?.getInt("samples", 0) ?: 0)
            .putLong("${kind}_last_rejected_producer_sequence", rejectedData?.getLong("producer_sequence", 0L) ?: 0L)
            .putLong("${kind}_last_rejected_session_started_at_ms", rejectedData?.getLong("session_started_at_ms", 0L) ?: 0L)
            .putLong("${kind}_last_rejected_published_at_ms", rejectedData?.getLong("timestamp", 0L) ?: 0L)
            .putLong("${kind}_last_rejected_enqueue_requested_at_ms", rejectedData?.getLong("producer_enqueue_requested_at_ms", 0L) ?: 0L)
            .putString("${kind}_last_rejected_payload_timestamp_source", rejectedData?.getString("payload_timestamp_source").orEmpty())
            .apply()
        SensorSuiteDump.write(context)
        Log.w(TAG, "sensor_snapshot_rejected kind=${kind.take(64)} category=${category.name.lowercase()} reason=$reason")
    }

    private const val TAG = "HealthBridgePhoneSync"
    private const val WATCH_SENSOR_EVENT_CLOCK_DOMAIN = "wear_sensor_event_timestamp_elapsed_realtime_nanos"

    private data class CalibrationWrite(
        val waveform: List<Float>,
        val encoding: String,
        val summary: String?,
        val status: String?,
        val reason: String?,
        val detail: String,
        val qualityScore: Int?,
        val confidenceScore: Int? = null,
        val ecgRPeakIndices: List<Int>? = null,
        val ecgRPeakIndexDistancesSamples: List<Int>? = null,
        val ecgTimelineSource: String? = null,
        val ecgCalibrationProfileId: String? = null,
        val ecgRhythmPatternTitle: String? = null,
        val ppgDominantPeriodSamples: Int? = null,
        val ppgPeriodCorrelation: Double? = null,
        val displayWaveform: List<Float>? = null,
        val biozDisplayWaveform: List<Float>? = null,
        val biozWaveformMedianPositionIndex: Int? = null,
        val biozRelativeChangePercent: Double? = null,
        val biozStabilityScore: Int? = null
    )

    private fun enrichFormulasFromWaveform(
        editor: android.content.SharedPreferences.Editor,
        kind: String,
        waveform: List<Float>,
        sampleRateHz: Double,
        alreadyHasPulse: Boolean,
        allowInference: Boolean
    ) {
        if (!allowInference || !RawWaveformFormulaPolicy.mayRunOpticalAlgorithms(kind)) return
        if (waveform.size < 32 || !sampleRateHz.isFinite() || sampleRateHz < 20.0) return
        val optical: ClinicalSensorFormulas.OpticalMetrics
        val hrv: ClinicalSensorFormulas.HrvMetrics
        try {
            optical = ClinicalSensorFormulas.opticalFromWaveform(waveform, sampleRateHz)
            hrv = ClinicalSensorFormulas.hrvFromRr(optical.rrIntervalsMs)
        } catch (_: RuntimeException) {
            return
        }
        if (!alreadyHasPulse) {
            optical.perfusionIndexPercent?.let { editor.putFloat("${kind}_formula_pi_percent", it.toFloat()) }
            optical.pulseRateBpm?.let { editor.putFloat("${kind}_formula_pulse_bpm", it.toFloat()) }
            optical.ratioOfRatiosR?.let { editor.putFloat("${kind}_formula_ratio_r", it.toFloat()) }
        }
        if (hrv.rrCount >= 3) {
            hrv.rmssdMs?.let { editor.putFloat("${kind}_formula_rmssd_ms", it.toFloat()) }
            hrv.nRmssd?.let { editor.putFloat("${kind}_formula_nrmssd", it.toFloat()) }
            hrv.sdnnMs?.let { editor.putFloat("${kind}_formula_sdnn_ms", it.toFloat()) }
            hrv.pNn50?.let { editor.putFloat("${kind}_formula_pnn50", it.toFloat()) }
            hrv.cvRr?.let { editor.putFloat("${kind}_formula_cvrr", it.toFloat()) }
            ClinicalSensorFormulas.researchStressScore(hrv.nRmssd, hrv.bpm ?: optical.pulseRateBpm)?.let {
                editor.putInt("${kind}_formula_stress_score", it)
            }
        }
    }

    private fun applyCalibration(
        context: Context,
        kind: String,
        waveform: List<Float>,
        sampleRate: Float,
        latest: List<Double>,
        eventCount: Int = 0,
        sampleCount: Int = 0,
        measuredAtMillis: Long = 0L,
        timestampSource: String = "UNKNOWN",
        channelMappingVerified: Boolean = false,
        wearQualified: Boolean = false,
        ecgClockEvidence: ClockEvidence? = null,
        sensorProfileFingerprint: String? = null,
        sourceEncoding: String? = null
    ): CalibrationWrite {
        if (!RawSensorPayloadPolicy.hasAcquisitionEvidence(eventCount, sampleCount, waveform.size, latest.size)) {
            return CalibrationWrite(
                waveform = waveform,
                encoding = sourceEncoding ?: "unknown",
                summary = null,
                status = null,
                reason = null,
                detail = "no_sensor_events;acquisition_result_preserved",
                qualityScore = null
            )
        }
        return when (kind) {
            "ppg" -> {
                val result = BiomedicalCalibration.refinePpg(
                    waveform, sampleRate, timestampSource, channelMappingVerified, wearQualified
                )
                CalibrationWrite(
                    waveform = result.waveform.ifEmpty { waveform },
                    encoding = "ppg_cleaned_flattened_unmapped",
                    summary = result.summary,
                    status = BiomedicalCalibration.ppgMeasurementStatus(result),
                    reason = result.summary,
                    detail = buildString {
                        append("quality=${result.qualityScore};valid=${result.valid}")
                        append(";pr=${result.pulseRateBpm};rr=${result.rrCount}")
                        append(";fs=${result.effectiveSampleRateHz}")
                        append(";confidence=${result.confidenceScore};source=${result.source};timestamp=${result.timestampSource};calibration=${result.calibrationStatus}")
                        result.comparisons.forEach { append("\n· $it") }
                    },
                    qualityScore = result.qualityScore,
                    confidenceScore = result.confidenceScore,
                    ppgDominantPeriodSamples = result.dominantPeriodSamples,
                    ppgPeriodCorrelation = result.periodCorrelation,
                    displayWaveform = result.displayWaveform
                )
            }
            "ecg" -> {
                val profile = EcgResearchProfileStore(context).read()
                    ?.takeIf {
                        it.inputEncoding == sourceEncoding &&
                            it.isBoundToSensorProfile(sensorProfileFingerprint)
                    }
                val result = BiomedicalCalibration.calibrateEcg(
                    waveform,
                    sampleRate,
                    clockEvidence = ecgClockEvidence,
                    waveformMappingVerified = channelMappingVerified,
                    wearQualified = wearQualified,
                    calibration = profile?.toCoreCalibrationProfile(),
                    waveformEncoding = sourceEncoding,
                    sensorProfileFingerprint = sensorProfileFingerprint
                )
                val mvWave = result.researchMvWaveform.orEmpty()
                val displayWave = when {
                    mvWave.size >= 32 -> mvWave
                    else -> result.waveform.ifEmpty { waveform }
                }
                val status = if (profile != null && mvWave.size >= 32) {
                    "calibrated_external_reference"
                } else {
                    "research_signal_unscaled"
                }
                CalibrationWrite(
                    waveform = displayWave,
                    encoding = if (profile != null && mvWave.size >= 32) {
                        "ecg_external_reference_mv"
                    } else {
                        "ecg_baseline_relative_unitless"
                    },
                    summary = if (profile != null && mvWave.size >= 32) {
                        "ECG 外部參考 mV 預覽 · slope=${"%.6g".format(profile.slopeMvPerRaw)} · " +
                            "offset=${"%.4f".format(profile.offsetMv)} mV · RMSE=${"%.4f".format(profile.rmseMv)} mV · " +
                            "來源 ${profile.source}（研究用，非診斷）"
                    } else {
                        result.summary
                    },
                    status = status,
                    reason = result.summary,
                    detail = buildString {
                        append("contact=${result.contactScore};hr=${result.heartRateBpm};rr=${result.rrCount};cv=${result.rrCv}")
                        append(";status=$status")
                        if (profile != null) {
                            append(";profile_id=${profile.profileId}")
                            append(";ext_slope=${profile.slopeMvPerRaw};ext_offset=${profile.offsetMv}")
                            append(";ext_rmse=${profile.rmseMv};ext_points=${profile.points}")
                            append(";ext_source=${profile.source}")
                        }
                        result.comparisons.forEach { append("\n· $it") }
                        append("\n· 無外部 raw→mV profile 時維持未縮放研究波形；有 profile 亦不解鎖 AF／醫療診斷")
                    },
                    qualityScore = result.contactScore,
                    ecgRPeakIndices = result.rPeakIndices,
                    ecgRPeakIndexDistancesSamples = result.rPeakIndexDistancesSamples,
                    ecgTimelineSource = result.timelineSource,
                    ecgCalibrationProfileId = profile?.profileId?.takeIf { mvWave.size >= 32 },
                    ecgRhythmPatternTitle = result.rhythmPattern?.title
                )
            }
            "bioz", "bioz_respiration" -> {
                val result = BiomedicalCalibration.calibrateBioz(latest, waveform)
                CalibrationWrite(
                    waveform = result.waveform.ifEmpty { waveform },
                    biozDisplayWaveform = result.displayWaveform,
                    encoding = "bioz_raw_unit_unknown",
                    summary = if (kind == "bioz_respiration") {
                        "呼吸研究（BioZ 原始）：${result.summary}"
                    } else result.summary,
                    status = if (result.waveform.isNotEmpty() || latest.isNotEmpty()) {
                        "research_signal_unscaled"
                    } else {
                        "no_events"
                    },
                    reason = result.summary,
                    detail = buildString {
                        append("R=${result.resistanceOhm};X=${result.reactanceOhm};Z=${result.impedanceOhm}")
                        append(";hydration=${result.hydrationIndex};mode=${result.mode}")
                        append(";waveform_median_position=${result.waveformMedianPositionIndex};relative_dispersion_percent=${result.stabilityScore}")
                        append(";relative_change_percent=${result.relativeChangePercent}")
                        append(";confidence=${result.confidenceScore}")
                        append(";raw_channel_values=${BiozRawChannelEvidence.encode(result.rawChannelValues)}")
                        result.comparisons.forEach { append("\n· $it") }
                        append("\n· BIOZ 無 OEM Ω／體成分／呼吸率係數；僅研究用原始訊號品質")
                    },
                    qualityScore = result.contactScore,
                    confidenceScore = result.confidenceScore,
                    biozWaveformMedianPositionIndex = result.waveformMedianPositionIndex,
                    biozRelativeChangePercent = result.relativeChangePercent,
                    biozStabilityScore = result.stabilityScore
                )
            }
            "spo2_raw", "mi_spo2" -> {
                val assessment = Spo2RawQualityPolicy.assess(waveform, sampleRate)
                CalibrationWrite(
                    waveform = waveform,
                    encoding = "spo2_vendor_unit_unverified",
                    summary = Spo2RawQualityPolicy.summary(assessment),
                    status = if (waveform.isNotEmpty() || latest.isNotEmpty()) "has_data" else "no_events",
                    reason = "Android Sensor API 事件值沒有公開百分比單位或 OEM 校準曲線",
                    detail = BiomedicalRawDetailText.spo2Preview(waveform.size) +
                        "\n· finite_points=${assessment.finiteSampleCount};total_points=${assessment.totalSampleCount};finite_coverage_percent=${assessment.finiteCoveragePercent ?: "unknown"};quality=${assessment.score ?: "unknown"};quality_status=${assessment.status};source=raw_optical;unit_status=unverified" +
                        "\n· 請使用 Health Connect 的 OxygenSaturationRecord.percentage 或合格參考血氧計取得百分比。",
                    qualityScore = assessment.score
                )
            }
            "body_temperature_raw", "thermometer" -> {
                val assessment = TemperatureSensorValuePolicy.assess(waveform, latest)
                val rawValue = assessment.deviceRawValue
                val wear = context.getSharedPreferences(FEATURE_PREFS, Context.MODE_PRIVATE)
                    .let { if (it.contains("worn")) it.getBoolean("worn", false) else null }
                val verdict = ClinicalVerdict.forTemperature(
                    rawValue,
                    waveform.size.coerceAtLeast(latest.size),
                    assessment.status,
                    wear,
                    source = "wear_sensor:$kind",
                    measuredAtMillis = measuredAtMillis,
                    timestampSource = timestampSource,
                    calibrationStatus = "UNVERIFIED"
                )
                CalibrationWrite(
                    waveform = waveform,
                    encoding = "device_temperature_raw_unverified",
                    summary = if (assessment.status == "invalid_sensor_values") {
                        "收到溫度感測事件，但沒有有限原始數值"
                    } else verdict.headline,
                    status = assessment.status,
                    reason = if (assessment.status == "invalid_sensor_values") {
                        "已收到事件但數值非有限；保留原始資料且不作生理判讀"
                    } else "裝置溫度通道單位未驗證；不換算、不作生理判讀",
                    detail = buildString {
                        append("device_raw_value=${rawValue};unit_status=unverified;status=${assessment.status};score=unknown;grade=research")
                        TemperatureResearchSummary.calculate(waveform)?.let {
                            append("\n· ").append(it.displayText())
                        }
                        append("\n· 不輸出體溫、皮膚溫度或臨床比較")
                    },
                    qualityScore = null
                )
            }
            "whs_heart_rate", "heart_rate_raw" -> {
                val assessment = HeartRateSensorValuePolicy.assess(latest, waveform, eventCount, sampleCount)
                val bpm = assessment.bpm
                val cal = ClinicalMetricCalibration.forHeartRate(bpm, resting = false)
                CalibrationWrite(
                    waveform = waveform,
                    encoding = "hr_clinical_compare",
                    summary = cal.headline,
                    status = assessment.status,
                    reason = buildString {
                        append(cal.comparisons.joinToString("；"))
                        if (assessment.status == "invalid_sensor_values") {
                            append("；已收到原始值，但未能解析出合理心率")
                        }
                    },
                    detail = buildString {
                        append("bpm=${bpm};range_reference=${cal.grade};signal_quality=unscored")
                        cal.comparisons.forEach { append("\n· $it") }
                    },
                    qualityScore = null
                )
            }
            "max_hr", "lifeq_ott" -> {
                // MAX86178 / LifeQ optical packets — ADC counts, NOT bpm.
                val usable = waveform.filter {
                    it.isFinite() && it != ClinicalSensorFormulas.OPTICAL_FILL
                }
                val optical = ClinicalSensorFormulas.opticalFromWaveform(usable.ifEmpty { waveform }, sampleRate.toDouble())
                val pulse = optical.pulseRateBpm?.takeIf { it in 30.0..220.0 }
                val span = if (usable.size >= 2) usable.max() - usable.min() else 0f
                val valid = usable.size >= 12 && span >= 24f || pulse != null
                CalibrationWrite(
                    waveform = usable.ifEmpty { waveform },
                    encoding = "optical_packed_adc",
                    summary = when {
                        pulse != null -> "光學封包推算脈搏 %.0f bpm（非 HAL 直出心率）".format(pulse)
                        valid -> "光學封包可用 · 尚無穩定脈搏估計"
                        else -> "光學封包振幅／填充不足"
                    },
                    status = when {
                        pulse != null -> "has_data"
                        valid -> "research_optical"
                        else -> "invalid_optical"
                    },
                    reason = optical.note,
                    detail = buildString {
                        append("adc_points=${usable.size};pulse=${pulse};pi=${optical.perfusionIndexPercent}")
                        append("\n· max_hr／LifeQ OTT 為光學 ADC 封包，不可把平均計數當 bpm")
                        append("\n· ${optical.note}")
                    },
                    qualityScore = BiomedicalCalibration.researchOpticalQuality(waveform, usable, span)
                )
            }
            else -> CalibrationWrite(
                waveform = waveform,
                encoding = "packed_or_scalar",
                summary = null,
                status = null,
                reason = null,
                detail = "",
                qualityScore = null
            )
        }
    }

    private fun dataStatusFallback(waveform: List<Float>): String =
        if (waveform.isNotEmpty()) "has_data" else "no_events"

    private fun persistRawSnapshotDiagnostics(
        context: Context,
        kind: String,
        data: com.google.android.gms.wearable.DataMap,
        receiptId: String
    ): Boolean {
        val diagnostics = context.getSharedPreferences("sensor_acquisition_diagnostics", Context.MODE_PRIVATE)
        return synchronized(RAW_SNAPSHOT_DIAGNOSTICS_LOCK) {
            val ledgerKey = "${kind}_raw_snapshot_diagnostic_receipts"
            val receipts = diagnostics.getString(ledgerKey, "").orEmpty()
            if (!RawSnapshotDiagnosticsPolicy.shouldRecord(receipts, receiptId)) {
                if (!RawSnapshotDiagnosticsPolicy.needsDurabilityRetry(kind)) return@synchronized true
                val retrySucceeded = diagnostics.edit()
                    .putString("${kind}_diagnostics_durability_checkpoint", UUID.randomUUID().toString())
                    .commit()
                RawSnapshotDiagnosticsPolicy.recordCommitResult(kind, retrySucceeded)
                return@synchronized retrySucceeded
            }

            val incomingSession = data.getLong("session_started_at_ms")
            val receivedSession = diagnostics.getLong("${kind}_phone_session_started_at", 0L)
            val incomingSessionId = data.getString("suite_id").orEmpty()
            val receivedSessionId = diagnostics.getString("${kind}_phone_suite_id", "").orEmpty()
            val editor = diagnostics.edit().putString(ledgerKey, RawSnapshotReceiptLedger.append(receipts, receiptId))
            val nextCount = RawSnapshotDiagnosticsPolicy.nextReceivedCount(
                incomingSession,
                receivedSession,
                diagnostics.getInt("${kind}_phone_received_count", 0),
                incomingSessionId,
                receivedSessionId,
                data.getLong("last_sample_at_ms", 0L),
                diagnostics.getLong("${kind}_watch_last_sample_at", 0L)
            )
            if (nextCount != null) {
                val incomingTime = data.getLong("timestamp")
                editor.putInt("${kind}_phone_received_count", nextCount)
                    .putLong("${kind}_phone_session_started_at", incomingSession)
                    .putString("${kind}_phone_suite_id", incomingSessionId)
                    .putLong("${kind}_phone_last_received_at", System.currentTimeMillis())
                    .putLong("${kind}_producer_enqueue_requested_at", data.getLong("producer_enqueue_requested_at_ms", incomingTime))
                    .putString("${kind}_watch_registration", data.getString("registration_result", "unknown"))
                    .putLong("${kind}_watch_session_started_at", incomingSession)
                    .putLong("${kind}_watch_first_sample_at", data.getLong("first_sample_at_ms", 0L))
                    .putLong("${kind}_watch_last_sample_at", data.getLong("last_sample_at_ms", 0L))
                    .putString("${kind}_watch_sample_timestamp_source", data.getString("last_sample_timestamp_source", "unknown"))
                    .putInt("${kind}_watch_event_count", data.getInt("event_count", 0).coerceAtLeast(0))
                    .putString("${kind}_watch_stop_reason", data.getString("acquisition_stop_reason", "unknown"))
            }
            val succeeded = editor.commit()
            RawSnapshotDiagnosticsPolicy.recordCommitResult(kind, succeeded)
            succeeded
        }
    }

    private const val EARLIEST_TIMESTAMP = 1_577_836_800_000L
    private const val MAX_FUTURE_MILLIS = 5 * 60 * 1000L
    const val KEY_RAW_SNAPSHOT_REJECTION_COUNT = "raw_snapshot_rejection_count"
    const val KEY_RAW_SNAPSHOT_ORDERING_REJECTION_COUNT = "raw_snapshot_ordering_rejection_count"
    const val KEY_RAW_SNAPSHOT_PAYLOAD_REJECTION_COUNT = "raw_snapshot_payload_rejection_count"
    const val KEY_RAW_SNAPSHOT_DUPLICATE_REJECTION_COUNT = "raw_snapshot_duplicate_rejection_count"
    const val KEY_RAW_SNAPSHOT_LAST_REJECTED_KIND = "raw_snapshot_last_rejected_kind"
    const val KEY_RAW_SNAPSHOT_LAST_REJECTION_REASON = "raw_snapshot_last_rejection_reason"
    const val KEY_RAW_SNAPSHOT_LAST_REJECTION_TIME = "raw_snapshot_last_rejection_time"
    const val RAW_SENSOR_PREFS = "raw_sensor_results"
    const val FEATURE_PREFS = "watch_feature_snapshot"
    private val FEATURE_LOCK = Any()
    private val RAW_SNAPSHOT_DIAGNOSTICS_LOCK = Any()
    private val RAW_SNAPSHOT_PROCESS_LOCK = Any()
}

internal object RawWaveformFormulaPolicy {
    val opticalDerivedKeys = listOf(
        "formula_pi_percent", "formula_relative_ac_dc", "formula_pulse_bpm", "formula_ratio_r", "formula_rmssd_ms",
        "formula_nrmssd", "formula_sdnn_ms", "formula_pnn50", "formula_cvrr", "formula_stress_score"
    )

    val sessionDerivedKeys = opticalDerivedKeys + listOf(
        "formula_altitude_m", "formula_pressure_hpa", "formula_sbp_mmhg", "formula_dbp_mmhg",
        "formula_note", "formula_citations", "formula_bp_category", "formula_bp_method",
        "formula_bp_confidence", "formula_bp_advice", "formula_rr_count"
    )

    fun mayRunOpticalAlgorithms(kind: String): Boolean = kind == "ppg"

    fun mayDerivePpgMetrics(
        waveform: List<Float>,
        sampleRateHz: Double,
        timestampSource: String?,
        channelMappingVerified: Boolean,
        wearQualified: Boolean
    ): Boolean = waveform.size >= 32 &&
        waveform.all { it.isFinite() && it != 524287f } &&
        sampleRateHz.isFinite() && sampleRateHz >= 20.0 &&
        timestampSource == "android_sensor_event_elapsed_realtime" &&
        channelMappingVerified && wearQualified

    fun keysToClearForSnapshot(kind: String, presentKeys: Set<String>): List<String> = buildList {
        addAll(sessionDerivedKeys.filterNot(presentKeys::contains).map { "${kind}_$it" })
        if (kind in SENSOR_EVENT_METADATA_KINDS) {
            val eventMetadataFields = listOf(
                "event_timestamps_ns" to "event_timestamps_ns",
                "event_timestamp_validity" to "event_timestamp_validity",
                "event_timestamp_validity_semantics" to "event_timestamp_validity_semantics",
                "event_sample_counts" to "event_sample_counts",
                "event_sample_counts_semantics" to "event_sample_counts_semantics",
                "event_callback_elapsed_realtime_ns" to "event_callback_elapsed_realtime_ns",
                "event_raw_values_rows" to "event_raw_values_rows",
                "event_raw_values_status" to "event_raw_values_status"
            )
            eventMetadataFields.filterNot { (inputKey, _) ->
                inputKey in presentKeys || (inputKey == "event_raw_values_rows" && kind == "ecg" && "event_metadata_rows" in presentKeys)
            }.forEach { (_, storedKey) -> add("${kind}_$storedKey") }
        }
    }.distinct()

    fun keysToClearForNewSession(kind: String): List<String> = buildList {
        addAll(sessionDerivedKeys.map { "${kind}_$it" })
        addAll(listOf(
            "${kind}_hal_additional_info_supported",
            "${kind}_hal_sample_period_s",
            "${kind}_hal_sample_jitter_s",
            "${kind}_quality_score",
            "${kind}_confidence_score",
            "${kind}_source_waveform",
            "${kind}_source_waveform_encoding"
        ))
        when (kind) {
            "ecg" -> addAll(listOf(
                "ecg_event_timestamps_ns",
                "ecg_event_timestamp_validity",
                "ecg_event_timestamp_validity_semantics",
                "ecg_event_callback_elapsed_realtime_ns",
                "ecg_event_sample_counts",
                "ecg_event_sample_counts_semantics",
                "ecg_event_metadata_rows",
                "ecg_event_raw_values_rows",
                "ecg_event_raw_values_status",
                "ecg_hal_sample_period_s",
                "ecg_hal_sample_jitter_s"
            ))
            in SENSOR_EVENT_METADATA_KINDS -> addAll(listOf(
                "${kind}_event_timestamps_ns",
                "${kind}_event_timestamp_validity",
                "${kind}_event_timestamp_validity_semantics",
                "${kind}_event_callback_elapsed_realtime_ns",
                "${kind}_event_sample_counts",
                "${kind}_event_sample_counts_semantics",
                "${kind}_event_raw_values_rows",
                "${kind}_event_raw_values_status"
            ))
        }
        if (kind in setOf("bioz", "bioz_respiration")) addAll(listOf(
            "${kind}_waveform_median_position_index",
            "${kind}_relative_change_percent",
            "${kind}_stability_score",
            "${kind}_timeline_source",
            "${kind}_algorithm_version"
        ))
        if (kind == "bioz") add("bioz_display_waveform")
        if (kind == "ecg") addAll(listOf(
            "ecg_research_waveform",
            "ecg_research_waveform_encoding",
            "ecg_research_decoded_value_count",
            "ecg_research_analysis_point_count",
            "ecg_research_decoded_event_count",
            "ecg_research_missing_sample_count",
            "ecg_research_decode_status",
            "ecg_research_decode_state",
            "ecg_research_estimated_sample_rate_hz"
        ))
        if (kind == "ppg") add("ppg_display_waveform")
    }.distinct()
}

internal object EcgCalibrationSnapshotPolicy {
    fun activeProfileId(profileId: String?, waveformEncoding: String): String? =
        profileId?.takeIf {
            it.isNotBlank() && waveformEncoding == "ecg_external_reference_mv"
        }
}

internal object HrvResearchSnapshotPolicy {
    val SOURCE_KINDS = setOf("ppg", "max_hr", "max_heart_rate", "lifeq_ott", "ecg")
    val FORMULA_KEYS = listOf(
        "formula_rmssd_ms", "formula_nrmssd", "formula_sdnn_ms",
        "formula_pnn50", "formula_cvrr", "formula_pulse_bpm"
    )
    val keysToClear = FORMULA_KEYS.map { "hrv_research_$it" } + listOf(
        "hrv_research_formula_stress_score",
        "hrv_research_time",
        "hrv_research_source_kind",
        "hrv_research_samples",
        "hrv_research_rr_count",
        "hrv_research_final",
        "hrv_research_formula_note",
        "hrv_research_session_started_at_ms",
        "hrv_research_session_started_elapsed_realtime_ns"
    )

    fun shouldAcceptCandidate(
        incomingKind: String,
        previousSourceKind: String,
        incomingMeasuredAt: Long,
        existingMeasuredAt: Long,
        incomingSessionStartedAt: Long,
        existingSessionStartedAt: Long,
        incomingSessionElapsedNs: Long,
        existingSessionElapsedNs: Long
    ): Boolean {
        if (incomingKind !in SOURCE_KINDS) return false
        if (previousSourceKind.isBlank()) return true
        val sameSession = incomingSessionStartedAt == existingSessionStartedAt &&
            incomingSessionElapsedNs > 0L && incomingSessionElapsedNs == existingSessionElapsedNs
        val incomingSessionIsNewer = incomingSessionStartedAt > existingSessionStartedAt ||
            (incomingSessionStartedAt == existingSessionStartedAt && incomingSessionElapsedNs > existingSessionElapsedNs)
        return if (previousSourceKind == incomingKind && sameSession) {
            incomingMeasuredAt >= existingMeasuredAt
        } else {
            incomingMeasuredAt > existingMeasuredAt ||
                (incomingMeasuredAt == existingMeasuredAt && incomingSessionIsNewer)
        }
    }

    fun shouldClearExisting(
        incomingKind: String,
        previousSourceKind: String,
        isFinal: Boolean,
        hasQualifiedCandidate: Boolean,
        incomingMeasuredAt: Long,
        existingMeasuredAt: Long,
        incomingSessionStartedAt: Long,
        existingSessionStartedAt: Long,
        incomingSessionElapsedNs: Long,
        existingSessionElapsedNs: Long
    ): Boolean {
        if (!isFinal || incomingKind !in SOURCE_KINDS || hasQualifiedCandidate) return false
        if (previousSourceKind.isBlank()) return true
        val sameSession = incomingSessionStartedAt == existingSessionStartedAt &&
            incomingSessionElapsedNs > 0L && incomingSessionElapsedNs == existingSessionElapsedNs
        val incomingSessionIsNewer = incomingSessionStartedAt > existingSessionStartedAt ||
            (incomingSessionStartedAt == existingSessionStartedAt && incomingSessionElapsedNs > existingSessionElapsedNs)
        return if (previousSourceKind == incomingKind && sameSession) {
            incomingMeasuredAt >= existingMeasuredAt
        } else {
            incomingMeasuredAt > existingMeasuredAt ||
                (incomingMeasuredAt == existingMeasuredAt && incomingSessionIsNewer)
        }
    }
}

internal object SensorSuiteSessionPolicy {
    const val SESSION_KEY = "suite_session_started_at_ms"
    const val SUITE_ID_KEY = "suite_session_id"
    const val RUN_STATE_KEY = "suite_run_state"
    val TERMINAL_STATES = setOf("completed", "partial", "cancelled", "timed_out", "interrupted", "failed")

    fun hasChronologicalTerminalTime(eventTimestamp: Long, suiteStartedAt: Long): Boolean =
        suiteStartedAt > 0L && eventTimestamp >= suiteStartedAt

    fun runStateForSuiteStart(reportedRunState: String, eventTimestamp: Long, suiteStartedAt: Long): String =
        if (reportedRunState in TERMINAL_STATES &&
            !hasChronologicalTerminalTime(eventTimestamp, suiteStartedAt)
        ) "running" else reportedRunState

    fun belongsToLatestSuite(
        rowSessionStartedAt: Long,
        suiteSessionStartedAt: Long,
        rowSuiteId: String = "",
        latestSuiteId: String = ""
    ): Boolean = if (rowSuiteId.isNotBlank() || latestSuiteId.isNotBlank()) {
        rowSuiteId.isNotBlank() && rowSuiteId == latestSuiteId
    } else {
        rowSessionStartedAt > 0L && suiteSessionStartedAt > 0L && rowSessionStartedAt >= suiteSessionStartedAt
    }

    fun terminalBelongsToCurrentSuite(
        incomingSuiteId: String,
        currentSuiteId: String,
        eventTimestamp: Long,
        incomingSuiteStartedAt: Long,
        currentSuiteStartedAt: Long
    ): Boolean = when {
        incomingSuiteId.isNotBlank() && incomingSuiteId == currentSuiteId ->
            hasChronologicalTerminalTime(eventTimestamp, currentSuiteStartedAt) &&
                (incomingSuiteStartedAt <= 0L || incomingSuiteStartedAt == currentSuiteStartedAt)
        incomingSuiteId.isNotBlank() && incomingSuiteStartedAt > currentSuiteStartedAt ->
            eventTimestamp >= incomingSuiteStartedAt
        incomingSuiteId.isBlank() && currentSuiteId.isBlank() ->
            currentSuiteStartedAt > 0L && eventTimestamp >= currentSuiteStartedAt
        else -> false
    }

    fun shouldAcceptSuiteTerminal(
        incomingSuiteId: String,
        currentSuiteId: String,
        eventTimestamp: Long,
        incomingSuiteStartedAt: Long,
        currentSuiteStartedAt: Long,
        currentRunState: String,
        previousTerminalTimestamp: Long
    ): Boolean {
        if (eventTimestamp <= previousTerminalTimestamp) return false
        val sameSuite = incomingSuiteId.isNotBlank() && incomingSuiteId == currentSuiteId
        if (sameSuite && currentRunState in TERMINAL_STATES) return false
        return terminalBelongsToCurrentSuite(
            incomingSuiteId, currentSuiteId, eventTimestamp, incomingSuiteStartedAt, currentSuiteStartedAt
        )
    }

    fun displayRunState(runState: String, suiteStartedAt: Long, now: Long): String = when {
        runState == "running" && suiteStartedAt > 0L && now >= suiteStartedAt + RUN_TERMINAL_GRACE_MILLIS ->
            "terminal_status_missing"
        else -> runState
    }

    fun runStateLabel(runState: String): String = when (runState) {
        "running" -> "進行中"
        "completed" -> "流程完成（不代表量測有效）"
        "partial" -> "部分完成"
        "cancelled" -> "已取消"
        "timed_out" -> "逾時"
        "interrupted" -> "中斷"
        "failed" -> "啟動失敗"
        "terminal_status_missing" -> "缺少手錶終態回報"
        else -> "尚無狀態"
    }

    private const val RUN_TERMINAL_GRACE_MILLIS = 8 * 60_000L

    fun terminalState(message: String, success: Boolean): String? = when {
        message.contains("盤點完成") || message.contains("全感測器盤點完成") -> if (success) "completed" else "partial"
        message.startsWith("掃描已達") -> "timed_out"
        message.startsWith("掃描被") -> "cancelled"
        message.startsWith("掃描因") -> "interrupted"
        else -> null
    }
}
