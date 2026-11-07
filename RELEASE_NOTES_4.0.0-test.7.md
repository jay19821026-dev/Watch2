# Xiaomi Health Bridge 4.0.0 test 7 / 測試版 7

作者 / Author: 王宥傑 (Wang You-Jie)

## 繁體中文

此版依照**已安裝在這隻 Watch 2 Pro 的原廠程式**，把原始體溫研究頁的完成條件改成需有近期、時間對齊的 Android 離腕感測器「已佩戴」事件。原廠體溫量測畫面使用類型 `34`，非 `1.0f` 時停止；原廠有型別體溫結果則由服務 `20500` 回傳。本專案不取用該私有服務、校準或負數錯誤碼。離腕或佩戴狀態未知時，有限的原始值仍可保留研究，但不標示量測完成、不突出數值，也不宣稱攝氏體溫。

血氧研究頁沿用 test 6 的配戴檢查；ECG 研究頁沿用先前對原廠事件標記及離腕事件的檢查。手機 APK 與 test 5／6 相同。兩支 APK 為 **debug 簽章側載測試版，非 Google Play 正式版**。實際手腕量測與手機同步仍待驗證；請核對 SHA-256。

來源：[原廠功能稽核](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md)、[APK 與感測器拆解](docs/OEM_APK_CAPABILITY_BREAKDOWN.md)、[資料庫及論文溯源](docs/ALGORITHM_DATASET_PAPER_MAP.md)。

## English

Based on the OEM app **installed on this Watch 2 Pro**, the raw body-temperature research page now requires a recent, time-aligned Android off-body sensor event reporting on-body status before marking capture complete. The OEM temperature screen uses sensor type `34` and stops when its value differs from `1.0f`; a separate typed result comes from private service type `20500`. This project does not use that service, its calibration, or its negative error-code rule. Off-wrist or unknown status retains bounded raw research evidence without claiming measurement completion, highlighting a result, or claiming degrees Celsius.

The SpO₂ research page retains test 6's wear-state check, and ECG retains the observed packet-marker and off-body checks. The phone APK is identical to test 5/6. Both APKs are **debug-signed sideload tests, not Google Play releases**. Live on-wrist measurement and paired sync remain to be verified. Check SHA-256.

Sources: [OEM feature audit](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), [APK and sensor breakdown](docs/OEM_APK_CAPABILITY_BREAKDOWN.md), [dataset and paper provenance](docs/ALGORITHM_DATASET_PAPER_MAP.md).
