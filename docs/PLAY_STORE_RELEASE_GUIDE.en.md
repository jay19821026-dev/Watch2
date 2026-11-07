# Google Play Release Guide

Developer/author: Wang You-Jie (王宥傑) · Package: `com.jaysato.xiaomihealthbridge` · Current source: 4.0.0 (155)

The GitHub APKs are debug sideload builds, not Play Store production binaries. Google Play requires a release-signed Android App Bundle and Play App Signing.

Create the Health & Fitness app in Play Console; securely create/back up an upload key; keep keys and passwords out of GitHub; configure `docs/release-signing.md`; and build `./gradlew :mobile:bundleRelease :wear:bundleRelease`. Verify application ID, version code, signing, permissions, and the current phone/Wear packaging rules. Complete bilingual listings, privacy URL, Data safety, Health apps declaration, content rating, target audience, ads, and app access. Validate installation, pairing, permissions, sync, deletion, and empty/error states through Internal testing on real hardware before staged rollout.

The current 4.0 debug pair shares versionCode 155. Google's [Wear OS packaging instructions](https://developer.android.com/training/wearables/packaging) require unique version codes across phone and watch APKs and describe Multi-APK delivery for a phone + watch pair. Assign final codes after inspecting the target Play listing, then rebuild signed artifacts and verify the merged manifests and device targeting. Google's [developer verification documentation](https://developer.android.com/developer-verification/assets/pdfs/updates-to-play-console-for-android-developer-verification.pdf) also says a package name previously installed outside Play may require signing-key ownership proof in the Create app flow. Follow the actual Console prompt; never export or publish the private key.

Do not claim diagnosis, treatment, disease detection, emergency monitoring, clinical accuracy, or medical-device status.

## New personal-account timeline

The personal Play Console developer account exists, but the last direct Console check found identity-document review pending and **Create app** disabled. No approval email was found in the connected Gmail search on 2026-11-07; that search alone cannot prove the Console state. Recheck the Console after Google notifies the owner. A new personal developer account created after November 13, 2023 must run a closed test with at least 12 continuously opted-in testers for 14 days before it can apply for production access. Internal testing can begin earlier but does not replace this gate. See [Google Play's personal-account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465) and [current project release status](PLAY_STORE_RELEASE_STATUS.md).

Ready-to-use bilingual invitations, test cases, privacy-safe feedback fields, the daily evidence log, and production-access answer templates are in `docs/PLAY_CLOSED_TEST_PLAN.md`.

## Health apps declaration recommendation

- Select **Health and fitness → Activity and Fitness** and **Health and fitness → Sleep Management**.
- Do not select medical-device, clinical-decision-support, disease-management, emergency-monitoring, diagnosis, or treatment categories.
- Justify every Health Connect permission using a visible in-app feature and ensure the Play declaration matches the signed AAB merged manifest.
- Public privacy-policy URL: `https://ailisa.net/portfolio/xiaomi-health-bridge#privacy`.
- Use `docs/DATA_SAFETY_DECLARATION.md` for the detailed answer matrix and permission-purpose text.
