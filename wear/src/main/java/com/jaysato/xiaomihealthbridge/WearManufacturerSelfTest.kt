package com.jaysato.xiaomihealthbridge

import kotlin.math.abs
import kotlin.math.sqrt

object WearManufacturerSelfTest {
    data class Result(val passed: Int, val total: Int, val lines: List<String>, val boundary: String) {
        fun compact(): String = "晶片自檢 $passed/$total\n${lines.joinToString("\n")}\n$boundary"
    }

    fun evaluate(snapshot: RawVitalsProbe.Snapshot): Result? {
        val wave = snapshot.waveform.filter(Float::isFinite)
        val span = if (wave.size >= 2) wave.max() - wave.min() else 0f
        val checks = mutableListOf<Pair<Boolean, String>>()
        val boundary = when (snapshot.kind) {
            RawVitalsProbe.Kind.ECG -> {
                checks += (wave.size >= 500) to "ECG ${wave.size} 點"
                val hasReportedSampleRate = snapshot.sampleRateHz.isFinite() && snapshot.sampleRateHz > 0.0
                checks += hasReportedSampleRate to
                    "HAL 取樣時基 ${if (hasReportedSampleRate) "${"%.1f".format(snapshot.sampleRateHz)} Hz" else "未知（索引時間軸）"}"
                checks += (span > 1e-4f) to "動態 span ${"%.3f".format(span)} raw"
                checks += (snapshot.halSampleJitterSeconds == null || snapshot.halSampleJitterSeconds >= 0.0) to
                    "HAL jitter ${snapshot.halSampleJitterSeconds?.let { "%.3f ms".format(it * 1000) } ?: "未提供"}"
                "MAX86178 僅為 HAL 名稱推測的候選 AFE；小米公開 Watch 2 Pro 規格未列 ECG。內建校準電壓與導聯檢測未由 Xiaomi HAL 暴露；不宣稱 mV／IEC 校準。"
            }
            RawVitalsProbe.Kind.PPG, RawVitalsProbe.Kind.SPO2 -> {
                val fillRatio = if (snapshot.waveform.isEmpty()) 1.0 else
                    snapshot.waveform.count { !it.isFinite() || abs(it - 524287f) < .5f }.toDouble() / snapshot.waveform.size
                checks += (wave.size >= 64) to "光學資料 ${wave.size} 點"
                checks += (fillRatio < .35) to "填充值 ${"%.0f".format(fillRatio * 100)}%"
                checks += (span >= 32f) to "動態 span ${"%.1f".format(span)} raw"
                "MAX86178 LED/光路/OEM SpO₂ 曲線未公開；只驗證 AFE 輸出。"
            }
            RawVitalsProbe.Kind.BIOZ, RawVitalsProbe.Kind.BIOZ_RESPIRATION -> {
                checks += (wave.size >= 16) to "BioZ ${wave.size} 點"
                checks += (span > 1e-4f) to "動態 span ${"%.3f".format(span)} raw"
                "MAX86178 刺激頻率、電流、相位與電極幾何未由 HAL 公開。"
            }
            RawVitalsProbe.Kind.BODY_TEMPERATURE, RawVitalsProbe.Kind.BOARD_TEMPERATURE,
            RawVitalsProbe.Kind.THERMOMETER, RawVitalsProbe.Kind.TEMPERATURE_ALARM -> {
                val value = snapshot.average ?: snapshot.latest.firstOrNull()
                checks += (value != null && value.isFinite()) to "裝置原始值 ${value?.takeIf { it.isFinite() }?.let { "%.3f".format(it) } ?: "—"} · 單位未驗證"
                "僅檢查是否收到有限原始值；不推定晶片型號、單位或整機校準。"
            }
            RawVitalsProbe.Kind.PRESSURE -> {
                val value = snapshot.average ?: snapshot.latest.firstOrNull()
                checks += (value != null) to "氣壓 ${value?.let { "%.2f hPa".format(it) } ?: "—"}"
                checks += (value != null && value in 300.0..1100.0) to "ICP101xx 量測範圍"
                "ICP101xx OTP 補償由 OEM 驅動處理；App 僅驗證 hPa 與漂移。"
            }
            RawVitalsProbe.Kind.ACCELEROMETER, RawVitalsProbe.Kind.ACCELEROMETER_UNCALIBRATED -> {
                val magnitude = magnitude(snapshot.latest)
                checks += (snapshot.latest.size >= 3) to "三軸向量"
                checks += (magnitude != null && magnitude in 7.5..12.5) to "|a| ${magnitude?.let { "%.3f m/s²".format(it) } ?: "—"}"
                "BMI260 靜止時以 1g 檢核；完整校準需六面靜置。"
            }
            RawVitalsProbe.Kind.GYROSCOPE, RawVitalsProbe.Kind.GYROSCOPE_UNCALIBRATED -> {
                val magnitude = magnitude(snapshot.latest)
                checks += (snapshot.latest.size >= 3) to "三軸向量"
                checks += (magnitude != null && magnitude <= .20) to "|ω| ${magnitude?.let { "%.4f rad/s".format(it) } ?: "—"}"
                "BMI260 CRT 命令未由 Android HAL 暴露；以靜止零速率驗證。"
            }
            RawVitalsProbe.Kind.MAGNETOMETER, RawVitalsProbe.Kind.MAGNETOMETER_UNCALIBRATED -> {
                val magnitude = magnitude(snapshot.latest)
                checks += (snapshot.latest.size >= 3) to "三軸向量"
                checks += (magnitude != null && magnitude in 20.0..100.0) to "|B| ${magnitude?.let { "%.2f μT".format(it) } ?: "—"}"
                "AK09918C 0.15 μT/LSB；仍需八字校正硬鐵／軟鐵偏差。"
            }
            RawVitalsProbe.Kind.LIGHT -> {
                val value = snapshot.latest.firstOrNull()
                checks += (value != null && value >= 0.0) to "照度 ${value?.let { "%.1f lux".format(it) } ?: "—"}"
                "LTR311 錶面遮蔽誤差需外部照度計多點校正。"
            }
            else -> return null
        }
        checks += (snapshot.samples > 0) to "HAL ${snapshot.status}"
        return Result(checks.count { it.first }, checks.size, checks.map { (ok, text) -> "${if (ok) "✓" else "!"} $text" }, boundary)
    }

    private fun magnitude(values: List<Double>): Double? = values.take(3).takeIf { it.size == 3 }
        ?.let { sqrt(it.sumOf { value -> value * value }) }
}
