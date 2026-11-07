# Google Play listing copy / 商店上架文案

Author / 作者：Wang You-Jie（王宥傑）  
Package / 套件：`com.jaysato.xiaomihealthbridge`  
Category / 類別：Health & Fitness / 健康與健身  
Website / 網站：`https://ailisa.net/portfolio/xiaomi-health-bridge`  
Privacy policy / 隱私政策：`https://ailisa.net/portfolio/xiaomi-health-bridge#privacy`  
Support email / 支援信箱：`s4140666@gmail.com`

The text below is ready to paste into Play Console after checking it against the final signed AAB. It deliberately avoids medical-device, diagnosis, treatment, emergency-monitoring, and clinical-accuracy claims.

## Traditional Chinese (zh-TW)

### App name / 應用程式名稱

`Xiaomi Health Bridge 健康橋接`

### Short description / 簡短說明

`在 Android 與 Wear OS 間查看、同步並整理個人健康與健身紀錄。`

### Full description / 完整說明

Xiaomi Health Bridge 是由王宥傑開發的 Android 與 Wear OS 開源健康資料研究專案，協助使用者在手機與手錶之間查看、同步及整理自己授權的健康與健身紀錄。

主要功能：

• 透過 Health Connect 顯示有日期、來源與狀態的個人紀錄  
• 整理心率、步數、距離、活動熱量、運動、睡眠與其他已授權指標  
• 支援 Android 手機與 Wear OS 手錶間的配對資料流程  
• 顯示無資料、資料過期、品質不足、不支援或同步失敗等狀態  
• 提供日／週趨勢及研究用途的非診斷性摘要  
• 可由使用者撤銷權限，並透過 Android 與 Health Connect 控制資料

隱私與透明度：

本版本沒有開發者健康資料伺服器、廣告或第三方分析 SDK。健康資料只用於使用者授權的 App 功能，並依 Android、Health Connect 與 Wear OS 平台機制處理。完整原始碼、資料來源、演算法與論文對照、限制及隱私政策皆公開可查。

重要限制：

本 App 是研究與個人資訊整理工具，不是醫療器材，不提供診斷、治療、疾病偵測、緊急監測或臨床準確度保證。身體不適或健康疑慮請諮詢合格醫療專業人員；緊急狀況請聯絡當地緊急服務。

開源專案與技術文件：
https://github.com/jay19821026-dev/Watch2

## English (en-US)

### App name

`Xiaomi Health Bridge`

### Short description

`View and organize authorized health and fitness records on Android and Wear OS.`

### Full description

Xiaomi Health Bridge is an open-source Android and Wear OS health-data research project by Wang You-Jie. It helps users view, synchronize, and organize health and fitness records they explicitly authorize across a phone and a watch.

Key features:

• View dated records with source and status information through Health Connect  
• Organize heart rate, steps, distance, active calories, exercise, sleep, and other authorized metrics  
• Support paired data flows between an Android phone and a Wear OS watch  
• Distinguish no data, stale data, insufficient quality, unsupported features, and synchronization failures  
• Provide daily and weekly trends plus clearly labeled non-diagnostic research summaries  
• Let users revoke permissions and manage records through Android and Health Connect controls

Privacy and transparency:

This build contains no developer-operated health-data backend, advertising, or third-party analytics SDK. Health data is used only for user-authorized app functionality and is handled through Android, Health Connect, and Wear OS platform mechanisms. Source code, dataset-to-paper-to-algorithm traceability, limitations, and the privacy policy are publicly documented.

Important limitations:

This app is a research and personal-information organization tool. It is not a medical device and does not provide diagnosis, treatment, disease detection, emergency monitoring, or guaranteed clinical accuracy. Consult a qualified healthcare professional for health concerns and contact local emergency services in an emergency.

Open-source project and technical documentation:
https://github.com/jay19821026-dev/Watch2

## Release notes / 版本資訊

### zh-TW

`4.0.0 測試版：新增血氧 Health Connect 紀錄的量測方式篩選，並在手錶提供由使用者點擊的原廠功能入口。保留健康資料來源、同步狀態與研究限制；原廠 ECG 是否啟用仍由 Mi Fitness 與手錶服務決定。`

### en-US

`4.0.0 test release: Adds Health Connect SpO₂ recording-method filters and user-tapped OEM feature links on the watch. Health-data provenance, sync status, and research limits remain visible. Mi Fitness and the OEM watch service determine whether ECG is activated.`

## Console selections / 管理中心選項

- App or game / 類型：App
- Category / 類別：Health & Fitness / 健康與健身
- Ads / 廣告：No / 無
- App access / App 存取權：All functionality is available without a developer-operated account; device permissions and a compatible Wear OS device are optional feature prerequisites. / 不需開發者帳戶；部分功能需裝置權限與相容 Wear OS 裝置。
- Target audience / 目標族群：18 and over / 18 歲以上
- News app / 新聞 App：No / 否
- Government app / 政府 App：No / 否
- Financial features / 金融功能：None / 無
- Health declaration / 健康聲明：Activity and Fitness; Sleep Management / 活動與健身；睡眠管理
- Medical device / 醫療器材：No / 否

## Graphic assets / 圖像素材

- Feature graphic / 功能宣傳圖：`docs/images/play-feature-graphic-1024x500.png` (1024 × 500; no personal data)
- Phone screenshots / 手機截圖，建議順序：
  1. `docs/images/mobile-summary-demo.png`
  2. `docs/images/mobile-summary-detail-demo.png`
  3. `docs/images/mobile-health-grid-demo.png`
  4. `docs/images/mobile-summary-navigation-demo.png`
  5. `docs/images/mobile-summary-secondary-demo.png`
- Research-limitations screenshot / 研究限制畫面：`docs/images/ecg-research-limitations-demo.jpg`

All screenshots are demonstration screens supplied and confirmed by the owner as containing no personal data. Recheck status-bar identifiers and all visible values before the final upload.

## Final pre-submit checks / 最終送審前檢查

1. Confirm the signed AAB contains the same package, version, permissions, SDKs, privacy behavior, and visible features described here.
2. Verify title, short description, and full description character limits in each Play locale.
3. Upload only owner-authorized, release-signed bundles; never upload the GitHub Debug APKs or the current unsigned AABs.
4. Complete the Health apps declaration and every Health Connect permission justification using `docs/DATA_SAFETY_DECLARATION.md`.
5. Run Internal testing and the required closed test before applying for production access.

Closed-test invitations, test cases, privacy-safe feedback fields, daily evidence log, and production-access answer templates are in `docs/PLAY_CLOSED_TEST_PLAN.md`.
