# LinkedIn project copy / LinkedIn 專案文案

## Project name / 專案名稱

Xiaomi Health Bridge｜Android + Wear OS Health Data Research

## Creator / 作者

王宥傑 (Wang You-Jie)

## Traditional Chinese

設計並開源 Xiaomi Health Bridge：一套連接 Android 手機與 Wear OS 手錶的健康資料研究專案。以 Kotlin、Health Connect、Wear OS Health Services 與 Wearable Data Layer 建立雙端資料流程，保留來源、時間、品質、同步及校準證據，並明確區分無資料、過期、品質不足、不支援與同步失敗。

專案包含手機與手錶原始碼、雙端測試 APK、中英文技術文件、隱私／Data safety 草案，以及公開研究資料庫的離線重現工具。研究來源包含 MIT-BIH Arrhythmia、MIT-BIH Noise Stress Test、ECG-ID、Wrist PPG During Exercise 與 PTB-XL；另以 Orphanidou et al. IEEE JBHI 2015 作為簡化訊號品質指標的研究參考。每個資料庫、論文、演算法與程式位置皆有一對一溯源，並記錄不能推論的臨床與硬體校準邊界。

此專案不是醫療器材，不提供診斷、治療或緊急監測。

## English

Designed and open-sourced Xiaomi Health Bridge, a health-data research project connecting Android phones and Wear OS watches. Built a two-device Kotlin pipeline using Health Connect, Wear OS Health Services, and the Wearable Data Layer, preserving provenance, timestamps, quality, synchronization, and calibration evidence while distinguishing missing, stale, low-quality, unsupported, and failed data states.

The project includes phone/watch source code, matching test APKs, bilingual technical documentation, privacy/Data safety drafts, and reproducible offline tools for public research datasets. References include MIT-BIH Arrhythmia, MIT-BIH Noise Stress Test, ECG-ID, Wrist PPG During Exercise, PTB-XL, and Orphanidou et al. (IEEE JBHI 2015) for a simplified signal-quality research reference. Dataset-to-paper-to-algorithm-to-code traceability and prohibited clinical/device-calibration inferences are explicitly documented.

This project is not a medical device and does not provide diagnosis, treatment, or emergency monitoring.

## Links / 連結

- Project page: https://ailisa.net/portfolio/xiaomi-health-bridge
- Source code: https://github.com/jay19821026-dev/Watch2
- Release: https://github.com/jay19821026-dev/Watch2/releases/tag/v3.13.44
- Provenance map: https://github.com/jay19821026-dev/Watch2/blob/main/docs/ALGORITHM_DATASET_PAPER_MAP.md
