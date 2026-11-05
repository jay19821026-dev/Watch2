# Mobile 2.5.0 UI revision

## 2.6.0 visual system

- Three persistent tabs: Summary, Health, Devices. Switching tabs retains scroll position and the selected tab survives activity recreation.
- Summary is redesigned around a decorative vector watch portrait, linked metric rows, and secondary insight disclosure. The portrait is illustration, not a measurement or progress gauge.
- Shared layered surfaces, typography, restrained category colors, ripple feedback, and short finite page transitions (respecting disabled system animations).
- Device diagnostics, permission management and expandable tool groups are separate from health exploration. Full diagnostic text remains accessible in dialogs.
- Detail charts, summary cards, source selectors, history rows, dialogs and permission rationale now share the visual system.
- UI changes do not delete or rewrite health records. The previously documented data reconciliation limitations remain.

- Health dashboard: compact neutral cards, vector-style metric glyphs, ripple interaction, expandable device/data tools. Existing tools remain available.
- All eight metric details use a calendar day/week/month selector, previous/next navigation, date picker, actual time-axis chart, selectable values, summary grid and paged history disclosure.
- Heart-rate chart renders the observed minimum/maximum within time buckets; selection shows an actual sample, not a synthetic interpolated value.
- Detail reads follow Health Connect page tokens and cancel obsolete requests. Empty periods remain empty. Source selection does not combine different applications.
- Small calorie records and resting-heart-rate records above 100 are no longer hidden by the detail UI. No Health Connect records are deleted by these changes.
- Interval metrics are distributed proportionally across hour/day boundaries and explicitly labelled as estimates. Same-source overlaps may still inflate totals; this is not a complete reconciliation engine.
- Dashboard remains a seven-day overview using its existing query path. It may differ from the source-filtered detail page; full dashboard pagination and aggregation reconciliation remain follow-up work.
- No invented training-zone durations, sleep stages, goal progress, or clinical status indicators are shown.

Verification: mobile assembleDebug and lintDebug; device install preserving app data; screenshots under artifacts/redesign-*.png. Device UI checks are limited to the screens actually captured, not an assertion that every control and device configuration has passed.
