package com.jaysato.xiaomihealthbridge

internal object WearSensorSuitePlan {
    const val WEAR_GATE_TIMEOUT_MILLIS = 8_000L
    const val MOTION_TRIGGER_TIMEOUT_MILLIS = 18_000L
    const val STATIONARY_QUIET_TIMEOUT_MILLIS = 12_000L
    const val GENERAL_SUITE_TIMEOUT_MILLIS = 12_000L
    const val BIOMEDICAL_STAGE_TIMEOUT_MILLIS = 45_000L
    const val PPG_STAGE_TIMEOUT_MILLIS = 15_000L
    const val PPG_WARMUP_MILLIS = 8_000L
    const val CONTACT_BIOMEDICAL_TIMEOUT_MILLIS = 60_000L
    const val OEM_BIOMEDICAL_TIMEOUT_MILLIS = 60_000L
    const val CLEANUP_RESERVE_MILLIS = 30_000L
    const val DEADLINE_MILLIS = 7 * 60_000L
    const val WAKELOCK_TIMEOUT_MILLIS = DEADLINE_MILLIS + CLEANUP_RESERVE_MILLIS

    val wearGateKinds = listOf(
        RawVitalsProbe.Kind.OFF_BODY,
        RawVitalsProbe.Kind.ADT,
        RawVitalsProbe.Kind.HARDWARE_ADT
    )

    val motionTriggerKinds = listOf(
        RawVitalsProbe.Kind.WRIST_TILT,
        RawVitalsProbe.Kind.STEP_DETECTOR,
        RawVitalsProbe.Kind.SIGNIFICANT_MOTION,
        RawVitalsProbe.Kind.MOTION,
        RawVitalsProbe.Kind.STATIONARY
    )

    val contactSensorKinds = listOf(
        RawVitalsProbe.Kind.MAX_HEART_RATE,
        RawVitalsProbe.Kind.PPG,
        RawVitalsProbe.Kind.SPO2,
        RawVitalsProbe.Kind.MI_SPO2,
        RawVitalsProbe.Kind.LIFE_Q_OTT,
        RawVitalsProbe.Kind.ECG,
        RawVitalsProbe.Kind.BIOZ
    )

    fun biomedicalQueue(): List<RawVitalsProbe.Kind> =
        (listOf(RawVitalsProbe.Kind.HEART_RATE) + contactSensorKinds).distinct()

    fun generalQueue(allKinds: List<RawVitalsProbe.Kind>): List<RawVitalsProbe.Kind> =
        allKinds.filterNot { it.biomedical || it in wearGateKinds || it in motionTriggerKinds }

    fun stageTimeoutMillis(kind: RawVitalsProbe.Kind): Long =
        WearMeasurementPolicy.broadSuiteOemProbeTimeoutMillis(kind) ?: when (kind) {
            RawVitalsProbe.Kind.PPG -> PPG_STAGE_TIMEOUT_MILLIS
            RawVitalsProbe.Kind.ECG -> WearMeasurementPolicy.ECG_SUITE_DISCOVERY_TIMEOUT_MILLIS
            RawVitalsProbe.Kind.BIOZ -> CONTACT_BIOMEDICAL_TIMEOUT_MILLIS
            RawVitalsProbe.Kind.MI_SPO2, RawVitalsProbe.Kind.LIFE_Q_OTT -> OEM_BIOMEDICAL_TIMEOUT_MILLIS
            else -> BIOMEDICAL_STAGE_TIMEOUT_MILLIS
        }

    fun worstCaseStageDurationMillis(): Long =
        wearGateKinds.size * WEAR_GATE_TIMEOUT_MILLIS +
            MOTION_TRIGGER_TIMEOUT_MILLIS +
            STATIONARY_QUIET_TIMEOUT_MILLIS +
            GENERAL_SUITE_TIMEOUT_MILLIS +
            biomedicalQueue().sumOf(::stageTimeoutMillis)
}

internal object WearSensorSuiteCleanup {
    data class Action(val name: String, val run: () -> Unit)

    data class Failure(val action: String, val error: Throwable)

    fun run(actions: List<Action>): List<Failure> = buildList {
        actions.forEach { action ->
            runCatching(action.run).exceptionOrNull()?.let { add(Failure(action.name, it)) }
        }
    }
}
