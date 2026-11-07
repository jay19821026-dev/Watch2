# LinkedIn project copy / LinkedIn 專案欄位文案

Author / 作者：王宥傑 (Wang You-Jie)
Prepared / 撰寫：2026-11-08
Publication status / 發布狀態：**draft; LinkedIn profile has not been updated or verified** / 草稿；尚未更新或核對 LinkedIn 個人檔案。

## Traditional Chinese / 繁體中文

**專案名稱：** AILISA Health Bridge｜Android × Wear OS 健康資料研究

**專案連結：** https://ailisa.net/portfolio/ailisa-health-bridge

**相關 GitHub：** https://github.com/jay19821026-dev/Watch2

**說明：**

由王宥傑開發的開源 Android 手機與 Xiaomi Watch 2 Pro／Wear OS 雙端專案。以 Wear OS Health Services、Wearable Data Layer 與 Health Connect 整理使用者授權的健康資料，顯示來源、時間、品質和同步狀態。4.0 test 18 提供心率與活動紀錄、血氧紀錄方式篩選、獨立 ECG 原始資料研究卡片，以及由使用者點擊的原廠功能入口。根據已安裝 Xiaomi Health 手錶程式的靜態分析，原廠呼吸練習與 BioZ 呼吸速率研究分開；原廠血氧、溫度及壓力的型別化服務結果也與 Android 原始感測事件和本專案自訂 HRV 啟發式分開。新版另依原廠手機 App 的手動血壓、血糖紀錄來源修正功能標示，停止顯示舊光學血壓快取值。專案公開雙端原始碼、測試 APK、中英文文件，以及資料庫、論文與演算法用途對照；MIT-BIH 的離線 QRS 基準和表現不佳的腕式 PPG 基準均如實列出，不能推論本手錶的臨床準確率。非醫療器材。

## English

**Project title:** AILISA Health Bridge | Android × Wear OS Health Data Research

**Project URL:** https://ailisa.net/portfolio/ailisa-health-bridge

**GitHub:** https://github.com/jay19821026-dev/Watch2

**Description:**

An open-source Android phone and Xiaomi Watch 2 Pro / Wear OS project by Wang You-Jie. It uses Wear OS Health Services, the Wearable Data Layer, and Health Connect to organize user-authorized health data with visible source, timing, quality, and sync status. Version 4.0 test 18 includes heart-rate and activity records, SpO₂ recording-method filters, a raw ECG research card, and user-tapped links to OEM watch screens. Static inspection of the installed Xiaomi Health watch app separates its guided breathing exercise from BioZ respiratory-rate research and its typed SpO₂, temperature, and stress results from public raw sensor events and this project's HRV heuristic. The OEM phone app marks its BP and glucose add-record paths as manual; this update corrects optical BP claims and suppresses legacy cached BP values. Both source modules, test APKs, bilingual documentation, and a dataset–paper–algorithm use map are public. The offline MIT-BIH QRS benchmark and poor simple wrist-PPG baseline are disclosed without claiming clinical accuracy for this watch. Not a medical device.

## Evidence before posting / 發布前核對

- Test 18 source and APKs use the public name AILISA Health Bridge and new package `net.ailisa.healthbridge`; the GitHub Release and updated ailisa.net page are prepared but must be verified after publication. The phone migration bridge completed one on-device import; keep the old app until the owner checks counts. The paired watch remains below the 40% install threshold, so test 18 Wear UI, sensors and bidirectional sync are unverified. Do not claim ECG was unlocked or any derived metric clinically validated.
- Add the project to the user's own LinkedIn profile only after the authenticated profile and destination fields can be verified. Record the resulting project URL after saving.
