# Xiaomi Health Bridge 4.0.0 test 5 / 測試版 5

作者 / Author: 王宥傑 (Wang You-Jie)

## 繁體中文

此測試版提供 Android 手機及 Xiaomi Watch 2 Pro（Wear OS）APK。手機 ECG 研究頁新增原廠啟用路徑說明：已安裝的 Mi Fitness 會從原廠產品設定取得功能清單，ECG 卡片會檢查其中的 `ecg` 項目。手錶端另有獨立的原廠啟用狀態。這些資訊不會改變本 App 已可使用的原始 Android ECG 感測器路徑。

手錶 APK 與 test 4 相同，沿用有事件標記和離腕狀態檢查的未校準 ECG 研究預覽。實機已宣告 ECG、PPG、BioZ、血氧及溫度相關通道；這只證明可見的感測器路徑，未證明原廠報告、校準或所有功能均已解鎖。兩支 APK 都是 **debug 簽章側載測試版，不是 Google Play 正式版**。請先核對 SHA-256。

詳細證據：[原廠 APK 功能拆解](docs/OEM_APK_CAPABILITY_BREAKDOWN.md)、[Watch 2 Pro 功能稽核](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md)、[資料庫與論文溯源](docs/ALGORITHM_DATASET_PAPER_MAP.md)。

## English

This test release includes Android phone and Xiaomi Watch 2 Pro Wear OS APKs. The phone ECG research page now explains the observed OEM activation path: the installed Mi Fitness app obtains product features from Xiaomi's product configuration, and its ECG card checks for `ecg`. A separate activation state exists in the watch service. Neither gate changes this project's existing access to the raw Android ECG sensor path.

The watch APK is identical to test 4 and retains the uncalibrated ECG research preview with event-marker and off-body checks. The live watch advertises ECG, PPG, BioZ, SpO₂-related and temperature-related channels. This establishes sensor-path visibility, not OEM reports, calibration, or universal feature availability. Both APKs are **debug-signed sideload tests, not Google Play builds**. Verify SHA-256 before updating.

Evidence: [OEM APK capability breakdown](docs/OEM_APK_CAPABILITY_BREAKDOWN.md), [Watch 2 Pro feature audit](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), [dataset and paper provenance](docs/ALGORITHM_DATASET_PAPER_MAP.md).
