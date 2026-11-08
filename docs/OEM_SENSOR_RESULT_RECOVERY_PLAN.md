# PPG / HRV / ECG / BioZ / temperature result recovery

Owner: 王宥傑. Evidence date: 2026-11-08. This plan is for the AILISA phone and Watch 2 Pro data path; it does not assert that OEM clinical algorithms or activation have been recovered.

## Observed state

- The phone's `raw_sensor_results` has historical ECG (2,971 events), PPG (8), BioZ (413), body-temperature raw (99), and thermometer (2) captures. These counts prove callback/storage activity, not a physiological interpretation.
- All five stored `*_time` fields are `0`. ECG, BioZ, body-temperature raw, and thermometer have `*_timestamp_source=sensor_event_timestamp_ahead_of_capture`; PPG has `sensor_event_timestamp_missing`. Their `*_published_at` and session times exist but are receipt/session times, not sample times.
- In retained metadata, 279/279 ECG event timestamps were about 0.125–0.156 s ahead of callback elapsed time; 2/2 thermometer events were about 0.095 s ahead; 96/99 body-temperature events and 319/324 BioZ events were also ahead (maximum observed BioZ lead 2.378 s). PPG 8/8 event timestamps were zero. This explains the current strict rejection, but does not establish a vendor clock conversion.
- `SensorEventEpochTimeResolver` returns `0` for those clock failures. `WearDataProcessor` writes that `0` to `${kind}_time`. This is a demonstrated path to the freshness/UI disappearance; confirm the exact affected screen with a regression test before attributing every vanished value to it.
- The OEM decompile shows separate raw collection and typed result paths: PPG/BioZ diagnostic collection versus processed service results; ECG sensor candidates enter a native analysis path; temperature service `20500` returns a typed float. See the two OEM source-trace documents. None supplies an AILISA raw-to-clinical conversion.

## Agent execution plan

1. **Freeze evidence:** Record app version, sensor type, event count, timestamp-source reason, session ID, receipt time, and UI state for one affected capture. Do not export physiological values into a public issue or external AI prompt.
2. **Find clock cause:** Compare event `timestamp` and callback `elapsedRealtimeNanos` from the same callback, and inspect the sensor's clock domain. Verify whether it uses an OEM clock, a future timestamp, or a missing timestamp. Do not assume the Android elapsed-realtime clock for a vendor stream.
3. **Repair persistence:** Keep raw events append-only while retained. Store the latest attempt separately from the last usable dated observation. A zero-time or empty new snapshot must not erase that prior observation; mark it historical and preserve its original time and source.
4. **Repair user display:** Show a concise current result only when its source, unit, timestamp, and quality gates pass. Otherwise show the prior dated result as history and a concrete repeat/sync action. Keep counts and clock errors in engineering details.
5. **Cross-check OEM:** Match an OEM typed result and AILISA capture by actual time, sensor, and measurement session. Do not apply OEM four-byte result parsing to public HAL raw arrays or treat an OEM header as a calibration map.
6. **Regression gate:** Test valid → invalid-time, valid → empty-session, late previous-session packet, reboot/time change, and repeated sync. Confirm PPG, ECG, BioZ, and temperature independently. Build mobile and Wear; test the actual screen on paired hardware when available.

Done means the last known usable result remains visible with its original date, a new failed attempt is reported separately, and no raw event count is presented as a health finding. A calibrated or rhythm result requires its own evidence and validation gate.

## Independent review roles

The local agents own implementation, code review, OEM parity, clock-domain audit, UX review, and release audit. The project has at most four concurrent agent slots including the coordinator; additional reviews run in batches. External models are optional independent reviewers and receive only a sanitized question, never owner health values, source files, credentials, or device identifiers. Roles: Claude challenges the root-cause and code path; DeepSeek reviews queue and producer/consumer behavior; Gemini `s4140666jpn` checks Android/OEM primary sources; Gemini `jay19821026` checks failure classification; Gemini `jaysato1026` designs disconfirming tests; Grok challenges system/performance assumptions; Kimi checks research citations. A role assignment is not evidence that an external service was contacted or endorsed the result.
