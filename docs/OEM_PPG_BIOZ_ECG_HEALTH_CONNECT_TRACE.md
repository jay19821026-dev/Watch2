# PPG, BioZ and ECG source trace / 原廠訊號與 Health Connect 對照

Checked 2026-11-08 against the locally decompiled, installed Watch 2 Pro Xiaomi Health APK and privileged MiHealthService APK. This document describes observed call paths; no OEM source or binary is redistributed. Decompiled output may contain errors, and these paths do not prove that a measurement is available or calibrated on this device.

2026-11-08 核對本機反組譯的 Watch 2 Pro 原廠健康 App 與特權服務。以下記錄可觀察的呼叫路徑，不散布原廠程式碼或二進位檔。反組譯可能有誤，程式中有路徑不等於本機已授權或校準。

## Source trace / 原始路徑

| Signal | OEM acquisition and handling | Confirmed boundary |
| --- | --- | --- |
| Heart-rate PPG / 心率光學 | `MiHealthService-decompiled/sources/com/xiaomi/wear/fitness/whs_service/collection/DataCollectionService.java`: `onBind` accepts `isPpgChecked` and `ppgRate` (1093, 1105); selected collection calls `d(65597, rate, ...)` (1241–1243), which registers an Android `SensorManager` listener (738–836). Callback writes `SensorEvent.values` using `g(...)` (390–395). CSV is named `ppgHr-...HZ.csv` (797–798); its header distinguishes green optical channels, counter and, in one collection mode, driver/TIA fields and a heart-rate value (1035–1046). | This is an OEM **raw collection/diagnostic** route. The header names are evidence of intended channel labels, not a verified AILISA field map, optical calibration or independent BPM algorithm. |
| SpO₂ PPG / 血氧光學 | Same service: `isPpgBloodOxygenChecked` and `ppgBloodOxygenRate` (1094, 1106), `d(65596, rate, ...)` (1305–1307), callback `j(...)` (384–389), `ppgSpo2-...HZ.csv` (794–795). Header labels red/IR channels, counter and optional driver/TIA and SpO₂ fields (1028–1034). Separately, `XiaomiHealth-watch-decompiled/sources/com/xiaomi/wear/fitness/health/spo2/Spo2ViewModel.java` starts typed service `20485` (317) and consumes its result (221). | Raw optical values are distinct from the service's percentage result. The APK does not establish a raw-to-SpO₂ formula or a validated oxygen percentage for AILISA. |
| BioZ / 生物阻抗 | The collection service accepts `isBodyFatChecked` and `bodyFatRate` (1092, 1109), calls `d(65599, rate, ...)` (1237–1239), writes ten raw values per callback using `e(...)` (414–418, 702–714), and names the CSV `bodyFat.csv` (803–804). Its header labels load magnitude/phase, I/Q, raw/average I/Q, measurement state and calibration state (1054–1055). Separately, `XiaomiHealth-watch-decompiled/sources/com/xiaomi/wear/fitness/health/bodycomposition/viewmodel/BodyCompositionViewModel.java` starts typed service `20487` (341), decodes subtype/value from returned bytes (90–150), and assembles body-composition results. | The CSV labels show what the OEM collector expects, but do not reveal this device's ohm scale, electrode configuration, calibration or the typed service's body-composition algorithm. The typed result cannot be derived safely from the raw values alone. |
| ECG / 心電原始事件 | `XiaomiHealth-watch-decompiled/sources/we/b.java` (`EcgSensorUtil.kt`) requests sensor `65573` (94). In `onSensorChanged` it skips the first 20 callbacks, checks event field 10 for marker `1000` and a separate worn state (232–249), then passes ten leading candidates per accepted event with synthetic 2 ms increments to `EcgJni.algInputSignal` (281–299). Acceleration goes to `algMovingInputSignal` (220). `health/ecg/jni/EcgJni.java` loads `libfitness` and declares native input/result methods. | OEM ECG activation is a separate service-owned gate. Native analysis, electrical units, lead configuration and clinical validation have not been recovered; the sensor path alone does not activate clinical ECG. |

The raw PPG collector is in the privileged **MiHealthService** APK, while the normal watch health screens receive typed service results. This resolves why a search limited to the watch health UI package did not find PPG processing. The raw BioZ collector and the typed body-composition service are likewise separate paths.

PPG 原始擷取器在特權 **MiHealthService** APK；一般手錶健康畫面取得的是服務處理過的結果。因此只搜尋健康畫面套件，會漏掉 PPG 原始擷取。BioZ 原始擷取與體成分結果也分屬不同路徑。

## AILISA and Health Connect / AILISA 與 Health Connect

AILISA Wear `RawVitalsProbe.kt` currently inventories named raw sensors `com.google.wear.sensor.ppg`, `.bioz`, `.ecg`. These names must be matched to live sensor type/vendor on this device before treating them as the OEM numeric channels above. The raw research screen/export preserves source values and timing but does not turn them into calibrated heart rate, SpO₂, body fat or diagnostic ECG.

AILISA 手錶版目前在 `RawVitalsProbe.kt` 列舉 `.ppg`、`.bioz`、`.ecg` 原始感測器名稱。仍須以實機 sensor type／vendor 核對其是否對應上述原廠數值通道。研究畫面與匯出保留原始值及時間，不把它們直接改稱心率、血氧、體脂或診斷心電圖。

Verified, unit-bearing watch heart-rate packets flow through `WearDataProcessor` → `HeartRateWriter` → Health Connect `HeartRateRecord`; supported typed activity/SpO₂ packets flow through `StandardHealthWriter` after value/time/permission checks. The phone has granted AILISA read and write permission for heart rate, steps and activity data on 2026-11-08, but these grants do not prove that a fresh watch packet was written or that another app has read it. Health Connect is the interoperability route; each receiving app also needs its own read permission. Raw PPG/BioZ/ECG event arrays have no matching standard clinical result record and are not written as one.

具備單位與有效來源的手錶心率封包會經 `WearDataProcessor` → `HeartRateWriter` → Health Connect `HeartRateRecord`；支援的活動／血氧結果經 `StandardHealthWriter` 檢查值、時間及權限後寫入。2026-11-08 手機已授權 AILISA 讀寫心率、步數及活動資料，但授權不證明已有新的手錶封包寫入，也不證明其他 App 已讀取。其他 App 仍需自行取得 Health Connect 讀取權限。原始 PPG／BioZ／ECG 事件陣列不會冒充標準臨床結果紀錄寫入。

Remaining verification: capture a worn-device sample for each raw channel; record actual sensor name/type, event shape, source time and contact state; compare any OEM typed result by timestamp without assuming a transfer function; then confirm a freshly written Health Connect record and receiving-app read independently.

後續實測需在佩戴狀態分別擷取各通道，記錄 sensor 名稱／type、事件欄位、來源時間與接觸狀態；再以時間對照原廠結果，而不是假設換算公式。最後要分別核對 Health Connect 新增紀錄與接收 App 的實際讀取。
