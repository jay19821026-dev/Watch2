# 晶片校準查證矩陣

更新日期：2026-09-21

## ECG / MAX86178

| 項目 | Analog Devices 公開資料 | Xiaomi Watch 2 Pro 裝置資料 | App 狀態 |
|---|---|---|---|
| 導聯 | MAX86178 支援 single-lead ECG | 未公開 ECGP／ECGN 實際接線 | 保留原始通道，不宣稱導聯 |
| ADC 至 mV | 晶片資料表未提供本裝置的完整端到端換算 | 未公開 | 不換算 mV |
| 增益 | MAX86178 可程式化增益；評估平台的設定不代表手錶 | 未公開 | 不套用評估平台增益 |
| 取樣率 | 評估平台可示例 512 SPS；不是手錶設定 | HAL 可觀察到 2 ms 請求週期 | 顯示實際事件時間，保留原始單位 |
| 臨床驗證 | 晶片資料表提到設計目標與 IEC 測試能力 | 未公開 Xiaomi 裝置級驗證 | 不啟用醫療診斷 |
| 房顫 | 需要驗證過的 ECG 演算法與臨床資料 | 未公開 | 只保留原始波形、品質與受載波拒絕保護的研究 RR；不發布房顫判讀 |

2026-09-21 實機短測：公開 Sensor HAL 同時暴露 wakeup／non-wakeup ECG（宣告最高事件率 500 Hz）。`dumpsys sensorservice` 的一段事件相隔約 20 ms，每事件前十個欄位有波形值；25 秒測試回傳 2456 個點，整段平均僅約 97 點/秒，兩者不能互相代替作為逐點 ADC 時鐘。手機舊版曾依整段平均吞吐率估出 174 bpm；這項估計已撤下，ECG 心率及 RR 暫不發布。手機測試版已新增匯出 HAL 事件時間戳，待重新連線後可核對連續區段與缺口。

後續程式稽核發現上述 2456 實際是 **HAL 事件數**：舊版把限長 512 點的畫圖緩衝大小拿來與事件總數比較，導致整段「點/秒」在事件數超過 512 後退化成「事件/秒」；而且同時訂閱 wakeup 與 non-wakeup，可能重複記錄。已改成僅在主通道註冊失敗時才啟用備援，並以累積有效波形點數計算平均吞吐。每事件的時間戳與實際擷取點數會一同保存在**最終**同步封包，以便區分連續段速率與跨段缺口；約 20 ms × 10 點只支持「可能的 500 點/秒封包有效速率」假說，不足以證明 OEM ADC 時鐘或醫療校準。

依 Android 公開 `SensorAdditionalInfo.TYPE_SAMPLING` API，測試版也會嘗試記錄 HAL 提供的原始採樣週期／jitter 與是否支援 additional info。這些欄位需下一次手錶實測才知道有無提供；若缺席，不能用封包間隔代替。外部 raw:mV 線性擬合現在拒絕長度不符、非有限值與沒有振幅變化的參考點，仍不代表通過獨立增益或臨床驗證。

為支援後續裝置級配對，最終 ECG 封包保留最近至多 5120 個**未縮放的原始點**及對應的最多 512 個事件時間戳／每事件點數；中途同步仍只送 512 點預覽。手機分別保存原始點與去基線顯示波形，匯出時提供原始點，且以最終封包時間戳阻止較舊的中途預覽覆蓋結果。依 Android Data Layer 文件，單個 DataItem 負載上限約 100 KB；5120 個 float 原始點與 512 組時間／點數欄位在此限額內。此資料仍未包含 OEM ADC 換算、電極導聯或臨床校準值。

ECG 事件若帶有無效時間戳，仍保留事件位置和點數以維持波形對齊，但不納入速率估算。匯出的 `timeline_aligned` 僅檢查事件數、每事件點數總和與原始波形長度一致；它是資料完整性檢查，**不是**電氣／醫療校準通過證明。

每次量測現在帶有獨立的起始時間識別碼；手機會拒絕遲到的上一輪封包，並在新一輪預覽到達時清除舊 ECG 時序中繼資料。ECG 前十個原始波形欄位的零值會保留，不再刪除零值或在全零時從後續中繼欄位「補」成波形。這維持每事件點數與波形的原始對應，仍需新版手錶實機驗證。

### 原廠 APK 查證結果

