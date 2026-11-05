# ECG research blockers

`ECG_STATUS = RESEARCH_BLOCKED`

| Gate | Current evidence | Required to resume |
| --- | --- | --- |
| Field mapping | 16-field pattern only; first ten values are candidates | OEM/HAL schema or paired, independently synchronized reference capture proving field identity and ordering |
| Acquisition time | The new first segment had positive monotonic event timestamps, but subsequent segments returned negative source timestamps; callback time is only a receipt envelope | Explain the cross-segment timestamp sign change and verify the watch source sample clock/period and event-to-sample packing |
| Physical amplitude | No watch-path lead, gain, baseline, ADC-to-mV contract | Device-specific instrument reference with held-out validation |
| AFib research | No verified ECG waveform/beat intervals or independent performance study for this capture path | Reference-labelled data and subject/session-disjoint validation after the signal/time gates pass |

The decoder retains raw rows and index-domain field trends. Blocked ECG must not erase or delay independently valid daily health functions. Stop expanding ECG reconstruction algorithms until one of the missing contracts or synchronized reference datasets is obtained.

## Public MAX86178 evidence checked — 2026-10-14

Analog Devices' MAXREFDES106 guide states that its ECG heart-rate algorithm supports a 512 Hz sampling rate and that the ECG ADC rate is derived from PLL and divider/oversampling settings. The MAX86178 product documentation also describes synchronized ECG/PPG/BioZ acquisition. These are evaluation-platform/device-family facts only; they do not identify Xiaomi's 16-field Android HAL payload, its active ADC configuration, lead mapping, gain, or timestamp semantics. Android's Sensor HAL exposes a `sensors_event_t` timestamp and payload, but the public interface does not define Xiaomi's vendor sensor type's field layout. Therefore these sources strengthen the rejection of the current callback/`maxRate` inference but do not unlock ECG decoding, mV, RR, HR, HRV, or AFib.

Sources: Analog Devices MAXREFDES106 User Guide, ECG/HR and ADC sample-rate sections; Analog Devices MAX86178 datasheet; Android Open Source Project Sensors HAL interface.
