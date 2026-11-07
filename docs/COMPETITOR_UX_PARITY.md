# Competitor UX／功能對齊盤點（Apple／Samsung／Google）

目標：功能槽位＋UIUX 流程對齊消費級智慧錶／健康 App，而非僅文字對照表。

## 手機端目標 IA（對標 Apple Health + Samsung Health）

| 區塊 | Apple | Samsung | Google/Fitbit | 本 App 目標 |
|---|---|---|---|---|
| 今日／摘要 | Summary＋Vitals | 主畫面卡片 | Today／Vitals | **Vitals 總覽**：大數字卡片＋量測 CTA |
| 瀏覽分類 | Browse：Heart／Sleep／… | 健康分類列表 | Browse | **瀏覽**：心律／血氧／睡眠／活動／身體 |
| 量測流程 | 專屬 App；結果回 Health | Monitor／量測頁 | 量測→結果 | **等到有數據才結束**（無固定秒數）→結果頁 |
| ECG | 專屬 App→分類→症狀 | Monitor ECG | ECG App | 獨立 ECG＋結果頁對齊 |
| 心率 | Heart Rate 歷史 | HR 卡片＋歷史 | HR 趨勢 | 心率詳情＋歷史區間切換 |
| SpO₂ | Blood Oxygen | 血氧卡 | SpO₂ | 量測流程＋結果（誠實無未校準 %） |
| HRV／壓力 | HRV／Vitals | 壓力分數 | Stress | HRV／壓力結果頁（等到足夠 RR） |
| 睡眠 | Sleep 分期 | 睡眠階段 | Fitbit 分期 | **Sleep 專頁**：總時長＋分期誠實不可用 |
| 運動 | Fitness／Workout | 運動 | Exercise | 運動頁對齊開始／暫停／結束 |
| 體溫 | Wrist temp | 皮膚溫 | 皮膚溫 | 體溫量測結果 |
| 體成分 | — | BIA | — | BioZ 結果（無 Ω 誠實） |
| BP | 部分區趨勢 | 袖帶校準 BP | 趨勢／外接 | 外部袖帶手動紀錄＋參考分類；手錶不顯示光學估計 |
| 血糖 | 外接 | 外接 | 研究 | 功能槽位＋不可用說明（不造假） |

**Current override (2026-10-10):** Wear BP page is informational only and does not start sensors or estimate mmHg. Phone BP categories apply to recorded cuff/Health Connect readings only. Earlier checklist items below describing a watch optical-BP estimate are historical and superseded.

## 手錶端目標 UX（對標 watchOS／One UI Watch／Wear OS）

| 體驗 | 對標 | 本 App |
|---|---|---|
| App 格 | 表盤→App 列表圖示 | **Apps 格**：心率／ECG／血氧／HRV／PPG／睡眠／運動／體溫／BioZ／壓力／血壓／指南針 |
| 量測中 | 全螢幕波形＋接觸提示 | 全螢幕量測；**有數值才結束** |
| 結果 | 大字分類＋Done | 大字結果＋臨床結論＋同步手機 |
| Vitals | 夜間指標一覽 | 手錶 Vitals 摘要頁（血壓入口僅顯示限制說明） |

## 誠實不可用（仍保留功能槽位與對齊 UI）

臨床 AF 診斷、未校準 SpO₂%、BIA 體脂、PSG 睡眠分期、手錶血壓、PAI——UI 顯示「此裝置／API 不可用」或研究標籤，不灌假醫療數值。血壓只使用外部袖帶紀錄；舊光學估值不得再顯示為 mmHg。

## 實作進度

1. ✅ 手機 Vitals 總覽＋Browse 分類入口  
2. ✅ 手錶 Apps 格狀啟動器  
3. ✅ 量測改為等到有數據（3.10.2）  
4. ⚠️ 歷史：光學血壓研究估算 API 曾存在；目前手錶端僅顯示未支援說明，手機只分類已有袖帶／Health Connect 讀值  
5. ✅ Sleep 專頁（總時長＋分期誠實）  
6. ✅ 心率歷史日／週／月／年（手機 MetricDetail＋手錶 WearHeartHistory）  
7. ✅ 心律通知（手機 IrregularRhythm＋手錶 WearRhythm）；量測完成大數字；睡眠讀同步時數  
8. ✅ Browse 擴充 ECG／HRV／噪音／他錶對照；Samsung BIA／BP 不經第三方 SDK 誠實標示  
9. ✅ 手機 MeasureResult 大數字 Done＋量測完成通知；噪音雙端同步  
10. ✅ max_hr 光學封包不再誤判為 bpm；心率量測優先 Health Services（3.11.3）  
11. ✅ 省電：被動心率節流、極省停被動 HR、縮短 suite wakelock  
12. ✅ 手錶運動專頁：本頁開始／暫停／結束＋即時 bpm／計時並同步（非跳轉主畫面 stub）  
13. ✅ 手機 Vitals 壓力／運動槽位；HRV 結果走大數字 Done  
14. ✅ 光學量測：雙路註冊 wakeup＋更快 invalid 切換；PPG 優先 max_hr／LifeQ；被動 HR 90s／省電 3min／極省停傳  
15. ✅ feature_snapshot 單調合併（拒舊覆寫）；開啟公開功能即啟動 WHS＋推送快照；光學血壓頁同套 until-data／onStop 修復  
16. ⚠️ 歷史：曾有 WHS／脈率推估；該路徑不代表裝置血壓功能，現行 Wear 畫面已停用推估  
17. ⚠️ 歷史：先前估值 UI 已由「不支援 mmHg 推算」說明取代  
18. ⏳ 原廠級 AF 核准、PSG 分期、核准袖帶 BP、BIA 體脂——無公開 API，維持誠實槽位  
