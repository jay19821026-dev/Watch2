# Xiaomi Health Bridge 4.0.0 test 10 / 測試版 10

作者 / Author: 王宥傑 (Wang You-Jie)

## 繁體中文

本版延續已安裝的 **Mi Fitness 3.59.2i** 與 Watch 2 Pro 原廠程式分析。手機原廠 ECG 紀錄包含報告取樣頻率、量測時長及原始資料；原廠在儲存前檢查至少 25 秒的紀錄長度，顯示時使用私有濾波器。這條紀錄路徑與本專案讀取 Android 感測器 `65573` 的原始事件不同，不能用原廠取樣率或顯示比例校準我們的事件。

手錶 ECG **研究預覽**保留已觀察到的 16 欄封包、第 11 欄 `1000` 標記及時間對齊配戴檢查；這次另要求事件時間戳有效且遞增，超過一秒的事件間隔會斷開畫線，避免把分離的原始事件畫成連續訊號。一秒是本專案的顯示規則，不是小米 ADC 取樣率或正式 ECG 紀錄驗收條件。此版未解鎖原廠 ECG、未匯入小米私有演算法，也不輸出未驗證的 mV、心律或診斷。

手機功能與 test 9 相同，但雙端 APK 均重新建置為 `versionCode 158`，使用相同的 Android Debug 簽章供側載測試。原廠資料流的逐段證據見[ECG 紀錄分析](docs/MI_FITNESS_3_59_2I_ECG_RECORD_PIPELINE.md)；資料庫、論文與演算法的實際使用／候選狀態見[溯源表](docs/ALGORITHM_DATASET_PAPER_MAP.md)。

## English

This build follows a static trace of the installed **Mi Fitness 3.59.2i** and Watch 2 Pro OEM apps. An OEM phone ECG record includes its own sampling frequency, duration and source data; the OEM checks for at least 25 seconds before storing a record and uses a private filter for display. That typed record path is distinct from this project's Android sensor `65573` raw events. Its frequency or display scaling cannot calibrate our events.

The watch **research preview** retains the observed 16-field packet, field-11 `1000` marker and time-aligned on-body check. It now requires valid, increasing event timestamps and breaks the drawn line after a gap longer than one second, preventing disconnected raw batches from appearing continuous. One second is our display rule, not Xiaomi's ADC sample period or official ECG record criterion. This build does not unlock OEM ECG, import Xiaomi's private algorithms, or emit unverified mV, rhythm or diagnoses.

Phone behavior is unchanged from test 9, but both APKs are rebuilt as `versionCode 158` with the same Android Debug signing certificate for sideload testing. See the [ECG record trace](docs/MI_FITNESS_3_59_2I_ECG_RECORD_PIPELINE.md) and [dataset, paper and algorithm provenance](docs/ALGORITHM_DATASET_PAPER_MAP.md). Google Play publication and on-wrist clinical validation are not claimed.
