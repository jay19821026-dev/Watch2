# 4.0 measurement result review / 4.0 量測結果檢查

This is a code-path review, not a claim that every sensor has produced a live Watch 2 Pro reading. Reviewed against `WatchFeatureInventory`, `WearMeasurePageActivity`, `MeasureResultActivity`, `HealthConclusionEngine`, and the decompiled OEM paths documented in `OEM_DATA_WINDOW_COMPARISON_4_0.md`. Live verification still requires installing the current APK on the watch and phone and retaining a synchronized sample for each route.

這是程式路徑檢查，不代表每個感測器都已在 Watch 2 Pro 實測取得數值。每個入口必須給出以下其中一種明確結果：**有效生理數值及來源／時間、原始資料摘要及未換算原因、未收到資料及重試方法、或硬體／授權不可用**。不能把沒有資料寫成正常，也不能把原始欄位冒充疾病診斷。

| Feature / 功能 | Current result path / 目前結論來源 | Remaining live evidence / 尚需實測 |
| --- | --- | --- |
| Heart rate / 心率 | Health Services or direct sensor bpm, with source/time; optical raw uses a separate research estimate or raw count | Paired phone/watch reading and Health Connect record |
| Steps, activity, workout / 步數、活動、運動 | Wear Health Services/Health Connect records; dated daily report | Same-day session and step count |
| Sleep / 睡眠 | Complete Health Connect sessions by wake date; no invented stages | One complete session and source |
| SpO₂ / 血氧 | Typed Health Connect percentage when available; raw `65596` is counted and labeled separately | Typed source and raw event captured on same watch |
| Temperature / 溫度 | Typed feature record if present; raw temperature remains unit-unverified | OEM typed result and raw channel paired comparison |
| ECG | Raw `65573` event count/preview, OEM-conditioned packet preview, research waveform where decoded; no native classifier output available to AILISA | Real event rows, on-body events, orientation mode, OEM report if exportable |
| PPG / 光學 | Raw `65597`/`65596` channels separated; qualified research pulse or explicit raw event count | Real sample clock/channel mapping and typed HR comparison |
| BioZ / 阻抗 | Raw `65599` count and signal summary; no verified Ω/body-composition conversion | OEM typed body-composition result paired to raw session |
| Pressure / 氣壓 | hPa with time; standard-atmosphere altitude is labeled as estimate | One trusted timestamp and reading |
| HRV / 心率變異 | Research RR only when clock/quality evidence passes; otherwise unavailable with reason | Paired beat timestamps or typed reference |
| Blood pressure / 血壓 | Manual cuff or eligible Health Connect record; no watch optical BP claim | Cuff record/source, if used |
| Respiration / 呼吸 | BioZ raw event summary or manual value; no unverified breaths/min conversion | Typed reference or paired validation |
| Motion, light, compass / 動作、光線、指南針 | Sensor observations in watch utility pages; not disease assessments | Sensor presence and page interaction |
| Glucose / 血糖 | Explicitly unavailable without an external CGM reading | A supported external data source, if requested |

## Result page defect corrected / 已修正的結果頁缺陷

Previously `MeasureResultValuePolicy` replaced every noncurrent value with `—`, including an ECG/PPG **raw event count**. The optical primary value also showed `—` whenever a pulse estimate did not qualify. The result page now keeps a known raw point/event count with an explicit noncurrent/unknown-time label, while still hiding stale bpm. A short conclusion card is always shown for received data, missing data, or a usable value. The Health Services heart-rate path now counts its feature record as received data rather than looking for a raw sensor record. `MeasureResultActivityTest` covers unknown-time raw counts and stale optical count versus bpm.

The health conclusion previously stopped showing a trusted heart-rate history after 24 hours even though the cached measurement still existed. It now shows the latest stored trusted heart-rate record with its original date, and explicitly says it is outside the current 10-minute analysis window. This does not turn historical data into a current measurement.

先前結果頁會把所有非近期資料都顯示成 `—`，連已收到的 ECG／PPG 原始事件數也遮掉；PPG 估算脈率未通過時也只有 `—`。現在保留可信的原始點／事件數，標明時間未知或歷史資料；舊 bpm 仍不顯示為現值。結果卡分別說明本次取得數值、只取得原始資料、或未收到資料。

## Release gate / 發行門檻

Current unit tests and builds check source behavior, but cannot prove that the watch actually emits data for every mode. Do not call an OEM native ECG result code from a private library or assign `200`–`204` labels to raw events without an authorized typed result. The final release still needs phone/watch live checks for the measurements claimed in store text, signed artifact verification, and the publication sequence.
