# Mi Fitness 9.8.348i clean-room comparison / 靜態反組譯比較

作者 / Author: 王宥傑 (Wang You-Jie)  
分析日期 / Analysis date: 2026-11-07

## Executive summary / 摘要

本文件比較使用者提供的 `Mi Fitness (Xiaomi Wear) 9.8.348i` APK 與 Xiaomi Health Bridge。分析只讀取 APK 的靜態程式結構；沒有登入小米帳號、沒有擷取個人健康資料、沒有呼叫小米伺服器，也沒有執行或移植小米的原生演算法。

This document compares the user-supplied Mi Fitness 9.8.348i APK with Xiaomi Health Bridge. The review is static only: no Xiaomi account was used, no personal health data was extracted, no Xiaomi server was contacted, and no Xiaomi native algorithm was executed or copied.

最重要的結論：Mi Fitness 是一條 OEM 專用資料管線，從裝置 SDK 同步資料、解析成型別化紀錄、寫入本機 Room 資料庫、產生聚合報表，再選擇性同步雲端。Xiaomi Health Bridge 則優先使用公開的 Wear OS Health Services、Android Health Connect、Android Sensor API 與 Google Wearable Data Layer，並針對未知映射、時鐘、單位與校準採 fail-closed 證據閘門。兩者目的不同，不能把 Mi Fitness 的欄位或演算法名稱當成 Watch 2 Pro 的公開協定或校準證據。

The key result is that Mi Fitness uses an OEM-specific pipeline: device-SDK synchronization, typed parsing, local Room persistence, report aggregation, and optional cloud synchronization. Xiaomi Health Bridge instead prioritizes public Wear OS Health Services, Android Health Connect, Android Sensor APIs, and the Google Wearable Data Layer, with fail-closed evidence gates for unknown mapping, clock, units, and calibration. An Mi Fitness field or algorithm name is not a public Watch 2 Pro protocol or calibration contract.

## Examined artifact / 分析標的

| Item | Value |
|---|---|
| File | `Mi+Fitness+(Xiaomi+Wear)_9.8.348i_APKPure.apk` |
| Package | `com.xiaomi.wearable` |
| Version | `9.8.348i` (`versionCode 908348`) |
| Android range | minSdk 26, targetSdk 34 |
| SHA-256 | `42958D4589654D9A9317B9B777F0A8B3C2637B8C8B11229308F9AEA4143C2A33` |
| Tool | JADX 1.5.6, static decompilation |
| Coverage caveat | 約 44,154 個類別；JADX 回報 415 個錯誤。下列結論只採可交叉驗證的 Manifest、Kotlin metadata、Room schema、資產與可讀類別；不把失敗方法猜成事實。 |

反組譯產物沒有提交到本儲存庫，避免再散布第三方著作。Only this clean-room observation report is retained; decompiled Xiaomi code is not committed.

## Observed Mi Fitness data flow / 觀察到的資料流

```text
Watch / wearable
  -> Xiaomi/Huami device SDK and Bluetooth transport
  -> per-data-type sync manager and last-sync watermark
  -> parser / recorder
  -> Room tables keyed by key + sid + time
  -> repository/business aggregation
  -> day/week/month UI and optional cloud upload/delete state
  -> restricted Xiaomi health-provider service for selected summaries
```

### 1. Device acquisition and synchronization / 裝置同步

- `HMDeviceSyncManager` exposes separate paths for daily activity, heart rate, resting heart rate, SpO2, low-SpO2 events, abnormal heart rate, stress, sleep, sport, PAI, training load and AFib-related data.
- Synchronization is device-scoped (`did`/`sid`) and keeps a per-type `lastSyncTime`, rather than treating the newest global packet as fresh evidence for every metric.
- Data is parsed through Xiaomi/Huami SDK result types before persistence. This differs from directly listening to Android `SensorEvent` values.
- The APK manifest declares Bluetooth, connected-device foreground-service, activity-recognition and Xiaomi-specific permissions. Static search found no `androidx.health.connect`, `android.health.connect`, or `HealthConnectClient` references, and the manifest does not declare the ordinary Android `BODY_SENSORS` permission.

Interpretation: for this build, the primary phone-side health path is OEM device sync, not evidence that Mi Fitness reads the same public Health Connect records or raw Android sensors used by Xiaomi Health Bridge.

### 2. Local persistence / 本機資料庫

`FitnessDatabase_Impl` exposes dedicated Room tables including:

