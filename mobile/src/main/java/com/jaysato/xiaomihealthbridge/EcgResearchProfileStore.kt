package com.jaysato.xiaomihealthbridge

import android.content.Context
import com.jaysato.xiaomihealthbridge.engine.EcgCalibrationPairingEvidence
import com.jaysato.xiaomihealthbridge.engine.EcgCalibrationProfile
import com.jaysato.xiaomihealthbridge.engine.EcgCoreResearchEngine
import java.util.UUID

/**
 * A user-supplied, research-only raw-to-mV fit from an isolated ECG reference source.
 * It is deliberately separate from device/OEM calibration and never enables diagnosis.
 */
class EcgResearchProfileStore(private val context: Context) {
    data class Profile(
        val slopeMvPerRaw: Double,
        val offsetMv: Double,
        val rmseMv: Double,
        val validationRmseMv: Double,
        val maximumAllowedRmseMv: Double,
        val points: Int,
        val validatedPoints: Int,
        val independentlyValidated: Boolean,
        val inputEncoding: String,
        val source: String,
        val createdAtMillis: Long,
        val referenceInstrument: String,
        val protocolId: String,
        val sensorProfileFingerprint: String,
        val trainingCaptureId: String,
        val holdoutCaptureId: String,
        val trainingRawMinimum: Double,
        val trainingRawMaximum: Double,
        val profileId: String = "",
        val trainingRaw: List<Double> = emptyList(),
        val trainingReferenceMv: List<Double> = emptyList(),
        val holdoutRaw: List<Double> = emptyList(),
        val holdoutReferenceMv: List<Double> = emptyList(),
        val maximumPairSkewNanos: Long = 0L,
        val trainingRawTimestampsNanos: List<Long> = emptyList(),
        val trainingReferenceTimestampsNanos: List<Long> = emptyList(),
        val holdoutRawTimestampsNanos: List<Long> = emptyList(),
        val holdoutReferenceTimestampsNanos: List<Long> = emptyList()
    ) {
        fun isBoundToSensorProfile(fingerprint: String?, expectedProfileId: String? = profileId): Boolean =
            fingerprint != null && fingerprint.matches(Regex("[0-9a-f]{64}")) &&
                sensorProfileFingerprint == fingerprint && !expectedProfileId.isNullOrBlank() &&
                profileId == expectedProfileId

        fun toCoreCalibrationProfile(): EcgCalibrationProfile? = EcgCoreResearchEngine.fitResearchCalibration(
            trainRaw = trainingRaw,
            trainReferenceMv = trainingReferenceMv,
            holdoutRaw = holdoutRaw,
            holdoutReferenceMv = holdoutReferenceMv,
            profileId = profileId,
            trainingDatasetId = trainingCaptureId,
            holdoutDatasetId = holdoutCaptureId,
            inputEncoding = inputEncoding,
            maxHoldoutRmseMv = maximumAllowedRmseMv,
            sensorProfileFingerprint = sensorProfileFingerprint,
            pairingEvidence = EcgCalibrationPairingEvidence(
                maximumPairSkewNanos,
                trainingRawTimestampsNanos,
                trainingReferenceTimestampsNanos,
                holdoutRawTimestampsNanos,
                holdoutReferenceTimestampsNanos
            )
        )?.takeIf { fitted ->
            fitted.trainingPoints == points && fitted.holdoutPoints == validatedPoints &&
                fitted.trainingRawMinimum == trainingRawMinimum && fitted.trainingRawMaximum == trainingRawMaximum &&
                kotlin.math.abs(fitted.slopeMvPerRaw - slopeMvPerRaw) <= 1e-12 &&
                kotlin.math.abs(fitted.offsetMv - offsetMv) <= 1e-12 &&
                kotlin.math.abs(fitted.holdoutRmseMv - validationRmseMv) <= 1e-12
        }

        val isEligibleForResearchMv: Boolean
            get() = slopeMvPerRaw.isFinite() && slopeMvPerRaw != 0.0 &&
                offsetMv.isFinite() && rmseMv.isFinite() && rmseMv >= 0.0 &&
                validationRmseMv.isFinite() && validationRmseMv >= 0.0 &&
                maximumAllowedRmseMv.isFinite() && maximumAllowedRmseMv > 0.0 &&
                validationRmseMv <= maximumAllowedRmseMv &&
                points >= MIN_POINTS && validatedPoints >= MIN_POINTS &&
                independentlyValidated && inputEncoding == EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING &&
                sensorProfileFingerprint.matches(Regex("[0-9a-f]{64}")) &&
                source.isNotBlank() && referenceInstrument.isNotBlank() && protocolId.isNotBlank() &&
                trainingCaptureId.isNotBlank() && holdoutCaptureId.isNotBlank() &&
                trainingCaptureId != holdoutCaptureId && trainingRawMinimum.isFinite() &&
                trainingRawMaximum.isFinite() && trainingRawMaximum > trainingRawMinimum && createdAtMillis > 0L &&
                pairedTimingEvidenceValid(trainingRawTimestampsNanos, trainingReferenceTimestampsNanos, points) &&
                pairedTimingEvidenceValid(holdoutRawTimestampsNanos, holdoutReferenceTimestampsNanos, validatedPoints) &&
                captureIntervalsDisjoint(trainingRawTimestampsNanos, holdoutRawTimestampsNanos) &&
                captureIntervalsDisjoint(trainingReferenceTimestampsNanos, holdoutReferenceTimestampsNanos) &&
                toCoreCalibrationProfile()?.let { EcgCoreResearchEngine.validateCalibration(it).allowed } == true

        private fun pairedTimingEvidenceValid(rawTimes: List<Long>, referenceTimes: List<Long>, expectedPoints: Int): Boolean =
            maximumPairSkewNanos in 1L..1_000_000_000L && rawTimes.size == expectedPoints &&
                referenceTimes.size == expectedPoints && rawTimes.all { it > 0L } &&
                referenceTimes.all { it > 0L } && rawTimes.zipWithNext().all { (a, b) -> b > a } &&
                referenceTimes.zipWithNext().all { (a, b) -> b > a } && rawTimes.indices.all { index ->
                    val rawTime = rawTimes[index]
                    val referenceTime = referenceTimes[index]
                    val skew = if (rawTime >= referenceTime) rawTime - referenceTime else referenceTime - rawTime
                    skew <= maximumPairSkewNanos
                }

        private fun captureIntervalsDisjoint(first: List<Long>, second: List<Long>): Boolean {
            val firstStart = first.minOrNull() ?: return false
            val firstEnd = first.maxOrNull() ?: return false
            val secondStart = second.minOrNull() ?: return false
            val secondEnd = second.maxOrNull() ?: return false
            return firstEnd < secondStart || secondEnd < firstStart
        }

        fun apply(raw: List<Float>): List<Float> {
            if (!isEligibleForResearchMv) return emptyList()
            if (raw.isEmpty() || raw.any { !it.isFinite() || it.toDouble() !in trainingRawMinimum..trainingRawMaximum }) {
                return emptyList()
            }
            return raw.map { (it.toDouble() * slopeMvPerRaw + offsetMv).toFloat() }
                .takeIf { values -> values.size == raw.size && values.all(Float::isFinite) }
                .orEmpty()
        }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): Profile? {
        if (!prefs.getBoolean(KEY_PRESENT, false)) return null
        if (listOf(KEY_PROFILE_ID, KEY_REFERENCE_INSTRUMENT, KEY_PROTOCOL_ID, KEY_SENSOR_PROFILE_FINGERPRINT, KEY_TRAINING_CAPTURE_ID,
                KEY_HOLDOUT_CAPTURE_ID, KEY_RAW_MINIMUM, KEY_RAW_MAXIMUM, KEY_TRAIN_RAW,
                KEY_TRAIN_REFERENCE, KEY_HOLDOUT_RAW, KEY_HOLDOUT_REFERENCE)
                .any { prefs.getString(it, null).isNullOrBlank() }) {
            clear()
            return null
        }
        val rawMinimum = prefs.getString(KEY_RAW_MINIMUM, null)?.toDoubleOrNull()
        val rawMaximum = prefs.getString(KEY_RAW_MAXIMUM, null)?.toDoubleOrNull()
        val slopeMvPerRaw = parseFiniteStoredScalar(prefs.getString(KEY_SLOPE, null))
        val offsetMv = parseFiniteStoredScalar(prefs.getString(KEY_OFFSET, null))
        val rmseMv = parseFiniteStoredScalar(prefs.getString(KEY_RMSE, null))
        val validationRmseMv = parseFiniteStoredScalar(prefs.getString(KEY_VALIDATION_RMSE, null))
        val maximumAllowedRmseMv = parseFiniteStoredScalar(prefs.getString(KEY_MAX_VALIDATION_RMSE, null))
        val trainingRaw = parseStoredPoints(KEY_TRAIN_RAW)
        val trainingReference = parseStoredPoints(KEY_TRAIN_REFERENCE)
        val holdoutRaw = parseStoredPoints(KEY_HOLDOUT_RAW)
        val holdoutReference = parseStoredPoints(KEY_HOLDOUT_REFERENCE)
        val trainingRawTimes = parseStoredLongs(KEY_TRAIN_RAW_TIMES)
        val trainingReferenceTimes = parseStoredLongs(KEY_TRAIN_REFERENCE_TIMES)
        val holdoutRawTimes = parseStoredLongs(KEY_HOLDOUT_RAW_TIMES)
        val holdoutReferenceTimes = parseStoredLongs(KEY_HOLDOUT_REFERENCE_TIMES)
        if (rawMinimum == null || rawMaximum == null || slopeMvPerRaw == null || offsetMv == null ||
            rmseMv == null || validationRmseMv == null || maximumAllowedRmseMv == null ||
            trainingRaw == null || trainingReference == null ||
            holdoutRaw == null || holdoutReference == null || trainingRawTimes == null ||
            trainingReferenceTimes == null || holdoutRawTimes == null || holdoutReferenceTimes == null) {
            clear()
            return null
        }
        val profile = Profile(
            slopeMvPerRaw = slopeMvPerRaw,
            offsetMv = offsetMv,
            rmseMv = rmseMv,
            validationRmseMv = validationRmseMv,
            maximumAllowedRmseMv = maximumAllowedRmseMv,
            points = prefs.getInt(KEY_POINTS, 0),
            validatedPoints = prefs.getInt(KEY_VALIDATED_POINTS, 0),
            independentlyValidated = prefs.getBoolean(KEY_INDEPENDENT, false),
            inputEncoding = prefs.getString(KEY_INPUT_ENCODING, "").orEmpty(),
            source = prefs.getString(KEY_SOURCE, "").orEmpty(),
            createdAtMillis = prefs.getLong(KEY_CREATED_AT, 0L),
            referenceInstrument = prefs.getString(KEY_REFERENCE_INSTRUMENT, "").orEmpty(),
            protocolId = prefs.getString(KEY_PROTOCOL_ID, "").orEmpty(),
            sensorProfileFingerprint = prefs.getString(KEY_SENSOR_PROFILE_FINGERPRINT, "").orEmpty(),
            trainingCaptureId = prefs.getString(KEY_TRAINING_CAPTURE_ID, "").orEmpty(),
            holdoutCaptureId = prefs.getString(KEY_HOLDOUT_CAPTURE_ID, "").orEmpty(),
            trainingRawMinimum = rawMinimum,
            trainingRawMaximum = rawMaximum,
            profileId = prefs.getString(KEY_PROFILE_ID, "").orEmpty(),
            trainingRaw = trainingRaw,
            trainingReferenceMv = trainingReference,
            holdoutRaw = holdoutRaw,
            holdoutReferenceMv = holdoutReference,
            maximumPairSkewNanos = prefs.getLong(KEY_MAX_PAIR_SKEW, 0L),
            trainingRawTimestampsNanos = trainingRawTimes,
            trainingReferenceTimestampsNanos = trainingReferenceTimes,
            holdoutRawTimestampsNanos = holdoutRawTimes,
            holdoutReferenceTimestampsNanos = holdoutReferenceTimes
        )
        if (!profile.isEligibleForResearchMv) {
            clear()
            return null
        }
        return profile
    }

