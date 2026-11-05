# 原始快照正式加密切換：呼叫端風險

目前 RawSensorStore 仍指向 legacy SharedPreferences；不以隔離加密測試冒充正式切換。

## 本輪程式查證

- EngineeringDebugActivity 的診斷採集與 renderReport 都直接開啟 RawSensorStore；renderReport 的 raw 變數目前未使用。未來若直接替換成會解密／遷移的 open，診斷畫面本身可能在 KeyStore 或 migration 失敗時中斷，需先移除無用途讀取並給採集失敗獨立顯示。
- writeRawExport 已在 try 範圍內讀取，仍需確認 UI 錯誤不誤報「已匯出」。
- MainActivity 的血氧參考讀取位於 readSafely 範圍，須驗證儲存失敗與無 Health Connect 記錄分別呈現。
- LocalSensorDataDeletion 清空目前正式 facade；尚未驗證切換後 concurrent acquisition 不會重新寫入已刪資料。

## 正式切換前必要驗收

1. KeyStore／解密／marker 失敗時：主畫面和工程頁仍可開啟，顯示保存／讀取失敗，不替換成空資料。
2. 非同步拒收與落盤失敗時：來源事件保留，未持久化不得 ack，UI 可看原因。
3. 刪除与採集／待寫工作重疊時：有明確序列化邊界，完成刪除後冷啟動不可讀回舊證據；只用隔離 fixture，勿刪除使用者資料。
4. 完成雙端正常掃描、手機保存及冷啟動基準後才切換；測試可重試失敗，不僅正常路徑。

Hermes 本輪已回覆，保存在 Obsidian responses/20261005-1210-xhb-hermes-cutover-blockers.md。其建議覆蓋非同步拒收、刪除競態、遷移失敗；將網路斷線當本機 executor 拒收原因不適用，因此不照搬。Grok 控制介面逾時，未提交新審查。ADB 無裝置，以上實機條件尚未具備。

已修正工程診斷：移除 renderReport 未使用的 raw 開啟；診斷採集的 raw 讀取異常獨立記為 raw_snapshot_query=failed:例外型別，取消仍傳播，保留其他診斷與已讀部分欄位，畫面及匯出均帶查詢狀態。Debug 建置通過（debug-storage-isolation-20261005.log），尚未實機注入 malformed prefs／解密失敗，不稱已完成加密切換。