- `hr_record`, `step_record`, `calorie_record`, `stand_record`
- `sleep_segment`, `spo2_record`, `stress_record`, `temperature_record`
- `sport_report`, `training_load_record`, `v02max_record`, `pai_record`
- `blood_pressure_record`, `blood_sugar_record`, `ecg_medical_record`
- `server_request` and `fds_data_id` for synchronization state

Most metric rows share `key`, device/source identity `sid`, `time`, serialized `value`, timezone offset/name, normalized UTC time, `isUpload`, and `isDeleted`. Primary keys combine metric key, source identity, and time. This supports source separation, deterministic updates, timezone-aware queries, upload state and soft deletion.

### 3. Repository and report layer / Repository 與聚合

- Metric repositories are separate (`HeartRateRepository`, `Spo2Repository`, `SleepRepository`, etc.).
- SpO2 distinguishes manual records, low-SpO2 records, detail records and daily reports.
- Sleep preserves segments and then builds project/report views such as sleep time, bedtime, wake time, diary, rhythm and monthly grouping.
- Report merging is source-aware (`sid`, view tag, start timestamp), which is more deliberate than selecting one app-wide “latest value.”
- Rows include cloud upload and deletion markers. This establishes an offline-first/local-cache pattern, but does not by itself prove what the server retains.

### 4. Algorithms and embedded assets / 演算法與模型

The APK contains proprietary native or model assets, including:

- `assets/mi_stage_model_0_1_1.tflite`
- `libjni_xiaomi_sleep_algorithm.so`, `libsleep_detect.so`, `libsleep_quality.so`, `libPhoneSleepSDK.so`
- `libjni_mi_ecg_algo.so`, `libmi_ecg_alg.so`
- `libgps_algo_share.so` and `libjni_gps_algo.so`

The readable `MiParseModel` wrapper loads the TFLite asset locally, uses a float matrix input and produces four output classes. The APK alone does not establish the training dataset, class semantics, validation population, accuracy, license for reuse, or suitability for Watch 2 Pro. The ECG wrapper exposes a native `ecg_midev` function, but its native implementation and validation evidence are not a public algorithm specification.

Therefore these binaries/models must not be copied into Xiaomi Health Bridge. Their presence is useful only as architectural evidence that Mi Fitness performs some sleep/ECG processing behind proprietary native/model boundaries.

### 5. Inter-app health provider / App 間資料介面

Mi Fitness contains a bound `HealthProviderService` and an AIDL-style interface descriptor `com.mi.health_provider.IHealthProviderApi`. The readable provider converts only a small recent summary: latest heart rate, latest SpO2, abnormal high/low heart-rate items, abnormal SpO2, and a medical-aid card.

The service is protected by Xiaomi-specific permissions such as `com.xiaomi.wearable.permission.health_provider`; other providers also use Xiaomi default-read or signature/system permissions. A normal Play-distributed app signed by 王宥傑 cannot legitimately acquire those signature permissions. We must not bypass them, re-sign Mi Fitness, inject code, scrape its private database, or depend on undocumented Binder transaction codes.

## Difference from Xiaomi Health Bridge / 與本專案寫法的差異

| Area | Mi Fitness 9.8.348i | Xiaomi Health Bridge | Engineering implication |
|---|---|---|---|
| Acquisition | OEM Xiaomi/Huami SDK and proprietary Bluetooth/device stack | Wear OS Health Services, Health Connect, Android Sensor API | Our public API path is maintainable and Play-compatible, but cannot see OEM-only calibrated channels unless Xiaomi exposes them. |
| Phone/watch transport | OEM device sync manager with per-type watermarks | Google Wearable Data Layer with per-session ordering, acknowledgement and retry | Keep our transport; borrow the per-metric watermark concept, not Xiaomi protocol code. |
| Canonical store | Many per-metric Room tables with common source/time/upload/delete fields | Health Connect for standard records plus app-private raw evidence database | Continue separating standard physiological records from raw research evidence. Add a common query projection rather than duplicating Xiaomi's schema. |
| Freshness | Per-type last-sync time and metric timestamps | Explicit source/receive/persist timestamps and per-metric freshness classifier | Our evidence model is stricter. Do not replace it with a single sync time. |
| Aggregation | Rich daily/weekly/monthly repositories and report merge layer | Direct Health Connect reads plus deterministic local insight engines | Mi Fitness's report UX is worth matching independently, especially source-aware day/week/month views. |
| Sleep/ECG | Proprietary native libraries and embedded TFLite model | Public papers/datasets, transparent Kotlin research algorithms, calibration gates | Our result is auditable but must remain research-only without device-specific validation. |
| Raw evidence | Consumer reports dominate readable storage schema | Append-only raw events, provenance, replay, decoder version and quality state | Our raw-evidence design is stronger for research and debugging. Preserve it. |
| Inter-app API | Signature-protected Xiaomi provider with selected summaries | Public Health Connect permissions and explicit user consent | Use Health Connect or explicit user export/import; do not call protected Xiaomi interfaces. |
| Cloud | Upload/delete state and server request watermarks are built into records | Primarily local/device; public build does not require a Xiaomi account | Keep cloud optional. Any future cloud sync needs explicit privacy disclosure and deletion semantics. |