已從手錶原廠套件 `com.xiaomi.wear.fitness`（版本 `2025091121`）取得唯讀檢視結果。APK 內包含 `lib/armeabi-v7a/libmi_ecg_alg.so`，可見原生符號包括 `mi_ecg_get_analysis_results`、`mi_ecg_get_average_hr`、`qrs_detection_process`、`qrs_af_diagnosis` 與 `mi_ecg_get_version`。

這證明原廠有私有 ECG 演算法模組，但 APK 沒有公開 Xiaomi Watch 2 Pro 的導聯圖、ADC 換算係數、增益設定、校準證書或臨床驗證報告。這個私有原生庫也不會被複製進本 App 或以未授權方式載入；目前 App 只使用公開 Sensor HAL，避免繞過簽章、授權與原廠資料邊界。

## BIOZ / MAX86178

MAX86178 支援多種 BioZ 激勵與接收模式，但手錶的激勵頻率、電流／電壓、電極接法、相位校準與阻抗係數沒有公開。App 保留原始通道與接觸品質；未驗證 HAL 欄位單位前不輸出 Ω、相位角、體脂、體水或血糖。原先依數值量級猜測 Ω 並推估含水指數的路徑已移除。

## PPG / SpO2

晶片資料表提供光學前端能力與雜訊參考，但手錶的 LED 電流、光電二極體配置、暗電流補償與 SpO2 演算法係數未公開。App 只做填充值剔除、波形品質與佩戴狀態檢查；不再將低速封包強制補成 25 Hz ADC 取樣率。

### 原廠 APK 新增查證

原廠 Fitness APK 的資源字串提供了可驗證的操作層資訊：量測時以中指與無名指接觸手錶兩個按鍵、保持手臂平放與靜止、避免手指接觸錶框，並指出心率低於 50 或高於 150 BPM 時 ECG 房顫結果會是 inconclusive。這可作為使用者引導與資料品質門檻，但不是 ECGP／ECGN 電氣導聯配置，也不是 ADC 校準資料。

同一 APK 明確說明皮膚溫度量測範圍為 34–42°C，且會受環境、佩戴時間、充電與機身溫度影響；這能支援體溫 UI 的量測條件提示，但仍沒有裝置級 offset／slope 或臨床驗證曲線。

### Xiaomi ECG-V1 文件的裝置範圍

網路上找到的 Xiaomi ECG-V1 電子說明（鏡像保存的原廠 e-IFU 內容）公開了另一個 ECG 產品的參考資料：單導聯 Lead I、24-bit、500 samples/second、標準 ECG 輸入準確度 2 BPM，以及 355 名成人受試者的房顫／竇性心律研究（含與 12 導聯 ECG Lead I 及心臟科醫師判讀的比較）。但同一文件明確寫明相容裝置為 **Xiaomi Watch 5 或更新型號**，並非 Xiaomi Watch 2 Pro。

因此這些數值與 98.65%／100% 臨床結果只能作為「Xiaomi ECG-V1 參考證據」，不能填入 Watch 2 Pro 的校準 profile，也不能用來宣稱本 App 或 Watch 2 Pro 已完成房顫驗證。

## 其他公開手錶感測器

Xiaomi 官方規格公開列出光學心率、加速度計、陀螺儀、環境光、電子羅盤、氣壓計與生物電阻抗感測器。這些通道可使用 Android Sensor HAL；磁力計、旋轉向量與氣壓高度只做工程計算，不代表醫療校準。

## 感測器校準稽核矩陣

| 通道 | 裝置級公開校準 | 目前可安全輸出 | 尚缺證據 |
|---|---|---|---|
| PPG／SpO2 | 未公開 LED 電流、光電二極體配置、補償與 SpO2 曲線 | 原始波形、接觸品質、研究用脈率 | OEM 光路、校準曲線與臨床驗證 |
| ECG | HAL 可取得事件；導聯、ADC、增益、時鐘未公開 | 原始波形、相對基線、品質與受品質門檻限制的研究 RR | ECGP／ECGN 接線、LSB、增益、逐點時鐘、臨床資料 |
| BIOZ | 激勵模式、頻率、電極幾何與相位係數未公開 | 原始波形與訊號品質 | 激勵參數、阻抗係數、體成分模型 |
| 體溫 | 未公開皮膚溫度轉換與環境補償 | 有事件時的原始趨勢 | 感測器識別、offset／slope、參考溫度驗證 |
| 氣壓計 | 可讀壓力，但沒有裝置級絕對高度校準證書 | 相對高度、爬升趨勢 | 海平面壓力來源與路線驗證 |
| 加速度／陀螺儀 | HAL 有校正與未校正向量；沒有跌倒醫療校準 | 姿態、活動與研究風險指標 | 軸矩陣、偏置、跌倒資料集與前瞻驗證 |
| 磁力計／旋轉向量 | 可算方向，但受環境磁場影響 | 指南針、返航輔助 | 磁校正矩陣、磁偏角與路線驗證 |
| 環境光 | 可取得 lux（若 HAL 暴露） | 夜間省電與亮度建議 | OEM lux 曲線與系統亮度權限 |

