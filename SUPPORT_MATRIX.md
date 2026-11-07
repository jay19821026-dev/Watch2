# 健康資料支援矩陣

| 資料 | 手錶公開來源 | 本 App 狀態 | Health Connect |
|---|---|---|---|
| 心率 | Health Services MeasureClient；韌體支援時另用 PassiveMonitoringClient；LifeQ／MAX86178 原始通道 | 已同步，逐筆唯一識別；掃描時同步寫入 WHS 心率；被動能力存在時支援背景與開機重註冊 | `HeartRateRecord` |
| 高心率 | 即時心率與使用者門檻 | 已提供本機震動警示 | 非獨立紀錄類型 |
| 低心率 | 即時心率與使用者門檻 | 已提供本機震動警示 | 非獨立紀錄類型 |
| 步數 | PassiveMonitoringClient，依韌體能力 | 已同步 | `StepsRecord` |
| 距離 | PassiveMonitoringClient，依韌體能力 | 已同步 | `DistanceRecord` |
| 卡路里 | PassiveMonitoringClient，依韌體能力 | 已同步 | `ActiveCaloriesBurnedRecord` |
| 運動 | ExerciseClient | 已支援手動開始／停止步行並同步工作階段 | `ExerciseSessionRecord` |
| GPS 路線 | ExerciseClient `LOCATION`，依運動類型與定位權限 | 步行運動期間最多記錄 1,000 點；有路線權限時隨工作階段寫入 | `ExerciseRoute` |
| 靜止心率 | 無可靠長時間靜止狀態與連續取樣來源 | 不推算；可在手機輸入外部可信量測 | `RestingHeartRateRecord`，手動輸入會標記為手動紀錄 |
| 心率變異 HRV | 公開 Health Services 不提供 RR 間期；可由 ECG／PPG 原始波形估計研究用指標 | 不由 BPM 偽算；細節頁可顯示估計 RR／SDNN／RMSSD（非臨床）；可輸入外部裝置 RMSSD | `HeartRateVariabilityRmssdRecord`，手動紀錄 |
| 呼吸速率 | 公開 Health Services 無資料型別 | 不由心率推算；可輸入外部可信量測 | `RespiratoryRateRecord`，手動紀錄 |
| 血氧 SpO₂ | `com.google.wear.sensor.mi.spo2`、`com.google.wear.sensor.spo2` 原始通道；已同步的 Health Connect 具名百分比紀錄 | 原始 HAL 欄位單位未經驗證，只保存原始事件與品質，不推斷百分比或寫入 Health Connect；手機可閱讀有來源的 `OxygenSaturationRecord`，並區分主動、被動、手動與未知量測方式 | `OxygenSaturationRecord` 只供具名百分比來源或使用者明確手動輸入；原始 HAL 事件不寫入 |
| 體表溫度 | `bodytemperature` 等原始通道；已同步的具名健康紀錄 | 原始通道的單位與裝置級換算未驗證，只顯示原始趨勢；不宣稱已校正，也不將腕部體表當核心體溫 | 已有可信來源的 `BodyTemperatureRecord` 可閱讀；原始 HAL 事件不寫入 |
| 離腕／佩戴 | `low_latency_offbody_detect`、MAX86178 ADT／HW-ADT | 掃描前佩戴門檻；未貼合則跳過生理校正 | 非 HC 紀錄 |
| PPG 原始波形 | `com.google.wear.sensor.ppg` | 專業網格波形、品質分數、SNR；可合法開啟原廠心率頁協助啟用晶片 | 無標準原始 PPG 紀錄 |
| ECG 原始波形 | `com.google.wear.sensor.ecg` | 原始多通道波形與品質；缺少導聯／臨床校準，不做房顫診斷 | Health Connect 無一般健康用原始 ECG 紀錄 |
| BIOZ 原始阻抗 | `com.google.wear.sensor.bioz` | 原始波形與品質；無原廠換算公式，不輸出體脂／水分 | 無標準 BIOZ 紀錄 |
| 氣壓／爬升／樓層 | `android.sensor.pressure` | 運動或掃描期間計算相對高度；無事件不顯示 0 | `ElevationGainedRecord`／`FloorsClimbedRecord` |
| 環境光／手勢／指南針 | light、accel／gyro／rotation、磁／方位 | 手錶前景功能；手機顯示功能快照與狀態入口 | 非 HC 健康紀錄 |
| 睡眠與階段 | 沒有公開完整睡眠階段資料 | 手機可輸入截至目前的睡眠總時數；不建立或推算階段 | `SleepSessionRecord`，空階段清單並標記為手動紀錄 |
| 體重 | 手錶無體重感測器標準來源 | 手機可驗證範圍後手動輸入 | `WeightRecord`，標記為手動紀錄 |
| 血壓 | Watch 2 Pro 官方清單無血壓硬體，Health Services 也無血壓型別 | 手機只接受真正外部血壓計讀值，不以心率推算 | `BloodPressureRecord`，標記為手動紀錄 |
| 壓力、PAI、身體能量、恢復、訓練負荷、Xiaomi 評分 | Xiaomi 專有且未公開為 Health Services 標準資料 | 不破解 HAL／SELinux；本機可用公開資料做訓練負荷／恢復等規則估算並標示不足 | 不寫入不相符欄位 |

「依韌體能力」表示 App 會在 Xiaomi Watch 2 Pro 上即時查詢 Health Services capabilities；只有裝置明確回報支援才會註冊與同步。生醫通道採依序獨占，避免 MAX86178 互搶導致無事件。

原廠健康頁面可由使用者在手錶前景明確點擊開啟；這只代表啟動請求，不代表原廠服務已授權、ECG 已啟用或感測器數值已完成校準。詳見 [4.0 原廠功能稽核](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md) 與 [晶片校準查證矩陣](docs/CHIP_CALIBRATION_STATUS.md)。
