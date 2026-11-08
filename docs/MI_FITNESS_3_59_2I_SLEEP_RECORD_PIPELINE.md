# Mi Fitness 3.59.2i sleep record comparison / 睡眠紀錄對照

作者 / Author: 王宥傑 (Wang You-Jie)  
檢查日期 / Reviewed: 2026-11-08

## Evidence / 來源

This is a static trace of the installed Mi Fitness 3.59.2i phone APK, SHA-256 `D7B0A2B45E12899D91F9323BD59DAD8E3531C490CBF1BC83345F5631929CFBE2`. Local reconstructed sources are outside this public repository at `MiFitness-phone-installed-3.59.2i/decompiled/sources`. No private sleep database, raw sleep bytes, OEM library, or user record was copied into the app.

這是已安裝手機 APK 的靜態流程分析，不代表已取得本人的原廠睡眠紀錄，也不能由程式結構推論分期模型的醫療準確度。

| Stage / 階段 | Reconstructed source / 反組譯來源 | Observed behavior / 觀察 |
|---|---|---|
| Device report parsing | `com/xiaomi/fit/fitness/device/mi/parse/utils/SleepSegmentUtils.java::computeSleepSegmentReport` | Requires `AllDaySleepParseData.sleepSrcBytes`, passes them to `MiDevSleepAlgo.parseSleep`, and rejects empty parsed summaries. Parsed segments with zero `sleepDuration` are omitted. |
| Native stage algorithm | `com/xiaomi/fit/fitness/sleep/algo/MiDevSleepAlgo.java` | Loads `jni_xiaomi_sleep_algorithm`, requests a native parse, and waits for its callback. The algorithm, training data, score calibration and stage validation are not disclosed by this Java trace. |
| Typed report | `SleepSegmentUtils.java::convertToSegmentReport` | Stores bed time, wake time, **sleep duration**, light/deep/wake durations and wake count in distinct fields. REM duration is added only when `hasRem`; per-stage intervals are added only when `hasStage` and a stage array exists. Breath quality and breathing rate use separate state fields. |
| Context and provenance | `SleepSegmentUtils.java::convertToSleepSegmentReport` | Adds time-zone offset, device bed/wake metadata, sleep efficiency, HRV, and conditional HR/SpO₂ summaries. These are source-specific report fields, not evidence that Health Connect or our raw sensors expose the same values. |
| Device write-back | `SleepSegmentUtils.java::writeBackSleepToDevice` | Uses a separate capability-dependent one- or seven-day write-back path. Its existence does not grant this app access to the OEM protocol. |

The readable `repo.healthconnect.biz.daily.SleepSyncBiz` in **both 3.53.0i and 3.59.2i** maps internal sleep states `2 → 5` (Health Connect deep), `3 → 4` (light), `4 → 6` (REM), and `5 → 1` (awake); other states become `0` (unknown). If the report's stage list is `null`, it creates one **unknown** stage spanning bed to wake; an empty but non-null list stays empty. These are writer mappings, not a decoded Watch 2 Pro sensor algorithm. The constant meanings were checked against the [AndroidX Health Connect `SleepSessionRecord` reference](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/SleepSessionRecord). / 兩版可讀匯出程式的睡眠狀態映射相同；缺少分期時會用「未知」涵蓋整個就寢區間，不能把未知分期當成實睡時數。

## 4.0 correction / 4.0 修正

Before this change, the phone Sleep page always placed the union of Health Connect session intervals in its large number, even when complete stage intervals could support subtracting awake time. The OEM report's separate sleep and wake durations make that presentation ambiguity clear; its numeric values and native algorithm are **not** reused.

The phone page now uses the project's existing `HealthInsightInputPolicy.sleepStagesCoverSessions` gate. If all selected Health Connect sessions have known, valid, non-overlapping stage intervals covering the sessions, the large number subtracts awake intervals and is labelled as sleep time. If stages are missing, partial, unknown or overlapping, the large number remains the deduplicated session interval and is explicitly labelled as **time span that may include wake**. Per-session rows show the Health Connect source package. No REM/deep estimate is invented. The watch's existing stage-complete display uses the same evidence distinction.

Android's [Health Connect sleep-session guide](https://developer.android.com/health-and-fitness/health-connect/features/sleep-sessions) explicitly makes stages optional and defines awake, sleeping, light, deep, REM and unknown stages. The full-coverage rule is this project's conservative display policy; it is **not** an Android API requirement or an OEM sleep-stage algorithm.

完整分期才可由 Health Connect 時段扣除清醒；分期缺漏或來源重疊時只顯示時段聯集，並標示可能包含清醒。這是本 App 對公開資料的呈現規則，不是移植小米原廠睡眠模型。

## Remaining checks / 待驗證

- On 2026-11-08, after installing test 12 on the paired phone, the Sleep page opened with Health Connect permission. A read-only UI inspection confirmed the partial-stage explanation, the "session span may include wake" label, and source-app fields on recent rows. No sleep value or source package name was printed or saved. This verifies the **partial-stage UI branch**, not the identity or correctness of an OEM Watch 2 Pro export.
- Check the exact Health Connect record provenance and complete-stage branch on a consented real or synthetic record. Static APK code and a partial-stage screen cannot prove a Watch 2 Pro sleep export is available to this app.
- Compare the phone page and watch glance for the same seven-day range after a real sync; avoid declaring a stage-complete result from only one source when other overlapping sources remain.
- The current page does not import Mi Fitness's private `SleepSegmentReport`, sleep score, HRV, breathing or SpO₂ fields. No OEM sleep stage accuracy or clinical claim is made.
