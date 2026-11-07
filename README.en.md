# Xiaomi Health Bridge

[繁體中文](README.md) | [English](README.en.md)

Xiaomi Health Bridge is an open-source Android phone + Wear OS research project by **王宥傑 (Wang You-Jie)**. It builds an auditable health-data path between Xiaomi Watch 2 Pro and Android: acquire data on the watch, retain provenance and quality evidence, synchronize through the Wearable Data Layer, persist on the phone, and write supported records to Health Connect.

![Privacy-safe Xiaomi Health Bridge phone and watch research mockup](docs/images/health-bridge-hero.png)

> The hero is a privacy-safe illustrative mockup. Actual features and support depend on the source, device capabilities, and `SUPPORT_MATRIX.md`.

Current test version: `4.0.0` (versionCode `162`). The source tree contains:

- `mobile/` — Android phone app
- `wear/` — Wear OS watch app
- `health-engine/` — shared classification, quality, and research-analysis core
- `research/clinical-data/` — reproducible offline public-dataset benchmarks; datasets are not redistributed

![Phone summary screen demonstration](docs/images/mobile-summary-demo.png)

All screenshots are demo screens containing no personal data. More examples: [summary navigation](docs/images/mobile-summary-navigation-demo.png), [health metrics](docs/images/mobile-health-grid-demo.png), and [ECG research limitations](docs/images/ecg-research-limitations-demo.jpg).

## What it does

- Uses Wear OS Health Services for supported heart-rate and activity data.
- Synchronizes watch and phone through Google Play services Wearable Data Layer.
- Reads and writes supported Health Connect records on the phone.
- Separates missing, stale, poor-quality, unsupported, and sync-failed states.
- Preserves research evidence for ECG, PPG, BioZ, raw SpO₂, temperature, and motion channels.
- Unlocks an interpreted result only when its units, timing, mapping, quality, and calibration requirements pass.
- Includes offline ECG/PPG method benchmarks against public datasets.
- Version 4.0 adds SpO₂ recording-method filtering and user-tapped links to exported OEM watch screens; the OEM screens remain the authority for activation state.
- Test 14 adds a watch-home link to the OEM guided breathing exercise after tracing its 1–5-minute options and optional start/end heart-rate display. This is separate from BioZ respiratory-rate research.
- Test 15 adds OEM-sourced preparation tips for raw SpO₂ and temperature and a [step-by-step comparison of typed OEM service results with public Android sensor events](docs/OEM_SPO2_TEMPERATURE_SOURCE_TRACE.md). Raw values remain uncalibrated.

## Medical disclaimer

This is a research and personal-development tool, **not a medical device**. It is not intended for diagnosis, treatment, disease prevention, emergency monitoring, or medication decisions. Public datasets and papers can evaluate a method or provide context; they cannot establish Xiaomi Watch 2 Pro hardware calibration, clinical accuracy, or an individual's health status.

ECG remains research-only unless device-specific lead mapping, ADC timing, and raw-code-to-mV calibration are verified. Without that evidence, the project exposes only raw or normalized index-domain information and does not produce mV, RR, HRV, AFib, or diagnostic claims.

## APKs

Matching 4.0.0 test 15 APKs are available from the [ailisa.net download page](https://ailisa.net/portfolio/xiaomi-health-bridge#downloads) or [GitHub Release](https://github.com/jay19821026-dev/Watch2/releases/tag/v4.0.0-test.15):

- `XiaomiHealthBridge-Mobile-4.0.0-vc163-test15-debug.apk` — Android phone
- `XiaomiHealthBridge-Wear-4.0.0-vc163-test15-debug.apk` — Xiaomi Watch 2 Pro / compatible Wear OS device

Debug APKs use an Android development signature and are not Play Store releases. Separate signed Play candidate AABs have been built from test 15 source but not uploaded; Console version history, certificate enrollment, and declarations still need checking. Verify the [test 15 SHA-256 file](https://github.com/jay19821026-dev/Watch2/releases/download/v4.0.0-test.15/SHA256SUMS-4.0.0-test.15.txt) before installation; see the [bilingual install and test guide](docs/4.0_TEST_INSTALL.md).

The ready-to-paste bilingual Play Store listing, console selections, release notes, and graphic-asset inventory are in [`docs/PLAY_STORE_LISTING_COPY.md`](docs/PLAY_STORE_LISTING_COPY.md). Release flow, privacy, Data safety, and Health apps declaration materials are in [`docs/PLAY_STORE_RELEASE_GUIDE.en.md`](docs/PLAY_STORE_RELEASE_GUIDE.en.md), [`docs/PRIVACY_POLICY.en.md`](docs/PRIVACY_POLICY.en.md), and [`docs/DATA_SAFETY_DECLARATION.md`](docs/DATA_SAFETY_DECLARATION.md).

The 12-tester/14-day closed-test recruitment, feedback, evidence, and production-access templates are in [`docs/PLAY_CLOSED_TEST_PLAN.md`](docs/PLAY_CLOSED_TEST_PLAN.md).

## Build

Requirements: JDK 17, Android SDK 36, Google Maven, and Maven Central access.

```powershell
.\gradlew.bat :health-engine:test :mobile:testDebugUnitTest :wear:testDebugUnitTest
.\gradlew.bat :mobile:assembleDebug :wear:assembleDebug
```

Outputs are `mobile/build/outputs/apk/debug/mobile-debug.apk` and `wear/build/outputs/apk/debug/wear-debug.apk`. Local SDK settings, Gradle caches, build output, downloaded research data, and signing material are excluded from Git.

## External data and literature

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the complete purpose, limits, licenses, and citations. Principal offline references include PhysioNet ECG-ID, MIT-BIH Noise Stress Test Database, MIT-BIH Arrhythmia Database, Wrist PPG During Exercise, and optional PTB-XL comparisons. None is bundled in the source or APKs, and none provides device calibration.

For a one-to-one dataset → paper → algorithm → implementation map, see [`docs/ALGORITHM_DATASET_PAPER_MAP.md`](docs/ALGORITHM_DATASET_PAPER_MAP.md).

For the clean-room static review of Mi Fitness 9.8.348i—its data pipeline, differences from this project, independently reusable product concepts, and proprietary boundaries—see [`docs/MI_FITNESS_9_8_348I_REVERSE_ENGINEERING_COMPARISON.md`](docs/MI_FITNESS_9_8_348I_REVERSE_ENGINEERING_COMPARISON.md). Decompiled source, models, and native libraries are not included in this repository.

For the Watch 2 Pro 4.0 watch-APK audit, ECG activation gate and feature-by-feature public entry points, see [`docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md`](docs/WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md).

## Privacy and evidence boundaries

- The project does not scrape or redistribute the private Mi Fitness database.
- Raw research evidence stays in app-private storage by default.
- The repository excludes personal health exports, device/account identifiers, network addresses, signing keys, and dataset copies.
- Unknown is not rendered as zero; estimated is not presented as measured; raw code is not presented as a physiological unit.

## License

Original project code is available under the [MIT License](LICENSE). Dependencies, datasets, publications, standards, vendor documents, and trademarks retain their own terms. This independent project is not endorsed by Xiaomi, Google, Android, Wear OS, PhysioNet, or the cited institutions.

Author: **王宥傑 (Wang You-Jie)**.