## What can be reused safely / 可安全利用的資料與功能概念

The following are product or architecture ideas to implement independently, not code to copy:

1. **Per-metric synchronization cursor** — maintain a source-scoped watermark for heart rate, sleep, SpO2, steps and sport independently. This prevents one successful metric from making another appear current.
2. **Source-aware composite identity** — retain metric key + source/device identity + measurement time, while continuing to use a generated record/session ID for collision handling.
3. **Timezone evidence** — preserve source time, UTC-normalized time, timezone offset and zone ID. Never overwrite source time with receive time.
4. **Offline state machine** — represent pending upload, acknowledged upload, soft deletion and final deletion separately if optional cloud backup is introduced.
5. **Day/week/month report projection** — build views from the canonical evidence store, with manual and automatic SpO2 separated and sleep segments visible.
6. **Abnormal-event UX** — present high/low heart-rate and low-SpO2 events as quality/freshness-qualified, rate-limited observations, not diagnoses.
7. **Recent-summary adapter** — expose a small, permission-checked app-owned API or export containing latest HR/SpO2 and their timestamps, rather than granting broad database access.
8. **User-authorized import** — support Mi Fitness's official export format if the user supplies an export and its license/format is documented. Import into a quarantine/staging table, validate units and timestamps, deduplicate, and mark provenance `mi_fitness_user_export`; never read Mi Fitness's private database.
9. **Capability-based UI** — show only features for which the source/API and evidence gates are available. OEM-only features remain visibly unsupported rather than filled with estimates.

## What cannot be reused / 不可直接利用

- Xiaomi/Huami SDK implementation, Bluetooth packet formats, authentication or pairing secrets.
- Signature-protected ContentProvider/Binder services or permissions.
- Decompiled Java/Kotlin source, cloud endpoints, tokens, certificates or internal transaction codes.
- Embedded TFLite/native sleep, ECG, GPS or SpO2 binaries and their outputs as if independently validated.
- Mi Fitness table names or values as proof of physical units, calibration, lead configuration, sampling rate or clinical accuracy.
- Private Mi Fitness files/database from another app sandbox, rooted-device extraction, runtime hooking or permission bypass.

## Recommended implementation plan / 建議實作順序

### P0 — retain and strengthen current architecture

- Keep public Health Services/Health Connect/Data Layer paths as the supported release architecture.
- Add a documented per-metric cursor/source projection on top of existing canonical records; do not create a second transport stack.
- Ensure day/week/month screens share the same freshness and provenance classifier as summary cards.
- Keep raw research evidence separate from measured Health Connect records.

### P1 — authorized Mi Fitness interoperability

- Obtain an official Mi Fitness data export from the user, then document its schema and license before implementing an importer.
- Prefer Health Connect when Mi Fitness or Xiaomi's system component exposes records there on the actual phone; verify data origin package, permission and timestamps at runtime instead of assuming this APK has a Health Connect client.
- Add import preview, field inventory, explicit confirmation, deduplication, deletion and rollback tests.

### P2 — independent feature parity

- Implement source-aware manual/automatic SpO2 history, sleep segment timelines, abnormal-event history and day/week/month projections using our own records.
- Validate any algorithm against the public datasets and papers already listed in `ALGORITHM_DATASET_PAPER_MAP.md`.
- Do not attempt native-model parity unless Xiaomi publishes a licensed SDK/model contract or a clean independent model is trained and validated from appropriately licensed data.

## Paper and dataset boundary / 論文與資料庫界線

### ECG visibility gates in the supplied 9.8.348i APK / 本次複核的 ECG 顯示條件

