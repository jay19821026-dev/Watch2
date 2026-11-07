# Mi Fitness 3.59.2i manual blood pressure and glucose source trace / 手動血壓與血糖來源追蹤

Author / 作者：王宥傑 (Wang You-Jie)  
Checked / 核對：2026-11-08

## Artifact and scope / 檔案與範圍

The inspected phone package is the installed `com.xiaomi.wearable` version `3.59.2i`, SHA-256 `D7B0A2B45E12899D91F9323BD59DAD8E3531C490CBF1BC83345F5631929CFBE2`. It was pulled read-only from the paired phone and decompiled with JADX. These paths describe **adding records in the phone app**. They do not enumerate every possible import path or establish the paired watch's effective capability list. Decompiled Java is a reconstruction, not Xiaomi's original Kotlin source.

本次分析的是手機上已安裝的 `com.xiaomi.wearable` 3.59.2i。以下路徑是**手機 App 新增紀錄**的流程；它不涵蓋所有匯入路徑，也不能證明配對手錶具有量測硬體。JADX 還原的 Java 不是原始 Kotlin。

## Blood pressure / 血壓

`sources/com/xiaomi/fitness/health/bloodpressure/BloodPressureViewModel.java`, lines 135–141: `insertBloodPressureRecord` constructs a `BloodPressureItem` from the supplied timestamp, high and low values, sets the supplied pulse, assigns source ID `xiaomiwear_app_manually`, then calls `IBloodPressureRepository.addRecord`. The nearby method signature accepts high, low, optional pulse and timestamp. This is a manual record creation path; it has no sensor subscription or watch measurement result in that method.

該方法以傳入的時間、收縮壓、舒張壓和脈搏建立紀錄，指定 `xiaomiwear_app_manually` 後寫入 repository。這一段不是手錶感測器回呼，也沒有從 Watch 2 Pro 讀取量測結果。

## Blood glucose / 血糖

`sources/com/xiaomi/fitness/health/bloodglucose/BloodGlucoseViewModel.java`, lines 263–268 and 558–560: `insertBloodGlucoseRecord` constructs a `BloodSugarItem` from supplied timestamp, glucose value and measurement period, assigns the same manual source ID, then calls `IBloodSugarRepository.addRecord`. A glucose history or entry screen in the phone app is therefore not evidence of a continuous glucose monitor in this watch.

該方法接收血糖值、量測時段與時間，建立紀錄並標成手動來源。手機上能顯示或輸入血糖，不等於這支手錶能自行測血糖。

## Watch and project comparison / 手錶與本專案對照

The installed watch package `com.xiaomi.wear.fitness` (versionCode `2025091121`, SHA-256 `920459E3B2EBF569517E4294029705561E7BEAFF6CCA30EDB142E5A4F83B5965`) has named health modules for ECG, heart rate, SpO₂, stress, body composition, temperature and sleep, but no named `health/bloodpressure` or `health/bloodglucose` module. Module absence alone cannot exclude hidden or separately packaged functionality. No calibrated, permissioned Watch 2 Pro BP/glucose measurement path has been established by these inspected artifacts.

Xiaomi Health Bridge 4.0 test 17 accepts **external cuff readings** in its phone blood-pressure page and labels glucose unavailable. It redirects legacy `optical_bp` result entries to the cuff page, suppresses old systolic/diastolic cache values in result policy, clears legacy BP formula fields during phone snapshot processing, and no longer serializes those fields on the watch. It does not call the OEM repositories or import OEM code. Unit tests check that old cached BP values remain hidden and stale BP keys are cleared.

本專案 test 17 的血壓頁僅供輸入外部袖帶讀值，血糖維持不可用；舊光學血壓結果與同步欄位不再對外顯示成 mmHg。這是根據原廠資料來源修正介面及資料流，並非移植原廠量測演算法。

## Interpretation boundary / 解讀界線

`xiaomiwear_app_manually` proves how **these two add-record methods** label records. It does not prove that every BP/glucose record in Mi Fitness is manual, that the watch has or lacks specific electrodes, or that a model/region flag is the sole gate. A usable automatic measurement would need a real producer path, units and calibration, source metadata, device-specific validation, and a permitted integration route. No clinical accuracy or medical-device approval is inferred here.

這個來源 ID 只能證明上述兩個新增方法的紀錄標籤；不能外推整個資料庫或所有機型。若未來有自動量測，仍須取得實際資料來源、單位、校準與裝置級驗證證據。
