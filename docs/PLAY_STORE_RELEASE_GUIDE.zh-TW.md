# Google Play 上架指南（繁體中文）

開發者／作者：王宥傑　新套件：`net.ailisa.healthbridge`　目前原始碼：4.0.0 test 18（Debug 版雙端 172）

GitHub Release 的 APK 是 Debug 側載測試版，不可當作 Play Store 正式版本。商店版必須使用正式簽章 Android App Bundle（AAB）與 Play App Signing。

1. 在 Play Console 建立「健康與健身」App。
2. 已在 Git 倉庫外建立候選 upload keystore；先前手機 170／手錶 171 的簽署 AAB 屬於**舊套件**，不可上傳為 AILISA 新套件。新套件的手機 173／手錶 174 候選 AAB 已重建並通過 bundletool 與簽章檢查，仍須核對 Console 並備份金鑰復原資料；不可把金鑰或密碼提交 GitHub，也不可將候選憑證視為已在 Play App Signing 註冊。
3. 驗證 applicationId、versionCode、簽章、權限，以及手機／Wear 模組符合最新多裝置發布規則。
4. 填妥中英文商店說明、隱私網址、資料安全、Health apps declaration、內容分級、目標族群、廣告與 App access。
5. 先上傳 Internal testing，以實機驗證安裝、配對、權限、同步、資料刪除及無資料狀態；通過 pre-launch report 後才逐步發布。

## 新個人帳戶的必要時程

個人開發者帳戶已建立；上次直接檢查時 Google 仍在審查身分文件，「建立應用程式」按鈕尚未開放。2026-11-08 的 Gmail 搜尋沒有找到新通知，但不能代替 Play Console 狀態。若此帳戶適用新個人帳戶規則，正式公開前須完成至少 12 位測試者連續加入 14 天的封閉測試，之後申請正式版存取權。Internal testing 可先進行，但不取代封閉測試。參考：[Google Play 新個人帳戶測試要求](https://support.google.com/googleplay/android-developer/answer/14151465)與[目前上架狀態](PLAY_STORE_RELEASE_STATUS.md)。

可直接使用的中英文招募文案、測試清單、回饋表、每日證據紀錄與正式版存取申請草稿見 `docs/PLAY_CLOSED_TEST_PLAN.md`。

## Health apps declaration 建議

- 依目前可見功能選取「健康與健身 → 活動與健身」、「睡眠管理」及「壓力管理、放鬆與心智能力」；實際送出前再核對最終 AAB 與管理中心表單。[官方分類說明](https://support.google.com/googleplay/android-developer/answer/14738291)。
- 目前無醫療器材核准，不得宣稱醫療器材、診斷、治療或緊急監測。血壓手動紀錄、風險文字等是否觸及表單中的其他醫療分類，須依最終可見功能與實際 Console 問題逐項審查，不預填「否」。
- 每個 Health Connect 權限都必須用 App 內可見功能逐項說明，且 Play Console 聲明需與正式 AAB 的 merged manifest 一致。
- 公開隱私政策網址：`https://ailisa.net/privacy/ailisa-health-bridge`。
- 詳細 Data safety 與權限用途文字見 `docs/DATA_SAFETY_DECLARATION.md`。

商店文案不得宣稱診斷、治療、疾病偵測、緊急監測或臨床準確度。
