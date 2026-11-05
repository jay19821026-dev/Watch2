# 原始快照存取邊界

手機原始感測快照的讀取、寫入、診斷匯出與使用者刪除，統一經過 `RawSensorStore.open`。目前仍使用既有 `raw_sensor_results` 私有 SharedPreferences，未更換檔案、資料格式或量測值。

此變更是後續加密遷移的前置工作，不代表原始快照已加密。PPG Room 證據庫的 SQLCipher 加密與這個快照儲存層不同。

新增 RawSnapshotCodec 作為加密前的有界序列化層，目前未接入正式儲存。支援 String、Int、Long、Float 原始位元、Boolean、String Set；拒絕錯誤 Unicode、未知型別、重複鍵／集合項、截斷、尾隨資料。總大小上限 8 MiB、4096 項、單字串 1 MiB，超界拒絕而非截斷。六項定向回歸通過，包含 NaN 原始位元保留與不合法 Unicode 拒絕；仍需 Android 實機遷移故障注入。

後續切換儲存後端必須同時驗證所有消費畫面、型別相容性、寫入確認、重新啟動讀回、刪除流程與金鑰遺失；不得只加密寫入端而讓畫面持續讀舊檔。

本批未新增感測量測、推估生理值或臨床判讀。實機取數驗收仍需另行執行。

提交前來源重核對已加入：複製期間來源更新會保留兩份證據並拒絕提交；7 項遷移回歸通過。僅此核對無法取代全程單一寫入鎖；正式切換前必須完成 SharedPreferences Editor 型別／clear／remove／commit／apply 相容、序列化磁碟寫入、更新通知、失敗確認與完整遷移鎖。現有 Android adapter 只供隔離驗收，不可讓多個實例同時對正式檔案遷移。

加密檔階段：RawSnapshotCipher 提供 AES-GCM 認證加密、隨機 nonce、版本／用途 AAD、有界讀取與認證解密；EncryptedRawSnapshotFile 使用 AtomicFile、fsync、完整讀回比對及解密驗證。新增 6 項 cipher 測試（錯誤金鑰、nonce／payload／tag 修改、截斷／尾隨及讀取界限）。双端單元測試與 Release、手機 Debug 通過。

手機覆蓋安裝成功後，以 debug-only／DUMP 保護入口執行 raw_snapshot mode：隔離隨機檔的加密混合型別讀回、AtomicFile.failWrite 回復、錯誤金鑰拒收後正確金鑰仍可讀及隔離檔刪除 PASS。證據在 .tooling/verify/raw-snapshot-file-device-20261005.json。未刪除使用者資料、未測真實進程終止／磁碟滿；正式 RawSensorStore 仍讀原 SharedPreferences。

Hermes review 位於代理交換 responses/20261005-xhb-hermes-raw-atomic-file.md。採納故障邊界測試建議；不直接採用定期換金鑰或「metadata 必須加密」泛稱：格式頭為公開且受認證資料，金鑰輪替必須有另行可驗證迁移流程。

Hermes 本機審查已回覆（Obsidian responses/20261005-xhb-hermes-raw-codec.md），僅使用去識別技術摘要。補上非法 UTF-8 payload 與重複鍵解碼測試後，最新共 8 項定向測試通過。其意見未涵蓋加密切換中斷、金鑰遺失或刪除提交邊界，不視為加密遷移核准；Grokbot 尚無已驗證接口。

驗證：手機 `testDebugUnitTest`、`assembleDebug`、`assembleRelease` 通過。原始快照直接開檔呼叫只剩統一入口；刪除流程也經過同一入口。首次編譯發現內部類別與 coroutine 的 `this` 接收者不符，已改用明確 Activity 接收者後重新通過。此批未安裝；Release 仍需正式簽章，建置成功不代表實機感測成功。
