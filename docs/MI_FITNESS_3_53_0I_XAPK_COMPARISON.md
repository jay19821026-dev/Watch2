# Mi Fitness 3.53.0i XAPK static comparison / 3.53.0i 靜態反組譯對照

Author / 作者：王宥傑 (Wang You-Jie)  
Analysis date / 分析日期：2026-11-08

## Scope / 範圍

The owner supplied `Mi+Fitness+(Xiaomi+Wear)_3.53.0i_APKPure.xapk`. This report compares its **readable, static** Android code and package metadata with the separately supplied 9.8.348i APK and AILISA Health Bridge 4.0. No Xiaomi account, personal health record, server response or paired-device product cache was read for this comparison. Decompiled Xiaomi source and binaries are not distributed in this repository. / 本報告只分析使用者提供 XAPK 的靜態套件及可讀類別，與另一份 9.8.348i APK、本專案 4.0 比較；未讀取小米帳號、個人健康資料、伺服器回應或配對裝置快取。儲存庫不散布反組譯後的原廠程式或二進位檔。

| Item / 項目 | 3.53.0i XAPK | 9.8.348i APK |
|---|---|---|
| Package / 套件 | `com.xiaomi.wearable` | `com.xiaomi.wearable` |
| Version code | `353000` | `908348` |
| minSdk / targetSdk | 26 / 35 | 26 / 34 |
| Packaging / 封裝 | Base APK plus ARM64, English and HDPI splits | Single APK with included native libraries |
| Base APK SHA-256 | `32792ABE4E58FE39F971AE12C92E58B666ABB05F1BE97A70EC8E8B2F6E31E5B1` | `42958D4589654D9A9317B9B777F0A8B3C2637B8C8B11229308F9AEA4143C2A33` |
| JADX 1.5.6 result / 反組譯 | 49,498 progress units, 464 reported errors; conclusions below use readable methods and APK metadata only | Prior report: roughly 44,154 classes, 415 reported errors |

The two version labels may belong to different release channels; their numeric order alone is not proof of a chronological feature rollout. / 兩個版號可能屬不同發行管道，不以數字大小推論功能先後。

## Verified differences / 已核對差異

| Topic | 3.53.0i observation | 9.8.348i comparison | Meaning for AILISA 4.0 |
|---|---|---|---|
| Health Connect | Manifest declares several `android.permission.health.WRITE_*` permissions. Readable `com.xiaomi.fitness.access.health_connect.HealthConnectUtils` requests write permissions for steps, oxygen saturation, heart rate, sleep, exercise, distance, active calories, elevation, speed and step cadence. `com.xiaomi.fitness.repo.healthconnect.biz.daily.BaseSyncBiz` calls `HealthConnectClient.insertRecords`; `SportSyncBiz` is a separate exercise path. | The 9.8.348i decompiled tree has no corresponding `com.xiaomi.fitness.access.health_connect` or `repo.healthconnect` module and no `HealthConnectClient` reference found in its `com.xiaomi.fitness` tree. | **New actionable path:** on a phone running a build that actually grants/syncs these records, request Health Connect access in AILISA and verify each record's `DataOrigin`, timestamp, unit, and freshness. Static writer code does not prove this owner's watch has exported any particular record. |
| Health Connect sync bookkeeping | `FitnessDatabase_Impl` adds `health_connect_sync_info`, keyed by user, data type, type, source `sid`, record key and time. It includes status, deletion state, Health Connect `recordId` / `clientRecordId`, and start/end times. | Table absent from 9.8.348i's inspected schema. | Source-aware IDs and per-record sync status are useful independent design ideas; do not copy Xiaomi's schema or infer that every watch metric is present. |
| Other local tables | This schema has 38 observed `CREATE TABLE` names, four more than the inspected 9.8.348i schema: `algo_curse`, `health_connect_sync_info`, `temperature_characteristic_record`, `temperature_trend`. | 34 observed tables. Both retain `ecg_medical_record`, `spo2_record`, `sleep_segment`, `stress_record`, blood-pressure and blood-sugar tables. | Table existence establishes data models, not actual Watch 2 Pro measurements or licensed algorithms. |
| ECG card gates | `getFeatureItemVisible` checks card 162 against product feature `ecg`; card 181 checks `Feature.ECG_MEASURE_REMIND`, whose string is `electrocardiogram`. `isSupportFeature` checks membership in the product's feature list; a separate `isEcg` helper checks product type 4. | The same three distinct checks are readable in 9.8.348i. | **No evidence of a new universal ECG unlock.** A card, reminder, product type, activation state and valid ECG record are separate. Changing a display model or boolean in AILISA cannot grant the OEM app's entitlement. |
| Product features | `ProductManagerRemoteImpl.update` merges `whitelistFeature` only when the product whitelist switch is on, merges `androidFeature`, then caches the product by model. | Equivalent structure appears in the 9.8.348i report. | An APK's feature list is not the paired Watch 2 Pro's final server/account/region/device state. Preserve unknown status when the effective product list is unavailable. |
| ECG record data | `EcgMedicalItemReport` keeps report metadata and retrieves waveform data separately through `FitnessDataGetter.getEcgRecordData`. Its constructor still has a 500 sample-frequency **default**. | Same separation and default observed in 9.8.348i. | Do not assign 500 Hz to AILISA's raw Android Sensor events; a model default is not the watch ADC clock or raw-code-to-mV calibration. |
| Native assets | Base APK contains `mi_stage_model_0_1_1.tflite`; ARM64 split contains `libjni_mi_ecg_algo.so`, `libmi_ecg_alg.so`, `libjni_xiaomi_sleep_algorithm.so`, `libsleep_detect.so`. | Corresponding assets occur in the 9.8.348i APK. | The assets do not identify a reusable algorithm, training dataset, license, Watch 2 Pro calibration, or clinical clearance. They are not copied into AILISA. |

