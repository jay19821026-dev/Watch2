# AILISA Health Bridge 4.0.0 test 28 / 測試版 28

作者 / Author: 王宥傑 (Wang You-Jie)

## 中文

手機心率詳細頁現在按 `HeartRateRecord` **每筆樣本的量測時間**，先選出查詢區間 `[開始, 結束)` 內的樣本，再檢查數值與來源。原廠 3.59.2i 的 `HrSyncBiz` 以同步時間窗封裝紀錄，並保留各 `HrItem.time`；跨日封裝紀錄不應使整頁被誤判為無效。9.8.348i 的 `HeartRateBiz` 則是型別化心率彙總，不能混稱為 Health Connect 寫入流程。完整對照見[雙版本資料時間窗稽核](OEM_DATA_WINDOW_COMPARISON_4_0.md)。

手機與 Wear OS 雙端均為 `net.ailisa.healthbridge`、`versionCode 200` 的 **Debug 簽章側載測試版**。雙端單元測試及 Debug 建置通過；跨日樣本回歸測試涵蓋開始前、起點、終點前及終點。尚未以配對手機／手錶核對本版實際資料，也沒有證實與原廠顯示數值相同。這不是 Play Store 正式版或醫療器材。

## English

The phone heart-rate detail now selects samples by each `HeartRateRecord.Sample.time` within `[start, end)` before checking their value and source. Mi Fitness 3.59.2i `HrSyncBiz` packages records by a sync window while retaining individual `HrItem.time`; a record crossing a calendar boundary should not invalidate the whole page. The 9.8.348i `HeartRateBiz` is a separate typed aggregation path, not a Health Connect writer. See the [two-version data-window audit](OEM_DATA_WINDOW_COMPARISON_4_0.md).

Both phone and Wear OS APKs are **debug-signed sideload tests**, package `net.ailisa.healthbridge`, `versionCode 200`. Both unit-test suites and Debug builds passed. A regression test covers samples just before the window, at its start, just before its end, and at its end. Paired-device readings and numerical parity with Mi Fitness remain unverified. This is neither a Play Store production build nor a medical device.
