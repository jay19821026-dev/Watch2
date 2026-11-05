# MAX86178 wearable calibration evidence (2026-10-10)

## Findings

- Analog Devices describes MAX86178 ECG programmable gain, built-in calibration voltages/self-test, synchronized PPG/ECG/BioZ channels, and BioZ calibration features. These are AFE capabilities, not a Watch 2 Pro transfer function, register configuration, electrode map, or proof that Xiaomi exports calibrated values.
- The MAX86178 EV kit is a separate two-board design with its own MCU, sensor-board component configuration, electrode cables, and GUI. Its ability to facilitate IEC 60601-2-47 testing does not certify the Xiaomi watch or make EV-kit coefficients portable.
- ADI's Study Watch 4.5 guide/SDK exposes sensor/application APIs and processed outputs on that ADI platform. It is useful as a firmware/system architecture example only; it is not a drop-in Xiaomi HAL decoder or calibration library.
- A wearable wrist BioZ study reports contact-resistance compensation using alternating 4-point/2-point measurements and evaluates body-fat estimates against DEXA in 203 participants. Its electrode geometry and measurement protocol differ from this watch, so its regression or scale cannot be copied.
- A wrist electrode-array paper describes using known reference resistors to measure gain/phase errors across frequency, then calibrating the measurement system. It also emphasizes electrode placement/contact and motion. This supports calibrating an assembled measurement path rather than inferring physical units from the AFE model name.
- Public GitHub search found ADI's `spo2-watch-sdk`, which targets the ADI VSM Study Watch 4.5, and a small third-party MAX86178 hardware design repository without firmware/calibration implementation. No verified Watch 2 Pro/MAX86178 end-to-end calibration repository was found in this search.

## Engineering implications

1. Keep current ECG `NORMALIZED_INDEX`/relative-amplitude output until a synchronized Watch 2 Pro raw stream and independent reference ECG are captured. Do not use EV-kit full-scale/gain or published AFE resolution alone as raw-to-mV coefficients.
2. Keep BioZ in relative-index space until the stimulus frequency/current or voltage, channel mapping, electrode topology, analog gain, and known-impedance reference response are verified for the assembled watch.
3. To unlock a research mV profile, import paired raw/reference time series from the same physical watch profile, with distinct non-overlapping train and holdout captures and shared timebase; retain per-device fingerprint and validation diagnostics. This is a research mapping, not clinical calibration.
4. Do not embed entire copyrighted papers or databases. Store bibliographic metadata, links, and brief fact summaries; use lawful public abstracts/open-access material. Mark any future research export as non-clinical, not as a legal guarantee that redistribution is authorized.

## Sources

- Analog Devices, [MAX86178 product page and documents](https://www.analog.com/en/products/max86178.html).
- Analog Devices, [MAX86178 Evaluation Kit guide (Rev. 5)](https://www.analog.com/media/en/technical-documentation/user-guides/max86178evkit.pdf).
- Analog Devices, [Study Watch 4.5 guide/SDK entry](https://analogdevicesinc.github.io/study-watch-sdk/python/).
- Jung et al., [Wrist-wearable bioelectrical impedance analyzer with miniature electrodes for daily obesity management](https://doi.org/10.1038/s41598-020-79667-3), *Scientific Reports* (2021).
- Ibrahim et al., [A Wrist-Worn Strap with an Array of Electrodes for Robust Physiological Sensing](https://doi.org/10.1109/EMBC.2018.8513238), IEEE EMBC (2018).
- Analog Devices, [Study Watch SDK source repository](https://github.com/analogdevicesinc/study-watch-sdk).
- Third-party MAX86178 board-only reference, [AmziSys ECG/PPG sensor wearable device](https://github.com/AmziSys/ECG-PPG-sensor-wearable-device); repository README says design files are not shared, so it is not treated as calibration evidence.

## Evidence still required for this watch

- Watch-specific firmware/HAL sample field contract, gain, electrode map, stimulus settings, and device profile identifier.
- A measurement setup with a calibrated ECG/BioZ reference instrument and synchronized captures from the same Watch 2 Pro.
- Independent holdout validation outside the fitted capture and, for health/clinical claims, an appropriately designed external clinical validation study.
