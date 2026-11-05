# Google Play Release Guide

Developer/author: Wang You-Jie (王宥傑) · Package: `com.jaysato.xiaomihealthbridge` · Version: 3.13.44 (154)

The GitHub APKs are debug sideload builds, not Play Store production binaries. Google Play requires a release-signed Android App Bundle and Play App Signing.

Create the Health & Fitness app in Play Console; securely create/back up an upload key; keep keys and passwords out of GitHub; configure `docs/release-signing.md`; and build `./gradlew :mobile:bundleRelease :wear:bundleRelease`. Verify application ID, version code, signing, permissions, and the current phone/Wear packaging rules. Complete bilingual listings, privacy URL, Data safety, Health apps declaration, content rating, target audience, ads, and app access. Validate installation, pairing, permissions, sync, deletion, and empty/error states through Internal testing on real hardware before staged rollout.

Do not claim diagnosis, treatment, disease detection, emergency monitoring, clinical accuracy, or medical-device status.
