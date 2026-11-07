# AILISA Health Bridge 4.0.0 test 18 / 測試版 18

Author / 作者：王宥傑 (Wang You-Jie)  
Date / 日期：2026-11-08

## 中文

- 對外名稱改為 **AILISA Health Bridge**，Android 手機與 Wear OS 手錶的新套件識別碼均為 `net.ailisa.healthbridge`；網站、商店文案、隱私政策與宣傳圖同步改名。Xiaomi Watch 2 Pro 與 Mi Fitness 僅作為相容裝置和研究對象的來源描述，本專案與小米公司沒有官方關聯。
- 提供舊手機 App 的 Debug 搬遷橋接 APK；新版「我的 → 匯入舊版資料」可嘗試搬入加密證據資料庫的紀錄及生理設定。舊版不會被刪除；Health Connect 權限需重新授予，手錶本機資料仍留在舊版。已在配對手機透過 App 畫面完成一次匯入；儀器測試 APK 仍被手機拒絕安裝。請保留舊版直到自行核對完成。
- 延續 test 17 後的 ECG 文件修正：區分原廠 ECG 卡片、心電圖提醒與啟用狀態；研究畫面明示原廠紀錄時脈和未校準 Sensor API 事件不可混用。
- 此版本是 Debug 側載測試版，**不是** Google Play 發行版或醫療器材，沒有啟用原廠 ECG，也沒有複製原廠程式碼。

## English

- Public branding is **AILISA Health Bridge**. The phone and Wear OS APKs now use `net.ailisa.healthbridge`. The site, store copy, privacy policy and feature graphic use the same name. Xiaomi Watch 2 Pro and Mi Fitness are mentioned only to describe compatibility and research evidence; this project is independent and not affiliated with Xiaomi.
- An optional debug-signed legacy phone bridge can expose the old app's encrypted evidence records and saved physiology profile to the new app through a same-signature local Android pipe. The old installation remains intact. Health Connect grants must be given again, and watch-local records are not transferred. An import completed through the visible app flow on the paired phone. The phone separately blocked instrumentation APK installation; keep the old app until you confirm counts and behavior.
- ECG documentation distinguishes the OEM ECG card, reminder, and activation gates. The research UI states that OEM record clocks cannot be applied to uncalibrated Android Sensor API events.
- These are **debug sideload builds**, not Play releases or medical devices. They do not enable OEM ECG or include copied OEM code.

## Files / 檔案

| APK | Package / 套件 | SHA-256 |
|---|---|---|
| `AILISAHealthBridge-Mobile-4.0.0-vc172-test18-debug.apk` | `net.ailisa.healthbridge` | `7E462C70B6DEF0CEB5DCD77B510BD863731CAC31DDC880748918F686602F5F2F` |
| `AILISAHealthBridge-Wear-4.0.0-vc172-test18-debug.apk` | `net.ailisa.healthbridge` | `8596CA09D609A9E860AAD606A345E63550F18703BED3C3315C62635192EAB979` |
| `AILISAHealthBridge-Legacy-Migration-Bridge-vc172-test18-debug.apk` | `com.jaysato.xiaomihealthbridge` (legacy update only) | `361642FE32315F65E2D96BA6544AC231CF8A84D97E5C9C4F3C20A1FA5033E771` |

Separate new-package, release-signed **local Play candidate AABs** passed bundletool and signature checks: phone code 173, Wear code 174. They have not been uploaded to Play Console or approved for distribution. / 另已建置新版套件的正式簽章 Play 候選 AAB，手機 173、手錶 174，通過 bundletool 及簽章檢查；尚未上傳或通過 Play 審核。

The two module unit-test suites and Debug builds passed. AAPT verified package IDs, labels and code 172. The legacy bridge and new phone APK installed side by side on the paired phone. The watch was charging at 33%, below the project's 40% installation threshold, so test 18 was not installed or checked on the watch. / 雙端單元測試及 Debug 建置通過，AAPT 確認識別碼、名稱與版號 172；舊橋接版與新版手機已並存安裝。手錶當時充電至 33%，未達 40% 安裝門檻，因此尚未裝入 test 18 或驗證手錶畫面。

See [bilingual installation steps](4.0_TEST_INSTALL.md), [migration guide](PACKAGE_MIGRATION_4_0.en.md), [OEM feature audit](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), and [dataset and paper map](ALGORITHM_DATASET_PAPER_MAP.md). / 詳見中英文安裝、搬遷、原廠功能證據與資料集論文對照文件。
