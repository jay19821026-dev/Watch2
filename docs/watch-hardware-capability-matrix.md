# Watch 2 Pro Hardware Capability Matrix

Snapshot source: paired watch `dumpsys sensorservice` sensor catalog on 2026-09-26. This records Android-exposed sensor names and vendor strings, not a PCB teardown or proof that every OEM algorithm emits data. Wake-up/non-wakeup variants are consolidated. Exact part numbers remain unverified unless the HAL name explicitly identifies a family.

The app labels candidate sensor families separately from physical part identity. Android fused/virtual orientation, rotation, gravity and linear-acceleration endpoints are not attributed to one physical chip; step-counter/detector and standard heart-rate endpoints are attributed by their runtime sensor metadata rather than assumed to be Bosch IMU or LifeQ hardware. The `chip_part` field is provenance text and may explicitly say that family, SKU, or producer is unverified.

## Exposed hardware and logical channels

| HAL identity / vendor string | Exposed function | Safe app use now | Limits / next evidence |
|---|---|---|---|
| `bmi26x` / BOSCH | Accelerometer, gyro, calibrated/uncalibrated variants | Motion/activity features, step-context, gesture and fall-candidate features | Exact BMI26x SKU and watch calibration are unknown; wrist placement and real-world validation limit fall claims. Bosch BMI270 is a wearable IMU reference, not proof of exact fitted SKU. |
| `ak0991x` / akm | Magnetometer, uncalibrated magnetometer | Compass after fusion/calibration; magnetic-disturbance indicator | Need calibration and interference rejection; never trust heading during magnetic disturbance. |
| `icp101xx` / TDK-Invensense | Barometric pressure | Relative pressure/altitude trend, climb/descent candidate | Absolute altitude needs local reference; weather/HVAC/vent behavior and motion confound floors. |
| `ltr311` / Lite-On | Ambient light | Ambient-light trend and user-facing night-mode suggestion | Does not grant permission to change system brightness; sampling must remain bounded. |
| `max86178_*` / HAL vendor `analog` | PPG, HR, ECG, BioZ, SpO2, ADT/off-body and HW ADT channels | Acquisition availability, event timing, sample counts, raw waveform continuity and quality diagnostics | HAL strings identify channel names; they do not disclose watch configuration, electrode routing, scale, gain, sampling clock or OEM calibration. Raw values are not bpm, %, mV, Ω, or diagnosis. |
| `mi_spo2` / MI_SLATE | Xiaomi SpO2 algorithm channel | Availability/status only unless a documented, validated percentage is returned | No current proof that this channel emits a calibrated percentage. Never substitute optical raw values. |
| `lifeq_lel_heart_rate` / LifeQ | Optical HR logical sensor | Use only if the event explicitly provides valid bpm and fresh provenance | Capability alone does not establish event availability, accuracy or clinical validity. |
| `ct7117` / SENSYLINK | Board temperature and body-temperature channels | Raw availability and same-device/site trend only after confirming units and event quality | HAL name/vendor does not establish skin/core temperature conversion or site calibration. |
| `thermometer` / vitalsilicon | Wear OS temperature algorithm channel | Read only a documented algorithm output with its units/provenance | Current emissions/units/validation are unverified; board temperature is not body temperature. |
| `pedometer_wrist` / MI | Step counter and step detector | Counts, cadence/step-rate trends where timestamps are valid | Avoid duplicate counting with Health Services/Health Connect; reconcile source and time ranges. |
| QTI/AOSP virtual sensors | Rotation vector, geomagnetic rotation, gravity, linear acceleration, significant motion, stationary/motion detection, orientation | Orientation, activity context, motion/fall candidate fusion | Virtual/fused outputs are not independent physical chips; avoid counting aliases as separate hardware. |
| `temperature_alarm` / LC | Temperature alarm channel | Capability/status only | Not a continuous calibrated body-temperature stream. |

## Current data-integrity finding

