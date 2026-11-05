package com.jaysato.xiaomihealthbridge

import kotlin.math.abs
import kotlin.math.sqrt

/** Candidate AFE reference checks. These validate transport plausibility, not device or medical calibration. */
object ManufacturerSelfTest {
    data class Check(val name: String, val passed: Boolean, val observed: String, val reference: String)
    data class Report(val title: String, val checks: List<Check>, val note: String) {
        val passedCount get() = checks.count(Check::passed)
    }

    fun evaluate(kind: String, waveform: List<Float>, sampleRateHz: Float, status: String): Report? {
        val finite = waveform.filter(Float::isFinite)
        val finiteRatio = if (waveform.isEmpty()) 0.0 else finite.size.toDouble() / waveform.size
        val span = if (finite.size >= 2) finite.max() - finite.min() else 0f
        return when (kind) {
            "ecg" -> Report(
                "ECG 候選 AFE 參考自檢",
                listOf(
                    Check("資料傳輸", finite.size >= 500, "${finite.size} 點", "至少 500 個有限原始值；不推定量測時長"),
                    Check("HAL 取樣時基", sampleRateHz.isFinite() && sampleRateHz > 0f,
                        if (sampleRateHz.isFinite() && sampleRateHz > 0f) "%.1f Hz".format(sampleRateHz) else "未回報",
                        "只採本次 HAL 正值；未知時用索引時間軸"),
                    Check("非固定輸出", span > 1e-4f, "span %.3f raw".format(span), "波形必須具有動態範圍"),
                    Check("有限值比例", finiteRatio >= 0.99, "%.1f%%".format(finiteRatio * 100), "≥99% 有限值"),
                    Check("HAL 狀態", status !in setOf("not_measured", "error"), status, "感測通道已成功回報")
                ),
                "MAX86178 僅為 HAL 名稱推測的候選 AFE；小米公開 Watch 2 Pro 規格未列 ECG。即使晶片具內建校準電壓，Xiaomi HAL 也未公開控制介面；此處只檢查原始輸出，不宣稱 mV 或裝置校準。"
            )
            "ppg", "spo2_raw", "mi_spo2" -> {
                val fill = finite.count { abs(it - 524287f) < 0.5f }
                val fillRatio = if (finite.isEmpty()) 1.0 else fill.toDouble() / finite.size
                Report(
                    "PPG 候選 AFE 參考自檢",
                    listOf(
                        Check("光學資料", finite.size >= 64, "${finite.size} 點", "足夠形成脈波片段"),
                        Check("填充值", fillRatio < 0.35, "%.1f%%".format(fillRatio * 100), "524287 填充值比例 <35%"),
                        Check("光學振幅", span >= 32f, "span %.1f raw".format(span), "App 實測 ALC 後最低動態 span 32 raw"),
                        Check("有限值比例", finiteRatio >= 0.99, "%.1f%%".format(finiteRatio * 100), "≥99% 有限值")
                    ),
                    "MAX86178 僅為 HAL 名稱推測的候選 AFE；公開晶片能力不能證明本手錶 LED 配置、光路或 OEM 校準曲線，因此不能由此自檢輸出 SpO₂%。"
                )
            }
            "bioz", "bioz_respiration" -> Report(
                "BioZ 候選 AFE 參考自檢",
                listOf(
                    Check("資料傳輸", finite.size >= 16, "${finite.size} 點", "通道持續回報"),
                    Check("非固定輸出", span > 1e-4f, "span %.3f raw".format(span), "接收鏈具有動態變化"),
                    Check("有限值比例", finiteRatio >= 0.99, "%.1f%%".format(finiteRatio * 100), "≥99% 有限值"),
                    Check("HAL 狀態", status !in setOf("not_measured", "error"), status, "感測通道已成功回報")
                ),
                "MAX86178 僅為 HAL 名稱推測的候選 AFE；公開晶片支援的刺激設定不能證明手錶實際頻率、電流、相位與電極幾何，因此不可換算 Ω、體脂或體水。"
            )
            "body_temperature_raw", "board_temperature", "thermometer", "temperature_alarm" -> {
                Report(
                    "溫度原始通道傳輸檢查",
                    listOf(
                        Check("有限原始資料", finite.isNotEmpty(), finite.lastOrNull()?.toString() ?: "—", "只驗證通道有有限值"),
                        Check("單位／轉換", false, "未驗證", "不推定 °C、解析度或晶片校準"),
                        Check("整機校準", false, "未驗證", "需裝置級文件與參考儀器配對驗證")
                    ),
                    "保留有限的裝置原始值供研究；單位、感測器型號、轉換係數及手錶整機校準未驗證，不輸出體溫或醫療判讀。"
                )
            }
            "pressure" -> {
                val pressure = finite.lastOrNull()?.toDouble()
                Report(
                    "ICP-101xx OTP 壓力補償檢核",
                    listOf(
                        Check("壓力資料", pressure != null, pressure?.let { "%.2f hPa".format(it) } ?: "—", "Android HAL 應輸出 hPa"),
                        Check("晶片量測範圍", pressure != null && pressure in 300.0..1100.0, pressure?.let { "%.2f hPa".format(it) } ?: "—", "300–1100 hPa"),
                        Check("有限值比例", finiteRatio >= 0.99, "%.1f%%".format(finiteRatio * 100), "≥99% 有限值")
                    ),
                    "TDK 指定 raw 壓力須使用每顆晶片 OTP0–OTP3 與溫度補償公式轉換。Android HAL 已輸出 hPa，代表 OEM 驅動應已套 OTP；App 無 raw/OTP 介面，僅能做範圍、漂移與外部氣壓計偏移驗證。"
                )
            }
            "accelerometer", "accelerometer_uncalibrated" -> {
                val magnitudes = vectorMagnitudes(finite)
                val median = magnitudes.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
                Report(
                    "BMI260 加速度參考自檢",
                    listOf(
                        Check("三軸資料", finite.size >= 3, "${finite.size} 值", "x/y/z 向量可讀"),
                        Check("靜止重力合理性", median != null && median in 7.5..12.5, median?.let { "|a| %.3f m/s²".format(it) } ?: "—", "靜止時接近 9.80665 m/s²"),
                        Check("有限值比例", finiteRatio >= 0.99, "%.1f%%".format(finiteRatio * 100), "≥99% 有限值")
                    ),
                    "Bosch BMI260 規格：16-bit、加速度零 g offset 約 ±20 mg、靈敏度誤差約 ±0.4%。Android 已轉 m/s²；完整六面校準仍需逐軸 ±g 靜置資料。"
                )
            }
            "gyroscope", "gyroscope_uncalibrated" -> {
                val magnitudes = vectorMagnitudes(finite)
                val median = magnitudes.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
                Report(
                    "BMI260 陀螺儀 CRT 參考自檢",
                    listOf(
                        Check("三軸資料", finite.size >= 3, "${finite.size} 值", "x/y/z 向量可讀"),
                        Check("靜止零速率", median != null && median <= 0.20, median?.let { "|ω| %.4f rad/s".format(it) } ?: "—", "靜止時應接近 0 rad/s"),
                        Check("有限值比例", finiteRatio >= 0.99, "%.1f%%".format(finiteRatio * 100), "≥99% 有限值")
                    ),
                    "BMI260 支援靜止 CRT 元件重新修調，官方列出 CRT 後靈敏度誤差約 ±0.4%、零速率 offset 約 ±0.5 dps。Android Sensor HAL 未暴露 CRT 命令／狀態暫存器，App 以靜止偏移統計驗證。"
                )
            }
            "magnetometer", "magnetometer_uncalibrated" -> {
                val magnitudes = vectorMagnitudes(finite)
                val median = magnitudes.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
                Report(
                    "AK09918C 磁力計參考自檢",
                    listOf(
                        Check("三軸資料", finite.size >= 3, "${finite.size} 值", "x/y/z μT 向量可讀"),
                        Check("地磁量級", median != null && median in 20.0..100.0, median?.let { "|B| %.2f μT".format(it) } ?: "—", "地表常見約 20–100 μT"),
                        Check("有限值比例", finiteRatio >= 0.99, "%.1f%%".format(finiteRatio * 100), "≥99% 有限值")
                    ),
                    "AKM 公開 AK09918C 靈敏度為 0.15 μT/LSB並具內建自檢。Android HAL 已輸出 μT，但未暴露自檢命令；指南針仍需使用者做八字硬鐵／軟鐵校正。"
                )
            }
            "light" -> {
                val lux = finite.lastOrNull()?.toDouble()
                Report(
                    "LTR311 環境光參考檢核",
                    listOf(
                        Check("照度資料", lux != null, lux?.let { "%.1f lux".format(it) } ?: "—", "Android HAL 應輸出 lux"),
                        Check("非負照度", lux != null && lux >= 0.0, lux?.let { "%.1f lux".format(it) } ?: "—", "lux 不可為負"),
                        Check("有限值比例", finiteRatio >= 0.99, "%.1f%%".format(finiteRatio * 100), "≥99% 有限值")
                    ),
                    "Lite-On LTR-311ALS 為數位環境光感測器。錶面玻璃與遮蔽會形成產品級比例偏差；需以外部照度計多點配對才能做 lux 校正。"
                )
            }
            else -> null
        }
    }

    private fun vectorMagnitudes(values: List<Float>): List<Double> = values.chunked(3).mapNotNull { row ->
        if (row.size != 3) null else sqrt(row.sumOf { it.toDouble() * it.toDouble() })
    }
}
