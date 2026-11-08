# AILISA Health Bridge 4.0.0 test 29 / 測試版 29

作者 / Author: 王宥傑 (Wang You-Jie)

## 中文

依 Mi Fitness 9.8.348i 與 3.59.2i 反組譯的 `HeartRateBiz`、`Spo2Biz` 比對，手機心率與血氧詳細圖表改用本地半小時（日頁）、本地日（週／月頁）時間分桶；額外年頁用本地月分桶。每桶畫出所選 Health Connect 來源的最低至最高值，單一值畫點。原始紀錄和概覽統計仍以單筆 Health Connect 值計算；**沒有複製原廠的去重整數陣列、來源合併、日均值或醫學判讀**。依據及版本差異見[資料時間窗對照](OEM_DATA_WINDOW_COMPARISON_4_0.md)。

手機與 Wear OS 雙端為套件 `net.ailisa.healthbridge`、`versionCode 203` 的 **Debug 側載測試版**。雙端單元測試與 Debug 建置通過；尚未在配對裝置量測驗證。這不是 Play Store 正式版或醫療器材。

## English

Comparison of decompiled Mi Fitness 9.8.348i and 3.59.2i `HeartRateBiz` and `Spo2Biz` led to local half-hour chart buckets for the day view and local-day buckets for week/month. AILISA's extra year view uses local-month buckets. Each bucket draws the minimum-to-maximum range of the selected Health Connect origin, or a dot for one value. Raw rows and overview statistics still use individual Health Connect values; **OEM distinct-value arrays, source merging, daily means, and medical interpretation are not reproduced**. See the [data-window comparison](OEM_DATA_WINDOW_COMPARISON_4_0.md).

Both Android phone and Wear OS APKs are **debug-signed sideload tests**, package `net.ailisa.healthbridge`, `versionCode 203`. Both unit-test suites and Debug builds passed. Paired-device measurement remains unverified. This is neither a Play Store production build nor a medical device.
