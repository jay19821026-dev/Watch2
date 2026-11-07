# Xiaomi Health Bridge 4.0.0 test 9 / 測試版 9

作者 / Author: 王宥傑 (Wang You-Jie)

## 繁體中文

本版根據**已安裝在配對手機上的 Mi Fitness 3.59.2i 反組譯程式**，修正 ECG 啟用說明。裝置設定中的 ECG 卡片檢查產品功能清單的 `ecg`；Play 版的量測提醒另檢查 `electrocardiogram_global`。官方啟用畫面透過裝置同步呼叫送出開啟要求，並在同步呼叫回報成功後寫入手機偏好值。該呼叫設定為不要求回傳狀態，因此成功回呼**不等於已讀回手錶的 ECG 啟用狀態**。

手機診斷紀錄中的 65 筆 Eco 產品快取列出三種 Watch 2 Pro 型號，均未列出上述 ECG 功能鍵；Watch 5 有 `electrocardiogram_global`。Eco 快取與 ECG 卡片使用的產品物件屬不同管理路徑，不能當作配對裝置的最終授權清單，也不能證明改顯示型號就能啟用 ECG。

手機 ECG 頁面已補上這些限制；手錶按鈕改名為「開啟原廠 ECG 頁面」，並說明它不會讀取或修改原廠啟用狀態。本 App 的原始 ECG 研究擷取與原廠啟用分開。此版**沒有開通原廠 ECG**，也沒有複製小米私有演算法或醫療認證。

雙端 APK 採相同 Debug 憑證、`versionCode 155`，供保留資料的覆蓋側載測試；已在配對手機與 Watch 2 Pro 覆蓋安裝並啟動。尚未完成手腕實測、ECG 正式啟用或 Play Store 發布。資料庫、論文與本專案演算法的對應關係見[來源表](docs/ALGORITHM_DATASET_PAPER_MAP.md)，原廠程式證據見[稽核文件](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md)。

## English

This build corrects the ECG activation explanation using the decompiled **Mi Fitness 3.59.2i installed on the paired phone**. The device-settings ECG card checks the product feature `ecg`; the Play-channel measurement reminder separately checks `electrocardiogram_global`. The official activation screen sends a device-sync request and writes a phone preference after a successful sync callback. That set call does **not** request a state response, so the callback is not a confirmed readback of the watch's ECG activation state.

A logged 65-product Eco cache includes three Watch 2 Pro variants without these ECG keys and a Watch 5 variant with `electrocardiogram_global`. The Eco cache and ECG card use different product-manager paths. The cache is not the paired device's effective entitlement, and changing a displayed model name does not establish activation.

The phone ECG page now explains these gates. The watch button is labeled `開啟原廠 ECG 頁面` (“Open OEM ECG page”) and explicitly says that it neither reads nor changes OEM activation. The independent raw ECG research capture remains separate. This build does **not** unlock OEM ECG, copy Xiaomi's private algorithms, or establish medical validation.

Both APKs are debug-signed, `versionCode 155`, and suitable for a data-preserving sideload update when the installed certificate matches. They were installed over the existing builds and launched on the paired phone and Watch 2 Pro. Live on-wrist capture, OEM ECG activation and Google Play publication remain unverified. See the [dataset/paper/algorithm map](docs/ALGORITHM_DATASET_PAPER_MAP.md) and [OEM source audit](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md).
