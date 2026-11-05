# AI analysis regression review

## Confirmed current-source defects and gaps

- AlgorithmicInsightsEngine returns stress, bodyEnergy, recovery and readiness as null unconditionally. LocalAiHealthAnalyzer requires stress and body energy to be present before producing a complete overall score. Consequently production insights cannot reach that complete-score path, even with readable sleep/activity evidence. Unit tests using manually populated Insights do not prove production score availability.
- AiHealthAnalysisActivity catches calculation, rendering and synchronous publication errors. Previous patch separated publication from calculation/render failure and added error logging. The exact user-reported exception remains unobserved; no matching exception appeared in the bounded phone log inspected today.
- Phone is currently Dozing, so no fresh foreground analysis was reproduced. Stored summary diagnostics contain 14 complete sleep intervals and a 22.8-hour historical-session fallback, no read errors or missing permissions, and zero oxygen records for the recorded day. These are retained query results, not a new measurement or today's sleep total.
- A 70/100 rule score cannot establish measurement accuracy or replace missing physiological inputs. No historical source snapshot has yet established when each reported previous value changed.

## Required repair

## Repair checkpoint

- UI redesign: two-column measurement cards, explicit text/color status, accessible tap-to-detail dialogs for values and risk rows, horizontal external-AI actions, and shorter main-screen copy. Numerical calculations unchanged. Large font values use wrapping and dynamic card height.
- Debug assembly passed (`ai-card-redesign.log`), phone installation returned Success. Post-install UI dump/navigation became unavailable before reaching the redesigned AI page; no visual acceptance claim is made.

- Restored direct main-page presentation of blood oxygen, PPG, ECG, BioZ, cuff BP, glucose availability and rhythm/risk findings. Hydration is read from today's Health Connect records/aggregate with permission/error isolation and single-origin/raw-total consistency checks. No glucose-record reader or synthetic physiological values were added.
- Added a peripheral vascular research status, not a PAD classifier. Research uses finger/toe/multiple-site data and reference ABI/ultrasound, not proof of smartwatch arterial blockage detection: https://pmc.ncbi.nlm.nih.gov/articles/PMC6165367/ and https://www.ahajournals.org/doi/full/10.1161/CIR.0000000000001251 .
- Manufacturer confirms MAX86178 has separate simultaneously operating ECG and BioZ channels, with FIFO timing for synchronization: https://www.analog.com/en/products/max86178.html . This does not establish watch electrode mapping, synchronization, or a BioZ-to-ECG conversion. UI explains the separate channels; no fusion algorithm is claimed.
- Mobile unit tests and Debug assembly passed (`ai-risk-ui-final.log`); mobile installation returned Success. New risk/hydration UI remains to be verified live.

- Simplified AI UI: large available values with local record timestamps, concise conclusion/actions, collapsible team/source/activity/risk sections, and formula details moved out of the primary summary. Existing measurement and model calculations unchanged by this presentation patch.
- Mobile tests and Debug assembly passed (`ai-simple-ui.log`); phone replacement returned Success and MainActivity launched. Post-install AI-screen navigation was not confirmed, so the new layout is not fully accepted on hardware. This is not proof of restoring the original historical algorithm.

- Foreground device reproduction via MainActivity → My → AI health analysis completed successfully and displayed resting HR 73 bpm. No ai_analysis_diagnostics exception file existed in this run; this does not explain previous failures.
- AI sleep scoring uses recent/qualified intervals, unlike Home's retained historical-session summary. Added a separate seven-day single-selected-origin interval-union summary to AI UI so readable history is visible without replacing stage-qualified or recent scoring inputs. Corrected the obsolete 'model not established' message to 'waiting for paired data'. Original early algorithm restoration remains unverified because no historical source baseline was located.

- Added ResearchCompositeModel personal-hrv-sleep-rule-v1 and wired its four outputs into production Insights and Wear publishing. These are newly defined exploratory rule scores, not recovered historical algorithms or validated physiological measures. Formula weights are product rules, not literature-derived clinical coefficients.
- Requires at least three same-origin HRV record days for the existing personal baseline; latest HRV must be within 36 hours and within 24 hours of a same-origin recent sleep record. Sleep-session duration is an explicitly labeled proxy when stages are incomplete. Missing evidence keeps affected outputs unavailable.
- Tests cover exact deterministic outputs, unpaired/missing/nonfinite inputs and monotonic response to HRV/sleep reductions. Initial full mobile/Wear suites and Debug/Release assemblies passed; final verification after copy correction pending.
- AI calculation/render exception class, bounded message and timestamp are now stored in ai_analysis_diagnostics for device readback. The previously reported exception has not been reproduced.
- Final mobile/Wear unit suites and mobile Debug/Release build passed after correcting two stale display-text assertions. This validates code behavior, not current-user input coverage or visible scores on the phone.

- AI page now marks successfully calculated individual results as analysis completed independently of unavailable composite score. Resting HR, HRV and recorded sleep-session duration are displayed directly with available record times and duration semantics.
- This repairs misleading success presentation. It does not implement the four missing composite models or establish the exact exception reported by the user. Historical score restoration and complete sensor output remain unresolved.

- Report completed individual analyses independently from composite-score availability.
- Implement explicit, tested research composite models with stated inputs and versions rather than assuming test fixtures represent production outputs.
- Reproduce the failure through the foreground UI, preserve the precise exception and query statuses, and compare each missing field with its underlying saved evidence.
- Reconcile Health Connect sleep-session duration with stage-derived sleep semantics before using it as a model input.
