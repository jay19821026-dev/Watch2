# 校準與醫療器材研究摘要

## 查證結論

### PPG／SpO₂

醫療器材流程把感測器、線材／介面與演算法視為同一個系統驗證；不是只拿晶片 datasheet 的係數套到手錶。FDA 的血氧指引要求以同步的參考血氧／共氧計資料比較，涵蓋指定飽和度範圍、受試者條件、低灌流與動作條件，並提交逐點資料、Bland–Altman 與誤差圖。皮膚色素、循環、溫度與接觸條件也需納入性能分析。

### ECG／節律

FDA 的 ECG／心律文件要求描述硬體、軟體、噪聲處理、QRS／節律演算法、測試資料與使用者理解結果的方式。以研究用途輸出時，本專案只保留相對波形、R 峰、RR、研究心率與品質；沒有導聯、增益、ADC→mV 和裝置配對資料，不升級成診斷 ECG 或 AF 結論。

核心引擎目前採兩段式處理：先對有限 HAL 值做去基線、相對縮放與波形品質檢查；若來源時間可信，才進行 R 峰／RR 研究計算；若時間不可信，改用 `NORMALIZED_INDEX` 索引時間軸，保留波形但停用 bpm、HRV 與 mV 換算。這與 FDA 對 ECG 演算法須以參考裝置／標準資料驗證，以及 IEEE ECG/PPG SQI 先做品質閘門的方向一致。索引時間軸不會被升級成感測器時鐘。

### BioZ／BIA

FDA 510(k) 的 BIA 案例會明確列出電極配置、激勵頻率、阻抗／電阻／電抗量測與參考設備；體組成模型還需要與 predicate/reference device 的等同性與性能資料。故本專案在缺少 Xiaomi 的激勵、電極與轉換係數時，只輸出 ADC 相對阻抗、穩定度、接觸品質與趨勢。

### SaMD 與演算法

IMDRF 將分析有效性拆成 accuracy、precision、偵測極限、線性／轉移函數與分析敏感度；之後仍要有臨床關聯與臨床性能證據。論文中漂亮的 MAE 不能直接轉成這支手錶的校準係數。

## 專案校準流程

```text
raw sensor evidence
→ source/time/unit/schema validation
→ synchronized reference pairing
→ device/session split
→ fit only on training sessions
→ holdout validation
→ MAE/RMSE/bias/limits of agreement/CI
→ profile review and versioning
→ derived result with profile_id and provenance
```

每個 profile 必須記錄：裝置／韌體、感測器通道、參考儀器型號與識別雜湊、UTC 時間、clock offset、最大配對差、單位、樣本數、分割方式、模型版本、有效期間與撤銷狀態。

## 可直接採用的研究算法方向

- PPG：帶通／去趨勢、模板／週期 SQI、IMU 動作分數、峰值配對；輸出研究脈率與相對形態。
- ECG：基線校正、正規化相對電位、最小峰間距、RR 與變異係數；未知時基只輸出索引域結果。
- BioZ：波形中位數基準、相對變化率、變異係數與接觸品質；沒有係數不輸出 Ω。
- SpO₂：只有已知波長、通道映射與參考配對才建立 ratio-of-ratios 到百分比模型。
- 溫度：同一裝置／同一佩戴位置的個人基線與外部溫度計配對；腕部原始值不直接當核心體溫。

## 來源

- FDA, [Pulse Oximeters 510(k) Guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/pulse-oximeters-premarket-notification-submissions-510ks-guidance-industry-and-fda-staff)
- FDA, [Diagnostic ECG Guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/diagnostic-ecg-guidance-including-non-alarming-st-segment-measurement-guidance-industry)
- FDA, [Arrhythmia Detector and Alarm Guidance](https://www.fda.gov/medical-devices/guidance-documents-medical-devices-and-radiation-emitting-products/arrhythmia-detector-and-alarm-class-ii-special-controls-guidance-document-industry-and-fda-staff)
- IMDRF, [SaMD Clinical Evaluation](https://www.imdrf.org/documents/software-medical-device-samd-clinical-evaluation)
- FDA, [Fresenius BCM2 BIA 510(k) Summary](https://www.accessdata.fda.gov/cdrh_docs/pdf25/K250634.pdf)
- FDA, [Tanita BIA 510(k) Summary](https://www.accessdata.fda.gov/cdrh_docs/pdf9/K090479.pdf)
- IEEE, [End-to-End PPG Processing Pipeline](https://ieeexplore.ieee.org/document/10385998/)
- IEEE, [Bioimpedance Quality-Aware Peak Detection](https://ieeexplore.ieee.org/abstract/document/11665121/)
- IEEE, [Signal-Quality Indices for ECG and PPG](https://doi.org/10.1109/JBHI.2014.2338351)
- IEEE, [Automatic ECG Signal Quality Determination](https://ieeexplore.ieee.org/document/10491116/)
- Google Patents, [Motion Artifact Mitigation for Pulse Photoplethysmography](https://patents.google.com/patent/US10881310B2/en)
