package com.jaysato.xiaomihealthbridge

object BloodPressureEvidencePolicy {
    const val HC_PREFS = "health_connect_blood_pressure_evidence"
    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    enum class Origin { MANUAL_CUFF, HEALTH_CONNECT }

    data class Evidence(
        val systolic: Double,
        val diastolic: Double,
        val measuredAt: Long,
        val source: String,
        val origin: Origin,
        val measurementMethod: String? = null
    ) {
        val researchOnly: Boolean get() = origin == Origin.HEALTH_CONNECT
    }

    fun isValid(evidence: Evidence, now: Long): Boolean =
        evidence.measuredAt > 0L && evidence.measuredAt <= now &&
            now - evidence.measuredAt <= MAX_AGE_MS &&
            evidence.systolic.isFinite() && evidence.diastolic.isFinite() &&
            evidence.systolic in 70.0..250.0 && evidence.diastolic in 35.0..150.0 &&
            evidence.systolic > evidence.diastolic && evidence.source.isNotBlank()

    fun newestValid(candidates: List<Evidence>, now: Long): Evidence? =
        candidates.filter { isValid(it, now) }.maxWithOrNull(
            compareBy<Evidence> { it.measuredAt }
                .thenBy { it.origin == Origin.MANUAL_CUFF }
                .thenBy { it.source }
        )
}
