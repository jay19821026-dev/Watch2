# Google Play closed-test plan / 封閉測試計畫

App：Xiaomi Health Bridge  
Author / 作者：Wang You-Jie（王宥傑）  
Package：`com.jaysato.xiaomihealthbridge`  
Required gate / 必要門檻：at least 12 testers continuously opted in for 14 days / 至少 12 位測試者連續加入 14 天

This plan is for a new personal Play developer account. The timer and tester eligibility are determined by Play Console, not by this document. Recruit more than 12 people (recommended: 15–20) so a single opt-out does not reset eligibility.

## 1. Track setup / 測試軌設定

1. Create the app with the exact package name `com.jaysato.xiaomihealthbridge`.
2. Enroll in Play App Signing and upload only the owner-authorized, release-signed Mobile and Wear OS bundles.
3. Create a Closed testing track named `closed-14-day`.
4. Use a Google Group or tester email list with 15–20 consenting participants.
5. Publish the closed-test release and copy both the web opt-in and Android opt-in links.
6. Record the Play Console start timestamp and daily eligible-tester count. Do not infer eligibility from invitation acceptance alone.

## 2. Traditional Chinese invitation / 繁體中文邀請

### Subject / 主旨

`邀請參加 Xiaomi Health Bridge Android／Wear OS 封閉測試`

### Message / 內容

您好，我是王宥傑，正在測試開源專案 Xiaomi Health Bridge。這是一款用於 Android 手機與 Wear OS 手錶健康資料整理的研究工具，不是醫療器材，也不提供診斷、治療或緊急監測。

測試需要您：

1. 使用受邀的 Google 帳戶開啟測試加入連結並選擇加入。
2. 在接下來至少 14 天維持加入狀態，期間不要退出測試。
3. 安裝 App，依自己願意授權的範圍測試介面、無資料狀態及權限撤銷；不必提供任何健康數值給開發者。
4. 透過 Play 測試回饋或指定表單回報當機、無法安裝、畫面錯誤與同步問題。請勿在回饋中附上個人健康資料。

測試加入連結：`[PLAY CLOSED-TEST OPT-IN URL]`

隱私政策：https://ailisa.net/portfolio/xiaomi-health-bridge#privacy  
原始碼：https://github.com/jay19821026-dev/Watch2

感謝協助。若不希望參與，請不要加入；已加入者也可隨時退出，但退出後可能不再計入 Google Play 的連續測試門檻。

## 3. English invitation

### Subject

`Invitation: Xiaomi Health Bridge Android/Wear OS closed test`

### Message

Hello, I am Wang You-Jie, the developer of the open-source Xiaomi Health Bridge project. It is a research tool for organizing authorized health records across Android and Wear OS. It is not a medical device and does not provide diagnosis, treatment, or emergency monitoring.

To participate:

1. Open the opt-in link with the invited Google account and join the test.
2. Remain opted in continuously for at least 14 days; please do not leave the test during that period.
3. Install the app and test the interface, empty-data states, and permission revocation using only permissions you choose to grant. You do not need to disclose any health values to the developer.
4. Report crashes, installation failures, display errors, and synchronization issues through Play testing feedback or the designated form. Do not include personal health data in feedback.

Opt-in link: `[PLAY CLOSED-TEST OPT-IN URL]`

Privacy policy: https://ailisa.net/portfolio/xiaomi-health-bridge#privacy  
Source: https://github.com/jay19821026-dev/Watch2

Participation is voluntary. You may leave at any time, but leaving may make the account ineligible for Google Play's continuous-testing count.

## 4. Tester checklist / 測試清單

Testers should mark each item Pass, Fail, Not supported, or Not tested. They must not submit actual health values, screenshots containing notifications, account addresses, device identifiers, or other personal data.

| Area / 範圍 | Test / 測試項目 | Expected / 預期結果 |
|---|---|---|
| Install | Install from the Play closed-test link / 從封閉測試連結安裝 | Installation succeeds without sideloading / 無須側載即可安裝 |
| First launch | Open without granting health permissions / 不授權健康權限開啟 | App explains missing permission and does not fabricate values / 清楚顯示未授權且不產生假資料 |
| Health Connect | Grant only selected read permissions / 僅授權部分讀取權限 | Only authorized categories are shown / 僅顯示已授權類別 |
| Empty state | Use a category with no records / 查看無紀錄類別 | Shows no-data state, not zero or a diagnosis / 顯示無資料而非零值或診斷 |
| Source/time | Open a record detail / 開啟紀錄詳情 | Source and timestamp are visible when available / 有資料時可見來源與時間 |
| Wear pairing | Pair a compatible Wear OS watch if available / 有相容手錶時進行配對 | Connection status is clear; failures are recoverable / 連線狀態清楚且失敗可復原 |
| Sync | Trigger an explicitly supported sync / 執行支援的同步 | No duplicate or silently fabricated record / 不重複、不偷偷產生資料 |
| Revoke | Revoke Health Connect permission / 撤銷 Health Connect 權限 | Subsequent access stops and UI explains the state / 停止存取並說明狀態 |
| Delete | Delete app/local data through Android controls / 透過 Android 控制刪除資料 | Local state is cleared as disclosed / 依揭露清除本機狀態 |
| Offline | Open with network unavailable / 離線開啟 | Local functions remain stable; no false cloud claim / 本機功能穩定且不宣稱雲端同步 |
| Accessibility | Increase text/display size / 放大文字與顯示 | Key controls remain usable / 主要控制仍可操作 |
| Stability | Reopen after process stop/reboot / 程序停止或重開機後再開啟 | No crash; state is explained / 不當機且狀態清楚 |