SpO₂ 額外防呆：`spo2_raw` 與 `mi_spo2` 的 Sensor HAL 封包均保留為原始資料，沒有原廠單位證據前不當作百分比、不套用臨床門檻，也不產生模擬血氧曲線。Android 的 `SensorEvent.values` 內容依感測器型別而異；只有 Health Connect 的 `OxygenSaturationRecord.percentage` 明確代表血氧百分比。實測原始封包曾出現大於一百萬的數值，因此單靠通道名稱或範圍推測單位並不安全。

程式稽核補正：先前 `WearDataProcessor` 仍會從 `mi_spo2`／`spo2_raw` 的原始封包挑選恰巧落在 70–100 的欄位並送入百分比判讀；這與上述邊界矛盾，已移除。此兩種 HAL 通道現在固定輸出 `spo2_vendor_unit_unverified`，只保留資料存在與原始樣本數；僅 Health Connect 的具名百分比 OxygenSaturationRecord 走臨床百分比 UI。

同一稽核也移除了手錶 `RawOxygenProbe` 的未驗證百分比同步：它曾將 `mi_spo2` 的第一欄或 `spo2` 封包的固定欄位當百分比，並寫入手錶歷史與手機資料層。現在手錶只擷取、顯示和記錄原始事件；不會以欄位位置、數值落在 70–100 或短期穩定性推測血氧百分比。原始探針使用不快於該感測器回報的最低週期（至少 10 ms）、逐一記錄實際成功註冊的通道，取得事件即停止，無事件時最長 60 秒後停止。這降低舊版 FASTEST／10 分鐘上限造成的耗電風險，但仍待真機測量驗證實際功耗。Wear OS release 曾於 2026-09-21 經 ADB `192.168.0.16:5555` 安裝成功（`com.jaysato.xiaomihealthbridge` versionCode `69`）；這不代表包含本次修正的版本已安裝。

升級遷移：新版第一次啟動時會清除橋接 App 在手錶本機以舊版 raw 欄位推測而寫下的 `oxygen` 歷史、最新值與時間戳；不會刪除 Health Connect 裡由其他來源寫入的任何紀錄，也不會刪除使用者手動輸入的百分比。2026-09-21 已完成手機與手錶 release 重新建置、手機單元測試與雙端 ADB 覆蓋安裝。

### 外部 ECG 研究 profile 流程

手機「臨床參考實驗室」現在可輸入至少三組**同步** `raw:mV` 配對和隔離式外部參考來源。App 會計算並儲存 slope、offset、RMSE、點數、來源與建立時間；只有係數有限、斜率非零、來源非空且至少三點時允許保存。保存後僅在該研究頁使用保留的 `ecg_raw_waveform` 顯示 mV 預覽，並明示來源與 RMSE；未保存時仍使用未縮放原始波形。此 profile 不會反推 Xiaomi OEM register、導聯、ADC LSB 或臨床驗證，也不會把房顫判讀解鎖。

2026-09-21 新增手錶「ECG RESEARCH」獨立量測頁：使用公開 ECG Sensor HAL 顯示**實際**原始波形，在 20 秒工作階段結束後才將完整最終封包同步到手機臨床參考實驗室。頁面顯示由事件時間戳與每事件波形點數推得的有效點率、HAL 額外資訊提供時的 jitter、樣本品質與候選研究 RR。若波形呈高頻重複載波、固定值或樣本不足，會拒絕輸出 RR；候選 RR 不是 mV 校準、不是心率醫療量測，也不是房顫判定。

視覺稽核補正：臨床實驗室的體溫卡和全感測器總覽不再以正弦函數產生「示意波形」。體溫僅繪製 20–45°C 範圍內的實際原始樣本（或單一實測快照）；總覽只會繪製 PPG、ECG 或 BioZ 的實際波形。無有效樣本時圖表保留空白，不以動畫／假曲線暗示已取得量測。

