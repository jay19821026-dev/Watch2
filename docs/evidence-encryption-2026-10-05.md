# 原始證據庫加密實作與驗收

## 最新手機實機證據

追加遷移中斷驗證：EvidenceDatabaseCopy 在第一個 session 已插入並逐筆核對、目的地交易尚未提交時，寫出 copy_transaction_open checkpoint。確認活躍 PID 27823 後 force-stop，PID 消失，再以 PID 29141 冷啟驗證。兩個來源 session／兩筆原始紀錄與衍生紀錄完整，目的地未提交 session/raw/derived 全部回滾；重新複製回傳 Result(2,2,1) 並逐筆核對 PASS。UUID 相同，隔離 fixture 已清理。

證據：`evidence-interruption-checkpoint-20261005.json`、`evidence-interruption-verify-20261005.json`；單元測試與 Debug/Release 建置：`evidence-interruption-build-20261005.log`。這是 copy 交易中途的實際程序終止測試，不等於已完成正式庫切換與明文清理。

追加跨程序驗證：prepare_restart 生成 UUID 隔離加密庫與 Keystore 包裝檔；讀到 PASS 後 force-stop，確認 PID 消失，再冷啟 verify_restart。PID 由 8545 換為 23075，fixture_id 相同，原始 NaN 編碼／未知 source time／callback time／衍生版本與來源均逐筆讀回 PASS。隔離 DB、包裝檔及 fixture 描述已清理，手機返回主頁。

證據：`evidence-restart-prepare-20261005.json`、`evidence-restart-verify-20261005.json`。這是程序終止後的隔離證據保存驗證；正式舊庫遷移中斷與切換仍需驗收。

Debug-only EvidenceStorageValidationActivity 已安裝並由 ADB UI 啟動，主程式安裝與 Activity 啟動成功。兩組隔離自測均 PASS；結果保存於 `.tooling/verify/evidence-storage-device-20261005.json`。

- 加密庫檔頭／測試字串檢查、錯誤金鑰拒絕、逐筆複製及核對、交易失敗回滾、重開讀回、僅刪衍生值保留原始證據、完整刪除且來源不受影響。
- Keystore 包裝 key 重開讀回、認證標籤損毀拒絕、原 envelope 保留、key 遺失不建立替代 key。
- 測試僅用獨立 UUID 名稱的資料庫與 key alias。結束後 databases/no_backup 未列出 selftest 殘留；未讀取或遷移使用者正式證據庫。
- 自測 Activity 只存在 src/debug，受 android.permission.DUMP 保護。Release 合併 manifest 未包含此 Activity。

這是實機 Debug 自測，並非 AndroidJUnitRunner instrumentation；後者仍因測試包安裝限制未執行。正式切換、遷移中斷恢復與明文清理仍待驗收。

手機已返回 MainActivity。自行啟動的離線軟體模擬器在核對 PID、workspace executable path 與 AVD 名稱後停止；目前不再等待其啟動。

