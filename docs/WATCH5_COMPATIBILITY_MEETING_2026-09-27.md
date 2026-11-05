# Watch 5 相容性與韌體決策會議 — 2026-09-27

## 決策

- **GO：**以同一 Wear OS app/codebase 做 Watch 2 Pro 與 Watch 5 的標準 API 相容性驗證；所有健康服務與感測器功能均以執行時 capability、註冊結果與回呼驗證，不依型號名稱假設可用。
- **NO-GO：**不把 Watch 2 Pro 跨刷成 Watch 5，也不移植 Watch 5 韌體、HAL、私有 SDK、感測器校準或生理換算係數。未找到小米官方跨型號映像／支援流程；不同感測器清單亦不支持兩機等同。不得以晶片系列相近推定韌體相容。
- **健康輸出限制：**近期匯出摘要為 38 類通道、26 類本 session 有原始事件、18 類 fresh、0 類通過品質閘。這只描述資料流；不代表生理有效或硬體損壞。原始資料沒有裝置級映射、時基、單位或校準證據時，仍不得轉為醫療數值或診斷。

## 會議分工與共識

- PM、Wear 架構、生理訊號、QA、韌體安全、發布安全六個代理獨立審查；另由 ChatGPT 作相容性反方審查。先前 Gemini、Claude、DeepSeek 的 Watch 5 規格／跨刷研究作參考；其中未獲官方證據的硬體細節與「必定刷壞」說法不採為事實。
- 共識：可移植的是一般 Wear OS app 層與公開 API；Health Services 應先查能力，SensorManager 僅盤點實際可見標準感測器。廠商 raw 通道及校準要逐機驗證。官方資料：[Watch 5 規格](https://www.mi.com/tw/product/xiaomi-watch-5/specs/)、[Watch 2 Pro 規格](https://www.mi.com/uk/product/xiaomi-watch-2-pro/specs/)、[Wear OS Health Services](https://developer.android.com/health-and-fitness/health-services)。

## 下一階段：`W5-COMPAT-01`

1. 不改健康演算法；新增／確認能力盤點輸出：OS/API、Health Services 支援型別、SensorManager 標準感測器、註冊結果、回呼數、來源時間戳有效性、Data Layer→手機持久化結果。
2. Watch 5 實機以同一 APK 做 bounded probe；未知 vendor raw 只列 raw-only/unknown，不顯示推算健康值。
3. 用同一 session ID 對帳 Wear 終態、手機接收、儲存、freshness、UI/匯出；負向測試包含權限拒絕、無回呼、無效時間戳、重播與傳輸失敗。
4. 完成後交付 capability matrix 與可重現測試報告；無 Watch 5 實機時只可標記未驗證，不能宣稱相容。

## 手機韌體檢查

連線手機辨識為 Xiaomi 15T Pro 台灣版；ADB 讀得 Android 16/API 36、`OS3.0.302.0.WOSTWXM.C06`。官方更新器畫面只顯示該版本與 2026 年 8 月安全修補說明；「下載最新完整檔案」顯示同一個 `3.0.302.0.WOSTWXM`、7.1 GB。下載曾開始但仍在 0% 時已停止；未安裝、刷入或重啟，因為沒有證據顯示有較新版 OTA。後續只透過手機系統更新器核對此型號／台灣區 OTA，不使用手錶韌體或第三方跨區 ROM。

## Watch 2 Pro 韌體更新盤點 — 2026-10-08

- 小米官方提供的是 Watch 2 Pro **系統韌體更新套件**：可從手錶「設定 → 系統 → 韌體更新」檢查，也可透過 Mi Fitness 推送；官方要求更新時手錶至少 50% 電量、連網並與手機保持連線。[官方 Watch 2 Pro FAQ](https://www.mi.com/global/support/faq/details/KA-42562/)
- 目前能確認的可升級路徑是整機 OTA。沒有找到小米公開提供可獨立刷寫的 Snapdragon W5 CPU／DSP、Sensor HAL、感測器韌體或校準套件；因此不能把晶片本身支援的能力當成可單獨開啟／更新的功能，也不嘗試跨型號刷機或非官方映像。
- 本次 ADB 只列出手機 `2506BPN68G`，未列出 Watch 2 Pro；故未讀到手錶目前韌體版本，也未驗證此地區是否有待安裝 OTA。需在手錶更新頁或 Mi Fitness 裝置更新頁實際查詢，才能確認可用版本；本記錄不代表已更新。

## 發布風險

目前 mobile/Wear release build 設定仍使用 debug signing、且未啟用 minify；現有產物屬內部 QA，不等於可正式發布。正式發布需另設安全保管的 release signing 與版本／hash 驗收，不在本次會議更改。
