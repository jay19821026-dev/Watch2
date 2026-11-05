# Algorithm, dataset, and paper provenance / 演算法、資料庫與論文溯源

作者 / Author: 王宥傑 (Wang You-Jie)  
最後更新 / Last updated: 2026-11-06

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

## Runtime algorithms / App 執行期演算法

- `health-engine/.../EcgCoreResearchEngine.kt`: quality-gated research ECG processing；映射與時基證據不足時鎖住 R-peak/RR。
- `mobile/.../SensorHealthAlgorithms.kt`: waveform quality、簡化 Orphanidou-style template correlation、填充值排除與工程摘要。
- `mobile/.../BiomedicalCalibration.kt`: ECG/PPG/BioZ/溫度證據閘門；映射、取樣時鐘、增益或校準未驗證時封鎖生理單位。
- `mobile/.../AlgorithmicInsightsEngine.kt`: 確定性彙整已授權的 Health Connect 紀錄；執行時不查詢或訓練 PhysioNet。
- `benchmark_ecg_qrs.py` / `benchmark_wrist_ppg.py`: 可重現離線 benchmark；Android App 不會呼叫。

## Data location, licenses, and prohibited claims / 資料位置、授權與禁限

下載資料放在 git-ignored `.local-research-data/`。ODC Attribution 1.0 資料庫保留其授權及署名要求；restricted datasets 需自行接受 data-use agreement。完整網址及機器可讀 metadata 見 `THIRD_PARTY_NOTICES.md` 與 `research/clinical-data/registry.json`。

No similarity, benchmark score, SQI, or plausible waveform establishes Xiaomi Watch 2 Pro clinical accuracy, OEM HAL mapping, calibrated mV/SpO₂/BioZ/temperature, AFib detection, diagnosis, treatment advice, or emergency monitoring. 這些結論需要裝置專屬契約、可追溯參考量測、預先定義驗證與適用監管證據。
