# Goal verification matrix（晶片頁＋他錶對齊＋文獻公式）

更新日期：2026-09-21 · App **3.10.3**

> 歷史矩陣：以下「通過」只代表 2026-09-21 當時的程式／裝置證據，不是 2026-10-03 的發布或實機驗收結論。最新狀態以 `docs/DEVICE_ACCEPTANCE_2026-10-03.md` 與 `docs/NEXT_WAVE_IMPROVEMENT_PLAN_2026-09-28.md` 為準；目前仍有掃描、簽章與校準未解項目。

## 需求與證據

| 需求 | 證據 | 狀態 |
|---|---|---|
| 晶片對照表各功能有手錶專屬頁 | ADB `am start -W`：WearChipHub + Ecg/Hrv/Compass/Backtrack/Workout/Gesture；Measure 模式 PPG/SPO2/BIOZ/RESPIRATION/OFF_BODY/HEART_RATE/TEMPERATURE/BOARD_TEMP/ACCEL/GYRO/STEPS/PRESSURE/LIGHT/LIFE_Q_OTT 皆 `Status: ok` | **通過** |
| 手機有對應檢視／遠端控制 | ChipFeatureHub／CompetitorParity／WatchFeatureMap／ClinicalLab／Main／RawSensorDetail／Vitals／Browse／結論／光學血壓／睡眠專頁；雙端 `versionName=3.10.3` | **通過** |
| Hub 列出 7 晶片 | 手機 UI 捲動：MAX86178、CT7117、BMI26x、AK0991x、ICP101xx、LTR311、LifeQ = YES | **通過** |
| 手錶量測可同步資料 | 手機 RawSensor pressure：`icp101xx Pressure Sensor` · 303 點 · 24.8 Hz · `has_data` · 品質 71/100（Data Layer → `raw_sensor_results`） | **通過** |
| 公式欄位路徑 | `WearRawSensorPublisher`／`WearDataProcessor`：`formula_pi_percent`／`formula_ratio_r`／`formula_rmssd_ms`／`formula_nrmssd`／`formula_altitude_m`／`formula_sbp_mmhg` 等；單元測試 green | **通過** |
| 光學血壓 API＋臨床建議 | `OpticalBloodPressureApi`＋雙端頁；ACC/AHA 分類；結論引擎 BP 槽位；`OpticalBloodPressureApiTest` | **通過** |
| 量測不限秒數 | WearMeasure／Control／Hrv／OpticalBp／SpO₂：有數據才結束；僅安全上限防 FGS 掛起 | **通過** |
| Apple／Samsung／Google UIUX | Vitals／Browse／Apps／Sleep／OBP／結論；對照表見 `COMPETITOR_UX_PARITY.md`；歷史／通知深度仍持續 | **進行中** |
| 臨床光學／感測公式導入 | `docs/CLINICAL_OPTICAL_FORMULAS.md`；雙端 `ClinicalSensorFormulas` + OBP | **通過** |

## 晶片 → 頁面

| 晶片 | 手錶 | 手機檢視 |
|---|---|---|
| MAX86178 | PPG／HR／SpO₂／ECG／BIOZ／呼吸／離腕／HRV／光學血壓 | Hub + Raw／ClinicalLab／OpticalBp |
| CT7117 | 體表／主板溫度 | Hub + Raw |
| BMI26x | 加速度／陀螺儀／計步／運動 | Hub + Raw／Workout |
| AK0991x | 指南針／返航 | Hub + 功能說明 |
| ICP101xx | 氣壓高度 | Hub + Raw（已同步實測） |
| LTR311 | 環境光／手勢夜間 | Hub + Raw／Gesture |
| LifeQ | 心率／OTT | Hub + Raw |

## 誠實邊界（仍成立，非缺口）

- SpO₂ **不**輸出未校準 %；BIOZ **不**輸出體脂 Ω；ECG **不**宣稱醫療 AF／mV 診斷。
- 光學血壓為研究估計，**非**袖帶核准裝置。
- 原廠 Xiaomi ECG 於此裝置常不可用 → 本 App 獨立 ECG 頁（3.9.12+）。
