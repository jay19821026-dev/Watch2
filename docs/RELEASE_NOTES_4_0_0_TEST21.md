# AILISA Health Bridge 4.0.0 test 21 / 測試版 21

Author / 作者：王宥傑 (Wang You-Jie)

## 中文

本版延續對使用者提供的 Mi Fitness 3.53.0i XAPK 的靜態分析。原廠可讀程式會將多個心率樣本裝入一筆 Health Connect 心率紀錄；因此手機的「裝置 → 同步明細 → 核對資料來源」現在分別顯示紀錄筆數與七天範圍內的樣本數，最新心率時間取自樣本時間，不再以整筆紀錄結束時間冒充量測時間。

來源核對擴充至原廠 Health Connect 寫入權限列出的十類紀錄：心率、血氧、步數、睡眠、距離、活動熱量、運動時段、爬升、速度、步頻。速度新增讀取權限，由使用者在 Health Connect 授權；本頁不讀取運動路線，也不顯示健康數值。來源欄位改稱「來源套件」；Android 裝置的步數來源也可能是合成套件名稱，不能單靠此欄證明來自 Watch 2 Pro。

雙端 Debug APK 套件識別碼為 `net.ailisa.healthbridge`、`versionCode 181`，已通過單元測試與建置並覆蓋安裝。手機未授權畫面已實機檢查；授權後的 Mi Fitness 紀錄與心率樣本數仍待核對。另已本機建置並檢查 upload key 簽署的 Play 候選 AAB（手機 182、手錶 183），**尚未上傳 Play Console**。本 Release 是 Debug 簽章側載測試版，非醫療器材或正式商店版本。

## English

This build continues the static analysis of the user-supplied Mi Fitness 3.53.0i XAPK. Its readable Health Connect writer can pack multiple heart-rate samples into one `HeartRateRecord`. The phone's Device → Sync details → Source audit now separates record count from in-range sample count and uses the newest sample timestamp instead of the enclosing record's end time.

The audit now covers ten record types named by the OEM writer permissions: heart rate, oxygen saturation, steps, sleep, distance, active calories, exercise sessions, elevation gained, speed and step cadence. Speed read access requires user-granted Health Connect permission. The audit does not read exercise routes or display health values. It calls `DataOrigin.packageName` a source package: on-device steps may use an Android synthetic package name, and this field alone cannot identify the Watch 2 Pro.

Both debug APKs use `net.ailisa.healthbridge`, `versionCode 181`, and passed unit tests, builds and in-place installations. The ungranted phone audit screen was checked on device; actual consented Mi Fitness records and sample counts remain unverified. Separately signed Play candidate AABs (phone 182, Wear 183) passed local checks but **have not been uploaded to Play Console**. These Release APKs are debug-signed sideload tests, not a production store or medical-device build.

See the [bilingual XAPK comparison](MI_FITNESS_3_53_0I_XAPK_COMPARISON.md), [install guide](4.0_TEST_INSTALL.md), and [Play readiness record](PLAY_STORE_RELEASE_STATUS.md).
