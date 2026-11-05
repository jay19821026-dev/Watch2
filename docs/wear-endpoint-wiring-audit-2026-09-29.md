# Wear Sensor Endpoint Wiring Audit — 2026-09-29

## Evidence scope

Static review of `RawVitalsProbe.Kind`, `WearSensorSuitePlan`, `WearRawSensorPublisher`, `WearDataProcessor`, `ChipFeatureCatalog`, and phone detail routes. The suite-coverage test requires every enum kind to be queued or explicitly documented as an alias. ADB reports no connected devices, so this is not proof of live sensor availability or end-to-end delivery.

## Shared pipeline

`SensorManager` endpoint lookup and listener registration → per-kind queue/gate → `WearRawSensorPublisher` snapshot → `/raw-sensor/<kind>` Data Layer item → `WearDataProcessor` validation/persistence → `RawSensorDetailActivity` / selected Health Center cards. Each stage can still fail independently; only paired-device evidence can establish runtime success.

## Endpoint disposition

| Endpoint group | Scan route / phone path | Current meaning and output boundary | Remaining proof |
|---|---|---|---|
| `HEART_RATE` | Biomedical queue; raw snapshot publisher and phone persistence; health summary has a qualified source-selection path | Standard heart-rate endpoint may report bpm, but a callback alone does not establish resting HR, wearing state, or freshness | Device callback, permission, wear/contact, timestamp, persistence and rendered source/time |
| `MAX_HEART_RATE`, `PPG`, `SPO2`, `MI_SPO2`, `LIFE_Q_OTT`, `ECG`, `BIOZ` | Biomedical queue; contact and/or OEM-mode gates; final raw snapshots carry per-kind payload | PPG estimates have stricter quality/mapping/time gates. ECG event rows remain unmapped unless verified encoding is supplied. Raw SpO₂ is not a percentage; BioZ is not Ω/body-fat without device transfer/calibration | Per-channel registration/event counts and terminal state; OEM schema, units/timebase, quality and device calibration where a physiological conversion is proposed |
| `BIOZ_RESPIRATION` | Explicit alias of `BIOZ`'s `com.google.wear.sensor.bioz`; not independently enqueued | No separate BioZ respiration capture or respiratory-rate algorithm exists; alias remains in inventory only and is not exposed as a separate actionable phone feature | Do not count alias as a separate sensor acquisition; future implementation requires a documented/validated method and distinct source evidence |
| Temperature endpoints (`BODY_TEMPERATURE`, `THERMOMETER`, `BOARD_TEMPERATURE`, `TEMPERATURE_ALARM`) | General queue; generic raw snapshot/persistence/detail route | OEM units and whether a channel is skin, ambient, board, algorithm, or alarm output are distinct; no substitution or core-temperature inference | Capture availability, source semantics, unit and timestamp per endpoint; validate display labels against fresh event |
| Standard environment (`PRESSURE`, `LIGHT`) | General queue; generic raw snapshot/persistence/detail route; derived pressure/light UI paths exist | Android standard pressure is hPa and light is lux. Pressure-derived altitude is relative/assumption-based, not floor count or surveyed elevation. Ambient-light path does not force system brightness | Live events, units and time provenance; baseline/drift and UI behavior on hardware |
| Motion/orientation (`ACCELEROMETER`, uncalibrated variant, `GYROSCOPE`, uncalibrated variant, `MAGNETOMETER`, uncalibrated variant, `ROTATION_VECTOR`, `GAME_ROTATION`, `GEOMAGNETIC_ROTATION`, `GRAVITY`, `LINEAR_ACCELERATION`, `ORIENTATION`) | General queue; generic raw snapshot/persistence/detail route; compass consumes orientation/magnetic inputs | Standard API units/axes apply by type; fused vectors and their underlying physical chip are not interchangeable. These are inputs, not proof that posture/fall features are clinically validated | Validate runtime callbacks, units, timestamps, sensor selection and compass calibration on device; derived fall/posture claims need separate evaluation |
| Steps (`STEP_COUNTER`) | General queue; raw counter snapshot/persistence/detail; Health Connect daily summary uses aggregate API | Android counter is cumulative since boot while active, not a daily total; daily count is separately aggregated from Health Connect | Device counter behavior/reset and independent Health Connect record/provider reconciliation |
| Motion triggers (`STEP_DETECTOR`, `WRIST_TILT`, `SIGNIFICANT_MOTION`, `STATIONARY`, `MOTION`) | Dedicated motion-trigger queue with bounded wait | Event/trigger endpoints can correctly produce no callback when their trigger condition does not occur; absence is not a continuous zero-valued reading | UI-started controlled movement/gesture test, trigger registration result, callback and cleanup |
| Wearing gates (`OFF_BODY`, `ADT`, `HARDWARE_ADT`) | Wear gate stage before broad scan | Standard off-body and vendor ADT statuses are separate endpoints; a capability listing alone does not establish accurate wear detection | Compare fresh status, watch state and subsequent biomedical acquisition in the same scan/session |
| Derived catalog rows (`HRV／壓力`, altitude, return navigation, other feature labels) | Not necessarily an independent sensor kind; may consume Health Connect or other endpoint data | Catalog presence is a mapping/potential feature, not a hardware measurement or proof the feature is wired end-to-end | Trace each feature's actual inputs, formula, freshness, UI route and regression tests; distinguish implemented, candidate and absent |

## Confirmed app-level gaps / risks

- ECG is received as raw API event values, but event field layout and sample clock are unverified; current phone policy correctly withholds ECG waveform/RR/BPM/HRV interpretation.
- BioZ respiration is an alias, not an independently sampled endpoint; a distinct respiration algorithm is absent.
- Raw `STEP_COUNTER` is not equivalent to Health Connect's daily total. Capability labels now explicitly distinguish the cumulative counter from daily aggregation.
- The capability catalog is not hardware inventory evidence. Wake-up/non-wakeup variants, calibrated/uncalibrated variants, and fused sensors must not be counted as separate physical chips.

## Acceptance gate

For each of the three planned UI-started paired runs, retain one session-correlated row per endpoint: availability, permission/registration result, event/sample count, sensor measurement time and source, payload interpretation/unit state, wear/OEM gate, publish/receive result, durable commit, restart readback, freshness decision, UI result, and stop/cleanup reason. Include failed, partial, cancelled and timed-out runs in the denominator. None of these gates was hardware-verified in this audit.
