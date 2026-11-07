# LinkedIn project copy / LinkedIn 專案欄位文案

Author / 作者：王宥傑 (Wang You-Jie)
Prepared / 撰寫：2026-11-08
Publication status / 發布狀態：**draft; LinkedIn profile has not been updated or verified** / 草稿；尚未更新或核對 LinkedIn 個人檔案。

## Traditional Chinese / 繁體中文

**專案名稱：** Xiaomi Health Bridge｜Android × Wear OS 健康資料研究

**專案連結：** https://ailisa.net/portfolio/xiaomi-health-bridge

**相關 GitHub：** https://github.com/jay19821026-dev/Watch2

**說明：**

由王宥傑開發的開源 Android 手機與 Xiaomi Watch 2 Pro／Wear OS 雙端專案。以 Wear OS Health Services、Wearable Data Layer 與 Health Connect 整理使用者授權的健康資料，顯示來源、時間、品質和同步狀態。4.0 test 15 提供心率與活動紀錄、血氧紀錄方式篩選、獨立 ECG 原始資料研究卡片，以及由使用者點擊的原廠功能入口。根據已安裝 Xiaomi Health 手錶程式的靜態分析，原廠「呼吸」入口是 1–5 分鐘引導練習，與本 App 的 BioZ 呼吸速率研究分開；原廠血氧、溫度服務結果也與 Android 原始感測事件分開處理。手錶頁面已加入原廠提出的佩戴與量測前提示，原始值仍不冒充百分比、攝氏溫度或診斷結果。專案公開雙端原始碼、測試 APK、中英文文件，以及資料庫、論文與演算法用途對照。非醫療器材。

## English

**Project title:** Xiaomi Health Bridge | Android × Wear OS Health Data Research

**Project URL:** https://ailisa.net/portfolio/xiaomi-health-bridge

**GitHub:** https://github.com/jay19821026-dev/Watch2

**Description:**

An open-source Android phone and Xiaomi Watch 2 Pro / Wear OS project by Wang You-Jie. It uses Wear OS Health Services, the Wearable Data Layer, and Health Connect to organize user-authorized health data with visible source, timing, quality, and sync status. Version 4.0 test 15 includes heart-rate and activity records, SpO₂ recording-method filters, a raw ECG research card, and user-tapped links to OEM watch screens. Static inspection of the installed Xiaomi Health watch app identifies its breathing module as a 1–5-minute guided exercise, separate from BioZ respiratory-rate research. OEM typed SpO₂ and temperature results are also traced separately from public raw sensor events. The watch pages add OEM-sourced preparation tips without treating raw fields as calibrated percentages, Celsius temperatures, or diagnoses. Both source modules, test APKs, bilingual documentation, and a dataset–paper–algorithm use map are public. Not a medical device.

## Evidence before posting / 發布前核對

- The 4.0.0 test 15 prerelease and both APK assets are live; all three public download URLs returned HTTP 200 on 2026-11-08: https://github.com/jay19821026-dev/Watch2/releases/tag/v4.0.0-test.15
- The bilingual site version 183 is published; public HTTP 200, test 15 text, source-trace link and both published APK hashes were checked: https://ailisa.net/portfolio/xiaomi-health-bridge
- Test 15 phone and watch APKs installed over the owner's connected pair on 2026-11-08; both report versionCode 163. Unit tests, build, manifest and signature checks passed. Live OEM breathing, SpO₂, temperature and bidirectional sync checks remain incomplete. Do not claim ECG was unlocked or any derived metric clinically validated.
- Add the project to the user's own LinkedIn profile only after the authenticated profile and destination fields can be verified. Record the resulting project URL after saving.
