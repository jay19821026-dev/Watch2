# 發布稽核 — 2026-10-05

## 現況

本批最終回歸：paired-final-review-20261005.log BUILD SUCCESSFUL，手機 634／Wear 198 項 Debug 測試 failure/error=0；完整 Release lint 無 Error，手機 189／Wear 313 個 Warning，雙端 Release 建置完成。這覆蓋近期快照診斷及 Health Connect 邊界修正，不代表所有 Warning 已解決或完整硬體驗收。ADB 清單仍空，本批未安裝；簽章與正式 raw 加密切換尚未完成。

最新回歸：paired-regression-20261005.log 結束 BUILD SUCCESSFUL；手機 628／Wear 198 個 Debug 單元測試 failure/error=0，雙端 Release 建置成功。重新核對 mobile-release-unsigned.apk 與 wear-release-unsigned.apk，apksigner 均 DOES NOT VERIFY / Missing META-INF/MANIFEST.MF，沒有正式簽章或發布。ADB 清單無裝置；本批修改未安裝，新增背壓 Android 斷言未執行。完整 lint 數字仍以下歷史批次為準，不宣稱本批已重跑完整 lint。

UI 快取修正版已覆蓋安装手機。正式加密庫 audit_production PASS，仍 7 組／896 預覽列、legacy_files_absent=true，證據 `ui-update-archive-20261005.json`。主頁穩定截圖 `ui-update-main-stable-20261005.png` 已目視核對：手錶裝飾圖、摘要標籤與底部導航無明顯裁切；三項摘要明示尚未授權。第一張截圖仍有切頁動畫殘影，未當作版面缺陷。只驗這個畫面，未證明整個 UI、實際 frame time 或耗電改善。手錶仍 USB 充電 24%，未開始腕部新量測。

繪圖配置修正：WatchPortrait 的固定 RadialGradient、LinearGradient、Typeface 與 Path 改為每個 View 初始化一次，不再每次 onDraw 建立。座標、文字及配色未改；這是裝飾圖而非實測波形。手機單元測試、Debug／Release 通過（portrait-draw-cache-20261005.log）。尚未安裝此版或做實際 frame time／耗電對比，不能宣稱耗電改善比例。

本輪集中回歸：手機 590、Wear 198 項單元測試，failure/error 皆 0。Release lint 完成，手機 196、Wear 313 個 Warning、無 Error；不能稱為零問題。紀錄 `.tooling/verify/consolidated-review-20261005.log`，lint XML 位於各模組 build/reports/lint-results-release.xml。

手機警告主要 UseKtx 85、SetTextI18n 65、DrawAllocation 13，另有 QueryPermissionsNeeded 與兩項 StaticFieldLeak，尚需逐一核對是否 application context／已釋放。Wear 主要 SetTextI18n 149、UseKtx 116、WearRecents 27，另有 ClickableViewAccessibility。警告尚未全部修復，不以 lint command 成功等同完整 review 完成。

- 手機與 Wear Release APK 都是 unsigned；本輪 apksigner verify 均回覆 DOES NOT VERIFY / Missing META-INF/MANIFEST.MF。建置成功不等於簽章完成。
- Release merged manifest 不含 Debug 隔離證據自測 Activity；保留工程 shell alias 且以 android.permission.DUMP 保護，並非公開無權限診斷入口。
- 最新手機 Debug 已安裝；Wear bounded outbox 修正版已安裝。正式發布尚未執行。

## 發布阻斷

後續 warning 稽核：SensorSuiteDump.pendingContext 寫入為 context.applicationContext，兩項 StaticFieldLeak 未證實持有 Activity，不以警告文字當洩漏證據。MetricDetailActivity 權限設定入口原先 resolveActivity 預檢有套件可見性誤擋風險，已直接 startActivity 並處理 ActivityNotFoundException／SecurityException，不新增廣泛 QUERY_ALL_PACKAGES。手機測試、Debug／Release、lint 通過（hc-settings-visibility-20261005.log），新版安裝成功；實際設定入口點擊仍待驗收。

1. 使用原授權 Release keystore 完成雙端簽章，核對憑證與現有更新相容性；禁止使用新金鑰冒充既有更新。
2. 完成其他 raw 儲存／匯出加密及生命週期驗收；PPG Room 加密不能代替全部資料範圍。
3. 最新配對腕部量測、資料傳輸／保存／重啟／UI 驗收；缺 Health Connect 權限不可報成無健康紀錄。
4. 真實 Data Layer 中斷恢復、通知、長期耗電及 UI 回歸。
5. 未知導聯、映射、單位、sample clock 與 OEM 校準仍不得換算成醫療診斷。

這份稽核不宣稱完整 code review 或整案驗收完成。
