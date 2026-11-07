# Xiaomi Health Bridge 4.0.0 test 14 / 測試版 14

作者 / Author: 王宥傑 (Wang You-Jie)

## 中文

- 從已安裝 Xiaomi Health 手錶 APK 確認：「呼吸」模組是 1–5 分鐘引導練習，結果畫面可顯示練習前後心率，心率由 Health Services 訂閱；這不是 BioZ 呼吸速率感測。
- 手錶首頁新增使用者點擊的原廠呼吸練習入口。它只開啟已匯出的 Xiaomi Health 畫面，不複製原廠程式、演算法或結果。
- BioZ 呼吸研究頁、原廠功能列表及中英文稽核文件明確區分「呼吸練習」與「呼吸速率」。新增 NIH NCCIH 與系統性回顧作為背景引用，並標示它們不提供小米演算法或本裝置校準。
- 保留 test 13 的 BioZ 佩戴條件與既有 ECG、睡眠資料邊界。

## English

- Static inspection of the installed Xiaomi Health watch APK identifies its `breath` module as a 1–5-minute guided exercise with optional start/end heart-rate display supplied by Health Services. This is separate from BioZ respiratory-rate research.
- A user-tapped watch-home shortcut opens the exported Xiaomi exercise screen. No OEM code, algorithm, service result, or private protocol is imported.
- The BioZ research page, OEM feature list, bilingual audit, and literature map now distinguish an exercise from a respiratory-rate measurement. NIH NCCIH and a peer-reviewed meta-analysis are background references, not device calibration or an app treatment claim.
- Retains test 13's BioZ wear-state gate and existing ECG/sleep evidence limits.

## Downloads / 下載

| Device / 裝置 | Asset | SHA-256 |
|---|---|---|
| Android phone / 手機 | `XiaomiHealthBridge-Mobile-4.0.0-vc162-test14-debug.apk` | `315cc324724226f3756dab9aa2b4a8cdb72c5b01a3b7a53a6de86dff10116bb6` |
| Wear OS watch / 手錶 | `XiaomiHealthBridge-Wear-4.0.0-vc162-test14-debug.apk` | `6df5878a3d8520f228b30e8ca758fb86c2820f7f632c2dda499f4b6979989375` |

Both are debug-signed sideload APKs, package `com.jaysato.xiaomihealthbridge`, versionCode 162, debug certificate SHA-256 `0c35a370032bfe706ef54ec6a789bcdc7e53d19c94b444fa6b67027a3cb8750f`. Both passed unit tests, assembled, passed manifest/signature checks, and installed over the paired devices without uninstalling. The watch entered doze before a reliable UI hierarchy could be captured, so launching and completing an OEM breathing session is **not yet verified**. These are not Play Store releases. Previously signed Play candidate AABs predate the test 13 and test 14 changes and should not be uploaded.

Evidence: [OEM feature audit](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), [literature and data-use map](ALGORITHM_DATASET_PAPER_MAP.md), [bilingual installation guide](4.0_TEST_INSTALL.md). Public context: [Android Health Services heart-rate MeasureClient](https://developer.android.com/health-and-fitness/health-services/compatibility), [NIH NCCIH relaxation techniques](https://www.nccih.nih.gov/health/relaxation-techniques-what-you-need-to-know), [Laborde et al. 2022](https://pubmed.ncbi.nlm.nih.gov/35623448/).
