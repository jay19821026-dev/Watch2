# ECG 最低可用偵測：發布門檻與目前狀態

日期：2026-10-10  
專案：Xiaomi Watch 2 Pro / Android 健康橋接  
狀態：**尚未達到可輸出正常／異常結論的門檻**

## 產品輸出定義

使用者要求量測後得到有意義結論。最低可用結果定義為三態，而非強迫每筆訊號二選一：

- **正常（本次可分析）**：本次 ECG 通過訊號品質閘門，裝置/波形格式與時間基準已驗證，演算法對應的測試範圍內未發現目標異常。
- **異常（建議留意／複測）**：本次 ECG 通過同一套品質及時間閘門，經驗證的演算法達到預先設定的異常門檻。只有在完成適用的臨床與法規驗證後，才可把特定異常名稱當作產品診斷輸出。
- **本次無法判定（需重新量測）**：波形、品質、時間或演算法輸入不符合門檻。必須說明具體原因及重新量測入口；不能把此狀態偽裝成正常或異常。

因此，「每次都輸出正常或異常」不是可靠最低標準：低品質或未解碼資料被迫二分會造成錯誤安撫或錯誤警報。最低可用要求是每次完成量測流程都有明確結論狀態，正常/異常僅在輸入合格時產生。

## 目前原始碼證據

- `health-engine/src/main/kotlin/com/jaysato/xiaomihealthbridge/engine/EcgRawEventDecoder.kt` 將目前 HAL 列視為未驗證候選欄位；沒有確認欄位就是 ECG 連續樣本。
- `mobile/src/main/java/com/jaysato/xiaomihealthbridge/EcgHalEventResearchDecoder.kt` 對收到的列回報 `decodedSampleCount = 0`、`waveform = emptyList()`、`estimatedSampleRateHz = null`。因此現在的 HAL 事件不能餵給節律偵測器。
- `EcgPayloadInterpretationPolicy`（位於 health-engine 的 ECG payload policy 原始碼）將可信波形契約開關維持關閉；心率/RR 衍生值要求已驗證波形格式、時間軸、取樣率、欄位映射及每事件樣本數。
- 手錶 ECG 畫面目前顯示候選值預覽，不代表波形；沒有可信的 R 峰、RR、心率或 AF 輸出。
- 手錶系統層可列出 ECG HAL sensor endpoint，只證明 endpoint 存在，不證明量到的 values 是 ECG 樣本或已校準訊號。

## 參考資料可以做什麼／不能做什麼

- PhysioNet/CinC 2017 單導程資料提供正常、AF、其他節律與過吵四類標籤，來源為 AliveCor，採樣率 300 Hz，適合用來做基準算法與測試流程；它不能解出 Xiaomi HAL 欄位、時鐘、導聯或增益。
- 本機 `.local-research-data/wrist/1.0.0` 的腕部資料集包含胸前 ECG、手腕 PPG/陀螺儀等通道，header 顯示 256 Hz；這可以探索跨感測器算法，但不能視為 Watch 2 Pro 的裝置驗證集。
- 本機 MIT-BIH record 100 header 顯示 360 Hz，通道與物理單位依資料 header 解碼。它是不同的臨床 Holter 資料來源，不可直接拿來標定手錶 ADC→mV。
- FDA arrhythmia detector guidance 建議按目標用途驗證節律演算法、以敏感度/特異度報告、開發集與驗證集分離，並以多個適用資料庫及合格臨床標註支撐效能；此 guidance 不能代替目前法規諮詢，也不授予本 App 醫療宣稱資格。
- MAX86178 或任何公開評估板的晶片能力不能代表 Xiaomi 成品的電極連接、AFE 設定、事件封包格式或校準常數。

## 取得「最低研究輸出」的實作階段

1. **封包契約**：以已知輸入、原廠 HAL/API 文件或可重複的硬體測試，確定完整事件欄位、marker/counter 語義、每列樣本數、來源時鐘及單位。若拿不到 OEM 契約，建立可接入的測試點，使用 ECG simulator 經實際手錶電極/AFE 路徑同步擷取；不能只憑圖形猜欄位。
2. **輸出相對研究波形**：契約確認到足以界定樣本順序後，保留 raw rows，產生獨立 normalized/index waveform；顯示來源、品質、時間域/索引域和未校準狀態。
3. **研究心搏指標**：只有同步取樣時鐘、mapping、品質門檻都通過才執行 QRS/R 峰及 RR/研究心率。沒有來源時鐘時最多顯示峰值候選索引，不換算毫秒或 bpm。
4. **二分類基準試驗**：先在公開臨床/穿戴資料做病人分組的訓練與獨立測試，含「其他節律」和「不可判讀」；報告 sensitivity、specificity、PPV、NPV、coverage/unclassified rate 及信賴區間，不能只報 accuracy 或品質分數。
5. **手錶裝置驗證**：以 Watch 2 Pro 的同步參考 ECG/模擬器及代表性使用者資料驗證 acquisition、signal quality、分類與失敗覆蓋率；資料切分不得讓同一受試者/session 同時進 train/test。
6. **上線範圍**：研究版可以輸出「研究分析：未見目標特徵／出現疑似目標特徵／本次不可判讀」並清楚標示非診斷。要輸出面向消費者的醫療「正常/異常」或 AF，須先完成 intended-use、風險管理、臨床性能及目標市場所需法規程序。

