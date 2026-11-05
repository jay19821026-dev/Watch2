# ECG Medical-Use Readiness: Evidence Checklist

Date: 2026-10-10

## Independent AI review and official-source update

Six independent local reviewers agreed on the same three non-substitutable evidence layers: (1) a defined intended use and target population plus independently validated algorithm performance, (2) end-to-end calibration and verification of the exact watch hardware/firmware/sensor path against traceable ECG references, and (3) a medical-device quality system and market-specific regulatory authorization. Literature, database scores, chip specifications, and passing software tests cannot substitute for these layers.

The FDA QMSR became effective on February 2, 2026 and incorporates ISO 13485:2016 by reference for applicable finished-device manufacturers. FDA's software policy navigator states that software intended to process or analyze a body-signal acquisition system for a medical purpose may be a device function. FDA diagnostic ECG guidance expressly does not resolve automated interpretation/classification/diagnosis, so a separate intended-use-specific regulatory strategy is needed. For ambulatory ECG, FDA recognizes IEC 60601-2-47; for diagnostic ECG reports, IEC 60601-2-25 is listed with a different scope. The applicable standards and submission route must be confirmed for the proposed product and market.

For arrhythmia-detector submissions, FDA's current special-controls guidance says performance evidence should describe and reproducibly test the algorithm, report sensitivity and specificity, use a development database separate from verification, generally evaluate at least two databases, and have rhythm annotations performed by at least three qualified cardiologists. These are recommendations in that guidance for the covered device type; the actual plan/acceptance criteria must be confirmed for this product and indication with the regulator. The same guidance explicitly separates an arrhythmia detector/alarm from ECG software that makes an interpretive diagnostic statement.

For Taiwan, TFDA directs applicants to determine whether the intended product/function falls within Taiwan's medical-device classification and grade, then follow the applicable registration path; it also publishes software-validation and AI/ML medical-device software technical guidance. This project has no classification opinion or TFDA registration evidence. The current software guidance is a preparation reference, not a substitute for product-specific review or clinical evidence.

## Project gap assessment (current source audit)

| Gate | Evidence required | Current state | Status |
|---|---|---|---|
| Intended use and jurisdiction | Locked indication, user, population, claims, classification and regulatory pathway | Not defined as a medical product; current app labels are research/reference | NOT MET |
| Signal contract | Verified HAL field map, lead/electrode layout, sample clock/rate, gain/baseline, filtering and firmware scope | Opaque HAL rows; mapping and clock unverified | NOT MET |
| Hardware chain | Traceable simulator plus simultaneous reference ECG; repeatability across devices/firmware/conditions | No paired watch/reference evidence | NOT MET |
| Algorithm performance | Frozen algorithm, subject-separated independent datasets, prespecified endpoints, subgroup/noise analysis, confidence intervals and unreadable-rate reporting | MIT-BIH is only an external software fixture; it does not establish performance on Watch 2 Pro inputs | NOT MET |
| Clinical validity | Prospective or otherwise justified study on intended hardware, software, population and claim with adjudicated reference | None in repository | NOT MET |
| Product controls | Design controls, risk management, software lifecycle, cybersecurity, usability, traceability, post-market process | No medical-device QMS or authorization evidence in repository | NOT MET |
| Release authorization | Applicable market authorization/clearance and labeling for exact product configuration | None established | NOT MET |

### Implemented safeguard from this review

The research ECG core now suppresses R-peak candidate indices unless the waveform mapping (or a matching paired-reference calibration profile), wear state, and minimum quality gate are satisfied. This reduces misleading output but does not validate the detector or make the app medical-grade. A regression test covers unmapped, low-quality, and not-worn cases.

### Decision

The project is **not medical-grade and cannot be made so by code changes alone**. The next blocking artifact is a documented Xiaomi/LifeQ signal contract or a traceable, synchronized Watch 2 Pro/reference-ECG bench capture that establishes field mapping and timing. A qualified regulatory/quality team must separately lock the intended use and market pathway. Until all gates are independently met and authorization is obtained where required, outputs stay research-only and must not be presented as medical normal/abnormal or diagnosis.

## What the independent reviews found

