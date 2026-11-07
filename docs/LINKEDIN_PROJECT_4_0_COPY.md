# LinkedIn project copy / LinkedIn 專案欄位文案

Author / 作者：王宥傑 (Wang You-Jie)
Prepared / 撰寫：2026-11-08
Publication status / 發布狀態：**draft; LinkedIn profile has not been updated or verified** / 草稿；尚未更新或核對 LinkedIn 個人檔案。

## Traditional Chinese / 繁體中文

**專案名稱：** Xiaomi Health Bridge｜Android × Wear OS 健康資料研究

**專案連結：** https://ailisa.net/portfolio/xiaomi-health-bridge

**相關 GitHub：** https://github.com/jay19821026-dev/Watch2

**說明：**

由王宥傑開發的開源 Android 手機與 Xiaomi Watch 2 Pro／Wear OS 雙端專案。以 Wear OS Health Services、Wearable Data Layer 與 Health Connect 整理使用者授權的健康資料，顯示來源、時間、品質和同步狀態。4.0 test 14 提供心率與活動紀錄、血氧紀錄方式篩選、獨立 ECG 原始資料研究卡片，以及由使用者點擊的原廠功能入口。根據已安裝 Xiaomi Health 手錶程式的靜態分析，新增的原廠「呼吸」入口是 1–5 分鐘引導練習，與本 App 的 BioZ 呼吸速率研究分開；完整原廠練習仍待實機驗證。ECG、BioZ、血氧及體溫原始通道只用於研究與品質檢查，缺少裝置校準時不輸出診斷結果。專案公開雙端原始碼、測試 APK、中英文文件，以及資料庫、論文與演算法用途對照。非醫療器材。

## English

**Project title:** Xiaomi Health Bridge | Android × Wear OS Health Data Research

**Project URL:** https://ailisa.net/portfolio/xiaomi-health-bridge

**GitHub:** https://github.com/jay19821026-dev/Watch2

**Description:**

An open-source Android phone and Xiaomi Watch 2 Pro / Wear OS project by Wang You-Jie. It uses Wear OS Health Services, the Wearable Data Layer, and Health Connect to organize user-authorized health data with visible source, timing, quality, and sync status. Version 4.0 test 14 includes heart-rate and activity records, SpO₂ recording-method filters, a raw ECG research card, and user-tapped links to OEM watch screens. Static inspection of the installed Xiaomi Health watch app identifies the new OEM breathing shortcut as a 1–5-minute guided exercise, separate from this app's BioZ respiratory-rate research; a full OEM session remains unverified. Raw ECG, BioZ, SpO₂, and temperature channels are research and quality evidence, not calibrated medical measurements or diagnoses. The project publishes both source modules, test APKs, bilingual documentation, and a dataset–paper–algorithm use map. Not a medical device.

## Evidence before posting / 發布前核對

- The 4.0.0 test 14 prerelease and both APK assets are live; all three public download URLs returned HTTP 200 on 2026-11-08: https://github.com/jay19821026-dev/Watch2/releases/tag/v4.0.0-test.14
- The bilingual site version 182 is published; public HTTP 200, test 14 text and both published APK hashes were checked: https://ailisa.net/portfolio/xiaomi-health-bridge
- Test 14 phone and watch APKs installed over the owner's connected pair on 2026-11-08; both report versionCode 162. Unit tests, build, manifest and signature checks passed. The watch entered doze before a reliable live OEM breathing-session check. Do not claim ECG was unlocked or any derived metric clinically validated.
- Add the project to the user's own LinkedIn profile only after the authenticated profile and destination fields can be verified. Record the resulting project URL after saving.
