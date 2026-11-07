# Play Store release status / Play 商店發行狀態

Last verified / 最後驗證：2026-11-08
Last built bundle / 上次已建置 AAB：4.0.0 test 17 原始碼（手機 `versionCode 170`、手錶 `171`；候選 upload key 簽章，尚未上傳 Play）
Current source / 目前原始碼：4.0.0 test 17（雙端 Debug `versionCode 169`；尚未上傳 Play）。test 17 依反組譯來源修正血壓標示及舊光學估值顯示。已從本版重建簽署候選 AAB；最終版本碼仍須對照 Console 歷史紀錄。
Package / 套件：`com.jaysato.xiaomihealthbridge`

The test 17 signed AABs are current local build candidates, not Play uploads or approved releases. Final codes, upload-certificate enrollment and Play Console health declarations still require Console inspection. / test 17 AAB 為目前原始碼的本機建置候選，並未上傳或通過 Play 審核。

## Verified / 已驗證

- `:mobile:bundleRelease` and `:wear:bundleRelease` both build successfully with the configured Android toolchain. The latest candidate rebuild on 2026-11-08 passed both bundle tasks and release vital lint.
- A Play-versioning verification build passed on 2026-11-07 with `PLAY_MOBILE_VERSION_CODE=156` and `PLAY_WEAR_VERSION_CODE=157`. The 2026-11-08 test 17 AAB rebuild used distinct candidate parameters `170`/`171`. Google bundletool 1.17.2 validated both packed AABs and independently dumped their embedded manifests: same package `com.jaysato.xiaomihealthbridge`, `versionName 4.0.0`, mobile `versionCode 170`, Wear `versionCode 171`; only the Wear bundle declares `android.hardware.type.watch`. Neither packed manifest contains `REQUEST_INSTALL_PACKAGES`. Final code selection still requires Console history inspection.
- Current signed candidate copies: ignored `dist/play-candidates/test17/XiaomiHealthBridge-Mobile-4.0.0-vc170-play-candidate.aab` and `dist/play-candidates/test17/XiaomiHealthBridge-Wear-4.0.0-vc171-play-candidate.aab`, with `SHA256SUMS-4.0.0-play-candidate-test17.txt`. Build outputs remain at `mobile/build/outputs/bundle/release/mobile-release.aab` and `wear/build/outputs/bundle/release/wear-release.aab`.
- The bilingual public privacy policy now resolves at `https://ailisa.net/privacy/xiaomi-health-bridge` (HTTP 200 verified 2026-11-07); the project page links to it. Use this direct URL in the Play listing.
- A new candidate upload keystore was generated **outside Git** on 2026-11-07. Its public certificate SHA-256 is `CA:BC:F3:70:D8:4A:2E:4D:BF:72:CE:B9:AF:2D:71:20:12:F4:04:05:89:86:34:95:3E:01:B7:FA:2C:FD:EE:A8`. Both AABs were rebuilt with this key; `jarsigner -verify -verbose -certs` reports `jar verified` and the `CN=Wang You-Jie, OU=Independent Developer, O=AILISA, C=TW` signer for both. This is not yet a registered Play upload certificate.
- SHA-256 of the **current test 17 source** signed AABs: mobile `F8E70CBFE56C8104C0A8A0FCB9DB8947EAFFFE60776A3CAC4EFE7DF9CF988095`; Wear `5A2DB705B1AB61649E2964E2B2124E498850B5F25927A284C0BE5AE1312D0E38`. Both bundle and release vital-lint tasks passed; `jarsigner -verify` returned `jar verified` and `keytool -printcert -jarfile` returned the same Wang You-Jie candidate upload certificate SHA-256 on both. The candidate key is not yet registered in Play Console. Earlier test 15, 12 and 9 AABs are superseded.
- The public sideload test APKs use the separate Android Debug certificate SHA-256 `0C:35:A3:70:03:2B:FE:70:6E:F5:4E:C6:A7:89:BC:DC:7E:53:D1:9C:94:B4:44:FA:6B:67:02:7A:3C:B8:75:0F`. `apksigner verify --print-certs` confirmed it on both released APKs. Preserve this distinction if Console asks for proof that the package name was previously installed outside Play.
- The 4.0.0 release variant now excludes the manual APK-install permission and receiver, and hides the phone/watch APK-transfer controls. Debug builds retain the manual test flow. Both variants compiled on 2026-11-07; inspect final signed manifests again before upload.
- These are signed **candidate** AABs. The owner must back up the keystore and password recovery material, confirm the package-name ownership path and upload certificate in Play Console, complete declarations and testing, and verify device delivery before production publication.
- The 512 × 512 app icon is available at `docs/images/play-app-icon-512.png`. An opaque 1024 × 500 24-bit PNG feature graphic is available at `docs/images/play-feature-graphic-1024x500-opaque.png`. The supplied phone screenshots are 1280 × 2772 with an aspect ratio above Play's 2:1 limit; they are website demos, not verified Play uploads. A current-version 1:1 Wear screenshot (at least 384 × 384) is also missing. See [listing asset preflight](PLAY_STORE_LISTING_COPY.md#graphic-assets--圖像素材) and [official preview-asset rules](https://support.google.com/googleplay/android-developer/answer/9866151). The watch was connected and charging at 8% at the latest check, so test 17 has not yet supplied a live Wear screenshot. The phone debug test 17 APK installed in place and reports versionCode 169; its main screen opened, but the visible health values are private and its raw screenshot remains ignored locally, not a Play asset.