- Per-column min–max normalization is acceptable only as an exploratory plot of opaque raw fields. It does not decode an ECG or validate waveform shape, time, lead, or voltage.
- The public MAX86178 data sheet / evaluation-kit guide describe the AFE and evaluation board, not Xiaomi Watch 2 Pro firmware, HAL field mapping, clock, or calibration. No verifiable public GitHub decoder for this watch's Wear OS event format was found.
- WFDB datasets make channel name, sampling frequency, gain, baseline, units, and annotations explicit. Those are not inferable from a 16-number `SensorEvent.values` row.
- FDA guidance does not confer authorization just because software follows a paper or uses FDA/IEEE-referenced methods. The FDA's digital-health navigator says intended use and signal-analysis function determine the regulatory analysis; signal acquisition/processing/analysis may be a device function. The diagnostic ECG guidance does not itself validate an automated rhythm classifier.
- MIT-BIH Arrhythmia Database record 100 has been downloaded locally as a public algorithm-test fixture. It cannot calibrate the watch's proprietary event format or prove clinical performance on this watch.

## Engineering gates before any medical claim

1. **Define the intended use and jurisdiction.** Specify whether the function displays a research signal, estimates rate, screens a rhythm, or diagnoses a condition; identify intended users and population. Obtain regulatory counsel / authority feedback for the target market.
2. **Establish the watch signal contract.** Obtain OEM/LifeQ documentation or paired bench evidence for all 16 field meanings, lead/electrode configuration, packing/order, counter semantics, source clock/sample period, filters, ADC gain/baseline, units, and firmware scope.
3. **Validate the acquisition chain.** Capture watch and a traceable reference ECG simultaneously on the same subject/session. Verify missing/reordered samples, time alignment, amplitude transfer, saturation, filtering, motion/contact failure, and device/firmware variation. Do not calibrate by matching unrelated public-database records.
4. **Verify algorithms independently.** Freeze the algorithm and test on appropriate, annotated external datasets with subject-level separation and a declared target population. Report sensitivity, positive predictive value, specificity, confidence intervals, class balance, failures/indeterminate outputs, and noise/motion subgroup results. MIT-BIH is one benchmark, not sufficient evidence by itself.
5. **Run device-specific clinical validation.** Use the intended watch, software, wear conditions, population, and synchronized adjudicated clinical reference. Predefine protocol, inclusion/exclusion, endpoints, sample size, and statistical analysis; retain an independent holdout set.
6. **Complete product controls.** Perform software verification/validation, risk management, cybersecurity, usability/human-factors, traceability, change control, complaint handling, labeling, and jurisdiction-specific quality-system/regulatory submission or authorization.
7. **Release only within the validated scope.** A successful build, high benchmark score, literature citation, or user checkbox is not regulatory clearance or proof of diagnostic accuracy.

## Current project status

- **Available now:** bounded 16-field raw-event inspection, per-field normalized trend by event index, immutable raw rows, explicit unverified mapping/clock/calibration flags, and tests that keep decoded samples and physiology outputs at zero/blocked.
- **Not established:** actual ECG sample mapping, trusted watch sample clock, device-specific mV calibration, reference-device agreement, R-peak/RR performance on watch data, AF sensitivity/specificity, or regulatory authorization.
- **Next decisive artifact:** OEM/LifeQ field-and-clock contract, or synchronized watch plus reference-ECG captures from the same sessions with documented device configuration. Until then the app may describe raw-field research inspection, not normal/abnormal ECG or a medical diagnosis.

## Sources

