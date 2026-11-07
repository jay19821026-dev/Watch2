package com.jaysato.xiaomihealthbridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.time.Instant
import java.util.UUID
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.google.common.util.concurrent.ListenableFuture

class WearControlService : WearableListenerService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        WearSnapshotOutbox.recoverPending(this)
    }
    private val measureClient by lazy { HealthServices.getClient(this).measureClient }
    private var sensorController: AdvancedSensorController? = null
    private var rawVitalsProbe: RawVitalsProbe? = null
    private var rawSuiteProbes = emptyList<RawVitalsProbe>()
    private var sensorSuiteToken = 0
    private var activeSensorSuiteToken: Int? = null
    private var activeSuiteId: String? = null
    private var activeSuiteStartedAtMs = 0L
    private var activeSuiteExpectedKinds: Set<RawVitalsProbe.Kind> = emptySet()
    private var suiteRunState: WearMeasurementPolicy.SuiteRunState? = null
    private var sensorSuiteDeadline: Runnable? = null
    private var sensorSuiteCallbackTag: Any? = null
    private val lastPreviewSentAt = mutableMapOf<String, Long>()
    private var lastUrgentSnapshotAt = 0L
    private var ppgWarmupProbe: RawVitalsProbe? = null
    private var ppgAccelerationCapture: WearAccelerationCapture? = null
    private val finishPpgWarmup = Runnable { finishPpgWarmupMeasurement() }
    private var suiteWakeLock: PowerManager.WakeLock? = null
    private var suiteForegroundActive = false
    private var wornQualified: Boolean? = null
    private var latestHeading = Double.NaN
    private var latestAltitude = Double.NaN
    private var latestLight = Double.NaN
    private var latestTemperature = Double.NaN
    private var latestTemperatureSource = ""
    private var latestTemperatureTimestampSource = ""
    private var latestTemperatureAggregationMethod = "unreported"
    private var latestTemperatureAggregationSampleCount = 0
    private var latestWhsHeartRate = Double.NaN
    private var latestHeadingTimestamp = 0L
    private var latestAltitudeTimestamp = 0L
    private var latestLightTimestamp = 0L
    private var latestHeadingTimestampSource = "unknown"
    private var latestAltitudeTimestampSource = "unknown"
    private var latestLightTimestampSource = "unknown"
    private var latestTemperatureTimestamp = 0L
    private var latestWhsHeartRateTimestamp = 0L
    private var wornQualifiedTimestamp = 0L
    private var wornQualifiedTimestampSource = "unknown"
    private var measuringWhs = false
    private val whsHeartRateSyncGate = WearHeartRateSyncGate()
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val featureEpochOffsetMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    private var lastAdtSnapshot: RawVitalsProbe.Snapshot? = null
    private var lastOffBodySnapshot: RawVitalsProbe.Snapshot? = null
    private var lastSpo2Snapshot: RawVitalsProbe.Snapshot? = null
    private var lastHrSnapshot: RawVitalsProbe.Snapshot? = null
    private val suiteKindsWithData = mutableSetOf<String>()
    private val suiteKindsFinalized = mutableSetOf<String>()
    private val sensorSuiteWarnings = mutableListOf<String>()
    private val featureListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val eventTime = sensorEventTime(event.timestamp)
            if (eventTime.epochMillis <= 0L) return
            when (event.sensor.type) {
                Sensor.TYPE_PRESSURE -> {
                    latestAltitude = SensorManager.getAltitude(
                        SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]
                    ).toDouble()
                    latestAltitudeTimestamp = eventTime.epochMillis
                    latestAltitudeTimestampSource = eventTime.source
                }
                Sensor.TYPE_LIGHT -> {
                    latestLight = event.values.firstOrNull()?.toDouble() ?: Double.NaN
                    latestLightTimestamp = eventTime.epochMillis
                    latestLightTimestampSource = eventTime.source
                }
                Sensor.TYPE_ORIENTATION -> {
                    latestHeading = event.values.firstOrNull()?.toDouble() ?: Double.NaN
                    latestHeadingTimestamp = eventTime.epochMillis
                    latestHeadingTimestampSource = eventTime.source
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    event.values.copyInto(gravity, endIndex = minOf(3, event.values.size))
                    updateHeadingFromFusion(eventTime.epochMillis, eventTime.source)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    event.values.copyInto(geomagnetic, endIndex = minOf(3, event.values.size))
                    updateHeadingFromFusion(eventTime.epochMillis, eventTime.source)
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val rotation = FloatArray(9)
                    val orientation = FloatArray(3)
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                    SensorManager.getOrientation(rotation, orientation)
                    latestHeading = Math.toDegrees(orientation[0].toDouble()).let { (it + 360.0) % 360.0 }
                    latestHeadingTimestamp = eventTime.epochMillis
                    latestHeadingTimestampSource = eventTime.source
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    private val heartRateCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) = Unit
        override fun onDataReceived(data: DataPointContainer) {
            val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
            data.getData(DataType.HEART_RATE_BPM).maxByOrNull { it.getTimeInstant(bootInstant) }?.let { point ->
                val bpm = point.value.toDouble()
                if (bpm in 1.0..300.0) {
                    val sampleTime = point.getTimeInstant(bootInstant).toEpochMilli()
                    if (activeSuiteId != null && activeSuiteStartedAtMs > 0L && sampleTime < activeSuiteStartedAtMs) {
                        Log.w(TAG, "heart_rate_sample_ignored reason=older_than_suite_start")
                        return@let
                    }
                    latestWhsHeartRate = bpm
                    latestWhsHeartRateTimestamp = sampleTime
                    publishWhsHeartRate(bpm, sampleTime)
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (WearControlCommandAuth.requiresForegroundHandoff(event.path)) {
            startSensorSuiteCommand()
            return
        }
        when (event.path) {
            PATH_PING -> report("手錶在線", true, StatusSyncPriority.TERMINAL)
            PATH_REGISTER -> {
                PassiveMonitoringSettings.setEnabled(this, true)
                PassiveRegistration.register(this) { result ->
                    result.onSuccess { report("背景監測已重新註冊（$it 種資料）", true) }
                        .onFailure { report("背景監測註冊失敗：${it.message}", false) }
                }
            }
            PATH_ENABLE_OPEN_FEATURES -> enableOpenFeatures()
            PATH_STATUS -> {
                if (event.data.isNotEmpty()) sendDebugStatus(event)
                else report(buildStatus(), true, StatusSyncPriority.TERMINAL)
            }
            PATH_OXYGEN_NOW -> {
                OxygenSyncWorker.runNow(this)
                report("已要求啟動 SpO₂ 晶片感測器", true, StatusSyncPriority.USER_ACTION)
            }
            PATH_TEMPERATURE_NOW -> startTemperatureMeasurement()
            PATH_PPG_NOW -> startRawVitals(RawVitalsProbe.Kind.PPG)
            PATH_ECG_NOW -> startRawVitals(RawVitalsProbe.Kind.ECG)
            PATH_BIOZ_NOW -> startRawVitals(RawVitalsProbe.Kind.BIOZ)
            PATH_OFF_BODY_NOW -> startRawVitals(RawVitalsProbe.Kind.OFF_BODY)
            PATH_OPTICAL_BP_NOW -> startOpticalBpUntilData()
            PATH_NOISE_NOW -> startActivity(
                Intent(this, WearNoiseActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ).also { report("已開啟噪音量測", true, StatusSyncPriority.USER_ACTION) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (WearControlCommandAuth.isWearableListenerStart(intent?.action)) {
            return super.onStartCommand(intent, flags, startId)
        }
        if (!WearControlCommandAuth.isAuthorized(this, intent)) {
            if (!hasActiveMeasurement()) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_MEASURE_TEMPERATURE -> startTemperatureMeasurement()
            ACTION_MEASURE_PPG -> startRawVitals(RawVitalsProbe.Kind.PPG)
            ACTION_MEASURE_ECG -> startRawVitals(RawVitalsProbe.Kind.ECG)
            ACTION_MEASURE_BIOZ -> startRawVitals(RawVitalsProbe.Kind.BIOZ)
            ACTION_SENSOR_SUITE -> startSensorSuite()
            ACTION_ENABLE_OPEN_FEATURES -> enableOpenFeatures()
            ACTION_MEASURE_OPTICAL_BP -> startOpticalBpUntilData()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startSensorSuiteCommand() {
        runCatching {
            startForegroundService(WearControlCommandAuth.createIntent(this, ACTION_SENSOR_SUITE))
        }
            .onFailure { error ->
                Log.e(TAG, "sensor_suite_handoff_failed", error)
                report(
                    "全感測器掃描未啟動：前景服務交接失敗（${error::class.simpleName ?: "啟動錯誤"}）",
                    false,
                    StatusSyncPriority.TERMINAL
                )
            }
    }

    private fun hasActiveMeasurement(): Boolean =
        suiteForegroundActive || activeSensorSuiteToken != null || sensorController != null ||
            rawVitalsProbe != null || rawSuiteProbes.isNotEmpty() || ppgWarmupProbe != null || measuringWhs

    private fun startTemperatureMeasurement() {
        if (!ensureHealthForegroundPrerequisite()) return
        if (isCharging()) {
            report("目前正在充電，已略過溫度原始通道量測；拔除充電器並佩戴後再試。", false)
            return
        }
        if (!enterSuiteForeground()) return
        sensorController?.stop()
        var received = false
        val controller = AdvancedSensorController(
            context = this,
            onUpdate = {},
            onTemperatureReady = { rawValue, measuredAt, timestampSource ->
                received = true
                val aggregation = WearTemperatureFeaturePolicy.directCaptureAggregation()
                rememberTemperatureFeature(
                    rawValue, measuredAt, "dedicated_temperature_capture", timestampSource,
                    aggregation.method, aggregation.sampleCount
                )
                sendFeatureSnapshot(urgent = true)
                report("已收到溫度裝置原始值；單位未驗證，不寫入 Health Connect 體溫紀錄。", true, StatusSyncPriority.TERMINAL)
                sensorController?.stop()
                sensorController = null
                exitSuiteForeground()
            }
        )
        sensorController = controller
        val sensorName = controller.temperatureSensorName
        if (sensorName == null) {
            report("找不到可用的溫度原始感測通道", false)
            sensorController = null
            exitSuiteForeground()
            return
        }
        controller.start()
        report("正在使用 $sensorName 量測，請貼合佩戴並保持不動；有讀值即結束", true, StatusSyncPriority.USER_ACTION)
        handler.postDelayed({
            if (sensorController === controller) {
                controller.stop()
                sensorController = null
                if (!received) report("等待過久仍無體溫讀值，請貼合佩戴後重試", false)
                exitSuiteForeground()
            }
        }, RAW_SENSOR_TIMEOUT_MILLIS)
    }

    private fun startRawVitals(kind: RawVitalsProbe.Kind) {
        if (!ensureHealthForegroundPrerequisite()) return
        if (isCharging() && kind.requiresWearContact) {
            report("目前正在充電，已略過 ${kind.label} 量測；拔除充電器並佩戴後再試。", false)
            return
        }
        if (!enterSuiteForeground()) return
        cancelActiveRawProbe()
        if (kind == RawVitalsProbe.Kind.PPG) {
            startPpgWithOpticalWarmup()
            return
        }
        if (kind == RawVitalsProbe.Kind.ECG) {
            startEcgUntilSamples()
            return
        }
        val probeHolder = arrayOfNulls<RawVitalsProbe>(1)
        val probe = createProbeSafely(kind, emitIntermediateSnapshots = true, onSnapshot = { snapshot ->
            sendRawSnapshot(snapshot, false)
            val minSamples = when (kind) {
                RawVitalsProbe.Kind.BIOZ, RawVitalsProbe.Kind.BIOZ_RESPIRATION -> 40
                RawVitalsProbe.Kind.OFF_BODY -> 1
                else -> 24
            }
            val biozNeedsWear = kind == RawVitalsProbe.Kind.BIOZ ||
                kind == RawVitalsProbe.Kind.BIOZ_RESPIRATION
            val usable = snapshot.samples >= minSamples && snapshot.status != "invalid_optical" &&
                (!biozNeedsWear || WearMeasurementPolicy.hasWornBiozRawCapture(
                    snapshot.worn, snapshot.samples, minSamples
                )) &&
                (kind != RawVitalsProbe.Kind.PPG ||
                    (snapshot.sampleRateHz >= 20.0 && RawVitalsProbe.isValidOpticalWaveform(snapshot.waveform.toList())))
            if (usable) {
                handler.post {
                    val active = probeHolder[0] ?: return@post
                    if (rawVitalsProbe !== active) return@post
                    val finalSnap = active.stop()
                    rawVitalsProbe = null
                    probeHolder[0] = null
                    sendRawSnapshot(finalSnap, true)
                    maybePublishTemperatureRaw(finalSnap)
                    report(
                        finalSnap.summary(),
                        if (biozNeedsWear) WearMeasurementPolicy.hasWornBiozRawCapture(
                            finalSnap.worn, finalSnap.samples, minSamples
                        ) else finalSnap.samples > 0
                    )
                    exitSuiteForeground()
                }
            }
        })
        if (probe == null) {
            report("${kind.label}感測器建立失敗；可重新量測", false)
            exitSuiteForeground()
            return
        }
        probeHolder[0] = probe
        rawVitalsProbe = probe
        if (!probe.start()) {
            val failed = probe.stop()
            sendRawSnapshot(failed, true)
            report("${kind.label}感測器啟動失敗：${failed.acquisitionStopReason}", false)
            rawVitalsProbe = null
            probeHolder[0] = null
            exitSuiteForeground()
            return
        }
        report("正在量測${kind.label}；最多 20 秒，有足夠數值會提早結束", true, StatusSyncPriority.USER_ACTION)
        handler.postDelayed({
            val active = probeHolder[0]
            if (active != null && rawVitalsProbe === active) {
                val snapshot = active.stop("time_limit_reached")
                rawVitalsProbe = null
                probeHolder[0] = null
                sendRawSnapshot(snapshot, true)
                maybePublishTemperatureRaw(snapshot)
                report(
                    if (snapshot.samples > 0) snapshot.summary() else "等待過久仍無數值 · ${snapshot.summary()}",
                    snapshot.samples > 0 && snapshot.status != "invalid_optical" &&
                        snapshot.acquisitionStopReason != "time_limit_reached"
                )
                exitSuiteForeground()
            }
        }, RAW_SENSOR_TIMEOUT_MILLIS)
    }

    private fun startOpticalBpUntilData() {
        val launched = runCatching {
            startActivity(
                Intent(this, WearOpticalBpActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                    .putExtra(WearOpticalBpActivity.EXTRA_AUTO_START, false)
            )
            true
        }.getOrDefault(false)
        if (!launched) {
            report("無法開啟血壓說明頁", false)
            return
        }
        report("此手錶沒有裝置級血壓校準；已停止由脈率推算血壓，請使用袖帶血壓計。", true, StatusSyncPriority.TERMINAL)
    }

    /** ECG：開啟本 App 獨立量測頁（不依賴原廠 ECG）。 */
    private fun startEcgUntilSamples() {
        val launched = runCatching {
            startActivity(
                Intent(this, WearEcgActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                    .putExtra(WearEcgActivity.EXTRA_AUTO_START, true)
            )
            true
        }.getOrDefault(false)
        if (!launched) {
            report("無法開啟獨立 ECG 量測頁", false)
            exitSuiteForeground()
            return
        }
        report("已開啟獨立 ECG：請雙指按住錶冠與側鍵電極；有足夠波形後自動結束。", true, StatusSyncPriority.USER_ACTION)
        exitSuiteForeground()
    }

    private fun startPpgWithOpticalWarmup() {
        report("PPG：先預熱心率通道，再擷取有效波形；原廠頁面須從手錶手動開啟", true, StatusSyncPriority.USER_ACTION)
        val warm = RawVitalsProbe(this, RawVitalsProbe.Kind.MAX_HEART_RATE) { sendRawSnapshot(it, false) }
        rawVitalsProbe = warm
        ppgWarmupProbe = warm
        if (!warm.start()) {
            rawVitalsProbe = null
            ppgWarmupProbe = null
            measurePpgExclusive(null)
            return
        }
        handler.postDelayed(finishPpgWarmup, PPG_WARMUP_MILLIS)
    }

    private fun finishPpgWarmupMeasurement() {
        val warm = ppgWarmupProbe ?: return
        ppgWarmupProbe = null
        if (rawVitalsProbe !== warm) return
        val warmSnap = warm.stop()
        rawVitalsProbe = null
        if (warmSnap.samples > 0) lastHrSnapshot = warmSnap
        measurePpgExclusive(warmSnap.takeIf {
            RawVitalsProbe.isValidOpticalWaveform(it.waveform.toList())
        })
    }

    private fun cancelActiveRawProbe() {
        handler.removeCallbacks(finishPpgWarmup)
        ppgWarmupProbe = null
        rawVitalsProbe?.stop()
        rawVitalsProbe = null
        ppgAccelerationCapture?.stop()
        ppgAccelerationCapture = null
    }

    private fun stopSuiteProbes() {
        val probes = rawSuiteProbes.toList()
        rawSuiteProbes = emptyList()
        WearSensorSuiteCleanup.run(probes.mapIndexed { index, probe ->
            WearSensorSuiteCleanup.Action("stop_probe_$index") {
                try {
                    probe.stop()
                } finally {
                    EcgMeasurementLease.release(probe)
                }
            }
        }).forEach { failure ->
            Log.e(TAG, "sensor_suite_cleanup_failed action=${failure.action} type=${failure.error.javaClass.simpleName}")
        }
    }

    private fun postSensorSuiteCallback(token: Int, delayMillis: Long, callback: Runnable): Boolean {
        val tag = sensorSuiteCallbackTag?.takeIf {
            WearSensorSuiteCallbackPolicy.accepts(token, activeSensorSuiteToken, sensorSuiteToken)
        } ?: return false
        return handler.postAtTime(callback, tag, android.os.SystemClock.uptimeMillis() + delayMillis.coerceAtLeast(0L))
    }

    private fun measurePpgExclusive(warmOptical: RawVitalsProbe.Snapshot?) {
        var completed = false
        val probeHolder = arrayOfNulls<RawVitalsProbe>(1)
        val safety = Runnable {
            val p = probeHolder[0] ?: return@Runnable
            finishPpg(p, warmOptical, completedFlag = { completed }, setCompleted = { completed = it }, reason = "等待過久仍無有效 PPG", stopReason = "time_limit_reached")
        }
        fun tryFinish(reason: String) {
            val p = probeHolder[0] ?: return
            finishPpg(p, warmOptical, completedFlag = { completed }, setCompleted = { completed = it }, reason = reason, stopReason = "capture_ready")
            handler.removeCallbacks(safety)
        }
        val motionCapture = WearAccelerationCapture(this, handler)
        motionCapture.start()
        ppgAccelerationCapture = motionCapture
        val probe = RawVitalsProbe(this, RawVitalsProbe.Kind.PPG) { snap ->
            sendRawSnapshot(snap, false)
            if (WearMeasurementPolicy.shouldFinishRawPpgCapture(snap.samples, snap.waveform.toList())) {
                handler.post { tryFinish("PPG 原始波形已擷取；取樣時鐘未驗證，不計算 BPM／HRV") }
            }
        }
        probeHolder[0] = probe
        rawVitalsProbe = probe
        if (!probe.start()) {
            val failed = probe.stop()
            val motion = ppgAccelerationCapture?.stop()
            ppgAccelerationCapture = null
            sendRawSnapshot(failed, true, motion)
            report("PPG 感測器啟動失敗：${failed.acquisitionStopReason}；其他光學通道不會代替 PPG", false)
            rawVitalsProbe = null
            probeHolder[0] = null
            exitSuiteForeground()
            return
        }
        report("正在量測 PPG 光學波形；最多 20 秒，有效波形會提早結束", true, StatusSyncPriority.USER_ACTION)
        handler.postDelayed(safety, RAW_SENSOR_TIMEOUT_MILLIS)
    }

    private fun finishPpg(
        probe: RawVitalsProbe,
        warmOptical: RawVitalsProbe.Snapshot?,
        completedFlag: () -> Boolean,
        setCompleted: (Boolean) -> Unit,
        reason: String,
        stopReason: String
    ) {
        if (completedFlag() || rawVitalsProbe !== probe) return
        setCompleted(true)
        var snapshot = probe.stop(stopReason)
        rawVitalsProbe = null
        val motion = ppgAccelerationCapture?.stop()
        ppgAccelerationCapture = null
        if (WearMeasurementPolicy.hasInvalidRawPpgSignal(snapshot.samples, snapshot.waveform.toList()) || snapshot.samples <= 0) {
            snapshot = snapshot.copy(
                status = if (snapshot.samples > 0) "invalid_optical" else snapshot.status,
                reason = "$reason；未以其他感測通道代替 PPG"
            )
        }
        sendRawSnapshot(snapshot, true, motion)
        maybePublishTemperatureRaw(snapshot)
        val summary = snapshot.summary()
        val resultMessage = if (snapshot.kind == RawVitalsProbe.Kind.PPG || snapshot.samples <= 0) {
            "$reason · $summary"
        } else {
            summary
        }
        report(
            resultMessage,
            snapshot.samples > 0 && snapshot.status != "invalid_optical" &&
                snapshot.acquisitionStopReason != "time_limit_reached"
        )
        exitSuiteForeground()
    }

    private fun beginSensorSuite(token: Int, expectedKinds: Set<RawVitalsProbe.Kind>) {
        activeSensorSuiteToken = token
        sensorSuiteCallbackTag = Any()
        activeSuiteId = UUID.randomUUID().toString()
        activeSuiteStartedAtMs = System.currentTimeMillis()
        latestWhsHeartRate = Double.NaN
        latestWhsHeartRateTimestamp = 0L
        activeSuiteExpectedKinds = expectedKinds
        suiteRunState = WearMeasurementPolicy.SuiteRunState.RUNNING
    }

    private fun createProbeSafely(
        kind: RawVitalsProbe.Kind,
        emitIntermediateSnapshots: Boolean = false,
        onSnapshot: (RawVitalsProbe.Snapshot) -> Unit = {}
    ): RawVitalsProbe? = SensorRegistrationGuard.create(
        factory = { RawVitalsProbe(this, kind, onSnapshot, emitIntermediateSnapshots) },
        onFailure = { error ->
            emitProbeCreationFailure(
                kind,
                if (error is SecurityException) "permission_denied" else "probe_creation_exception",
                error
            )
        }
    )

    private fun emitProbeCreationFailure(
        kind: RawVitalsProbe.Kind,
        reason: String,
        error: Throwable
    ) {
        Log.w(TAG, "probe_creation_failed kind=${kind.wireName} reason=$reason", error)
        sendRawSnapshot(
            RawVitalsProbe.derived(
                kind = kind,
                values = emptyList(),
                sensorName = "probe-creation",
                vendor = "HealthBridge",
                reason = "$reason：${error.javaClass.simpleName}"
            ).copy(
                status = reason,
                sampleRateHz = 0.0,
                registrationResult = reason,
                acquisitionStopReason = reason
            ),
            true
        )
    }

    private fun clearSensorSuiteSession() {
        sensorSuiteCallbackTag?.let(handler::removeCallbacksAndMessages)
        sensorSuiteCallbackTag = null
        activeSensorSuiteToken = null
        activeSuiteId = null
        activeSuiteStartedAtMs = 0L
        activeSuiteExpectedKinds = emptySet()
        suiteRunState = null
    }

    private fun startSensorSuite() {
        if (WearMeasurementPolicy.shouldIgnoreDuplicateSuiteStart(activeSensorSuiteToken != null)) {
            report(
                "全感測器掃描已在執行中；忽略重複啟動命令",
                true,
                StatusSyncPriority.PROGRESS,
                suiteRunState = WearMeasurementPolicy.SuiteRunState.RUNNING
            )
            return
        }
        if (!ensureHealthForegroundPrerequisite(reportAsSuite = true)) return
        if (isCharging()) {
            startChargingSafeSensorSuite()
            return
        }
        val token = ++sensorSuiteToken
        beginSensorSuite(token, RawVitalsProbe.Kind.entries.toSet())
        cancelActiveRawProbe()
        stopSuiteProbes()
        lastAdtSnapshot = null
        lastOffBodySnapshot = null
        lastSpo2Snapshot = null
        lastHrSnapshot = null
        suiteKindsWithData.clear()
        suiteKindsFinalized.clear()
        sensorSuiteWarnings.clear()
        if (!enterSuiteForeground(SENSOR_SUITE_WAKELOCK_TIMEOUT_MILLIS, token)) {
            clearSensorSuiteSession()
            stopSelf()
            return
        }
        scheduleSensorSuiteDeadline(token)
        startWhsHeartRate()
        wornQualified = null
        wornQualifiedTimestamp = 0L
        wornQualifiedTimestampSource = "unknown"
        report(
            "全感測器掃描開始：佩戴校正 → 動作觸發 → 環境 → 生醫獨占／晶片校準",
            false,
            StatusSyncPriority.PROGRESS,
            suiteRunState = WearMeasurementPolicy.SuiteRunState.RUNNING
        )
        val wearKinds = WearSensorSuitePlan.wearGateKinds
        val motionKinds = WearSensorSuitePlan.motionTriggerKinds
        runWearGate(token, wearKinds, 0) { worn ->
            wornQualified = worn
            report(
                WearMeasurementPolicy.wearGateMessage(worn),
                worn == true,
                if (worn == true) StatusSyncPriority.USER_ACTION else StatusSyncPriority.PROGRESS
            )
            runMotionTriggerPhase(token, motionKinds) { motionResponsive, motionTested, motionSnapshots ->
                val general = WearSensorSuitePlan.generalQueue(RawVitalsProbe.Kind.entries)
                val started = general.mapNotNull { kind ->
                    val probe = createProbeSafely(kind) ?: return@mapNotNull null
                    if (probe.start()) probe else {
                        sendRawSnapshot(probe.stop(), true)
                        null
                    }
                }
                rawSuiteProbes = started
                report("環境與連續通道掃描：${started.size} 項", true, StatusSyncPriority.PROGRESS)
                postSensorSuiteCallback(token, GENERAL_SUITE_TIMEOUT_MILLIS, Runnable {
                    if (sensorSuiteToken == token) {
                        val snapshots = started.map { it.stop() }
                        rawSuiteProbes = emptyList()
                        snapshots.forEach {
                            sendRawSnapshot(it, true)
                            captureFeature(it)
                        }
                        ensureOrientationSnapshot(snapshots)
                        sendFeatureSnapshot()
                        val queue = WearSensorSuitePlan.biomedicalQueue()
                        runBiomedicalSuite(
                            token,
                            queue,
                            0,
                            motionResponsive + snapshots.count { it.samples > 0 },
                            motionTested + snapshots.size + wearKinds.size
                        )
                    }
                })
            }
        }
    }

    private fun startChargingSafeSensorSuite() {
        if (!ensureHealthForegroundPrerequisite(reportAsSuite = true)) return
        activeSensorSuiteToken?.let {
            cancelSensorSuite(it, "掃描被新的操作取代", WearMeasurementPolicy.SuiteRunState.CANCELLED, stopService = false)
        }
        val token = ++sensorSuiteToken
        beginSensorSuite(
            token,
            (CHARGING_SAFE_SENSOR_KINDS + CHARGING_SKIPPED_SENSOR_KINDS).toSet()
        )
        cancelActiveRawProbe()
        stopSuiteProbes()
        lastAdtSnapshot = null
        lastOffBodySnapshot = null
        lastSpo2Snapshot = null
        lastHrSnapshot = null
        wornQualified = null
        wornQualifiedTimestamp = 0L
        wornQualifiedTimestampSource = "unknown"
        suiteKindsWithData.clear()
        suiteKindsFinalized.clear()
        sensorSuiteWarnings.clear()
        if (!enterSuiteForeground(SENSOR_SUITE_WAKELOCK_TIMEOUT_MILLIS, token)) {
            clearSensorSuiteSession()
            stopSelf()
            return
        }
        scheduleSensorSuiteDeadline(token)
        report(
            "全感測器掃描開始：充電中僅執行短時非接觸低耗電掃描；貼腕生理通道略過",
            false,
            StatusSyncPriority.PROGRESS,
            suiteRunState = WearMeasurementPolicy.SuiteRunState.RUNNING
        )
        val probes = CHARGING_SAFE_SENSOR_KINDS.mapNotNull { kind ->
            createProbeSafely(kind)?.let { probe ->
                if (probe.start()) probe else {
                    sendRawSnapshot(probe.stop(), true)
                    null
                }
            }
        }
        rawSuiteProbes = probes
        postSensorSuiteCallback(token, CHARGING_SAFE_SCAN_TIMEOUT_MILLIS, Runnable {
            if (sensorSuiteToken != token) return@Runnable
            val snapshots = probes.map { it.stop() }
            rawSuiteProbes = emptyList()
            snapshots.forEach {
                sendRawSnapshot(it, true)
                captureFeature(it)
            }
            CHARGING_SKIPPED_SENSOR_KINDS.forEach { kind ->
                val skipped = RawVitalsProbe.derived(
                    kind = kind,
                    values = emptyList(),
                    sensorName = "charging-guard",
                    vendor = "HealthBridge",
                    reason = "手錶正在充電；貼腕量測略過，避免輸出無效生理資料"
                ).let(WearMeasurementPolicy::markSkippedCharging)
                sendRawSnapshot(skipped, true)
            }
            sendFeatureSnapshot(urgent = true)
            unregisterFeatureSensors()
            val withData = snapshots.count { it.samples > 0 }
            val unreportedChannels = WearMeasurementPolicy.unreportedSuiteKinds(
                completedKinds = suiteKindsFinalized,
                expectedKinds = activeSuiteExpectedKinds
            )
            report(
                "充電中低耗電盤點完成：$withData/${probes.size} 項非接觸通道收到原始事件（非校準量測）；貼腕通道已略過。" +
                    WearMeasurementPolicy.unreportedChannelSummary(unreportedChannels),
                WearMeasurementPolicy.reportSuccess(
                    WearMeasurementPolicy.completedSuiteState(unreportedChannels),
                    fallback = false
                ),
                StatusSyncPriority.TERMINAL,
                suiteRunState = WearMeasurementPolicy.completedSuiteState(unreportedChannels)
            )
            finishSensorSuite(token)
        })
    }

    private fun runMotionTriggerPhase(
        token: Int,
        kinds: List<RawVitalsProbe.Kind>,
        done: (responsive: Int, tested: Int, snapshots: List<RawVitalsProbe.Snapshot>) -> Unit
    ) {
        if (sensorSuiteToken != token) return
        val activeKinds = kinds.filterNot { it == RawVitalsProbe.Kind.STATIONARY }
        val started = (activeKinds + RawVitalsProbe.Kind.STEP_COUNTER).distinct().mapNotNull { kind ->
            val probe = createProbeSafely(kind) ?: return@mapNotNull null
            if (probe.start()) probe else {
                sendRawSnapshot(probe.stop(), true)
                null
            }
        }
        rawSuiteProbes = started
        pulseVibration()
        report("動作觸發階段 ${started.size} 項：請抬腕、走路或輕輕甩動 ${MOTION_TRIGGER_TIMEOUT_MILLIS / 1000} 秒", true, StatusSyncPriority.USER_ACTION)
        var pulse = 0
        val pulseRunnable = object : Runnable {
            override fun run() {
                if (sensorSuiteToken != token) return
                pulse++
                if (pulse < 7) {
                    pulseVibration()
                    postSensorSuiteCallback(token, 2_200L, this)
                }
            }
        }
        postSensorSuiteCallback(token, 2_200L, pulseRunnable)
        postSensorSuiteCallback(token, MOTION_TRIGGER_TIMEOUT_MILLIS, Runnable {
            if (sensorSuiteToken != token) return@Runnable
            handler.removeCallbacks(pulseRunnable)
            val activeSnapshots = started.map { it.stop() }
            rawSuiteProbes = emptyList()
            activeSnapshots.forEach { sendRawSnapshot(it, true) }
            runStationaryQuietPhase(token, kinds, activeSnapshots, done)
        })
    }

    private fun runStationaryQuietPhase(
        token: Int,
        kinds: List<RawVitalsProbe.Kind>,
        activeSnapshots: List<RawVitalsProbe.Snapshot>,
        done: (responsive: Int, tested: Int, snapshots: List<RawVitalsProbe.Snapshot>) -> Unit
    ) {
        if (sensorSuiteToken != token) return
        val quietKinds = buildList {
            if (kinds.contains(RawVitalsProbe.Kind.STATIONARY)) add(RawVitalsProbe.Kind.STATIONARY)
            val smd = activeSnapshots.firstOrNull { it.kind == RawVitalsProbe.Kind.SIGNIFICANT_MOTION }
            if (smd == null || smd.samples == 0) add(RawVitalsProbe.Kind.SIGNIFICANT_MOTION)
        }.distinct()
        val started = quietKinds.mapNotNull { kind ->
            val probe = createProbeSafely(kind) ?: return@mapNotNull null
            if (probe.start()) probe else {
                sendRawSnapshot(probe.stop(), true)
                null
            }
        }
        rawSuiteProbes = started
        report("靜止偵測階段 ${started.size} 項：請保持手腕靜止 ${STATIONARY_QUIET_TIMEOUT_MILLIS / 1000} 秒", true, StatusSyncPriority.USER_ACTION)
        postSensorSuiteCallback(token, STATIONARY_QUIET_TIMEOUT_MILLIS, Runnable {
            if (sensorSuiteToken != token) return@Runnable
            val quietSnapshots = started.map { it.stop() }
                rawSuiteProbes = emptyList()
            quietSnapshots.forEach { sendRawSnapshot(it, true) }
            val merged = mergeMotionSnapshots(activeSnapshots, quietSnapshots)
            done(
                merged.count { it.kind in kinds && it.samples > 0 },
                kinds.size,
                merged
            )
        })
    }

    private fun mergeMotionSnapshots(
        active: List<RawVitalsProbe.Snapshot>,
        quiet: List<RawVitalsProbe.Snapshot>
    ): List<RawVitalsProbe.Snapshot> {
        val byKind = linkedMapOf<RawVitalsProbe.Kind, RawVitalsProbe.Snapshot>()
        active.forEach { byKind[it.kind] = it }
        quiet.forEach { next ->
            val prev = byKind[next.kind]
            byKind[next.kind] = when {
                prev == null -> next
                next.samples >= prev.samples -> next
                else -> prev
            }
        }
        return byKind.values.toList()
    }

    private fun runWearGate(token: Int, queue: List<RawVitalsProbe.Kind>, index: Int, done: (Boolean?) -> Unit) {
        if (sensorSuiteToken != token) return
        if (index >= queue.size) {
            done(wornQualified)
            return
        }
        val kind = queue[index]
        val probe = createProbeSafely(kind)
        if (probe == null) {
            runWearGate(token, queue, index + 1, done)
            return
        }
        rawSuiteProbes = listOf(probe)
        if (!probe.start()) {
            sendRawSnapshot(probe.stop(), true)
            runWearGate(token, queue, index + 1, done)
            return
        }
        report("佩戴校正 ${index + 1}/${queue.size}：${kind.label}", true, StatusSyncPriority.PROGRESS)
        postSensorSuiteCallback(token, WEAR_GATE_TIMEOUT_MILLIS, Runnable {
            if (sensorSuiteToken == token) {
                val snapshot = probe.stop()
                rawSuiteProbes = emptyList()
                sendRawSnapshot(snapshot, true)
                if (kind == RawVitalsProbe.Kind.OFF_BODY) {
                    lastOffBodySnapshot = snapshot
                    if (snapshot.worn != null) {
                        wornQualified = snapshot.worn
                        wornQualifiedTimestamp = snapshot.lastSampleAtEpochMs
                        wornQualifiedTimestampSource = snapshot.sampleTimestampSource
                    }
                }
                if (kind == RawVitalsProbe.Kind.ADT && snapshot.samples > 0) {
                    lastAdtSnapshot = snapshot
                }
                runWearGate(token, queue, index + 1, done)
            }
        })
    }

    private fun runBiomedicalSuite(token: Int, queue: List<RawVitalsProbe.Kind>, index: Int, responsive: Int, tested: Int) {
        if (sensorSuiteToken != token) return
        if (index >= queue.size) {
            ensureFullCatalogCoverage()
            sendFeatureSnapshot(urgent = true)
            stopWhsHeartRate()
            unregisterFeatureSensors()
            val completionToken = token
            val withData = suiteKindsWithData.size
            val totalKinds = RawVitalsProbe.Kind.entries.size
            val wornText = when (wornQualified) {
                true -> "佩戴合格"
                false -> "離腕感測器回報未貼合（此訊號不單獨證明佩戴狀態）"
                null -> "佩戴未知"
            }
            val optical = lastHrSnapshot?.waveform?.toList()?.let { ChipCalibrationProfiles.assessOpticalWaveform(it) }
            val tempNote = ChipCalibrationProfiles.temperatureCalibrationNote()
            val unreportedChannels = WearMeasurementPolicy.unreportedSuiteKinds(
                suiteKindsFinalized,
                activeSuiteExpectedKinds
            )
            val runState = WearMeasurementPolicy.completedSuiteState(unreportedChannels)
            report(
                WearMeasurementPolicy.appendSensorSuiteWarnings(
                    WearMeasurementPolicy.sensorSuiteCompletionMessage(
                    phasesWithSamples = responsive,
                    phasesTested = tested,
                    channelsWithRawEvents = withData,
                    totalChannels = totalKinds,
                    wearStatus = wornText,
                    opticalAdvice = optical?.advice,
                    temperatureNote = tempNote,
                    unreportedChannels = unreportedChannels
                    ),
                    sensorSuiteWarnings
                ),
                WearMeasurementPolicy.reportSuccess(runState, fallback = false),
                StatusSyncPriority.TERMINAL,
                suiteRunState = runState
            )
            finishSensorSuite(completionToken)
            return
        }
        val kind = queue[index]
        val oemProbeTimeout = WearMeasurementPolicy.broadSuiteOemProbeTimeoutMillis(kind)
        if (oemProbeTimeout != null) {
            report(
                "${kind.label} 需要原廠量測頁；背景全掃描不會嘗試啟動畫面，僅快速檢查公開通道。需要擷取時請開啟單項量測頁並手動點選原廠量測。",
                true,
                StatusSyncPriority.PROGRESS
            )
        }
        val finishHolder = arrayOfNulls<Runnable>(1)
        val probe = createProbeSafely(
            kind = kind,
            emitIntermediateSnapshots = WearMeasurementPolicy.shouldEmitIntermediateSuiteSnapshots(kind),
            onSnapshot = { snapshot ->
            val opticalReady = kind in setOf(
                RawVitalsProbe.Kind.PPG,
                RawVitalsProbe.Kind.MAX_HEART_RATE,
                RawVitalsProbe.Kind.LIFE_Q_OTT
            ) && WearMeasurementPolicy.shouldEarlyFinishBroadSuiteProbe(
                kind,
                snapshot.eventCount,
                snapshot.waveform.toList(),
                System.currentTimeMillis() - snapshot.sessionStartedAtEpochMs
            )
            if (opticalReady) {
                finishHolder[0]?.let { advance ->
                    handler.removeCallbacks(advance)
                    postSensorSuiteCallback(token, 0L, advance)
                }
            }
            }
        )
        if (probe == null) {
            runBiomedicalSuite(token, queue, index + 1, responsive, tested + 1)
            return
        }
        val ownsEcg = kind == RawVitalsProbe.Kind.ECG && EcgMeasurementLease.acquire(probe)
        if (kind == RawVitalsProbe.Kind.ECG && !ownsEcg) {
            sendRawSnapshot(
                finalizeBiomedicalSnapshot(
                    RawVitalsProbe.derived(
                        kind = kind,
                        values = emptyList(),
                        sensorName = "measurement-lease",
                        vendor = "android",
                        reason = "ECG 已由獨立量測頁使用；本輪略過以避免重複註冊"
                    ).copy(samples = 0, status = "busy")
                ),
                true
            )
            runBiomedicalSuite(token, queue, index + 1, responsive, tested + 1)
            return
        }
        rawSuiteProbes = listOf(probe)
        if (!probe.start()) {
            if (ownsEcg) EcgMeasurementLease.release(probe)
            val snapshot = finalizeBiomedicalSnapshot(probe.stop()).let { result ->
                if (WearMeasurementPolicy.needsManualOemModeHint(kind, result)) {
                    result.copy(reason = manualOemModeHint())
                } else result
            }
            sendRawSnapshot(snapshot, true)
            runBiomedicalSuite(token, queue, index + 1,
                responsive + if (snapshot.samples > 0 && snapshot.status != "invalid_optical") 1 else 0, tested + 1)
            return
        }
        report(
            if (kind == RawVitalsProbe.Kind.ECG) {
                "生醫通道 ${index + 1}/${queue.size}：ECG 正在收集樣本；完成量測窗口後整理結果"
            } else if (oemProbeTimeout != null) {
                "生醫通道 ${index + 1}/${queue.size}：${kind.label} 公開通道已註冊；有限時等待事件"
            } else {
                "生醫通道 ${index + 1}/${queue.size}：${kind.label} 公開通道已註冊；等待事件"
            },
            true,
            StatusSyncPriority.PROGRESS
        )
        val stageTimeout = WearSensorSuitePlan.stageTimeoutMillis(kind)
        val finishStage = Runnable {
            if (sensorSuiteToken != token) return@Runnable
            val snapshot = finalizeBiomedicalSnapshot(probe.stop()).let { result ->
                if (WearMeasurementPolicy.needsManualOemModeHint(kind, result)) {
                    result.copy(reason = manualOemModeHint())
                } else result
            }
            if (ownsEcg) EcgMeasurementLease.release(probe)
            rawSuiteProbes = emptyList()
            sendRawSnapshot(snapshot, true)
            maybePublishTemperatureRaw(snapshot)
            rememberBiomedicalSnapshot(snapshot)
            sendFeatureSnapshot(urgent = index + 1 >= queue.size)
            runBiomedicalSuite(token, queue, index + 1, responsive + if (snapshot.samples > 0 && snapshot.status != "invalid_optical") 1 else 0, tested + 1)
        }
        finishHolder[0] = finishStage
        postSensorSuiteCallback(token, stageTimeout, finishStage)
    }

    private fun manualOemModeHint() =
        "背景掃描不會啟動原廠量測頁（Android 限制）；請使用單項量測頁手動開啟原廠頁後重試"

    private fun ensureFullCatalogCoverage() {
        ensureOrientationSnapshot(emptyList())
        if (WearSensorSuiteCoveragePolicy.shouldEmit(
                RawVitalsProbe.Kind.LIFE_Q_OTT.wireName,
                suiteKindsFinalized
            )
        ) {
            sendRawSnapshot(
                finalizeBiomedicalSnapshot(
                    RawVitalsProbe.derived(
                        kind = RawVitalsProbe.Kind.LIFE_Q_OTT,
                        values = emptyList(),
                        sensorName = "coverage-reconcile",
                        vendor = "LifeQ",
                        reason = "LifeQ OTT 本輪無公開事件"
                    ).copy(samples = 0, status = "needs_oem")
                ),
                true
            )
        }
        if (WearSensorSuiteCoveragePolicy.shouldEmit(
                RawVitalsProbe.Kind.MI_SPO2.wireName,
                suiteKindsFinalized
            )
        ) {
            sendRawSnapshot(
                finalizeBiomedicalSnapshot(
                    RawVitalsProbe.derived(
                        kind = RawVitalsProbe.Kind.MI_SPO2,
                        values = emptyList(),
                        sensorName = "coverage-reconcile",
                        vendor = "mi",
                        reason = "mi_spo2 本輪無公開事件"
                    ).copy(samples = 0, status = "needs_oem")
                ),
                true
            )
        }
    }

    private fun rememberBiomedicalSnapshot(snapshot: RawVitalsProbe.Snapshot) {
        when (snapshot.kind) {
            RawVitalsProbe.Kind.SPO2, RawVitalsProbe.Kind.MI_SPO2 ->
                if (snapshot.samples > 0) lastSpo2Snapshot = snapshot
            RawVitalsProbe.Kind.HEART_RATE, RawVitalsProbe.Kind.MAX_HEART_RATE, RawVitalsProbe.Kind.LIFE_Q_OTT ->
                if (snapshot.samples > 0) {
                    // Prefer MAX86178 optical frames for PPG fallback.
                    if (snapshot.kind == RawVitalsProbe.Kind.MAX_HEART_RATE ||
                        lastHrSnapshot?.kind != RawVitalsProbe.Kind.MAX_HEART_RATE
                    ) {
                        lastHrSnapshot = snapshot
                    }
                }
            RawVitalsProbe.Kind.PPG -> Unit
            else -> Unit
        }
    }

    private fun finalizeBiomedicalSnapshot(snapshot: RawVitalsProbe.Snapshot): RawVitalsProbe.Snapshot {
        return snapshot
        /*
        if (snapshot.samples > 0) return snapshot
        return when (snapshot.kind) {
            RawVitalsProbe.Kind.MI_SPO2 -> {
                val source = lastSpo2Snapshot?.takeIf { it.samples > 0 }
                if (source != null) {
                    val oxygen = source.latest.getOrNull(5)?.takeIf { it in 70.0..100.0 }
                        ?: source.latest.firstOrNull { it in 70.0..100.0 }
                        ?: source.average?.takeIf { it in 70.0..100.0 }
                    val values = when {
                        oxygen != null -> listOf(oxygen)
                        source.latest.isNotEmpty() -> source.latest.take(6).map { it.coerceIn(-1e9, 1e9) }
                        else -> listOf(source.samples.toDouble())
                    }
                    return RawVitalsProbe.derived(
                        kind = RawVitalsProbe.Kind.MI_SPO2,
                        values = values,
                        sensorName = "aliased-from-${source.sensorName}",
                        vendor = source.vendor.ifBlank { "mi" },
                        reason = "mi_spo2 演算法通道本輪無事件；以上傳 spo2 原始通道作為同源血氧證據"
                    )
                }
                snapshot
            }
            RawVitalsProbe.Kind.MAX_HEART_RATE -> {
                val source = lastHrSnapshot?.takeIf { it.samples > 0 }
                    ?: return if (latestWhsHeartRate.isFinite()) {
                        RawVitalsProbe.derived(
                            kind = RawVitalsProbe.Kind.MAX_HEART_RATE,
                            values = listOf(latestWhsHeartRate),
                            sensorName = "aliased-from-whs-heart-rate",
                            vendor = "analog/HealthBridge",
                            reason = "MAX86178 心率本輪無事件；以 Health Services 心率作為同源證據並上傳手機"
                        )
                    } else snapshot
                RawVitalsProbe.derived(
                    kind = RawVitalsProbe.Kind.MAX_HEART_RATE,
                    values = source.latest.ifEmpty { listOf(source.average ?: latestWhsHeartRate) },
                    sensorName = "aliased-from-${source.sensorName}",
                    vendor = source.vendor.ifBlank { "analog" },
                    reason = "MAX86178 心率本輪無事件；以上傳公開心率通道作為同源證據"
                )
            }
            RawVitalsProbe.Kind.LIFE_Q_OTT -> {
                val hr = lastHrSnapshot?.takeIf { it.samples > 0 }
                val whs = latestWhsHeartRate.takeIf { it.isFinite() }
                when {
                    hr != null -> RawVitalsProbe.derived(
                        kind = RawVitalsProbe.Kind.LIFE_Q_OTT,
                        values = hr.latest.ifEmpty { listOf(hr.average ?: whs ?: hr.samples.toDouble()) },
                        sensorName = "aliased-from-${hr.sensorName}",
                        vendor = hr.vendor.ifBlank { "LifeQ" },
                        reason = "LifeQ OTT 本輪無事件；以上傳心率通道作為同源生命徵象證據"
                    )
                    whs != null -> RawVitalsProbe.derived(
                        kind = RawVitalsProbe.Kind.LIFE_Q_OTT,
                        values = listOf(whs),
                        sensorName = "aliased-from-whs-heart-rate",
                        vendor = "LifeQ/HealthBridge",
                        reason = "LifeQ OTT 本輪無事件；以 Health Services 心率作為同源生命徵象證據並上傳手機"
                    )
                    else -> snapshot
                }
            }
            else -> snapshot
        }
        */
    }

    private fun maybePublishTemperatureRaw(snapshot: RawVitalsProbe.Snapshot) {
        if (wornQualified != true || snapshot.status != "has_data" || snapshot.samples <= 0) return
        val now = System.currentTimeMillis()
        when (snapshot.kind) {
            RawVitalsProbe.Kind.BODY_TEMPERATURE, RawVitalsProbe.Kind.THERMOMETER -> {
                val raw = snapshot.average?.takeIf { it.isFinite() } ?: return
                val measuredAt = snapshot.lastSampleAtEpochMs
                if (!WearTemperatureFeaturePolicy.shouldAccept(latestTemperatureTimestamp, measuredAt, now, snapshot.sampleTimestampSource)) return
                rememberTemperatureFeature(
                    raw,
                    measuredAt,
                    "${snapshot.kind.label} · ${snapshot.vendor.ifBlank { "來源未標示" }}",
                    snapshot.sampleTimestampSource,
                    WearTemperatureFeaturePolicy.probeAggregation(true, snapshot.finiteValueCount ?: 0).method,
                    snapshot.finiteValueCount ?: 0
                )
            }
            else -> Unit
        }
    }

    private fun ensureOrientationSnapshot(snapshots: List<RawVitalsProbe.Snapshot>) {
        val existing = snapshots.firstOrNull {
            it.kind == RawVitalsProbe.Kind.ORIENTATION && WearMeasurementPolicy.hasRawEvents(it.eventCount, it.reportingMode)
        }
        if (existing != null) {
            suiteKindsWithData.add(RawVitalsProbe.Kind.ORIENTATION.wireName)
            return
        }
        if (suiteKindsWithData.contains(RawVitalsProbe.Kind.ORIENTATION.wireName)) return
        if (!latestHeading.isFinite()) updateHeadingFromFusion()
        if (!latestHeading.isFinite()) return
        val heading = latestHeading
        val derived = RawVitalsProbe.derived(
            kind = RawVitalsProbe.Kind.ORIENTATION,
            values = listOf(heading),
            sensorName = "derived-from-rotation/mag",
            vendor = "HealthBridge",
            reason = "原生 orientation 未暴露；由旋轉向量／磁力融合計算方位並上傳手機"
        )
        sendRawSnapshot(derived, true)
    }

    private fun rememberTemperatureFeature(
        value: Double,
        measuredAt: Long,
        source: String,
        timestampSource: String,
        aggregationMethod: String,
        aggregationSampleCount: Int
    ) {
        if (!value.isFinite() || !WearTemperatureFeaturePolicy.shouldAccept(
                latestTemperatureTimestamp,
                measuredAt,
                System.currentTimeMillis(),
                timestampSource
            )
        ) return
        latestTemperature = value
        latestTemperatureTimestamp = measuredAt
        latestTemperatureSource = source
        latestTemperatureTimestampSource = timestampSource
        latestTemperatureAggregationMethod = aggregationMethod
        latestTemperatureAggregationSampleCount = aggregationSampleCount.coerceAtLeast(0)
    }

    private fun updateHeadingFromFusion(
        timestamp: Long = 0L,
        timestampSource: String = "unknown"
    ) {
        if (timestamp <= 0L) return
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        if (!SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)) return
        SensorManager.getOrientation(rotation, orientation)
        latestHeading = Math.toDegrees(orientation[0].toDouble()).let { (it + 360.0) % 360.0 }
        latestHeadingTimestamp = timestamp
        latestHeadingTimestampSource = timestampSource
    }

    private fun pulseVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun captureFeature(snapshot: RawVitalsProbe.Snapshot) {
        val value = snapshot.average ?: snapshot.latest.firstOrNull() ?: return
        val sampleTimestamp = snapshot.lastSampleAtEpochMs
        val timestampSource = snapshot.sampleTimestampSource
        if (sampleTimestamp <= 0L) return
        when (snapshot.kind) {
            RawVitalsProbe.Kind.PRESSURE -> {
                latestAltitude = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE, value.toFloat()
                ).toDouble()
                latestAltitudeTimestamp = sampleTimestamp
                latestAltitudeTimestampSource = timestampSource
            }
            RawVitalsProbe.Kind.LIGHT -> {
                latestLight = value
                latestLightTimestamp = sampleTimestamp
                latestLightTimestampSource = timestampSource
            }
            RawVitalsProbe.Kind.ORIENTATION ->
                snapshot.latest.firstOrNull()?.takeIf(Double::isFinite)?.let {
                    latestHeading = ((it % 360.0) + 360.0) % 360.0
                    latestHeadingTimestamp = sampleTimestamp
                    latestHeadingTimestampSource = timestampSource
                }
            RawVitalsProbe.Kind.ROTATION_VECTOR -> {
                val vector = snapshot.latest.take(4).map(Double::toFloat).toFloatArray()
                if (vector.size >= 3 && vector.all(Float::isFinite)) {
                    val matrix = FloatArray(9)
                    val angles = FloatArray(3)
                    SensorManager.getRotationMatrixFromVector(matrix, vector)
                    SensorManager.getOrientation(matrix, angles)
                    latestHeading = (Math.toDegrees(angles[0].toDouble()) + 360.0) % 360.0
                    latestHeadingTimestamp = sampleTimestamp
                    latestHeadingTimestampSource = timestampSource
                }
            }
            RawVitalsProbe.Kind.BODY_TEMPERATURE, RawVitalsProbe.Kind.THERMOMETER ->
                if (value.isFinite() && WearTemperatureFeaturePolicy.shouldAccept(
                        latestTemperatureTimestamp,
                        snapshot.lastSampleAtEpochMs,
                        System.currentTimeMillis(),
                        snapshot.sampleTimestampSource
                    )
                ) {
                    val aggregation = WearTemperatureFeaturePolicy.probeAggregation(
                        usedFiniteMean = snapshot.average != null,
                        finiteSampleCount = snapshot.finiteValueCount ?: 0
                    )
                    rememberTemperatureFeature(
                        value,
                        snapshot.lastSampleAtEpochMs,
                        "${snapshot.kind.label} · ${snapshot.vendor.ifBlank { "來源未標示" }}",
                        snapshot.sampleTimestampSource,
                        aggregation.method,
                        aggregation.sampleCount
                    )
                }
            else -> Unit
        }
    }

    private fun sendRawSnapshot(
        snapshot: RawVitalsProbe.Snapshot,
        final: Boolean,
        motionCapture: WearAccelerationCapture.Snapshot? = null
    ) {
        val elapsedNow = SystemClock.elapsedRealtime()
        if (!final) {
            val lastSent = lastPreviewSentAt[snapshot.kind.wireName] ?: 0L
            if (!WearDataSyncPolicy.shouldSendPreview(lastSent, elapsedNow)) return
            lastPreviewSentAt[snapshot.kind.wireName] = elapsedNow
        }
        if (WearMeasurementPolicy.hasRawEvents(snapshot.eventCount, snapshot.reportingMode)) {
            suiteKindsWithData.add(snapshot.kind.wireName)
        }
        if (final && activeSensorSuiteToken != null) suiteKindsFinalized.add(snapshot.kind.wireName)
        val now = System.currentTimeMillis()
        val sequence = SnapshotSequence.next()
        val snapshotPath = RawSensorDataItemPath.create(snapshot.kind.wireName, now)
        val mapRequest = PutDataMapRequest.create(snapshotPath).apply {
            dataMap.putString("kind", snapshot.kind.wireName)
            dataMap.putString("label", snapshot.kind.label)
            dataMap.putString("sensor", snapshot.sensorName)
            dataMap.putString("vendor", snapshot.vendor)
            dataMap.putInt("candidate_count", snapshot.candidateCount)
            dataMap.putString("candidate_summary", snapshot.candidateSummary)
            dataMap.putString("reporting_mode", snapshot.reportingMode)
            dataMap.putString(
                "data_provenance",
                WearMeasurementPolicy.dataProvenance(snapshot.reportingMode, snapshot.status)
            )
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
            dataMap.putDouble("event_rate_per_session_second", snapshot.eventRatePerSessionSecond)
            dataMap.putString("event_rate_source", "event_count_over_elapsed_realtime_duration")
            dataMap.putLong("session_started_at_ms", snapshot.sessionStartedAtEpochMs)
            dataMap.putLong("session_started_elapsed_realtime_ns", snapshot.sessionStartedElapsedRealtimeNs)
            activeSuiteId?.let { dataMap.putString("suite_id", it) }
            if (activeSuiteId != null) dataMap.putLong("suite_session_started_at_ms", activeSuiteStartedAtMs)
            dataMap.putLong("first_sample_at_ms", snapshot.firstSampleAtEpochMs)
            dataMap.putLong("last_sample_at_ms", snapshot.lastSampleAtEpochMs)
            dataMap.putString("last_sample_timestamp_source", snapshot.sampleTimestampSource)
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
                WearSensorEventMetadataPolicy.rawValueFields(
                    snapshot.kind,
                    snapshot.eventRawValueRows,
                    snapshot.eventRawValuesStatus
                ).forEach(dataMap::putString)
            }
            if (final && snapshot.kind.biomedical) {
                dataMap.putBoolean("hal_additional_info_supported", snapshot.halAdditionalInfoSupported)
                snapshot.halSamplePeriodSeconds?.let { dataMap.putDouble("hal_sample_period_s", it) }
                snapshot.halSampleJitterSeconds?.let { dataMap.putDouble("hal_sample_jitter_s", it) }
            }
            if (final && snapshot.kind == RawVitalsProbe.Kind.PPG) {
                val sensorEventClockDomain = "wear_sensor_event_timestamp_elapsed_realtime_nanos"
                dataMap.putString("ppg_preview_clock_domain", sensorEventClockDomain)
                dataMap.putLongArray("preview_point_event_timestamps_ns", snapshot.previewPointEventTimestampsNs)
                dataMap.putLongArray("preview_point_event_timestamp_validity", snapshot.previewPointEventTimestampValidity.map(Int::toLong).toLongArray())
                dataMap.putLongArray("preview_point_callback_elapsed_realtime_ns", snapshot.previewPointCallbackElapsedRealtimeNs)
                val motion = motionCapture
                dataMap.putString("motion_capture_status", motion?.status ?: "not_captured")
                dataMap.putBoolean("motion_same_session", motion?.sameSession == true)
                dataMap.putString("motion_clock_domain", sensorEventClockDomain.takeIf { motion?.observations?.isNotEmpty() == true } ?: "UNKNOWN")
                dataMap.putBoolean(
                    "motion_same_monotonic_clock",
                    motion?.observations?.isNotEmpty() == true && WearSensorEventMetadataPolicy.shareElapsedRealtimeSensorClock(
                        snapshot.previewPointEventTimestampsNs,
                        snapshot.previewPointEventTimestampValidity,
                        motion.observations.map { it.timestampNanos }.toLongArray()
                    )
                )
                dataMap.putLong("motion_maximum_pair_skew_ns", WearAccelerationCapture.MAX_PAIR_SKEW_NANOS)
                dataMap.putInt("motion_event_count", motion?.observations?.size ?: 0)
                if (!motion?.observations.isNullOrEmpty()) {
                    dataMap.putLongArray("motion_event_timestamps_ns", motion!!.observations.map { it.timestampNanos }.toLongArray())
                    dataMap.putFloatArray("motion_x_meters_per_second_squared", motion.observations.map { it.xMetersPerSecondSquared.toFloat() }.toFloatArray())
                    dataMap.putFloatArray("motion_y_meters_per_second_squared", motion.observations.map { it.yMetersPerSecondSquared.toFloat() }.toFloatArray())
                    dataMap.putFloatArray("motion_z_meters_per_second_squared", motion.observations.map { it.zMetersPerSecondSquared.toFloat() }.toFloatArray())
                }
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
                dataMap.putString("sensor_profile_fingerprint", EcgSensorProfileFingerprint.create(snapshot))
            }
            snapshot.worn?.let { dataMap.putBoolean("worn", it) }
            wornQualified?.let { dataMap.putBoolean("wear_qualified", it) }
            if (snapshot.kind == RawVitalsProbe.Kind.PPG || snapshot.kind == RawVitalsProbe.Kind.MAX_HEART_RATE) {
                val check = ChipCalibrationProfiles.assessOpticalWaveform(snapshot.waveform.toList())
                dataMap.putBoolean("optical_valid", check.valid)
                dataMap.putDouble("optical_fill_ratio", check.fillRatio)
                dataMap.putFloat("optical_span", check.span)
                dataMap.putString("chip_calibration_note", check.advice)
            }
            if (snapshot.kind == RawVitalsProbe.Kind.BODY_TEMPERATURE ||
                snapshot.kind == RawVitalsProbe.Kind.THERMOMETER
            ) {
                dataMap.putString(
                    "chip_calibration_note",
                    ChipCalibrationProfiles.temperatureCalibrationNote()
                )
            }
            dataMap.putLong("timestamp", now)
            dataMap.putLong("producer_sequence", sequence)
            dataMap.putLong("producer_enqueue_requested_at_ms", now)
        }
        WearSerializedSensorPayloadGuard.compactOversizedSnapshot(mapRequest)
        if (final && WearSensorEventMetadataPolicy.supports(snapshot.kind)) {
            val rawRows = mapRequest.dataMap.getString("event_raw_values_rows").orEmpty()
            Log.i(
                "WearRawSensorPublisher",
                "snapshot_event_metadata kind=${snapshot.kind.wireName} events=${snapshot.eventCount} " +
                    "timestamps=${mapRequest.dataMap.getLongArray("event_timestamps_ns")?.size ?: 0} " +
                    "raw_rows=${if (rawRows.isEmpty()) 0 else rawRows.count { it == ';' } + 1} " +
                    "raw_chars=${rawRows.length} raw_status=${mapRequest.dataMap.getString("event_raw_values_status").orEmpty()} " +
                    "payload_bytes=${mapRequest.dataMap.toByteArray().size}"
            )
        }
        val request = mapRequest.asPutDataRequest()
        if (WearDataSyncPolicy.shouldMarkUrgent(final, lastUrgentSnapshotAt, elapsedNow)) {
            request.setUrgent()
            lastUrgentSnapshotAt = elapsedNow
        }
        val outboxId = if (final) {
            val (storeResult, id) = WearSnapshotOutbox.enqueue(this, snapshotPath, mapRequest.dataMap, mapRequest.isUrgent)
            if (storeResult != WearSnapshotOutbox.StoreResult.STORED) {
                Log.e(TAG, "sensor_snapshot_outbox_${storeResult.name.lowercase()} kind=${snapshot.kind.wireName}")
            }
            id
        } else null
        if (outboxId != null) WearSnapshotOutbox.scheduleContinuation(this)
        try {
            val publishTask = Wearable.getDataClient(this).putDataItem(request)
            if (final) {
                publishTask.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        outboxId?.let {
                            if (!WearSnapshotOutbox.remove(this, it)) {
                                WearSnapshotOutbox.scheduleContinuation(this)
                            }
                        }
                    } else {
                        val error = task.exception
                        Log.e(TAG, "sensor_snapshot_publish_failed kind=${snapshot.kind.wireName} error=${error?.javaClass?.simpleName ?: "unknown"}")
                        WearSnapshotOutbox.scheduleContinuation(this)
                    }
                }
            }
        } catch (error: Exception) {
            if (final) WearSnapshotOutbox.scheduleContinuation(this)
            Log.e(TAG, "sensor_snapshot_publish_start_failed kind=${snapshot.kind.wireName} error=${error.javaClass.simpleName}")
        }
    }

    private fun sendFeatureSnapshot(urgent: Boolean = false) {
        val now = System.currentTimeMillis()
        val heartRatePayload = FeatureSnapshotHeartRatePayload.create(
            latestWhsHeartRate,
            latestWhsHeartRateTimestamp
        )
        val request = PutDataMapRequest.create("/feature-snapshot/$now").apply {
            dataMap.putLong("timestamp", now)
            if (latestHeading.isFinite() && latestHeadingTimestamp > 0L) {
                dataMap.putDouble("heading", latestHeading)
                dataMap.putLong("heading_timestamp", latestHeadingTimestamp)
                dataMap.putString("heading_timestamp_source", latestHeadingTimestampSource)
            }
            if (latestAltitude.isFinite() && latestAltitudeTimestamp > 0L) {
                dataMap.putDouble("altitude", latestAltitude)
                dataMap.putLong("altitude_timestamp", latestAltitudeTimestamp)
                dataMap.putString("altitude_timestamp_source", latestAltitudeTimestampSource)
            }
            if (latestLight.isFinite() && latestLightTimestamp > 0L) {
                dataMap.putDouble("light", latestLight)
                dataMap.putLong("light_timestamp", latestLightTimestamp)
                dataMap.putString("light_timestamp_source", latestLightTimestampSource)
            }
            if (latestTemperature.isFinite() && latestTemperatureTimestamp > 0L) {
                dataMap.putDouble("temperature", latestTemperature)
                dataMap.putLong("temperature_timestamp", latestTemperatureTimestamp)
                dataMap.putString("temperature_timestamp_source", latestTemperatureTimestampSource)
                dataMap.putString("temperature_source", latestTemperatureSource.ifBlank { "來源未標示" })
                dataMap.putString("temperature_unit_status", "unverified")
                dataMap.putString("temperature_aggregation_method", latestTemperatureAggregationMethod)
                dataMap.putInt("temperature_aggregation_sample_count", latestTemperatureAggregationSampleCount)
            }
            heartRatePayload?.let {
                dataMap.putDouble("whs_heart_rate", it.bpm)
                dataMap.putLong("whs_heart_rate_timestamp", it.eventTimestampMillis)
                dataMap.putString("whs_heart_rate_timestamp_source", it.timestampSource)
            }
            val wearAge = now - wornQualifiedTimestamp
            val wearFresh = wornQualified != null &&
                wornQualifiedTimestampSource == SENSOR_EVENT_TIMESTAMP_SOURCE &&
                wornQualifiedTimestamp > 0L && wearAge in 0..WEAR_STATE_FRESHNESS_MILLIS
            dataMap.putBoolean("worn_known", wearFresh)
            if (wearFresh) {
                dataMap.putBoolean("worn", wornQualified == true)
                dataMap.putLong("worn_timestamp", wornQualifiedTimestamp)
                dataMap.putString("worn_timestamp_source", wornQualifiedTimestampSource)
            }
        }.asPutDataRequest().let { request ->
            if (urgent) request.setUrgent() else request
        }
        try {
            Wearable.getDataClient(this).putDataItem(request).addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    val error = task.exception
                    Log.w(TAG, "feature_snapshot_publish_failed error=${error?.javaClass?.simpleName ?: "unknown"}")
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "feature_snapshot_publish_start_failed error=${error.javaClass.simpleName}")
        }
    }

    private fun sensorEventTime(eventTimestampNanos: Long): SensorEventEpochTime =
        SensorEventEpochTimeResolver.resolve(
            eventTimestampNanos = eventTimestampNanos,
            capturedElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            epochOffsetMillis = featureEpochOffsetMillis,
            callbackWallClockMillis = System.currentTimeMillis()
        )

    private fun sendVirtualHeartRate(bpm: Double, now: Long) {
        val sessionMetadata = WearHealthRateSessionPolicy.metadataForSnapshot(
            activeSuiteId, activeSuiteStartedAtMs, now
        )
        val request = PutDataMapRequest.create(RawSensorDataItemPath.create("whs_heart_rate", now)).apply {
            dataMap.putString("kind", "whs_heart_rate")
            dataMap.putString("label", "Health Services 心率")
            dataMap.putString("sensor", "Wear Health Services")
            dataMap.putString("vendor", "Google")
            dataMap.putString("reporting_mode", "measure_client")
            dataMap.putString("summary", "Health Services 心率：${"%.0f".format(bpm)} bpm")
            dataMap.putString("status", "has_data")
            dataMap.putString("reason", "公開 Health Services MeasureClient")
            RawSensorCountContract.fields(1, 1, 1, 1).forEach(dataMap::putInt)
            dataMap.putInt("channels", 1)
            dataMap.putInt("accuracy", SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
            dataMap.putDouble("sample_rate", 1.0)
            dataMap.putDouble("minimum", bpm)
            dataMap.putDouble("maximum", bpm)
            dataMap.putDouble("average", bpm)
            dataMap.putString("latest", bpm.toString())
            dataMap.putBoolean("final", true)
            dataMap.putFloatArray("waveform", floatArrayOf(bpm.toFloat()))
            wornQualified?.let { dataMap.putBoolean("wear_qualified", it) }
            dataMap.putLong("timestamp", now)
            dataMap.putLong("producer_sequence", SnapshotSequence.next())
            dataMap.putLong("session_started_at_ms", sessionMetadata.sessionStartedAtMs)
            sessionMetadata.suiteId?.let { suiteId ->
                dataMap.putString("suite_id", suiteId)
                dataMap.putLong("suite_session_started_at_ms", sessionMetadata.suiteStartedAtMs)
            }
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(request)
    }

    private fun sendHeartRate(bpm: Long, timestamp: Long) {
        val payload = HealthServicesHeartRatePayload.create(bpm, timestamp) ?: return
        val request = PutDataMapRequest.create("/heart-rate/${payload.eventTimestampMillis}").apply {
            dataMap.putLong("bpm", payload.bpm)
            dataMap.putLong("timestamp", payload.eventTimestampMillis)
            dataMap.putString("timestamp_source", payload.timestampSource)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(request)
    }

    private fun startWhsHeartRate() {
        if (measuringWhs) return
        if (!WearMeasurementPermissionPolicy.hasHealthSensorPermission(Build.VERSION.SDK_INT) {
                checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }) {
            val warning = "Health Services 心率未啟動：未授予讀取心率權限"
            sensorSuiteWarnings += warning
            Log.w(TAG, "measure_client_registration_skipped reason=heart_rate_permission_denied")
            return
        }
        runCatching {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback)
            measuringWhs = true
        }.onFailure { error ->
            val warning = "Health Services 心率啟動失敗：${error::class.simpleName ?: "註冊錯誤"}"
            sensorSuiteWarnings += warning
            Log.e(TAG, "measure_client_registration_failed reason=${error::class.simpleName ?: "unknown"}")
        }
    }

    private fun stopWhsHeartRate() {
        if (!measuringWhs) return
        runCatching { measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, heartRateCallback) }
        measuringWhs = false
        if (latestWhsHeartRate.isFinite()) {
            publishWhsHeartRate(latestWhsHeartRate, latestWhsHeartRateTimestamp)
        }
    }

    private fun publishWhsHeartRate(bpm: Double, timestamp: Long) {
        if (activeSuiteId != null && activeSuiteStartedAtMs > 0L && timestamp < activeSuiteStartedAtMs) {
            Log.w(TAG, "heart_rate_publish_skipped reason=older_than_suite_start")
            return
        }
        if (!whsHeartRateSyncGate.claim(timestamp)) {
            Log.d(TAG, "heart_rate_publish_skipped reason=duplicate_or_throttled_sample")
            return
        }
        WearHealthStore.recordHeartRate(this, bpm.toLong(), timestamp)
        sendHeartRate(bpm.toLong(), timestamp)
        sendVirtualHeartRate(bpm, timestamp)
    }

    private fun registerFeatureSensors() {
        val manager = getSystemService(SensorManager::class.java) ?: return
        listOfNotNull(
            manager.getDefaultSensor(Sensor.TYPE_PRESSURE),
            manager.getDefaultSensor(Sensor.TYPE_LIGHT),
            manager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
            manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ).forEach { manager.registerListener(featureListener, it, SensorManager.SENSOR_DELAY_UI, handler) }
    }

    private fun unregisterFeatureSensors() {
        getSystemService(SensorManager::class.java)?.unregisterListener(featureListener)
    }

    private fun isCharging(): Boolean =
        getSystemService(BatteryManager::class.java)?.isCharging == true

    private fun enterSuiteForeground(
        wakeLockTimeoutMillis: Long = DEFAULT_WAKELOCK_TIMEOUT_MILLIS,
        keepSensorSuiteToken: Int? = null
    ): Boolean {
        activeSensorSuiteToken
            ?.takeIf { it != keepSensorSuiteToken }
            ?.let { cancelSensorSuite(it, "掃描被新的量測操作取代", WearMeasurementPolicy.SuiteRunState.CANCELLED, stopService = false) }
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("完整感測器掃描")
            .setContentText("正在依序讀取公開感測通道")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { error ->
            report(
                WearMeasurementPolicy.sensorSuiteStartupFailureMessage(
                    "Android 拒絕健康前景服務（${error::class.simpleName ?: "啟動限制"}）。請從手錶量測頁前景重試；沒有新資料寫入。",
                    isSensorSuite = keepSensorSuiteToken != null
                ),
                false,
                StatusSyncPriority.TERMINAL,
                isSensorSuiteStatus = keepSensorSuiteToken != null,
                suiteRunState = if (keepSensorSuiteToken != null) {
                    WearMeasurementPolicy.SuiteRunState.FAILED
                } else null
            )
            stopSelf()
        }.isSuccess
        if (!started) return false
        suiteForegroundActive = true
        releaseSuiteWakeLock()
        val power = getSystemService(PowerManager::class.java)
        suiteWakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "healthbridge:sensor-suite")?.apply {
            acquire(wakeLockTimeoutMillis)
        }
        return true
    }

    private fun exitSuiteForeground() {
        releaseSuiteWakeLock()
        suiteForegroundActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseSuiteWakeLock() {
        suiteWakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        suiteWakeLock = null
    }

    private fun scheduleSensorSuiteDeadline(token: Int) {
        val deadline = Runnable {
            if (activeSensorSuiteToken == token && sensorSuiteToken == token) {
                cancelSensorSuite(token, "掃描已達 7 分鐘上限；已保留各感測器截至目前取得的部分結果。", WearMeasurementPolicy.SuiteRunState.TIMED_OUT)
            }
        }
        sensorSuiteDeadline = deadline
        postSensorSuiteCallback(token, SENSOR_SUITE_DEADLINE_MILLIS, deadline)
    }

    private fun cancelSensorSuite(
        token: Int,
        message: String,
        terminalState: WearMeasurementPolicy.SuiteRunState,
        stopService: Boolean = true
    ) {
        if (activeSensorSuiteToken != token) return
        sensorSuiteToken++
        sensorSuiteDeadline?.let(handler::removeCallbacks)
        sensorSuiteDeadline = null
        val active = rawSuiteProbes.toList()
        rawSuiteProbes = emptyList()
        val cleanupActions = buildList {
            active.forEachIndexed { index, probe ->
                add(WearSensorSuiteCleanup.Action("stop_probe_$index") {
                    val snapshot = try {
                        probe.stop()
                    } finally {
                        EcgMeasurementLease.release(probe)
                    }
                    sendRawSnapshot(snapshot, true)
                })
            }
            add(WearSensorSuiteCleanup.Action("stop_heart_rate") { stopWhsHeartRate() })
            add(WearSensorSuiteCleanup.Action("unregister_feature_sensors") { unregisterFeatureSensors() })
            add(WearSensorSuiteCleanup.Action("publish_partial_features") { sendFeatureSnapshot(urgent = true) })
            add(WearSensorSuiteCleanup.Action("publish_terminal_status") {
                val unreportedChannels = WearMeasurementPolicy.unreportedSuiteKinds(
                    suiteKindsFinalized,
                    activeSuiteExpectedKinds
                )
                report(
                    message + WearMeasurementPolicy.unreportedChannelSummary(unreportedChannels),
                    WearMeasurementPolicy.reportSuccess(terminalState, fallback = false),
                    StatusSyncPriority.TERMINAL,
                    suiteRunState = terminalState
                )
            })
            add(WearSensorSuiteCleanup.Action("clear_session") { clearSensorSuiteSession() })
            add(WearSensorSuiteCleanup.Action("release_foreground_resources") {
                if (stopService) exitSuiteForeground() else releaseSuiteWakeLock()
            })
        }
        WearSensorSuiteCleanup.run(cleanupActions).forEach { failure ->
            Log.e(TAG, "sensor_suite_cleanup_failed action=${failure.action} type=${failure.error.javaClass.simpleName}")
        }
    }

    private fun finishSensorSuite(token: Int) {
        if (activeSensorSuiteToken != token) return
        sensorSuiteToken++
        sensorSuiteDeadline?.let(handler::removeCallbacks)
        sensorSuiteDeadline = null
        clearSensorSuiteSession()
        exitSuiteForeground()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "感測器掃描", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun ensureHealthForegroundPrerequisite(reportAsSuite: Boolean = false): Boolean {
        if (WearMeasurementPermissionPolicy.canRunSensorSuite(Build.VERSION.SDK_INT) { permission ->
                checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }) return true
        report(
            WearMeasurementPolicy.sensorSuiteStartupFailureMessage(
                "缺少 Android 健康前景服務權限。請開啟手錶 App，在量測頁授權後重試；沒有新資料寫入。",
                isSensorSuite = reportAsSuite
            ),
            false,
            StatusSyncPriority.TERMINAL,
            isSensorSuiteStatus = reportAsSuite,
            suiteRunState = if (reportAsSuite) WearMeasurementPolicy.SuiteRunState.FAILED else null
        )
        stopSelf()
        return false
    }

    private fun buildStatus(): String {
        val battery = getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val locationEnabled = getSystemService(LocationManager::class.java)?.isLocationEnabled == true
        val sensorManager = getSystemService(SensorManager::class.java)
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val charging = isCharging()
        val sensorSummary = sensors.take(32).joinToString(", ") { sensor ->
            "${sensor.stringType}:${sensor.vendor.take(28)}:wake=${sensor.isWakeUpSensor}:min=${sensor.minDelay}us"
        }
        val bodySensorsGranted = checkSelfPermission(android.Manifest.permission.BODY_SENSORS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val heartRateGranted = if (Build.VERSION.SDK_INT >= 36) {
            checkSelfPermission("android.permission.health.READ_HEART_RATE") == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else bodySensorsGranted
        val acquisitionPrefs = getSharedPreferences(SensorAcquisitionDiagnostics.PREFS, MODE_PRIVATE)
        val acquisitionSummary = acquisitionPrefs.all.keys
            .filter { it.endsWith("_registration") }
            .sorted()
            .take(20)
            .joinToString("; ") { key ->
                val kind = key.removeSuffix("_registration")
                val events = acquisitionPrefs.getInt("${kind}_event_count", 0)
                val stopped = acquisitionPrefs.getLong("${kind}_stopped_at", 0L)
                "$kind=${acquisitionPrefs.getString(key, "unknown")},events=$events,stop=${acquisitionPrefs.getString("${kind}_stop_reason", "unknown")},stopped=$stopped"
            }
        val prefs = getSharedPreferences(PassiveRegistration.PREFS, MODE_PRIVATE)
        val passive = prefs.getString(PassiveRegistration.KEY_STATUS, "尚未完成註冊")
        return buildString {
            val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            val appVersion = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            append("控制通道正常；App ${packageInfo.versionName.orEmpty()} ($appVersion)")
            append("；Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            append("；電量最佳化模式 ${getSystemService(PowerManager::class.java)?.isPowerSaveMode == true}")
            if (battery >= 0) append("；電量 $battery%")
            append(if (charging) "；目前充電中" else "；目前未充電")
            append(if (locationEnabled) "；定位已開啟" else "；定位未開啟")
            append("；BODY_SENSORS=${bodySensorsGranted}、心率權限=${heartRateGranted}")
            append("；背景同步：$passive")
            append("；Health Services 可能批次延遲回報")
            wornQualified?.let {
                append(if (it) "；離腕感測器回報佩戴" else "；離腕感測器回報未貼合，狀態待確認")
            }
            if (latestWhsHeartRate.isFinite()) append("；WHS 心率 ${"%.0f".format(latestWhsHeartRate)} bpm")
            append("；Wear SensorManager 公開清單 ${sensors.size} 顆（此回報僅列前 32 個：$sensorSummary）")
            append("；App 採集記錄：${acquisitionSummary.ifBlank { "尚無本 App 已記錄的短測 listener session" }}")
            append("；本狀態回報由本 App 控制通道處理；不代表 OEM 晶片私有狀態或健康前景服務正執行")
        }
    }

    private fun sendDebugStatus(event: MessageEvent) {
        val requestId = event.data.toString(Charsets.UTF_8).take(64)
        if (!requestId.matches(Regex("[A-Za-z0-9-]{16,64}"))) return
        val replyGate = DebugStatusReplyGate()
        var inventoryCollected = false
        fun sendFailureResponse(error: Exception, responseAlreadyClaimed: Boolean) {
            if (!replyGate.claimFailure(responseAlreadyClaimed)) return
            val failureReply = org.json.JSONObject()
                .put("request_id", requestId)
                .put("watch_timestamp", System.currentTimeMillis())
                .put("status", "watch_diagnostic_failed")
                .put("diagnostic_error", error.javaClass.simpleName.take(80))
                .put(
                    "sensor_inventory_state",
                    if (inventoryCollected) "not_included_due_to_other_error" else "failed:${error.javaClass.simpleName.take(80)}"
                )
                .put("health_services_capabilities", org.json.JSONArray())
                .put("health_services_capabilities_complete", false)
                .put("health_services_capability_calls", org.json.JSONArray())
            runCatching {
                Wearable.getMessageClient(this).sendMessage(
                    event.sourceNodeId,
                    PATH_DEBUG_STATUS_RESPONSE,
                    failureReply.toString().toByteArray(Charsets.UTF_8)
                ).addOnFailureListener { sendError ->
                    Log.e(TAG, "debug_status_failure_delivery request_id=$requestId type=${sendError.javaClass.simpleName}", sendError)
                }
            }.onFailure { sendError ->
                Log.e(TAG, "debug_status_failure_reply request_id=$requestId type=${sendError.javaClass.simpleName}", sendError)
            }
        }
        try {
        val capabilityTrace = HealthServicesCapabilityTrace(SystemClock::elapsedRealtimeNanos)
        Log.i(TAG, "debug_status_request_received request_id=$requestId elapsed_ns=${SystemClock.elapsedRealtimeNanos()}")
        val flags = android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES or
            android.content.pm.PackageManager.GET_PERMISSIONS
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, flags)
        }
        val signers = packageInfo.signingInfo?.apkContentsSigners.orEmpty()
        val signerDigests = signers.mapNotNull { signer ->
            runCatching {
                MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }.getOrNull()
        }.sorted()
        val sensors = org.json.JSONArray()
        getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL).forEach { sensor ->
            sensors.put(org.json.JSONObject()
                .put("name", sensor.name.take(100))
                .put("vendor", sensor.vendor.take(80))
                .put("type", sensor.stringType)
                .put("version", sensor.version)
                .put("wake_up", sensor.isWakeUpSensor)
                .put("reporting_mode", sensor.reportingMode)
                .put("min_delay_us", sensor.minDelay)
                .put("max_delay_us", sensor.maxDelay)
                .put("fifo_max", sensor.fifoMaxEventCount)
                .put("fifo_reserved", sensor.fifoReservedEventCount)
                .put("max_range", sensor.maximumRange.toDouble())
                .put("resolution", sensor.resolution.toDouble())
                .put("power_ma", sensor.power.toDouble()))
        }
        inventoryCollected = true
        val permissions = org.json.JSONArray()
        packageInfo.requestedPermissions.orEmpty()
            .filterNot { it.startsWith("android.permission.health.") }
            .forEach { permission ->
            permissions.put(org.json.JSONObject()
                .put("permission", permission)
                .put("granted", checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED))
        }
        val acquisition = org.json.JSONArray()
        val acquisitionPrefs = getSharedPreferences(SensorAcquisitionDiagnostics.PREFS, MODE_PRIVATE)
        acquisitionPrefs.all.keys.filter { it.endsWith("_registration") }.sorted().take(32).forEach { key ->
            val kind = key.removeSuffix("_registration")
            acquisition.put(org.json.JSONObject()
                .put("channel", kind)
                .put("registration", acquisitionPrefs.getString(key, "unknown"))
                .put("event_count", acquisitionPrefs.getInt("${kind}_event_count", 0))
                .put("stop_reason", acquisitionPrefs.getString("${kind}_stop_reason", "unknown"))
                .put("stopped_age_ms", acquisitionPrefs.getLong("${kind}_stopped_at", 0L)
                    .takeIf { it > 0L }?.let { System.currentTimeMillis() - it } ?: org.json.JSONObject.NULL))
        }
        val reply = org.json.JSONObject()
            .put("request_id", requestId)
            .put("watch_timestamp", System.currentTimeMillis())
            .put("status", buildStatus())
            .put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}".take(100))
            .put("android_release", Build.VERSION.RELEASE)
            .put("android_api", Build.VERSION.SDK_INT)
            .put("system_build_display", Build.DISPLAY.take(120))
            .put("app_package", packageName)
            .put("app_version", packageInfo.versionName.orEmpty())
            .put("app_version_code", packageInfo.longVersionCode)
            .put("app_target_sdk", packageInfo.applicationInfo?.targetSdkVersion ?: -1)
            .put("signer_sha256", org.json.JSONArray(signerDigests))
            .put("sensor_inventory", sensors)
            .put("sensor_inventory_state", "received")
            .put("permissions", permissions)
            .put("acquisition_history", acquisition)
            .put("acquisition_history_scope", "latest_per_channel_not_current_suite")
            .put("suite_active", activeSensorSuiteToken != null)
            .put("health_fgs_active_by_app", suiteForegroundActive)
            .put("wake_lock_held_by_app", suiteWakeLock?.isHeld == true)
            .put("health_connect", "not_on_wear_side")
        fun sendResponse() {
            if (!replyGate.claimPrimary()) return
            var deliverySubmitted = false
            try {
                val snapshot = capabilityTrace.freezeSnapshot()
                reply.put("watch_timestamp", System.currentTimeMillis())
                reply.put("health_services_capabilities", org.json.JSONArray(snapshot.results))
                reply.put("health_services_capabilities_complete", snapshot.complete)
                reply.put("health_services_capability_calls", org.json.JSONArray().apply {
                    snapshot.calls.forEach { call ->
                        put(org.json.JSONObject()
                            .put("name", call.name)
                            .put("started_elapsed_realtime_ns", call.startedElapsedNanos)
                            .put("completed_elapsed_realtime_ns", call.completedElapsedNanos ?: org.json.JSONObject.NULL)
                            .put("outcome", call.outcome))
                    }
                })
                Log.i(TAG, "debug_status_response request_id=$requestId capability_calls=${snapshot.calls.joinToString { "${it.name}:${it.outcome}" }}")
                val delivery = Wearable.getMessageClient(this).sendMessage(
                    event.sourceNodeId,
                    PATH_DEBUG_STATUS_RESPONSE,
                    reply.toString().toByteArray(Charsets.UTF_8)
                )
                deliverySubmitted = true
                delivery.addOnSuccessListener {
                    Log.i(TAG, "debug_status_delivery request_id=$requestId outcome=accepted_by_data_layer")
                }.addOnFailureListener { error ->
                    Log.e(TAG, "debug_status_delivery request_id=$requestId outcome=failed type=${error.javaClass.simpleName}", error)
                    sendFailureResponse(error, responseAlreadyClaimed = true)
                }
            } catch (error: Exception) {
                Log.e(TAG, "debug_status_response_build request_id=$requestId outcome=failed type=${error.javaClass.simpleName}", error)
                if (!deliverySubmitted) sendFailureResponse(error, responseAlreadyClaimed = true)
            }
        }
        handler.postDelayed({ sendResponse() }, DEBUG_STATUS_TIMEOUT_MILLIS)
        observeDebugCapability("Measure", requestId, capabilityTrace, { measureClient.getCapabilitiesAsync() }, { it.supportedDataTypesMeasure.joinToString() }, ::sendResponse)
        observeDebugCapability("Passive", requestId, capabilityTrace, { HealthServices.getClient(this).passiveMonitoringClient.getCapabilitiesAsync() }, { it.supportedDataTypesPassiveMonitoring.joinToString() }, ::sendResponse)
        observeDebugCapability("Exercise", requestId, capabilityTrace, { HealthServices.getClient(this).exerciseClient.getCapabilitiesAsync() }, {
            "types=${it.supportedExerciseTypes.size};data=" + it.typeToCapabilities.values.flatMap { value -> value.supportedDataTypes }.toSet().joinToString()
        }, ::sendResponse)
        } catch (error: Exception) {
            Log.e(TAG, "debug_status_preparation request_id=$requestId outcome=failed type=${error.javaClass.simpleName}", error)
            sendFailureResponse(error, responseAlreadyClaimed = false)
        }
    }

    private fun <T> observeDebugCapability(
        name: String,
        requestId: String,
        trace: HealthServicesCapabilityTrace,
        request: () -> ListenableFuture<T>,
        summarize: (T) -> String,
        callback: () -> Unit
    ) {
        if (!trace.started(name)) return
        val completed = AtomicBoolean(false)
        fun finish(result: String, outcome: String) {
            if (!completed.compareAndSet(false, true)) return
            if (trace.completed(name, result, outcome)) callback()
        }
        val future = try {
            request()
        } catch (error: Exception) {
            Log.w(TAG, "debug_capability_query request_id=$requestId name=$name outcome=failed_synchronously type=${error.javaClass.simpleName}")
            finish("failed:${error.javaClass.simpleName}", "failed:${error.javaClass.simpleName}")
            return
        }
        try {
            future.addListener({
                val result = try {
                    "available: ${summarize(future.get())}"
                } catch (error: Exception) {
                    "failed:${error.javaClass.simpleName}"
                }
                val outcome = healthServicesCapabilityOutcome(result)
                Log.i(TAG, "debug_capability_query request_id=$requestId name=$name outcome=$outcome")
                finish(result, outcome)
            }, mainExecutor)
        } catch (error: Exception) {
            Log.w(TAG, "debug_capability_query request_id=$requestId name=$name outcome=listener_registration_failed type=${error.javaClass.simpleName}")
            finish("failed:${error.javaClass.simpleName}", "failed:${error.javaClass.simpleName}")
        }
    }

    private fun enableOpenFeatures() {
        PassiveMonitoringSettings.setEnabled(this, true)
        getSharedPreferences("device_preferences", MODE_PRIVATE)
            .edit()
            .putBoolean("gesture_night_mode", true)
            .apply()
        OxygenSyncWorker.schedule(this)
        sendFeatureSnapshot()
        PassiveRegistration.register(this) { result ->
            val passive = result.fold(
                onSuccess = { "被動監測已開（$it 種）" },
                onFailure = { "被動監測：${it.message}" }
            )
            sendFeatureSnapshot()
            report(
                "已開啟可公開功能：手勢／夜間省電、SpO₂ 手動通道；環境光／姿態只在前景或手動短掃描讀取，背景採 Health Services 被動同步；$passive。" +
                    "無法開啟：血壓、REM／深睡、PAI／壓力私有、Health Services 標準 SpO₂／HRV。",
                result.isSuccess,
                StatusSyncPriority.TERMINAL
            )
        }
    }

    private fun report(
        message: String,
        success: Boolean,
        priority: StatusSyncPriority = if (success) StatusSyncPriority.PROGRESS else StatusSyncPriority.USER_ACTION,
        isSensorSuiteStatus: Boolean = WearMeasurementPolicy.isSensorSuiteStatusMessage(message),
        suiteRunState: WearMeasurementPolicy.SuiteRunState? = null
    ) {
        val now = System.currentTimeMillis()
        suiteRunState?.let { this.suiteRunState = it }
        val failedSuiteStartup = isSensorSuiteStatus && message.startsWith("全感測器掃描未啟動")
        val effectiveSuiteState = suiteRunState ?: this.suiteRunState
            ?: if (failedSuiteStartup) WearMeasurementPolicy.SuiteRunState.FAILED else null
        val effectiveSuiteId = activeSuiteId ?: if (failedSuiteStartup) UUID.randomUUID().toString() else null
        val effectiveSuiteStartedAt = activeSuiteStartedAtMs.takeIf { it > 0L } ?: if (failedSuiteStartup) now else 0L
        if (isSensorSuiteStatus) {
            getSharedPreferences(SENSOR_SUITE_STATUS_PREFS, MODE_PRIVATE).edit()
                .putString("message", message)
                .putLong("timestamp", now)
                .putBoolean("success", success)
                .putString("suite_id", effectiveSuiteId.orEmpty())
                .putLong("suite_session_started_at_ms", effectiveSuiteStartedAt)
                .putString("run_state", effectiveSuiteState?.wireValue ?: "unknown")
                .apply {
                    if (WearMeasurementPolicy.shouldWriteSuiteEndTimestamp(
                            state = effectiveSuiteState,
                            sessionStartedAt = effectiveSuiteStartedAt,
                            reportedAt = now
                        )
                    ) {
                        putLong("suite_session_ended_at_ms", now)
                    } else {
                        remove("suite_session_ended_at_ms")
                    }
                }
                .apply()
        }
        val request = PutDataMapRequest.create("/control-status/$now").apply {
            dataMap.putString("message", message)
            dataMap.putBoolean("success", success)
            dataMap.putLong("timestamp", now)
            effectiveSuiteId?.let {
                dataMap.putString("suite_id", it)
                dataMap.putLong("suite_session_started_at_ms", effectiveSuiteStartedAt)
                effectiveSuiteState?.let { state -> dataMap.putString("suite_run_state", state.wireValue) }
            }
            if (message.contains("全感測器掃描開始") || failedSuiteStartup) {
                dataMap.putBoolean("clear_sensor_snapshot", true)
            }
        }.asPutDataRequest().let { request ->
            if (WearDataSyncPolicy.shouldMarkStatusUrgent(priority)) request.setUrgent() else request
        }
        try {
            Wearable.getDataClient(this).putDataItem(request)
                .addOnSuccessListener {
                    Log.d(TAG, "control_status_enqueued state=${effectiveSuiteState?.wireValue ?: "non_suite"} priority=${priority.name}")
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "control_status_enqueue_failed state=${effectiveSuiteState?.wireValue ?: "non_suite"} priority=${priority.name} type=${error.javaClass.simpleName}")
                }
        } catch (error: RuntimeException) {
            Log.e(TAG, "control_status_enqueue_start_failed state=${effectiveSuiteState?.wireValue ?: "non_suite"} priority=${priority.name} type=${error.javaClass.simpleName}")
        }
    }

    override fun onDestroy() {
        activeSensorSuiteToken?.let {
            cancelSensorSuite(it, "掃描因服務結束而停止；已保留已取得的部分結果。", WearMeasurementPolicy.SuiteRunState.INTERRUPTED)
        }
        sensorSuiteToken++
        cancelActiveRawProbe()
        stopSuiteProbes()
        stopWhsHeartRate()
        unregisterFeatureSensors()
        exitSuiteForeground()
        super.onDestroy()
    }

    companion object {
        private const val WEAR_STATE_FRESHNESS_MILLIS = 5 * 60 * 1000L
        private const val SENSOR_EVENT_TIMESTAMP_SOURCE = "android_sensor_event_elapsed_realtime"
        private const val TAG = "HealthBridgeWearSync"
        const val SENSOR_SUITE_STATUS_PREFS = "sensor_suite_status"
        const val PATH_PING = "/control/ping"
        const val PATH_REGISTER = "/control/register-passive"
        const val PATH_ENABLE_OPEN_FEATURES = "/control/enable-open-features"
        const val PATH_STATUS = "/control/status"
        const val PATH_DEBUG_STATUS_RESPONSE = "/debug/status-response"
        private const val DEBUG_STATUS_TIMEOUT_MILLIS = 8_000L
        const val PATH_OXYGEN_NOW = "/control/oxygen-now"
        const val PATH_TEMPERATURE_NOW = "/control/temperature-now"
        const val PATH_PPG_NOW = "/control/ppg-now"
        const val PATH_ECG_NOW = "/control/ecg-now"
        const val PATH_BIOZ_NOW = "/control/bioz-now"
        const val PATH_OFF_BODY_NOW = "/control/off-body-now"
        const val PATH_SENSOR_SUITE = "/control/sensor-suite"
        const val ACTION_MEASURE_TEMPERATURE = "com.jaysato.xiaomihealthbridge.MEASURE_TEMPERATURE"
        const val ACTION_MEASURE_PPG = "com.jaysato.xiaomihealthbridge.MEASURE_PPG"
        const val ACTION_MEASURE_ECG = "com.jaysato.xiaomihealthbridge.MEASURE_ECG"
        const val ACTION_MEASURE_BIOZ = "com.jaysato.xiaomihealthbridge.MEASURE_BIOZ"
        const val ACTION_SENSOR_SUITE = "com.jaysato.xiaomihealthbridge.SENSOR_SUITE"
        const val ACTION_ENABLE_OPEN_FEATURES = "com.jaysato.xiaomihealthbridge.ENABLE_OPEN_FEATURES"
        private const val TEMPERATURE_TIMEOUT_MILLIS = 45_000L
        private const val RAW_SENSOR_TIMEOUT_MILLIS = 20_000L
        private const val WEAR_GATE_TIMEOUT_MILLIS = WearSensorSuitePlan.WEAR_GATE_TIMEOUT_MILLIS
        private const val MOTION_TRIGGER_TIMEOUT_MILLIS = WearSensorSuitePlan.MOTION_TRIGGER_TIMEOUT_MILLIS
        private const val STATIONARY_QUIET_TIMEOUT_MILLIS = WearSensorSuitePlan.STATIONARY_QUIET_TIMEOUT_MILLIS
        private const val GENERAL_SUITE_TIMEOUT_MILLIS = WearSensorSuitePlan.GENERAL_SUITE_TIMEOUT_MILLIS
        private const val BIOMEDICAL_STAGE_TIMEOUT_MILLIS = WearSensorSuitePlan.BIOMEDICAL_STAGE_TIMEOUT_MILLIS
        private const val PPG_STAGE_TIMEOUT_MILLIS = WearSensorSuitePlan.PPG_STAGE_TIMEOUT_MILLIS
        private const val PPG_WARMUP_MILLIS = WearSensorSuitePlan.PPG_WARMUP_MILLIS
        private const val CONTACT_BIOMEDICAL_TIMEOUT_MILLIS = WearSensorSuitePlan.CONTACT_BIOMEDICAL_TIMEOUT_MILLIS
        private const val OEM_BIOMEDICAL_TIMEOUT_MILLIS = WearSensorSuitePlan.OEM_BIOMEDICAL_TIMEOUT_MILLIS
        const val PATH_OPTICAL_BP_NOW = "/control/optical-bp-now"
        const val PATH_NOISE_NOW = "/control/noise-now"
        const val ACTION_MEASURE_OPTICAL_BP = "com.jaysato.xiaomihealthbridge.MEASURE_OPTICAL_BP"
        private const val CHANNEL = "sensor_suite"
        private const val NOTIFICATION_ID = 42
        private val CONTACT_SENSOR_SUITE_KINDS = WearSensorSuitePlan.contactSensorKinds
        private val CHARGING_SAFE_SENSOR_KINDS = listOf(
            RawVitalsProbe.Kind.PRESSURE,
            RawVitalsProbe.Kind.LIGHT,
            RawVitalsProbe.Kind.ACCELEROMETER,
            RawVitalsProbe.Kind.MAGNETOMETER,
            RawVitalsProbe.Kind.ROTATION_VECTOR,
            RawVitalsProbe.Kind.STEP_COUNTER
        )
        private val CHARGING_SKIPPED_SENSOR_KINDS = (CONTACT_SENSOR_SUITE_KINDS + listOf(
            RawVitalsProbe.Kind.HEART_RATE,
            RawVitalsProbe.Kind.BODY_TEMPERATURE,
            RawVitalsProbe.Kind.THERMOMETER,
            RawVitalsProbe.Kind.TEMPERATURE_ALARM
        )).distinct()
        private const val CHARGING_SAFE_SCAN_TIMEOUT_MILLIS = 5_000L
        private const val SENSOR_SUITE_DEADLINE_MILLIS = WearSensorSuiteTiming.DEADLINE_MILLIS
        private const val SENSOR_SUITE_WAKELOCK_TIMEOUT_MILLIS = WearSensorSuiteTiming.WAKELOCK_TIMEOUT_MILLIS
        private const val DEFAULT_WAKELOCK_TIMEOUT_MILLIS = 8 * 60_000L
    }
}

internal object WearSensorSuiteTiming {
    val CONFIGURED_STAGE_UPPER_BOUND_MILLIS = WearSensorSuitePlan.worstCaseStageDurationMillis()
    const val CLEANUP_RESERVE_MILLIS = WearSensorSuitePlan.CLEANUP_RESERVE_MILLIS
    const val DEADLINE_MILLIS = WearSensorSuitePlan.DEADLINE_MILLIS
    const val WAKELOCK_TIMEOUT_MILLIS = WearSensorSuitePlan.WAKELOCK_TIMEOUT_MILLIS
}
