# Independent sensor pipeline reviews, 2026-11-08

The owner approved sending a de-identified technical brief. No health values, device identifiers, credentials, or source files were sent. These model responses are review leads, not proof of a device capability or a validated health algorithm.

| Reviewer | Scope | Conversation |
| --- | --- | --- |
| DeepSeek | Data Layer ordering, retries, receipt binding | [review](https://chat.deepseek.com/a/chat/s/6dd712e5-552a-4d23-84da-b96b2863c8e5) |
| Gemini, account u/2 | Android and Health API clock semantics | [review](https://gemini.google.com/u/2/app/cdc9c83a32071d02?hl=zh-TW&pageId=none) |
| Gemini, account u/3 | User-facing data states | [review](https://gemini.google.com/u/3/app/c18f928b5e911b5e?hl=zh-TW&pageId=none) |
| Gemini, account u/1 | Paired-device clock experiment | [review](https://gemini.google.com/u/1/app/949798f719302eb7?hl=zh-TW&pageId=none) |
| Grok | Buffering, ordering, battery | [review](https://grok.com/c/4f2bfdf9-2160-4b1a-abc5-be0be3563938) |
| Claude | History overwrite and false interpretation | [review](https://claude.ai/chat/0eb1a390-8d32-42de-8a9c-e5e7b9f28795) |
| Meta AI | Short result card wording | [review](https://www.meta.ai/prompt/605a323e-df21-49bb-9fa2-9c48f8cf81a1) |

Kimi displayed a service queue limit; the prompt remained unsent. Cursor Agents was accessible but no repository was selected or agent dispatched, to avoid concurrent writes to the active development checkout.

## Findings to verify against code and device

1. A `putDataItem` success is not, by itself, proof that the phone durably merged the snapshot. Inspect the existing wear outbox and phone receipt ledger together before changing retry behavior. The project already has `WearSnapshotOutbox`, `RawSnapshotReceiptLedger`, and regression tests; a generic recommendation to add an outbox would duplicate existing work.
2. A watch callback receipt time and a phone Data Layer receipt time are different observations. Do not use either as a calibrated sample timestamp or as evidence of a physiological result. Test a batched delivery in which two snapshots arrive together and verify the UI labels the watch and phone times correctly.
3. For each raw modality, test sequence A (nonempty final snapshot), B (empty newer session), then replay A. Verify that A remains in bounded history, B cannot replace it as a current health value, and replay cannot rewind the latest valid typed result. Existing tests cover parts of this; compare them with the actual `WearDataProcessor` branches.
4. Compare event timestamp and callback elapsed clock across a short capture, reboot, charging and wearing transitions. A small future offset must remain recorded as evidence rather than silently becoming timestamp zero. Reviewers proposed numerical thresholds, but these are hypotheses until measured on this watch.
5. UI must distinguish a current typed result, a historical raw capture, a failed measurement, and an OEM activation state. Raw event counts cannot imply ECG rhythm, BioZ interpretation, temperature in degrees, or calibrated SpO2.

The DeepSeek response included third-party pages alongside official Android documentation. Verify claims about Data Layer guarantees in primary Android documentation and in this code before adopting them. The other models saw only the brief, not source code or the paired devices, so their findings are candidate tests rather than confirmed defects.
