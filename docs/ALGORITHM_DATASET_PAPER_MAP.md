# Algorithm, dataset, and paper provenance / 演算法、資料庫與論文溯源

作者 / Author: 王宥傑 (Wang You-Jie)
最後更新 / Last updated: 2026-11-08

本文件說明資料來源、使用的演算法及程式位置。公開資料庫只供離線研究工具使用，不包含在 App、APK 或 Git 儲存庫；App 即時資料來自使用者授權的 Health Connect、Wear OS Health Services 或裝置感測器。

This document identifies data sources, algorithms, and implementations. Public datasets are used only by offline research tools and are not bundled in the app, APKs, or repository. Runtime data comes from user-authorized Health Connect, Wear OS Health Services, or device sensors.

## Traceability matrix / 溯源對照表

| Dataset or paper / 資料庫或論文 | Algorithm or use / 演算法或用途 | Implementation / 程式位置 | Boundary / 限制 |
|---|---|---|---|
| MIT-BIH Arrhythmia v1.0.0; DOI `10.13026/C2F305`; Moody & Mark (2001), DOI `10.1109/51.932724` | WFDB **XQRS/GQRS** 對 `atr` 標註；150 ms 一對一節拍比對；靈敏度與陽性預測值。 | `research/clinical-data/benchmark_ecg_qrs.py`, `test_benchmark_ecg_qrs.py`, `registry.json` | 只驗證離線 detector；不驗證手錶 HAL、腕式導聯、mV、AFib 或臨床準確度。 |
| MIT-BIH Noise Stress Test v1.0.0; DOI `10.13026/C2HS3T`; Moody, Muldrow & Mark (1984) | baseline wander、muscle artifact、electrode-motion 的描述性雜訊比較。 | `docs/ECG_PUBLIC_DATABASE_COMPARISON_2026-10-08.md` 與 registry metadata | 最近特徵距離不是診斷分類器，不能判定手錶實體雜訊根因。 |
| ECG-ID v1.0.0; DOI `10.13026/C2J01F` | 尺度無關 ECG 頻譜與形態研究參考。 | `research/clinical-data/` registry 與離線比較流程 | 不是 Xiaomi 校準檔、導聯等效證據或 AF 陰性對照。 |
| Wrist PPG During Exercise v1.0.0; DOI `10.13026/C2PQ1X`; Jarchi & Casson, *Data* 2017, DOI `10.3390/data2010001` | 腕式 PPG 心率方法對胸前 ECG R-peak 標註；動作 session 比較。 | `benchmark_wrist_ppg.py`, `test_benchmark_wrist_ppg.py` | 八位參與者與不同硬體不能證明 Xiaomi 準確度、校準或族群表現。 |
| Orphanidou et al., IEEE JBHI 2015, DOI `10.1109/JBHI.2014.2338351` | 簡化平均 beat/pulse template-correlation SQI；參考門檻 ECG `0.66`、PPG `0.86`。 | `ClinicalReferenceTables.kt`, `SensorHealthAlgorithms.kt`, `BiomedicalCalibration.kt`, `health-engine/` tests | 簡化工程 SQI，不是論文完整驗證系統或醫療品質認證；只用於拒絕／標示研究輸出。 |
| PTB-XL (PhysioNet) | 選用的跨資料庫尺度無關統計與泛化檢查。 | registry/research workflow；資料由使用者下載至 `.local-research-data/` | 12 導程臨床 ECG 不等於腕式單導程／不透明 HAL event。 |
| Charlton et al., wearable PPG review, PMC7612541 | PPG morphology、motion artifact、signal quality 與驗證設計背景。 | 研究文件與工程邊界；未複製臨床 classifier | Review 不能提供通用腕式校準或醫療宣稱。 |
| Takazawa PMID 9719069; J-SHIPP PMID 26911232; VascAgeNet PMID 34951543 | 探索性 vascular-age 研究背景。 | `ResearchClinicalEvidence.kt` 與 health-analysis 文件 | 不宣稱本裝置或個人已有驗證的血管年齡。 |
| MAX86178 datasheet / MAXREFDES106 | ECG/PPG/BioZ AFE 能力與校準缺口分析。 | `research/max86178-calibration-evidence.md`, `docs/MAX86178_CALIBRATION_EVIDENCE_2026-10-10.md` | 不證明零售手錶的寄存器、導聯、增益、時鐘或 raw-code→mV；不得移植 MAX30003 公式。 |
| Installed Xiaomi Health watch APK, SHA-256 `920459E3…A4F83B5965` | **程式結構對照，非論文或公開資料庫**：ECG 65573 事件的前 10 欄、索引 10 標記、離腕條件與 native 分析邊界。 | `wear/.../EcgEventPreview.kt`, `WearEcgActivity.kt`; detailed evidence in `WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md`. | 只修正研究預覽的欄位選擇；未移入原廠程式碼／函式庫，未取得其訓練資料、校準、醫療驗證或使用權。 |