- [FDA: Is the Software Function Intended For a Medical Purpose?](https://www.fda.gov/medical-devices/digital-health-center-excellence/step-1-software-function-intended-medical-purpose)
- [FDA: Signal acquisition / analysis and clinical decision support](https://www.fda.gov/medical-devices/digital-health-center-excellence/step-6-software-function-intended-provide-clinical-decision-support)
- [FDA: Diagnostic ECG Guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/diagnostic-ecg-guidance-including-non-alarming-st-segment-measurement-guidance-industry)
- [FDA: Arrhythmia Detector and Alarm Guidance](https://www.fda.gov/medical-devices/guidance-documents-medical-devices-and-radiation-emitting-products/arrhythmia-detector-and-alarm-class-ii-special-controls-guidance-document-industry-and-fda-staff)
- [FDA: Quality Management System Regulation (QMSR)](https://www.fda.gov/medical-devices/postmarket-requirements-devices/quality-management-system-regulation-qmsr)
- [FDA: Software function intended to provide clinical decision support](https://www.fda.gov/medical-devices/digital-health-center-excellence/step-6-software-function-intended-provide-clinical-decision-support)
- [FDA recognized IEC 60601-2-47 record](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfstandards/detail.cfm?standard__identification_no=41095)
- [FDA recognized IEC 60601-2-25 record](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfstandards/detail.cfm?standard__identification_no=44849)
- [IEC 62304: Medical device software life cycle processes](https://webstore.iec.ch/en/publication/6792)
- [ISO 14971: Risk management for medical devices](https://www.iso.org/standard/72704.html)
- [European Commission MDCG 2020-1: Clinical evaluation/performance evaluation of medical device software](https://health.ec.europa.eu/system/files/2020-09/md_mdcg_2020_1_guidance_clinic_eva_md_software_en_0.pdf)
- [TFDA: Medical-device permit application route](https://www.fda.gov.tw/tc/siteContent.aspx?sid=11638)
- [TFDA: Medical-device software validation guidance](https://www.fda.gov.tw/tc/includes/GetFile.ashx?id=f637553892654662304)
- [TFDA: AI/ML medical-device software technical guidance](https://www.fda.gov.tw/tc/includes/GetFile.ashx?id=f637648030350293426&type=1)
- [WFDB: Signals, samples, and time](https://physionet.org/physiotools/wpg/wpg_3.htm)
- [PhysioNet: MIT-BIH Arrhythmia Database and license](https://physionet.org/content/mitdb/1.0.0/)

## Medical-grade conditions: official-source review (2026-10-10)

This checklist is a readiness plan, not a certification or authorization. “Medical grade” is not achieved by citing FDA/IEEE papers, matching a public database, adding an AI model, or changing the UI. The exact intended use, hardware/software configuration, target market, evidence, quality system, and regulator's applicable pathway must all align.

### Required workstreams and acceptance evidence

| Workstream | Minimum project deliverable / acceptance gate | Current disposition |
|---|---|---|
| Intended use and regulatory pathway | Freeze one narrow claim, intended user/population, use environment, contraindications, and exact jurisdictions; obtain qualified regulatory review and confirm the applicable classification/submission route before making medical claims. | OPEN; no medical intended use or route approved |
| Design and quality system | The responsible manufacturer/sponsor must operate the QMS and design/development controls applicable to its role and market: requirements-to-test traceability, configuration/change control, supplier controls, CAPA/complaints, release records, and post-market surveillance. FDA QMSR became effective 2026-02-02 and incorporates ISO 13485:2016 by reference for applicable finished-device manufacturers; this is not the same as a blanket requirement to purchase an ISO certificate. | NOT ESTABLISHED |
| Risk management | Maintain lifecycle hazard analysis and risk controls, including false reassurance, false alerts, unreadable/poor-contact recordings, stale or mismatched sync, software updates, privacy, and cybersecurity; verify control effectiveness. | NOT ESTABLISHED |
| Software lifecycle and V&V | Classify software safety risk; maintain architecture, detailed requirements, unit/integration/system tests, anomaly records, reproducible builds, release/configuration traceability, and verification/validation proportional to risk. | Partial engineering tests only; no medical-device lifecycle evidence |
| Signal and hardware contract | Verify the complete product measurement chain: sensor/electrodes and analog front end, HAL/payload mapping, source clock/rate, gain/baseline, filters, digital scaling, transmission, units, contact behavior, and supported firmware/configurations, using manufacturer evidence or traceable synchronized bench measurements. AFE/chip specifications alone do not calibrate the retail watch. | NOT MET; mapping/clock/calibration remain unverified |
| Bench performance | Test the complete intended device chain under normal/worst-case conditions against traceable simulators/reference instruments, including amplitude/time fidelity, accuracy, repeatability, motion/contact, edge cases, and failure/unreadable handling. | NOT MET; no Watch 2 Pro paired reference set |
| Algorithm verification | Lock the algorithm and thresholds before evaluation; use adjudicated, representative recordings, subject-level separation, independent holdout, prespecified endpoints, confidence intervals, subgroup/noise analysis, and report indeterminate/failure rates. Development data cannot double as the verification set. | NOT MET on this watch's signal format |
| Clinical/performance evidence | Generate evidence appropriate to the intended claim, risk, and regulatory pathway using the intended device/software and accepted reference. A clinical trial is not universally required for every pathway; determine this with regulatory review before fixing the plan. | NOT MET |
| Human factors, labeling, privacy, cybersecurity | Validate user comprehension/action around results and failure states; document limitations, data protection, threat model, vulnerability handling, update/security processes, and labeling for each jurisdiction. | NOT ESTABLISHED |
| Market authorization and post-market | Complete the applicable regulator conformity assessment/submission/clearance/authorization and maintain post-market monitoring before marketing or labeling the function as a medical device. | NOT MET; no authorization evidence |

### Applicable primary references

- FDA QMSR: https://www.fda.gov/medical-devices/postmarket-requirements-devices/quality-management-system-regulation-qmsr (effective February 2, 2026; ISO 13485:2016 incorporated by reference).
- FDA Diagnostic ECG Guidance: https://www.fda.gov/regulatory-information/search-fda-guidance-documents/diagnostic-ecg-guidance-including-non-alarming-st-segment-measurement-guidance-industry (defines ECG intended-use/performance context; not a watch-specific validation).
- FDA Arrhythmia Detector and Alarm Special Controls: https://www.fda.gov/medical-devices/guidance-documents-medical-devices-and-radiation-emitting-products/arrhythmia-detector-and-alarm-class-ii-special-controls-guidance-document-industry-and-fda-staff (includes algorithm verification on adjudicated data; development database should not also be the performance test set).
- FDA Premarket Submissions for Device Software Functions: https://www.fda.gov/regulatory-information/search-fda-guidance-documents/content-premarket-submissions-device-software-functions (software documentation/testing expectations for submissions).
- ISO 13485:2016: https://www.iso.org/standard/59752.html (medical-device quality management systems).
- ISO 14971:2019: https://www.iso.org/standard/72704.html (medical-device risk management, including software).
- IEC 62304: https://committee.iso.org/standard/38421.html (medical-device software lifecycle processes).
- EU MDCG 2019-11 rev.1: https://health.ec.europa.eu/latest-updates/update-mdcg-2019-11-rev1-qualification-and-classification-software-regulation-eu-2017745-and-2025-06-17_en (software qualification/classification under MDR/IVDR; updated June 2025).
- Example of a distinct device-specific regulatory submission: FDA Apple ECG App 510(k) summary, https://www.accessdata.fda.gov/cdrh_docs/pdf20/K201525.pdf. This is not transferable clearance for this watch or app.

### Independent agent review

Three local agents independently reviewed the regulatory checklist against primary sources. Their consensus: QMSR date is correct but is not a product clearance; ISO 13485/14971 and IEC 62304 do not substitute for device performance validation or authorization; IEC 60601-2-25/-2-47 scopes differ and must be selected for the actual recorder/intended use; not every pathway universally requires the same clinical-trial package; and FDA pathway cannot be assumed to be 510(k). This review is a checklist quality check, not legal advice or evidence that any gate passed.

### Decision and next action

No code-only action can satisfy the missing external evidence or create regulatory authorization. The highest-value next step is to secure the Xiaomi/LifeQ sensor signal contract and conduct a synchronized, traceable Watch 2 Pro/reference-ECG bench study. In parallel, the sponsor must lock one target claim/jurisdiction and establish a QMS with regulatory/clinical ownership. The applicable ECG particular standard must be selected by intended use and recorder type: IEC 60601-2-25 and -2-47 have different scopes, and -2-47 excludes intermittent event recorders. IEC 62304 addresses software lifecycle processes; it does not by itself cover final device validation/release. Until the necessary gates and market-specific authorization are met, retain research-only presentation; do not output medical normal/abnormal, AF diagnosis, validated mV, or “medical grade.”
