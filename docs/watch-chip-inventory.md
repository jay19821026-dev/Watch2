# Xiaomi Watch 2 Pro sensor and chip inventory

## Evidence and limits

- Source: Wear OS `dumpsys sensorservice` capture at 2026-09-25 13:31 (local), plus this app's sensor catalog and the phone export captured at 15:43.
- The HAL exposes 66 sensor endpoints, including wake-up/non-wake-up pairs and virtual sensors. Those endpoints are not 66 separate chips.
- Names such as `bmi26x`, `ak0991x`, and `icp101xx` are HAL family labels, not proof of an exact silicon SKU. Treat a vendor string as sensor-service metadata, not a board-level bill of materials.
- The phone export was already old when inspected at about 16:10. It is useful to classify the last capture, not evidence of current live readings.

## Reported physical sensor families

| Family / reported part | Vendor string | HAL endpoints / functions | Last exported result and current limitation |
|---|---|---|---|
| MAX86178 | `analog` | PPG, ECG, SpO₂ raw, HR raw, BioZ, optical off-body ADT, hardware ADT | PPG: 76 values / 16 packets, fill/zero-heavy, unknown channel map and sample clock; no BPM/HRV. HR optical: 128 points, no BPM or clock. SpO₂: 512 raw points, no OEM percent scale. Xiaomi SpO₂ algorithm: 0 events. ECG: 10 points / 1 packet, contact score 0 and no verified clock/lead or mV mapping. BioZ: 512 raw points, no validated Ω/current/electrode model. These are separate acquisition paths; raw data presence is not a usable vital sign. |
| CT7117 | `SENSYLINK` | Board temperature and body-temperature HAL channels | Body-temperature channel: 204 values at about 15.8 reported events/s in the old export. Treat as skin/watch measurement only; chip datasheet accuracy is not complete-watch or clinical accuracy. |
| `bmi26x` family | `BOSCH` | Accelerometer and gyroscope, calibrated and uncalibrated | Present in HAL. Exact chip SKU not established by this alias. Motion capture is foreground/activity dependent; do not infer a fall or posture diagnosis from raw acceleration alone. |
| `ak0991x` family | `akm` | Magnetometer, uncalibrated magnetometer; compass/orientation uses fusion | Present in HAL. Exact AKM part and system calibration are not established. Compass needs calibration and magnetic-interference handling. |
| `icp101xx` family | `TDK-Invensense` | Barometric pressure | Present in HAL. Exact part/OTP parameters are not exposed to the app; relative altitude needs a stable baseline and drift filtering. |
| LTR311 | `Lite-On` | Ambient light | Present in HAL as on-change. Not a medical sensor; OS/OEM owns automatic display brightness. |

## Algorithm and sensor-hub endpoints (not additional confirmed chips)

| Reported source | HAL / service functions | Classification |
|---|---|---|
| Xiaomi / MI | `pedometer_wrist` step detector/counter, `mi_wear` off-body, wrist tilt, `mi_spo2` | Vendor services/algorithms. The sensor-service dump does not identify their underlying silicon. `mi_spo2` had no events in the old export. |
| LifeQ | `lifeq_lel_heart_rate`, `lifeq_del_ott` | Algorithm/sensor endpoints. No specific optical die is proven by these names. |
| Qualcomm/QTI + AOSP | Gravity, linear acceleration, rotation vectors, geomagnetic rotation, significant motion, stationary and motion detection | Fused/virtual or platform endpoints, not separate physical health chips. |
| Android location provider | GPS route in exercise screens | No GNSS chip identity was available in the sensor-service inventory; keep it separate from this sensor-chip list. |

## One-by-one investigation order

1. **MAX86178 acquisition first:** test each endpoint only in its correct OEM/user measurement mode; record actual callback and sample counts, timestamps, packet metadata, contact/wear state, and registration result. Do not treat sample-count/shape heuristics as calibrated quality.
2. **LifeQ/Wear Health Services heart rate:** verify passive registration, permissions, callback timestamps and Health Connect/Data Layer persistence independently of MAX86178 raw PPG.
3. **CT7117 temperature:** verify current callbacks, units, thermal stabilization, skin-contact conditions and reference-thermometer comparison; do not label skin temperature as core temperature.
4. **BMI26x / MI pedometer:** test step counts against controlled walking and device/provider totals; separate raw IMU from Xiaomi step algorithm.
5. **ICP101xx pressure and AK0991x compass:** compare against reference barometer/known heading, characterize drift/interference, and validate reset/baseline behavior.
6. **LTR311 light / wrist tilt and QTI fusion:** verify event delivery and battery cost in bounded sessions; report the platform's output rather than implying a raw physical measurement.

## Literature-backed algorithm boundaries

- **MAX86178 PPG:** the 2023 wearable PPG roadmap describes motion artefacts as a major reliability problem and identifies signal-quality classification, artefact-region detection, and recovery as separate research approaches. App policy: preserve raw waveform, surface quality/coverage and reject unreliable pulse/HRV derivation; do not interpolate invalid packets into a clinical number. Source: [Charlton et al., The 2023 wearable photoplethysmography roadmap](https://pmc.ncbi.nlm.nih.gov/articles/PMC10686289/).
- **CT7117 wrist temperature:** contact skin-temperature measurements depend on sensor attachment, contact pressure, equilibrium, and ambient conditions. A wrist skin temperature trend is valid to label as skin/device-site temperature, not as core temperature or fever diagnosis. Source: [Jia et al., Skin Temperature Measurement Using Contact Thermometry: A Systematic Review](https://pubmed.ncbi.nlm.nih.gov/29441024/); see also [wearable core-temperature prediction systematic review](https://pmc.ncbi.nlm.nih.gov/articles/PMC9572283/), which reviews device-specific predictive algorithms rather than a universal wrist conversion.
- **BMI26x fall algorithms:** published reviews show heterogeneous sensor placement, protocols, and performance; commonly studied setups use waist/lumbar placement and controlled functional tests, which are not equivalent to a wrist-watch real-world fall-risk diagnosis. App policy: keep wrist impact detection as a user-confirmable safety event; do not present accelerometer thresholds as a medical fall-risk score. Sources: [Bet et al., Fall detection and fall risk assessment in older person using wearable sensors: a systematic review](https://pubmed.ncbi.nlm.nih.gov/31450081/); [systematic review of wearable sensor-based fall-risk assessment](https://pmc.ncbi.nlm.nih.gov/articles/PMC9504041/).
- **RR/HRV:** do not infer clinically useful HRV from a few detected beats. The existing app minimum is only a software gate, not a duration/clinical-validity guarantee; retain the research-only qualifier until a device-specific comparison with reference ECG and adequate recording duration exists. Source: [Shaffer & Ginsberg, An Overview of Heart Rate Variability Metrics and Norms](https://pmc.ncbi.nlm.nih.gov/articles/PMC5624990/).
- These publications support which measurements require quality gates and what must not be claimed. They do not provide Xiaomi Watch 2 Pro sensor selection, MAX86178 channel mapping, ECG lead wiring, ADC scaling, or clinical calibration constants.

## Immediate failure summary

In the 15:43 phone export, 27 channels had historical raw events; 26 were classified stale and only one was fresh at that export timestamp. The one fresh value was a Health Services heart-rate sample. At inspection time roughly 27 minutes later, even that file was no longer current. The watch was on USB power and no app-owned service was bound, so the state does not establish a live sensor test. The primary present blocker is therefore absent/current acquisition evidence, while several MAX86178 channels additionally lack enough device-specific metadata for safe physical-unit conversion.
