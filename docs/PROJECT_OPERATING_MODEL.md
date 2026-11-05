# Project Operating Model

## Purpose

Deliver a reliable Xiaomi Watch 2 Pro + Android health-data bridge with transparent provenance, safe sensor limits, low battery impact, and reproducible release evidence. A successful build is not proof of a valid measurement. Never promise 100% sensor success or medical-grade accuracy without device-specific clinical evidence.

## Decision and ownership

- **Product Manager (PM):** owns scope, prioritized backlog, acceptance criteria, dependencies, and user-facing progress. PM does not override clinical, privacy, security, or platform gates.
- **Product Owner / Domain Analyst:** translates user needs into workflows, requirements, non-goals, and measurable outcomes; resolves domain ambiguity with the PM before implementation.
- **Project / Delivery Manager:** tracks owners, milestones, blockers, dependencies, and handoffs; keeps one current status and escalates schedule risk. In this small project, the PM also performs this role.
- **UX Researcher / Product Designer:** validates user journeys, information hierarchy, watch-sized interaction patterns, and understandable empty/error states.
- **Accessibility Specialist:** checks contrast, font scaling, touch targets, semantics, and assistive-technology navigation across phone and watch.
- **Software Architect:** owns module boundaries, platform choices, data contracts, lifecycle design, and architecture decision records. The Technical Lead covers this role unless the task needs an independent review.
- **Technical Lead / Integrator:** owns architecture, evidence chain, cross-module design, merge decisions, and final technical recommendation.
- **Android Phone Engineer / Lead:** implements mobile screens, Android permissions/lifecycle, Health Connect, persistence, notifications, and phone-side integration.
- **Backend / Integration Engineer:** owns any server, API, authentication, retries, and external-provider integration when such components exist; otherwise marked not applicable (the current health pipeline is designed to remain local).
- **Wear OS Engineer / Lead:** implements watch screens, sensor APIs, Health Services, foreground services, Data Layer, power behavior, and watch-side diagnostics.
- **Data Pipeline Engineer:** owns acquisition-to-display lineage: acquisition → payload/units → quality/calibration → watch snapshot → Data Layer → phone storage → freshness → UI.
- **Algorithm & Research Lead:** reviews formulas and primary/peer-reviewed evidence; records population/device assumptions and uncertainty. No coefficient is copied across devices without validation.
- **Data / Analytics Engineer:** owns event schemas, provenance, data-quality checks, migration safety, and reproducible analysis; never turns absent or raw values into fabricated health measurements.
- **Technical Writer / Developer Experience:** maintains setup, permission, troubleshooting, release, and user-facing help documentation so each feature has an operable path.
- **Privacy & Security Reviewer:** checks health-data minimization, permissions, exports, secrets, threat surface, and external-review sanitization.
- **Reliability / Observability Engineer (SRE):** defines bounded diagnostics, crash/ANR visibility, service health, recovery behavior, and operational evidence without collecting unnecessary personal health data.
- **Battery & Performance Engineer:** checks sampling cadence, listener/service lifetime, wake locks, sync frequency, background work, and drain measurements.
- **UX & Accessibility Lead:** ensures legible watch-sized screens, comprehensible status states, accessibility, and no misleading medical claims.
- **QA / Test Automation Engineer:** creates stage-specific unit, integration, contract, UI, paired-device, regression, and end-to-end test coverage; keeps fixtures synthetic or sanitized.
- **QC / Independent Reviewer:** challenges evidence, reproduces defects, checks regressions and safety boundaries; does not authoritatively approve its own implementation.
- **Build / CI/CD / Release Engineer:** maintains repeatable Gradle/toolchain configuration; owns deterministic builds, lint/test gates, signing/configuration checks, artifact provenance, versioning, release notes, and rollback readiness. Does not publish or install without explicit user authorization.

One person or agent may cover multiple roles in a small team, but each role's checklist remains distinct. Mark backend work not applicable when no backend exists rather than inventing a service. AI reviewers are advisory; the Technical Lead owns decisions and must resolve conflicting advice against source evidence.

## Current role assignment

