# AILISA Health Bridge 4.0.0 test 36

Author: 王宥傑 (Wang You-Jie). Phone and Wear OS build code: 218.

## 中文

- 手錶將感測器事件時間與收到事件的時間分開傳送。當原廠感測器時間缺失或不合時鐘基準，手機仍可保留有來源標示的原始擷取時間。
- 新一輪沒有事件時，原始資料詳細頁保留上一次原始預覽，並明確標示為歷史資料；不將接收時間當作已驗證的量測時間。
- 修正新擷取沒有預覽內容時可能錯配舊預覽與新時間、手錶時鐘異常時可能顯示錯誤日期的問題。
- 對照 Mi Fitness 9.8.348i 與 3.x 的解包紀錄：AILISA 已接入可見的公開原始感測器路徑；原廠處理後的 ECG、體成分及溫度結果仍走獨立服務或原生函式庫。本版沒有移植原廠演算法、啟用原廠 ECG，或驗證疾病判讀。
- 舊紀錄若原本只有時間 `0`，只能顯示已保存的原始內容與發送／接收時間，不能事後還原精確量測時間或判讀結果。一般健康卡片的可判讀數值規則沒有變更。

## English

- The watch sends sensor-event time separately from callback receipt time. If a vendor event clock is missing or incompatible, the phone retains a labeled raw-capture receipt time.
- An empty new session leaves the previous raw preview visible as history. Receipt time is not presented as a verified measurement time.
- Fixed a preview/time mismatch when a new capture has no preview, and an implausible watch clock that could show the wrong calendar date.
- Comparison with the Mi Fitness 9.8.348i and 3.x decompilation shows AILISA already connects to the visible public raw-sensor paths. OEM processed ECG, body-composition and temperature results use separate services or native libraries. This build does not import OEM algorithms, activate OEM ECG, or validate disease classification.
- Existing time-0 captures can show retained raw content and labeled send/receipt time; their exact measurement time or interpreted result cannot be reconstructed. Normal health-card eligibility rules are unchanged.

Debug-signed sideload test build. Sensor-history behavior is covered by unit tests; paired-device live measurement and clinical interpretation are separate gates.
