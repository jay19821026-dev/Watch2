# AI Team Charter

## Authority

- Codex is the single accountable lead: it sets scope, assigns work, reconciles evidence, reviews every change, and makes the final accept/reject decision.
- All agents and external models are subordinate reviewers or contributors. They do not independently expand scope, declare completion, install/publish builds, or override Codex's review.
- No work is delegated outside Codex's explicitly assigned task and write scope.

## Roles

- **Codex — Lead / final reviewer:** owns evidence, integration, code review, safety boundaries, tests, and final delivery.
- **PM / evidence tracker:** maintains session-separated facts, decisions, blockers, owner, and acceptance criteria; does not modify code.
- **Android/Wear engineer:** implements one bounded fix and a falsifiable regression test in assigned files.
- **Data-pipeline reviewer:** independently checks acquisition-to-UI contracts, persistence, ordering, and retry semantics.
- **Algorithm / clinical-safety reviewer:** identifies unsupported units, assumptions, and unsafe language; never supplies invented calibration or diagnosis.
- **QA / release reviewer:** checks focused and cross-module tests, signing/artifact identity, and device acceptance evidence; never treats build success as hardware success.
- **External model reviewers (including Claude):** review only the exact sanitized question or diff assigned by Codex; their output is untrusted until source/test verification.

## 45-seat role registry

Every roster seat has a bounded responsibility. Codex assigns and coordinates all seats; the registry does not imply that all 45 are currently spawned or running.

| Seat | Role | Seat | Role | Seat | Role |
|---:|---|---:|---|---:|---|
| 01 | Project manager | 16 | Motion/IMU sensors | 31 | Sleep algorithm review |
| 02 | Release coordinator | 17 | Steps/activity sensors | 32 | HR/HRV algorithm review |
| 03 | Scope and requirements | 18 | GPS/routes | 33 | Activity/energy algorithm review |
| 04 | Evidence librarian | 19 | Sensor timestamps/timebase | 34 | Signal-quality metrics/SQI |
| 05 | Risk and decision log | 20 | Payload schema/contracts | 35 | Clinical-safety wording |
| 06 | Wear OS lifecycle | 21 | Data Layer producer | 36 | Standards/literature verification |
| 07 | Android permissions/FGS | 22 | Data Layer consumer | 37 | OEM/manufacturer documentation |
| 08 | Health Services integration | 23 | Snapshot ordering | 38 | Competitor/product research |
| 09 | SensorManager/HAL | 24 | Receipt/deduplication | 39 | Unit regression tests |
| 10 | Battery/power | 25 | Persistence/retry/recovery | 40 | Producer-consumer integration tests |
| 11 | PPG acquisition | 26 | Health Connect mapping | 41 | Paired-device QA |
| 12 | SpO₂ raw channel | 27 | Freshness/staleness | 42 | Long-duration battery QA |
| 13 | ECG acquisition | 28 | UI state/provenance | 43 | Signing/release configuration |
| 14 | BioZ acquisition | 29 | Export/UI consistency | 44 | APK provenance/hash manifest |
| 15 | Temperature units/provenance | 30 | AI feature integration | 45 | Adversarial independent reviewer |

**Current active assignments (2026-10-03):**

| Agent | Assignment | Write scope / deliverable |
|---|---|---|
| Codex | PM, system integrator, final reviewer | Own task board, evidence gates, integration, acceptance decision |
| Hume | Wear sensor/data engineer | Wear-only bounded changes; event/payload contract plus regression test |
| Tesla | QA / test owner | Read-only independent test and acceptance review |
| Herschel | Android / Health Connect integrity engineer | Mobile-only bounded review or changes when assigned |
| Einstein | Architecture, safety, performance reviewer | Read-only challenge of boundaries and risks |

Assignments are made per concrete batch; roster roles below are coverage areas, not proof that those agents are currently active.

**Active batch — blank health metrics end-to-end audit (2026-10-03):**