### 證據邊界（2026-09-21）

Analog Devices 公開資料證實 MAX86178 是可支援單導聯的 AFE，並提供晶片參考電氣性能；但它同時是可配置的 ECG／BioZ 前端，不代表 Xiaomi Watch 2 Pro 的實際設定。小米公開規格列出消費級感測器清單，但沒有公布手錶的導聯接線、ADC 係數、增益、取樣時鐘或臨床驗證常數。原廠 APK 內存在私有 ECG 演算法庫，也不能反推出上述裝置級參數。

因此以下欄位刻意維持 `unknown`：導聯配置、ADC-to-mV、原廠增益、原廠取樣時鐘、BIOZ 阻抗換算與房顫臨床驗證。不能把評估板設定或晶片規格冒充手錶校準值；若未來取得 OEM 校準 profile，可直接接入既有原始資料管線。

2026-09-21 再核對 Xiaomi Watch 2 Pro 官方感測器規格：列有光學心率、加速度、陀螺儀、環境光、電子羅盤、氣壓計與生物電阻抗，並未公布 ECG 的導聯或電氣校準。Android `SensorAdditionalInfo.TYPE_SAMPLING` 若由 HAL 提供，僅能給出原始採樣週期與抖動，不能推得電極接線、ADC-to-mV 或臨床驗證。已擷取到的 ECG HAL 事件因此只能證明可取得原始事件，不能證明 ECG 醫療功能可用。

2026-09-21 再次查核官方資料：Xiaomi Watch 2 Pro 最新公開規格仍只列出光學心率、加速度、陀螺儀、環境光、電子羅盤、氣壓與 BioZ，未列 ECG。Analog Devices MAX86178 資料表則明確說明 ECG 的增益、濾波、ADC 與內建校驗皆為**可程式化**。因此資料表的 ENOB、雜訊與輸入範圍只能用於設計／品質界線；沒有 Watch 2 Pro firmware register profile 時，不能計算本機 ADC LSB 或 raw→mV 係數。

下一個裝置級驗證門檻：手錶重新連上 ADB 後，記錄同一量測工作階段的原始 ECG、每封包單調時間戳與樣本數、HAL `TYPE_SAMPLING`，確認波形與時間軸完整；再以已知振幅／頻率的 ECG 校正器（隔離且符合安全規範）同步注入，至少跨多個振幅與頻率點做獨立驗證。沒有實際電極接線證據與參考設備時，不寫入轉換係數，也不開啟診斷。

實機日誌 `dist/ecg-measurement-20260921.log` 顯示 Sensor HAL 曾接受 ECG 訂閱（請求週期 2,000,000 ns），但同時回報 `Background activity launch blocked`，因此舊版從前景服務啟動原廠 ECG 畫面沒有成功。請求週期不是已驗證的實際 ADC 時鐘。已改為只有在使用者於手錶主畫面點擊 ECG 時，才從可見 Activity 同步啟動原始資料服務並嘗試切換原廠 App；手機遠端指令僅訂閱原始通道，不再宣稱已開啟原廠頁面。這個修正仍需手錶重新連線後實測。

原廠手錶 APK `dist/Fitness-watch-official.apk`（`com.xiaomi.wear.fitness`，versionCode `2025091121`）的 AndroidManifest 經 `aapt dump xmltree` 核對，`EcgMainTileActivity` 與 `EcgInstructionActivity` 皆宣告 `android:exported="true"`，且前者有同名 action 與 `DEFAULT` category。這證明入口對第三方可尋址，但不證明目前手錶安裝的是同版、不證明 OEM 頁面一定接受量測，也不提供導聯／ADC 校準常數。先前失敗的明確原因仍是背景啟動限制；下一步需在手錶前景實際點擊並記錄 Activity 與 HAL 結果。

舊版實機匯出 `dist/sensor-suite-device-latest.json` 的 ECG 封包包含 16 個 float；其中範例前 10 個為連續變動數值，後 6 個依序是 `1, 7110, 0, 0, 0, 0`。不能由這一筆資料確定各欄位語義，尤其不可把 `7110` 擅自解讀成增益或時鐘。新版在波形之外，同步保留每個事件的後段原始欄位，與事件時間戳逐列對齊並輸出 `metadata_rows_aligned`，供日後對照原廠文件與參考儀器。舊匯出只有 512 點預覽、缺完整逐封包時間軸，不能用於裝置級校正。

