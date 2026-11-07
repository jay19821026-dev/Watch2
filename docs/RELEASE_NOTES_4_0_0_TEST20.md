# AILISA Health Bridge 4.0.0 test 20 / 測試版 20

Author / 作者：王宥傑 (Wang You-Jie)

## 中文

本版延續 test 19 根據 Mi Fitness 3.53.0i XAPK 靜態反組譯新增的 Health Connect 資料來源核對。手機「裝置 → 同步明細」可分別顯示心率、血氧、步數、睡眠、距離和活動熱量的授權、近七天來源 App、紀錄數與最新時間。尚未取得此手機的讀取授權，因此不宣稱已讀到 Watch 2 Pro 的實際紀錄。

手錶首頁修正實機可見的心率圓形區文字重疊、讓主要量測按鈕在 466 × 466 螢幕完整出現，並將頁首與健康資訊 Tile 改為 AILISA 品牌。手機摘要及手錶示意圖的舊字樣也同步更正。新增實際手錶截圖供商店素材預檢；沒有在圖中展示健康數值。

手機與手錶 Debug APK 使用 `net.ailisa.healthbridge`、`versionCode 178`，已通過雙端單元測試、建置、簽章檢查與覆蓋安裝。手錶首頁已在配對裝置開啟並核對畫面；感測器、Health Connect 實際來源與雙向同步仍待實測。另已本機建置使用候選 upload key 簽署的 Play AAB（手機 179、手錶 180），**未上傳 Play Console**。此 Release 的 APK 是 Debug 簽章側載測試版，不是正式商店版或醫療器材。

## English

This build retains the local Health Connect origin audit added in test 19 from static inspection of the user-supplied Mi Fitness 3.53.0i XAPK. On the phone, Device → Sync details distinguishes permissions, seven-day source apps, counts and latest times for heart rate, oxygen saturation, steps, sleep, distance and active calories. Read permissions are still not granted on the paired phone, so actual Watch 2 Pro record origins are unverified.

The watch home screen now avoids overlapping heart-rate text and shows the main measurement button fully on the 466 × 466 display. The header and health tile use the AILISA brand; the phone summary and watch illustration were updated too. A real watch screenshot with no health value was prepared for store-asset review.

The phone and Wear debug APKs share package `net.ailisa.healthbridge`, `versionCode 178`. Both unit-test suites, builds, signature checks and in-place installations passed. The paired watch displayed the corrected home screen. Sensor behavior, actual Health Connect origins and bidirectional sync remain unverified. Separately signed Play candidate AABs (phone 179, Wear 180) are local only and **have not been uploaded to Play Console**. These Release APKs are debug-signed sideload tests, not production store or medical-device builds.

See the [bilingual Mi Fitness XAPK comparison](MI_FITNESS_3_53_0I_XAPK_COMPARISON.md), [install guide](4.0_TEST_INSTALL.md), and [Play readiness record](PLAY_STORE_RELEASE_STATUS.md).