## 5. Feedback form / 回饋表

- Tester alias / 測試者代號：
- Test date / 日期：
- Phone Android version / 手機 Android 版本：
- Wear OS device tested? / 是否測試手錶：Yes / No
- App version shown / App 顯示版本：
- Areas tested / 已測範圍：Install, permissions, Health Connect, Wear pairing, sync, revoke/delete, empty/error state, accessibility
- Result / 結果：Pass / Fail / Not supported / Not tested
- Reproduction steps without personal data / 不含個資的重現步驟：
- Expected behavior / 預期行為：
- Actual behavior / 實際行為：
- Crash or error text / 錯誤文字：
- May the developer contact you about this report? / 是否同意針對此回報聯絡：Yes / No

Do not request or retain medical history, actual measurements, Health Connect exports, device serial numbers, full account addresses, or unrestricted diagnostic logs.

## 6. Daily evidence log / 每日證據紀錄

Record only aggregate operational evidence:

| Date | Play Console eligible/opted-in count | Release version | New failures | Fix/reference | Notes |
|---|---:|---|---:|---|---|
| YYYY-MM-DD | 0 | 3.13.44 (154) | 0 | — | Test not started |

Keep screenshots of the Play Console count in a private owner-controlled location. Do not publish tester email addresses or account lists in GitHub.

## 7. Production-access application draft / 正式版存取申請草稿

Replace bracketed facts with truthful results from the completed test. Never invent engagement or feedback.

### About the closed test / 關於封閉測試

**How testers were recruited / 如何招募：**  
`Testers were recruited from the developer's personal and professional network after receiving a bilingual explanation of the app's research-only, non-medical scope. Participation was voluntary, and testers were instructed not to submit personal health data in feedback.`

**Tester engagement / 測試參與情形：**  
`[NUMBER] testers remained opted in for [NUMBER] consecutive days. They tested installation, permission-denied and partial-permission states, Health Connect display, empty/error states, revocation, and—where compatible hardware was available—phone/Wear OS pairing and synchronization.`

**Feedback summary / 回饋摘要：**  
`Feedback was collected through [PLAY TESTING FEEDBACK / FORM]. Testers reported [FACTUAL THEMES]. No personal health measurements were requested or retained. The main changes made were [FACTUAL FIXES WITH VERSION/COMMIT REFERENCES].`

### About the app / 關於 App

**Target audience / 目標使用者：**  
`Adults who use Android, Health Connect, and optionally Wear OS and want a transparent, source-aware way to organize their own authorized health and fitness records. The app is not intended for children, clinical diagnosis, treatment, or emergency monitoring.`

**User value / 使用價值：**  
`The app brings authorized phone and watch records into a source- and time-aware interface, distinguishes missing, stale, unsupported, and low-quality states, and documents the provenance and limitations of its non-diagnostic research summaries.`

**Expected first-year installs / 預估首年安裝：**  
Choose the smallest truthful Play Console range based on the actual distribution plan; do not use repository views or GitHub downloads as Play install estimates.

### Production readiness / 正式版準備度

**Changes from testing / 測試後變更：**  
`Based on the closed test, version [VERSION] changed [FACTUAL CHANGES]. Regression tests covered [TESTS], and the signed bundle was re-verified before rollout.`

**Why the app is ready / 為何已準備完成：**  
`The release completed the required closed test, passed the project test suite and Play pre-launch checks, used owner-authorized Play App Signing, matched its Health apps and Data safety declarations, exposed a public privacy policy, and was validated for installation, permissions, revocation, empty/error states, and compatible phone/watch flows. Remaining research and hardware-calibration limitations are disclosed in the app and public documentation.`

## 8. Exit criteria / 結束條件

- Play Console shows at least 12 continuously opted-in testers for the full required period.
- All critical install, crash, permission, privacy, deletion, and data-integrity failures are fixed and retested.
- Final signed bundles, version codes, signer identity, hashes, declarations, listing text, and privacy URL match.
- Pre-launch report has no unresolved blocking issue.
- Production-access answers use actual test evidence, not this template's placeholders.

Official reference: [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465).
