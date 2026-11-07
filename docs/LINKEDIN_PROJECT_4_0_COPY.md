# LinkedIn project copy / LinkedIn 專案欄位文案

Author / 作者：王宥傑 (Wang You-Jie)
Prepared / 撰寫：2026-11-08
Publication status / 發布狀態：**draft; LinkedIn profile has not been updated or verified** / 草稿；尚未更新或核對 LinkedIn 個人檔案。

## Traditional Chinese / 繁體中文

**專案名稱：** Xiaomi Health Bridge｜Android × Wear OS 健康資料研究

**專案連結：** https://ailisa.net/portfolio/xiaomi-health-bridge

**相關 GitHub：** https://github.com/jay19821026-dev/Watch2

**說明：**

由王宥傑開發的開源 Android 手機與 Xiaomi Watch 2 Pro／Wear OS 雙端專案。使用 Wear OS Health Services、Wearable Data Layer 和 Health Connect 整理使用者授權的心率、活動及其他健康紀錄，並顯示資料來源、時間、品質與同步狀態。4.0 測試版新增血氧紀錄方式篩選、獨立 ECG 研究卡片，以及由使用者明確點擊的原廠功能入口。實機盤點確認這隻錶的 ECG、PPG、BioZ、血氧相關與溫度感測器通道；原始 Android ECG 路徑沒有觀察到硬體封鎖，原廠 ECG 卡片與啟用狀態則有獨立的軟體門檻。ECG 研究預覽依原廠封包標記與配戴狀態雙條件過濾；血氧與體溫原始擷取依原廠程式觀察到的離腕判斷，僅在確認佩戴時標示完成。test 8 另依原廠壓力量測的離腕行為，讓本專案自訂 HRV 研究壓力分數需通過配戴、光學品質和 RR 數量檢查；這不是小米原廠分數或血壓。原始通道僅作研究與品質檢視，未取得裝置專屬校準與臨床驗證前不宣稱醫療數值或診斷。專案公開雙端原始碼、測試 APK、中英文文件及資料庫、論文、演算法溯源。非醫療器材。

## English

**Project title:** Xiaomi Health Bridge | Android × Wear OS Health Data Research

**Project URL:** https://ailisa.net/portfolio/xiaomi-health-bridge

**GitHub:** https://github.com/jay19821026-dev/Watch2

**Description:**

An open-source Android phone and Xiaomi Watch 2 Pro / Wear OS project by Wang You-Jie. It uses Wear OS Health Services, the Wearable Data Layer, and Health Connect to organize user-authorized health records with visible provenance, timing, quality, and sync status. The 4.0 test build adds SpO₂ recording-method filters, an independent ECG research card, and explicit user-tapped links to OEM watch screens. A live inventory confirms ECG, PPG, BioZ, SpO₂-related, and temperature sensor channels on this watch. No hardware block has been observed on its raw Android ECG path; OEM card availability and activation have separate software gates. The ECG research preview filters on both an OEM-observed packet marker and time-aligned on-body status. Based on behavior observed in the installed OEM app, raw SpO₂ and body-temperature capture are marked complete only with confirmed on-body status. Test 8 also uses the OEM stress screen's off-wrist behavior to gate our own HRV-derived research stress score on wear state, optical quality, and RR count. It is neither Xiaomi's score nor blood pressure. Raw channels remain research and quality evidence, not calibrated medical measurements or diagnoses. The project publishes both source modules, test APKs, bilingual documentation, and dataset–paper–algorithm provenance. Not a medical device.

## Evidence before posting / 發布前核對

- The 4.0.0 test 8 prerelease and both APK assets are live; all three public downloads were fetched again and matched their local SHA-256: https://github.com/jay19821026-dev/Watch2/releases/tag/v4.0.0-test.8
- The public bilingual page is live at Site version 172; HTTP 200, test 8 links, and both APK hashes were checked: https://ailisa.net/portfolio/xiaomi-health-bridge
- The test 8 phone and watch APKs installed and launched on the owner's connected pair on 2026-11-08. The watch-side OEM ECG entry showed activation guidance; the owner reports no Mi Fitness ECG card. Actual on-wrist SpO₂/temperature/stress capture and bidirectional sync remain unverified; do not claim that ECG was unlocked or any derived metric clinically validated.
- Add the project to the user's own LinkedIn profile only after the authenticated profile and destination fields can be verified. Record the resulting project URL after saving.
