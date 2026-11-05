# ECG Research Calibration Evidence

This note records the limits of the ECG raw-to-mV research profile and the external evidence used to define its software gates. The profile is a display-scale research conversion only; it does not establish device calibration, lead configuration, diagnostic accuracy, or clinical validity.

## Evidence and scope

- FDA, [Diagnostic ECG Guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/diagnostic-ecg-guidance-including-non-alarming-st-segment-measurement-guidance-industry): describes device comparison and performance testing considerations for diagnostic ECG devices. It is not a validation protocol specific to Xiaomi Watch 2 Pro and does not supply an ADC-to-mV coefficient.
- Clifford et al., [Signal-quality indices for the electrocardiogram and photoplethysmogram](https://doi.org/10.1109/JBHI.2014.2338351): develops ECG/PPG signal-quality methods using the study's own datasets. Its metrics may motivate signal-quality gating, but cannot calibrate this watch's sensor or establish clinical performance on this device.
- Analog Devices, [MAX86178 product page and datasheet](https://www.analog.com/en/products/max86178.html): describes AFE features, programmable gain and built-in test/calibration facilities. Chip capabilities are not evidence of Xiaomi's electrode wiring, firmware configuration, ADC scaling, or exposed HAL encoding.

## Software gate

The core research engine fits a linear raw-to-reference-mV mapping on training pairs and evaluates it only against separately supplied holdout pairs. A profile is eligible only when the training and holdout dataset identifiers differ, sample counts meet the minimum, the waveform encoding matches, and holdout RMSE is within a declared threshold. The ECG waveform mapping must also be verified before the engine emits research mV values. Otherwise it retains normalized/index-domain waveform output and emits no mV sequence.

Dataset identifiers and a user-entered RMSE threshold are provenance declarations, not proof that the reference instrument is traceable, synchronized, correctly configured, or that datasets are genuinely independent. A successful software gate therefore means only that the entered research profile met its declared numeric checks. It must not be labeled OEM calibration or clinical validation.

## Copyright

The project stores bibliographic links and short technical summaries only. It does not bundle or redistribute paywalled articles, standards, or restricted databases. Non-commercial or private use alone does not grant redistribution rights.
