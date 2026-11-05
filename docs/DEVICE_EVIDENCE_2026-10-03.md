# Paired-device evidence audit

Collected 2026-10-03; not a new acceptance scan.

- Both phone and watch are visible in ADB, both installed version 147 / 3.13.37.
- Watch battery reports level 100 and AC/USB/wireless/dock powered false. This does not prove wrist contact.
- Phone current window is NotificationShade, not the app. No UI-started measurement was performed.
- Watch suite preferences report completed, message 24/34 stages received samples. This is retained status, not fresh acquisition evidence.
- Saved start 1790974990819, end 1790599702775, status timestamp 1790975152534: end is older than start and belongs to an earlier interrupted session. Do not compute duration from this pair.
- Current source WearControlService now writes end only for a terminal state with a positive start and report >= start, otherwise removes it. This source behavior has unit coverage but the observed installed preference state contradicts the intended invariant. Matching version names alone do not establish installed artifact identity or that its scan executed this source.
- Latest app exit-info entries inspected are system empty-process eviction and package update, not proof of an app crash.
- Vendor healthservices/whshal native frames appear in retained crash history. Missing matching process header/abort evidence prevents causal attribution to this app or current scan. Bounded raw crash evidence saved locally in .tooling/verify/watch-crash-current.log. Do not describe vendor frames alone as a new app bug.

## Next acceptance gate

### 2026-10-03 continued verification

- Cross-device verification completed with `BUILD SUCCESSFUL`: mobile/Wear Debug unit-test tasks, mobile/Wear Release assembly, and Release lint completed without a Gradle failure. This is build evidence only; it does not prove sensor acquisition, Data Layer delivery, Health Connect freshness, or UI correctness.
- Phone ADB remains online. The Wear endpoint `192.168.0.16:41613` is `offline`; reconnect returned Windows connection-refused (10061). No Wear APK install, launch, sensor scan, or watch-side readback is claimed for this run.
- Release outputs remain unsigned. Formal release installation/publishing is still gated on the original signing keystore.

### Reconnection and current verification

- Both ADB endpoints are currently listed online. A Wear Debug replacement was attempted against `192.168.0.16:41613`, but the install command did not return before the bounded command window and was stopped; no install success or startup claim is made. Local Wear Debug SHA-256 remains `BD9CD81273028E3C4C43986383A061BDF13F57D549A176A393765AD1281CB75A`.

- After user-confirmed Health Connect consent, package dump reports 17 granted READ health permissions. Relaunched MainActivity through the launcher path; bounded app-only UI readback changed steps from loading placeholders to 722. SpO2 and sleep remained placeholders in this observation; their missing-record/permission/aggregation causes are not yet established. This proves the steps UI can receive a value, not its source completeness, day aggregation accuracy, or a fresh watch scan. Cross-device unit/Release tasks passed (`recovery-cross-device-validation.log`).

- Phone UI launch now shows the actual Health Connect consent screen, with zero selected permissions in the visible vital-sign group. User was asked to select permissions and confirm Allow. Do not attribute unavailable Health Connect values solely to algorithms before verifying current grants. No grant was automatically selected; no fresh Room archive/status file was found in this readback check.

- Archive acknowledgment gate now checks coroutine cancellation after storage and before deletion. Four targeted regression tests passed: ordered success, storage exception, storage cancellation, and cancellation immediately after successful storage. Debug assembly passed. These tests validate the sequencing helper used by production, not Data Layer restart/redelivery or Room device behavior.

- Subsequent connection recovered without reinstalling: WearMainActivity launch delivered to its running instance and application PID was present. This confirms process availability, not fresh sensor acquisition.
- Code review identified a PPG evidence durability gap: archive insertion runs in a coroutine independently of the DataItem acknowledgment, and the completed-receipt replay branch can acknowledge before archive recovery. Process cancellation or database failure can therefore lose the new Room preview while legacy snapshot storage succeeds. Durable archive checkpoint/recovery remains required; no fix is claimed yet.
- Follow-up patch routes accepted final PPG and completed-receipt replay acknowledgments through Room append-or-verify first. Archive failure or cancellation retains the DataItem; no parallel fire-and-forget archive remains. Mobile unit tests and Debug assembly passed in `ppg-ack-archive.log`. Automatic redelivery after archive-only failure and process-restart replay still require testing; this is not full durable retry acceptance. This patch is not yet installed.

- Watch reconnected on the newly supplied ADB endpoint: Android 14 / API 34, battery 99%, all charging flags false. Wear Debug APK replacement returned Success; no uninstall or data deletion.
- Phone main Debug APK replacement returned Success. The separate instrumentation APK installation was rejected with INSTALL_FAILED_USER_RESTRICTED; zero tests ran in this attempt. Replay and new derived-transaction rollback tests remain unverified on hardware.
- Shared health-engine test task succeeded (up-to-date). This does not establish hardware acquisition or paired scan acceptance.
- Retained vendor Health Services native crash frames predate this installation; they are not evidence of a new application crash or its cause.
- After installation, the launcher check returned `error: closed` and the watch became offline. Startup, installed hash matching, and fresh scanning were not verified. Local Wear APK SHA-256: BD9CD81273028E3C4C43986383A061BDF13F57D549A176A393765AD1281CB75A.

### Installed-artifact verification and update

Installed artifacts initially differed from the latest Debug APKs despite identical 147/3.13.37 version labels. Updated both via adb install -r (Success); no uninstall/data deletion. Then compared on-device base.apk SHA-256 to the local APK: both match.

- Wear SHA-256: F7A845B468A69948CB3A604373E8484D0F03D232147CCFA6D1AEFDCB6CC722B7
- Mobile SHA-256: 8B5171C01A17304FA7194296063060E852A2CC85E18DDEEC06B3DB088DE327F0

These are Debug builds, not formal signed Release delivery. EngineeringDebugActivity correctly rejects direct external shell launch because it is not exported; do not weaken export security to bypass UI acceptance. Exported MainActivity launch succeeds, but phone UI still shows NotificationShade. No new scan or readback acceptance is claimed.

Verify installed artifact identity against built APK; then run UI-started paired scans while worn, recording each new suite ID, start/end ordering, Data Layer delivery, phone persistence and UI readback. Do not repair history by substituting receipt time for acquisition time. Three scans and release signing remain unverified.
