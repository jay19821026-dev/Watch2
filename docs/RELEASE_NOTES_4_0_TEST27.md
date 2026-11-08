# AILISA Health Bridge 4.0.0 test 27 / 測試版 27

作者 / Author: 王宥傑 (Wang You-Jie)

## 中文

這是手機與 Wear OS 手錶的 **Debug 簽章側載測試版**，雙端 `versionCode 197`，套件識別碼 `net.ailisa.healthbridge`。承接 test 26 的原廠資料流對照，本版修正：不再把低心率樣本跨度當作睡眠時數；Health Connect 腕部體溫詳細頁只顯示腕部位置紀錄，不套用核心體溫參考評分；圖表日頁採本地半小時、週／月頁採本地日，年頁採本地月。這只對齊原廠溫度圖表的時間粒度，資料與聚合算法仍不同。撤回以年齡預測最高心率拼接公式得出的 VO₂max 數字；溯源表列明 Uth 與 Tanaka 論文及限制。

**驗證範圍：**手機與手錶 Debug 建置、單元測試通過。發布前 ADB 清單沒有可連線裝置，test 27 尚未在配對手機／手錶安裝或完成實際量測。原廠 ECG 啟用、BioZ 體成分、光學血氧百分比與體溫校準未經驗證。本版不是 Google Play 正式版或醫療器材。

## English

These are **debug-signed sideload builds** for Android phone and Wear OS, both at `versionCode 197` and package ID `net.ailisa.healthbridge`. Building on the OEM data-flow comparison in test 26, this version stops treating low-heart-rate spans as sleep duration. The Health Connect wrist-temperature detail only shows records tagged with wrist measurement location and no longer scores them against a core-temperature reference. Its chart uses local half-hour buckets for the day view, local days for week/month, and local months for the extra year view. This matches OEM temperature chart **time granularity**, not OEM values or aggregation. The unsupported VO₂max number combining age-predicted HRmax with a separate heart-rate-ratio study was removed; the provenance table cites Uth and Tanaka and explains the boundary.

**Verification:** phone and watch Debug builds and unit tests passed. No device was listed by ADB before publication, so test 27 has not been installed or measurement-tested on the paired phone/watch. OEM ECG activation, BioZ body-composition conversion, optical SpO₂ percentage and temperature calibration remain unverified. This is neither a Google Play production build nor a medical device.

See [OEM data-window comparison](OEM_DATA_WINDOW_COMPARISON_4_0.md), [algorithm and dataset provenance](ALGORITHM_DATASET_PAPER_MAP.md), and the [migration guide](PACKAGE_MIGRATION_4_0.en.md).