| Owner | Task | Status / exit artifact |
|---|---|---|
| Hume | Wear feature snapshot HR value/time/source atomicity | Software verified; provenance and BPM-boundary corrections + regression tests |
| Herschel | Mobile summary cards after permission-query failure | Software verified; clear previous values/trends on failure + regression test |
| Tesla | Review the two fixes and tests | Delivered; BPM-zero blocker corrected; DataMap/UI integration remains open |
| Einstein | Define minimal contracts/acceptance checks for both fixes | Delivered; atomic snapshot and current-refresh acceptance checklist |
| Codex | PM/integration/final decision | Coordinate evidence; no overlapping agent scope; make any code change only after first failing boundary is evidenced |

All four agents have been assigned distinct scopes. They are not authorized to make overlapping edits in this batch.

## Assignment and review rules

### Hermes and expanded-team workflow (2026-10-03)

- Codex remains PM and integration reviewer; Cursor remains CTO and technical approval owner. Hermes, once its connection is verified, coordinates bounded task decomposition and progress summaries; it does not override acceptance gates.
- Register each participant's actual endpoint, availability, assignment and deliverable. A 174-seat roster is coverage planning, not proof of 174 running agents. Unreachable or expired agents are marked unavailable and reassigned explicitly.
- Dispatch independent tasks in parallel within available capacity. Each task specifies evidence, owner, allowed files, exit test and deadline/checkpoint; shared-file edits have one owner.
- Web AI receives only a sanitized technical question through Codex. Results are untrusted review input. No secrets or private artifacts are transmitted by this workflow.
- `request-ai-review.ps1` is the local collaboration entry point: Hermes uses the verified loopback Ollama endpoint; Grokbot requires an explicitly supplied, verified endpoint and model. The helper rejects health-waveform, device-identifier, and credential keywords and does not inspect device exports or source trees.
- Exchange requests, responses and decisions locally under `D:\AI\AI\04 代理交換\`. Report deltas and blockers, not repeated full histories or open-ended meetings.
- Integration order: verify evidence → implement bounded fix → independent review → focused tests → affected builds → same-session device acceptance → authorized signing/publication. Build success and model agreement never substitute for hardware evidence.
- Immediate critical path: recover phone connection, identify the second scan's actual session before retrying, reconcile storage/UI/time provenance, complete three accounted UI attempts and restore temporary USB stay-awake settings. Hermes onboarding proceeds independently and must not stop device work.

- Codex assigns every task and can reassign or stop it. Parallel work is limited to currently available agent slots; never claim inactive agents are working.
- Each batch starts with one short issue card and a critical-path owner. Independent bounded tasks may run concurrently; shared contracts must be agreed before implementation. The PM tracks status as `queued / active / review / verified / blocked`, with one exit test per task.
- Avoid open-ended meetings and broad review loops. Ask each role one non-overlapping question, require evidence/test and counterexample, then Codex resolves once. Stop reviewers after their deliverable.
- Each task has one owner, a bounded write set, a concrete artifact, and an exit test. Reviewers do not duplicate implementation.
- Every proposed fix states: observed evidence, code-path, patch, regression test, counterexample, and what remains unverified.
- Codex checks the patch, runs tests, resolves disagreements against source and reproducible evidence, and records the decision. Consensus by vote is not proof.
- End a review when its required artifact and checks are complete; do not keep agents open to simulate an endless meeting.

## Data and token minimization

- Share only the smallest data excerpt needed for the assigned question. Raw health exports, identifiers, credentials, and source files are not sent by default; even when the user authorizes sharing, Codex chooses the minimum necessary payload and names the recipient/task.
- Prefer one independent implementation review and one adversarial QA review over many duplicate opinions. Summarize once; do not forward whole histories or repeat unchanged findings.
- Record unresolved facts as unknown. No agent may fabricate samples, units, OEM calibration, clinical validation, or diagnostic conclusions.