| Role group | Accountable owner | Boundary |
| --- | --- | --- |
| PM, Product Owner, Delivery Manager | Codex (accountable); Hooke (advisory when available) | Tracks goal, priority, acceptance, blockers and next owner; Codex validates decisions against repository evidence. |
| Architecture, Android Phone, Wear OS, Data Pipeline, Algorithms, Backend applicability | Codex / Technical Lead | Codex owns implementation and integration; backend is not applicable unless a server component is explicitly added. |
| UX Research, Product Design, Accessibility, Developer Documentation | Goodall (advisory); Codex implements | Reviews usability and readability; design claims require screenshots or UI tests when available. |
| QA / Test Automation | Erdos (advisory); Codex runs tests | Proposes regression cases; test/build output remains the acceptance evidence. |
| Platform / Wear runtime | Locke (advisory); Codex implements | Reviews Android/Wear lifecycle and API constraints. |
| Privacy / Security, Battery / Performance, Reliability / Observability | Anscombe (advisory); Codex verifies | Review is advisory; no battery or security claim without measured evidence. |
| QC / Independent Review | Descartes (advisory) | Challenges attribution and acceptance; cannot approve its own implementation or substitute for device validation. |
| Build / CI, Release Management | Codex; user authorizes publication | Repeatable local verification and artifact accounting; public release remains a separate user-approved action. |
| Implementation / first-pass review | Codex + Claude (co-owners per user direction) | Claude may review or implement a bounded change; Codex integrates and owns final correctness and safety review. |
| Independent debugging / build assistance | Cursor Agent (advisory; user-invoked) | Reproduces issues, inspects code paths, proposes or implements bounded fixes, and runs requested local checks; Codex reviews every diff and test result. |

The named agents are role lenses, not a claim that each has executed every task. For each task, record which reviews actually ran and which were unavailable; never infer a review from an assignment alone.

### Cursor collaboration

- Open this repository in Cursor; project guidance is loaded from `.cursor/rules/healthbridge-engineering.mdc` together with this file and the repository `AGENTS.md`.
- Use Cursor for a separate debugging hypothesis, targeted code review, bounded implementation, and local test/build assistance. Give it one concrete issue, expected behavior, relevant files, and acceptance checks; ask it to report evidence and exact commands/results.
- Cursor-produced edits are proposals until Codex reviews the complete diff, checks the health-data safety boundaries, and independently verifies the relevant tests/build. Do not let concurrent agents edit the same files.
- Do not send raw health exports, sensor waveforms, device identifiers, credentials, or account data to Cursor or another external service. Use sanitized excerpts and synthetic fixtures.
- Cursor must not claim device measurement success from compilation, weaken quality/calibration gates to make values appear, commit, install, publish, or upload artifacts unless the user explicitly requests that action.
- Claude and Codex remain responsible for implementation and writing by default; Cursor supplements debugging and construction when assigned, rather than silently taking over ownership.

## Delivery gates

1. **PM intake:** state user problem, measurable outcome, scope, priority, risks, dependencies, and acceptance criteria.
2. **Evidence / diagnosis:** capture bounded logs, app versions, power/charging/wear context, and exact failed pipeline stage. Keep private health samples local unless specifically authorized.
3. **Design review:** Android, Wear, pipeline, privacy/security, battery, UX, and research owners review relevant impact before implementation.
4. **Implementation:** add narrow regression coverage and stage-specific diagnostics. Preserve raw samples where appropriate; never fabricate physiological values.
5. **CI gate:** run targeted tests first, then phone + Wear unit tests, lint, and debug/release builds for cross-device work.
6. **QC gate:** independently inspect the diff, safety/privacy boundaries, failure paths, stale/duplicate ordering, permissions, and UI labels.
7. **Device acceptance:** trigger through app UI, record same-session watch acquisition and phone persistence/freshness/UI evidence. Separate charging-safe checks from wrist-contact checks.
8. **Release decision:** PM confirms scope; QA/QC evidence is green; CI artifacts are reproducible; privacy/security and safety blockers are resolved. The user authorizes installation/publication separately.

## Task record template

For each task, maintain:

- Objective / priority / owner / reviewers
- Baseline evidence and affected pipeline stage
- Acceptance criteria and explicit non-goals
- Changes and regression tests
- CI results and artifact identifiers
- Paired-device result, including conditions and timestamps
- Known limitations / unresolved blockers
- Decision: ready, needs work, or blocked; next owner and next action

