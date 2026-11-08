# AILISA Health Bridge 4.0.0 test 37

Author / 作者：王宥傑 (Wang You-Jie). Phone and Wear OS versionCode: 219.

## 中文

- 首頁把「這次收到原始訊號」與「上次有原始資料」分開顯示，並可開啟有資料的 ECG、PPG 或溫度詳情頁；可判讀的既有心率／血氧／溫度結果仍依各自來源與時間顯示。
- 當 test 36 以前保存的原始事件遭遇下一次空白量測，手機會先保留舊預覽與發送時間，避免只剩空白。發送／接收時間不是已驗證的感測器量測時間。
- Code review 修正體溫原始通道選錯、當次與歷史混標，以及有可用結果時錯誤導到原始資料頁的問題。
- 臨床參考頁的溫度詳情會開啟實際有資料的通道；BioZ 原始資料改標為阻抗原始訊號，避免暗示已有體成分量測結果。
- 原廠 ECG、BioZ、溫度的處理後結果仍未由公開 API 取得；本版沒有啟用原廠 ECG、移植原廠模型或建立心律診斷。

## English

- Home now distinguishes a current raw capture from historical raw evidence and opens the ECG, PPG, or temperature detail that actually contains data. Existing interpretable heart-rate, oxygen, and temperature records retain their own provenance and time rules.
- Before an empty new session overwrites a pre-test-36 raw capture, the phone saves its bounded preview and watch publish time. Send/receipt time is not a verified sensor measurement time.
- Code review fixes the wrong temperature channel, current/history labeling, and navigation away from an available interpreted result.
- The temperature detail opens the channel with actual data; raw BioZ is labeled as impedance signal rather than a body-composition result.
- OEM processed ECG, BioZ, and temperature results remain unavailable through a verified public path. This build does not activate OEM ECG, import its model, or make a rhythm diagnosis.

Debug-signed sideload test build. Unit tests and builds do not replace a fresh paired-device measurement check.
