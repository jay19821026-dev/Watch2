# Health Connect compatibility / Health Connect 相容性

作者 / Author: 王宥傑 (Wang You-Jie)

## 繁體中文

Health Connect 是 Android 的健康資料交換層，不是手錶專屬的付費「Premium」功能。AILISA 手錶版先用 Wear OS Health Services／公開感測器取得支援的紀錄，透過 Wearable Data Layer 傳到配對手機；手機版在使用者授予對應 Health Connect 寫入權限後，才能寫入合適的標準紀錄。手機版也可在取得讀取權限後讀取 Health Connect 中由其他 App 寫入的紀錄，但**其他 App 是否願意讀取 AILISA 資料，取決於該 App 自身的權限及實作**。

目前程式碼可查證的手錶同步寫入路徑：Health Services 心率 → `HeartRateRecord`；手錶活動／運動資料 → 步數、距離、熱量、運動等適用的 Health Connect 紀錄。程式另外具備使用者手動輸入的體重、血壓、靜息心率、HRV、呼吸速率、血氧及睡眠紀錄寫入路徑；**手動輸入不等於 Watch 2 Pro 自動量測**。`StandardHealthWriter` 有血氧紀錄分支，但已檢查的手錶發送端沒有把原始光學事件轉成百分比並自動送入該分支；不能宣稱 AILISA 已將原廠血氧或 ECG 自動同步到 Health Connect。

原始 ECG、PPG、BioZ 和未校準體表溫度保留研究標籤，不寫成已校準的臨床數值。可在手機 Health Connect 的「應用程式權限」核對授權，再在 AILISA 的「裝置 → 同步明細 → 核對資料來源」核對實際紀錄數、來源與時間。安裝包建置成功並不證明任何資料已成功寫入；test 31 尚待配對裝置實測。

## English

Health Connect is Android's health-data exchange layer, not a watch-specific paid “Premium” feature. The AILISA watch app obtains supported records through Wear OS Health Services and public sensors, then sends them to the paired phone through the Wearable Data Layer. The phone can write suitable standard records only after the user grants the corresponding Health Connect write permissions. It can also read other apps' records after read permission is granted. **Whether another app consumes AILISA records depends on that app's own permissions and implementation.**

The inspected automatic paths are Health Services heart rate to `HeartRateRecord`, and supported watch activity/workout intervals to applicable steps, distance, calorie and exercise records. Separate manual-entry paths can write weight, blood pressure, resting heart rate, HRV, respiratory rate, oxygen and sleep records; **manual entry is not an automatic Watch 2 Pro measurement**. Although `StandardHealthWriter` contains an oxygen-record branch, the inspected watch sender does not convert raw optical events to calibrated percentages and automatically send them through it. No automatic OEM oxygen or ECG export is claimed.

Raw ECG, PPG, BioZ and uncalibrated skin-temperature events retain research labels. Check permissions in Health Connect, then compare actual source package, record counts and times in AILISA's device sync/source audit. Successful APK builds are not evidence of successful live writes; test 31 awaits paired-device validation.

References: [Android Health Connect overview](https://developer.android.com/health-and-fitness/health-connect), [read and write data](https://developer.android.com/health-and-fitness/health-connect/write-data), and this project's [OEM source-path comparison](OEM_DATA_WINDOW_COMPARISON_4_0.md).