    fun readActive(raw: android.content.SharedPreferences = RawSensorStore.open(context)): Profile? {
        val waveformEncoding = raw.getString("ecg_waveform_encoding", "").orEmpty()
        if (waveformEncoding != ECG_EXTERNAL_REFERENCE_MV_ENCODING) return null
        val profile = read()?.takeIf {
            EcgResearchProfileActivationPolicy.matches(
                it,
                waveformEncoding,
                raw.getString("ecg_calibration_profile_id", null),
                raw.getString("ecg_sensor_profile_fingerprint", null)
            )
        }
        if (profile == null) clear()
        return profile
    }

    fun save(
        fit: EcgResearchCalibration.Fit,
        validation: EcgResearchCalibration.LeaveOneOutValidation,
        source: String
    ): Boolean {
        // Leave-one-out validation uses the fitting data and must never unlock mV.
        return false
    }

    fun validateAndSaveIndependent(
        trainRaw: List<Double>,
        trainReferenceMv: List<Double>,
        holdoutRaw: List<Double>,
        holdoutReferenceMv: List<Double>,
        maximumAllowedRmseMv: Double,
        source: String,
        referenceInstrument: String,
        protocolId: String,
        sensorProfileFingerprint: String,
        trainingCaptureId: String,
        holdoutCaptureId: String,
        maximumPairSkewNanos: Long = 0L,
        trainingRawTimestampsNanos: List<Long> = emptyList(),
        trainingReferenceTimestampsNanos: List<Long> = emptyList(),
        holdoutRawTimestampsNanos: List<Long> = emptyList(),
        holdoutReferenceTimestampsNanos: List<Long> = emptyList()
    ): Boolean {
        val sensorEvidence = RawSensorStore.open(context)
        if (!EcgPayloadInterpretationPolicy.canInterpretAsWaveform(
                sensorEvidence.getString("ecg_waveform_encoding", null)
            ) || !EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(
                sensorEvidence.getString("ecg_research_waveform_encoding", null),
                sensorEvidence.getString("ecg_research_decode_status", null)
            ) || sensorEvidence.getString("ecg_sensor_profile_fingerprint", null) != sensorProfileFingerprint
        ) return false
        val fit = EcgResearchCalibration.fitRawToMillivolts(trainRaw, trainReferenceMv)
        val validation = EcgResearchCalibration.validateIndependentHoldout(
            trainRaw, trainReferenceMv, holdoutRaw, holdoutReferenceMv, maximumAllowedRmseMv
        )
        return saveValidatedFit(fit, validation, source, referenceInstrument, protocolId,
            trainingCaptureId, holdoutCaptureId, sensorProfileFingerprint, trainRaw.minOrNull() ?: return false,
            trainRaw.maxOrNull() ?: return false, trainRaw, trainReferenceMv, holdoutRaw, holdoutReferenceMv,
            maximumPairSkewNanos, trainingRawTimestampsNanos, trainingReferenceTimestampsNanos,
            holdoutRawTimestampsNanos, holdoutReferenceTimestampsNanos)
    }

