# Project workflow

- Before planning or changing code, read `docs/ACTIVE_PROJECT_GOAL.md`. It is the persistent source of truth for product scope, priorities, acceptance gates, and non-negotiable evidence boundaries. Update it when the user changes the project objective.
- Keep responses and routine log summaries concise. Inspect targeted files and bounded log windows; do not dump the whole repository or full device logs into context.
- For Android/Wear OS sensor, sync, battery, or health-calculation work, follow `.agents/skills/healthbridge-debugger/SKILL.md` before changing code.
- Diagnose the pipeline in order: acquisition → payload/units → quality/calibration → watch snapshot → Data Layer → phone storage → freshness → UI. Separate missing, rejected, uncalibrated, stale, and hidden data.
- Never fabricate measurements, units, OEM calibration, clinical validation, or diagnosis. Use research-only labels unless the device-specific evidence supports a physiological result.
- Prefer targeted tests first, then both mobile and Wear unit tests and release builds for cross-device changes. Summarize results; do not paste build logs.
- Do not publish, upload, or install builds unless explicitly requested. A successful build is not proof of hardware measurement success.
- Roles: Cursor agent = CTO; Codex = project manager; Grok bot = Codex assistant. All cloud/external-AI Q&A goes through the Codex agent; Cursor may only spawn local subagents. Exchange all Markdown results locally in the Obsidian vault `D:\AI\AI\04 代理交換\` (see its README).
- Use external AI only for independent, sanitized technical review; never transmit raw health exports, device identifiers, credentials, or source files without explicit authorization.
