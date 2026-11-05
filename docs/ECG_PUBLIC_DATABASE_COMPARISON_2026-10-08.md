# ECG 公開資料庫研究比較（2026-10-08）

## 範圍與隱私

- 僅在本機對手機目前保存的 ECG 研究序列及已授權、已下載的公開參考資料計算摘要特徵；原始手錶序列未傳送到外部網站或 AI。
- 本次手機序列有 2,290 個有限研究樣本。事件時間與每事件 10 點推得取樣率約 500.0521 點/秒、序列長度約 4.58 秒；這是推估時基，不是 OEM ADC clock 驗證。
- 截圖與本機狀態均未檢出 R 峰候選。因此本次只作頻譜、漂移、濾波與噪聲特徵距離比較，不作逐搏形態、RR、AF、正常／異常、mV 或診斷判定。
- 2026-10-06 的舊報告取樣為 2,690 點；本次是目前手機保存的 2,290 點快照。沒有相同 session ID 證據，不把兩者差異當成資料遺失或訊號退化。

## 比較資料

- **ECG-ID Person_01/rec_1**：PhysioNet 描述為 ECG Lead I、20 秒、500 Hz、12-bit、名義 ±10 mV，並同時提供 raw 與 filtered 通道。用來作同取樣率下的尺度無關頻譜參考；它不是本手錶校準檔，也不是 AF 陰性對照。資料依 PhysioNet 頁面的 ODC Attribution License v1.0 使用，應保留來源與 DOI 歸屬。
- **MIT-BIH Noise Stress Test Database**：取 baseline-wander (`bw`)、electrode-motion (`em`) 及 muscle-artifact (`ma`) 參考片段作描述性噪聲特徵距離；最近模板不構成噪聲分類結果。
- **PTB-XL**：保留在多資料庫工具的參考集合，供尺度無關訊號統計對照；12 導程臨床 ECG 不代表腕式導聯等價。

## 本次計算結果

方法：每段先去線性趨勢、以 robust scale 正規化，再計算 Hann-windowed FFT 頻帶比例及週期特徵；展示濾波為 60 Hz notch + 0.5–40 Hz bandpass。噪聲距離使用基線、ECG 頻帶、60 Hz、高頻比例及正規化導數 RMS 的歐氏距離；值愈小表示該特徵向量較接近，不是分類器或臨床分數。

| 指標 | 手錶原始 | 手錶濾波後 | ECG-ID raw | ECG-ID filtered |
|---|---:|---:|---:|---:|
| 點數 | 2,290 | 2,290 | 10,000 | 10,000 |
| 分析時基 (Hz) | 500.0521 推估 | 500.0521 推估 | 500 | 500 |
| 時長 (s) | 約 4.58 推估 | 約 4.58 推估 | 20 | 20 |
| <0.5 Hz 基線能量比例 | 84.80% | 57.46% | 3.52% | 1.53% |
| 0.5–40 Hz 能量比例 | 1.53% | 11.51% | 92.52% | 95.09% |
| 59–61 Hz 能量比例 | 1.62% | 0.0027% | 0.0256% | 約 0% |
| >40 Hz 能量比例 | 3.60% | 0.15% | 1.51% | 0.08% |

噪聲參考距離（原始手錶特徵）：`nstdb_bw=0.188294`、`nstdb_em=0.338410`、`nstdb_ma=0.693910`。在這三個參考向量中，`bw` 距離最近；此結果只能說明「所選特徵較接近該段 baseline-wander 樣本」，不能斷言手錶資料的根因就是基線漂移。

## 判讀與後續門檻

1. 本次頻譜顯示基線能量很高、0.5–40 Hz ECG 頻帶能量很低；現有 60 Hz notch + bandpass 能壓低 60 Hz 與 >40 Hz 成分，但不能消除低頻漂移。不能把濾波後的完整曲線當成已恢復的 ECG。
2. 片段只有約 4.58 秒，FFT 間距約 0.218 Hz；<0.5 Hz 區域只有極少頻點，基線比例僅作粗略指標，不能據此精確判斷漂移來源或診斷。
3. ECG-ID 同樣率但導程、受試者、增益與擷取設備不同。取樣率相近不等於逐點波形可比，也不提供 watch raw code → mV 校準。
4. 下一次採集需取得更長且連續的原始事件、可靠佩戴／電極接觸證據、同步加速度，以及可驗證的樣本時間基準；再以獨立 record/person split 和資料庫標註評估 QRS/R 峰靈敏度、PPV、定位誤差與 SQI。不得只挑穩定片段報分。
5. 若要解鎖研究 mV，仍需此裝置同步參考 ECG 儀器資料及留出驗證；公共資料庫的別人 ECG 不能校準這支手錶。

## 可重現性

- 工具：`tools/ecg_multi_reference_compare.py`
- 輸入：本機保存的本次 ECG CSV；參考資料在 `artifacts/reference/physionet/`。
- 輸出摘要：`artifacts/ecg-multi-database-comparison-2026-10-08.json`。
- 參考：PhysioNet [ECG-ID](https://physionet.org/content/ecgiddb/1.0.0/)、[MIT-BIH Noise Stress Test Database](https://physionet.org/content/nstdb/1.0.0/)、[MIT-BIH Arrhythmia Database](https://physionet.org/content/mitdb/1.0.0/)；ECG-ID DOI `10.13026/C2J01F`。
