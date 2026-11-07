# Xiaomi Health Bridge 4.0.0 test 8 / 測試版 8

作者 / Author: 王宥傑 (Wang You-Jie)

## 繁體中文

依照**已安裝在 Watch 2 Pro 的原廠手錶 APK**，原廠壓力量測由服務類型 `20486` 回傳有型別結果；量測頁使用離腕感測器，離腕時退出量測。這裡的 `PRESS` 是心理壓力指數，**不是血壓或氣壓**。

本專案的光學 HRV「研究壓力分數」仍使用專案自訂公式，沒有移植小米分數或私有服務。test 8 新增近期、時間對齊的已佩戴狀態檢查，並要求光學波形通過品質檢查及至少五筆 RR 間隔，才顯示與同步該分數。離腕或狀態未知時仍保存有限原始資料，但不產生研究壓力分數。手機頁面也明示分數不是原廠結果，並修正一段把已停用 PPG 血壓估算寫成可用的舊說明。

演算法係數 `0.20`、`70/30`、`50`、`15` 是本專案啟發式選擇，**沒有對應的訓練資料庫、原廠公式或臨床驗證論文**；完整來源界線見[資料庫與演算法溯源](docs/ALGORITHM_DATASET_PAPER_MAP.md)。ECG、血氧與體溫沿用先前研究版的原廠行為對照。手機與手錶 APK 均為 **debug 簽章側載測試版，非 Google Play 正式版**。手腕上實際量測、離腕切換及手機同步尚待驗證。

## English

The Xiaomi Health APK **installed on this Watch 2 Pro** starts a typed stress measurement through service `20486` and leaves the measuring state when its off-body sensor reports off-wrist. Its `PRESS` label denotes psychological stress, **not blood pressure or atmospheric pressure**.

Our HRV-derived research stress score remains a project-authored heuristic. Test 8 now displays and syncs it only with a recent time-aligned on-body event, a valid optical waveform, and at least five RR intervals. Off-wrist or unknown-state raw samples remain bounded research evidence without a stress score. The phone labels the score as non-OEM and corrects outdated blood-pressure copy.

The formula coefficients `0.20`, `70/30`, `50`, and `15` are project choices. **No dataset, Xiaomi formula, or clinical validation paper supplies them.** See the [algorithm and dataset provenance](docs/ALGORITHM_DATASET_PAPER_MAP.md). Earlier ECG, SpO₂, and temperature research checks remain. Both APKs are **debug-signed sideload tests, not Google Play releases**. Live on-wrist, off-wrist transition, and paired-sync behavior still need verification.

Source traces: [Watch 2 Pro OEM audit](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md) and [APK capability breakdown](docs/OEM_APK_CAPABILITY_BREAKDOWN.md).
