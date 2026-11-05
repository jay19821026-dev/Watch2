package com.jaysato.xiaomihealthbridge

/**
 * Android-exposed sensor endpoint and app feature map.
 * HAL names/vendor strings are evidence for candidate families, not a verified physical BOM.
 *
 * Pages never invent medical algorithms; they open honest research / engineering UIs.
 */
object WearChipCatalog {

    data class Feature(
        val title: String,
        val otherWatchUse: String,
        val page: Page
    )

    sealed class Page {
        data class Measure(val mode: WearMeasurePageActivity.Mode) : Page()
        data object Ecg : Page()
        data object Hrv : Page()
        data object Compass : Page()
        data object Backtrack : Page()
        data object Workout : Page()
        data object Gesture : Page()
        data object Calibration : Page()
    }

    data class Chip(
        val part: String,
        val vendor: String,
        val role: String,
        val halEvidence: String,
        val features: List<Feature>
    )

    val chips: List<Chip> = listOf(
        Chip(
            part = "MAX86178 channel family (candidate)",
            vendor = "HAL naming evidence; device BOM unverified",
            role = "光學／ECG／BioZ logical endpoints",
            halEvidence = "MAX86178-like channel names; watch configuration and physical AFE unverified",
            features = listOf(
                Feature("PPG 光學波形", "ADI Study Watch／多數光學錶：連續心率、脈波", Page.Measure(WearMeasurePageActivity.Mode.PPG)),
                Feature("光學心率", "消費級手錶 24h HR；本頁走公開 max_hr／HR 通道", Page.Measure(WearMeasurePageActivity.Mode.HEART_RATE)),
                Feature("SpO₂ 原始", "血氧頁（Galaxy／小米等）；本頁不寫未驗證 %", Page.Measure(WearMeasurePageActivity.Mode.SPO2)),
                Feature("ECG 單導聯", "Apple／Galaxy／小米 ECG；含研究級心律篩檢", Page.Ecg),
                Feature("BioZ／體成分原始", "Samsung BIA、小米體成分；本頁僅原始阻抗", Page.Measure(WearMeasurePageActivity.Mode.BIOZ)),
                Feature("呼吸研究（BioZ）", "ADI 資料表：BioZ 可估呼吸；僅顯示原始通道", Page.Measure(WearMeasurePageActivity.Mode.RESPIRATION)),
                Feature("離腕／佩戴偵測", "多數光學錶自動暫停量測", Page.Measure(WearMeasurePageActivity.Mode.OFF_BODY)),
                Feature("HRV／壓力研究", "Apple Vitals／Galaxy／Pixel 壓力·HRV", Page.Hrv),
            )
        ),
        Chip(
            part = "型號未確認",
            vendor = "未確認",
            role = "溫度原始通道",
            halEvidence = "Wear Sensor HAL 溫度通道；晶片映射、單位與轉換未驗證",
            features = listOf(
                Feature("溫度原始值", "感測器型號及單位未驗證；研究用途", Page.Measure(WearMeasurePageActivity.Mode.TEMPERATURE)),
                Feature("主板溫度原始值", "工程原始通道，單位未驗證", Page.Measure(WearMeasurePageActivity.Mode.BOARD_TEMP)),
            )
        ),
        Chip(
            part = "BOSCH bmi26x family (candidate)",
            vendor = "HAL vendor string; exact SKU unverified",
            role = "加速度＋陀螺儀端點",
            halEvidence = "bmi26x Accelerometer／Gyroscope",
            features = listOf(
                Feature("加速度／活動", "活動特徵候選；跌倒判斷尚未驗證", Page.Measure(WearMeasurePageActivity.Mode.ACCEL)),
                Feature("陀螺儀", "運動姿態、手勢輔助", Page.Measure(WearMeasurePageActivity.Mode.GYRO)),
            )
        ),
        Chip(
            part = "AKM ak0991x family (candidate)",
            vendor = "HAL vendor/name evidence; exact SKU unverified",
            role = "磁力計",
            halEvidence = "ak0991x Magnetometer",
            features = listOf(
                Feature("指南針", "地圖／戶外導航", Page.Compass),
                Feature("返航導航", "結合方位的回程輔助", Page.Backtrack),
                Feature("磁力計校準", "地磁量級與八字校正檢核", Page.Measure(WearMeasurePageActivity.Mode.MAGNET)),
            )
        ),
        Chip(
            part = "TDK icp101xx family (candidate)",
            vendor = "HAL vendor/name evidence; exact SKU unverified",
            role = "氣壓計",
            halEvidence = "icp101xx Pressure",
            features = listOf(
                Feature("氣壓／標準高度估算", "高度公式值不代表實際海拔、樓層或運動爬升", Page.Measure(WearMeasurePageActivity.Mode.PRESSURE)),
            )
        ),
        Chip(
            part = "Lite-On ltr311 family (candidate)",
            vendor = "HAL vendor/name evidence; exact SKU unverified",
            role = "環境光",
            halEvidence = "ltr311 Ambient Light",
            features = listOf(
                Feature("環境光／夜間亮度", "自動亮度、抬腕省電", Page.Measure(WearMeasurePageActivity.Mode.LIGHT)),
                Feature("手勢／夜間省電設定", "抬腕點亮策略", Page.Gesture),
            )
        ),
        Chip(
            part = "計步／運動邏輯端點",
            vendor = "Android／OEM；依 runtime sensor metadata 判定",
            role = "非實體晶片清單",
            halEvidence = "android.sensor.step_counter / step_detector；亦可能有 pedometer_wrist OEM endpoint",
            features = listOf(
                Feature("計步／步伐", "來源與區間需核對；避免與 Health Services／Health Connect 重複加總", Page.Measure(WearMeasurePageActivity.Mode.STEPS)),
                Feature("運動工作階段", "由運動 API 與記錄提供；不歸屬單一 IMU 晶片", Page.Workout),
            )
        ),
        Chip(
            part = "LifeQ LEL",
            vendor = "LifeQ",
            role = "光學生理演算法通道",
            halEvidence = "lifeq_lel_heart_rate／lifeq_del_ott",
            features = listOf(
                Feature("LifeQ 心率通道", "原廠連續 HR 演算法來源之一", Page.Measure(WearMeasurePageActivity.Mode.HEART_RATE)),
                Feature("LifeQ OTT 原始", "組織光學通道（研究）", Page.Measure(WearMeasurePageActivity.Mode.LIFE_Q_OTT)),
            )
        ),
    )

    val calibrationFeature = Feature(
        "手錶校正中心",
        "體表溫度／氣壓／環境光外部參考校正；IMU／磁力計導引",
        Page.Calibration
    )
}
