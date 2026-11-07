# OEM APK capability breakdown / 原廠 APK 功能拆解

Author / 作者：王宥傑 (Wang You-Jie)
Inspected / 檢查日期：2026-11-08

This inventory distinguishes **code bundled in the phone APK**, **code installed on this Watch 2 Pro**, **a feature offered for the paired product**, and **a usable measurement**. They are four different claims. The phone APK is a shared multi-device app; its modules do not establish Watch 2 Pro entitlement or prove which newer Xiaomi watch offers them.

本表分開記錄「手機 APK 內含程式碼」、「這隻 Watch 2 Pro 上實際安裝程式碼」、「配對產品設定允許顯示」及「能完成有效量測」。四者不能畫上等號。手機 APK 是多裝置共用程式；單靠解包無法證明某項功能在 Watch 2 Pro 或特定新機型可用。

## Inspected artifacts / 已檢查檔案

| Artifact | Identity | Decompiler limit |
|---|---|---|
| User-supplied Mi Fitness phone APK | `com.xiaomi.wearable` 9.8.348i; SHA-256 `42958D4589654D9A9317B9B777F0A8B3C2637B8C8B11229308F9AEA4143C2A33` | JADX 1.5.6 completed with 415 errors. |
| APK pulled from this Watch 2 Pro | `com.xiaomi.wear.fitness` versionCode 2025091121; SHA-256 `920459E3B2EBF569517E4294029705561E7BEAFF6CCA30EDB142E5A4F83B5965` | JADX completed with 107 errors; native code is not recovered. |
| Privileged health service pulled from the same watch | `com.xiaomi.wear.fitness.whs_service` versionCode 2025091109; SHA-256 `47738E2C5BBD754D8F6636ACB0DE03DC2847A5B0A87BE4AD32DF0EE11651941A` | JADX completed with 44 errors; service data is private to the OEM app. |
| Mi Fitness base APK pulled read-only from the owner's **currently connected phone** | `com.xiaomi.wearable` versionName `3.59.2i`, versionCode `359002`; SHA-256 `D7B0A2B45E12899D91F9323BD59DAD8E3531C490CBF1BC83345F5631929CFBE2` | Different version from the supplied `9.8.348i` APK. JADX completed with 508 errors; four installed split APKs were observed, while this source pass decompiled the base APK only. No private phone app data or account token was read. |

Original APKs, decompiled source, assets, models and native binaries remain local and are **not redistributed** in this public repository. Source paths below are identifiers for the owner's local review.

## Shared phone feature gate / 手機端共用功能閘門

The reconstructed `com/xiaomi/fitness/device/manager/DeviceModelExtKt.java`, method `isSupportFeature`, reads `deviceModel.getProduct().getFeatures()` and checks `features.contains(feature)`. `Product.java` deserializes `features`, `feature_map`, `whitelist_feature` and `android_feature`; the APK alone does not reveal the current paired Watch 2 Pro's complete product record or its upstream provisioning. The UI also applies separate connection, account, ROM and channel checks in some cases.

手機端 `isSupportFeature` 的核心是查詢目前產品資料中的 `features` 清單。APK 解包可以看到功能鍵和顯示邏輯，卻無法從共用 APK 推定此時配對的 Watch 2 Pro 產品清單、帳號狀態或服務端核准。部分畫面還有連線狀態、手機 ROM 與發行通路判斷。

**Current-phone cross-check / 目前手機版本交叉核對：** In the installed `3.59.2i` base APK, `com/xiaomi/fitness/device/manager/DeviceModelExtKt.java` still implements `isSupportFeature` as `deviceModel.getProduct().getFeatures().contains(feature)` when the list exists. In `extensions/DeviceModelExtKt.java`, device-settings card case `162` still checks the literal `"ecg"`. The installed phone APK also declares `Feature.ECG="ecg"`, `Feature.ECG_MEASURE_REMIND="electrocardiogram"`, and a distinct `Feature.ECG_GLOBAL_MEASURE_REMIND="electrocardiogram_global"`. Thus the older supplied APK's card-gate finding survives in the actually installed phone version. The **actual paired product feature list** remains unread because it is held in the app's private runtime data; no model spoof or account inspection was performed.

**實機手機版本交叉核對：** 已安裝的 `3.59.2i` 仍以產品 `features` 清單決定功能支援，ECG 卡片 `162` 仍檢查 `ecg`。這比只看先前提供的不同版本 APK 更有力；但尚未讀到此帳號與配對錶當下的產品清單，所以不能單靠反組譯斷言清單實際含有或缺少 `ecg`。

