# Xiaomi Health Bridge 4.0.0 test 2 / 測試版 2

作者 / Author: 王宥傑 (Wang You-Jie)

## 繁體中文

此版本包含 Android 手機與 Xiaomi Watch 2 Pro（Wear OS）測試 APK。手錶 ECG 研究頁根據**目前手錶上實際安裝的 Xiaomi Health APK** 靜態分析，更新原始事件預覽：只對最近連續、符合 16 欄格式且第 11 欄標記為 1000 的事件，顯示前 10 欄未校準候選值。非符合事件會中斷圖形，不連接缺口。原始資料仍完整保留於既有有界擷取流程。

原廠 ECG 畫面目前顯示須透過手機 Mi Fitness ECG 卡片啟用。此測試版沒有變更原廠啟用狀態、沒有原廠 native 演算法，也不提供 mV、房顫或診斷。血氧原廠百分比來自原廠量測服務；本 App 的 raw HAL 值不能直接視為百分比。

安裝：先在手機與手錶分別安裝對應 APK，允許所需權限；需自行確認兩端配對與資料同步。這是 debug 簽章測試版，**不是 Google Play 正式版本**。

來源與限制：[原廠功能稽核](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md)、[資料庫及論文溯源](docs/ALGORITHM_DATASET_PAPER_MAP.md)、[隱私政策](docs/PRIVACY_POLICY.zh-TW.md)。

## English

This test release contains Android phone and Xiaomi Watch 2 Pro Wear OS APKs. The watch ECG research view now follows an observation from the **exact Xiaomi Health APK installed on this watch**: it previews the first ten uncalibrated candidate fields only from the latest continuous run of 16-field events whose field at index 10 is 1000. Nonmatching events break the plot. Bounded raw-event capture remains intact.

The OEM ECG page currently shows activation guidance pointing to the phone Mi Fitness ECG card. This build does not change OEM activation, include Xiaomi's native analysis, or report calibrated mV, AFib, or a diagnosis. OEM SpO₂ percentages come from the OEM measurement service; our raw HAL events are not percentages.

Install the matching phone and watch APKs on their respective devices and grant the required permissions. This is a **debug-signed test build, not a Google Play release**.

Evidence and limits: [OEM feature audit](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), [dataset and paper provenance](docs/ALGORITHM_DATASET_PAPER_MAP.md), [privacy policy](docs/PRIVACY_POLICY.en.md).
