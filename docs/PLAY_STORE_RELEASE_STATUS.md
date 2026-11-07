# Play Store release status / Play 商店發行狀態

Last verified / 最後驗證：2026-11-08
Last built bundle / 上次已建置 AAB：4.0.0 test 16 原始碼（手機 `versionCode 167`、手錶 `168`；候選 upload key 簽章，尚未上傳 Play）
Current source / 目前原始碼：4.0.0 test 16（雙端 Debug `versionCode 166`；尚未上傳 Play）。test 16 加入原廠壓力服務的來源核對與手錶 HRV 頁入口。Play 候選已從 test 16 原始碼重建；最終版本碼仍須對照 Console 歷史紀錄。
Package / 套件：`com.jaysato.xiaomihealthbridge`

The signed test 12 and test 15 Play candidates both predate current test 16 source. The test 16 signed AABs are current **local candidates**, not Play uploads or approved releases. Final codes, upload-certificate enrollment and Play Console health declarations still require Console inspection. / test 12 與 test 15 候選 AAB 都早於目前原始碼；test 16 已簽署 AAB 仍只是本機候選，並未上傳或通過 Play 審核。

## Verified / 已驗證

- `:mobile:bundleRelease` and `:wear:bundleRelease` both build successfully with the configured Android toolchain. The latest candidate rebuild on 2026-11-08 passed both bundle tasks and release vital lint.
- A Play-versioning verification build passed on 2026-11-07 with `PLAY_MOBILE_VERSION_CODE=156` and `PLAY_WEAR_VERSION_CODE=157`. The 2026-11-08 test 16 AAB rebuild used distinct candidate parameters `167`/`168`. Google bundletool 1.17.2 validated both packed AABs and independently dumped their embedded manifests: same package `com.jaysato.xiaomihealthbridge`, `versionName 4.0.0`, mobile `versionCode 167`, Wear `versionCode 168`; only the Wear bundle declares `android.hardware.type.watch`. Neither packed manifest contains `REQUEST_INSTALL_PACKAGES`. Final code selection still requires Console history inspection.
- Current signed candidate copies: ignored `dist/play-candidates/test16/XiaomiHealthBridge-Mobile-4.0.0-vc167-play-candidate.aab` and `dist/play-candidates/test16/XiaomiHealthBridge-Wear-4.0.0-vc168-play-candidate.aab`, with `SHA256SUMS-4.0.0-play-candidate-test16.txt`. Build outputs remain at `mobile/build/outputs/bundle/release/mobile-release.aab` and `wear/build/outputs/bundle/release/wear-release.aab`.
- The bilingual public privacy policy now resolves at `https://ailisa.net/privacy/xiaomi-health-bridge` (HTTP 200 verified 2026-11-07); the project page links to it. Use this direct URL in the Play listing.
- A new candidate upload keystore was generated **outside Git** on 2026-11-07. Its public certificate SHA-256 is `CA:BC:F3:70:D8:4A:2E:4D:BF:72:CE:B9:AF:2D:71:20:12:F4:04:05:89:86:34:95:3E:01:B7:FA:2C:FD:EE:A8`. Both AABs were rebuilt with this key; `jarsigner -verify -verbose -certs` reports `jar verified` and the `CN=Wang You-Jie, OU=Independent Developer, O=AILISA, C=TW` signer for both. This is not yet a registered Play upload certificate.
- SHA-256 of the **current test 16 source** signed AABs: mobile `E3C5BD553C2528785627AB7AD8C516EFB989CE55E34326AD93E19834F66CD0DA`; Wear `A1D9B9DAE394C59128645D652800A3C021F910A35305AD1C6C44B91B8447DBE5`. Both bundle and release vital-lint tasks passed; `jarsigner -verify` returned `jar verified` and `keytool -printcert -jarfile` returned the same Wang You-Jie candidate upload certificate SHA-256 on both. The candidate key is not yet registered in Play Console. Earlier test 15, 12 and 9 AABs are superseded.
- The public sideload test APKs use the separate Android Debug certificate SHA-256 `0C:35:A3:70:03:2B:FE:70:6E:F5:4E:C6:A7:89:BC:DC:7E:53:D1:9C:94:B4:44:FA:6B:67:02:7A:3C:B8:75:0F`. `apksigner verify --print-certs` confirmed it on both released APKs. Preserve this distinction if Console asks for proof that the package name was previously installed outside Play.
- The 4.0.0 release variant now excludes the manual APK-install permission and receiver, and hides the phone/watch APK-transfer controls. Debug builds retain the manual test flow. Both variants compiled on 2026-11-07; inspect final signed manifests again before upload.
- These are signed **candidate** AABs. The owner must back up the keystore and password recovery material, confirm the package-name ownership path and upload certificate in Play Console, complete declarations and testing, and verify device delivery before production publication.

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
4. **Current candidate build complete:** `scripts/build-play-signed.ps1` rebuilt test 16 source with separate 167/168 version-code parameters without checking secrets into Git. Google bundletool validated both AABs and decoded the packed manifests. Recheck the final Console version history before assigning permanent codes or uploading.
5. **Candidate signature verified:** compare the AAB signer fingerprint with the upload certificate shown in Play Console after enrollment; do not assume the candidate key is accepted until Console confirms it.
6. Decide and validate the Google Play multi-device packaging/release strategy for the phone and Wear OS artifacts; do not assume two independent artifacts with the same application ID can be uploaded without the current Play Console rules and configuration.
   Google's current [dedicated form-factor track instructions](https://support.google.com/googleplay/android-developer/answer/13295490) place Wear OS releases on a separate Wear OS track and say a **first** Wear OS experience requires a Wear OS-enabled app bundle. The same instructions then describe uploading a Wear OS AAB or APK to a test track. The [Android Wear packaging guide](https://developer.android.com/training/wearables/packaging) describes separate phone/watch packages, unique cross-form-factor version codes and Multi-APK delivery. These pages leave the exact first-release upload path for this two-module project to be resolved against the live Console UI; the signed 167/168 AABs are build/validation candidates, **not** evidence that Play will accept this pair. / 官方文件同時說明獨立 Wear 測試軌與初次上架的 Wear-enabled AAB 要求；必須以實際 Console 畫面確定本專案的提交形式。
   The test 16 sideload APKs both have `versionCode 166`, so they are **not** a valid final phone/Wear Play upload pair. The signed candidate AABs use distinct 167/168 codes; allocate final monotonically safe codes only after checking Console's existing artifacts. The public test release does not establish Play artifact readiness.
   The same official guide asks for the same package name and a watch `android.hardware.type.watch` feature; the repository currently declares these, but final merged manifests and Play device targeting still require inspection.
   Google's [Android developer verification guide](https://developer.android.com/developer-verification/assets/pdfs/updates-to-play-console-for-android-developer-verification.pdf) says a package name previously installed or distributed outside Play may require proof of ownership of its private signing key during new app creation. This package has public debug-signed test APKs; inspect Play Console's actual registration prompt before deciding the key flow. Do not upload a private key or assume that a debug certificate is the future Play app-signing key.
7. Complete the public privacy-policy URL, Data safety, Health apps declaration, app access, ads, content rating, target audience, testing track, store screenshots, and medical/non-medical claims review. The Data safety Yes/No answers remain unresolved until final AAB and phone/watch runtime transfer review; do not copy an old blanket "No data collected" answer.
8. Start with Internal testing and real phone/watch verification before any production rollout.

The signing key and passwords are credentials. They are deliberately excluded from source control and are not invented or embedded by this project.