**Product-list origin / 產品清單來源：** In this same installed phone APK, `DeviceRequest.getProductByCategory()` requests a product configuration from Mi Fitness's own service, passing locale, app version, device categories and a last-modified time. `ProductManagerRemoteImpl.update()` stores returned products by model and saves the list in Mi Fitness's private `files/config/product_<locale>.json` cache. It unconditionally merges an `androidFeature` list into `features`; a separate `whitelistFeature` merge depends on Mi Fitness's own product whitelist setting. `isSupportFeature()` then reads the resulting product's `features`. Thus the card test is **not a hardcoded comparison of the visible model name inside the APK**. The actual server response, cached Watch 2 Pro record and Watch 5 record were not accessed; static code cannot say which flags those records currently carry. No private service endpoint, cookie or account token is used by this project.

**原廠產品清單的來源：** 已安裝手機程式會按語系、App 版本與更新時間向原廠服務取得產品設定，按機型保存到 Mi Fitness 私有的 `product_<locale>.json` 快取，再合併 Android 功能與有條件的白名單功能。ECG 卡片檢查的是這份結果的 `features`，不是單看畫面上的機型名稱。尚未取得此帳號實際的 Watch 2 Pro 或 Watch 5 產品紀錄，不能斷言兩者當前的旗標差異。

| Phone code / 手機程式碼 | Concrete local evidence / 具體證據 | Availability finding / 可用性判斷 |
|---|---|---|
| ECG / 心電圖 | `health/ecg/`; `Feature.ECG="ecg"`; device settings card type `162` checks `"ecg"`. The Play-channel reminder/type-402 path checks `"electrocardiogram_global"`, while another channel checks `"electrocardiogram"`. `EcgActivationFragment` writes the phone activation preference after `EcgActivationCall` reports sync success, but that set call uses `needResponse=false` and does not query the final watch state. A cached 65-product **Eco** catalog logged by the installed phone lists three Watch 2 Pro variants without any of those three ECG keys, but its Watch 5 row includes `electrocardiogram_global`. | Owner reports **no ECG card** for paired Watch 2 Pro. The Eco cache is not the paired product's effective private feature list or activation state. Bundled code, another model's flag, sync callback and a displayed-name change do not establish that the card or watch activation is available. |
| Blood pressure / 血壓 | `health/bloodpressure/`; `Feature.BLOOD_PRESSURE_MEASURE_REMIND="blood_pressure"`; `BloodPressureFragment.java` checks this product feature. | Phone module is present. No Watch 2 Pro blood-pressure sensor or cuff-equivalent measurement established. |
| Blood glucose / 血糖 | `health/bloodglucose/`; `Feature.BLOOD_GLUCOSE="blood_sugar"`; third-party CGM code checks `"blood_sugar"`. | This is a phone/third-party data path, not evidence that the watch measures glucose. |
| Blood oxygen / 血氧 | `health/spo2/`; monitoring setting card `115` checks `"blood_oxygen_monitor"`; watch OEM `spo2` module and privileged service measurement type `20485`. | OEM typed result may yield percentage; raw HAL channels are not percentages. Own app uses attributed Health Connect records or research raw events. |
| Heart rate / 心率 | `health/hrm/`; setting card `113` checks `"heart_rate"`; installed watch `HeartRateViewModel.java` subscribes to bundled Health Services `"HeartRate"` data type via `WhsManager.v(...)`, receives a typed data point and availability, then unregisters with `WhsManager.y(...)`. | Our app already uses public Wear Health Services `HEART_RATE_BPM` `MeasureClient` with event timestamps and keeps raw optical channels separate. This is a comparable public route, not a hidden OEM unlock; quality and freshness remain device checks. |
| Sleep / 睡眠 | `health/sleep/`; sleep setting card `114` has device-specific assist-sleep logic; watch OEM `sleep` module. | Own app can show Health Connect sleep sessions/stages; OEM stage model cannot be inferred from the APK. |
| Sleep rhythm / 睡眠節律 | `health/sleep/rhythm/`; `Feature.SLEEP_RHYTHM="sleep_rhythm"`; rhythm repository checks the feature. | Separate product gate; no proof this Watch 2 Pro exposes it. |
| Stress / 壓力 | `health/stress/`; `Feature.PRESSURE_MONITOR="pressure_monitor"`, `STRESS_MANUAL="manual_pressure"`; watch OEM `stress` module. The watch `StressViewModel.java` starts service `20486`, receives its result in a callback logged as `stress Value`, and `be/b.java` names the same service `OEM_NONPASSIVE_PRESS`. `StressMeasuringActivity.java` observes `BodySensorUtil` and exits measuring on an off-wrist event. | Here `PRESS` means stress/psychological pressure, **not arterial blood pressure or barometric pressure**. Our research score now needs confirmed wear, valid optical signal and five RR intervals; those are our gates, not the OEM score formula. Raw HRV is not interchangeable with the OEM result. |
| Body composition / 體成分 | `Feature.BODY_FAT="body_fat"`; watch OEM `bodycomposition` module starts privileged service type `20487` and assembles subtype-tagged results; this is separate from the Android raw BioZ sensor path. | OEM output needs electrode state, impedance scale, user profile and model; raw BioZ cannot be converted to validated body-fat percentage from this evidence. See the [watch source trace](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md). |
| Temperature / 溫度 | `health/temperature/`; `Feature.TEMPERATURE="temperature"`; watch OEM `temperature` module. | Physical unit, anatomical site and correction need independent validation for raw readings. |
| Breathing / 呼吸 | `Feature.BREATHING_SCORE="breathing_score"`; watch OEM `breath` module. | A breathing feature or score is not proof of a calibrated respiration waveform from BioZ. |
| Activity and steps / 活動、步數 | `health/step/`, `health/calorie/`, `health/stand/`; watch OEM `sport` package. | Public Health Services/Health Connect route can be used with explicit source and time. |
| Training load, running index, VO₂max / 訓練負荷、跑步指標、最大攝氧量 | `health/trainload/`, `health/runningindicator/`, `health/vo2max/`; feature keys `"traning_load"` (OEM spelling), `"running_index"`, `"vo2max"`. | Bundled calculations and screens are not evidence this device reports the same values or that OEM algorithms can be copied. |
| PAI, vitality, physical status / 活力相關分數 | `health/pai/`, `health/vitality/`, `health/physicalstatus/`; feature keys `"pai"`, `"vitality"`, `"tsb"`. | Proprietary score semantics and eligible device inputs need separate evidence. |
| Device features / 裝置功能 | `Feature.java` also lists eSIM, NFC, watch face, weather, third-party app, notifications, sport HR broadcast, camera control and others. `DeviceModelExtKt.java` maps many settings card IDs to feature keys. | These are shared phone capabilities, not health sensors. Each requires its own Watch 2 Pro product feature, permission and device-side path. |

