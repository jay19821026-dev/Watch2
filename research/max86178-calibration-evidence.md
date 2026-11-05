# MAX86178 public calibration evidence

Checked 2026-10-10 against Analog Devices primary documentation. This note records what can be translated into code and what cannot be inferred for the Xiaomi Watch 2 Pro.

## What the public documentation establishes

- The MAX86178 is a multi-channel AFE for PPG, ECG, and BioZ. Its ECG path contains a MUX, programmable gain, filters, and ADC; BioZ has selectable drive and receive paths and calibration support. The datasheet lists performance/range specifications and synchronized FIFO timing, but the public two-page datasheet does not provide the watch's register configuration or a conversion contract for the Xiaomi HAL event payload. [MAX86178 datasheet](https://www.analog.com/media/en/technical-documentation/data-sheets/max86178.pdf)
- The MAXREFDES106/Health Sensor Platform 4.0 guide describes a reference implementation, including ECG signal-chain configuration, electrode assignment, and BioZ calibration via internal/external resistors. It requires configuration of the signal path and electrodes; its example profile is not evidence of Xiaomi's board, electrode topology, gain, or firmware settings. [MAXREFDES106 user guide](https://www.analog.com/media/en/technical-documentation/user-guides/maxrefdes106.pdf)
- The MAX86178 evaluation kit is a configurable ECG/PPG/BioZ development platform with its own sensor board and software. An evaluation-kit calibration or output is not transferable to a finished watch without proving equivalent hardware and configuration. [MAX86178EVKIT guide](https://www.analog.com/media/en/technical-documentation/user-guides/max86178evkit.pdf)
- ADI's public ECG ADC-to-mV example with `1000 * ADC_count / (2^17 * Gain)` is explicitly for the **MAX30003**, including its word format and gain choices. It must not be copied into a MAX86178 or Xiaomi HAL decoder. [MAX30003/MAX86171 synchronized ECG/PPG note](https://www.analog.com/en/resources/design-notes/chips-to-measure-ecg-and-ppg-with-temporal-correlation--maxim-integrated.html)

## Engineering consequence

No public source found here establishes that Xiaomi HAL `SensorEvent.values` are MAX86178 FIFO words, which indices are ECG samples, the ECG lead/mux, ADC encoding, PGA gain, filter settings, acquisition clock, or raw-to-mV transfer. The current phone capture also has ECG events but zero extracted ECG samples. Therefore a MAX86178/MAX30003 formula cannot decode this payload or produce defensible mV. Keep `mapping=unverified`, use only clearly labelled raw/index-domain inspection, and keep RR/bpm/mV gated until the payload contract is verified.

## Calibration path supported by the evidence

1. Obtain the Xiaomi/OEM HAL payload and device configuration contract (field mapping, encoding/sign convention, lead/mux, gain, filter, clock and calibration state), or obtain raw AFE access plus the exact register dump.
2. Verify the decoder using known digital vectors and a synchronized signal/reference generator; retain original events and compare reconstructed samples against the instrument trace.
3. For a research mV profile, collect synchronized watch raw data and calibrated reference ECG over the supported amplitude/frequency range; bind the profile to sensor/configuration fingerprint and evaluate on held-out sessions/subjects. Do not train and validate on adjacent samples from one recording only.
4. For BioZ, record stimulus frequency/current, I/Q channel selection, electrode topology, and reference resistor calibration. A chip's programmable BioZ modes do not identify which mode the watch uses.
5. Only then expose device-specific physical units. Do not borrow MAX30003/MAX86150 register formats, gains, or conversions for MAX86178.

This is a research engineering note, not a medical-device validation or clinical claim. Source documents remain external links; copyrighted PDFs are not bundled into the app or redistributed.
