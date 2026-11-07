# AILISA Health Bridge 4.0.0 test 19 / 測試版 19

Author / 作者：王宥傑 (Wang You-Jie)

## 中文

本版根據使用者提供的 Mi Fitness 3.53.0i XAPK 靜態反組譯，新增手機端 **裝置 → 同步明細 → 核對 Mi Fitness／Health Connect 資料來源**。這個本機頁面對照原廠 APK 可見的 Health Connect 寫入路徑，逐項檢查心率、血氧、步數、睡眠、距離與活動熱量的讀取授權、近七天紀錄數、來源套件及最新時間。未授權、沒有紀錄、讀取失敗及查詢上限分別顯示；不顯示健康數值，不上傳結果。

這項功能使用 Android 公開的 Health Connect API，沒有複製原廠程式碼或演算法。APK 內有 ECG 功能碼仍不能證明 Watch 2 Pro 已啟用 ECG，也不能據此產生醫療結論。詳見[中英文反組譯對照報告](MI_FITNESS_3_53_0I_XAPK_COMPARISON.md)。

雙端 Debug APK 均為 `net.ailisa.healthbridge`、`versionCode 175`，已通過手機與手錶單元測試、建置、簽章及覆蓋安裝。手機實際開啟來源核對頁，未授權項目正確顯示「未授權」。尚未取得 Health Connect 讀取授權，因此未聲稱已讀到 Mi Fitness 的實際紀錄。此為側載測試版，未上架 Google Play。

## English

This build adds a local **Device → Sync details → Mi Fitness / Health Connect source audit** informed by the user-supplied Mi Fitness 3.53.0i XAPK analysis. It checks granted read permissions, seven-day counts, source package names and latest record times for heart rate, oxygen saturation, steps, sleep, distance and active calories. Permission denied, no readable records, read failure and query-limit states are distinct. The audit displays no health values and uploads nothing.

It uses the public Android Health Connect API; it does not copy OEM code or algorithms. ECG code in the APK does not prove Watch 2 Pro activation or support a clinical claim. See the [bilingual static comparison](MI_FITNESS_3_53_0I_XAPK_COMPARISON.md).

Both debug APKs use `net.ailisa.healthbridge`, `versionCode 175`. Mobile and Wear unit tests, builds, signature checks and in-place installs passed. The phone opened the audit page and displayed the permission-denied state correctly. Health Connect read permission has not yet been granted, so actual Mi Fitness record origins remain unverified. This is a sideload test build, not a Google Play release.