The list above is an evidence-backed **module and gate inventory**, not a claim that all bundled functions have been ported. In particular, `Feature.ECG` and `Feature.ECG_MEASURE_REMIND` are distinct keys; changing a local boolean in our own app would not alter the OEM product record or the watch privileged service.

## Live Watch 2 Pro sensor inventory / Watch 2 Pro 即時感測器盤點

On 2026-11-08, `adb shell dumpsys sensorservice` on the connected owner's **Xiaomi Watch 2 Pro** listed the following Android sensor channels. This is stronger evidence than finding a class in a shared APK: the watch's framework advertises these channels at runtime. It still does not prove calibration, user-facing activation, uninterrupted data, or clinical accuracy. Sensor names and type numbers are copied as short interface identifiers; no raw personal sensor values are included.

2026-11-08 直接讀取這隻已連線 Watch 2 Pro 的 Android 感測器清單，確認系統當下確實宣告下列通道。這支持「硬體及 HAL 路徑存在」的說法，但仍需分開驗證事件能否讀取、接觸狀態、單位校準及原廠功能授權。

| Sensor advertised by this watch | Android type | What it establishes / 能確認的事 | What remains / 尚待確認 |
|---|---:|---|---|
| `max86178_ecg` | 65573 | ECG event channel exists (wakeup and non-wakeup). | Electrode contact, ADC scale, clock gaps, OEM activation and valid ECG interpretation. |
| `max86178_ppg` | 65572 | Optical PPG raw channel exists. | LED/channel mapping, motion correction and validated heart-rate/SpO₂ derivation. |
| `max86178_spo2`, `mi_spo2` | 65596, 65588 | Two distinct SpO₂-related sensor interfaces exist. | Which raw fields map to calibrated OEM percentage; their names alone cannot supply it. |
| `lifeq_lel_heart_rate`, `max86178_hr` | 21, 65597 | Standard heart-rate and vendor HR channels exist. | Sample quality, wear/contact state and provenance of each reported value. |
| `max86178_bioz` | 65599 | Bioimpedance raw channel exists. | Electrode mode, ohm scale and body-composition or respiratory model. |
| `ct7117 body temperature`, `ct7117 board temperature`, `thermometer algo` | 65590, 65589, 65591 | Separate body, board and derived temperature interfaces exist. | Site, ambient correction, true units and medical-use accuracy. |
| `mi_wear`, `max86178_offbody`, `max86178_offbody0` | 34, 65598, 65600 | Wear/contact-related state channels exist. | Exact state polarity and synchronization with every physiological event. |
| `icp101xx Pressure Sensor` | 6 | Barometric pressure sensor exists. | **Not** evidence of blood pressure measurement. |

