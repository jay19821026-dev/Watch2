package com.jaysato.xiaomihealthbridge

import kotlin.math.sqrt
import kotlin.math.abs

object ExternalReferenceCalibration {
    data class ErrorReport(
        val mae: Double,
        val rmse: Double,
        val bias: Double,
        val lowerAgreementLimit: Double,
        val upperAgreementLimit: Double,
        val lowerBiasConfidenceLimit: Double,
        val upperBiasConfidenceLimit: Double,
        val sampleCount: Int
    )

    fun summarizeErrors(errors: List<Double>): ErrorReport? {
        if (errors.size < 2 || errors.any { !it.isFinite() }) return null
        val bias = errors.average()
        val deviation = sqrt(errors.sumOf { (it - bias) * (it - bias) } / (errors.size - 1))
        val rmse = sqrt(errors.sumOf { it * it } / errors.size)
        val margin = 1.96 * deviation / sqrt(errors.size.toDouble())
        val report = ErrorReport(errors.sumOf { abs(it) } / errors.size, rmse, bias,
            bias - 1.96 * deviation, bias + 1.96 * deviation,
            bias - margin, bias + margin, errors.size)
        return report.takeIf {
            listOf(it.mae, it.rmse, it.bias, it.lowerAgreementLimit, it.upperAgreementLimit,
                it.lowerBiasConfidenceLimit, it.upperBiasConfidenceLimit).all(Double::isFinite)
        }
    }
    data class Result(
        val slope: Double,
        val offset: Double,
        val fitRmse: Double,
        val validationRmse: Double?,
        val points: Int,
        val usable: Boolean,
        val note: String,
        val validationReport: ErrorReport? = null
    ) {
        fun apply(raw: Double): Double? = (raw * slope + offset).takeIf { usable && it.isFinite() }
    }

    fun fit(raw: List<Double>, reference: List<Double>): Result {
        if (raw.size != reference.size || raw.size < 5 ||
            raw.any { !it.isFinite() } || reference.any { !it.isFinite() }) {
            return invalid(minOf(raw.size, reference.size), "至少需要五組等長、有限且同步的 raw:reference 配對點")
        }
        if ((raw.max() - raw.min()) <= 1e-12 || (reference.max() - reference.min()) <= 1e-12) {
            return invalid(raw.size, "raw 或外部參考沒有足夠變化")
        }
        val model = regression(raw, reference) ?: return invalid(raw.size, "線性模型無法建立")
        val validationErrors = raw.indices.mapNotNull { heldOut ->
            val trainingRaw = raw.filterIndexed { index, _ -> index != heldOut }
            val trainingReference = reference.filterIndexed { index, _ -> index != heldOut }
            regression(trainingRaw, trainingReference)?.let { fit ->
                (fit.slope * raw[heldOut] + fit.offset - reference[heldOut]).takeIf(Double::isFinite)
            }
        }
        if (validationErrors.size != raw.size) {
            return Result(model.slope, model.offset, model.rmse, null, raw.size, false, "留一驗證未能涵蓋每個參考點")
        }
        val validationRmse = sqrt(validationErrors.map { it * it }.average())
        return Result(
            slope = model.slope,
            offset = model.offset,
            fitRmse = model.rmse,
            validationRmse = validationRmse,
            points = raw.size,
            usable = validationRmse.isFinite(),
            note = "外部參考線性擬合＋leave-one-out 驗證；不是 OEM／醫療校準",
            validationReport = summarizeErrors(validationErrors)
        )
    }

    private fun regression(raw: List<Double>, reference: List<Double>): Model? {
        val rawMean = raw.average()
        val referenceMean = reference.average()
        val denominator = raw.sumOf { (it - rawMean) * (it - rawMean) }
        if (denominator <= 1e-12) return null
        val slope = raw.indices.sumOf { index ->
            (raw[index] - rawMean) * (reference[index] - referenceMean)
        } / denominator
        val offset = referenceMean - slope * rawMean
        val rmse = sqrt(raw.indices.map { index ->
            val error = slope * raw[index] + offset - reference[index]
            error * error
        }.average())
        return Model(slope, offset, rmse).takeIf {
            it.slope.isFinite() && it.slope != 0.0 && it.offset.isFinite() && it.rmse.isFinite()
        }
    }

    private fun invalid(points: Int, note: String) = Result(0.0, 0.0, Double.NaN, null, points, false, note)
    private data class Model(val slope: Double, val offset: Double, val rmse: Double)
}
