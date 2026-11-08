# AILISA Health Bridge 4.0.0 test 26 / 測試版 26

作者 / Author: 王宥傑 (Wang You-Jie)

## 中文

這是手機與 Wear OS 手錶的 **Debug 簽章側載測試版**，雙端 `versionCode 196`、套件識別碼 `net.ailisa.healthbridge`。本版對照 Watch 2 Pro 原廠健康服務與 Mi Fitness 3.59.2i 反組譯資料，修正近七個本地日曆日的查詢起點、Health Connect 心率逐樣本時間、跨界睡眠區間與血氧逐筆量測時間。溫度資料保留來源和時間；新增心率光學 `65597` 與血氧光學 `65596` 原始通道的獨立研究入口。健康分析首頁優先列出實際取得的紀錄與研究訊號，不把原始 PPG／BioZ／ECG 轉成未驗證的醫療結果。

**驗證範圍：**手機與手錶 Debug 建置、單元測試通過。test 26 尚未在配對手機與手錶完成安裝及實際量測驗證。原廠 ECG 啟用、BioZ 體成分換算、血氧光學百分比與溫度校準未經驗證；本版沒有宣稱解鎖這些功能，也不是 Google Play 正式版本或醫療器材。

## English

These are **debug-signed sideload builds** for Android phone and Wear OS, both at `versionCode 196` with package ID `net.ailisa.healthbridge`. Based on the inspected Watch 2 Pro OEM health service and decompiled Mi Fitness 3.59.2i paths, this build aligns the seven-local-date query start, filters individual Health Connect heart-rate samples by measurement time, clips sleep sessions crossing the window, and checks each oxygen record's measurement time. Temperature retains source and timestamp details. Separate research controls target raw optical heart-rate sensor `65597` and raw optical oxygen sensor `65596`. The analysis screen leads with observed records and raw research signals without presenting uncalibrated PPG, BioZ, or ECG as medical results.

**Verification:** phone and watch Debug builds and unit tests passed. Test 26 has not yet been installed and measurement-tested on the paired devices. OEM ECG activation, BioZ body-composition conversion, optical SpO₂ percentage, and temperature calibration remain unverified. This is neither a Google Play production release nor a medical device.

See [OEM data-window comparison](OEM_DATA_WINDOW_COMPARISON_4_0.md), [OEM feature gap](OEM_TO_AILISA_FEATURE_GAP_4_0.md), [signal source trace](OEM_PPG_BIOZ_ECG_HEALTH_CONNECT_TRACE.md), and [migration guide](PACKAGE_MIGRATION_4_0.en.md).
