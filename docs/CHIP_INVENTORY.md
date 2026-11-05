# 實機晶片清單與他錶功能對照

更新日期：2026-09-27  
證據：硬體名稱取自 `adb shell dumpsys sensorservice` 實機盤點；目前可用性另依手機匯出的 2026-09-27 07:16:28 掃描（非新版安裝後驗收）記錄。舊 ADB port 是歷史證據，不代表目前連線位址。

## HAL／清單判讀界線（2026-09-29）

- Android Sensors HAL 是 OEM 感測器實作與 Android framework 之間的介面；一般 App 使用 `SensorManager` 公開的 sensor/event API，不等於直接讀晶片暫存器、私有韌體或 OEM 生理演算法。
- `dumpsys sensorservice`／`SensorManager.getSensorList(TYPE_ALL)` 回報的是框架公開的 sensor endpoint，不是實體晶片計數。相同實體元件可對應多個端點（融合型、wake-up/non-wake-up、校正/未校正）；端點存在也不代表本 App 有權限、註冊成功、收到有效事件或知道其數值映射。
- App 畫面資料一律稱為「Android Sensor API 原始事件／值」：是本 App 收到並保存的 `SensorEvent`，非直接 HAL 封包。若型別映射、採樣時基或單位未知，重複數值（例如畫面中的 `48438`）僅能當原始碼值；無法據此判定為有效 ECG、飽和或故障，需搭配 sensor metadata、事件時間戳、精度、註冊結果與連續變化檢驗。
- 本表晶片／通道名稱是依實機公開名稱與歷史盤點建立的來源線索；除非有 OEM 文件或可重現的裝置證據，不應將名稱關聯視為 die-to-channel 接線、量測精度或醫療校準的證明。
- 下一次逐端點盤點須分別記錄：公開可見、權限狀態、註冊回傳/例外、事件數與時間戳、值是否變化、退出/逾時原因、同步與保存結果。不得把任何一層的成功推論成下一層成功。官方背景：[AOSP Sensors HAL](https://source.android.com/docs/core/interaction/sensors/sensors-hal2)、[感測器互動規範](https://source.android.com/docs/core/interaction/sensors/interaction)、[Android Sensor API](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)。

| 晶片 | 廠商 | HAL 名稱 | 他錶／參考常見功能 | 本 App 專屬頁 |
|---|---|---|---|---|
| MAX86178 | Analog Devices | `max86178_ppg/ecg/bioz/spo2/hr/offbody` | PPG、HR、SpO₂、ECG、BIA 體成分、BioZ 呼吸、離腕 | PPG／心率／SpO₂／ECG／BIOZ／呼吸研究／離腕 |
| CT7117 | SensyLink | `ct7117 body/board temperature` | 皮膚溫趨勢、板溫監控 | 體表溫度／主板溫度 |
| BMI26x | Bosch | `bmi26x Accelerometer/Gyroscope` | 步數、運動、姿態 | 加速度／陀螺儀／計步／運動頁 |
| AK0991x | AKM | `ak0991x Magnetometer` | 指南針、導航 | 指南針／返航 |
| ICP101xx | TDK-Invensense | `icp101xx Pressure` | 氣壓高度、樓層 | 氣壓／相對高度 |
| LTR311 | Lite-On | `ltr311 Ambient Light` | 自動亮度、抬腕 | 環境光／手勢夜間 |
| LifeQ LEL | LifeQ | `lifeq_lel_heart_rate`、`lifeq_del_ott` | 連續 HR 算法 | 心率頁／OTT 原始 |

邊界：不把 ADI 評估板係數或他廠臨床結果寫成本機校準；SpO₂／BIOZ／ECG 無裝置級證明前不輸出臨床單位或診斷。

## 歷史功能頁索引（3.9.8）

| 端 | 入口 |
|---|---|
| 手錶 | 主畫面「晶片功能中心」→ 各晶片功能專屬頁；量測結束同步 `/raw-sensor/{kind}`（含 `chip_part`／`feature_page`） |
| 手機 | 主畫面／健康中心「晶片功能與資料」→ 顯示已同步摘要、開啟 `RawSensorDetailActivity`、遠端 `/control/*` |
| 對照 | `WatchFeatureMapActivity` 改為依晶片分類 |

## 2026-09-27 實機狀態覆核

以上是硬體/HAL 能力與 App 頁面索引，不代表每個項目已完成可用生理換算。最新手機匯出仍是 2026-09-27 07:16:28 的同一場 `partial` 掃描；新版手機 UI 安裝後尚無新掃描，因此下表只代表該舊場資料：

| 晶片／來源群 | 已觀察到的通道證據 | 目前失敗／限制分類 | 可安全確認的結果 |
|---|---|---|---|
| MAX86178 光學（PPG/MAX HR） | PPG 8 events/128 samples；MAX 光學 491/7,856 | PPG 有填充值／映射與時基未知；MAX HR 無可信取樣時基 | 原始資料收到；不換算 BPM/HRV |
| MAX86178 SpO₂ | 原始通道 487/3,896；MI SpO₂ 0 samples、`needs_oem_mode` | 原始封包無百分比單位/OEM曲線；MI 算法需原廠模式 | 僅 raw count/status，不輸出 SpO₂% |
| MAX86178 ECG | 220 events/2,200 samples | raw 時基/導聯/ADC 標定未知，RR 未通過 | 原始波形收到，不作心律診斷 |
| MAX86178 BioZ | 424 events/3,392 samples | 電極、激勵參數和 Ω 換算未知 | 原始阻抗訊號收到，不輸出體脂/體水/呼吸率 |
| LifeQ OTT | 0 samples、`needs_oem_mode` | 背景掃描未啟動原廠模式 | 明確標示需要原廠量測入口 |
| CT7117／溫度 HAL | thermometer 2 events，無有效 Celsius | 有事件但未解析出有效溫度；不得與另一溫度來源混用 | 事件數可見；目前沒有可用溫度換算值 |
| BMI26x／動作 | step detector `needs_motion` | 本輪沒有走動觸發 | 需要在量測窗口步行重測 |
| 離腕通道 | 本輪 `off_wrist` event | 佩戴/接觸門檻未通過 | 不接受同場生醫數據作校正 |

本次手機與手錶安裝版本仍顯示 3.13.15/code125；手機實際安裝 APK 已與當次建置 SHA-256 對上。日誌未見對應時段新的 App crash/ANR；這不能替代新版實機掃描驗收。`0 payload rejects` 只代表沒有明確 payload reject，不能解讀成感測成功或全資料零遺失。完整 PM 驗收表見 [[PM_HANDOFF_SENSOR_SCAN_2026-09-27]]。
