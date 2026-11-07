# Xiaomi Health Bridge 4.0.0 test 12 / 測試版 12

作者 / Author: 王宥傑 (Wang You-Jie)

## 繁體中文

依照已安裝的 Mi Fitness 3.59.2i 睡眠資料流程，確認原廠把睡眠時段、睡眠與清醒時長及分期是否存在分開處理。手機版睡眠專頁現在以使用者授權的 Health Connect 資料顯示：只有來源分期完整覆蓋時段、類型已知且來源時段不重疊時，主數值才扣除清醒時間並標示為睡眠時長；其餘情況顯示去重後的時段，明示可能包含清醒。近期時段附來源 App 套件名稱。沒有移植原廠私有睡眠模型、分數或訓練資料。

先前 test 10/11 的 ECG 研究預覽時間中斷與起始 20 事件畫線規則仍保留。手錶版沒有新增睡眠演算法，但與手機版一起重新建置為 `versionCode 160`，供雙端側載。這是 Debug 簽章測試版，非 Play Store 版本。雙端單元測試與 Debug 建置通過；實際手機 Health Connect 睡眠來源、戴錶感測與同步仍須實機核對。

反組譯流程與資料來源詳見[睡眠紀錄對照](docs/MI_FITNESS_3_59_2I_SLEEP_RECORD_PIPELINE.md)、[原廠功能稽核](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md)及[資料庫、論文與演算法溯源](docs/ALGORITHM_DATASET_PAPER_MAP.md)。

## English

The installed Mi Fitness 3.59.2i sleep path treats session span, sleep/wake duration, and stage availability as distinct report fields. The phone Sleep page now applies that distinction to user-authorized Health Connect data. It subtracts awake intervals and labels the main value as sleep time only when known, valid stages fully cover non-overlapping sessions. Otherwise it shows the deduplicated session span and says it may include waking. Recent rows show the source app package. No private OEM sleep model, score, training data, or sleep record is imported.

The ECG research-preview continuity and first-20-event drawing rules from tests 10 and 11 remain. Both phone and watch Debug APKs are rebuilt as `versionCode 160` for sideloading; the watch gains no new sleep algorithm in this release. Both unit-test suites and Debug builds passed. Actual Health Connect sleep provenance, on-wrist sensor behavior, and paired sync still require device verification. This is not a Google Play release.

See the [sleep record comparison](docs/MI_FITNESS_3_59_2I_SLEEP_RECORD_PIPELINE.md), [OEM audit](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), and [dataset–paper–algorithm map](docs/ALGORITHM_DATASET_PAPER_MAP.md).
