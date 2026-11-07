# Xiaomi Health Bridge 4.0.0 test 11 / 測試版 11

作者 / Author: 王宥傑 (Wang You-Jie)

## 繁體中文

依照已安裝於 Watch 2 Pro 的原廠 Xiaomi Health APK 反組譯路徑 `EcgSensorUtil.kt`，原廠在一次 ECG 量測開始後先略過 20 個感測器回呼，接著才檢查第 11 欄 `1000` 標記與配戴狀態。本版只在**研究預覽畫線**略過相同的前 20 個事件；所有原始事件仍照原樣保存，供重播與檢查。原有的無效／倒退時間戳及超過一秒事件間隔斷線規則繼續適用。

手錶原廠路徑依腕向設定有條件地反轉訊號正負，並把候選值送入私有原生演算法。本專案保留原始正負值與實際 Android 事件時間，不移植原廠合成的 2 毫秒時間軸、私有濾波器、mV 比例或診斷。手機原廠 ECG 紀錄另有自己的取樣頻率及 25 秒收件條件，不能套用到原始感測事件。

雙端側載 Debug APK 同步更新為 `versionCode 159`。手錶單元測試與 Debug 建置通過；實際戴錶 ECG 訊號、原廠啟用與臨床效能仍待獨立驗證。完整原廠對照見[功能稽核](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md)及[手機 ECG 紀錄路徑](docs/MI_FITNESS_3_59_2I_ECG_RECORD_PIPELINE.md)。

## English

The installed Xiaomi Health watch APK's reconstructed `EcgSensorUtil.kt` ignores the first 20 ECG sensor callbacks in each measurement before applying its field-11 `1000` marker and wear-state gate. This build omits those 20 start-up events **only from the research preview line**. Every raw callback remains retained for replay and inspection. The existing break on missing/nonincreasing timestamps or event gaps over one second remains in place.

The OEM watch path conditionally reverses signal sign for wrist orientation and feeds candidates into a private native algorithm. Our app preserves raw sign and real Android event timestamps; it does not import the OEM's synthetic two-millisecond time axis, private filter, mV scaling, or diagnosis. The phone OEM record has its own sample frequency and 25-second intake gate, neither of which applies to our raw sensor packets.

Both debug-signed sideload APKs are rebuilt as `versionCode 159`. Wear unit tests and Debug build passed. Live on-wrist ECG behavior, OEM activation and clinical performance remain unverified. See the [OEM feature audit](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md) and [phone ECG record trace](docs/MI_FITNESS_3_59_2I_ECG_RECORD_PIPELINE.md).