The latest phone export remains old (last full suite began 04:07; export timestamp 04:41). Its historical per-channel diagnostics show PPG registered with 8 watch events, ECG with 217, and BioZ with 426; each had one phone-accepted snapshot and stopped with samples. That proves historical capture and at least one accepted snapshot, not present-day operation or full waveform delivery. The phone counter is snapshot-count, not waveform-sample count, so no loss rate can be inferred. PPG remains quality/calibration blocked; ECG and BioZ remain raw/unscaled; respiration has no independent acquisition/validated derivation. At the later live check the watch remained USB-powered on the charging UI; no current scan was run.

## Algorithm work order and safety gates

1. **Acquisition diagnostics first:** per session log capability, registration result, first callback latency, event/sample count, timestamp domain/monotonicity, snapshot enqueue, phone acceptance, stale age, and stop reason. Distinguish unsupported, not started, no events, raw-only, quality rejected, calibration missing, and derived estimate.
2. **Low-risk motion/context:** step cadence/activity context from validated step and IMU data; pressure-plus-motion climb candidates; compass with magnetometer calibration and disturbance suppression. Validate against counted/manual reference data before scoring.
3. **Fall candidate:** short-window accelerometer/gyro pattern + post-impact inactivity + user cancellation; present as an unverified event candidate until wearable-specific prospective validation and false-alarm measurement. Never claim zero misses or diagnostic risk.
4. **Temperature trend:** preserve sensor/site/units, compare only same-watch same-location measurements against personal baseline and environmental context. Do not infer fever/core temperature from uncalibrated skin/board temperature.
5. **Optical/electrical:** first expose availability, sentinel/zero/saturation proportion, callback-time distribution, gaps and quality trend. Only derive bpm/SpO2/ECG units/BioZ units after device-specific format, calibration and validation evidence.

No algorithm can make failure probability zero. The objective is explicit terminal states, useful quality/provenance trends, bounded sampling/power, and no fabricated physiological values.

## Primary references

- Analog Devices, [MAX86178 product page and datasheet](https://www.analog.com/en/products/max86178.html): documents the AFE's PPG, ECG and BioZ capabilities and evaluation/reference designs. Its chip specifications do not calibrate this watch's complete optical/electrode/mechanical signal chain.
- Bosch Sensortec, [BMI270 product page](https://www.bosch-sensortec.com/en/products/motion-sensors/imus/bmi270) and [datasheet](https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bmi270-ds000.pdf): wearable IMU and motion-triggered features; fitted watch SKU still unconfirmed.
- TDK InvenSense, [ICP-101xx datasheet](https://product.tdk.com/system/files/dam/doc/product/sensor/pressure/capacitive-pressure/data_sheet/ds-000186-icp-101xx.pdf): low-noise barometric sensor and pressure/altitude capability; watch integration still needs calibration.
- Charlton et al., [Photoplethysmogram Analysis and Applications: An Integrative Review](https://pubmed.ncbi.nlm.nih.gov/35300400/): surveys PPG artifacts, signal quality indices, preprocessing and feature detection.
- Warrington et al., [Wearable devices for falls: umbrella review](https://pubmed.ncbi.nlm.nih.gov/34775947/): summarizes heterogeneous sensitivity/specificity and notes sensor-placement and real-world evidence limitations.
- [Skin Temperature Measurement Using Contact Thermometry: A Systematic Review](https://pubmed.ncbi.nlm.nih.gov/29441024/): highlights setup, attachment, pressure, environment and sensor-type effects; absolute skin readings require careful comparability.

## References applied carefully

The MAX86178 is a high-performance analog front end, not a turnkey calibrated watch. Reference designs use their own electrode/optical arrangement, firmware and validation, so their coefficients cannot be copied into the Xiaomi watch. Published fall-detection performance is heterogeneous and sensor placement matters; published rates are not this app's performance. Skin temperature is highly setup-dependent; only a within-device trend is potentially meaningful without clinical conversion.
