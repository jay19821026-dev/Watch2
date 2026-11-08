# Independent sensor review assignments

Coordinator: Codex. Owner: 王宥傑. Status: assignments prepared; browser delivery and responses **not completed**. Only de-identified observations below may be sent. No raw health values, device IDs, account details, credentials, or source files.

## Shared brief

An Android/Wear OS health app captures raw vendor PPG, ECG, BioZ and temperature events. Many event timestamps are missing or slightly ahead of the callback monotonic clock, so the app has stored a zero sample time and hidden the capture in freshness-based UI. A repair now preserves sensor time separately from callback receipt time and keeps bounded raw history through an empty later session. OEM APK inspection shows separate raw collectors and typed result services; native ECG filtering/QRS/AF function names are visible, but coefficients, calibration and model weights are unavailable. Review this architecture without assuming a clinical result or advising a private-service bypass.

## Assigned reviewers and questions

| Reviewer | Assignment | Evidence required in response |
| --- | --- | --- |
| DeepSeek | Audit producer → Data Layer → phone merge ordering, retries, and preview/receipt atomicity. | A minimal counterexample and deterministic regression case. |
| Gemini `s4140666jpn` | Check primary Android SensorEvent, elapsed realtime, Health Services and Health Connect time semantics. | Official API links; distinguish documented guarantee from vendor behavior. |
| Gemini `jay19821026` | Classify missing, future, stale, rejected, uncalibrated and hidden states independently. | State table with one user-facing outcome per state. |
| Gemini `jaysato1026` | Design falsification experiments for vendor clock origin across reboot, charging, on-wrist and repeated capture. | Smallest paired-device test and pass/fail criteria. |
| Grok | Challenge performance, buffering, callback ordering, and battery impact of the timestamp repair. | Concrete failure mode and bounded mitigation. |
| Claude | Adversarial code review: identify how history might be misdated, overwritten, or mistaken for a physiological result. | Severity-ranked findings tied to a reproduction path. |
| Kimi | Check peer-reviewed ECG/PPG literature relevant to timing, quality, and AF research without generalizing to this device. | DOI/venue, study population, and why it does or does not validate this watch. |
| Meta AI | Review plain-language result hierarchy and whether a person can tell current result from historical raw capture. | Short card copy and a user comprehension test. |

Acceptance: each response must name one falsifiable claim and its evidence limit. The coordinator verifies primary sources and code/tests before adopting any suggestion. No model consensus substitutes for device calibration or clinical validation.
