# LinkedIn project copy / LinkedIn 專案欄位文案

Author / 作者：王宥傑 (Wang You-Jie)
Prepared / 撰寫：2026-11-08
Publication status / 發布狀態：**draft; LinkedIn profile has not been updated or verified** / 草稿；尚未更新或核對 LinkedIn 個人檔案。

## Traditional Chinese / 繁體中文

**專案名稱：** AILISA Health Bridge｜Android × Wear OS 健康資料研究

**專案連結：** https://ailisa.net/portfolio/ailisa-health-bridge

**相關 GitHub：** https://github.com/jay19821026-dev/Watch2

**說明：**

由王宥傑獨立開發的開源 Android 手機與 Wear OS 健康資料專案，支援 Xiaomi Watch 2 Pro。以 Wear OS Health Services、Wearable Data Layer 與 Health Connect 整理使用者授權的心率、活動、睡眠等紀錄，並標示來源、時間、品質與同步狀態。4.0 test 24 對照 Mi Fitness 3.53.0i 與配對手機 3.59.2i 的步數及活動熱量同步：原廠依內部來源分組後取最高合計值。本專案優先使用 Health Connect 的平台彙總；不可用時檢查來源與時段，避免重疊紀錄相加，並標示備援值不是去重或完整日總量。手錶首頁已實機修正重疊文字。GitHub 提供雙端原始碼與測試 APK、中英文文件，以及公開資料庫、論文與演算法用途對照。ECG 原始事件僅供研究，不宣稱已解鎖原廠 ECG 或具醫療診斷能力。

## English

**Project title:** AILISA Health Bridge | Android × Wear OS Health Data Research

**Project URL:** https://ailisa.net/portfolio/ailisa-health-bridge

**GitHub:** https://github.com/jay19821026-dev/Watch2

**Description:**

An open-source Android phone and Wear OS health-data project independently developed by Wang You-Jie, compatible with Xiaomi Watch 2 Pro. It uses Wear OS Health Services, the Wearable Data Layer and Health Connect to organize user-authorized heart-rate, activity and sleep records with visible provenance, timing, quality and sync state. Version 4.0 test 24 compares Mi Fitness 3.53.0i and the paired phone's 3.59.2i step and active-calorie synchronization, where the OEM groups internal sources and takes the highest source total. AILISA prefers Health Connect's platform aggregate; its fallbacks check source intervals rather than adding overlapping records, and label the source maximum as neither a deduplicated nor a complete daily total. The watch home layout was corrected on the paired device. Phone and watch source, test APKs, bilingual documentation and a dataset–paper–algorithm use map are public. Raw ECG events remain research-only; no OEM ECG unlock or medical-diagnostic claim is made.

## Evidence before posting / 發布前核對

- Test 24 is public on GitHub Release and ailisa.net (Site version 194); the real test 20 Wear screenshot remains on the site and its home layout carries into test 24. Both test 24 APKs installed; the phone's ungranted Health Connect state remains. Watch sensors, consented Mi Fitness records and bidirectional sync remain unverified. Signed Play AABs are local candidates only and have not been uploaded. Do not claim OEM ECG unlock or clinical validation.
- Add the project to the user's own LinkedIn profile only after the authenticated profile and destination fields can be verified. Record the resulting project URL after saving.
