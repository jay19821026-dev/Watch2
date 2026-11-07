# Watch 2 Pro 4.0 OEM feature audit / 原廠功能稽核

作者 / Author: 王宥傑 (Wang You-Jie)
日期 / Date: 2026-11-07
範圍 / Scope: Xiaomi Health Bridge `4.0.0` 的獨立實作；本文件不授權或啟用原廠私有功能。

## Evidence and method / 證據與方法

| Artifact | Static evidence | Limits |
|---|---|---|
| User-supplied Mi Fitness phone APK | `com.xiaomi.wearable`, `9.8.348i`, SHA-256 `42958D4589654D9A9317B9B777F0A8B3C2637B8C8B11229308F9AEA4143C2A33`; JADX 1.5.6; see [phone comparison](MI_FITNESS_9_8_348I_REVERSE_ENGINEERING_COMPARISON.md) | Decompiled code has errors; no Xiaomi account, cloud or device-state query was performed. |
| Local Xiaomi Health watch APK | `dist/Fitness-watch-official.apk`, `com.xiaomi.wear.fitness`, versionCode `2025091121`, SHA-256 `920459E3B2EBF569517E4294029705561E7BEAFF6CCA30EDB142E5A4F83B5965`; manifest via Android `aapt`, static inspection via JADX 1.5.6 | JADX finished with 107 errors. The artifact's exact match to the watch currently worn has **not** been rechecked in this audit. |
| Prior Watch 2 Pro evidence | [chip calibration status](CHIP_CALIBRATION_STATUS.md) records a prior ECG HAL subscription, roughly 20 ms between three packets with ten leading values each, and a foreground launch of the OEM ECG Activity. | This is historical evidence from 2026-09-21; it does not show the current `ecgActivation` value or a completed ECG measurement. |
| Current connection | `adb devices -l` on 2026-11-07 returned no phone or watch. | No fresh installed-version, activation, permission, sensor, worn-state or UI result is claimed. |

