# LinkedIn project copy / LinkedIn 專案欄位文案

Author / 作者：王宥傑 (Wang You-Jie)
Prepared / 撰寫：2026-11-08
Publication status / 發布狀態：**draft; LinkedIn profile has not been updated or verified** / 草稿；尚未更新或核對 LinkedIn 個人檔案。

## Traditional Chinese / 繁體中文

**專案名稱：** AILISA Health Bridge｜Android × Wear OS 健康資料研究

**專案連結：** https://ailisa.net/portfolio/ailisa-health-bridge

**相關 GitHub：** https://github.com/jay19821026-dev/Watch2

**說明：**

由王宥傑獨立開發的開源 Android 手機與 Wear OS 健康資料專案，以 Xiaomi Watch 2 Pro 作為相容性研究裝置。使用 Wear OS Health Services、Wearable Data Layer 與 Health Connect 整理授權的心率、活動、睡眠紀錄，顯示來源、量測時間、品質與同步狀態。4.0 test 32 對照反組譯的 Mi Fitness 與原廠手錶資料流，修正心率逐樣本時間與詳細頁跨日篩選、跨界睡眠時段、血氧量測時間，以及心率／血氧／腕部體溫圖表時間分桶；不再以低心率跨度推估睡眠，也不顯示缺乏本裝置驗證的 VO₂max 或血氧分數；血氧摘要先排除零值。手機分析頁新增每日資料報告，列出當日紀錄的來源、時間、同來源昨日差值與缺漏；文字由本機規則產生。GitHub 提供手機／手錶原始碼、雙端測試 APK、中英文文件及資料庫、論文、演算法用途對照。ECG、PPG 與 BioZ 原始事件仍屬研究資料，不宣稱已解鎖原廠功能或具醫療診斷能力。test 32 已通過雙端建置與單元測試，尚待配對裝置量測驗證。

## English

**Project title:** AILISA Health Bridge | Android × Wear OS Health Data Research

**Project URL:** https://ailisa.net/portfolio/ailisa-health-bridge

**GitHub:** https://github.com/jay19821026-dev/Watch2

**Description:**

An open-source Android phone and Wear OS health-data project independently developed by Wang You-Jie, using Xiaomi Watch 2 Pro for compatibility research. Wear OS Health Services, the Wearable Data Layer and Health Connect organize authorized heart-rate, activity and sleep records with visible source, measurement time, quality and sync state. Version 4.0 test 32 compares decompiled Mi Fitness and OEM watch data paths to correct per-sample heart-rate timing and detail-window filtering, boundary-crossing sleep intervals, oxygen measurement time, and heart-rate/oxygen/wrist-temperature chart buckets. It no longer treats low-heart-rate spans as sleep or displays unvalidated VO₂max or oxygen scores; zero-valued oxygen records are excluded from the descriptive summary. A new daily phone report lists same-day records, source and time, same-source differences from yesterday, and missing inputs using local rules. Phone and watch source, test APKs, bilingual documentation and a dataset–paper–algorithm use map are public. Raw ECG, PPG and BioZ events remain research-only; no OEM feature unlock or medical-diagnostic claim is made. Test 32 passed both builds and unit tests; paired-device measurement validation is pending.

## One-field bilingual version / 單一欄位中英文短版

王宥傑開發的 Android 與 Wear OS 開源健康資料研究專案，以 Xiaomi Watch 2 Pro 為相容性研究裝置。4.0 test 32 對照原廠資料流修正量測時間、睡眠時段、血氧零值與腕部體溫圖表，並新增本機每日資料報告；雙端原始碼、測試 APK、論文與資料庫用途對照已公開。ECG／PPG／BioZ 僅供研究；雙端建置與單元測試通過，真機量測待驗證。

Open-source Android and Wear OS health-data research by Wang You-Jie. Test 32 corrects timing, sleep, zero-value oxygen handling and wrist-temperature views, and adds a locally generated daily data report using inspected OEM data paths. Phone/watch source, test APKs and research provenance are public. ECG/PPG/BioZ remain research-only; paired-device measurement validation is pending.

Project: https://ailisa.net/portfolio/ailisa-health-bridge

## Evidence before posting / 發布前核對

- Test 32 is public on GitHub Release and ailisa.net (Site version 202). Both APKs passed builds and unit tests, but ADB listed no devices before publication; no test 32 install or live measurement is claimed. The site's watch photo is an older test 20 capture. Signed Play AABs have not been uploaded. Do not claim OEM ECG unlock or clinical validation.
- Add the project to the user's own LinkedIn profile only after the authenticated profile and destination fields can be verified. Record the resulting project URL after saving.
