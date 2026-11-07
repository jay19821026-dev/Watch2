# Xiaomi Health Bridge 4.0.0 test 17 / 測試版 17

Author / 作者：王宥傑 (Wang You-Jie)

## 中文

手機與 Xiaomi Watch 2 Pro／Wear OS 雙端 Debug 測試 APK，版本碼均為 `169`。本版依已安裝 Mi Fitness 3.59.2i 的反組譯路徑修正血壓與血糖功能描述：手機新增血壓、血糖紀錄的方法都將來源標為 `xiaomiwear_app_manually`。這是手動紀錄路徑，不能當作 Watch 2 Pro 有血壓或血糖量測硬體的證據。

本 App 的手機血壓頁只接受外部袖帶讀值。過去的「光學血壓估計」導覽字樣已改正；手錶入口只顯示不支援說明，不啟動血壓感測器。舊版 `optical_bp` 結果入口現在導向袖帶紀錄頁；即使舊快取含收縮／舒張壓與時間，結果政策也不再顯示它們為 mmHg。新同步封包會清除舊血壓公式欄位，且手錶端不再序列化那些欄位。血糖仍標示無可用來源。原廠 ECG 啟用狀態未改動。

**驗證範圍：**手機 1,073 項及手錶 240 項 Debug 單元測試通過；雙端 APK 建置、版本 `169`、套件與相同 Debug 簽章已核對。手錶最後檢查為 9% 且未充電，test 17 **尚未安裝或完成配對裝置的畫面、感測器與同步驗證**。這些 APK 不是醫療器材，也不是 Google Play 發行版。

## English

These are Debug-signed sideload APKs for Android phone and Xiaomi Watch 2 Pro / Wear OS, both with version code `169`. This update corrects the blood-pressure and glucose descriptions using decompiled evidence from the installed Mi Fitness 3.59.2i app: its phone add-record paths mark both record types with the manual source ID `xiaomiwear_app_manually`. A phone record form does not establish a Watch 2 Pro blood-pressure or glucose sensor.

Our phone blood-pressure page accepts external cuff readings only. Navigation no longer advertises an optical blood-pressure estimate, and the watch entry opens an unsupported-feature explanation without starting a BP sensor. The legacy `optical_bp` result entry now goes to the cuff-record page. Even if old cached systolic/diastolic values and a timestamp exist, the result policy suppresses their mmHg display. New raw snapshots clear legacy BP formula fields, and the watch no longer serializes them. Glucose remains unavailable. OEM ECG activation is unchanged.

**Verification:** 1,073 mobile and 240 Wear Debug unit tests passed. Both APK builds, version `169` manifests, package names and matching Debug signatures were checked. The paired watch was at 9% and not charging at the latest check, so test 17 **has not yet been installed or verified through the paired-device UI, sensors or sync**. These are neither medical devices nor Google Play releases.

## Files / 檔案

| File | SHA-256 |
|---|---|
| `XiaomiHealthBridge-Mobile-4.0.0-vc169-test17-debug.apk` | `974F02EAD36D40412F6B457280BE017F196603B80B771AA8AA171CB513995C31` |
| `XiaomiHealthBridge-Wear-4.0.0-vc169-test17-debug.apk` | `461F46CAE2A0CB3273F725EA9EA881118D56AF1039F539910AF22D2A37B51AD9` |

Both have package `com.jaysato.xiaomihealthbridge` and the same Android Debug certificate. Follow the [bilingual install guide](4.0_TEST_INSTALL.md), [OEM feature audit](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), and [dataset/paper map](ALGORITHM_DATASET_PAPER_MAP.md).
