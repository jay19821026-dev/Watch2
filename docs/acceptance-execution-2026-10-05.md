# 剩餘驗收執行順序

本機 Hermes 已回覆優先序，原文在 Obsidian 代理交換 `responses/20261005-xhb-hermes-acceptance-priority.md`。其回答重列已完成項目，沒有提供新的重連实现或測試證據；不直接採用為完成證明。Grokbot 無已驗證接口。

## 下一批交付，不再重跑已通過的隔離庫測試

1. **資料恢復驗收**：手機手動授權後從 App 重讀 Health Connect，按步數／血氧／睡眠記錄權限、查詢狀態、來源及量測時間；比較 UI 與查詢結果，不把授權失敗當空紀錄。
2. **最新雙端感測驗收**：手錶離充電佩戴後，以 App UI 建立不重疊 session。核對擷取、payload、品質、快照、Data Layer、手機保存、冷啟動讀回與畫面，每階段保留同一 session 對照。取消與部分成功列入結果，不排除失敗輪次。
3. **中斷恢復驗收**：先完成正常基準，再做可恢復的傳輸中斷；記錄中斷前後 pending、DataItem、receipt、庫內 session 數。不得用關閉 ADB Wi-Fi 假裝藍牙／Data Layer 斷線，不得破壞配對或刪使用者資料。
4. **原始儲存加密**：盤點所有 raw_sensor_results 的直接讀寫者，再透過統一存取層遷移；先測混合型別、大小界限、取消、中斷、KeyStore 遺失與刪除。不得只加密寫入而讓舊讀取者靜默讀不到資料。
5. **發布**：原授權金鑰簽章、憑證核對、完整 review 與 release gate。不能替換金鑰或把 unsigned APK 當正式版本。

## 現時前置條件

集中回歸已結束：手機 628／Wear 198 項測試與雙端 Release 建置通過（paired-regression-20261005.log），但 APK 驗簽仍失敗，unsigned 不可發布。以下「建置已啟動」為歷史狀態。

最新查證優先於以下歷史狀態：ADB devices 清單目前無裝置，33501 連線逾時，沒有正在執行的腕部掃描。最近可讀的手機工程頁 Health Connect 授權類型為 0；必須重連後重新查詢，不能當作目前已授權。RawSensorStore.open 仍使用舊 SharedPreferences，隔離加密庫測試通過不等於正式原始快照已加密。雙端完整單元測試與 Release 建置已啟動，日誌 paired-regression-20261005.log；在結束前不宣稱通過。正式簽章、三輪配對掃描、真實傳輸中斷恢復及新增背壓實機斷言仍未驗收。

後續 UI 操作查證：ADB 啟動 App 正常，但依 UI bounds 注入「裝置」點擊後，UI dump 仍為摘要，未證實觸控注入有效；不宣稱已點選授權。已用 APPLICATION_DETAILS_SETTINGS 成功開啟 MIUI 本 App 系統詳情頁，供使用者管理權限。嘗試的健康設定 action 未解析成功，未繼續猜 action，也未以 shell 授權假裝使用者完成 Health Connect 同意。仍等待已提出的「可驗收」前置條件。

2026-10-05 最新 ADB：雙端在線，手錶 USB powered=true/status=2/24%；手機摘要讀取授權缺失已有實機 UI 證據。已請使用者離充電佩戴並完成手動授權。未啟動腕部量測，沒有活躍掃描可等待。

模型推估、原始訊號擷取與臨床校準分開驗收；即使以上軟體關卡通過，仍不能推定未知映射／時基／單位已變成醫療診斷。
