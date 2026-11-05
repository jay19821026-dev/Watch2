package com.jaysato.xiaomihealthbridge

object WatchSensorCatalog {
    data class Item(val kind: String, val label: String, val group: String)

    val items = listOf(
        // MAX86178
        Item("ppg", "PPG 光學波形", "MAX86178"),
        Item("max_hr", "MAX86178 光學原始通道", "MAX86178"),
        Item("spo2_raw", "SpO₂ 原始通道", "MAX86178"),
        Item("ecg", "ECG 原始波形", "MAX86178"),
        Item("bioz", "BIOZ／體成分原始", "MAX86178"),
        Item("bioz_respiration", "呼吸研究（共用 BioZ 端點）", "MAX86178"),
        Item("adt", "MAX86178 離腕 ADT", "MAX86178"),
        Item("hardware_adt", "MAX86178 硬體離腕", "MAX86178"),
        Item("off_body", "離腕偵測", "Xiaomi／MI"),
        Item("mi_spo2", "Xiaomi SpO₂ 演算法", "Xiaomi／MI_SLATE"),
        Item("step_counter", "重開機後累積步數", "Xiaomi／MI"),
        Item("step_detector", "步伐偵測", "Xiaomi／MI"),
        Item("wrist_tilt", "抬腕手勢", "Xiaomi／MI"),
        Item("body_temperature_raw", "溫度原始值", "型號未確認"),
        Item("board_temperature", "主板溫度原始值", "型號未確認"),
        Item("thermometer", "溫度原始通道", "型號未確認"),
        Item("temperature_alarm", "溫度狀態通道", "型號未確認"),
        // BMI26x
        Item("accelerometer", "加速度計", "BMI26x"),
        Item("accelerometer_uncalibrated", "未校正加速度", "BMI26x"),
        Item("gyroscope", "陀螺儀", "BMI26x"),
        Item("gyroscope_uncalibrated", "未校正陀螺儀", "BMI26x"),
        // AK0991x
        Item("magnetometer", "磁力計", "AK0991x"),
        Item("magnetometer_uncalibrated", "未校正磁力計", "AK0991x"),
        Item("orientation", "方位角", "融合"),
        Item("rotation_vector", "旋轉向量", "融合"),
        // ICP101xx
        Item("pressure", "氣壓／標準高度估算", "ICP101xx"),
        // LTR311
        Item("light", "環境光", "LTR311"),
        // LifeQ
        Item("whs_heart_rate", "Health Services 心率", "LifeQ"),
        Item("heart_rate_raw", "心率感測通道", "LifeQ"),
        Item("lifeq_ott", "LifeQ OTT 通道", "LifeQ"),
        Item("hrv_research", "HRV／壓力研究", "LifeQ"),
        // fused / other
        Item("game_rotation", "遊戲旋轉向量", "融合"),
        Item("gravity", "重力向量", "融合"),
        Item("linear_acceleration", "線性加速度", "融合"),
        Item("geomagnetic_rotation", "地磁旋轉向量", "融合"),
        Item("significant_motion", "顯著動作偵測", "融合"),
        Item("stationary", "靜止狀態偵測", "融合"),
        Item("motion", "移動狀態偵測", "融合")
    )
}
