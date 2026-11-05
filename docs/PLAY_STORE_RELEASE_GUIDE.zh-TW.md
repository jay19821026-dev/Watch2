# Google Play 上架指南（繁體中文）

開發者／作者：王宥傑　套件：`com.jaysato.xiaomihealthbridge`　版本：3.13.44（154）

GitHub Release 的 APK 是 Debug 側載測試版，不可當作 Play Store 正式版本。商店版必須使用正式簽章 Android App Bundle（AAB）與 Play App Signing。

1. 在 Play Console 建立「健康與健身」App。
2. 建立並備份 upload keystore，絕不把金鑰或密碼提交 GitHub；依 `docs/release-signing.md` 配置後執行 `./gradlew :mobile:bundleRelease :wear:bundleRelease`。
3. 驗證 applicationId、versionCode、簽章、權限，以及手機／Wear 模組符合最新多裝置發布規則。
4. 填妥中英文商店說明、隱私網址、資料安全、Health apps declaration、內容分級、目標族群、廣告與 App access。
5. 先上傳 Internal testing，以實機驗證安裝、配對、權限、同步、資料刪除及無資料狀態；通過 pre-launch report 後才逐步發布。

商店文案不得宣稱診斷、治療、疾病偵測、緊急監測或臨床準確度。