    private fun saveValidatedFit(
        fit: EcgResearchCalibration.Fit,
        validation: EcgResearchCalibration.IndependentHoldoutValidation,
        source: String,
        referenceInstrument: String,
        protocolId: String,
        trainingCaptureId: String,
        holdoutCaptureId: String,
        sensorProfileFingerprint: String,
        trainingRawMinimum: Double,
        trainingRawMaximum: Double,
        trainingRaw: List<Double>,
        trainingReferenceMv: List<Double>,
        holdoutRaw: List<Double>,
        holdoutReferenceMv: List<Double>,
        maximumPairSkewNanos: Long,
        trainingRawTimestampsNanos: List<Long>,
        trainingReferenceTimestampsNanos: List<Long>,
        holdoutRawTimestampsNanos: List<Long>,
        holdoutReferenceTimestampsNanos: List<Long>
    ): Boolean {
        val normalizedSource = source.trim()
        val normalizedInstrument = referenceInstrument.trim()
        val normalizedProtocol = protocolId.trim()
        val normalizedTrainingId = trainingCaptureId.trim()
        val normalizedHoldoutId = holdoutCaptureId.trim()
        val profileId = UUID.randomUUID().toString()
        if (!fit.usable || fit.points < MIN_POINTS || !fit.slopeMvPerRaw.isFinite() ||
            fit.slopeMvPerRaw == 0.0 || !fit.offsetMv.isFinite() || !fit.rmseMv.isFinite() ||
            fit.rmseMv < 0.0 || !validation.usable ||
            validation.heldOutPoints < MIN_POINTS || validation.rmseMv?.isFinite() != true ||
            validation.rmseMv < 0.0 || validation.maximumAllowedRmseMv?.isFinite() != true ||
            validation.maximumAllowedRmseMv <= 0.0 || !validation.passed ||
            validation.rmseMv > validation.maximumAllowedRmseMv || normalizedSource.isBlank() ||
            normalizedInstrument.isBlank() || normalizedProtocol.isBlank() ||
            !sensorProfileFingerprint.matches(Regex("[0-9a-f]{64}")) ||
            normalizedTrainingId.isBlank() || normalizedHoldoutId.isBlank() ||
            normalizedTrainingId == normalizedHoldoutId || !trainingRawMinimum.isFinite() ||
            !trainingRawMaximum.isFinite() || trainingRawMaximum <= trainingRawMinimum ||
            !pairedTimingEvidenceValid(maximumPairSkewNanos, trainingRawTimestampsNanos, trainingReferenceTimestampsNanos, fit.points) ||
            !pairedTimingEvidenceValid(maximumPairSkewNanos, holdoutRawTimestampsNanos, holdoutReferenceTimestampsNanos, validation.heldOutPoints) ||
            !captureIntervalsDisjoint(trainingRawTimestampsNanos, holdoutRawTimestampsNanos) ||
            !captureIntervalsDisjoint(trainingReferenceTimestampsNanos, holdoutReferenceTimestampsNanos)) return false
        return prefs.edit()
            .putBoolean(KEY_PRESENT, true)
            .putString(KEY_PROFILE_ID, profileId)
            .putString(KEY_SLOPE, fit.slopeMvPerRaw.toString())
            .putString(KEY_OFFSET, fit.offsetMv.toString())
            .putString(KEY_RMSE, fit.rmseMv.toString())
            .putString(KEY_VALIDATION_RMSE, validation.rmseMv.toString())
            .putString(KEY_MAX_VALIDATION_RMSE, validation.maximumAllowedRmseMv.toString())
            .putInt(KEY_POINTS, fit.points)
            .putInt(KEY_VALIDATED_POINTS, validation.heldOutPoints)
            .putBoolean(KEY_INDEPENDENT, true)
            .putString(KEY_INPUT_ENCODING, EcgPayloadInterpretationPolicy.VERIFIED_WAVEFORM_ENCODING)
            .putString(KEY_SOURCE, normalizedSource)
            .putString(KEY_REFERENCE_INSTRUMENT, normalizedInstrument)
            .putString(KEY_PROTOCOL_ID, normalizedProtocol)
            .putString(KEY_SENSOR_PROFILE_FINGERPRINT, sensorProfileFingerprint)
            .putString(KEY_TRAINING_CAPTURE_ID, normalizedTrainingId)
            .putString(KEY_HOLDOUT_CAPTURE_ID, normalizedHoldoutId)
            .putString(KEY_RAW_MINIMUM, trainingRawMinimum.toString())
            .putString(KEY_RAW_MAXIMUM, trainingRawMaximum.toString())
            .putString(KEY_TRAIN_RAW, trainingRaw.joinToString(","))
            .putString(KEY_TRAIN_REFERENCE, trainingReferenceMv.joinToString(","))
            .putString(KEY_HOLDOUT_RAW, holdoutRaw.joinToString(","))
            .putString(KEY_HOLDOUT_REFERENCE, holdoutReferenceMv.joinToString(","))
            .putLong(KEY_MAX_PAIR_SKEW, maximumPairSkewNanos)
            .putString(KEY_TRAIN_RAW_TIMES, trainingRawTimestampsNanos.joinToString(","))
            .putString(KEY_TRAIN_REFERENCE_TIMES, trainingReferenceTimestampsNanos.joinToString(","))
            .putString(KEY_HOLDOUT_RAW_TIMES, holdoutRawTimestampsNanos.joinToString(","))
            .putString(KEY_HOLDOUT_REFERENCE_TIMES, holdoutReferenceTimestampsNanos.joinToString(","))
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .commit()
    }

