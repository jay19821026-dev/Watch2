# Package migration / 套件識別碼搬遷

AILISA Health Bridge 4.0.0 test 18 changes the Android package ID from `com.jaysato.xiaomihealthbridge` to `net.ailisa.healthbridge` on both phone and watch. Android therefore installs it as a separate app. **Keep the old apps installed.** The two packages can coexist.

AILISA Health Bridge 4.0.0 test 18 將手機與手錶的 Android 套件識別碼由 `com.jaysato.xiaomihealthbridge` 改為 `net.ailisa.healthbridge`，所以 Android 會視為兩個不同的 App。**請保留舊版，不要解除安裝。**

## Phone migration / 手機資料搬遷

1. Verify that the old phone app is a debug-signed build from this repository. Install `AILISAHealthBridge-Legacy-Migration-Bridge-vc172-test18-debug.apk` **over the old phone app**. This APK keeps the old package ID and must have the same signing certificate. Do not install it on the watch. / 確認舊手機版是本專案同一張 Debug 憑證簽署的版本，將 Legacy Migration Bridge APK 覆蓋安裝在舊手機版上。不要裝到手錶。
2. Install `AILISAHealthBridge-Mobile-4.0.0-vc172-test18-debug.apk` as the new app. Open **我的 → 匯入舊版資料 → 開始匯入**. / 安裝新版手機 APK，開啟「我的 → 匯入舊版資料 → 開始匯入」。
3. Verify the reported session, raw-sample and derived-result counts. The new app creates its own encrypted database and key. The old app retains its data, and a retry verifies matching records instead of silently overwriting conflicts. / 核對匯入的紀錄、原始樣本及推導結果筆數。新版會用自己的金鑰重新加密，舊版仍保留；重試時會核對同一筆資料。
4. Re-grant Health Connect permissions to the new app. Health Connect records remain in Health Connect and are not copied by this bridge. / 重新授予新版 Health Connect 權限；橋接不複製 Health Connect 內的資料。

The bridge transfers the phone's encrypted evidence database content and saved physiology profile through a same-signature Android content-provider pipe. It does not transfer diagnostic preferences, notification permission state, watch-local files/settings, OEM Mi Fitness data, or Android Health Connect grants. The bridge is an **experimental debug migration**. A full import completed on the paired phone through the visible app flow; the device instrumentation APK was separately blocked by `INSTALL_FAILED_USER_RESTRICTED`. Keep both old packages until you verify your own counts and functions. / 橋接範圍僅限手機加密證據資料庫及生理設定，不包含診斷設定、通知授權、手錶本機資料、原廠 Mi Fitness 資料與 Health Connect 授權。配對手機已透過 App 畫面完成一次匯入；另行安裝的儀器測試 APK 被手機限制。請仍保留舊版，直到你確認自己的資料與功能。

The signed Play AAB candidate from test 17 has the **old** package ID and cannot update the new app. A new release-signed `net.ailisa.healthbridge` AAB and Play Console registration are required. Debug APKs must not be uploaded to Play. / test 17 Play AAB 仍是舊識別碼，不能更新新版；必須重新建置正式簽章 AAB 並在 Play Console 建立新的套件。Debug APK 不可上傳 Play。
