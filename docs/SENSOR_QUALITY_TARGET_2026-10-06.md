# 感測品質改善目標

## 當前使用者目標

今天攻 ECG、PPG、BioZ、體溫、血管年輕程度。協作 AI 查詢資料並審查，讓手錶與手機呈現具來源依據的數值；診斷效能需另外驗證。完整目標尚未完成。

## 已完成審查及修正

- Codex 子代理已詢問現有 Claude 光學審查分頁；只送去識別技術問題。
- Claude 支持呈現樣本數、有限值比例、平線及原始統計；時鐘、通道與參考儀器仍需驗證。
- QA 發現單一突波可令近乎平線 PPG 得99分；已加入相鄰變化覆蓋懲罰並新增回歸測試。
- 手機頁面 grade 未定時改顯示原始資料參考，避免標題越過評分狀態。
- BiomedicalCalibrationTest、ClinicalVerdictTest 與 mobile assembleDebug 通過；未代表新一輪硬體採集成功。
- 公開 Wearable Exam Stress 下載已啟動，但尚未取得完整檔案或完成基準比對。

下載更新：腕部整包在120秒後逾時，4832829 bytes 檔案為不完整 ZIP，不可使用。已取得 BIDMC bidmc01.hea、bidmc01.dat 與授權文字。header 為5通道、125 Hz、60001樣本；dat 600010 bytes，符合16-bit交錯長度。包括 PPG、阻抗呼吸與 ECG，尚未做演算法基準，不作使用者疾病判定或小米校準。

基準更新：新增 tools/bidmc_signal_benchmark.py，以 header 的 gain、baseline、unit 解碼5通道並驗證完整長度；已執行通過60001 frames，輸出 artifacts/research-datasets/bidmc-benchmark.json（含SHA-256及描述統計）。平線與單一突波對照通過。這是解析與統計基準，尚非濾波效能或疾病分類驗證。

血管研究代理結果：SI=身高/脈波反射延遲需可信秒尺度；ECG–PPG 為PAT，包含射血前期。腕部未知時鐘不能轉成血管年齡。參考 https://doi.org/10.1042/cs1030371 與 https://pmc.ncbi.nlm.nih.gov/articles/PMC2948201/ 。後續需參考cfPWV、血壓及裝置驗證。

## 雙端追查更新

新一輪PPG實機：session_started_at=1791297352001、8事件；手機收到8事件/128原始值，quality_score=14，source timestamp missing/time=0。已證明此輪擷取與同步有事件，未證明可換算脈率。

使用者要求補可用flags：Snapshot.evidenceFlags()新增事件、有限值、來源時間、callback時間、HAL週期狀態；publisher傳evidence_flags/version，手機保存。flags不生成量測值、時間或時鐘。雙端Debug與Wear時間解析測試通過；手機安裝成功，手錶安裝仍待回覆。

SQLite更新：現有加密Room證據庫擴展final preview封存至ECG、BioZ、溫度及原始血氧。session metadata保存四項可用性bool、producer flags/version、原始量測時間、發布時間和回報取樣率。新metadata用preview-v2 session key，保留舊紀錄不可變。修正原先使用封包timestamp當source time的問題。新增BioZ flags與重播一致性instrumentation測試；Debug及測試APK建置通過。實機安裝被系統回報INSTALL_FAILED_USER_RESTRICTED，未完成新版本安裝或資料庫測試；0 tests不算通過。

整理更新：證據頁可切換PPG、ECG、BioZ與兩種溫度通道，讀取SQLite flags、來源時間／發布時間及有限原始值統計。封存後另存raw-preview-statistics-v1衍生結果，包含有限比例、均值、範圍及可用性gate；重播驗證一致，刪除衍生結果不刪原始。未對生理換算宣稱可用。手機Debug、測試APK建置與archive input/ack單元測試通過；新增資料庫實機測試仍待允許安裝。

metadata v3更新：另存事件原始欄位、callback elapsed時間及source session邊界；callback標為receipt envelope，不提升為量測時鐘。metadata UTF-8上限1MB，超限拒絕整包且保留待確認DataItem。Debug與instrumentation APK建置通過；實機測試未執行。

安裝異常追查：connectedDebugAndroidTest嘗試後pm path及pm list packages對雙端主套件皆無結果；測試流程可能清理主APK，未確認因果。改以指定serial直接ADB安裝，避免再跑connected task。手機重送仍INSTALL_FAILED_USER_RESTRICTED，已請使用者允許USB安裝；手錶重裝命令待回覆。不得將成功build或單次install回覆替代pm path及UI驗證。

- 實機手錶未充電、82%；頁面被就寢模式錶面覆蓋。PPG 診斷紀錄64事件屬舊session，不能宣稱本輪取得新量測。
- 手機PPG保存1024原始值，但量測時間0且來源標記sensor_event_timestamp_ahead_of_capture；尚需新session對照，不歸因為程式閹割。
- Wear 時間分類代理未找到可證實的程式錯誤；現有9個時間轉換測試通過。需事件時間與ingestion monotonic時間的配對證據。
- 溫度代理新增TemperaturePresentation，將值、時間、狀態、來源及波形綁定同一通道，顯示實際時間及新鮮度。5個專項測試、ClinicalVerdictTest、mobile assembleDebug通過；已重新安裝手機Debug APK。
- ECG、BioZ及血管年齡仍未完成新session及參考驗證；總目標維持未完成。

Claude 核實參考：https://mhealth.jmir.org/2021/2/e19210/ （溫度參考校正與環境影響）。其餘未核實引用不採用。

目標：PPG、ECG、BioZ、溫度逐通道改善工程品質，90/100 是待驗證目標，不是校準係數或診斷準確率。不得為達標修改評分權重、刪除低品質區段後冒充全段結果，或只反覆測試直到高分。

## 外部審查

2026-10-06 已向登入的 Gemini 網頁提交去識別技術問題，未傳送波形或程式碼。

Gemini 建議 PPG 自適應濾波、ECG 雙偵測器一致性、溫度穩定性檢查。這些是待驗證候選，尚未實作或證明適合本裝置。

駁回直接套用部分：BioZ 與 EDA 混用；未知時鐘套固定 Hz 濾波；未經核實的引用及溫度／抖動門檻。時間戳差值只有在確認每事件樣本數、批次與來源時鐘後才能推算取樣率。

## 驗收要求

- 每個通道分別報告原始品質、處理後品質、區段覆蓋率、拒絕原因與版本。
- 固定預先定義的評分方法，以保留 session 驗證；同時記錄所有失敗量測。
- PPG：先取得新事件及可信通道；再評估平線、飽和、動作關聯與脈搏特徵。
- ECG：驗證封包解碼與相對時間，比較濾波前後峰值位置和形態，禁止以品質分數代替 AF 驗證。
- BioZ：確認激勵頻率、電極與欄位含義後再換算阻抗；目前先驗證重複性與原始漂移。
- 溫度：核對通道、單位、接觸穩定和環境影響，再驗證趨勢與參考儀器偏差。

尚未達成全部通道90/100；尚未完成裝置校準或本輪實機驗收。