來源：[SQLCipher 官方 Android / Room 整合文件](https://github.com/sqlcipher/sqlcipher-android)。採用 Maven Central 可取得的 4.19.1 Community AAR 與 Room SupportOpenHelperFactory。

已新增 EncryptedEvidenceDatabase：要求至少 32-byte passphrase、複製傳入陣列、載入 SQLCipher，保留現有 Room schema。

已新增 EvidencePassphraseStore：隨機 32-byte passphrase，由 Android Keystore AES-GCM 包裝；固定用途 AAD、隨機 IV、AtomicFile 寫入，位於 noBackupFilesDir。既有證據要求的 passphrase 遺失或無法解密時拒絕建立替代值，保留既有檔案供調查。

包裝檔寫入後立即有界讀回，逐 byte 核對後才回傳 passphrase；不以 finishWrite 返回推定已成功保存。WrappedEvidencePassphraseStore 支援隔離的 test-only alias/file，新增金鑰重新載入、認證標籤損毀、金鑰遺失與保留原包裝檔的 instrumentation。測試不存取正式 key alias/file。

本輪手機單元測試、instrumentation APK 編譯與 Release 建置通過（`evidence-key-readback-20261005.log`）；Keystore 實機測試尚未執行。

測試環境追加：停用 Vulkan 的直接軟體模式啟動仍有活躍 emulator process，ADB 顯示 emulator-5554 offline。這是待啟動的具體程序，不是測試通過；不能因觀察逾時自行重啟另一實例。

已新增 instrumentation：保存測試原始證據、確認檔頭不是明文 SQLite／未出現測試字串、錯誤 passphrase 拒絕讀取、正確 passphrase 重開讀回與刪除。

手機單元測試、instrumentation APK 編譯與 Release 建置通過，紀錄 `evidence-encryption-key-20261005.log`。instrumentation 尚未執行。

上述為前期驗證紀錄。2026-10-05 正式 SignalEvidenceDatabase.get 已接上加密切換：手機既有 7 組 session、896 筆儲存預覽列逐筆核對後遷移完成，舊明文主檔與側檔已清理。預覽列數不代表已解碼 ADC 樣本數。

新版 Debug APK 已覆蓋安裝手機；正式庫稽核 PASS。實際 force-stop 後 PID 32577 消失，冷啟動 PID 1137 再次稽核 PASS，7 組／896 列保持一致。證據：`.tooling/verify/evidence-production-audit-20261005.json`、`.tooling/verify/evidence-production-restart-audit-20261005.json`、`.tooling/verify/evidence-production-restart-report-20261005.json`。測試未刪除使用者健康紀錄。返回主程式時系統顯示權限確認頁，尚不代表所有 UI 或感測量測已驗收。

僅 PPG Room 證據庫完成加密；其他 SharedPreferences、原始快照及匯出仍需逐項驗收。下列舊待辦的 Room 切換項已完成，不代表全部原始資料皆已加密。

新增 EvidenceDatabaseCopy：來源一致性交易中讀取最多 200 個 session，目的地要求為另一個空庫；目的地單一交易逐筆複製／核對 session、原始值、時間來源、衍生 payload 與 provenance，核對總量後才提交。相同物件、相同名稱或相同檔案拒絕複製。來源未修改，切換與刪除明文不由此函式執行。

新增加密複製 instrumentation：未知時間與 NaN 原始編碼保留、目的地重開讀回、已填目的地拒絕覆寫、目的地刪除不影響來源、中途注入失敗必須回滾整個目的地、來源保持完整且可重試。這些測試仍待實際執行。

本輪手機單元測試、instrumentation APK 編譯與 Release 建置通過：`evidence-copy-final-20261005.log`。沒有以編譯通過推定遷移／回滾測試已執行。

2026-10-05 後續盤點：手機 raw_sensor_results.xml 約 272 KB、watch_feature_snapshot.xml 約 1.4 KB 仍存在；只核對檔案大小，未輸出健康內容。雙端新增明確 cloud-backup、device-transfer 與舊版 fullBackupContent 全域排除規則，避免只靠 allowBackup=false 或把加密庫搬到沒有原 Keystore 的裝置。這不是 SharedPreferences 加密，也不表示已完成實際換機測試。手錶最新仍 USB 充電，24%，未啟動腕部量測。

下一步驗收／整合：

1. 在可執行 instrumentation 的裝置上測試加密、金鑰重開、遺失／損毀與回滾。
2. 從舊明文庫匯出到獨立加密暫存庫；保留來源，逐表驗證 schema、版本與內容後才切換。
3. 中斷後可恢復，來源或暫存有衝突時保留雙方；禁止 destructive migration 或默默建立空資料庫。
4. Room 正式初始化接上加密 factory 與包裝 key；既有庫遷移驗收通過後再清理明文主檔與側檔。
5. 隱私文字按實際涵蓋範圍更新；SharedPreferences、匯出與 Room 的加密狀態分別驗收。

測試環境：既有 Android AVD 啟動因缺 hypervisor driver 失敗；軟體模式嘗試未產生可用 ADB 裝置。未安裝主機驅動，未修改使用者手機既有資料。手機測試 APK 安裝仍待系統允許。