    private fun parseStoredPoints(key: String): List<Double>? {
        val encoded = prefs.getString(key, null)?.takeIf { it.length <= 16_384 } ?: return null
        val tokens = encoded.split(',')
        if (tokens.size !in MIN_POINTS..EcgResearchCalibration.MAX_RESEARCH_CALIBRATION_POINTS ||
            tokens.any { it.isBlank() || it.length > 32 }) return null
        return tokens.map { it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }
    }

    private fun parseStoredLongs(key: String): List<Long>? {
        val encoded = prefs.getString(key, null)?.takeIf { it.length <= 16_384 } ?: return null
        val tokens = encoded.split(',')
        if (tokens.size !in MIN_POINTS..EcgResearchCalibration.MAX_RESEARCH_CALIBRATION_POINTS ||
            tokens.any { it.isBlank() || it.length > 20 }) return null
        return tokens.map { it.toLongOrNull()?.takeIf { value -> value > 0L } ?: return null }
    }

    private fun pairedTimingEvidenceValid(
        maximumPairSkewNanos: Long,
        rawTimes: List<Long>,
        referenceTimes: List<Long>,
        expectedPoints: Int
    ): Boolean = maximumPairSkewNanos in 1L..1_000_000_000L && rawTimes.size == expectedPoints &&
        referenceTimes.size == expectedPoints && rawTimes.all { it > 0L } && referenceTimes.all { it > 0L } &&
        rawTimes.zipWithNext().all { (a, b) -> b > a } && referenceTimes.zipWithNext().all { (a, b) -> b > a } &&
        rawTimes.indices.all { index ->
            val rawTime = rawTimes[index]
            val referenceTime = referenceTimes[index]
            val skew = if (rawTime >= referenceTime) rawTime - referenceTime else referenceTime - rawTime
            skew <= maximumPairSkewNanos
        }

