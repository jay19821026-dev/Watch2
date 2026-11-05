# Google Play 上架指南（繁體中文）

開發者／作者：王宥傑　套件：`com.jaysato.xiaomihealthbridge`　版本：3.13.44（154）

GitHub Release 的 APK 是 Debug 側載測試版，不可當作 Play Store 正式版本。商店版必須使用正式簽章 Android App Bundle（AAB）與 Play App Signing。

1. 在 Play Console 建立「健康與健身」App。
2. 建立並備份 upload keystore，絕不把金鑰或密碼提交 GitHub；依 `docs/release-signing.md` 配置後執行 `./gradlew :mobile:bundleRelease :wear:bundleRelease`。
3. 驗證 applicationId、versionCode、簽章、權限，以及手機／Wear 模組符合最新多裝置發布規則。
4. 填妥中英文商店說明、隱私網址、資料安全、Health apps declaration、內容分級、目標族群、廣告與 App access。
5. 先上傳 Internal testing，以實機驗證安裝、配對、權限、同步、資料刪除及無資料狀態；通過 pre-launch report 後才逐步發布。

## 新個人帳戶的必要時程

目前 Play Console 顯示此 Google 帳戶尚未建立開發人員帳戶。若建立的是 2023-11-13 之後的新個人帳戶，正式公開前必須先完成封閉測試：至少 12 位測試者連續加入 14 天，之後再申請正式版存取權。Internal testing 可先進行，但不取代這項封閉測試門檻。參考：[Google Play 新個人帳戶測試要求](https://support.google.com/googleplay/android-developer/answer/14151465)。

## Health apps declaration 建議

- 選取「健康與健身 → 活動與健身」及「健康與健身 → 睡眠管理」。
- 不選醫療器材、臨床決策支援、疾病管理、緊急監測、診斷或治療。
- 每個 Health Connect 權限都必須用 App 內可見功能逐項說明，且 Play Console 聲明需與正式 AAB 的 merged manifest 一致。
- 公開隱私政策網址：`https://ailisa.net/portfolio/xiaomi-health-bridge#privacy`。
- 詳細 Data safety 與權限用途文字見 `docs/DATA_SAFETY_DECLARATION.md`。

商店文案不得宣稱診斷、治療、疾病偵測、緊急監測或臨床準確度。
