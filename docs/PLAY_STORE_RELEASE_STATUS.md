# Play Store release status / Play 商店發行狀態

Last verified / 最後驗證：2026-11-06  
Version / 版本：3.13.44 (`versionCode 154`)  
Package / 套件：`com.jaysato.xiaomihealthbridge`

## Verified / 已驗證

- `:mobile:bundleRelease` and `:wear:bundleRelease` both build successfully with JDK 17 / Android SDK 36.
- Output files: `mobile/build/outputs/bundle/release/mobile-release.aab` and `wear/build/outputs/bundle/release/wear-release.aab`.
- `jarsigner -verify -verbose -certs` reports **`jar is unsigned`** for both current outputs because no authorized release keystore is configured.
- Therefore these AABs are build-verification artifacts only and must not be uploaded as a production release.

手機與手錶 Release AAB 均可成功建置，但目前兩者皆未簽章；只能證明建置流程可用，不能上傳為正式商店版本。

## Required before Play Console upload / 上傳前必要條件

1. Create or obtain the owner's upload keystore and securely retain its password/recovery material.
2. Configure `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` outside Git.
3. Rebuild both bundles and require `jarsigner` to report a verified signature with the intended upload certificate.
4. Decide and validate the Google Play multi-device packaging/release strategy for the phone and Wear OS artifacts; do not assume two independent artifacts with the same application ID can be uploaded without the current Play Console rules and configuration.
5. Complete the public privacy-policy URL, Data safety, Health apps declaration, app access, ads, content rating, target audience, testing track, store screenshots, and medical/non-medical claims review.
6. Start with Internal testing and real phone/watch verification before any production rollout.

The signing key and passwords are credentials. They are deliberately excluded from source control and are not invented or embedded by this project.