    private fun captureIntervalsDisjoint(first: List<Long>, second: List<Long>): Boolean {
        val firstStart = first.minOrNull() ?: return false
        val firstEnd = first.maxOrNull() ?: return false
        val secondStart = second.minOrNull() ?: return false
        val secondEnd = second.maxOrNull() ?: return false
        return firstEnd < secondStart || secondEnd < firstStart
    }

    fun clear() {
        prefs.edit().clear().commit()
        val raw = RawSensorStore.open(context)
        if (raw.getString("ecg_waveform_encoding", "") == "ecg_external_reference_mv") {
            val researchWaveform = raw.getString("ecg_research_waveform", "").orEmpty()
            val researchEncoding = raw.getString("ecg_research_waveform_encoding", "").orEmpty()
            raw.edit().apply {
                if (researchWaveform.isNotBlank() && EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(
                        researchEncoding, raw.getString("ecg_research_decode_status", null)
                    )) {
                    putString("ecg_waveform", researchWaveform)
                    putString("ecg_waveform_encoding", researchEncoding)
                    putString("ecg_status", "research_signal_unscaled")
                } else {
                    remove("ecg_waveform")
                    remove("ecg_waveform_encoding")
                    remove("ecg_status")
                }
                remove("ecg_calibration")
                remove("ecg_calibration_profile_id")
            }.commit()
        }
    }