## Runtime algorithms / App 執行期演算法

- `health-engine/.../EcgCoreResearchEngine.kt`: quality-gated research ECG processing；映射與時基證據不足時鎖住 R-peak/RR。
- `mobile/.../SensorHealthAlgorithms.kt`: waveform quality、簡化 Orphanidou-style template correlation、填充值排除與工程摘要。
- `mobile/.../BiomedicalCalibration.kt`: ECG/PPG/BioZ/溫度證據閘門；映射、取樣時鐘、增益或校準未驗證時封鎖生理單位。
- `mobile/.../AlgorithmicInsightsEngine.kt`: 確定性彙整已授權的 Health Connect 紀錄；執行時不查詢或訓練 PhysioNet。
- `benchmark_ecg_qrs.py` / `benchmark_wrist_ppg.py`: 可重現離線 benchmark；Android App 不會呼叫。
- `wear/.../EcgEventPreview.kt`: 依目前手錶上原廠 APK 的靜態行為，僅將符合 16 欄且索引 10 標記為 1000 的原始事件前 10 欄展示為**未校準候選值**；研究頁明示尚無同步離腕狀態、可信 mV 或節律判讀。此行為來源是 APK 對照，不是公開論文，也不代表取得原廠演算法。

## Newly reviewed external sources / 新查核的外部來源

These sources were checked on 2026-11-07. They are **not imported into the app or its offline benchmark** unless a future change records the exact data version, access rights, method, code path and reproducible result. 以下來源已查核，但**尚未**作為本 App 訓練資料、測試結果或裝置校準；不能把「找到資料庫」寫成「已驗證本手錶」。

