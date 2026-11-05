# 手錶資料缺口診斷

## 已確認可用

- 步數、活動熱量與睡眠已在手機 Health Connect 出現，表示手機端讀取與既有同步鏈正常。
- Xiaomi Watch 2 Pro 原生心率頁實測到心率，表示 LifeQ 心率感測器與硬體正常。
- 手錶 `BODY_SENSORS`、`BODY_SENSORS_BACKGROUND`、`ACTIVITY_RECOGNITION`、`READ_HEART_RATE` 與背景健康資料權限已授予。
- 3.5.0 起：全感測器掃描會先做離腕／佩戴校正，再依序獨占 MAX86178 生醫通道（心率 → PPG → SpO₂ → ECG → BIOZ），避免晶片互搶。
- 公開 Sensor HAL 已實機確認：體表溫度 CT7117、離腕 `mi_wear`、氣壓／環境光／動作、ECG 原始多通道事件；PPG／SpO₂／BIOZ 可註冊，部分模式需原廠量測頁合法啟用。

## 目前沒有資料的原因

| 資料 | 判斷 | 原因 |
|---|---|---|
| 心率（Health Connect） | 感測器有讀值，但 HC 可能仍缺樣本 | Wear Health Services MeasureClient 與本 App 同步鏈可寫入 `HeartRateRecord`；原生 Xiaomi 心率頁的前景讀值不會自動變成手機 HC 紀錄。 |
| HRV | 無臨床級資料 | Health Services 沒有公開逐拍 R-R；可由 ECG／PPG 原始波形估計研究用 RR／SDNN／RMSSD，不能由單一 BPM 推算，也不是房顫診斷。 |
| 血壓 | 無資料 | Watch 2 Pro 沒有公開血壓感測器或校準 API。 |
| ECG／房顫診斷 | 僅原始波形 | `com.google.wear.sensor.ecg` 可回傳原始電位；缺少導聯與臨床校準，不能診斷房顫／缺血。 |
| BIOZ／體組成 | 僅原始阻抗 | `com.google.wear.sensor.bioz` 可回傳私有通道；無原廠阻抗換算公式，不能輸出體脂或水分。 |
| PPG | 品質分析可用 | `com.google.wear.sensor.ppg` 可做波形與 SNR／品質分數；不能直接診斷疾病。 |
| 睡眠階段 | 只有睡眠時長 | Health Connect 來源未提供可驗證的深睡／淺睡／REM 分期；不能把總時長拆成醫療級階段。 |
| SpO₂ | 依來源與品質而定 | `mi.spo2`／`max86178_spo2` 存在；標準 Health Services 無 SpO₂ 型別。僅穩定樣本且 ≥90% 才寫入 HC；與 PPG 同時註冊會互斥無輸出。 |
| 體溫、氣壓、樓層、GPS | 依量測情境而定 | 感測器存在；體溫需貼合佩戴前景量測，樓層／爬升需氣壓事件，GPS 需運動工作階段。沒有事件時顯示「尚未量測」，不顯示 0。 |

## 結論

可安全使用公開 Sensor HAL、Wear Health Services 與 Xiaomi 演算法／原廠量測頁入口；不會破解或繞過原廠 HAL／SELinux。真正仍需原廠授權的是臨床級 ECG、BIOZ 體組成換算、PAI 與部分私有晶片模式。本機健康分析會對缺少來源的項目降低可信度，不會偽造診斷結果。
