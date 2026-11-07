# LinkedIn project copy / LinkedIn 專案欄位文案

Author / 作者：王宥傑 (Wang You-Jie)
Prepared / 撰寫：2026-11-08
Publication status / 發布狀態：**draft; LinkedIn profile has not been updated or verified** / 草稿；尚未更新或核對 LinkedIn 個人檔案。

## Traditional Chinese / 繁體中文

**專案名稱：** Xiaomi Health Bridge｜Android × Wear OS 健康資料研究

**專案連結：** https://ailisa.net/portfolio/xiaomi-health-bridge

**相關 GitHub：** https://github.com/jay19821026-dev/Watch2

**說明：**

由王宥傑開發的開源 Android 手機與 Xiaomi Watch 2 Pro／Wear OS 雙端專案。使用 Wear OS Health Services、Wearable Data Layer 和 Health Connect 整理使用者授權的心率、活動及其他健康紀錄，並顯示資料來源、時間、品質與同步狀態。4.0 測試版提供血氧紀錄方式篩選、獨立 ECG 研究卡片，以及由使用者明確點擊的原廠功能入口。實機盤點確認這隻錶的 ECG、PPG、BioZ、血氧相關與溫度感測器通道；原始 Android ECG 路徑可見，原廠 ECG 卡片與啟用狀態另受軟體流程控制。ECG 研究預覽依原廠封包標記與配戴狀態過濾；test 10 另以事件時間戳斷開相隔超過一秒的畫線，避免分離事件看似連續訊號。血氧與體溫原始擷取僅在確認佩戴時標示完成。研究壓力分數依原廠壓力量測的離腕行為加入配戴、光學品質和 RR 數量檢查，但公式由本專案自訂，不是小米原廠分數或血壓。已安裝 Mi Fitness 的反組譯證據區分 ECG 卡片、提醒功能鍵、同步呼叫及帶有取樣頻率的原廠正式紀錄；同步成功不代表已讀回手錶啟用狀態，原廠紀錄時基也不能移用到本 App 原始事件。未取得裝置專屬校準與臨床驗證前不宣稱醫療數值或診斷。專案公開雙端原始碼、測試 APK、中英文文件及資料庫、論文、演算法溯源。非醫療器材。

## English

**Project title:** Xiaomi Health Bridge | Android × Wear OS Health Data Research

**Project URL:** https://ailisa.net/portfolio/xiaomi-health-bridge

**GitHub:** https://github.com/jay19821026-dev/Watch2

**Description:**

An open-source Android phone and Xiaomi Watch 2 Pro / Wear OS project by Wang You-Jie. It uses Wear OS Health Services, the Wearable Data Layer, and Health Connect to organize user-authorized health records with visible provenance, timing, quality, and sync status. The 4.0 test build provides SpO₂ recording-method filters, an independent ECG research card, and explicit user-tapped links to OEM watch screens. A live inventory confirms ECG, PPG, BioZ, SpO₂-related, and temperature sensor channels on this watch. The raw Android ECG path is visible; OEM card availability and activation have separate software gates. The ECG research preview filters on an OEM-observed packet marker and time-aligned on-body status. Test 10 additionally breaks the drawn line across event timestamp gaps over one second, preventing disconnected raw events from appearing continuous. Raw SpO₂ and body-temperature capture are marked complete only with confirmed on-body status. Based on the OEM stress screen's off-wrist behavior, our HRV-derived research stress score also requires wear state, optical quality, and enough RR intervals. Its formula is our own, neither Xiaomi's score nor blood pressure. The installed Mi Fitness code distinguishes ECG card, reminder and sync gates from a typed OEM record with its own sampling frequency; a successful callback is not a confirmed readback of watch activation, and the OEM record clock cannot be transplanted into our raw events. Raw channels remain research and quality evidence, not calibrated medical measurements or diagnoses. The project publishes both source modules, test APKs, bilingual documentation, and dataset–paper–algorithm provenance. Not a medical device.

## Evidence before posting / 發布前核對

- The 4.0.0 test 10 prerelease and both APK assets are live; all three public downloads were fetched again and matched their local SHA-256: https://github.com/jay19821026-dev/Watch2/releases/tag/v4.0.0-test.10
- The public bilingual page is live at Site version 178; HTTP 200, test 10 links, both APK hashes, and the installed-phone ECG pipeline document link were checked: https://ailisa.net/portfolio/xiaomi-health-bridge
- The test 10 phone and watch APKs installed over the owner's connected pair on 2026-11-08; both report versionCode 158, and the phone launcher/watch main Activity started. The watch-side OEM ECG entry previously showed activation guidance; the owner reports no Mi Fitness ECG card. Actual on-wrist ECG preview, SpO₂/temperature/stress capture, and bidirectional sync remain unverified; do not claim ECG was unlocked or any derived metric clinically validated.
- Add the project to the user's own LinkedIn profile only after the authenticated profile and destination fields can be verified. Record the resulting project URL after saving.
