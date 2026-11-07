# Xiaomi Health Bridge 4.0.0 test 16 / 測試版 16

Author / 作者：王宥傑 (Wang You-Jie)

## 中文

手機與 Xiaomi Watch 2 Pro／Wear OS 雙端測試 APK，版本碼均為 `166`。手錶 HRV 研究頁現在清楚區分本專案的壓力啟發式與小米健康的原廠壓力量測，並提供由使用者點擊的原廠壓力頁入口；離開研究頁前會停止本 App 的擷取。這項修正依據已安裝原廠手錶 APK 的靜態對照：原廠壓力頁使用型別化服務 `20486`，並在離腕時中止量測。本專案沒有移入原廠分數、演算法或私有服務。

研究文件現在清楚標出資料庫的實際使用狀態。PhysioNet MIT-BIH 1.0.0 的離線 XQRS 基準已處理 48/48 筆紀錄，145/145 個必要檔案通過官方 SHA-256 清單核對；150 ms 一對一節拍比對的 pooled sensitivity 為 99.20%，PPV 為 99.30%。這只驗證離線偵測流程，不是 Watch 2 Pro ECG 解碼、房顫偵測或醫療準確率。腕式 PPG 公開資料庫的簡單基準結果較差，也在[來源對照](ALGORITHM_DATASET_PAPER_MAP.md)中如實揭露。

**驗證範圍：**手機 1,071 項及手錶 240 項 Debug 單元測試通過；雙端 APK 建置、套件／版本資訊與 Debug 簽章核對通過。手錶當時電量 12%，test 16 **尚未安裝到配對手錶或完成畫面／感測器真機驗證**。原廠 ECG 仍由原廠啟用狀態決定；此測試版不是醫療器材，也不是 Google Play 正式版。

## English

These are sideload test APKs for the Android phone and Xiaomi Watch 2 Pro / Wear OS, both at version code `166`. The watch HRV research page now distinguishes this project's stress heuristic from Xiaomi Health's OEM stress measurement. A user-tapped button opens the OEM stress screen after stopping this app's capture. Static inspection of the installed watch APK shows a typed OEM stress service `20486` and an off-wrist stop path. No OEM score, algorithm, or private service was imported.

The [dataset and paper map](ALGORITHM_DATASET_PAPER_MAP.md) now separates completed benchmarks from candidates. An offline XQRS benchmark on PhysioNet MIT-BIH 1.0.0 processed 48/48 records; all 145 required input files matched the official SHA-256 manifest. With one-to-one matching at 150 ms, pooled beat sensitivity was 99.20% and positive predictive value was 99.30%. This evaluates an offline detector on another ECG system, **not** the watch's HAL decoder, AFib detection, or clinical performance. The poor simple wrist-PPG baseline is also disclosed in the source map.

**Verification:** 1,071 mobile and 240 Wear Debug unit tests passed. Both APK builds, package/version manifests, and Debug signatures were checked. The paired watch was at 12% battery, so test 16 **has not yet been installed or verified through its UI or sensors on that watch**. OEM ECG activation remains controlled by the OEM app. This is neither a medical device nor a Google Play production build.

## Files / 檔案

| File | SHA-256 |
|---|---|
| `XiaomiHealthBridge-Mobile-4.0.0-vc166-test16-debug.apk` | `876c467e22f416f0f7d6783fe132c2be960a270cdf93de28c4647be7033aa863` |
| `XiaomiHealthBridge-Wear-4.0.0-vc166-test16-debug.apk` | `2224f342935c864dab1a12b48b28dffbda3a862d550a5998f9889387acd6da19` |

Both use package `com.jaysato.xiaomihealthbridge` and the same Android Debug certificate. Follow the [bilingual install and test guide](4.0_TEST_INSTALL.md). Research evidence and limits: [OEM feature audit](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), [dataset/paper map](ALGORITHM_DATASET_PAPER_MAP.md), [PhysioNet MIT-BIH source](https://physionet.org/content/mitdb/1.0.0/).