手機與手錶 Release AAB 均已使用同一把候選 upload key 簽章；目前尚未完成 Play Console 註冊、聲明及實機測試，也尚未上傳。

## Play Console account gate / Play Console 帳戶閘門

- The personal developer account **Jay Yo Wang** now exists and is reachable in Play Console.
- Google reports that the uploaded identity documents are still under review. The account owner will receive an email when review finishes; Google states this can take several days.
- Contact-phone verification is blocked until the identity review and other required verification steps are approved.
- The **Create app** action is disabled with the visible reason that account verification must be completed first.
- No application has been created, no AAB has been uploaded, and no Health apps declaration has been submitted.

As of 2026-11-08, another search of the connected Gmail account for recent Play Console/developer and identity-verification messages returned no results. This does **not** prove the Console review is still pending; the current Console UI has not been rechecked. Do not treat this email search as approval or rejection.

個人開發者帳戶已建立，但 Google 仍在審查身分文件。審查完成前無法驗證聯絡電話，也無法建立應用程式；因此目前不能進入商店資訊、健康 App 聲明、測試軌或 AAB 上傳頁面。這是 Google 帳戶驗證閘門，不是 Android 專案建置失敗。

## Required before Play Console upload / 上傳前必要條件

1. Wait for Google's identity-document approval email; then the owner completes the required contact-phone verification in Play Console.
2. Create the app only after the console enables that action.
3. **Candidate key generated:** securely back up the keystore and password recovery material outside this Windows profile before enrolling it in Play. The local DPAPI-encrypted password files are not a portable backup.
4. **Current candidate build complete:** `scripts/build-play-signed.ps1` rebuilt test 17 source with separate 170/171 version-code parameters without checking secrets into Git. Google bundletool validated both AABs and decoded the packed manifests. Recheck the final Console version history before assigning permanent codes or uploading.
5. **Candidate signature verified:** compare the AAB signer fingerprint with the upload certificate shown in Play Console after enrollment; do not assume the candidate key is accepted until Console confirms it.
6. Decide and validate the Google Play multi-device packaging/release strategy for the phone and Wear OS artifacts; do not assume two independent artifacts with the same application ID can be uploaded without the current Play Console rules and configuration.
   Google's current [dedicated form-factor track instructions](https://support.google.com/googleplay/android-developer/answer/13295490) place Wear OS releases on a separate Wear OS track and say a **first** Wear OS experience requires a Wear OS-enabled app bundle. The same instructions then describe uploading a Wear OS AAB or APK to a test track. The [Android Wear packaging guide](https://developer.android.com/training/wearables/packaging) describes separate phone/watch packages, unique cross-form-factor version codes and Multi-APK delivery. These pages leave the exact first-release upload path for this two-module project to be resolved against the live Console UI; the signed 170/171 AABs are build/validation candidates, **not** evidence that Play will accept this pair. / 官方文件同時說明獨立 Wear 測試軌與初次上架的 Wear-enabled AAB 要求；必須以實際 Console 畫面確定本專案的提交形式。
   The test 17 sideload APKs both have `versionCode 169`, so they are **not** a valid final phone/Wear Play upload pair. The signed candidate AABs use distinct 170/171 codes; allocate final monotonically safe codes only after checking Console's existing artifacts. The public test release does not establish Play artifact readiness.
   The same official guide asks for the same package name and a watch `android.hardware.type.watch` feature; the repository currently declares these, but final merged manifests and Play device targeting still require inspection.
   Google's [Android developer verification guide](https://developer.android.com/developer-verification/assets/pdfs/updates-to-play-console-for-android-developer-verification.pdf) says a package name previously installed or distributed outside Play may require proof of ownership of its private signing key during new app creation. This package has public debug-signed test APKs; inspect Play Console's actual registration prompt before deciding the key flow. Do not upload a private key or assume that a debug certificate is the future Play app-signing key.
7. Complete the public privacy-policy URL, Data safety, Health apps declaration, app access, ads, content rating, target audience, testing track, store screenshots, and medical/non-medical claims review. The Data safety Yes/No answers remain unresolved until final AAB and phone/watch runtime transfer review; do not copy an old blanket "No data collected" answer.
8. Start with Internal testing and real phone/watch verification before any production rollout.

The signing key and passwords are credentials. They are deliberately excluded from source control and are not invented or embedded by this project.
