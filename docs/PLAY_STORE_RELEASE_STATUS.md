# Play Store release status / Play 商店發行狀態

Last verified / 最後驗證：2026-11-08
Last built bundle / 上次已建置 AAB：4.0.0（手機建置參數 `versionCode 161`、手錶 `162`；依 test 12 原始碼重建；候選 upload key 簽章，尚未上傳 Play）
Current source / 目前原始碼：4.0.0 (`versionCode 162`，雙端 Debug；尚未上傳 Play)
Package / 套件：`com.jaysato.xiaomihealthbridge`

The signed test 12 Play candidates predate the test 13 BioZ wear-state correction and test 14 guided-breathing shortcut and are superseded. Rebuild with distinct codes greater than the final Console history after account verification; do not upload these older candidate bundles. / 舊候選 AAB 未包含 test 13／14 的修正，不應上傳。

## Verified / 已驗證

- `:mobile:bundleRelease` and `:wear:bundleRelease` both build successfully with the configured Android toolchain. The latest candidate rebuild on 2026-11-08 passed both bundle tasks and release vital lint.
- A Play-versioning verification build passed on 2026-11-07 with `PLAY_MOBILE_VERSION_CODE=156` and `PLAY_WEAR_VERSION_CODE=157`. `aapt dump badging` confirmed these codes in the respective unsigned release APKs. The current 2026-11-08 AAB rebuild used distinct candidate parameters `161`/`162`; final code selection still requires Console history inspection. The AAB manifest version codes have not been independently decoded from the bundle here.
- Output files: `mobile/build/outputs/bundle/release/mobile-release.aab` and `wear/build/outputs/bundle/release/wear-release.aab`.
- The bilingual public privacy policy now resolves at `https://ailisa.net/privacy/xiaomi-health-bridge` (HTTP 200 verified 2026-11-07); the project page links to it. Use this direct URL in the Play listing.
- A new candidate upload keystore was generated **outside Git** on 2026-11-07. Its public certificate SHA-256 is `CA:BC:F3:70:D8:4A:2E:4D:BF:72:CE:B9:AF:2D:71:20:12:F4:04:05:89:86:34:95:3E:01:B7:FA:2C:FD:EE:A8`. Both AABs were rebuilt with this key; `jarsigner -verify -verbose -certs` reports `jar verified` and the `CN=Wang You-Jie, OU=Independent Developer, O=AILISA, C=TW` signer for both. This is not yet a registered Play upload certificate.
- SHA-256 of the **2026-11-08 test 12 source rebuild** signed AABs: mobile `664428CC855ABFA5B58A3CD45125B61D4EA19E6D28639198B4C4B703E3243C7A`; Wear `D3CCA572BE485A7F8274CBB0A510B7B960E398A6AB9EF556A130D1F3A0D46398`. Both bundle and release vital-lint tasks passed. `jarsigner -verify -verbose -certs` reported `jar verified` and the Wang You-Jie signer; `keytool -printcert -jarfile` returned the same candidate upload certificate SHA-256 on both. The candidate key is not yet registered in Play Console. Earlier test 9 AABs with codes 156/157 are superseded.
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

As of 2026-11-08, a fresh search of the connected Gmail account for recent Play Console/developer messages returned no results. This does **not** prove the Console review is still pending; the current Console UI has not been rechecked. Do not treat this email search as approval or rejection.

個人開發者帳戶已建立，但 Google 仍在審查身分文件。審查完成前無法驗證聯絡電話，也無法建立應用程式；因此目前不能進入商店資訊、健康 App 聲明、測試軌或 AAB 上傳頁面。這是 Google 帳戶驗證閘門，不是 Android 專案建置失敗。

## Required before Play Console upload / 上傳前必要條件

1. Wait for Google's identity-document approval email; then the owner completes the required contact-phone verification in Play Console.
2. Create the app only after the console enables that action.
3. **Candidate key generated:** securely back up the keystore and password recovery material outside this Windows profile before enrolling it in Play. The local DPAPI-encrypted password files are not a portable backup.
4. **Current candidate build complete:** `scripts/build-play-signed.ps1` rebuilt test 12 source with separate 161/162 version-code parameters without checking secrets into Git. Recheck the final Console version history and decode the AAB manifests before assigning permanent codes or uploading.
5. **Candidate signature verified:** compare the AAB signer fingerprint with the upload certificate shown in Play Console after enrollment; do not assume the candidate key is accepted until Console confirms it.
6. Decide and validate the Google Play multi-device packaging/release strategy for the phone and Wear OS artifacts; do not assume two independent artifacts with the same application ID can be uploaded without the current Play Console rules and configuration.
   Google's [Wear OS packaging guide](https://developer.android.com/training/wearables/packaging) requires a **unique versionCode across form factors** and directs phone + watch APK publishing through Multi-APK delivery. The test 14 sideload APKs both have `versionCode 162`, so they are **not** a valid final phone/Wear Play upload pair. Allocate distinct, monotonically safe Play codes after checking the Console's existing artifacts; rebuild and verify both signed outputs. The test release does not establish Play artifact readiness.
   The same official guide asks for the same package name and a watch `android.hardware.type.watch` feature; the repository currently declares these, but final merged manifests and Play device targeting still require inspection.
   Google's [Android developer verification guide](https://developer.android.com/developer-verification/assets/pdfs/updates-to-play-console-for-android-developer-verification.pdf) says a package name previously installed or distributed outside Play may require proof of ownership of its private signing key during new app creation. This package has public debug-signed test APKs; inspect Play Console's actual registration prompt before deciding the key flow. Do not upload a private key or assume that a debug certificate is the future Play app-signing key.
7. Complete the public privacy-policy URL, Data safety, Health apps declaration, app access, ads, content rating, target audience, testing track, store screenshots, and medical/non-medical claims review. The Data safety Yes/No answers remain unresolved until final AAB and phone/watch runtime transfer review; do not copy an old blanket "No data collected" answer.
8. Start with Internal testing and real phone/watch verification before any production rollout.

The signing key and passwords are credentials. They are deliberately excluded from source control and are not invented or embedded by this project.
