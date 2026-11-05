# Third-party data, literature, APIs, and software

本文件記錄 Xiaomi Health Bridge 使用或參考的外部資料、論文、API 與套件。公開資料庫只供離線研究工具使用，不包含在原始碼或 APK；公開資料與論文不能取代 Xiaomi Watch 2 Pro 的裝置專屬校準或臨床驗證。

This file records external datasets, papers, APIs, and software used or consulted by Xiaomi Health Bridge. Public datasets are used only by offline research tools and are not bundled in the repository or APKs. They cannot substitute for device-specific calibration or clinical validation.

## Public datasets / 公開資料庫

| Source | Project use / 專案用途 | License and citation / 授權與引用 | Boundary / 不代表 |
|---|---|---|---|
| [ECG-ID v1.0.0](https://physionet.org/content/ecgiddb/1.0.0/) | 500 Hz 下尺度無關的頻譜／形態參考；scale-independent spectrum and morphology reference. | PhysioNet ODC Attribution 1.0; DOI [10.13026/C2J01F](https://doi.org/10.13026/C2J01F). | 不是 Xiaomi 校準檔、AF 陰性對照或等效導聯證據。 |
| [MIT-BIH Noise Stress Test Database v1.0.0](https://physionet.org/content/nstdb/1.0.0/) | 與 baseline wander、electrode motion、muscle artifact 做描述性特徵距離比較。 | PhysioNet ODC Attribution 1.0; DOI [10.13026/C2HS3T](https://doi.org/10.13026/C2HS3T); Moody, Muldrow & Mark, 1984. | 最近距離不是雜訊分類器，也不能斷言手錶訊號根因。 |
| [MIT-BIH Arrhythmia Database v1.0.0](https://physionet.org/content/mitdb/1.0.0/) | 以標註節拍離線測試 WFDB XQRS/GQRS 與比對管線。 | PhysioNet ODC Attribution 1.0; DOI [10.13026/C2F305](https://doi.org/10.13026/C2F305); Moody & Mark (2001), DOI [10.1109/51.932724](https://doi.org/10.1109/51.932724). | 不驗證腕式 ECG、HAL payload、mV、AFib 或產品臨床表現。 |
| [Wrist PPG During Exercise v1.0.0](https://physionet.org/content/wrist/1.0.0/) | 使用胸前 ECG 人工 R-peak 標註評估研究用 PPG 心率方法與動作干擾處理。 | PhysioNet ODC Attribution 1.0; DOI [10.13026/C2PQ1X](https://doi.org/10.13026/C2PQ1X); Jarchi & Casson (2017), DOI [10.3390/data2010001](https://doi.org/10.3390/data2010001). | 八位參與者與不同裝置不能證明 Xiaomi 準確度、校準或族群臨床表現。 |
| [PTB-XL](https://physionet.org/content/ptb-xl/) | 選用的多資料庫、尺度無關 ECG 統計參考。 | 使用時遵守所下載版本頁面的授權與引用。 | 12 導程臨床 ECG 不等於腕式導聯。 |
| [ECG-capable smartwatch simulator dataset](https://physionet.org/content/ecg-capable-smartwatches/1.0.0/) | 校準訊號注入與 smartwatch/reference 配對方法先例；資料不散布。 | PhysioNet restricted access；使用者須自行接受當期 data-use agreement。 | 其他手錶型號不能決定 Xiaomi raw-code→mV 映射。 |

下載資料應放在 git-ignored 的 `.local-research-data/`。發布 ODC-By 資料庫衍生輸出時，必須保留資料庫名稱、來源與 [ODC Attribution License](https://opendatacommons.org/licenses/by/1-0/) 歸屬。

## Papers and guidance / 論文與指引

- Charlton et al., wearable PPG review, [PMC7612541](https://pmc.ncbi.nlm.nih.gov/articles/PMC7612541/)：PPG、動作干擾、訊號品質與驗證背景。
- Wrist PPG atrial-fibrillation review, [PMC8890076](https://pmc.ncbi.nlm.nih.gov/articles/PMC8890076/)：只作研究背景，不授權 App 輸出 AF 結果。
- Takazawa et al., [PubMed 9719069](https://pubmed.ncbi.nlm.nih.gov/9719069/); J-SHIPP, [PubMed 26911232](https://pubmed.ncbi.nlm.nih.gov/26911232/); VascAgeNet review, [PubMed 34951543](https://pubmed.ncbi.nlm.nih.gov/34951543/)：界定探索性 PPG vascular-age 方法與限制，不宣稱已驗證血管年齡。
- [FDA Diagnostic ECG Guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/diagnostic-ecg-guidance-including-non-alarming-st-segment-measurement-guidance-industry), [FDA pulse oximeter guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/pulse-oximeters-premarket-notification-submissions-510ks-guidance-industry-and-food-and-drug), and [IMDRF SaMD clinical evaluation](https://www.imdrf.org/documents/software-medical-device-samd-clinical-evaluation)：資料完整性與驗證邊界，不是本專案的認證或核准。
- [WFDB header specification](https://physionet.org/physiotools/wag/header-5.htm)：每筆紀錄 ADC gain／baseline 與未校準訊號語意。
- IEC 60601-2-25：診斷 ECG 安全／效能標準參考；標準全文不散布，也不聲稱本專案符合該標準。

## Platform and hardware documentation / 平台與硬體文件

- [Android SensorEvent timestamp](https://developer.android.com/reference/android/hardware/SensorEvent#timestamp), [Health Services](https://developer.android.com/health-and-fitness/guides/health-services), [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect), and [Wear OS Data Layer](https://developer.android.com/training/wearables/data/overview) define platform behavior used by the apps.
- [Xiaomi Watch 2 Pro specifications](https://www.mi.com/global/product/xiaomi-watch-2-pro/specs/) and Xiaomi Health SDK documentation are capability references only. The project does not redistribute Xiaomi firmware or private databases.
- [Analog Devices MAX86178](https://www.analog.com/en/products/max86178.html), its datasheet, and MAXREFDES106 documentation describe chip/evaluation-platform features; they do not establish the retail watch's gain, electrode path, firmware access, or ADC-to-mV coefficient.

## Runtime dependencies / 執行期套件

Gradle resolves AndroidX, Kotlin, Google Play services, Guava, JUnit, and SQLCipher artifacts from Google Maven/Maven Central. Their own notices and licenses apply. AndroidX, Kotlin, coroutines, Google Play services, and Guava are primarily Apache-2.0 licensed; JUnit 4 uses EPL-1.0; SQLCipher for Android retains Zetetic's applicable terms. Python research dependencies are pinned in `research/clinical-data/requirements-lock.txt` and retain their own licenses.

The repository MIT License covers original project code only. It does not relicense dependencies, datasets, papers, standards, vendor documentation, or trademarks.