2026-09-21 於實機 ADB `192.168.0.16:37701`／`5555` 完成新版資料鏈驗證：原始 ECG HAL 成功啟用約 25 秒，未再發生前景服務崩潰；每個事件封裝 10 個波形點，事件時間差固定 20,000,000 ns，因此有效點速率為 **500 points/s**，觀察區段的事件 jitter 為近零。部分 Xiaomi HAL 時間戳為負的長整數，但仍嚴格單調遞增；校驗邏輯已改用相鄰差值，不再錯誤要求絕對時間戳為正。

同一實測波形在 25 點 lag 的自相關約 0.982，等價於 500 Hz 下約 20 Hz 的高度重複載波／測試樣式；舊算法曾誤報 165 bpm。新版會將高於 4 Hz 且自相關達 0.98 的高度重複樣式標記為非人體 RR，停止發布心率與 RR。這項修正保留真正低頻、通過品質門檻的研究級 RR，但仍不構成房顫診斷。實測資料保存在 `dist/raw-sensor-results-37701.xml`、`dist/raw-sensor-results-37701-v2.xml` 與 `dist/ecg-live-37701-v2.log`。

UI 狀態修正：在取得外部參考 ECG 的同步配對點、raw-to-mV 擬合與獨立驗證前，ECG 一律標示 `research_signal_unscaled`，不再以高波形品質標示 `calibrated_research`。只有成功寫入外部參考 profile 並套用到原始波形時，才可使用 `calibrated_external_reference`（仍不解鎖 AF／醫療診斷）。BIOZ 同樣固定為 `research_signal_unscaled`（無 OEM Ω）。

2026-09-21 新增手機「手錶功能對照表」：列出健康總覽、訓練、導航、感測、安全與醫療邊界，對照 Apple Watch 功能體驗並誠實標示可用／研究／部分／不可用。ADB `192.168.0.16:46809` 用於本輪安裝驗證。

2026-09-21 於實機 ADB `192.168.0.16:35593` 再次驗證：`max86178_ecg` 已由本 App 啟用，`sensorservice` 顯示請求週期 `2.00 ms`，HAL 宣告 `maxRate=500 Hz`。連續三個封包的時間戳相隔約 `19.99–20.02 ms`，每封包 10 個前段波形值，和 500 points/s 的有效資料節奏一致。封包後六欄仍原樣保留（例如 `9,1490,0,0,0,0`），沒有猜測其為增益、mV、lead 或時鐘。此驗證確認 **相對時間軸與封包完整度**；它不提供 ADC LSB、mV offset、導聯配置、原廠濾波／增益或臨床／AF 驗證，因此校準狀態維持研究級未縮放。

同次連線亦以原廠公開 intent 成功叫起 `com.xiaomi.wear.fitness/.health.ecg.EcgMainTileActivity`：ADB 回傳 `Status: ok`、`LaunchState: HOT`，且 ActivityManager 顯示該 Activity 為 `ResumedActivity`／`mFocusedApp`。這完成了原廠 ECG **前景操作入口**的實機驗證；它仍不代表使用者已通過原廠接觸檢查、不會外洩私有 ECG 演算法，也不會改變本 App 的非診斷界線。

## 官方來源

- https://www.analog.com/media/en/technical-documentation/data-sheets/max86178.pdf
- https://www.analog.com/media/en/technical-documentation/user-guides/max86178evkit.pdf
- https://www.analog.com/media/en/technical-documentation/user-guides/maxrefdes106.pdf
- https://www.mi.com/it/product/xiaomi-watch-2-pro/specs/
- https://developer.android.com/reference/android/hardware/SensorEvent
- https://developer.android.com/reference/android/health/connect/datatypes/OxygenSaturationRecord
- https://developer.android.com/reference/android/hardware/SensorAdditionalInfo
- https://developer.android.com/guide/components/activities/secure-bal
- https://device.report/m/e8a47dec4c8ba2b10d03fd530d373d3d11af294793479cbe40ad3ebae92aaa6f

未經 Xiaomi 提供裝置級 firmware、register profile、電極圖、校準證書與臨床驗證報告，不應將 MAX86178 評估平台參數寫成 Watch 2 Pro 的校準常數。

原廠 APK 資源中的上述操作與限制字串，已作為「OEM 操作證據」保存；它們不會被升格為電氣校準、醫療診斷或房顫驗證。
