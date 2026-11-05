package com.jaysato.xiaomihealthbridge

/**
 * Phone-side mirror of the watch sensor-endpoint → feature map.
 * Candidate part families inferred from HAL names are not a verified device BOM.
 * Each feature maps to a raw-sensor kind and/or remote control path.
 */
object ChipFeatureCatalog {
    data class Feature(
        val title: String,
        val kind: String?,
        val controlPath: String?,
        val otherWatchUse: String,
        val phoneView: String
    )

    data class Chip(
        val part: String,
        val vendor: String,
        val role: String,
        val features: List<Feature>
    )

    val chips = listOf(
        Chip(
            "MAX86178 channel family (candidate)", "HAL naming evidence; device BOM unverified", "光學／ECG／BioZ logical endpoints",
            listOf(
                Feature("PPG 光學波形", "ppg", "/control/ppg-now", "光學錶連續心率、脈波", "臨床實驗室／原始詳情"),
                Feature("光學／LifeQ 心率", "heart_rate_raw", null, "24h HR", "健康中心心率＋原始詳情"),
                Feature("MAX86178 光學原始通道", "max_hr", null, "光學原始封包（非 bpm）", "原始詳情"),
                Feature("SpO₂ 原始", "spo2_raw", "/control/oxygen-now", "血氧頁", "原始詳情（不寫 %）"),
                Feature("ECG 單導聯", "ecg", "/control/ecg-now", "ECG／心律篩檢（本 App 獨立頁）", "臨床實驗室＋心律研究欄位"),
                Feature("體成分／BioZ", "bioz", "/control/bioz-now", "Samsung BIA／小米體成分", "原始阻抗詳情"),
                Feature("光學離腕 ADT", "adt", "/control/off-body-now", "光學佩戴狀態", "佩戴狀態詳情"),
                Feature("硬體離腕 ADT", "hardware_adt", null, "硬體佩戴狀態", "佩戴狀態詳情"),
            )
        ),
        Chip(
            "未確認溫度 HAL", "未核實", "裝置提供溫度通道；感測器型號、單位及轉換未核實",
            listOf(
                Feature("溫度原始值", "body_temperature_raw", "/control/temperature-now", "原始值趨勢", "單位未驗證／原始詳情"),
                Feature("主板溫度", "board_temperature", null, "工程熱監控", "原始詳情"),
            )
        ),
        Chip(
            "BOSCH bmi26x family (candidate)", "HAL vendor string; exact SKU unverified", "加速度＋陀螺儀端點",
            listOf(
                Feature("加速度／活動", "accelerometer", null, "步數、運動姿態", "原始詳情"),
                Feature("陀螺儀", "gyroscope", null, "姿態／手勢", "原始詳情"),
                Feature("步行運動", null, null, "運動工作階段", "運動分析頁"),
            )
        ),
        Chip(
            "AKM ak0991x family (candidate)", "HAL vendor/name evidence; exact SKU unverified", "磁力計端點",
            listOf(
                Feature("磁場方向", "magnetometer", null, "戶外導航輸入", "原始詳情"),
                Feature("返航導航", null, null, "回程輔助", "手錶端啟動；手機顯示狀態"),
            )
        ),
        Chip(
            "TDK icp101xx family (candidate)", "HAL vendor/name evidence; exact SKU unverified", "氣壓計端點",
            listOf(
                Feature("氣壓／壓力高度估算", "pressure", null, "氣壓趨勢；高度公式值不代表實際海拔、樓層或爬升", "原始詳情＋標準大氣公式估算"),
            )
        ),
        Chip(
            "Lite-On ltr311 family (candidate)", "HAL vendor/name evidence; exact SKU unverified", "環境光端點",
            listOf(
                Feature("環境光", "light", null, "自動亮度", "原始詳情＋feature light"),
            )
        ),
        Chip(
            "MI sensor endpoints", "Xiaomi／MI", "計步、佩戴與手勢演算法端點",
            listOf(
                Feature("計步器", "step_counter", null, "重開機後累積計數（非當日總量）", "原始計數詳情；日步數由 Health Connect 彙總"),
                Feature("步伐偵測", "step_detector", null, "步伐事件", "原始詳情"),
                Feature("離腕偵測", "off_body", "/control/off-body-now", "佩戴狀態", "佩戴狀態詳情"),
                Feature("抬腕手勢", "wrist_tilt", null, "抬腕點亮", "功能說明"),
                Feature("小米 SpO₂ 演算法", "mi_spo2", null, "廠商演算法輸出", "原始詳情（不寫 %）"),
            )
        ),
        Chip(
            "LifeQ", "LifeQ", "光學生理演算法",
            listOf(
                Feature("LifeQ 心率", "heart_rate_raw", null, "連續 HR 算法", "心率詳情"),
                Feature("LifeQ OTT 原始", "lifeq_ott", null, "組織光學通道", "原始詳情"),
                Feature("HRV／壓力", "hrv_research", null, "Apple／Galaxy／Pixel HRV", "公式欄位詳情"),
            )
        ),
    )
}