## 時間估計與真正阻塞

不能誠實給出上線日：關鍵日期取決於目前缺失的裝置證據，而非多寫幾個演算法即可解決。

- **可立即完成的軟體工作**：研究資料集 benchmark harness、三態 UI/結果契約、假資料/公開資料測試、原始事件 replay、品質與拒判率報表。
- **阻塞硬體研究輸出的資料**：一筆可重播且完整保存的實際 HAL raw event fixture、其來源時鐘與欄位契約，或同步 simulator/reference capture；需包含裝置/韌體版本、設定、接觸條件、參考波形及共同時鐘證據。
- **阻塞醫療用途發布的工作**：具代表性的獨立臨床驗證、預先設定的 acceptance criteria、標註流程、風險與法規路徑，以及目標地區主管機關/法規專業審查。

## 判定

當前可以交付的正確結論是：**ECG endpoint/原始事件路徑存在；Watch 2 Pro HAL 事件尚未證明可解碼成 ECG 波形，故目前不能可靠輸出本次正常／異常或 AF 結論。** 先把第 1 階段做成可重播證據，再進行研究算法；公開臨床資料只能驗證通用算法，不能取代這支手錶的訊號契約與校準。

## 參考訊號演算法冒煙測試（非手錶驗證）

- 本機 `physionet-wrist-ppg-exercise-1.0.0/s1_walk` 讀取 WFDB header 得到胸前 ECG 256 Hz 與 563 個 `N` R-peak 標註。
- 對 chest ECG 使用 5–20 Hz Butterworth bandpass + `find_peaks`（280 ms 最小距離、robust scale ×1.2 prominence），在 ±100 ms 配對標記：TP 506、FP 820、FN 57；sensitivity 89.88%，PPV 38.16%。
- 這是單一正常步行記錄上的未調參 baseline；大量假峰，**不可用於產品**。沒有測 AF、沒有跨受試者 holdout、不是 Watch 2 Pro ECG，也不代表 app 效能。它提供可量化起點，下一步應在 participant-disjoint、多活動/節律資料上改進並統計拒判率。
- 新增 `research/clinical-data/benchmark_ecg_qrs.py`，以 WFDB XQRS/GQRS 和一對一 ±150 ms beat matching，輸出每筆、pooled 與 macro sensitivity/PPV；四個單元測試涵蓋配對、空標記及 macro/pooled 分離。對腕部 exercise dataset 的 19 筆 chest ECG 跑 XQRS：pooled sensitivity 98.35%、pooled PPV 93.90%、macro sensitivity 98.27%、macro PPV 93.77%；但最差記錄 `s1_walk` sensitivity 95.74%、PPV 71.68%，`s6_run` PPV 78.34%。此資料集只有 8 位參與者、胸前 ECG 非小米手錶；它未包含適合此任務的 AF 驗證，結果只表示通用 QRS baseline 在該資料系統的 beat localization 表現，不是節律分類或發布驗證。
- benchmark JSON 位於被 `.gitignore` 排除的 `.local-research-data/results/wrist-chest-ecg-xqrs.json`；MIT-BIH 下載行程受單檔網路回應拖慢，只取得少量 header/annotation、沒有新增 signal `.dat`，已停止。既有單筆 MIT-BIH record 100 仍可做局部 sanity check，不作全資料庫結論。
- 修正前 ECG 比較 scripts 對 Watch 隱含假設 500 Hz；現在兩個 scripts 都拒絕缺少取樣率參數，並標示工具無法自行驗證呼叫端提供的時基。這避免未知 ECG 時基被靜默轉成時間/頻域特徵；不能因此把呼叫端數字視為真正驗證。
- 驗證：`research/clinical-data/test_benchmark_ecg_qrs.py` 4 tests 與 `tools.test_ecg_reference_compare` 3 tests passed；`health-engine` ECG decoder/core tests、mobile ECG decoder/policy/field tests、Wear preview test passed；mobile/Wear Debug 與 Release assemble passed。

## 來源

- [FDA Arrhythmia Detector and Alarm guidance](https://www.fda.gov/medical-devices/guidance-documents-medical-devices-and-radiation-emitting-products/arrhythmia-detector-and-alarm-class-ii-special-controls-guidance-document-industry-and-fda-staff)
- [FDA-recognized ANSI/AAMI EC57:2012](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfstandards/detail.cfm?standard__identification_no=31679)
- [PhysioNet/CinC 2017 single-lead AF dataset](https://physionet.org/content/challenge-2017/1.0.0/)
- [MIT-BIH AF Database](https://archive.physionet.org/physiobank/database/afdb/)
- [Analog Devices MAX86178](https://www.analog.com/en/products/max86178.html)