## Paired phone cross-check / 配對手機交叉核對

The owner's currently installed Mi Fitness is a **separate 3.59.2i build** (`versionCode 359002`, `targetSdk 35`), not this 3.53.0i XAPK. A read-only package check found Health Connect write permissions for heart rate, sleep and oxygen saturation in its manifest; the package dump also showed a granted state for some user entries. This is consistent with a Health Connect integration path, but does **not** prove that a current Watch 2 Pro record was written or that AILISA has permission to read it. / 配對手機目前安裝的是另一版 3.59.2i；唯讀套件檢查可見心率、睡眠和血氧的 Health Connect 寫入權限，部分使用者狀態顯示已授權。這與整合路徑相符，但不能證明該手錶已有實際紀錄或 AILISA 已取得讀取權限。

## Practical comparison / 可採取的下一步

1. **Use the official Health Connect bridge when it is actually enabled.** On the paired phone, inspect Mi Fitness's Health Connect permission screen, grant only desired record types, sync the watch, then let AILISA read the resulting Health Connect records after the user grants AILISA permission. Confirm `DataOrigin.packageName` instead of relying on APK class names. / 在配對手機先走官方 Health Connect 授權與同步，再由使用者授權 AILISA 讀取；逐筆核對來源套件、時間及單位。
2. **Keep unsupported readings explicit.** If Health Connect has no ECG, blood pressure, glucose or temperature record, do not synthesize those values from an APK table, native library name, sensor raw field or Watch 5 feature string. / 若 Health Connect 沒有相應紀錄，不從原廠資料表、函式庫名稱、原始欄位或 Watch 5 功能字串虛構結果。
3. **Compare real capability at runtime.** AILISA 4.0 already separates Health Connect measured records from watch raw research events. A targeted device check should record available Health Connect types, permission grants, actual `DataOrigin`s and fresh record counts without publishing private values. / 以實機權限與實際來源為準，僅公開能力狀態，不公開個人數值。

No Xiaomi private API, proprietary native model, source code, authentication, or signature-restricted service is required for these steps. Public datasets and papers used by AILISA are mapped separately in [`ALGORITHM_DATASET_PAPER_MAP.md`](ALGORITHM_DATASET_PAPER_MAP.md); neither inspected APK identifies Xiaomi's training corpus. / 以上流程不需要原廠私有 API、模型、原始碼或簽章專屬服務。AILISA 使用的公開資料集與論文另有對照表；兩份 APK 都沒有證明小米模型採用哪篇論文或資料庫。
