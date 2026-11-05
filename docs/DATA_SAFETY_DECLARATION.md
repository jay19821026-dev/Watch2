# Google Play Data safety working declaration / 資料安全填寫草案

Review this against the exact signed AAB before submission; it is not an automatically submitted declaration.

- Health/fitness data is accessed only for user-authorized app functionality. 健康資料僅用於使用者授權的功能。
- Watch location may support exercise/location features; microphone access applies only to user-invoked features. 位置與麥克風依功能及權限使用。
- This source build has no developer health-data backend, ads, or third-party analytics. 此版本無開發者健康後端、廣告或第三方分析。
- Phone/watch transfer and Health Connect use platform security controls. 同步與 Health Connect 依平台安全機制處理。
- Users can revoke permissions and delete app/Health Connect data through Android controls. 使用者可撤權並刪除資料。

Before release, audit merged manifests and SDKs, justify every health permission in-app, verify the public privacy URL, inspect the signed AAB, and truthfully complete Health apps, Data safety, content-rating, target-audience, ads, app-access, and deletion declarations.

## Console answer matrix / 管理中心填寫矩陣

Use this matrix only if the signed AAB and runtime inspection still match the source audited for 3.13.44. If a later build adds a server, analytics, advertising, crash reporting, account login, or another SDK, reassess every answer before submission.

| Play Console question | 3.13.44 working answer | Evidence / boundary |
|---|---|---|
| Does the app collect or share required user-data categories with the developer or third parties? | **No**, for this source build | No developer backend, ads, analytics, or third-party telemetry SDK is present. Health Connect and paired-device transfers provide user-requested on-device functionality; re-check the final merged manifests and dependency graph. |
| Is all user data encrypted in transit? | **Yes**, where data crosses the phone/watch link | The app relies on Android/Wearable platform transport security; local Health Connect access is platform-mediated. Do not describe local-only processing as a developer cloud service. |
| Can users request deletion? | **Yes, through Android controls** | Users can revoke Health Connect permissions and delete app/Health Connect records through Android. The app has no developer account or server-side profile to delete. |
| Ads | **No** | No ad SDK or advertising feature is included. |
| Account creation / app access | **No account required** | Core screens do not require developer-operated login credentials. |
| Health and fitness data purpose | **App functionality** | Display, synchronize, summarize, and let the user manage authorized health/fitness records. Not advertising, credit, insurance, employment, diagnosis, or sale. |

Do not answer that health data is anonymous merely because the app has no account. Health metrics are sensitive user data even when processed locally. Do not claim that Google, Health Connect, the device manufacturer, or the Play transport is a developer-controlled recipient.

## Health apps declaration / 健康 App 聲明

Select **Health and fitness → Activity and Fitness** and **Health and fitness → Sleep Management**. Do not select Medical Device Apps, Clinical Decision Support, Diseases and Conditions Management, emergency monitoring, or diagnosis/treatment categories for this research build.

The manifest currently requests Health Connect access for heart rate, resting heart rate, HRV, oxygen saturation, respiratory rate, body temperature (read only), blood pressure, weight, steps, distance, active/total calories, exercise and route writing, sleep, elevation, floors, hydration, and extended history. In the Health Connect permission declaration, justify each requested data type with the matching visible feature; the Play declaration and final merged manifest must match exactly.

Suggested permission justification:

> Xiaomi Health Bridge reads user-authorized Health Connect records to display dated health and fitness history, source attribution, daily/weekly summaries, sleep and activity views, and clearly labeled non-diagnostic trend analysis. It writes only records that the user enters or explicitly synchronizes from the paired Wear OS experience, including supported activity, route, vital, sleep, body, and hydration records. Extended history access lets the user browse records older than the default Health Connect window. Data is not used for advertising, eligibility decisions, diagnosis, treatment, or emergency monitoring.

Official references: [Health apps declaration](https://support.google.com/googleplay/android-developer/answer/14738291), [Health and medical apps policy](https://support.google.com/googleplay/android-developer/answer/18265346), and [publishing a Health Connect app](https://developer.android.com/health-and-fitness/health-connect/publish).