**SpO₂ behavior added from the installed watch APK / 依原廠手錶 APK 修正血氧：** `health/spo2/Spo2ViewModel.java` starts a typed OEM service measurement (type `20485`). Separately, `health/spo2/activity/MeasuringActivity.java` uses `BodySensorUtil`; its callback stops a running measurement when the body sensor value is not `1.0f`. The 4.0 research page now registers the public Android low-latency off-body sensor while acquiring raw SpO₂ events. It reports raw capture as complete only with a recent, time-aligned on-body event. An off-wrist or unknown state leaves the raw event available for research but never turns it into a percentage. The app's 60-second contact freshness window is its own conservative rule, not a reverse-engineered OEM SpO₂ constant.

原廠血氧頁除了經由服務取得有型別的量測結果，還會監看佩戴感測器；量測中若回報值不是 `1.0f`，就停止量測。本專案 4.0 因此在擷取公開血氧原始事件時，同步查驗 Android 離腕事件；僅在近期、時間對齊的「已佩戴」事件存在時標示原始擷取完成。離腕或未知時只保留研究封包，不產生血氧百分比。60 秒時效是本專案自己的保守規則。

**Temperature behavior / 體溫處理：** The installed OEM `TemperatureViewModel` starts a typed measurement through service type `20500`. The injected body sensor for its measuring Activity is Android type `34`; an active OEM measurement stops when the reported value differs from `1.0f`. The 4.0 raw body-temperature page now also requires a recent, time-aligned on-body event before declaring raw capture complete. It retains off-wrist or unknown raw values only as research evidence, without a prominent result or Celsius claim. The OEM service's negative-value error handling is **not** applied to the separate raw HAL stream.

原廠體溫量測結果由服務類型 `20500` 回傳，量測畫面另使用 Android 類型 `34` 監看佩戴；非 `1.0f` 時停止。本專案的原始體溫頁依此新增佩戴狀態判斷，離腕或未知時不標示完成，也不把原始值寫成攝氏體溫。原廠服務的負數錯誤碼不能直接套用到不同的原始 HAL 通道。

For this watch, the ECG, PPG, SpO₂, HR, BioZ and temperature paths are therefore **present in the Android sensor inventory**. The phone APK's blood-pressure and blood-glucose modules remain separate claims; neither follows from the barometer nor from these channels. A missing OEM card may indeed be a software/product gate for a particular feature, but this inventory does not identify every upstream rule.

就這隻手錶而言，ECG、PPG、血氧相關、心率、BioZ 與溫度通道有實機證據。手機 APK 裡的血壓與血糖模組是另外的資料路徑；氣壓計不能當血壓計。原廠卡片缺席可能涉及軟體或產品設定，但上述清單尚不能指出每一道上游閘門。

**Hardware-lock finding / 硬體鎖定判斷：** No hardware block has been observed on this Watch 2 Pro's **raw Android ECG sensor path**: the live device exposes type `65573`, and the project has received raw events through that path. This finding is scoped to access to raw events. It does not establish that OEM ECG recording, calibration, report generation, account activation, or every other sensor feature is available without a separate firmware, service, or product gate.

**結論：** 目前沒有觀察到這隻 Watch 2 Pro 的**原始 Android ECG 感測器路徑**被硬體封鎖；實機宣告 `65573`，本專案也曾從該路徑收到原始事件。這個結論只涵蓋原始事件存取，不能延伸成原廠心電圖報告、校準、帳戶啟用或其他全部功能均已解鎖。那些路徑仍須逐項量測。

## OEM switches on this watch / 原廠手錶端的開關

