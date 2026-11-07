# Google Play Release Guide

Developer/author: Wang You-Jie (王宥傑) · Package: `com.jaysato.xiaomihealthbridge` · Current source: 4.0.0 test 17 (Debug code 169 on both devices)

The GitHub APKs are debug sideload builds, not Play Store production binaries. Google Play requires a release-signed Android App Bundle and Play App Signing.

After Play Console enables app creation, create the Health & Fitness app. A local candidate upload key exists outside Git; back up the key and portable password recovery material securely before enrollment. Test 17 source has produced signed local candidate AABs with mobile/Wear codes 170/171. Verify application ID, final version codes, upload-certificate enrollment, permissions, and the live phone/Wear packaging path in Console. Complete bilingual listings, privacy URL, Data safety, Health apps declaration, content rating, target audience, ads, and app access. Validate installation, pairing, permissions, sync, deletion, and empty/error states through Internal testing on real hardware before staged rollout.

The test 17 Debug pair shares versionCode 169 and is for sideload testing only. Google's [Wear OS packaging instructions](https://developer.android.com/training/wearables/packaging) require unique version codes across phone and watch artifacts and describe Multi-APK delivery for a phone + watch pair. The signed candidate AABs use distinct 170/171 codes, but the exact first-release path and final version history still require Console inspection. Google's [developer verification documentation](https://developer.android.com/developer-verification/assets/pdfs/updates-to-play-console-for-android-developer-verification.pdf) says a package name previously installed outside Play may require signing-key ownership proof in the Create app flow. Follow the actual Console prompt; never export or publish the private key.

Do not claim diagnosis, treatment, disease detection, emergency monitoring, clinical accuracy, or medical-device status.

## New personal-account timeline

The personal Play Console developer account exists, but the last direct Console check found identity-document review pending and **Create app** disabled. No approval email was found in the connected Gmail search on 2026-11-07; that search alone cannot prove the Console state. Recheck the Console after Google notifies the owner. A new personal developer account created after November 13, 2023 must run a closed test with at least 12 continuously opted-in testers for 14 days before it can apply for production access. Internal testing can begin earlier but does not replace this gate. See [Google Play's personal-account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465) and [current project release status](PLAY_STORE_RELEASE_STATUS.md).

Ready-to-use bilingual invitations, test cases, privacy-safe feedback fields, the daily evidence log, and production-access answer templates are in `docs/PLAY_CLOSED_TEST_PLAN.md`.

## Health apps declaration recommendation

- Select **Health and fitness → Activity and Fitness**, **Sleep Management**, and **Stress Management, Relaxation, Mental Acuity** for the currently visible features, then verify the final AAB and actual form before submission. See [Google's category definitions](https://support.google.com/googleplay/android-developer/answer/14738291).
- No medical-device authorization or diagnostic/treatment/emergency claim is established. Review whether the visible manual BP record and related guidance trigger any other medical category in the actual Console form; do not prefill an unsupported No answer.
- Justify every Health Connect permission using a visible in-app feature and ensure the Play declaration matches the signed AAB merged manifest.
- Public privacy-policy URL: `https://ailisa.net/privacy/xiaomi-health-bridge`.
- Use `docs/DATA_SAFETY_DECLARATION.md` for the detailed answer matrix and permission-purpose text.