## Required task assignment

Every non-trivial task must name one accountable owner and applicable reviewers before work begins. Use this compact assignment block in issue notes or handoffs:

| Workstream | Accountable role | Required review / evidence |
| --- | --- | --- |
| Scope, priority, acceptance | PM | Measurable outcome, dependencies, explicit non-goals |
| Architecture and integration | Technical Lead / Integrator | Producer-consumer impact and decision record |
| Phone app | Android Phone Lead | Permissions, persistence, lifecycle, UI and tests |
| Watch app | Wear OS Lead | Sensor/service lifecycle, watch UX, power and tests |
| Data correctness | Data Pipeline Engineer | Stage-by-stage provenance and freshness evidence |
| Formula claims | Algorithm & Research Lead | Inputs, units, assumptions, primary/literature sources |
| Privacy/security | Privacy & Security Reviewer | Data minimization, permissions, export and secret checks |
| Power/performance | Battery & Performance Engineer | Bounded work and before/after measurement where relevant |
| Usability | UX & Accessibility Lead | Legibility, accessibility and understandable failure states |
| Verification | QA Engineer | Focused regression plus device path when applicable |
| Independent challenge | QC / Independent Reviewer | Review evidence and failure paths independently of author |
| Build/release | CI/CD & Release Engineer | Reproducible artifacts, gates, signing and release decision |

For small changes, roles may be combined, but PM scope, implementation ownership, independent QC, and release authorization must remain explicit. Do not assign an unavailable agent or imply an AI role has executed a review when it has not.

Every task update must include this short PM status line: `Goal | Owner | Acceptance | State | Blocker/next action`. Close a task only when acceptance evidence is attached; a merged patch or green build alone does not close hardware measurement work.

## Current advisory assignments (2026-09-27)

| Agent | Role | Review output / status |
| --- | --- | --- |
| Hooke | PM / scope and acceptance | Confirmed that installation/build evidence is not biometric acceptance; defined unplugged UI-scan acceptance gates. |
| Erdos | Sensor-pipeline QA | Flagged that old duplicate/older HR rejections need sequence replay tests; treated it as a hypothesis, not a proven failure. |
| Locke | Wear runtime / Android platform | Flagged uncaught sensor-registration permission exceptions; this turn added a per-channel `permission_denied` failure path and cleanup. Runtime denial scenario remains untested. |
| Goodall | Mobile UX / data trust | Flagged inconsistent MAX/PPG pulse and HRV gates across mobile health/result surfaces; mapping/timebase/sample/quality checks were tightened and tests added. |
| Anscombe | Privacy / battery | Confirmed no active app notification at inspection and no drain-rate evidence; provided a controlled unplugged comparison protocol. |
| Descartes | Independent QC / adversarial review | Rejected any claim of full measurement success; required same-session event→snapshot→phone/UI proof and reiterated calibration boundaries. |

All six reviews were advisory, used only sanitized evidence, and did not receive raw health exports, credentials, or device identifiers. Codex remains accountable for code, merge decisions, safety review, and acceptance.

## Current handoff

- **Current objective:** fix the watch sensor-suite startup/diagnostic and phone optical-data status mismatch without weakening calibration gates.
- **Completed this turn:** clarified mobile PPG timebase vs point-count messages; blocked derived optical metrics unless channel mapping and sample timebase are verified; persisted watch full-scan startup/HR permission failures; prevented stale wear-qualified state in charging-safe scans; full mobile/Wear tests, lint, debug and release builds passed; local release APKs were installed on paired devices.
- **Not yet accepted:** no post-install UI-triggered sensor suite was run. The watch is USB-powered/charging (65%), so wrist-contact sensor verification would be invalid. Existing export is older, with unknown PPG clock/channel map, raw-only SpO₂/ECG/BioZ and no independent BioZ respiration path. No clinical calibration claim is made.
- **Next action:** when watch is off charger and worn snugly, launch Sensor Suite from the watch UI; capture a fresh same-session acquisition→snapshot→Data Layer→phone storage→freshness→UI trace. Verify HR permission behavior and phone optical explanation separately.
- **Release posture:** local QA install only; not a public release. Keep target and version metadata explicit before a distributable release.
