# Play Store release status / Play 商店發行狀態

Last verified / 最後驗證：2026-11-07
Last built bundle / 上次已建置 AAB：4.0.0（手機 `versionCode 156`、手錶 `157`；未簽章的 Play 配置驗證產物）
Current source / 目前原始碼：4.0.0 (`versionCode 155`；尚未上傳 Play)
Package / 套件：`com.jaysato.xiaomihealthbridge`

## Verified / 已驗證

- `:mobile:bundleRelease` and `:wear:bundleRelease` both build successfully with JDK 17 / Android SDK 36.
- A Play-versioning verification build also passed on 2026-11-07 with `PLAY_MOBILE_VERSION_CODE=156` and `PLAY_WEAR_VERSION_CODE=157`. `aapt dump badging` confirmed these codes in the respective unsigned release APKs. These are example codes pending Console history inspection, not uploaded artifacts.
- Output files: `mobile/build/outputs/bundle/release/mobile-release.aab` and `wear/build/outputs/bundle/release/wear-release.aab`.
- `jarsigner -verify -verbose -certs` reports **`jar is unsigned`** for both current outputs because no authorized release keystore is configured.
- The 4.0.0 release variant now excludes the manual APK-install permission and receiver, and hides the phone/watch APK-transfer controls. Debug builds retain the manual test flow. Both variants compiled on 2026-11-07; inspect final signed manifests again before upload.
- Therefore these AABs are build-verification artifacts only and must not be uploaded as a production release.

手機與手錶 Release AAB 均可成功建置，但目前兩者皆未簽章；只能證明建置流程可用，不能上傳為正式商店版本。

## Play Console account gate / Play Console 帳戶閘門

- The personal developer account **Jay Yo Wang** now exists and is reachable in Play Console.
- Google reports that the uploaded identity documents are still under review. The account owner will receive an email when review finishes; Google states this can take several days.
- Contact-phone verification is blocked until the identity review and other required verification steps are approved.
- The **Create app** action is disabled with the visible reason that account verification must be completed first.
- No application has been created, no AAB has been uploaded, and no Health apps declaration has been submitted.

個人開發者帳戶已建立，但 Google 仍在審查身分文件。審查完成前無法驗證聯絡電話，也無法建立應用程式；因此目前不能進入商店資訊、健康 App 聲明、測試軌或 AAB 上傳頁面。這是 Google 帳戶驗證閘門，不是 Android 專案建置失敗。

## Required before Play Console upload / 上傳前必要條件

1. Wait for Google's identity-document approval email; then the owner completes the required contact-phone verification in Play Console.
2. Create the app only after the console enables that action.
3. Create or obtain the owner's upload keystore and securely retain its password/recovery material.
4. Configure `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` outside Git.
5. Rebuild both bundles and require `jarsigner` to report a verified signature with the intended upload certificate.
6. Decide and validate the Google Play multi-device packaging/release strategy for the phone and Wear OS artifacts; do not assume two independent artifacts with the same application ID can be uploaded without the current Play Console rules and configuration.
   Google's [Wear OS packaging guide](https://developer.android.com/training/wearables/packaging) requires a **unique versionCode across form factors** and directs phone + watch APK publishing through Multi-APK delivery. The 4.0 sideload test APKs both have `versionCode 155`, so they are **not** a valid final phone/Wear Play upload pair. Allocate distinct, monotonically safe Play codes after checking the Console's existing artifacts; rebuild and verify both signed outputs. The test release does not establish Play artifact readiness.
   The same official guide asks for the same package name and a watch `android.hardware.type.watch` feature; the repository currently declares these, but final merged manifests and Play device targeting still require inspection.
   Google's [Android developer verification guide](https://developer.android.com/developer-verification/assets/pdfs/updates-to-play-console-for-android-developer-verification.pdf) says a package name previously installed or distributed outside Play may require proof of ownership of its private signing key during new app creation. This package has public debug-signed test APKs; inspect Play Console's actual registration prompt before deciding the key flow. Do not upload a private key or assume that a debug certificate is the future Play app-signing key.
7. Complete the public privacy-policy URL, Data safety, Health apps declaration, app access, ads, content rating, target audience, testing track, store screenshots, and medical/non-medical claims review. The Data safety Yes/No answers remain unresolved until final AAB and phone/watch runtime transfer review; do not copy an old blanket "No data collected" answer.
8. Start with Internal testing and real phone/watch verification before any production rollout.

The signing key and passwords are credentials. They are deliberately excluded from source control and are not invented or embedded by this project.