The [Android Sensors HAL](https://source.android.com/docs/core/interaction/sensors/sensors-hal2) connects device sensors to the Android sensor framework. Apps may receive only events actually exposed by the framework and permitted on the device. A readable `SensorEvent.values` array is not automatically a calibrated physical measurement. Health Connect's [recording method metadata](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/metadata/Metadata) distinguishes manual, active, automatic and unknown recording, but is supplied by the source app and is not a calibration certificate.

Android HAL 是感測器和 Android framework 之間的介面。App 是否能讀取取決於裝置實際暴露的通道及權限；`SensorEvent.values` 的原始欄位不等於已校準的生理數值。Health Connect 的量測方式由資料來源提供，也不能當成醫療驗證。

## ECG: what the APK actually gates / ECG 實際閘門

1. The watch APK contains an exported `EcgMainTileActivity`, `EcgMainActivity`, ECG instruction/result screens, an ECG tile, and a native `EcgJni` wrapper. The presence of these classes proves code is bundled, not that every watch model is entitled to the feature.
2. `EcgMainActivity` asks its `EcgMainViewModel` for service data. The callback reads the OEM `LocalData.ecgActivation` boolean. When true, it opens first-use or main ECG flow; when false, it shows the activation guidance page. This is the **observed immediate UI gate**. The watch APK's English activation guidance tells the user to update the phone's Mi Fitness app and activate ECG from its ECG card. Its button only acknowledges and closes that guidance page.
3. The supplied phone Mi Fitness APK includes ECG card/activation resources and an ECG WebView authorization flow with Xiaomi account authorization and application agreement. This supports an **official phone-side activation path**, but does not prove the ECG card is shown for this Watch 2 Pro, that the account/region is eligible, or that authorization succeeds. No account token or endpoint is used by our app.
4. The watch-side `EcgSensorUtil` obtains a vendor sensor by numeric type `65573` and also subscribes to acceleration and off-body/contact-related sensors. It inspects event fields and sends candidate signal values into native `EcgJni`. A native analysis result and average heart rate are later displayed by OEM code. We do not copy the field mapping, native binary, timing assumptions, or clinical output into this app.
5. The watch APK does **not** reveal why this watch's service might set `ecgActivation=false`. Possible model, region, firmware, account, consent, regulatory or provisioning rules remain hypotheses until the current paired device and official Xiaomi eligibility guidance identify the source. Xiaomi's [global Watch 2 Pro product page](https://www.mi.com/global/product/xiaomi-watch-2-pro/) lists heart rate, blood oxygen, sleep, stress and body composition/skin temperature, but does not advertise ECG on that page; this supports caution, not a definitive explanation for the flag.
6. Forcing the boolean to true in a UI or modified APK would hide the actual gate. It would not establish sensor contact, calibration, native result validity, server entitlement or medical authorization. Xiaomi Health Bridge 4.0 instead exposes a **foreground, user-tapped OEM ECG page link** and retains its independent raw ECG research capture. Opening an exported Activity is not the same as activating ECG.

原廠程式有 ECG 頁面，也有 `ecgActivation` 狀態閘門；這是「畫面為何進不了量測」的直接線索。它不回答「原廠為何把這隻 Watch 2 Pro 設為 false」。4.0 可以由使用者點擊原廠入口，查看原廠給出的啟用畫面；不能宣稱已解鎖。獨立 ECG 只保留原始研究訊號，不輸出未經驗證的 mV、房顫或診斷。

## Feature inventory / 功能盤點

`OEM module` means bundled watch APK code; `4.0 path` states what our app can truthfully do. Presence of a module is not proof of availability on the user's device.

The inspected watch manifest marks these top-level Activities `android:exported=true`: `HeartRateMainActivity`, `spo2.activity.MainActivity`, `EcgMainTileActivity`, `BodyCompositionActivity`, `TemperatureMainActivity`, `StressMainActivity`, `SleepMainActivity`, and `BreathMainActivity`. Version 4.0 links only to these declared entry points from a visible watch screen. Exported status means Android can attempt to start an Activity; it does **not** grant access to Xiaomi's protected data or force the feature to activate.

| Feature / 功能 | OEM static evidence | Xiaomi Health Bridge 4.0 path | Remaining gate / 未完成條件 |
|---|---|---|---|
| Heart rate / 心率 | `heartrate` module; phone HR records and reports | Wear Health Services or allowed sensor data → Data Layer → Health Connect/history | Fresh worn-device verification and source/quality checks |
| ECG / 心電圖 | `ecg` module, tile, activation flag, vendor sensor, native analysis | Independent raw capture plus user-tapped OEM page entry | OEM activation reason; electrode/clock/unit calibration; clinical validation |
| SpO₂ / 血氧 | `spo2` watch module; phone manual, low-event and detail records | Health Connect percentage records with source and recording-method separation; raw HAL stays raw | OEM optical mapping/calibration; live export or HC provenance |
| BioZ/body composition / 生物阻抗與體成分 | `bodycomposition` module | User-tapped OEM page and raw BioZ research capture | OEM electrode configuration, ohm scale and body-composition model |
| Skin/body temperature / 體溫 | `temperature` module | Public Health Connect records if present; raw watch sensor diagnostics | Device-specific Celsius scale, site, correction and validation |
| Stress/HRV / 壓力 | `stress` module; phone stress table | Transparent HRV/raw quality research where inputs are valid | OEM stress algorithm/model and verified sampling conditions |
| Sleep / 睡眠 | `sleep` watch module; phone sleep segments and reports | Health Connect sessions/stages, source-aware day/week/month views | OEM native sleep model and stage validation unavailable |
| Breathing / 呼吸 | `breath` module | Health Connect respiratory records or raw BioZ research, labelled by source | OEM BioZ-to-respiration calibration |
| Activity/sport / 活動運動 | Phone activity/sport/training-load records; watch sport code | Health Services/Health Connect, existing watch workout and history | OEM PAI/training-load formula and source-specific parity |
| Navigation and sensors / 導航與環境 | Watch app exposes IMU/GNSS/barometer/light modules; current app has sensor pages | Public Android sensor/location APIs, existing navigation/compass views | Device permissions, real-device availability and environmental calibration |

## 4.0 implementation checkpoint / 4.0 實作點

- Both app modules move from `3.13.44` / versionCode 154 to `4.0.0` / versionCode 155. The matching debug APKs are test builds; they have **not** been installed on the current watch or approved by Play.
- Phone SpO₂ history now separates Health Connect manual, active, automatic and unknown recording methods per selected source, labels each history row, and suppresses the device-reference score when manual or unknown rows are included. It does not infer whether the sensor itself was calibrated.
- Wear ECG research page now has a user-tapped link to the OEM exported ECG Activity. The app never changes `ecgActivation`; it reports only that the launch request was sent and lets the OEM page show the actual state.
- A new **原廠功能入口** screen on the watch offers foreground links to the OEM heart-rate, SpO₂, ECG, body-composition, temperature, stress, sleep and breathing screens whose top-level Activities are exported in the inspected APK. Each launch is a request, not a claim that the feature is enabled.
- The other watch features listed above already have public-path pages or research capture in this repository. **No OEM private algorithm, private binder or hidden activation flag was imported.** Their current physical availability must be checked on the paired watch.

## Next device checks / 下一次真機核對

1. Confirm phone/watch installed package versions, OEM watch APK version, permissions, region and current firmware without collecting account secrets. In Mi Fitness, check whether an ECG card exists for this paired Watch 2 Pro; if present, follow its official activation flow and record only the visible outcome, not credentials or tokens.
2. On an uncharged, worn watch, tap **查看原廠 ECG 啟用狀態** and record only the visible result and bounded app/system logs. Distinguish activation guidance, permission denial, missing Activity, missing sensor event and successful OEM measurement.
3. Run independent ECG raw capture separately. Record event count, 16-field shape, source timestamps, contact state, timeouts and Data Layer acknowledgement. Do not copy OEM native outputs or turn packet cadence into mV/AF claims.
4. Verify each other feature through its public API or explicit OEM page, one at a time, with per-metric source/freshness. Mark unsupported and uncalibrated results honestly.

## English conclusion

The OEM watch APK bundles many feature modules, including ECG. The ECG UI is gated by a service-owned `ecgActivation` flag; the decompiled APK does not reveal why the current Watch 2 Pro is or is not activated. Version 4.0 independently exposes accessible public paths and a foreground OEM ECG entry, while preserving research-only raw evidence and the original gate. Clinical ECG, SpO₂ and body-composition outputs require device-specific calibration and validation beyond APK inspection.
