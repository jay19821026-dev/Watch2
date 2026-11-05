# ECG decoder root cause — 2026-10-10

## Finding

The observed `decoded_points=0` is an intentional research gate, not evidence that no HAL events arrived. `EcgRawEventDecoder.decode` parses 16-field rows, inspects the first ten fields as **candidates**, and returns `candidate_values_unverified` with `mappingVerified=false`. `EcgHalEventResearchDecoder.decode` explicitly returns an empty waveform and `decodedSampleCount=0` regardless of candidate count. `EcgPayloadInterpretationPolicy.VERIFIED_ECG_EVENT_CONTRACT_AVAILABLE` is false. This explains the direct software path to zero decoded points. Whether Xiaomi's fields contain ECG samples remains unverified; the intentional gate is not an OEM decoder defect.

## Pipeline

1. Watch `RawVitalsProbe` records `SensorEvent.values`, source timestamps, callback elapsed time, event counts, and bounded raw rows.
2. `WearControlService.sendRawSnapshot` serializes these fields in the existing Wear Data Layer snapshot; no second transport was created.
3. Phone `WearDataProcessor` validates the payload, invokes `EcgHalEventResearchDecoder`, and saves current snapshot metadata plus a Room raw-preview archive through `PpgEvidenceArchive`.
4. `EcgRawEventDecoder` produces layout/counter diagnostics, not ECG samples. `EcgDecoderReadiness` records independent sample, mapping, clock, and rate blockers.
5. `PpgEvidenceActivity` shows the index-domain raw-field inspection and `ECG_STATUS`; `HealthConclusionEngine` does not promote the candidate fields to rhythm diagnosis.

## Evidence limits

- The earlier phone snapshot documented 2,967 raw events, zero raw/decoded ECG samples, and 268 retained recent 16-field rows. This is a historical snapshot, not a fresh capture in this review.
- The 16-column pattern and `+10` counter-like field do not establish field semantics, sample packing, lead, gain, or ADC clock. Callback spacing is delivery cadence, not the acquisition clock.
- The workspace has no `.git` directory. Historical commits, blame, and the requested `feature/ecg-decoder-recovery` branch cannot be created from this checkout without its original Git metadata. Initializing a new unrelated repository would falsely imply continuity with project history.

## Decision

`ECG_STATUS = RESEARCH_BLOCKED`. A real decoder defect is not proven. Preserve raw events and the research inspector; do not claim AFib, normal/abnormal rhythm, bpm, RR, HRV, or mV from this payload.

## Follow-up watch evidence — 2026-10-14

Read-only ADB access to `192.168.0.16:37633` showed ECG registration, `209` events, `stopped_with_samples`, populated first/last event times, and `ecg_sample_timestamp_source=android_sensor_event_elapsed_realtime`. This confirms watch-side event delivery and an elapsed-time field, but does not establish the 16-field payload contract, ECG channel mapping, ADC rate, lead, gain, or calibration. The intentional decoder gate remains required.

The same watch sensor metadata advertises `maxRate=500.00Hz` for `max86178_ecg`. This is a sensor capability bound, not a measured session sampling rate. The source pipeline continues to publish the session rate as unknown when the HAL does not report a verified period; the metadata maximum must not be used as a substitute.

The Wear listener requests the ECG/BioZ minimum period of `2,000µs` when registering the sensor. That is an application request, not proof that the HAL delivered samples at 500Hz. `RawVitalsProbe.reportedSampleRateHz` intentionally returns `0.0` for ECG, so the request period is not promoted to the measured `ecg_sample_rate` field.

## Consumer-state correction — 2026-10-14

The phone UI previously collapsed several different pipeline outcomes into a generic waiting/unavailable message. `SensorResultAvailability` now separates persisted raw evidence, rejected signal quality, a calculation-eligible result, and an explicit synchronization/persistence failure. A never-started measurement remains `待量測` and is not mislabeled as a sync failure. This corrects presentation only: it does not promote the unverified 16-field payload to ECG samples or relax any clock, mapping, calibration, RR, HRV, mV, or AFib gate.

The synchronization-failure state is backed by the existing bounded rejection ledger rather than inferred from missing measurements. The Vitals page reads the last rejected sensor kind and rejection time from the control preferences and shows `同步失敗` only for a recent same-kind rejection when no successful raw capture is present. An old rejection or rejection from another sensor cannot override current evidence.

## Fresh paired-device observation — 2026-10-10 23:21–23:25 +08:00

A user-authorized, off-charger watch ECG capture was started from `WearEcgActivity` and at least four bounded segments reached the phone's `raw_sensor_results` preferences. Segment 1 was saved with 2,967 raw events, zero decoded samples, zero sample rate, and `NORMALIZED_INDEX`; its retained 261 rows had positive, strictly increasing `SensorEvent.timestamp` values (median adjacent event delta 20,019,271 ns). Segments 2–4 were saved separately with 2,969, 2,967, and 2,967 raw events; their retained 264, 264, and 268-row windows had negative source timestamps and all source-time validity flags were zero. Observed segments remained `candidate_values_unverified`. Counts from different segments must not be merged. The first segment's event cadence is not a verified ADC sample clock or proof that the first ten fields are consecutive ECG samples. The change from positive to negative source timestamps between adjacent segments is direct evidence that timestamp interpretation needs device/HAL investigation before a stable timeline can be published. Callback receipt timestamps stayed positive but cannot repair source time. The phone retained only bounded recent raw rows for each latest segment, not every one of the thousands of event payloads.
