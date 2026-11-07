# Xiaomi Health Bridge 4.0.0 test 13 / 測試版 13

作者 / Author: 王宥傑 (Wang You-Jie)  
日期 / Date: 2026-11-08

## 中文

- 根據已安裝 Xiaomi Health 手錶 APK 的體成分流程，確認原廠量測需要按鍵電極接觸與個人資料；原廠服務結果不能直接從公開 BioZ 原始事件換算。
- 手錶 BioZ 研究頁與手機遠端觸發路徑現在都要求近期 Android 離腕感測器回報「已佩戴」，且有至少 40 筆原始事件，才標示原始擷取完成。離腕或狀態未知時保留原始證據，但不顯示完成。
- 佩戴狀態不等於手指接觸電極，也不提供體脂百分比、阻抗歐姆值或醫療結論。40 筆與 60 秒時窗是本專案條件，並非小米原廠演算法。
- 延續 test 12 的手機睡眠分期完整性標示、來源欄位，以及 ECG 原始研究預覽的保守條件。

## English

- The installed Xiaomi Health watch package shows that its body-composition flow depends on button-electrode contact and profile data. Its typed service result cannot be reconstructed from public raw BioZ events alone.
- The watch BioZ research page and phone-triggered capture now require a recent Android on-body event and at least 40 raw events before reporting raw capture complete. Off-wrist or unknown wear state retains bounded raw evidence without a completion claim.
- On-body state does not verify finger contact, physical impedance units, body-fat percentage, or a medical result. The 40-event and 60-second rules are this project's own gates.
- Retains test 12's partial-sleep-stage labels, source fields, and conservative ECG research preview.

## Downloads / 下載

| Device / 裝置 | Asset | SHA-256 |
|---|---|---|
| Android phone / 手機 | `XiaomiHealthBridge-Mobile-4.0.0-vc161-test13-debug.apk` | `0ac12cd402ce54203de92cecd7071dbb05e49ecb94b36211eab875cb247cfc66` |
| Wear OS watch / 手錶 | `XiaomiHealthBridge-Wear-4.0.0-vc161-test13-debug.apk` | `60b9f349df2367439ac788ef7b90b0ef7d03e700401ecbe8d052138cff3b0044` |

These are debug-signed sideload APKs with package `com.jaysato.xiaomihealthbridge`, versionCode 161 and certificate SHA-256 `0c35a370032bfe706ef54ec6a789bcdc7e53d19c94b444fa6b67027a3cb8750f`. They are not Play releases. The phone and watch APKs passed unit tests, built, passed manifest/signature checks, and installed over the paired devices. Live worn/off-wrist BioZ and phone/watch sync acceptance remain pending. The earlier signed Play candidate AABs do not include this change and should not be uploaded.

See the [bilingual install/test guide](4.0_TEST_INSTALL.md), [OEM feature audit](WATCH2PRO_4_0_OEM_FEATURE_AUDIT.md), [algorithm/dataset/paper map](ALGORITHM_DATASET_PAPER_MAP.md), and [Android off-body sensor contract](https://developer.android.com/reference/android/hardware/SensorEvent#TYPE_LOW_LATENCY_OFFBODY_DETECT).
