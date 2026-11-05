# Research reference matrix

研究輸出依公開論文方法建立，但不把論文或晶片規格當成 Xiaomi Watch 2 Pro 的裝置校準證明。

## PPG

保留 raw 與濾波波形，分開計算振幅／灌流、週期／模板與動作干擾；只有峰值序列和時間基礎足夠時才輸出研究脈率。動作訊號可作 artifact reference，不能把缺失點變成實測點。

參考：[Wearable PPG review](https://pmc.ncbi.nlm.nih.gov/articles/PMC7612541/)、[wrist PPG AF review](https://pmc.ncbi.nlm.nih.gov/articles/PMC8890076/)。

## ECG

未驗證導聯、增益與 ADC→mV 時，輸出基線相對波形；R 峰／RR 需要可解讀波形與時間基礎。未知時基時只做索引域形態分析，不換算 bpm。

## BioZ

未驗證激勵、電極、增益、相位和 ADC→Ω 係數時，只輸出相對阻抗、變化／穩定度和接觸品質，不標示 Ω 或體組成。

## 參考儀器匯入

參考檔需包含 session、UTC 時間、單位、品質與儀器識別雜湊；配對時保存 clock offset、最大時間差、profile 版本、MAE、RMSE、bias 和一致性界限。論文資料不能替代裝置專屬配對校準。
# Zero-value handling and evidence preservation

The core rule is to classify zero before filtering it. A measured zero remains in
the immutable raw series. Only a zero explicitly identified by the producer as a
missing-value sentinel may be replaced in a derived analysis series, and the
replacement retains provenance and confidence. If no finite neighboring evidence
exists, the derived value remains unavailable rather than being invented.

This follows the evidence-first pattern used by signal-quality literature: quality
indices gate downstream use, while imputation is kept separate from the observed
signal. Regulatory ECG and pulse-oximeter guidance likewise requires validation of
the intended measurement and does not treat a completed display series as proof of
accuracy. Relevant references: FDA ECG guidance, FDA pulse-oximeter guidance,
IMDRF SaMD clinical evaluation, IEEE wearable-PPG signal-processing literature,
and the open-source `heartpy`/`neurokit2` signal-processing implementations.
