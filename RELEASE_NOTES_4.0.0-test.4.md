# Xiaomi Health Bridge 4.0.0 test 4 / 測試版 4

作者 / Author: 王宥傑 (Wang You-Jie)

## 繁體中文

此測試版提供 Android 手機及 Xiaomi Watch 2 Pro（Wear OS）兩支 APK。手錶 ECG 研究頁現在依照這支錶已安裝原廠 APK 的可觀察邏輯，同時檢查原始事件第 11 欄（索引 10）的 `1000` 標記，以及 Android 離腕感測器在事件時間附近回報的配戴狀態。缺少、過期、時間順序不符或離腕狀態會中斷候選值預覽；原始封包仍可在既有限額內保存。60 秒的狀態時效是本專案採用的保守規則，並非原廠常數。

這項修改只影響未校準的研究預覽。離腕偵測不證明手指碰觸電極，也不提供 ADC 至 mV 換算、樣本時脈、節律分類或醫療診斷。原廠頁面目前仍要求從手機 Mi Fitness 的 ECG 卡片正式啟用；使用者回報配對手機沒有該卡片。沒有改寫原廠 `ecgActivation`、私有服務或帳戶授權。

手機版保留 Health Connect 來源與血氧紀錄方式篩選，以及獨立 ECG 研究卡片。兩支 APK 都是 **debug 簽章的側載測試版，並非 Google Play 正式版**。更新前請核對 SHA-256；若舊版簽章不同，先備份資料，勿直接解除安裝。

詳細資料：[Watch 2 Pro 原廠功能稽核](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md)、[原廠 APK 功能拆解](docs/OEM_APK_CAPABILITY_BREAKDOWN.md)、[資料庫、論文及演算法溯源](docs/ALGORITHM_DATASET_PAPER_MAP.md)、[隱私政策](docs/PRIVACY_POLICY.zh-TW.md)。

## English

This test release contains Android phone and Xiaomi Watch 2 Pro Wear OS APKs. The watch ECG research view now follows two observable conditions from the OEM APK installed on this watch: field 10 of a raw event equals `1000`, and a recent Android off-body sensor event reports on-body status. Missing, stale, future-dated, or off-body status breaks the candidate preview. Bounded raw packets remain available. The 60-second freshness window is our conservative app policy, not an extracted OEM constant.

This changes only the **uncalibrated research preview**. On-body detection does not establish finger or electrode contact, ADC-to-mV scale, sample timing, rhythm classification, or clinical validity. The OEM ECG page still asks for official activation from the phone Mi Fitness ECG card; the owner reports that the paired phone has no such card. This release does not modify the OEM `ecgActivation` flag, private service, or account authorization.

The phone build retains source-aware Health Connect and SpO₂ recording-method filters and its independent ECG research card. Both APKs are **debug-signed sideload tests, not Google Play releases**. Verify SHA-256 before updating. If an older build has another signer, back up data before any uninstall.

Evidence: [Watch 2 Pro OEM audit](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), [OEM APK capability breakdown](docs/OEM_APK_CAPABILITY_BREAKDOWN.md), [dataset, paper and algorithm provenance](docs/ALGORITHM_DATASET_PAPER_MAP.md), [privacy policy](docs/PRIVACY_POLICY.en.md).