    companion object {
        internal const val ECG_EXTERNAL_REFERENCE_MV_ENCODING = "ecg_external_reference_mv"

        internal fun parseFiniteStoredScalar(value: String?): Double? =
            value?.toDoubleOrNull()?.takeIf { it.isFinite() }

        internal const val PREFS = "ecg_research_profile"
        private const val KEY_PRESENT = "present"
        private const val KEY_PROFILE_ID = "profile_id"
        private const val KEY_SLOPE = "slope_mv_per_raw"
        private const val KEY_OFFSET = "offset_mv"
        private const val KEY_RMSE = "rmse_mv"
        private const val KEY_VALIDATION_RMSE = "validation_rmse_mv"
        private const val KEY_MAX_VALIDATION_RMSE = "max_validation_rmse_mv"
        private const val KEY_POINTS = "points"
        private const val KEY_VALIDATED_POINTS = "validated_points"
        private const val KEY_INDEPENDENT = "independently_validated"
        private const val KEY_INPUT_ENCODING = "input_encoding"
        private const val KEY_SOURCE = "source"
        private const val KEY_REFERENCE_INSTRUMENT = "reference_instrument"
        private const val KEY_PROTOCOL_ID = "protocol_id"
        private const val KEY_SENSOR_PROFILE_FINGERPRINT = "sensor_profile_fingerprint"
        private const val KEY_TRAINING_CAPTURE_ID = "training_capture_id"
        private const val KEY_HOLDOUT_CAPTURE_ID = "holdout_capture_id"
        private const val KEY_RAW_MINIMUM = "training_raw_minimum"
        private const val KEY_RAW_MAXIMUM = "training_raw_maximum"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_TRAIN_RAW = "training_raw_points"
        private const val KEY_TRAIN_REFERENCE = "training_reference_mv_points"
        private const val KEY_HOLDOUT_RAW = "holdout_raw_points"
        private const val KEY_HOLDOUT_REFERENCE = "holdout_reference_mv_points"
        private const val KEY_MAX_PAIR_SKEW = "maximum_pair_skew_nanos"
        private const val KEY_TRAIN_RAW_TIMES = "training_raw_timestamps_nanos"
        private const val KEY_TRAIN_REFERENCE_TIMES = "training_reference_timestamps_nanos"
        private const val KEY_HOLDOUT_RAW_TIMES = "holdout_raw_timestamps_nanos"
        private const val KEY_HOLDOUT_REFERENCE_TIMES = "holdout_reference_timestamps_nanos"
        const val MIN_POINTS = EcgResearchCalibration.MIN_RESEARCH_PROFILE_POINTS
    }
}

internal object EcgResearchProfileActivationPolicy {
    fun matches(
        profile: EcgResearchProfileStore.Profile?,
        waveformEncoding: String,
        storedProfileId: String?,
        sensorFingerprint: String?
    ): Boolean = profile != null &&
        waveformEncoding == EcgResearchProfileStore.ECG_EXTERNAL_REFERENCE_MV_ENCODING &&
        storedProfileId == profile.profileId &&
        profile.isEligibleForResearchMv && profile.isBoundToSensorProfile(sensorFingerprint)
}
