# Google Play Data safety working declaration / 資料安全填寫草案

Review this against the exact signed AAB before submission; it is not an automatically submitted declaration.

- Health/fitness data is accessed only for user-authorized app functionality. 健康資料僅用於使用者授權的功能。
- Watch location may support exercise/location features; microphone access applies only to user-invoked features. 位置與麥克風依功能及權限使用。
- This source build has no developer health-data backend, ads, or third-party analytics. 此版本無開發者健康後端、廣告或第三方分析。
- Phone/watch transfer and Health Connect use platform security controls. 同步與 Health Connect 依平台安全機制處理。
- Users can revoke permissions and delete app/Health Connect data through Android controls. 使用者可撤權並刪除資料。

Before release, audit merged manifests and SDKs, justify every health permission in-app, verify the public privacy URL, inspect the signed AAB, and truthfully complete Health apps, Data safety, content-rating, target-audience, ads, app-access, and deletion declarations.

## Console answer matrix / 管理中心填寫矩陣

This is a 4.0.0 source review, not a completed Play Console form. Reassess every answer against both final signed AABs and a phone/watch runtime trace. A developer backend is not the sole criterion for collection: Play defines collection by transmission off the device, with specific exceptions. In particular, classify Wearable Data Layer transfers, Health Connect writes, user-selected exports, and any SDK traffic under the [official Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469) before entering a Yes/No answer.

| Play Console question | 4.0.0 working answer | Evidence / boundary |
|---|---|---|
| Does the app collect or share required user-data categories? | **Unresolved; do not submit No yet** | Source has no developer backend, ads, or analytics, but Wearable Data Layer sends health and research records between devices and Health Connect writes records to another app. Apply Play's definitions and exceptions to the actual final distribution and runtime behavior. |
| Is all collected user data encrypted in transit? | **Unresolved pending traffic and transport audit** | The implementation uses Google Play services Wearable Data Layer, but this source review has not proved the encryption properties of every transfer or SDK. Answer only after classifying what is collected. |
| Can users request deletion? | **Android controls are available; verify cross-device deletion** | Revoking permissions and clearing app/Health Connect data are available. Confirm whether watch data, phone data, queues, and exported copies are deleted as claimed before selecting a Console option. |
| Ads | **No** | No ad SDK or advertising feature is included. |
| Account creation / app access | **No account required** | Core screens do not require developer-operated login credentials. |
| Health and fitness data purpose | **App functionality** | Display, synchronize, summarize, and let the user manage authorized health/fitness records. Not advertising, credit, insurance, employment, diagnosis, or sale. |

The Wear app requests `RECORD_AUDIO` for a foreground noise meter. It reads PCM samples, derives dBFS and an optional user-calibrated dBA estimate, and sends the derived result to the paired phone through Wearable Data Layer. The reviewed code does not persist or transmit raw audio. The final Data safety review must classify the derived noise record and verify this behavior in the exact build. The Wear debug APK also requests `REQUEST_INSTALL_PACKAGES` for manual APK transfer. The release variant removes that permission and its receiver; release phone/watch UI hides the transfer/install actions. Verify both merged manifests before Play submission.

Do not answer that health data is anonymous merely because the app has no account. Health metrics are sensitive user data even when processed locally. Do not claim that Google, Health Connect, the device manufacturer, or the Play transport is a developer-controlled recipient.

## Health apps declaration / 健康 App 聲明

Select **Health and fitness → Activity and Fitness**, **Sleep Management**, and **Stress Management, Relaxation, Mental Acuity**. The app displays a labeled research stress score and brief rest/retest guidance, so omitting the stress category would understate its visible features. Do not select Medical Device Apps merely because the app reads health data; this research build has no demonstrated device authorization or validated diagnostic/treatment function. Review the actual Play form and current app screens before submitting any medical, disease-management or clinical-decision category; the app must not claim emergency monitoring or diagnosis.

請勾選「健康與健身 → 活動與健身、睡眠管理、壓力管理／放鬆／心智能力」。本 App 顯示研究壓力分數並提供休息後重測文字，因此壓力類別須如實揭露。醫療器材或臨床決策分類須依實際功能與法規狀態審查，不能因蒐集健康資料就自行宣稱核准。

The manifest currently requests Health Connect access for heart rate, resting heart rate, HRV, oxygen saturation, respiratory rate, body temperature (read only), blood pressure, weight, steps (also used to read step cadence), distance, speed (read only for the source-audit view), active/total calories, exercise and route writing, sleep, elevation, floors, hydration, and extended history. In the Health Connect permission declaration, justify each requested data type with the matching visible feature; the Play declaration and final merged manifest must match exactly.

Suggested permission justification:

> AILISA Health Bridge reads user-authorized Health Connect records to display dated health and fitness history, source attribution, daily/weekly summaries, sleep and activity views, and clearly labeled non-diagnostic trend analysis. It writes only records that the user enters or explicitly synchronizes from the paired Wear OS experience, including supported activity, route, vital, sleep, body, and hydration records. Extended history access lets the user browse records older than the default Health Connect window. Data is not used for advertising, eligibility decisions, diagnosis, treatment, or emergency monitoring.

Official references: [Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291), [Health and medical apps policy](https://support.google.com/googleplay/android-developer/answer/18265346), and [publishing a Health Connect app](https://developer.android.com/health-and-fitness/health-connect/publish).
