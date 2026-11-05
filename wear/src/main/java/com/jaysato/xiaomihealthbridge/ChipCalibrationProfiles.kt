package com.jaysato.xiaomihealthbridge

/**
 * Heuristic checks for chip-reported raw data; these are not device calibration.
 *
 * Direct MAX86178 register writes are not exposed by Xiaomi's public Wear HAL;
 * OEM fitness pages own LED current / AFE mode. Chip datasheets do not prove
 * how Xiaomi configured or calibrated the complete watch.
 *
 * Sources:
 *  - Analog Devices MAX86178 datasheet / MAXREFDES106 HSP 4.0 User Guide
 *    (LED Driver PA, photodiode bias, ALC ambient cancellation, ECG/BioZ cal voltages)
 */
object ChipCalibrationProfiles {
    /** Sentinel observed in raw stream; its meaning is not OEM-confirmed. */
    const val OPTICAL_FILL = 524287f

    /** Raw morphology heuristic only; channel mapping and ALC state are not verified. */
    const val MIN_VALID_OPTICAL_SPAN = 24f

    const val MIN_VALID_OPTICAL_SAMPLES = 64

    data class OpticalCheck(
        val valid: Boolean,
        val fillRatio: Double,
        val span: Float,
        val advice: String
    )

    fun assessOpticalWaveform(values: List<Float>): OpticalCheck {
        if (values.isEmpty()) {
            return OpticalCheck(false, 1.0, 0f, "未收到光學樣本；感測器啟用狀態與原因尚未確認")
        }
        val usable = values.filter { it.isFinite() && it != OPTICAL_FILL }
        val fill = (values.size - usable.size).toDouble() / values.size
        val finite = usable
        val span = if (finite.size >= 2) finite.max() - finite.min() else 0f
        val valid = fill < 0.65 && finite.size >= MIN_VALID_OPTICAL_SAMPLES && span >= MIN_VALID_OPTICAL_SPAN
        val advice = when {
            valid -> "原始值形態啟發式檢查通過；不代表有效 PPG、通道映射、取樣率或生理量測成立"
            fill >= 0.65 -> "原始封包含大量已知 HAL 填充值；可能原因未確認，僅保留資料供檢查"
            else -> "原始值振幅未達啟發式門檻；通道、增益、貼合及感測器模式尚未確認"
        }
        return OpticalCheck(valid, fill, span, advice)
    }

    fun temperatureCalibrationNote(): String =
        "已收到溫度感測器原始讀值；手錶通道單位、轉換係數與整機校準未驗證，不套用偏移、不寫入 Health Connect 體溫紀錄。"
}
