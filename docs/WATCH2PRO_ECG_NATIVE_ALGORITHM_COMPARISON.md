# Watch 2 Pro ECG native path and public watch algorithms

作者：王宥傑。2026-11-08。這是唯讀靜態分析與公開文獻對照，不是原廠原始碼或臨床驗證。

## 本錶原廠程式實際看到什麼

分析本機從已安裝 Xiaomi Health 手錶 APK 解包的 `armeabi-v7a/libfitness.so`，SHA-256 `8EBC98CB0D256C3E1ACAC0BE583F209663887513659087D5492CB2B20F6B240B`，188,392 bytes。只檢視可見的 ASCII／ELF 名稱；沒有修改手錶、服務或 APK，沒有讀取個人健康資料，也沒有把函式庫提交到本儲存庫。

1. Java 包裝器 `EcgJni` 暴露 `algInputSignal`、`algMovingInputSignal`、`getAnalysisResults`、`getAverageHr`、`init`、`reset`、`cleanUp`。手錶端 `EcgSensorUtil` 在佩戴檢查及起始回呼處理後，傳 ECG 候選值與時間到原生函式，另傳加速度；詳見 [本錶原廠功能稽核](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md)。
2. 原生函式庫保留可辨識名稱：`digital_filter`／`highpass_filter`、`qrs_detection_process`、`find_valid_rr_for_hr`、`sqa_process`／`sqa_hr_valid_assessment`、`wear_status_model`、`af_feature_extraction`、`af_model`、`lr_model`、`nsr_duration`。這支持一條**推論**流程：濾波與接觸／動作檢查 → QRS/RR 與心率 → 訊號品質 → AF／正常竇性心律相關模型與不確定結果。名稱只證明模組存在，不提供輸入單位、參數、權重、閾值或臨床性能。
3. 庫中版本字串為 `MiECG-0.0.22-arm-androideabi28-M61`。版本字串不能證明這隻 Watch 2 Pro 已獲原廠 ECG 啟用；實機仍顯示 Activation Guidance。手機 Mi Fitness 的正式 ECG 卡片使用裝置報告內的 `analysisResult`，與 AILISA 的 Android 原始感測事件不是同一資料型別；詳見 [手機 ECG 紀錄流程](MI_FITNESS_3_59_2I_ECG_RECORD_PIPELINE.md)。

## 公開系統的可借鏡部分

| 系統 | 公開證據 | AILISA 可獨立改進之處 |
| --- | --- | --- |
| Apple Watch | [Apple 心率與活動驗證白皮書](https://www.apple.com/health/pdf/Heart_Rate_Calorimetry_Activity_on_Apple_Watch_November_2024.pdf)描述光學脈率與參考 ECG 在不同活動情境下的比較；[HealthKit HRV 文件](https://developer.apple.com/documentation/healthkit/hkquantitytypeidentifier/heartratevariabilitysdnn)明示 Apple HRV 用 SDNN。 | 評估必須按靜止／運動分層，並按指標標明 RMSSD 或 SDNN；不能拿兩種 HRV 數字直接比大小。 |
| Google Pixel Watch | [Google Research 論文摘要](https://research.google/pubs/pixel-watch-robust-heart-rate-sensing-from-multipath-ppg-and-on-device-deep-learning-trained-on-10000-hours-of-free-living-and-fitness-data/)描述多光路 PPG、運動條件與獨立驗證集。 | 只有確認多通道定義、時鐘、佩戴與同步參考資料後，才可訓練或評估自己的模型；不能把本錶未映射的原始欄位當成 Pixel 通道。 |
| Garmin | [Garmin HRV Status 說明](https://www.garmin.com/en-US/blog/fitness/understanding-the-hrv-status-on-your-garmin-smartwatch/)用睡眠期間資料建立個人基準，強調長期趨勢。 | AILISA 現有 RMSSD 個人趨勢只與近期同來源紀錄比較；本次新增 21 天內的基準時限，避免很久以前的紀錄混成現況。這是本專案規則，不是 Garmin 的 21 夜演算法。 |
| Samsung | [Samsung ECG 使用說明](https://www.samsung.com/us/support/answer/ANS10003433/)分開顯示竇性心律、房顫、品質不佳及無法分類。 | 把「量測品質不佳」和「心律分類」做成不同狀態；沒有有效波形時，不能硬判正常或異常。 |
| Android 公開 API | [Wear OS Health Services](https://developer.android.com/health-and-fitness/health-services)已配置裝置感測器並提供心率等計算結果；[相容性說明](https://developer.android.com/health-and-fitness/health-services/compatibility)指出不同裝置資料頻率與可用性不同。 | 優先使用帶單位與時間的 Health Services 心率；原始 PPG/ECG/BioZ 保持獨立來源與標籤。 |

## 本次實作與下一個驗證門檻

- `SensorHealthAlgorithms.hrvDeviationFromPersonalBaseline` 原先取最近六個**有紀錄日**，可能跨越很久；現在只取最新日之前 21 個日曆天內最多六個有紀錄日。不足兩日則不建立個人比較，但仍可顯示當前 HRV 讀值。這是避免過時基準的資料窗口修正，不是疾病判讀。
- `AiHealthAnalysisPipeline` 的一般畫面警告移除 Java 例外類名，保留具體可執行的讀取／重試狀態。
- 若要建立可與原廠分類比較的獨立 ECG 模型，先取得**這支錶**可驗證的波形欄位、取樣時鐘、接觸與動作訊號、同步參考 ECG 及分離的驗證集。現有 `libfitness` 名稱不能替代這些資料，更不能單獨證明 AF 敏感度或特異度。
