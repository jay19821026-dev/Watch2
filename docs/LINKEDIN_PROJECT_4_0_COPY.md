# LinkedIn project copy / LinkedIn 專案欄位文案

Author / 作者：王宥傑 (Wang You-Jie)
Prepared / 撰寫：2026-11-08
Publication status / 發布狀態：**draft; LinkedIn profile has not been updated or verified** / 草稿；尚未更新或核對 LinkedIn 個人檔案。

## Traditional Chinese / 繁體中文

**專案名稱：** Xiaomi Health Bridge｜Android × Wear OS 健康資料研究

**專案連結：** https://ailisa.net/portfolio/xiaomi-health-bridge

**相關 GitHub：** https://github.com/jay19821026-dev/Watch2

**說明：**

由王宥傑開發的開源 Android 手機與 Xiaomi Watch 2 Pro／Wear OS 雙端專案。以 Wear OS Health Services、Wearable Data Layer 與 Health Connect 整理使用者授權的健康資料，顯示來源、時間、品質和同步狀態。4.0 test 16 提供心率與活動紀錄、血氧紀錄方式篩選、獨立 ECG 原始資料研究卡片，以及由使用者點擊的原廠功能入口。根據已安裝 Xiaomi Health 手錶程式的靜態分析，原廠呼吸練習與 BioZ 呼吸速率研究分開；原廠血氧、溫度及壓力的型別化服務結果也與 Android 原始感測事件和本專案自訂 HRV 啟發式分開。手錶頁面新增原廠壓力入口，切換前會停止本 App 擷取。專案公開雙端原始碼、測試 APK、中英文文件，以及資料庫、論文與演算法用途對照；MIT-BIH 的離線 QRS 基準和表現不佳的腕式 PPG 基準均如實列出，不能推論本手錶的臨床準確率。非醫療器材。

## English

**Project title:** Xiaomi Health Bridge | Android × Wear OS Health Data Research

**Project URL:** https://ailisa.net/portfolio/xiaomi-health-bridge

**GitHub:** https://github.com/jay19821026-dev/Watch2

**Description:**

An open-source Android phone and Xiaomi Watch 2 Pro / Wear OS project by Wang You-Jie. It uses Wear OS Health Services, the Wearable Data Layer, and Health Connect to organize user-authorized health data with visible source, timing, quality, and sync status. Version 4.0 test 16 includes heart-rate and activity records, SpO₂ recording-method filters, a raw ECG research card, and user-tapped links to OEM watch screens. Static inspection of the installed Xiaomi Health watch app separates its guided breathing exercise from BioZ respiratory-rate research and its typed SpO₂, temperature, and stress results from public raw sensor events and this project's HRV heuristic. The watch research page adds an OEM stress entry and stops its own capture before opening that screen. Both source modules, test APKs, bilingual documentation, and a dataset–paper–algorithm use map are public. The offline MIT-BIH QRS benchmark and poor simple wrist-PPG baseline are disclosed without claiming clinical accuracy for this watch. Not a medical device.

## Evidence before posting / 發布前核對

- The 4.0.0 test 16 prerelease and both APK assets are live; all three public download URLs returned HTTP 200 and the release asset digests matched the local APKs on 2026-11-08: https://github.com/jay19821026-dev/Watch2/releases/tag/v4.0.0-test.16
- The bilingual site version 185 is published; public HTTP 200, test 16 text, new OEM stress source trace, MIT-BIH and wrist-PPG status, both published APK hashes, and three user-provided demo screenshots were checked on 2026-11-08: https://ailisa.net/portfolio/xiaomi-health-bridge
- Test 16 phone and watch APKs both report versionCode 166. Mobile 1,071 and Wear 240 unit tests, builds, manifests and signatures passed. **Test 16 is not yet installed or visually verified on the paired watch** because its battery was 12% at build time and later fell to 10%; live OEM stress, breathing, SpO₂, temperature and bidirectional sync remain unverified. Do not claim ECG was unlocked or any derived metric clinically validated.
- Add the project to the user's own LinkedIn profile only after the authenticated profile and destination fields can be verified. Record the resulting project URL after saving.
