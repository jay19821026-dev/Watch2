# Signal composer implementation checkpoint

## Implemented in this patch

- AdvancedSensorController temperature publication interval reduced from ten minutes to five seconds; existing first-sample persistence retained. This change affects this controller only, not every sensor capture path.
- ExternalReferenceCalibration returns held-out residual MAE, RMSE, signed bias, mean difference ±1.96 sample SD, and normal-approximation 95% confidence limits for bias. Rejects insufficient, nonfinite, and overflowed residual summaries.
- SensorCalibrationCenterActivity displays these exploratory statistics after fitting a research profile. Leave-one-out residuals are dependent; these intervals are not external clinical validation or subject-cluster-adjusted uncertainty.

## Outstanding scope

## Follow-up implementation and review

- AI analysis screen now renders the local result before attempting watch publication. A synchronous publication exception is logged and shown as a sync-only failure; calculation/render exceptions are logged separately and the error screen provides retry. This fixes error coupling, not an established root cause of the reported regression. Mobile unit tests and Debug assembly passed in `ai-analysis-recovery.log`.
- Current LocalAiHealthAnalyzer score is `100 - concern` and is withheld when evidence is insufficient. It is not measurement accuracy. No evidence supports a universal minimum 70 or 100% physiological output coverage.

- Clinical lab now displays bounded research summaries for PPG, ECG, BioZ, oxygen and temperature previews: finite coverage, raw mean/standard deviation, and mapped ECG index trend/adjacent change.
- Added tested, immutable-input short-interior-gap interpolation with per-point origin labels. Only explicitly mapped ordered waveforms qualify. Current CSV preview ingestion may remove missing tokens, so the presence of this helper does not prove real missing event reconstruction; preserved sparse index ingestion remains outstanding.
- Saved calibration profiles now retain a validation report and algorithm version, readable after reopening the calibration center. Existing profiles retain backward-compatible defaults. These remain current-profile records, not a complete multi-version model registry.
- Review retained unknown encoding/time/unit gates and bounded numeric input; summaries do not turn callback time into acquisition time. Normal-approximation uncertainty is exploratory and not subject-cluster-adjusted.
- Mobile/Wear unit tests, shared engine tests and both Debug/Release assemblies passed in `research-signal-review.log`.
- Mobile Debug replacement returned Success and launcher start succeeded. Wear initially went offline; reconnect succeeded and Debug replacement returned Success. Installation does not establish fresh sensor or UI acceptance.
- Watch battery 92%, charging flags false. Retained crash history contains a vendor Health Services SIGSEGV at 11:39, predating this build; causation is unresolved.

- Complete per-event bounded raw archive and overflow accounting across all channels.
- Connect multi-channel reconstruction with explicit mapping/order evidence and immutable derived storage.
- Synchronize motion evidence and add session trend/quality summaries.
- Validate ECG rate/RR extraction with trustworthy waveform mapping and time scale.
- Train and externally validate physiological models from paired reference data; no new physiological model is supplied by this patch.
- Persist full calibration reports with dataset/session split and model version; current reports are shown at fitting time.
- Verify fresh paired device acquisition, transmission, persistence and UI after installation.
