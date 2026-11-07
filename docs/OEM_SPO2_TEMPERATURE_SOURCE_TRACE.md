# OEM SpO₂ and temperature source trace / 原廠血氧與體溫資料路徑

Author / 作者：王宥傑 (Wang You-Jie)  
Inspected / 查核：2026-11-08

## Exact artifacts / 來源

- Watch: installed `com.xiaomi.wear.fitness` versionCode `2025091121`, SHA-256 `920459E3B2EBF569517E4294029705561E7BEAFF6CCA30EDB142E5A4F83B5965`, reconstructed locally with JADX 1.5.6. The decompiled output had 107 errors; the paths below are observations of reconstructed behavior, not original Xiaomi source.
- Phone: installed `com.xiaomi.wearable` `3.59.2i`, SHA-256 `D7B0A2B45E12899D91F9323BD59DAD8E3531C490CBF1BC83345F5631929CFBE2`.
- Neither OEM APK, Xiaomi native library, private account record nor patient/owner health value is included here.

## Watch service results versus public raw events / 手錶服務結果與公開原始事件

| Feature | OEM watch APK trace | Meaning supported by trace | Difference in Xiaomi Health Bridge 4.0 |
|---|---|---|---|
| SpO₂ / 血氧 | `health/spo2/Spo2ViewModel.java`: `g()` registers a `dd.d` callback, starts service type `20485`, and sends a local `start_measuring_spo2` broadcast. `i()` stops the service, unregisters the callback and sends `stop_measuring_spo2`. | Callback `d.a(type, bytes)` handles only type `20485` with non-null bytes. `xb/l.java` converts four bytes to a little-endian signed integer. Values greater than zero update the view model; values below zero update an OEM error state; zero does neither in this callback. The callback itself has no visible upper-bound check. This describes a **typed service result**, not how raw optical sensor columns become a percentage. | Our `WearMeasurePageActivity` reads a separate public Android SpO₂-related sensor through `RawVitalsProbe`. It retains bounded raw rows, requires a recent on-body event to call raw capture complete, and does not apply the OEM service's signed-result convention or emit a percentage. On the phone, attributed `OxygenSaturationRecord` values from Health Connect may be displayed with their source and recording method. |
| Skin temperature / 皮膚溫度 | `health/temperature/TemperatureViewModel.java`: `g()` starts service type `20500`; the callback accepts only `20500` and parses four bytes through `xb/l.java` as a little-endian IEEE-754 float. `i()` stops and unregisters. | A negative float is treated as a service error state. Otherwise the view model formats the float to one decimal and parses it back before updating its state; this branch does not explicitly check for finite values. OEM English resources call this a skin-surface measurement and display `°C` in their own UI. This does **not** identify the scale or correction of a separate Android raw temperature sensor. | Our raw body-temperature page preserves source and time, requires on-body state for raw-capture completion, and labels its unit unverified. A separately sourced Health Connect `BodyTemperatureRecord` can carry Celsius with provenance. Raw HAL values are not written as body temperature. |

The OEM watch measurement Activities separately monitor Android off-body sensor type `34` and stop a running measurement when the watch is off wrist. This was previously traced in [the 4.0 feature audit](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md). The on-body event only supports a contact-state prerequisite; it does not prove optical quality, temperature calibration, or a medically valid result. / 離腕感測只確認佩戴前提，不能代替光學品質、溫度校準或醫療驗證。

## Acquisition instructions reflected in 4.0 / 已用於 4.0 的操作提示

- OEM English `values-en-rGB/strings.xml` `setting_low_spo2_measuring_status1`, `status3` and `status5` say to keep the watch against skin, keep the wrist still and tighten the strap. The 4.0 raw SpO₂ page now gives those practical preparation steps without asserting that they unlock an OEM percentage.
- OEM `temperature_learn_more_text4` advises wearing the watch for at least ten minutes, keeping watch and wrist dry, and avoiding a reading while charging or immediately after high-power use. The 4.0 raw-temperature page now gives those preparation steps. The ten-minute condition is **OEM user guidance**, not our raw-sensor calibration or acceptance threshold.
- OEM `temperature_learn_more_text2` describes the OEM skin-surface measurement range as 34–42 °C. We **do not** copy that range into a validity gate for our public raw channel, whose unit has not been demonstrated.

## Phone records / 手機紀錄

In the installed phone APK, `com/xiaomi/fitness/health/spo2/Spo2ViewModel.java` exposes separate repository queries for manually measured records and extreme-low records. `com/xiaomi/fit/fitness/persist/db/internal/Spo2RecordEntity.java` names a local `spo2_record` table keyed by `key`, `sid` and `time`. These classes show stored-record and UI separation; they do not reveal a public raw-optical-to-percentage conversion, and our app does not read Mi Fitness's private database. Our phone instead reads Health Connect with per-source attribution and recording-method filters. / 原廠手機保有自己的血氧紀錄與查詢；本 App 不讀其私有資料庫，而是按 Health Connect 的來源與量測方式展示使用者授權的紀錄。

## Verification limits / 尚待驗證

- Static decompilation does not prove the watch-side `20485` or `20500` service returns a value in the current account/session. No raw owner reading or private OEM service call was collected for this trace.
- There is no evidenced correspondence between `SensorEvent.values` and the OEM four-byte service result. Do not decode public HAL event fields with `xb/l.java` or assign `%`/`°C` from the OEM result path.
- The 4.0 guidance change was code-reviewed and built; live worn and off-wrist raw SpO₂ and temperature sessions remain unverified. A full original-app session and paired Health Connect record would be separate evidence.

Public API boundaries: [Android off-body sensor](https://developer.android.com/reference/android/hardware/SensorEvent#TYPE_LOW_LATENCY_OFFBODY_DETECT), [Health Connect `OxygenSaturationRecord`](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/OxygenSaturationRecord), [Health Connect `BodyTemperatureRecord`](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/BodyTemperatureRecord). These define Android record semantics; they are not Xiaomi device calibration or clinical validation.
