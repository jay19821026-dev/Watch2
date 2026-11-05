# Google Play Data safety working declaration / 資料安全填寫草案

Review this against the exact signed AAB before submission; it is not an automatically submitted declaration.

- Health/fitness data is accessed only for user-authorized app functionality. 健康資料僅用於使用者授權的功能。
- Watch location may support exercise/location features; microphone access applies only to user-invoked features. 位置與麥克風依功能及權限使用。
- This source build has no developer health-data backend, ads, or third-party analytics. 此版本無開發者健康後端、廣告或第三方分析。
- Phone/watch transfer and Health Connect use platform security controls. 同步與 Health Connect 依平台安全機制處理。
- Users can revoke permissions and delete app/Health Connect data through Android controls. 使用者可撤權並刪除資料。

Before release, audit merged manifests and SDKs, justify every health permission in-app, verify the public privacy URL, inspect the signed AAB, and truthfully complete Health apps, Data safety, content-rating, target-audience, ads, app-access, and deletion declarations.
