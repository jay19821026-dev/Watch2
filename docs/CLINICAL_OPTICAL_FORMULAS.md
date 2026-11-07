# 臨床光學／感測公式導入（研究級）

更新日期：2026-11-08

本文件區分公開文獻中的通用指標定義與**本專案自行設定的啟發式公式**。一律標示研究用途；無裝置級校準與驗證時不輸出醫療診斷。原廠 APK 只用於辨識服務和佩戴流程，不提供本專案公式的臨床效度。

## 光學 PPG／脈搏血氧

| 公式 | 內容 | 本 App 行為 |
|---|---|---|
| 灌注指數 | \(PI\% = 100 \cdot AC/DC\) | 輸出 `formula_pi_percent` |
| 比値法 | \(R=(AC_{red}/DC_{red})/(AC_{IR}/DC_{IR})\) | 雙波長齊備才算 R；**不**用未校準 \(a-bR\) 轉 SpO₂% |
| 脈率 | 波峰間距 → bpm | `formula_pulse_bpm` |

參考：標準脈搏血氧調變比／Beer–Lambert 推導；Masimo 等 PI 定義。

## HRV／心律不整篩檢

| 指標 | 公式 | 來源 |
|---|---|---|
| SDNN | RR 標準差 | 短時 HRV 文獻 |
| RMSSD | \(\sqrt{\mathrm{mean}(\Delta RR^2)}\) | 短時 HRV；穿戴 AF 特徵 |
| nRMSSD | \(RMSSD/\overline{RR}\) | Sci Rep 2019 腕式 PPG AF 等 |
| 研究壓力分數 | \(0\!-\!100\)：高 nRMSSD → 較低分數；另加心率項 | **本專案自訂啟發式**，沒有對應的訓練資料庫、原廠公式或臨床驗證論文；不是小米 `20486` 結果。係數與限制見[演算法溯源](ALGORITHM_DATASET_PAPER_MAP.md)。 |
| pNN50 | \(\|\Delta RR\|\ge 50\,\mathrm{ms}\) 比例 | 短時 HRV |
| CV(RR)、CV(ΔRR) | Tateno & Glass 2001 | 研究級可能 AF／不規則 |

實作：`ClinicalSensorFormulas`、`WearRhythmScreening`、`WearHrvActivity`。

## 氣壓高度

\[
h = 44330.77 \left(1 - \left(\frac{P}{1013.25}\right)^{0.190266}\right)
\]

ISA 標準大氣相對高度；非測繪級校準。

## 光學／無袖帶血壓

手錶端目前不推算 mmHg：PPG／脈率與沒有同步時鐘的 ECG 不足以校準血壓。舊的族群係數估算器已從 Wear 生產程式移除，避免日後誤接到產品畫面。手機端只分類使用者輸入的袖帶或 Health Connect 讀值；這不代表手錶血壓量測功能。

ACC/AHA 分類只用於有來源與時間資訊的已記錄血壓讀值。若未來要恢復任何無袖帶估算，必須先另建裝置專屬同步參考研究、獨立臨床驗證與監管路徑，不可沿用舊人口係數或單次袖帶偏移。

## SQI

Orphanidou et al., IEEE JBHI 2015：ECG／PPG 模板相關門檻（既有 `SensorHealthAlgorithms`）。

## 他錶對照

手機 `CompetitorParityActivity`：Apple／Samsung／Google 功能 vs 本 App 狀態。
