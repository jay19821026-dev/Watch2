# Health Data Freshness Policy

- Policy version: 1.0
- Effective date: 2026-09-28
- Status: Initial policy; metric-specific windows are intentionally unconfigured until their source semantics and product requirements are verified.

## Rules

- A metric may be called current/fresh only when its row has an approved maximum age, source time basis, future-time tolerance, and a traceable source rule.
- `UNCONFIGURED` means the record may be retained as history but must not be labeled current/fresh or used for a current-value score/trend.
- Measurement, publication, receipt, and synchronization timestamps are separate fields. Unknown or unconvertible measurement time is `timestamp_unknown`; malformed or out-of-tolerance future time is `invalid_timestamp`.
- Changing a row requires a policy-version increment, rationale/source, and tests for in-window, expiry boundary, future boundary, missing, malformed, and unconvertible timestamps.

## Metric rules

| Metric | Source/record type | Measurement time basis | Maximum age | Future tolerance | Status |
|---|---|---|---|---|---|
| Heart rate | Health Connect `HeartRateRecord` or identified watch sample | Source record/sample timestamp; basis must be verified per source | UNCONFIGURED | UNCONFIGURED | Not eligible for current/fresh classification |
| Resting heart rate | Health Connect `RestingHeartRateRecord` | Source record timestamp | UNCONFIGURED | UNCONFIGURED | Not eligible for current/fresh classification |
| HRV RMSSD | Health Connect `HeartRateVariabilityRmssdRecord` | Source record timestamp | UNCONFIGURED | UNCONFIGURED | Not eligible for current/fresh classification |
| Oxygen saturation | Health Connect `OxygenSaturationRecord`; calibrated percentage only | Source record timestamp | UNCONFIGURED | UNCONFIGURED | Not eligible for current/fresh classification |
| Sleep | Health Connect `SleepSessionRecord` and stage intervals | Source interval start/end | UNCONFIGURED | UNCONFIGURED | Not eligible for current/fresh classification |
| Steps | Health Connect `StepsRecord` | Source interval start/end | UNCONFIGURED | UNCONFIGURED | Not eligible for current/fresh classification |
| Distance | Health Connect `DistanceRecord` | Source interval start/end | UNCONFIGURED | UNCONFIGURED | Not eligible for current/fresh classification |
| Active calories | Health Connect `ActiveCaloriesBurnedRecord` | Source interval start/end | UNCONFIGURED | UNCONFIGURED | Not eligible for current/fresh classification |
| Body temperature | Health Connect `BodyTemperatureRecord`; wearable raw events are not equivalent | Source record timestamp | UNCONFIGURED | UNCONFIGURED | Not eligible for current/fresh classification |
| Weight | Health Connect `WeightRecord` | Source record timestamp | UNCONFIGURED | UNCONFIGURED | Not eligible for current/fresh classification |

## Adoption and implementation status

This document defines policy, not evidence that the current application complies. Until approved metric windows and regression tests are implemented, release acceptance for current/fresh displays and dependent calculations is FAIL. Do not invent windows or infer time bases to make a metric pass.

`SensorDisplayState.DEFAULT_FRESHNESS_MILLIS` (30 minutes) is only the recency of a received on-demand sensor-suite snapshot used by the engineering/debug UI. It is not an approved Health Connect metric freshness window, a continuous-measurement guarantee, signal-quality evidence, or clinical validity. The UI label is therefore “非近期掃描資料”; Health Connect rows above remain `UNCONFIGURED` until their own source semantics and product limits are established.

Raw Android sensor-event snapshots additionally require timestamp provenance `android_sensor_event_elapsed_realtime` before they can be classified as a fresh sensor input. Callback-wall-clock fallback timestamps, missing/unreported sources, and invalid clock bases are retained as raw history but treated as `timestamp_unknown` by the mobile sensor display and guarded research calculations. This raw-sensor provenance check does not configure Health Connect freshness and does not establish physiological validity.
