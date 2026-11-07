# Xiaomi Health Bridge 4.0.0 test 6 / 測試版 6

作者 / Author: 王宥傑 (Wang You-Jie)

## 繁體中文

此測試版包含 Android 手機與 Xiaomi Watch 2 Pro（Wear OS）APK。手錶血氧研究頁參照**這隻錶已安裝的原廠程式**修正：原廠 `MeasuringActivity` 會監看配戴感測器，量測中回報值不是 `1.0` 時停止。現在本專案在擷取公開 SpO₂ 原始事件時同步讀取 Android 離腕感測器，僅有近期、時間對齊的已佩戴事件，才把原始擷取標示完成。離腕或狀態未知時仍可保留有限的原始研究資料，但不宣稱量測完成，也不產生血氧百分比。

手錶實機列出 `mi_spo2`、`max86178_spo2` 與 `mi_wear` 通道。原廠百分比由私有服務的有型別結果提供；本 App 沒有取用其校準演算法。60 秒佩戴狀態時效是本專案的保守規則，不是原廠常數。手機 APK 與 test 5 相同。兩支 APK 都是 **debug 簽章側載測試版，不是 Google Play 正式版**；請核對 SHA-256。

證據：[Watch 2 Pro 原廠功能稽核](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md)、[APK 與實機感測器拆解](docs/OEM_APK_CAPABILITY_BREAKDOWN.md)、[資料庫及論文來源](docs/ALGORITHM_DATASET_PAPER_MAP.md)。

## English

This test release contains Android phone and Xiaomi Watch 2 Pro Wear OS APKs. The watch SpO₂ research page now reflects behavior observed in the OEM app **installed on this watch**: its measuring activity monitors a body sensor and stops an active measurement when the value differs from `1.0`. Our app now reads Android's public off-body sensor alongside raw SpO₂ events. It marks raw capture complete only when a recent, time-aligned on-body event exists. Off-wrist or unknown status retains bounded raw research evidence without claiming measurement completion or producing a saturation percentage.

The live watch advertises `mi_spo2`, `max86178_spo2`, and `mi_wear` channels. The OEM percentage comes from a typed private service result; this app does not use its calibration algorithm. The 60-second contact freshness window is this project's conservative policy, not an OEM constant. The phone APK is identical to test 5. Both APKs are **debug-signed sideload tests, not Google Play releases**. Verify SHA-256.

Evidence: [Watch 2 Pro OEM feature audit](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), [APK and live sensor breakdown](docs/OEM_APK_CAPABILITY_BREAKDOWN.md), [dataset and paper provenance](docs/ALGORITHM_DATASET_PAPER_MAP.md).