The supplied APK's reconstructed `com/xiaomi/fitness/device/manager/extensions/DeviceModelExtKt.java::getFeatureItemVisible` uses **different feature tokens** for two card IDs: `162` checks `"ecg"` while `181` checks `Feature.ECG_MEASURE_REMIND`, whose value is `"electrocardiogram"` in `export/bean/Feature.java`. The same APK's `device/manager/DeviceModelExtKt.java::isSupportFeature` tests membership of the current product's `features` list. Its separate `isEcg` helper tests product `type == 4`; that helper is not proof that either card is visible on the paired Watch 2 Pro. `ProductManagerRemoteImpl.update` conditionally merges `whitelistFeature` when its product-list switch is enabled, always merges `androidFeature`, and caches the product by model. These observations come from readable Java reconstructed by JADX 1.5.6, not from the owner's current Mi Fitness account or private product cache.

使用者提供的 9.8.348i APK 中，ECG 主卡片 `162` 讀 `ecg`，量測提醒卡片 `181` 讀 `electrocardiogram`；另外還有按產品類型 `4` 判斷的 `isEcg`，三者不能視為同一開關。產品清單可合併白名單與 Android 功能欄位，但這份 APK 沒有提供本帳號配對手錶的最終功能集合。手錶已有原廠 ECG 程式碼與啟用提示，仍不能由此斷言 Watch 2 Pro 已通過帳號、裝置、區域或醫療核准流程。可獨立實作的是「區分功能可見性、裝置量測能力、授權與原始資料品質」的狀態顯示，不能把這些欄位值直接寫成 `true` 來宣稱原廠 ECG 已開通。

The separately installed phone APK is **3.59.2i**, not this supplied 9.8.348i build. Its activation call and watch-side service flag are traced in [the paired-device audit](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md). Cross-version names and branches may differ, so do not combine them into a single alleged current execution path. No OEM ECG waveform, clinical classifier, training corpus or calibration coefficient was extracted by this card-gate trace. / 目前手機實裝 3.59.2i，不能把兩個版本的分支拼成一條已驗證的實機執行路徑；卡片判斷也沒有揭露演算法所用論文或資料庫。

Mi Fitness's proprietary model assets do not reveal a citable training paper or dataset. We must not invent one. Xiaomi Health Bridge's transparent algorithm provenance remains the authoritative public mapping:

- MIT-BIH Arrhythmia and Noise Stress Test for offline ECG detector/noise benchmarking.
- ECG-ID and PTB-XL for scale-independent ECG research and cross-dataset checks.
- Wrist PPG During Exercise for PPG-vs-ECG research benchmarking.
- Orphanidou et al. (IEEE JBHI 2015) for simplified signal-quality/template-correlation engineering gates.
- Wearable PPG reviews for motion-artifact, morphology and validation design context.

Exact DOI, implementation path, licensing and non-clinical boundary are maintained in `docs/ALGORITHM_DATASET_PAPER_MAP.md`, `docs/RESEARCH_REFERENCE_MATRIX.md`, `THIRD_PARTY_NOTICES.md`, and `research/clinical-data/registry.json`.

## Verification status / 驗證狀態

The subsequent Watch 2 Pro 4.0 review of the **separate watch-side Xiaomi Health APK**, its ECG activation guidance and the supplied phone APK's ECG authorization flow is documented in [WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md). The watch-side service flag and phone-side activation resources do not establish that this user's Watch 2 Pro is currently eligible or enabled.

- `STATIC_APK_IDENTITY = VERIFIED`
- `MANIFEST_AND_ROOM_SCHEMA = VERIFIED`
- `DEVICE_SYNC_ARCHITECTURE = VERIFIED_FROM_READABLE_METADATA_AND_CLASSES`
- `PROPRIETARY_NATIVE_ALGORITHM = NOT_INSPECTED_OR_REUSED`
- `MI_FITNESS_PERSONAL_DATA_ACCESS = NOT_PERFORMED`
- `MI_FITNESS_HEALTH_CONNECT_PATH = NOT_FOUND_IN_THIS APK BUILD`
- `WATCH_2_PRO_PROTOCOL_OR_CALIBRATION = NOT ESTABLISHED`
- `SAFE_REUSE = ARCHITECTURE_AND_USER_AUTHORIZED_INTEROP_ONLY`

This is an engineering interoperability review, not a security audit, medical validation, license opinion, or claim that every obfuscated path was recovered.