The installed watch's `LocalData` parcel contains separate fields for `assistSleep`, `sleepBreathQuality`, `bloodOxygenMonitor`, `heartRateMonitor`, `stressMonitor`, `stand_reminder_on`, and `ecgActivation`, along with alert thresholds and goals. In the installed privileged service, reconstructed `o8/a.java` reads them from its private `local_data` preferences using keys `assistSleep`, `sleepBreathQuality`, `bloodOxygenMonitor`, `heart_rate_monitor`, `stress_monitor`, `stand_reminder_on`, and `ecg_activation_status`. These are **configuration/activation state values**, distinct from physical sensor presence. A further trace of `WhsService.java` finds writers for several monitoring settings: the service receives a structured configuration message, stores the value, and forwards a compact command to a lower device layer. This establishes a genuine software-controlled monitoring route on this watch. The reconstructed code does not establish that every control is open to third-party apps or reveal every upstream phone/account rule.

這隻手錶的原廠服務確實把「輔助睡眠、睡眠呼吸品質、全天血氧、心率監測、壓力監測、久坐提醒、ECG 啟用」當作各自的設定或啟用狀態保存。進一步追到部分寫入流程：原廠服務接收設定訊息後，會保存開關，並向更底層轉送設定命令。這是「手機 App 操作既有手錶功能」的直接程式證據。ECG 啟用的上游寫入路徑仍未定位；不能把設定欄位等同校準完成或把 `false` 全改 `true` 當成有效量測。

| OEM setting/state | Local preference key | Runtime meaning established by inspection |
|---|---|---|
| Assisted sleep / 輔助睡眠 | `assistSleep` | Stored watch service setting; no new physical sensor implied. |
| Sleep breathing quality / 睡眠呼吸品質 | `sleepBreathQuality` | Stored setting; output algorithm and eligibility still need tracing. |
| Blood oxygen monitoring / 全天血氧監測 | `bloodOxygenMonitor` | Stored monitoring toggle, separate from manual SpO₂ typed request. |
| Heart-rate monitoring / 心率監測 | `heart_rate_monitor` | Stored monitoring interval/mode integer, not a simple boolean. |
| Stress monitoring / 壓力監測 | `stress_monitor` | Stored monitoring toggle; OEM score model remains private. |
| Stand reminder / 久坐提醒 | `stand_reminder_on` | Stored reminder toggle, not a physiological sensor. |
| ECG activation / ECG 啟用 | `ecg_activation_status` | Stored activation state; on this watch the visible OEM page requested phone ECG-card activation. |

Concrete write trace: in the installed service's `WhsService.java`, `syncSpo2Config` persists `bloodOxygenMonitor` and an oxygen warning goal, then calls a lower-layer configuration path; `syncHeartRateConfig` persists `assistSleep` and `sleepBreathQuality`, and updates `heart_rate_monitor` separately. The service's `handleCloseOrOpenHealthAllMonitor` reads current values for SpO₂, stress, HR and both sleep options and forwards their states during monitor resume. A second service source file `whs_service/s.java` also writes HR, sleep, SpO₂ and stress settings. These are observations of OEM behavior, **not** a supported third-party control protocol; this project does not replay private messages or alter the OEM service.

具體寫入路徑：`WhsService.java` 的血氧設定流程保存全天監測與警示門檻；心率設定流程保存輔助睡眠、睡眠呼吸品質和心率監測設定；重新啟動監測時會讀取並下送已保存的狀態。`whs_service/s.java` 另有心率、睡眠、血氧、壓力設定寫入。這些是原廠內部流程觀察，不是可對第三方保證的介面。

This is a stronger distinction than simply counting APK classes: the **same installed watch** advertises the relevant sensor channels and stores separate OEM feature switches. To establish a specific software lock as the only obstacle, each switch still needs a traced writer and a successful device measurement after activation. / 相較只數 APK 類別，這是更直接的實機證據；但要證明某項功能「只差軟體鎖」，仍須追到該開關的寫入來源，並驗證開啟後的量測結果。

## Watch 5 identity hypothesis / 模擬手錶 5 身分的假設

Xiaomi's [Watch 5 product page](https://www.mi.com/sg/product/xiaomi-watch-5/) advertises ECG reports and says the watch firmware and Mi Fitness app must be updated. Its [Watch 5 FAQ](https://www.mi.com/uk/support/faq/details/KA-667495/) also states that the Watch 5 does **not** support body-temperature or body-composition measurement. Therefore “all Watch 5 functions” is not a superset of the channels present on this Watch 2 Pro, and the models cannot be treated as interchangeable feature profiles.

