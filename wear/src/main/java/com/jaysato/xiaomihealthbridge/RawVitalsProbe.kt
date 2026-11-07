package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventCallback
import android.hardware.SensorAdditionalInfo
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

class RawVitalsProbe(
    context: Context,
    private val kind: Kind,
    private val onSnapshot: (Snapshot) -> Unit,
    private val emitIntermediateSnapshots: Boolean = true
) : SensorEventCallback() {
    constructor(
        context: Context,
        kind: Kind,
        onSnapshot: (Snapshot) -> Unit
    ) : this(context, kind, onSnapshot, true)

    private val manager = context.getSystemService(SensorManager::class.java)
    private val appContext = context.applicationContext
    private var eventEpochOffsetMs = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val candidates = manager.getSensorList(Sensor.TYPE_ALL).filter { it.stringType == kind.stringType }
        .ifEmpty {
            when (kind) {
                Kind.ORIENTATION -> manager.getSensorList(Sensor.TYPE_ORIENTATION)
                Kind.STEP_DETECTOR -> manager.getSensorList(Sensor.TYPE_STEP_DETECTOR)
                Kind.WRIST_TILT -> manager.getSensorList(TYPE_WRIST_TILT_GESTURE)
                Kind.SIGNIFICANT_MOTION -> manager.getSensorList(Sensor.TYPE_SIGNIFICANT_MOTION)
                Kind.STATIONARY -> manager.getSensorList(Sensor.TYPE_STATIONARY_DETECT)
                Kind.MOTION -> manager.getSensorList(Sensor.TYPE_MOTION_DETECT)
                else -> emptyList()
            }
        }
    private val candidateSummary = candidates.take(MAX_CANDIDATE_DIAGNOSTICS).joinToString(";") { candidate ->
        listOf(
            candidate.name.take(MAX_SENSOR_NAME_LENGTH),
            candidate.vendor.take(MAX_SENSOR_VENDOR_LENGTH),
            "type=${candidate.type}",
            "wake=${candidate.isWakeUpSensor}",
            "minDelay=${candidate.minDelay}",
            "mode=${reportingModeName(candidate.reportingMode)}"
        ).joinToString("|")
    }
    private val sensor = candidates.firstOrNull { !it.isWakeUpSensor }
        ?: candidates.firstOrNull()
        ?: when (kind) {
            Kind.ORIENTATION -> manager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
            Kind.STEP_DETECTOR -> manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            Kind.WRIST_TILT -> manager.getDefaultSensor(TYPE_WRIST_TILT_GESTURE)
            Kind.SIGNIFICANT_MOTION -> manager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
            Kind.STATIONARY -> manager.getDefaultSensor(Sensor.TYPE_STATIONARY_DETECT)
            Kind.MOTION -> manager.getDefaultSensor(Sensor.TYPE_MOTION_DETECT)
            else -> null
        }
    private val wakeup = candidates.firstOrNull { it.isWakeUpSensor }
    private val offBodySensor = if (kind in setOf(
        Kind.ECG, Kind.SPO2, Kind.MI_SPO2, Kind.BODY_TEMPERATURE, Kind.THERMOMETER,
        Kind.PPG, Kind.MAX_HEART_RATE, Kind.LIFE_Q_OTT
    )) {
        manager.getSensorList(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT).firstOrNull { !it.isWakeUpSensor }
            ?: manager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
    } else null
    private var lastOffBodyTimestampNs = 0L
    private var lastOnBody: Boolean? = null
    private var startedAt = 0L
    private var sessionStartedElapsedRealtimeNs = 0L
    private var samples = 0
    private var firstSampleAtEpochMs = 0L
    private var lastSampleAtEpochMs = 0L
    private var sampleTimestampSource = "unknown"
    private var registrationResult = "unknown"
    private var acquisitionStopReason = "running"
    private var channelCount = 0
    private var accuracy = SensorManager.SENSOR_STATUS_NO_CONTACT
    private var minimum = Double.POSITIVE_INFINITY
    private var maximum = Double.NEGATIVE_INFINITY
    private var sum = 0.0
    private var finiteSampleCount = 0L
    private var latest = emptyList<Double>()
    private val waveform = ArrayDeque<Float>()
    private val opticalRows = ArrayDeque<FloatArray>()
    private val ppgPreviewEventTimestampsNs = ArrayDeque<Long>()
    private val ppgPreviewEventTimestampValidity = ArrayDeque<Int>()
    private val ppgPreviewCallbackTimestampsNs = ArrayDeque<Long>()
    private val eventTimestampsNs = ArrayDeque<Long>()
    private val eventTimestampValidity = ArrayDeque<Int>()
    private val eventCallbackElapsedRealtimeNs = ArrayDeque<Long>()
    private val eventSampleCounts = ArrayDeque<Int>()
    private val eventRawValueRows = ArrayDeque<String>()
    private val ecgEventContactStates = ArrayDeque<Int>()
    private var eventRawValueChars = 0
    private var eventRawValuesSizeRejected = false
    private var eventRawValuesTruncated = false
    private var halSamplePeriodSeconds: Double? = null
    private var halSampleJitterSeconds: Double? = null
    private var triggerArmed = false
    private var triggerRegistrationSensor: Sensor? = null
    private var activeSensor: Sensor? = null
    private var running = false
    private val wakeupFallback = Runnable { switchToWakeupIfNoSamples() }
    private val triggerListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            if (!running || event.sensor != activeSensor) return
            val extractedCount = ingest(event.values, event.sensor, event.timestamp)
            recordEventMetadata(event.values, event.timestamp, extractedCount)
            triggerArmed = false
            triggerRegistrationSensor = null
            if (emitIntermediateSnapshots) onSnapshot(snapshot())
        }
    }

    val sensorName: String? get() = (activeSensor ?: sensor ?: wakeup)?.name
    val vendor: String get() = (activeSensor ?: sensor ?: wakeup)?.vendor.orEmpty()
    val reportingMode: String get() = reportingModeName((activeSensor ?: sensor ?: wakeup)?.reportingMode)

    fun start(): Boolean {
        handler.removeCallbacks(wakeupFallback)
        unregisterSensorListeners()
        running = false
        activeSensor = null
        startedAt = System.currentTimeMillis()
        sessionStartedElapsedRealtimeNs = SystemClock.elapsedRealtimeNanos()
        eventEpochOffsetMs = startedAt - SystemClock.elapsedRealtime()
        samples = 0
        firstSampleAtEpochMs = 0L
        lastSampleAtEpochMs = 0L
        sampleTimestampSource = "unknown"
        registrationResult = "unknown"
        acquisitionStopReason = "running"
        channelCount = 0
        latest = emptyList()
        waveform.clear()
        opticalRows.clear()
        ppgPreviewEventTimestampsNs.clear()
        ppgPreviewEventTimestampValidity.clear()
        ppgPreviewCallbackTimestampsNs.clear()
        eventTimestampsNs.clear()
        eventTimestampValidity.clear()
        eventCallbackElapsedRealtimeNs.clear()
        eventSampleCounts.clear()
        eventRawValueRows.clear()
        ecgEventContactStates.clear()
        lastOffBodyTimestampNs = 0L
        lastOnBody = null
        eventRawValueChars = 0
        eventRawValuesSizeRejected = false
        eventRawValuesTruncated = false
        halSamplePeriodSeconds = null
        halSampleJitterSeconds = null
        minimum = Double.POSITIVE_INFINITY
        maximum = Double.NEGATIVE_INFINITY
        sum = 0.0
        finiteSampleCount = 0L
        val selected = sensor ?: wakeup
        if (selected == null) {
            registrationResult = "sensor_not_exposed"
            acquisitionStopReason = "sensor_not_exposed"
            SensorAcquisitionDiagnostics.recordStart(appContext, kind.wireName, false)
            SensorAcquisitionDiagnostics.recordStop(appContext, kind.wireName, acquisitionStopReason, 0, 0L, 0L)
            return false
        }
        val started = SensorRegistrationGuard.run(
            register = {
                if (isOneShot(selected)) {
                    activeSensor = selected
                    triggerRegistrationSensor = selected
                    triggerArmed = manager.requestTriggerSensor(triggerListener, selected)
                    if (!triggerArmed) triggerRegistrationSensor = null
                    running = triggerArmed
                    triggerArmed
                } else {
                    val delay = samplingDelayUs(selected, kind)
                    var fallbackSelected = false
                    val started = SensorRegistrationGuard.runPrimaryThenFallback(
                        primary = { manager.registerListener(this, selected, delay, handler) },
                        fallback = {
                            val fallbackSensor = wakeup?.takeIf { it != selected } ?: return@runPrimaryThenFallback false
                            fallbackSelected = true
                            manager.registerListener(this, fallbackSensor, samplingDelayUs(fallbackSensor, kind), handler)
                        },
                        onPrimaryRuntimeFailure = { error ->
                            Log.w(TAG, "primary_sensor_registration_failed kind=${kind.wireName}", error)
                        }
                    )
                    if (started) {
                        activeSensor = if (fallbackSelected) wakeup else selected
                        running = true
                        if (!fallbackSelected && wakeup != null && wakeup != selected) {
                            handler.postDelayed(wakeupFallback, WAKEUP_FALLBACK_DELAY_MILLIS)
                        }
                        manager.flush(this)
                        true
                    } else {
                        activeSensor = null
                        false
                    }
                }
            },
            onPermissionDenied = { error ->
                Log.w(TAG, "sensor_registration_denied kind=${kind.wireName}", error)
                failRegistration("permission_denied")
            },
            onRuntimeFailure = { error ->
                Log.e(TAG, "sensor_start_failed kind=${kind.wireName}", error)
                failRegistration("registration_exception")
            }
        )
        registrationResult = if (started) "registered" else
            SensorRegistrationGuard.terminalFailureReason(registrationResult)
        if (started && offBodySensor != null) {
            runCatching {
                manager.registerListener(this, offBodySensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
            }.onFailure { error -> Log.w(TAG, "offbody_registration_failed kind=${kind.wireName}", error) }
        }
        if (!started) acquisitionStopReason =
            SensorRegistrationGuard.terminalFailureReason(acquisitionStopReason)
        SensorAcquisitionDiagnostics.recordStart(appContext, kind.wireName, started)
        if (!started) {
            SensorAcquisitionDiagnostics.recordStop(appContext, kind.wireName, acquisitionStopReason, 0, 0L, 0L)
        }
        return started
    }

    private fun failRegistration(reason: String): Boolean {
        handler.removeCallbacks(wakeupFallback)
        unregisterSensorListeners("registration_failure_cleanup_failed")
        activeSensor = null
        running = false
        registrationResult = reason
        acquisitionStopReason = reason
        return false
    }

    fun stop(stopReason: String? = null): Snapshot {
        handler.removeCallbacks(wakeupFallback)
        running = false
        if (acquisitionStopReason == "running") {
            acquisitionStopReason = AcquisitionStopPolicy.resolve(stopReason, samples > 0, status(null))
        }
        val result = snapshot(fullWaveform = true)
        unregisterSensorListeners()
        SensorAcquisitionDiagnostics.recordStop(
            appContext, kind.wireName, acquisitionStopReason, samples,
            firstSampleAtEpochMs, lastSampleAtEpochMs, sampleTimestampSource
        )
        return result
    }

    private fun unregisterSensorListeners(failureEvent: String = "sensor_cleanup_failed") {
        val cleanupActions = mutableListOf<() -> Unit>({ manager.unregisterListener(this) })
        triggerRegistrationSensor?.let { selected -> cleanupActions += { manager.cancelTriggerSensor(triggerListener, selected) } }
        SensorRegistrationGuard.cleanup(cleanupActions) { error ->
            Log.w(TAG, "$failureEvent kind=${kind.wireName}", error)
        }
        triggerArmed = false
        triggerRegistrationSensor = null
        activeSensor = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (running && event.sensor == offBodySensor) {
            lastOnBody = event.values.firstOrNull()?.let { value ->
                if (value.isFinite()) value == 1f else null
            }
            lastOffBodyTimestampNs = event.timestamp
            if (emitIntermediateSnapshots && samples > 0 && kind != Kind.ECG) onSnapshot(snapshot())
            return
        }
        if (!running || event.sensor != activeSensor) return
        val extractedCount = ingest(event.values, event.sensor, event.timestamp)
        recordEventMetadata(event.values, event.timestamp, extractedCount)
        if (emitIntermediateSnapshots) {
            if (kind == Kind.ECG) {
                if (samples == 1 || samples % ECG_UPDATE_INTERVAL == 0) onSnapshot(snapshot())
            } else if (samples % UPDATE_INTERVAL == 0) {
                onSnapshot(snapshot())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (!running || sensor != activeSensor) return
        this.accuracy = accuracy
    }

    private fun recordEventMetadata(values: FloatArray, timestampNanos: Long, extractedCount: Int) {
        if (!WearSensorEventMetadataPolicy.supports(kind)) return
        eventTimestampsNs.addLast(timestampNanos)
        eventTimestampValidity.addLast(if (WearSensorEventMetadataPolicy.sourceTimestampPresent(timestampNanos)) 1 else 0)
        eventCallbackElapsedRealtimeNs.addLast(SystemClock.elapsedRealtimeNanos())
        eventSampleCounts.addLast(extractedCount)
        if (!eventRawValuesSizeRejected) {
            if (values.size > MAX_RAW_EVENT_VALUES_PER_ROW) {
                eventRawValueRows.clear()
                ecgEventContactStates.clear()
                eventRawValueChars = 0
                eventRawValuesSizeRejected = true
            } else {
                val rawRow = values.joinToString(",")
                while (EcgRawRetentionPolicy.shouldEvict(
                    eventRawValueChars, rawRow.length, eventRawValueRows.isNotEmpty(), MAX_ECG_RAW_VALUE_CHARS
                )) {
                    eventRawValueChars -= eventRawValueRows.removeFirst().length + if (eventRawValueRows.isEmpty()) 0 else 1
                    if (kind == Kind.ECG) ecgEventContactStates.removeFirst()
                    eventTimestampsNs.removeFirst()
                    eventTimestampValidity.removeFirst()
                    eventCallbackElapsedRealtimeNs.removeFirst()
                    eventSampleCounts.removeFirst()
                    eventRawValuesTruncated = true
                }
                val addedChars = rawRow.length + if (eventRawValueRows.isEmpty()) 0 else 1
                if (rawRow.length > MAX_ECG_RAW_VALUE_CHARS || eventRawValueChars + addedChars > MAX_ECG_RAW_VALUE_CHARS) {
                    eventRawValueRows.clear()
                    ecgEventContactStates.clear()
                    eventRawValueChars = 0
                    eventRawValuesSizeRejected = true
                } else {
                    eventRawValueRows.addLast(rawRow)
                    if (kind == Kind.ECG) ecgEventContactStates.addLast(
                        EcgEventPreview.contactAtEvent(timestampNanos, lastOffBodyTimestampNs, lastOnBody)
                    )
                    eventRawValueChars += addedChars
                }
            }
        }
        while (eventTimestampsNs.size > MAX_EVENT_TIMESTAMP_ROWS) {
            eventTimestampsNs.removeFirst()
            eventTimestampValidity.removeFirst()
            eventCallbackElapsedRealtimeNs.removeFirst()
            eventSampleCounts.removeFirst()
            if (eventRawValueRows.isNotEmpty()) {
                eventRawValueChars -= eventRawValueRows.removeFirst().length + 1
                if (kind == Kind.ECG) ecgEventContactStates.removeFirst()
                eventRawValuesTruncated = true
            }
        }
    }

    private fun switchToWakeupIfNoSamples() {
        val fallback = wakeup ?: return
        val current = activeSensor ?: return
        if (!running || samples > 0 || fallback == current) return
        var cleanupFailed = false
        val started = SensorRegistrationGuard.runAfterCleanup(
            cleanup = { manager.unregisterListener(this, current) },
            register = { manager.registerListener(this, fallback, samplingDelayUs(fallback, kind), handler) },
            onCleanupFailure = { error ->
                cleanupFailed = true
                acquisitionStopReason = "fallback_unregister_exception"
                Log.w(TAG, "fallback_sensor_unregister_failed kind=${kind.wireName}", error)
            },
            onPermissionDenied = { error ->
                acquisitionStopReason = "permission_denied"
                Log.w(TAG, "fallback_sensor_registration_denied kind=${kind.wireName}", error)
                false
            },
            onRuntimeFailure = { error ->
                acquisitionStopReason = "registration_exception"
                Log.w(TAG, "fallback_sensor_registration_failed kind=${kind.wireName}", error)
                false
            }
        )
        if (cleanupFailed) {
            running = false
            return
        }
        if (!started && acquisitionStopReason == "running") acquisitionStopReason = "registration_failed"
        if (!started) registrationResult = acquisitionStopReason
        activeSensor = fallback.takeIf { started }
        running = started
    }

    override fun onSensorAdditionalInfo(info: SensorAdditionalInfo) {
        if (kind !in RAW_WAVEFORM_KINDS || info.sensor != activeSensor ||
            info.type != SensorAdditionalInfo.TYPE_SAMPLING) return
        val period = info.floatValues?.getOrNull(0)?.toDouble()
        val jitter = info.floatValues?.getOrNull(1)?.toDouble()
        halSamplePeriodSeconds = validHalSamplePeriodSeconds(period)
        halSampleJitterSeconds = validHalSampleJitterSeconds(jitter)
    }

    private fun ingest(values: FloatArray, source: Sensor, eventTimestampNanos: Long): Int {
        samples++
        val sampleTime = SensorEventEpochTimeResolver.resolve(
            eventTimestampNanos = eventTimestampNanos,
            capturedElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            epochOffsetMillis = eventEpochOffsetMs,
            callbackWallClockMillis = System.currentTimeMillis()
        )
        sampleTimestampSource = sampleTime.source
        if (firstSampleAtEpochMs == 0L) firstSampleAtEpochMs = sampleTime.epochMillis
        lastSampleAtEpochMs = sampleTime.epochMillis
        channelCount = max(channelCount, values.size)
        latest = values.take(MAX_CHANNELS).map(Float::toDouble)
        if (kind in setOf(Kind.PPG, Kind.MAX_HEART_RATE, Kind.LIFE_Q_OTT)) {
            opticalRows.addLast(values.copyOf())
            if (kind == Kind.PPG) {
                ppgPreviewEventTimestampsNs.addLast(eventTimestampNanos)
                ppgPreviewEventTimestampValidity.addLast(if (WearSensorEventMetadataPolicy.sourceTimestampPresent(eventTimestampNanos)) 1 else 0)
                ppgPreviewCallbackTimestampsNs.addLast(SystemClock.elapsedRealtimeNanos())
            }
            while (opticalRows.size > MAX_OPTICAL_ROWS) opticalRows.removeFirst()
            while (kind == Kind.PPG && ppgPreviewEventTimestampsNs.size > MAX_OPTICAL_ROWS) {
                ppgPreviewEventTimestampsNs.removeFirst()
                ppgPreviewEventTimestampValidity.removeFirst()
                ppgPreviewCallbackTimestampsNs.removeFirst()
            }
        }
        val extracted = unpackWaveform(kind, values)
        extracted.forEach { sample ->
            waveform.addLast(sample)
            val limit = when {
                kind == Kind.ECG -> MAX_ECG_WAVEFORM_SAMPLES
                kind in setOf(Kind.BODY_TEMPERATURE, Kind.THERMOMETER) -> MAX_EVENT_TIMESTAMP_ROWS
                else -> MAX_WAVEFORM_SAMPLES
            }
            while (waveform.size > limit) waveform.removeFirst()
            val value = sample.toDouble()
            if (value.isFinite()) {
                minimum = min(minimum, value)
                maximum = max(maximum, value)
                sum += value
                finiteSampleCount++
            }
        }
        if (emitIntermediateSnapshots && source.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) {
            onSnapshot(snapshot())
        }
        return extracted.size
    }

    private fun snapshot(fullWaveform: Boolean = false): Snapshot {
        val elapsedNanos = max(1L, SystemClock.elapsedRealtimeNanos() - sessionStartedElapsedRealtimeNs)
        val finiteMin = minimum.takeIf(Double::isFinite)
        val finiteMax = maximum.takeIf(Double::isFinite)
        val average = if (finiteSampleCount > 0) sum / finiteSampleCount else null
        val worn = when (kind) {
            // Android TYPE_LOW_LATENCY_OFFBODY_DETECT: 1.0 = on-body, 0.0 = off-body.
            Kind.OFF_BODY -> latest.firstOrNull()?.let { isOnBody(it) }
            Kind.SPO2, Kind.MI_SPO2, Kind.BODY_TEMPERATURE, Kind.THERMOMETER,
            Kind.PPG, Kind.MAX_HEART_RATE, Kind.LIFE_Q_OTT -> when (EcgEventPreview.contactAtEvent(
                eventTimestampsNs.lastOrNull() ?: 0L, lastOffBodyTimestampNs, lastOnBody
            )) {
                1 -> true
                -1 -> false
                else -> null
            }
            else -> null
        }
        val rawValueCount = if (kind.hasRawValueCount) {
            finiteSampleCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else {
            null
        }
        val reportedSamples = rawValueCount ?: samples.toInt()
        val opticalKind = kind in setOf(Kind.PPG, Kind.MAX_HEART_RATE, Kind.LIFE_Q_OTT)
        val selectedWaveform = if (opticalKind) selectedOpticalWaveform() else null
        val opticalCheck = selectedWaveform?.let { ChipCalibrationProfiles.assessOpticalWaveform(it.toList()) }
        val rate = reportedSampleRateHz(kind, halSamplePeriodSeconds)
        val eventRate = eventRatePerSessionSecond(samples, elapsedNanos)
        val snapReason = when {
            opticalCheck?.valid == false -> opticalCheck.advice
            opticalCheck?.valid == true && rate > 0.0 -> "已取得光學封包及 HAL 回報時基，但通道映射未經裝置驗證；保留原始訊號，不計算 BPM／HRV／SpO₂"
            opticalCheck?.valid == true -> "已取得光學封包，但缺少可信通道映射與 HAL 取樣時基；保留原始訊號，不計算 BPM／HRV／SpO₂"
            else -> reason(worn)
        }
        return Snapshot(
            kind = kind,
            sensorName = sensorName.orEmpty(),
            vendor = vendor,
            reportingMode = reportingMode,
            samples = reportedSamples,
            finiteValueCount = rawValueCount,
            eventCount = samples,
            sampleRateHz = rate,
            eventRatePerSessionSecond = eventRate,
            sessionStartedAtEpochMs = startedAt,
            sessionStartedElapsedRealtimeNs = sessionStartedElapsedRealtimeNs,
            firstSampleAtEpochMs = firstSampleAtEpochMs,
            lastSampleAtEpochMs = lastSampleAtEpochMs,
            sampleTimestampSource = sampleTimestampSource,
            registrationResult = registrationResult,
            acquisitionStopReason = acquisitionStopReason,
            channels = channelCount,
            accuracy = accuracy,
            minimum = finiteMin,
            maximum = finiteMax,
            average = average,
            latest = latest,
            waveform = if (kind in setOf(Kind.PPG, Kind.MAX_HEART_RATE, Kind.LIFE_Q_OTT)) {
                selectedWaveform ?: waveform.toFloatArray()
            } else if (kind == Kind.ECG) {
                val aligned = if (fullWaveform) eventSampleCounts.sum().coerceAtMost(MAX_ECG_WAVEFORM_SAMPLES)
                else MAX_WAVEFORM_SAMPLES
                waveform.takeLast(aligned).toFloatArray()
            } else waveform.toFloatArray(),
            eventTimestampsNs = eventTimestampsNs.toLongArray(),
            eventTimestampValidity = eventTimestampValidity.toIntArray(),
            eventCallbackElapsedRealtimeNs = eventCallbackElapsedRealtimeNs.toLongArray(),
            previewPointEventTimestampsNs = if (kind == Kind.PPG) ppgPreviewEventTimestampsNs.toLongArray() else longArrayOf(),
            previewPointEventTimestampValidity = if (kind == Kind.PPG) ppgPreviewEventTimestampValidity.toIntArray() else intArrayOf(),
            previewPointCallbackElapsedRealtimeNs = if (kind == Kind.PPG) ppgPreviewCallbackTimestampsNs.toLongArray() else longArrayOf(),
            eventSampleCounts = eventSampleCounts.toIntArray(),
            eventRawValueRows = eventRawValueRows.toList(),
            ecgEventContactStates = ecgEventContactStates.toIntArray(),
            eventRawValuesStatus = when {
                !WearSensorEventMetadataPolicy.supports(kind) -> "not_applicable"
                eventRawValuesSizeRejected -> "rejected_size_limit"
                eventRawValuesTruncated -> "bounded_recent_events"
                else -> "complete_for_observed_events"
            },
            halAdditionalInfoSupported = (activeSensor ?: sensor ?: wakeup)?.isAdditionalInfoSupported ?: false,
            halSamplePeriodSeconds = halSamplePeriodSeconds,
            halSampleJitterSeconds = halSampleJitterSeconds,
            worn = worn,
            status = if (opticalKind && samples > 0) "unscaled_optical" else status(worn),
            reason = snapReason,
            candidateSummary = candidateSummary,
            candidateCount = candidates.size
        )
    }

    private fun status(worn: Boolean?): String = when {
        sensor == null && wakeup == null -> "missing"
        samples > 0 && worn == true -> "worn"
        samples > 0 && worn == false -> "off_wrist"
        samples > 0 -> "has_data"
        needsMotionTrigger(sensor ?: wakeup) || kind.needsMotion -> "needs_motion"
        kind.needsContact -> "needs_contact"
        kind.needsOemMode -> "needs_oem_mode"
        else -> "no_events"
    }

    private fun reason(worn: Boolean?): String = when {
        sensor == null && wakeup == null -> "裝置未暴露此 stringType"
        samples > 0 && worn == true -> "佩戴合格，已取得硬體事件"
        samples > 0 && worn == false -> "硬體判定離腕；此段不作為生理校正"
        samples > 0 -> "已取得原始事件"
        needsMotionTrigger(sensor ?: wakeup) || kind.needsMotion -> "觸發型通道：需抬腕、走路或改變姿勢才會送事件"
        kind.needsContact -> "感測器已註冊，通常需要電極或指觸接觸"
        kind.needsOemMode -> "公開 HAL 可註冊，但私有晶片模式可能需原廠量測頁啟用"
        else -> "量測期間沒有事件"
    }

    enum class Kind(
        val wireName: String,
        val label: String,
        val stringType: String,
        val biomedical: Boolean = false,
        val needsOemMode: Boolean = false,
        val needsContact: Boolean = false,
        val needsMotion: Boolean = false,
        val hasRawValueCount: Boolean = biomedical
    ) {
        HEART_RATE("heart_rate_raw", "心率感測通道", "android.sensor.heart_rate", biomedical = true),
        MAX_HEART_RATE("max_hr", "MAX86178 光學心率封包", "com.google.wear.sensor.hr", biomedical = true, needsOemMode = true),
        MI_SPO2("mi_spo2", "Xiaomi SpO₂ 演算法", "com.google.wear.sensor.mi.spo2", biomedical = true, needsOemMode = true),
        SPO2("spo2_raw", "SpO₂ 原始通道", "com.google.wear.sensor.spo2", biomedical = true, needsOemMode = true),
        PPG("ppg", "PPG 光學波形", "com.google.wear.sensor.ppg", biomedical = true, needsOemMode = true),
        ECG("ecg", "ECG 原始事件值", "com.google.wear.sensor.ecg", biomedical = true, needsContact = true),
        BIOZ("bioz", "BIOZ 阻抗波形", "com.google.wear.sensor.bioz", biomedical = true, needsOemMode = true, needsContact = true),
        BIOZ_RESPIRATION(
            "bioz_respiration", "呼吸研究（BioZ）", "com.google.wear.sensor.bioz",
            biomedical = true, needsOemMode = true, needsContact = true
        ),
        LIFE_Q_OTT("lifeq_ott", "LifeQ OTT 通道", "com.lifeq.wear.sensor.ott", biomedical = true, needsOemMode = true),
        BODY_TEMPERATURE("body_temperature_raw", "體表溫度原始通道", "com.google.wear.sensor.bodytemperature", hasRawValueCount = true),
        THERMOMETER("thermometer", "溫度演算法通道", "com.google.wear.sensor.algo.temperature", hasRawValueCount = true),
        BOARD_TEMPERATURE("board_temperature", "主板溫度", "com.google.wear.sensor.boardtemperature"),
        TEMPERATURE_ALARM("temperature_alarm", "溫度警示", "com.google.wear.sensor.temperature_alarm"),
        PRESSURE("pressure", "氣壓計", "android.sensor.pressure"),
        LIGHT("light", "環境光", "android.sensor.light"),
        ACCELEROMETER("accelerometer", "加速度計", "android.sensor.accelerometer"),
        ACCELEROMETER_UNCALIBRATED("accelerometer_uncalibrated", "未校正加速度", "android.sensor.accelerometer_uncalibrated"),
        GYROSCOPE("gyroscope", "陀螺儀", "android.sensor.gyroscope"),
        GYROSCOPE_UNCALIBRATED("gyroscope_uncalibrated", "未校正陀螺儀", "android.sensor.gyroscope_uncalibrated"),
        MAGNETOMETER("magnetometer", "磁力計", "android.sensor.magnetic_field"),
        MAGNETOMETER_UNCALIBRATED("magnetometer_uncalibrated", "未校正磁力計", "android.sensor.magnetic_field_uncalibrated"),
        ROTATION_VECTOR("rotation_vector", "旋轉向量", "android.sensor.rotation_vector"),
        GAME_ROTATION("game_rotation", "遊戲旋轉向量", "android.sensor.game_rotation_vector"),
        GRAVITY("gravity", "重力向量", "android.sensor.gravity"),
        LINEAR_ACCELERATION("linear_acceleration", "線性加速度", "android.sensor.linear_acceleration"),
        GEOMAGNETIC_ROTATION("geomagnetic_rotation", "地磁旋轉向量", "android.sensor.geomagnetic_rotation_vector"),
        ORIENTATION("orientation", "方位角", "android.sensor.orientation"),
        STEP_COUNTER("step_counter", "計步器", "android.sensor.step_counter"),
        STEP_DETECTOR("step_detector", "步伐偵測", "android.sensor.step_detector", needsMotion = true),
        WRIST_TILT("wrist_tilt", "抬腕手勢", "android.sensor.wrist_tilt_gesture", needsMotion = true),
        SIGNIFICANT_MOTION("significant_motion", "顯著動作偵測", "android.sensor.significant_motion", needsMotion = true),
        STATIONARY("stationary", "靜止狀態偵測", "android.sensor.stationary_detect", needsMotion = true),
        MOTION("motion", "移動狀態偵測", "android.sensor.motion_detect", needsMotion = true),
        ADT("adt", "MAX86178 離腕 ADT", "com.google.wear.sensor.adt"),
        HARDWARE_ADT("hardware_adt", "MAX86178 硬體離腕", "com.google.wear.sensor.hwadt"),
        OFF_BODY("off_body", "離腕偵測", "android.sensor.low_latency_offbody_detect")

        ;

        val requiresWearContact: Boolean
            get() = this in setOf(
                HEART_RATE, MAX_HEART_RATE, MI_SPO2, SPO2, PPG, ECG, BIOZ,
                BIOZ_RESPIRATION, LIFE_Q_OTT, OFF_BODY
            )
    }

    data class Snapshot(
        val kind: Kind,
        val sensorName: String,
        val vendor: String,
        val reportingMode: String,
        val samples: Int,
        val finiteValueCount: Int? = samples,
        val eventCount: Int,
        val sampleRateHz: Double,
        val eventRatePerSessionSecond: Double = 0.0,
        val sessionStartedAtEpochMs: Long = 0L,
        val sessionStartedElapsedRealtimeNs: Long = 0L,
        val firstSampleAtEpochMs: Long = 0L,
        val lastSampleAtEpochMs: Long = 0L,
        val sampleTimestampSource: String = "unknown",
        val registrationResult: String = "unknown",
        val acquisitionStopReason: String = "unknown",
        val channels: Int,
        val accuracy: Int,
        val minimum: Double?,
        val maximum: Double?,
        val average: Double?,
        val latest: List<Double>,
        val waveform: FloatArray,
        val worn: Boolean?,
        val status: String,
        val reason: String,
        val eventTimestampsNs: LongArray = longArrayOf(),
        val eventTimestampValidity: IntArray = intArrayOf(),
        val eventCallbackElapsedRealtimeNs: LongArray = longArrayOf(),
        val previewPointEventTimestampsNs: LongArray = longArrayOf(),
        val previewPointEventTimestampValidity: IntArray = intArrayOf(),
        val previewPointCallbackElapsedRealtimeNs: LongArray = longArrayOf(),
        val eventSampleCounts: IntArray = intArrayOf(),
        val eventRawValueRows: List<String> = emptyList(),
        val ecgEventContactStates: IntArray = intArrayOf(),
        val eventRawValuesStatus: String = "not_applicable",
        val halAdditionalInfoSupported: Boolean = false,
        val halSamplePeriodSeconds: Double? = null,
        val halSampleJitterSeconds: Double? = null,
        val candidateSummary: String = "",
        val candidateCount: Int = 0
    ) {
        fun evidenceFlags(): List<String> = buildList {
            add(if (eventCount > 0) "EVENTS_RECEIVED" else "EVENTS_MISSING")
            add(RawEvidenceFlags.finiteValueFlag(finiteValueCount))
            add(if (latest.any { it.isFinite() }) "RAW_PAYLOAD_FINITE_VALUES_RECEIVED" else "RAW_PAYLOAD_FINITE_VALUES_MISSING")
            add(if (lastSampleAtEpochMs > 0L && sampleTimestampSource in setOf(
                    "android_sensor_event_elapsed_realtime", "health_services_datapoint_epoch"))
                "SOURCE_TIME_AVAILABLE" else "SOURCE_TIME_UNVERIFIED")
            add(if (eventCallbackElapsedRealtimeNs.any { it > 0L })
                "CALLBACK_TIME_AVAILABLE" else "CALLBACK_TIME_MISSING")
            add(if (halSamplePeriodSeconds?.let { it.isFinite() && it > 0.0 } == true)
                "HAL_SAMPLE_PERIOD_AVAILABLE" else "HAL_SAMPLE_PERIOD_UNKNOWN")
        }

        fun summary(): String = when {
            eventCount > 0 && kind == Kind.ECG -> "${kind.label}：$eventCount 個事件 · 原始欄位未解碼"
            samples == 0 -> "${kind.label}：${reason}"
            worn != null -> "${kind.label}：${if (worn) "已佩戴" else "已離腕"} · $samples 筆 · ${summaryRateDescription(kind, sampleRateHz, eventRatePerSessionSecond)}"
            else -> "${kind.label}：$samples 筆 · ${summaryRateDescription(kind, sampleRateHz, eventRatePerSessionSecond)} · $channels 通道 · 範圍 ${minimum?.let { "%.2f".format(it) }}–${maximum?.let { "%.2f".format(it) }} · 最新 ${latest.take(6).joinToString { "%.2f".format(it) }}"
        }
    }

    private fun selectedOpticalWaveform(): FloatArray {
        if (opticalRows.isEmpty()) return waveform.toFloatArray()
        return selectOpticalChannelForResearch(opticalRows).toFloatArray()
    }

    companion object {
        private const val TAG = "RawVitalsProbe"
        private const val MAX_CHANNELS = 16
        private const val MAX_WAVEFORM_SAMPLES = 1024
        private const val MAX_OPTICAL_ROWS = 1024
        private const val MAX_ECG_WAVEFORM_SAMPLES = 5120
        private const val MAX_EVENT_TIMESTAMP_ROWS = 512
        private const val MAX_ECG_RAW_VALUE_CHARS = 32_768
        private const val MAX_RAW_EVENT_VALUES_PER_ROW = 64
        private const val UPDATE_INTERVAL = 8
        private const val ECG_UPDATE_INTERVAL = 5
        private const val MAX_CANDIDATE_DIAGNOSTICS = 8
        private const val MAX_SENSOR_NAME_LENGTH = 96
        private const val MAX_SENSOR_VENDOR_LENGTH = 64
        private const val WAKEUP_FALLBACK_DELAY_MILLIS = 1_500L
        // Sensor.TYPE_WRIST_TILT_GESTURE = 26; not always present in Wear compile stubs.
        private const val TYPE_WRIST_TILT_GESTURE = 26
        private val RAW_WAVEFORM_KINDS = setOf(
            Kind.PPG, Kind.MAX_HEART_RATE, Kind.SPO2, Kind.ECG, Kind.BIOZ,
            Kind.BIOZ_RESPIRATION, Kind.LIFE_Q_OTT
        )

        internal fun validHalSamplePeriodSeconds(period: Double?): Double? =
            period?.takeIf { it.isFinite() && it in 0.0001..1.0 }

        internal fun validHalSampleJitterSeconds(jitter: Double?): Double? =
            jitter?.takeIf { it.isFinite() && it >= 0.0 }

        fun unpackWaveform(kind: Kind, values: FloatArray): List<Float> {
            if (kind in setOf(Kind.ECG, Kind.BIOZ, Kind.BIOZ_RESPIRATION)) return emptyList()
            val count = when (kind) {
                Kind.PPG, Kind.MAX_HEART_RATE, Kind.LIFE_Q_OTT -> 16
                Kind.SPO2, Kind.BIOZ, Kind.BIOZ_RESPIRATION -> 8
                else -> 1
            }
            val packed = values.take(count).filter(Float::isFinite)
            if (packed.size >= 2) return packed
            if (packed.size == 1) return packed
            return emptyList()
        }

        fun isValidOpticalWaveform(values: List<Float>): Boolean {
            return ChipCalibrationProfiles.assessOpticalWaveform(values).valid
        }

        internal fun isUsableOpticalValue(value: Float): Boolean =
            value.isFinite() && value != ChipCalibrationProfiles.OPTICAL_FILL

        internal fun selectOpticalChannelForResearch(rows: List<FloatArray>): List<Float> {
            if (rows.isEmpty()) return emptyList()
            val width = rows.maxOf { it.size.coerceAtMost(MAX_CHANNELS) }
            if (width == 0) return List(rows.size) { ChipCalibrationProfiles.OPTICAL_FILL }
            val selected = (0 until width).maxByOrNull { channel ->
                val values = rows.mapNotNull { row -> row.getOrNull(channel)?.takeIf(::isUsableOpticalValue) }
                if (values.size < 2) Double.NEGATIVE_INFINITY
                else {
                    val mean = values.average()
                    val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
                    values.size * 10_000.0 + variance
                }
            } ?: return List(rows.size) { ChipCalibrationProfiles.OPTICAL_FILL }
            return rows.map { row ->
                row.getOrNull(selected)?.takeIf(Float::isFinite) ?: ChipCalibrationProfiles.OPTICAL_FILL
            }
        }

        private const val PPG_FILL_VALUE = 524287f

        internal fun samplingDelayUs(sensor: Sensor, kind: Kind): Int {
            val hardwareMinimum = sensor.minDelay.takeIf { it > 0 } ?: MIN_SAFE_SAMPLE_PERIOD_US
            val appMinimum = minimumSamplingPeriodUs(kind)
            return maxOf(hardwareMinimum, appMinimum)
        }

        fun minimumSamplingPeriodUs(kind: Kind): Int = when (kind) {
            Kind.ECG, Kind.BIOZ, Kind.BIOZ_RESPIRATION -> MIN_ECG_BIOZ_SAMPLE_PERIOD_US
            Kind.PPG, Kind.SPO2, Kind.MAX_HEART_RATE, Kind.MI_SPO2, Kind.LIFE_Q_OTT -> MIN_OPTICAL_SAMPLE_PERIOD_US
            else -> MIN_GENERAL_SAMPLE_PERIOD_US
        }

        internal fun reportedSampleRateHz(kind: Kind, halSamplePeriodSeconds: Double?): Double {
            if (kind == Kind.ECG) return 0.0
            if (kind in RAW_WAVEFORM_KINDS) {
                return validHalSamplePeriodSeconds(halSamplePeriodSeconds)?.let { 1.0 / it } ?: 0.0
            }
            return 0.0
        }

        internal fun eventRatePerSessionSecond(eventCount: Int, elapsedNanos: Long): Double =
            eventCount.coerceAtLeast(0) * 1_000_000_000.0 / elapsedNanos.coerceAtLeast(1L)

        internal fun summaryRateDescription(
            kind: Kind,
            sampleRateHz: Double,
            eventRatePerSessionSecond: Double = 0.0
        ): String = when {
            kind in RAW_WAVEFORM_KINDS && sampleRateHz.isFinite() && sampleRateHz > 0.0 ->
                "HAL 回報取樣率 ${"%.1f".format(sampleRateHz)} Hz"
            kind in RAW_WAVEFORM_KINDS -> "HAL 取樣時基未回報"
            kind.biomedical -> "硬體取樣率未知"
            eventRatePerSessionSecond.isFinite() && eventRatePerSessionSecond > 0.0 ->
                "整段事件吞吐 ${"%.1f".format(eventRatePerSessionSecond)} 事件/秒（非取樣率）"
            else -> "整段事件吞吐未提供"
        }

        private const val MIN_SAFE_SAMPLE_PERIOD_US = 10_000
        private const val MIN_OPTICAL_SAMPLE_PERIOD_US = 10_000
        private const val MIN_ECG_BIOZ_SAMPLE_PERIOD_US = 2_000
        private const val MIN_GENERAL_SAMPLE_PERIOD_US = 50_000

        private fun isOneShot(sensor: Sensor?): Boolean =
            sensor?.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT

        private fun needsMotionTrigger(sensor: Sensor?): Boolean {
            val mode = sensor?.reportingMode ?: return false
            return mode == Sensor.REPORTING_MODE_ONE_SHOT || mode == Sensor.REPORTING_MODE_SPECIAL_TRIGGER
        }

        fun isOnBody(value: Double): Boolean = value >= 0.5

        fun derived(
            kind: Kind,
            values: List<Double>,
            sensorName: String,
            vendor: String,
            reason: String,
            measurementTimestampEpochMs: Long? = null,
            timestampSource: String = "unknown"
        ): Snapshot {
            val waveform = values.map { it.toFloat() }.toFloatArray()
            val measurementTime = measurementTimestampEpochMs?.takeIf { it > 0L } ?: 0L
            return Snapshot(
                kind = kind,
                sensorName = sensorName,
                vendor = vendor,
                reportingMode = "derived",
                samples = values.size,
                finiteValueCount = null,
                eventCount = 0,
                sampleRateHz = 1.0,
                sessionStartedAtEpochMs = System.currentTimeMillis(),
                firstSampleAtEpochMs = measurementTime,
                lastSampleAtEpochMs = measurementTime,
                sampleTimestampSource = timestampSource.takeIf { measurementTime > 0L } ?: "unknown",
                channels = values.size,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                minimum = values.minOrNull(),
                maximum = values.maxOrNull(),
                average = values.average().takeIf { it.isFinite() },
                latest = values,
                waveform = waveform,
                worn = null,
                status = "derived_estimate",
                reason = reason
            )
        }

        private fun reportingModeName(mode: Int?): String = when (mode) {
            Sensor.REPORTING_MODE_CONTINUOUS -> "continuous"
            Sensor.REPORTING_MODE_ON_CHANGE -> "on_change"
            Sensor.REPORTING_MODE_ONE_SHOT -> "one_shot"
            Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "special_trigger"
            else -> "unknown"
        }
    }
}

internal object AcquisitionStopPolicy {
    fun resolve(requestedReason: String?, hasSamples: Boolean, emptyStatus: String): String =
        requestedReason?.takeIf(String::isNotBlank)
            ?: if (hasSamples) "stopped_with_samples" else emptyStatus
}