| Institution / 機構 | Primary source / 原始來源 | Possible research use / 可評估用途 | Access and device boundary / 存取與裝置限制 |
|---|---|---|---|
| PhysioNet / Cambridge–Oldenburg researchers | [MIMIC-III-Ext-PPG v1.0.0](https://physionet.org/content/mimic-iii-ext-ppg/1.0.0/), DOI `10.13026/nmwb-6h34`; authors Moulaeifard, Charlton, Strodthoff (2026) | ICU PPG signal quality, rhythm and respiratory benchmark design | Credentialed access, training and data-use agreement required. Bedside ICU signals do not establish wrist sensor accuracy or Xiaomi ECG/SpO₂ calibration. Version 1.0.0 is not the current latest version; pin a version before analysis. |
| Seoul National University Hospital / VitalDB | [VitalDB open dataset and API](https://api.vitaldb.net/) and [API reference](https://api.vitaldb.net/docs/) | Independently test waveform processing on intraoperative ECG/PPG and numeric records | Hospital operating-room devices and patient setting differ from Watch 2 Pro; verify track-level availability, license and patient grouping before a benchmark. No VitalDB data is bundled or used at runtime. |
| PhysioNet | [Pulse Transit Time PPG Dataset v1.1.0](https://physionet.org/content/pulse-transit-time-ppg/1.1.0/) | Multi-site synchronized PPG/ECG/inertial/pressure timing experiments | External research sensors and site-specific setup; PTT is not a watch blood-pressure or oxygen-calibration formula. |
| FDA / openFDA | [Device adverse event API](https://open.fda.gov/apis/device/event/) | Safety surveillance context and device-report terminology | MAUDE reports are submitted adverse-event reports, not performance validation, an algorithm training set or a Watch 2 Pro authorization record. No live openFDA query is in the app. |
| FDA / CDRH | [2025 draft pulse-oximeter performance guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/pulse-oximeters-medical-purposes-non-clinical-and-clinical-performance-testing-labeling-and) and [FDA pulse-oximeter overview](https://www.fda.gov/medical-devices/products-and-medical-procedures/pulse-oximeters) | Plan a future paired reference study with skin-pigmentation and other accuracy factors considered | The January 2025 document is **draft, not for implementation**. It does not approve this app or supply Watch 2 Pro coefficients. FDA notes possible inaccuracies from circulation, pigmentation, skin thickness, temperature and other factors. |
| Beth Israel Deaconess Medical Center / PhysioNet | [BIDMC PPG and Respiration v1.0.0](https://physionet.org/content/bidmc/1.0.0/), DOI `10.13026/C2208R`; associated paper Pimentel et al., *IEEE TBME*, DOI `10.1109/TBME.2016.2613124` | Compare PPG-derived respiratory-rate methods against annotated hospital impedance respiration; inspect ECG/PPG/SpO₂ numerics provenance | 53 hospital recordings, each eight minutes; the monitor's SpO₂ numerics do not calibrate a Watch 2 Pro raw optical channel. Open under ODC Attribution 1.0; dataset not yet benchmarked by this project. |
| University of Michigan / PhysioNet | [Wrist motion, heart rate and PSG sleep labels v1.0.0](https://physionet.org/content/sleep-accel/1.0.0/), DOI `10.13026/hmhs-py35`; Walch et al., *Sleep* (2019) | Study evaluation design for sleep-stage models using wrist motion/PPG heart rate versus polysomnography | 31 subjects and Apple Watch acquisition; registry metadata exists, but the app has no validated Xiaomi sleep-stage classifier and this dataset has not been run through a benchmark here. |
| UCI Machine Learning Repository / Reiss et al. | [PPG-DaLiA](https://archive.ics.uci.edu/dataset/495/ppg-dalia), DOI `10.24432/C53890` | Candidate motion-aware wrist PPG heart-rate benchmark with chest ECG reference | 15 subjects and other devices; CC BY 4.0. A registry entry does not mean results were reproduced or Watch 2 Pro sensor output was calibrated. |
| PhysioNet / multi-institution wearable researchers | [Auricle v1.0.0](https://physionet.org/content/auricle/1.0.0/), DOI `10.13026/swme-vp69` (published 2026-10-13) | Candidate multimodal cardiovascular and hemodynamic method comparison | Review its exact sensors, access rights, subject split and reference labels before any use; not an OEM mapping, Watch 2 Pro calibration dataset or current app dependency. |
| FDA / openFDA | [Device 510(k) clearances API](https://open.fda.gov/apis/device/510k/) and [FDA searchable 510(k) database](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfPMN/pmn.cfm) | Check exact device/manufacturer regulatory records before a future medical-device statement | A clearance for a different product cannot be transferred to this app or watch. No verified 510(k) record for this project is asserted, and the app performs no live FDA query. |

Research coverage remains open-ended: university, hospital, regulator and manufacturer resources are catalogued by concrete relevance and access rights, not asserted to have been exhaustively searched. A dataset enters the active traceability matrix only after an actual reproducible analysis and rights review. / 搜尋範圍持續擴充；只有完成授權與可重現分析，才會列為「已使用」。

## Data location, licenses, and prohibited claims / 資料位置、授權與禁限

下載資料放在 git-ignored `.local-research-data/`。ODC Attribution 1.0 資料庫保留其授權及署名要求；restricted datasets 需自行接受 data-use agreement。完整網址及機器可讀 metadata 見 `THIRD_PARTY_NOTICES.md` 與 `research/clinical-data/registry.json`。

No similarity, benchmark score, SQI, or plausible waveform establishes Xiaomi Watch 2 Pro clinical accuracy, OEM HAL mapping, calibrated mV/SpO₂/BioZ/temperature, AFib detection, diagnosis, treatment advice, or emergency monitoring. 這些結論需要裝置專屬契約、可追溯參考量測、預先定義驗證與適用監管證據。
