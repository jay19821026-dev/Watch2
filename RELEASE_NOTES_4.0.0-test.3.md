# Xiaomi Health Bridge 4.0.0 test 3 / 測試版 3

作者 / Author: 王宥傑 (Wang You-Jie)

## 繁體中文

這個測試版含 Android 手機與 Xiaomi Watch 2 Pro（Wear OS）APK。手機新增獨立的「ECG 心電圖」卡片與原廠啟用狀態說明；手錶 ECG 研究頁以實際安裝原廠 APK 的封包線索，預覽符合條件的未校準原始候選值。沒有擷取時，手機會顯示待機狀態。

新增[原廠 APK 功能拆解](docs/OEM_APK_CAPABILITY_BREAKDOWN.md)：逐項區分手機共用程式的功能鍵、這隻 Watch 2 Pro 的即時感測器清單、原廠服務的監測開關與量測路徑。實機宣告 ECG、PPG、血氧相關、心率、BioZ 與溫度通道。這些證據不代表原廠 ECG 已獲啟用或具醫療效度。原廠 ECG 目前仍顯示需由手機 Mi Fitness 卡片啟用；使用者在配對手機上未見該卡片。

安裝對應的手機與手錶 APK，並自行確認權限與資料同步。這是 **debug 簽章測試版，非 Google Play 正式版**。已安裝較早測試版時，若簽章不同可能需先備份資料再安裝；不要在未備份下解除安裝。

詳細來源與限制：[Watch 2 Pro 4.0 原廠稽核](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md)、[功能拆解](docs/OEM_APK_CAPABILITY_BREAKDOWN.md)、[資料庫及論文溯源](docs/ALGORITHM_DATASET_PAPER_MAP.md)、[隱私政策](docs/PRIVACY_POLICY.zh-TW.md)。

## English

This test build contains Android phone and Xiaomi Watch 2 Pro Wear OS APKs. The phone now has an independent ECG card with an explanation of OEM activation, and shows an idle state when no capture is active. The watch ECG research view previews uncalibrated candidate fields according to packet clues from the exact OEM APK installed on this watch.

The new [OEM APK capability breakdown](docs/OEM_APK_CAPABILITY_BREAKDOWN.md) separates shared phone feature keys, the live Watch 2 Pro sensor inventory, OEM monitoring switches, and actual measurement paths. The watch advertises ECG, PPG, SpO₂-related, HR, BioZ and temperature channels. This does not mean OEM ECG is activated or medically validated. The OEM ECG page still requests activation from a Mi Fitness phone card, which the owner does not see for this paired watch.

Install the matching phone and watch APKs and check permissions and sync. These are **debug-signed test builds, not Google Play releases**. If an older test build uses a different signature, back up app data before any uninstall.

Evidence and limits: [Watch 2 Pro OEM audit](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), [capability breakdown](docs/OEM_APK_CAPABILITY_BREAKDOWN.md), [dataset and paper provenance](docs/ALGORITHM_DATASET_PAPER_MAP.md), [privacy policy](docs/PRIVACY_POLICY.en.md).
