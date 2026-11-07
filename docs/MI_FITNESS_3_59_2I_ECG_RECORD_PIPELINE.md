# Mi Fitness 3.59.2i ECG record path / ECG 紀錄路徑

作者 / Author: 王宥傑 (Wang You-Jie)  
檢查日期 / Reviewed: 2026-11-08

## Scope / 範圍

This is a **static code trace** of the Mi Fitness `3.59.2i` base APK installed on the owner's paired Android phone, SHA-256 `D7B0A2B45E12899D91F9323BD59DAD8E3531C490CBF1BC83345F5631929CFBE2`. It does not read the app's private database or any person's ECG record. The inspected local tree is `MiFitness-phone-installed-3.59.2i/decompiled/sources` outside this repository. Method names and branch behavior are observations from reconstructed Java, not a published Xiaomi API specification.

這是已配對手機上 Mi Fitness `3.59.2i` 的**靜態程式追蹤**，沒有讀取 App 私有資料庫或任何個人的心電圖紀錄。下表只描述程式路徑，不代表本人的 Watch 2 Pro 已通過原廠 ECG 啟用或可取得正式紀錄。

## OEM trace / 原廠流程

| Stage / 階段 | Decompiled source / 反組譯位置 | Finding / 觀察 |
|---|---|---|
| Device data dispatch / 資料分流 | `com/xiaomi/fit/fitness/device/mi/parse/FitnessDataProcessor.java`, `recordParsedDataResult`, case `11` | Reads `FitnessParseResult.ecgMeasureResult`. If present, calls `MiParsedDataRecorder.recordEcg(sid, dataId, originalBytes, result)`. A parse failure produces an error. This is a device record path, separate from Android `SensorEvent.values`. |
| Binary report / 二進位報告 | `parser/daily/EcgMeasureParser.java`, `nativeParse` | Reads a little-endian binary body. The report fields are checked against `dataValid`; recognized fields include data source, start time, duration, sample frequency, average HR, analysis result and symptom bits. A repeated source-data field yields a `List<Float>`. |
| Schema report / 結構化報告 | `parser/schema/daily/EcgMeasureParser.java` | Alternative schema route maps `timestamp`, `samplingRate`, `measurementDuration`, `heartRates`, `avgHeartRate`, data source, analytical result and symptom fields into `EcgMeasureResult`. Unknown keys are retained as extra fields. |
| Record acceptance / 紀錄收件 | `parser/data/EcgMeasureResult.java`, `isInvalid`; `device/mi/parse/utils/MiParsedDataRecorder.java`, `recordEcg` | Rejects a record when declared duration is **under 25 seconds**, sample frequency is nonpositive, or sample count divided by that frequency is **under 25 seconds**. This is a format/length gate; it does not establish electrode quality, accuracy or medical validity. |
| Separate persistence / 分離儲存 | `MiParsedDataRecorder.recordEcg` | Creates an `EcgMedicalItemReport` with time, duration, sample frequency, average HR, symptoms, data source and analysis result. It stores the report separately from the original binary FDS data, keyed by device/source identity and record ID, then notifies the health page. The app's private storage is not read here. |
| Display retrieval / 顯示取用 | `impl/internal/FitnessFDSDataGetter.java`, `getEcgRecordData`; `com/mi/fitness/ecg/EcgFilterAlgo.java` | Reloads original record bytes, parses again, feeds samples, **record-provided** frequency and data source to an OEM/native filter, then divides filtered output by `1000.0f` for its returned display array. The code alone does not prove the physical unit of that returned array or disclose the native filter's algorithm. |

The `25`-second rule is observed only for an **OEM record containing its own declared duration, frequency and source-data list**. Applying it to our separate Sensor API events would require a validated mapping from each event to samples and a verified point clock, which we do not have. The official parser's `analysisResult` is device-record metadata and is not computed by Xiaomi Health Bridge.

The installed **watch** APK follows a different, earlier path: `we/b.java` (`EcgSensorUtil.kt`) counts ECG sensor callbacks and does not forward its first 20 to the native algorithm. Our raw capture retains those callbacks for provenance; the next research-preview source revision omits them only from the drawn candidate line. This watch-side warm-up is not the phone parser's 25-second record-acceptance rule. / 手錶端原廠程式先略過 20 個感測回呼；本專案保留原始資料，只在研究預覽省略。它與手機端 25 秒收件條件不同。

上述 25 秒條件只適用於**帶有原廠報告欄位的紀錄**。不能把 4.0 的原始感測事件湊滿 25 秒就視為正式心電圖，也不能把原廠顯示路徑的 `/1000` 直接當成本專案的 raw code→mV 校準。

## Comparison with Xiaomi Health Bridge 4.0 / 與本專案 4.0 比較

| Concern / 項目 | OEM phone record / 原廠手機紀錄 | Our public path / 本專案公開介面 |
|---|---|---|
| Input | Device-supplied binary or schema record with typed report and source data. | Watch Android sensor type `65573` / `com.google.wear.sensor.ecg`, containing raw `SensorEvent.values` packets. |
| Timebase | Report includes sample frequency and measurement duration; record ID has timestamp and time-zone offset. | Stores bounded event rows, sensor timestamps and callback timing. No proven per-point ADC clock or OEM report frequency. |
| Validity | Report field-validity bits and the 25-second format/length gate. | Checks packet width/finite values, OEM-observed field-10 marker and time-aligned on-body state for a **research preview**. This does not establish electrode contact. |
| Processing | OEM native filter and analysis result; implementation and calibration remain proprietary. | No OEM native binary, report injection, official analysis result or assumed `/1000` conversion. Research decoder keeps unverified raw rows separate from clinically usable waveforms. |
| Storage and display | Private report record and original FDS bytes, then filtered display data. | Own app storage and Wearable Data Layer snapshots with source and freshness; separate from Mi Fitness data and Health Connect attribution. |

**4.0 implementation consequence / 實作結論：** Keep OEM record metadata and Android raw event metadata in separate namespaces. Show a sample rate only when its source is known and mark HAL event timing as an estimate, not the OEM `samplingRate`. Do not promote raw events to an official ECG record, reuse the OEM native filter, interpret OEM analysis codes, or claim mV without device-specific evidence. The 4.0 research preview follows this separation; its next source change also breaks the drawn candidate trace when sensor event timestamps are absent, nonincreasing or more than one second apart, rather than joining disconnected sessions into a continuous line. **One second is this project's display rule, not an OEM ADC interval or ECG acceptance rule.** The phone ECG explanation in test 9 further distinguishes OEM activation from our raw capture. A future importer would need an explicitly authorized, documented export format and independent validation before merging record types.

## Limits and references / 限制與參考

- Code presence does not prove the paired Watch 2 Pro can obtain an OEM ECG record. The watch showed activation guidance; the owner reports no Mi Fitness ECG card. See [Watch 2 Pro OEM audit](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md).
- No Xiaomi database contents, APK bytecode, native library or user ECG data are redistributed here.
- Public ECG datasets and papers are **comparison/validation references**, not sources of Xiaomi's proprietary filter or this watch's calibration. See the [dataset → paper → algorithm map](ALGORITHM_DATASET_PAPER_MAP.md) and [third-party notices](../THIRD_PARTY_NOTICES.md).
