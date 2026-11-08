# AILISA vs Mi Fitness: comparative validation / 與原廠比較的驗證計畫

作者 / Author: 王宥傑 (Wang You-Jie)  
更新 / Updated: 2026-11-08

## Claim boundary / 結論界線

Public university, hospital, PhysioNet and FDA resources can supply **reference signals, annotations, methods and study designs**. They cannot establish that AILISA is more accurate than Mi Fitness on a Watch 2 Pro without matched watch measurements and an independent reference. The inspected APKs expose data handling but not Xiaomi's training corpus, calibration constants or full validation results. No overall superiority claim is made. / 公開資料可供方法開發與離線測試，不能單靠文獻宣稱本手錶 App 的量測優於原廠。

## Three separate comparisons / 三種不可混用的比較

| Level | Inputs | Valid conclusion | Current state |
| --- | --- | --- | --- |
| Code-path equivalence / 程式路徑 | Decompiled Mi Fitness 9.8.348i/3.59.2i and AILISA source | Field mapping, timestamp, chart bucket and source-handling differences | Partially traced in [data-window audit](OEM_DATA_WINDOW_COMPARISON_4_0.md); OEM proprietary computation remains opaque. |
| Offline public benchmark / 公開資料離線基準 | Dataset signals and independent labels | Performance of a **specified offline algorithm on that dataset** | MIT-BIH QRS and a limited wrist-PPG baseline have results in [provenance map](ALGORITHM_DATASET_PAPER_MAP.md); neither tests Watch 2 Pro or Mi Fitness. |
| Paired device comparison / 同時段實機比較 | Watch 2 Pro AILISA and Mi Fitness outputs, synchronized reference instrument, motion/contact/context and excluded-run log | Device- and condition-specific error, coverage and failure comparison | **Not performed**. No paired reference ECG/oximeter/thermometer/body-composition criterion dataset from this watch is available. |

## Metric-specific reference plan / 指標與參考標準

| Metric | Proposed independent reference and endpoints | Preconditions before a numerical or diagnostic claim |
| --- | --- | --- |
| Heart rate / 心率 | Simultaneous chest ECG R peaks; MAE and error distribution in bpm, output coverage, latency, motion strata and unreadable rate. | Match sample clocks and intervals; compare AILISA and OEM on exactly the same periods and people; hold out people and activities. PhysioNet [Wrist PPG During Exercise](https://physionet.org/content/wrist/1.0.0/) provides an **offline** wrist PPG + motion + chest ECG example on other hardware, not this device's calibration. |
| SpO₂ / 血氧 | An appropriate calibrated reference protocol and paired readings; bias, limits of agreement, coverage and failure rate across the intended saturation range. | Verify red/IR/raw-to-percent mapping and measurement mode first. A typed Health Connect percentage is an OEM/app output, **not** an independent reference; do not use the OEM value as both target and truth. |
| ECG / 心電圖 | Traceable timing/amplitude reference and expert-adjudicated paired rhythm labels; sensitivity/specificity with confidence intervals, unreadable rate and subgroup analysis. | Establish Watch 2 Pro lead path, raw-code→mV, sampling clock and signal quality. FDA's [DEN180044 review](https://www.accessdata.fda.gov/cdrh_docs/reviews/DEN180044.pdf) illustrates adjudicated-dataset algorithm testing for a different ECG device; its results cannot be inherited. |
| Sleep / 睡眠 | Simultaneous polysomnography or suitably validated EEG stages; total sleep time error, wake sensitivity, stage confusion and missing-output rate by night. | Compare the same nights; distinguish bed-to-wake session span from scored sleep and missing stages. |
| Temperature / 溫度 | Matched measurement site and calibrated thermometer; bias, limits of agreement and ambient/contact strata. | OEM `bodyTemp` and `skinTemp` are separate; AILISA's HC wrist `BodyTemperatureRecord` is not automatically either OEM stream or core temperature. |
| BioZ/body composition / 阻抗與體成分 | Verified electrode configuration and impedance calibration; a body-composition criterion such as a four-compartment method for any body-fat claim. | Raw BioZ codes and contact state do not yield ohms or body-fat percentage without device-specific gain/frequency/electrode evidence. |

## Comparison protocol / 執行規則

1. Pre-register the intended use, devices/firmware, people, activities, reference method, endpoints, acceptance limits and exclusions **before** seeing outcomes. / 先登錄目標、設備、標準、閾值與排除條件。
2. Capture synchronized OEM, AILISA and reference output on the **same person and interval**. Preserve raw time, timezone, data origin, record and series-sample counts, recording method, on-body/contact status and failed/empty sessions. / 同人同時段擷取，保留來源、時間與失敗樣本。
3. Use participant-separated development and locked test sets. Report all attempts, not just successful outputs; calculate paired difference in error or coverage with uncertainty. / 依人切分，鎖定測試集，所有嘗試都納入涵蓋率。
4. Keep public datasets in their own offline benchmark column. For example, PhysioNet's [MIMIC-III-Ext-PPG](https://physionet.org/content/mimic-iii-ext-ppg/1.0.0/) is curated ICU waveform data at 125 Hz, not wrist Watch 2 Pro data; [MIMIC-IV-ECG](https://physionet.org/content/mimic-iv-ecg/1.0/) notes clock and population limits. / 公開資料集不能替代本裝置配對驗證。
5. A claim of “better than OEM” is allowed only for the **pre-specified metric, conditions and endpoint** where paired held-out results support it. A gain in offline benchmark accuracy or UI clarity alone does not establish better clinical or sensor performance. / 僅在預先定義且實測支持的項目與條件下，比較優劣。

No external dataset is bundled in this project. Follow each dataset's access and attribution terms; see [third-party notices](../THIRD_PARTY_NOTICES.md). / 本專案不內附外部資料檔，須依各資料源授權使用。
