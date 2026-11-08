# Watch 2 Pro / Watch 5 韌體比對進度（2026-11-08）

## 已取得的原始檔

- 透過使用者的 Watch 2 Pro ADB，唯讀擷取已安裝的 `Fitness.apk`（SHA-256 `920459E3B2EBF569517E4294029705561E7BEAFF6CCA30EDB142E5A4F83B5965`）與 `MiHealthService.apk`（SHA-256 `47738E2C5BBD754D8F6636ACB0DE03DC2847A5B0A87BE4AD32DF0EE11651941A`）。檔案只存於 Git 忽略的 `.local-research-data/watch2pro-installed-2026-11-08/`，未發布原廠二進位檔。
- 實機系統指紋：`xiaomi/axolotlte/axolotlte:14/AW2A.240903.001.XM128S/AW2A.240903.001.XM128S:user/release-keys`。這是目前安裝版本的證據，不是下載得到完整 OTA 韌體。
- Android sensorservice 宣告 Watch 2 Pro 的 PPG `65572`、ECG `65573`、MI SpO₂ `65588`、體表溫度 `65590`、原始 SpO₂ `65596`、BioZ `65599`。既有 AILISA 診斷紀錄顯示 PPG、BioZ、原始 SpO₂、體表溫度曾註冊且收到事件；此次尚未做一輪新的佩戴量測。

## Watch 5 韌體來源查核

- 使用者指定的型號是[台灣官方 Xiaomi Watch 5 產品頁](https://www.mi.com/tw/product/xiaomi-watch-5/)，不是 REDMI Watch 5。該頁說明 Watch 5 採 Snapdragon W5 Gen 1 + BES2800BP 雙晶片、4 LED／4 PD 光學模組，以及 EMG／PPG／IMU 手勢模組；台灣產品頁未列出 ECG 功能或直接韌體下載連結。產品頁也沒有提供 Watch 2 Pro 相同的感測器欄位映射或校準係數。
- [小米 Watch 5 FAQ](https://www.mi.com/global/support/faq/details/KA-667495/) 說明透過手錶「設定 → 系統 → 系統更新」下載與安裝，未提供可直接取得的完整韌體包。
- [小米 Watch 2 Pro FAQ](https://www.mi.com/global/support/faq/details/KA-42562/) 說明後續升級包透過 Mi Fitness 推送，也未提供完整韌體下載連結。
- 查核公開韌體目錄時，`miwear.watch.o65` 是 **REDMI Watch 5**，不是 Xiaomi Watch 5；不能拿它當 `blenny`（國際版 Xiaomi Watch 5）韌體。第三方機型目錄將國際版 Watch 5 標為 `blenny`；這項機型對照僅供搜尋，不是韌體真實性證明。
- 截至本次查核，尚未找到可核對來源、型號、版本與 SHA-256 的 Xiaomi Watch 5 `blenny` 完整 OTA／工廠映像，因此沒有下載或反組譯 Watch 5 韌體，也沒有宣稱兩款韌體可以混刷。

## 能否混用功能

Watch 2 Pro 的 Android 14 系統、裝置服務與 HAL 必須一起匹配；Watch 5 的功能程式即使取得，也不能只憑同名感測器或相近處理器移植韌體模組。公開 App 層可比較功能流程、資料欄位與顯示方式，再用 AILISA 自己的程式接入 Watch 2 Pro 已公開且實測可用的 API。ECG 原廠啟用狀態、校準係數與臨床判讀仍需各自驗證。此處沒有修改系統、刷機或寫入原廠私有偏好。

## 下一個可驗證步驟

1. 取得**確切的** Watch 5 `blenny` 官方 OTA 來源或一支可授權唯讀擷取的 Watch 5，記錄版本、檔案大小與 SHA-256。
2. 靜態比對兩款 `Fitness`／健康服務的公開元件、感測器型別、功能閘門和資料結構；不複製原廠程式碼或未授權二進位檔到 AILISA。
3. 在 Watch 2 Pro 做新的佩戴量測，逐通道核對註冊、回呼、資料時間與手機收到的結果，區分「系統列出」與「實際量到」。
