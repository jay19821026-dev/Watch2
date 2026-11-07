# Xiaomi Health Bridge 4.0.0 test 15 / 測試版 15

作者 / Author: 王宥傑 (Wang You-Jie)

## 中文

- 依已安裝的 Xiaomi Health 手錶 App 反組譯結果，補充血氧與皮膚溫度的資料路徑：原廠服務 `20485` 回傳整數血氧結果，`20500` 回傳浮點溫度結果；它們與 Android 公開原始感測器事件不同。
- 手錶原始血氧頁新增貼合皮膚、收緊錶帶、手腕保持靜止的操作提示。原始溫度頁新增佩戴至少十分鐘、保持乾燥、避免充電及高耗電操作後立即量測的提示。這些提示不會把未校準的原始值變成百分比或攝氏溫度。
- 新增[中英文原廠血氧與溫度資料路徑對照](OEM_SPO2_TEMPERATURE_SOURCE_TRACE.md)，列出封包型別、解析分支、手機端紀錄來源，以及尚未驗證的界線。延續 test 13 的佩戴條件及 test 14 的引導式呼吸練習入口。

## English

- The installed Xiaomi Health watch APK separates typed service results from public Android raw sensor events: service `20485` delivers an integer SpO₂ result and `20500` a float temperature result. Their result decoders are not a contract for the raw HAL fields.
- The watch raw-SpO₂ page now reminds users to keep the strap snug and wrist still. The raw-temperature page adds the OEM's preparation guidance: wear the watch for at least ten minutes, keep the watch and wrist dry, and avoid measuring while charging or immediately after high-power use. These steps do not calibrate raw values or assign `%`/`°C`.
- A [bilingual source trace](OEM_SPO2_TEMPERATURE_SOURCE_TRACE.md) records service types, decoding branches, phone record paths, and limits. Test 13's wear-state gate and test 14's guided-breathing shortcut remain included.

## Downloads / 下載

| Device / 裝置 | Asset | SHA-256 |
|---|---|---|
| Android phone / 手機 | `XiaomiHealthBridge-Mobile-4.0.0-vc163-test15-debug.apk` | `9a7cbfd505383068f3696f78a59e0edcfc218fa64fbbbe798f89cc11a5074b87` |
| Wear OS watch / 手錶 | `XiaomiHealthBridge-Wear-4.0.0-vc163-test15-debug.apk` | `8324a640e44ad420f0b598a238b322020d8e644de61700094109dd6d92ee34a1` |

Both are debug-signed sideload APKs with package `com.jaysato.xiaomihealthbridge`, versionCode 163, and the same local Android debug certificate SHA-256 `0c35a370032bfe706ef54ec6a789bcdc7e53d19c94b444fa6b67027a3cb8750f`. Both unit-test suites and APK builds passed; manifest/signature checks passed; both APKs installed in place on the paired phone and watch on 2026-11-08. No new live worn/off-wrist SpO₂ or temperature session, complete OEM breathing session, or bidirectional sync result was verified. These are not Play Store releases. Separately signed Play candidate AABs have now been built from this source with distinct 164/165 version codes, but have not been uploaded or enrolled in Play; the older test 12 candidates are superseded.

See [the bilingual install and test guide](4.0_TEST_INSTALL.md), [OEM feature audit](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), and [dataset/paper use map](ALGORITHM_DATASET_PAPER_MAP.md).