小米官方 Watch 5 頁面列出 ECG，但官方 FAQ 又指出 Watch 5 不支援體溫和體成分量測；這隻 Watch 2 Pro 卻有溫度和 BioZ 通道。因此「套用 Watch 5 的全部功能」不是把這隻錶現有能力全部打開的等價操作。

| Layer to test / 待測層 | Existing evidence / 現有證據 | Prediction if only the phone product features were changed / 只改手機產品功能清單的預期 |
|---|---|---|
| Phone ECG card | The supplied APK's card type `162` checks product `features.contains("ecg")`; the owner currently sees no card for Watch 2 Pro. | A product record containing `ecg` passes **this card visibility check**. Other checks and official activation still apply. |
| Watch raw ECG channel | This actual Watch 2 Pro advertises sensor type `65573`; prior owner-device testing recorded raw events. | No model spoof is required to establish the raw Android sensor path. |
| OEM ECG measurement | This actual watch's OEM ECG entry showed activation guidance; privileged service stores `ecg_activation_status`. | Phone card visibility alone does not set the watch service flag or validate the OEM analysis. |
| Other monitoring toggles | The installed service stores and forwards SpO₂, HR, sleep and stress settings. | Each can have its own product/phone/service gate; an ECG feature flag does not automatically enable them. |

**Static simulation result so far:** evaluation of the decompiled card predicate gives `features.contains("ecg") = true` for a hypothetical eligible product record; the real Watch 2 Pro still has a visible OEM activation gate, and raw ECG events were already obtainable. This is a **two-layer counterexample** to “change the model name and everything unlocks.” No Watch 5 product record, phone APK, pairing identity, private service preference or medical output was modified. A real device-identity change would additionally risk pairing and cloud state and would not isolate which gate caused the outcome. The next discriminating evidence is the paired phone's actual product feature list and the OEM service's activation writer.

**目前靜態模擬結論：** 按手機程式的判斷式，假設產品清單含 `ecg`，該卡片可通過這一關；Watch 2 Pro 的原始 ECG 通道本來就可見，但原廠量測仍顯示啟用提示。這證明至少有不同層的軟體狀態，尚不能說改一個機型名稱就全開。本次未更改手錶身分、配對資料、原廠服務或醫療輸出。

## Installed watch packages / 這隻手錶實際安裝的模組

The installed Xiaomi Health APK has `health/{heartrate,spo2,ecg,bodycomposition,temperature,stress,sleep,breath}` and `sport/` packages. Its manifest exports the main Activities recorded in [the Watch 2 Pro feature audit](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md). Xiaomi Health uses the separate privileged health service for typed measurements and local state. Presence of an exported Activity permits a foreground launch attempt; it does not make private binder data or activation available to our package.

手錶上有上述八類健康模組與運動模組，且部分主畫面可由使用者點選開啟。真正的原廠量測資料與啟用狀態由另一個系統權限服務提供；第三方 App 不能因為畫面可開就取得相同的權限或結果。

| Data layer / 資料層 | Observed path / 已見路徑 | Own app action / 本專案做法 |
|---|---|---|
| Android public APIs | Health Services, Health Connect, permitted sensor events | Implement independently with per-record source, timestamp and quality checks. |
| OEM watch UI | Exported Xiaomi Health Activity | Open only after user taps a visible entry; show OEM's own availability message. |
| OEM typed service | Privileged `WhsService`/`WhsManager`, `LocalData`, typed requests | Document the behavior; do not claim a stable third-party API or copy private service code. |
| OEM native algorithms | ECG JNI and other proprietary model/algorithm paths | Record inputs and limits; do not reuse binaries or label uncalibrated raw samples as medical outputs. |

## What to verify next / 下一步真機核對

1. Capture the **current paired product feature list** or official device-settings evidence, without account tokens or device identifiers, to distinguish a missing card from a service failure.
2. For each relevant Watch 2 Pro function, test the OEM foreground page and record the visible outcome (available, guidance, unsupported, or permission request).
3. Compare public API records and raw sensor events with explicit source, time, field shape, contact state and units. Validate each metric separately before exposing a derived number.
4. Keep ECG, SpO₂, body composition and any blood-pressure/glucose claims labelled according to their actual evidence. The APK alone supplies no clinical validation or device-specific entitlement.

This breakdown extends the [OEM feature audit](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md) and [phone APK comparison](MI_FITNESS_9_8_348I_REVERSE_ENGINEERING_COMPARISON.md). / 本文件補充原廠功能稽核與手機 APK 對照文件。
