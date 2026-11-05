# 多感測器醫療用途條件與缺口矩陣

更新：2026-10-10

## 結論

「醫療級」不是單一演算法分數或晶片規格。必須先鎖定每個功能的預期用途、使用者、族群、場景與宣稱，再以**最終手錶硬體＋韌體＋手機 App＋演算法**證明安全與性能，並完成適用市場的品質系統和法規程序。論文、FDA/IEEE 標準引用、AI 評估、免責聲明或 App 內部測試都不能單獨使產品成為醫療器材或取得上市授權。

這是工程準備與缺口清單，不是法律意見或認證。FDA guidance 通常表達 FDA 當前建議，不可一概改寫為普遍適用的法條；各感測器的測試方法、資料庫數量、標註者數、臨床研究及接受界線，須由監管專業人員依產品宣稱、分類、submission path 及適用標準確認。

## 各感測器需要的產品級證據

| 輸出／宣稱 | 必須先固定並驗證的量測鏈 | 產品級性能／臨床證據 | 專案目前缺口 |
|---|---|---|---|
| ECG 波形、心律／AF | 導聯與電極位置、HAL 欄位映射、ADC 碼、增益／基線、濾波、單位、取樣時鐘、接觸／佩戴條件；完整傳輸鏈不得改變語義 | 最終產品台架測試（幅度、時間、頻率響應、雜訊、干擾、掉訊、重複性）；若宣稱心律分類，另以預先鎖定演算法、獨立且專家判讀的資料驗證靈敏度、特異度、不可判讀率及信賴區間 | HAL 時戳與欄位／導聯／增益未確認；無同時同步的可追溯參考 ECG 配對資料；無本手錶輸入格式上的獨立臨床性能資料 |
| 心率（HR） | 來源 API、量測模式、實際時間、佩戴／運動條件、輸出更新與濾波語義 | 在預期靜息／運動範圍內，與同步 ECG 參考比較；預先定義誤差、偏差、失敗率及分層結果 | 可讀到個別 HR 記錄，不等於本 App 的各場景精度已驗證；無成品級配對性能報告 |
| PPG 波形／脈率 | 光學通道映射、LED/PD 配置、時序、增益、取樣率、貼合／皮膚接觸、運動與環境條件 | 原始波形品質、脈率須分別驗證；以同步 ECG／適用參考設備，在靜止與運動等預期條件下測偏差、重複性及無效率 | 有 raw 訊號但通道與取樣時鐘未確認；不得從圖形外觀推斷已解碼或醫療精度 |
| SpO₂ | 原廠光學配置、紅光／紅外通道及演算法、時間基準、部位、貼合和皮膚色素等影響因子 | 最終產品的非臨床和臨床性能；適用時以同步動脈血氣共氧儀參考，覆蓋宣稱範圍和代表性族群，報告偏差、精度、失敗與分層結果 | 手錶 raw waveform 不足以重建原廠校準；不能以一般 PPG、公開資料集或 AI 把 raw code 換成醫療 SpO₂ |
| BioZ／體成分等 | 電極／電流路徑、激勵頻率與電流、通道映射、增益、相位／阻抗轉換、寄生效應、接觸條件與校準 | 先明確定義要量的是阻抗、呼吸、血流動力學或體成分；依該用途以電氣標準件做量測鏈校驗，並與合適參考方法做重複性及臨床效度研究 | 目前僅研究 raw 欄位統計與事件索引；電極映射、單位及 raw→Ω 校準均未驗證；不存在可套用所有 BioZ 宣稱的單一通用驗收值 |
| 體表／體溫 | 感測器位置、接觸／環境、熱平衡、補償模型、原廠單位與每台裝置校準 | 若宣稱臨床體溫，按臨床溫度計用途與量測部位驗證精度、偏差、重複性及條件；皮膚溫度趨勢不等於核心體溫或發燒診斷 | 板溫 raw 值或一次讀值不能證明是體溫；未知通道單位不得轉成 °C 或作疾病判定 |

## 全產品共同門檻

1. **預期用途與市場**：逐項鎖定「只顯示資料」或「監測／篩檢／診斷」等宣稱、目標族群、使用環境、使用者及結果後續行動；由美國 FDA、台灣 TFDA、歐盟 MDR 等適用市場確認分類與上市路徑。不同宣稱不能共用一個籠統的「健康分析」驗收。
2. **設計與風險控制**：建立需求、架構、危害分析、風險控制、需求—測試追溯、缺陷／變更紀錄、軟體驗證與確認、資安、可用性、人因、標示及上市後監測流程。美國 QMSR 自 2026-02-02 生效，適用的器材製造商須依其品質系統要求運作；QMS 不是產品核准。
3. **量測與校準**：使用可追溯標準器／參考設備，在最終產品配置上驗證；保留設備型號與校正證書、韌體／App 版本、同步方式、原始配對資料、分析程式版本和預先規定的接受標準。晶片／評估板規格不能代表小米零售手錶的整機校準。
4. **演算法驗證**：凍結演算法與門檻後，使用與訓練／調參資料分離的驗證資料；依具體預期用途及監管路徑預先制定指標，適用時報告靈敏度、特異度、偏差、誤差界限、信賴區間、族群分層、不可判讀／失敗率及外部效度。FDA 特定指引提到的資料庫數量或專家標註方案，只在適用產品／路徑下依監管意見採用，不作為所有穿戴演算法的通用門檻。不得把同一人的相鄰窗口分到訓練與測試兩側而造成資料洩漏。
5. **臨床與授權**：性能／臨床證據須對應最終裝置、軟體、預期用途與族群；是否需要前瞻性試驗、IDE／倫理審查或其他臨床證據，依風險、分類和監管路徑判定，不假設每一產品都採相同試驗。取得適用的市場授權前，不可用「符合論文／標準」替代許可或宣稱醫療診斷。

## 本次獨立審查與程式端核對

- 三個獨立代理分別審視美國法規路徑、各感測器驗證要求，以及對「醫療級」主張的反方缺口；它們一致指出產品宣稱決定適用法規，標準／QMS／程式碼均不能取代成品驗證與上市授權。代理提供的主張已與官方來源交叉核對；代理意見本身不作法規證據。
- 程式抽查：`HealthConclusionEngine.ecgConclusion` 將 ECG 脈率顯示交給 `ClinicalVerdict.isEcgPulseDisplayable`，且在研究輸出文字中明示非臨床診斷。只證明此路徑有軟體閘門，不等於完整 App 的所有路徑均已審核。
- 已執行 `:mobile:testDebugUnitTest --tests com.jaysato.xiaomihealthbridge.ClinicalVerdictTest --tests com.jaysato.xiaomihealthbridge.EcgResearchDisplayTest --tests com.jaysato.xiaomihealthbridge.HealthConclusionEngineTest`，結果 `BUILD SUCCESSFUL`。這些是單元測試，不是醫療性能測試、臨床研究或法規核准。

## 本專案現況與可執行順序

依現有程式與研究紀錄：ECG/HAL 映射與時鐘未驗證；PPG 通道／取樣時鐘未驗證；BioZ 未有裝置專屬 Ω 校準；原始 SpO₂ 波形無已驗證 OEM 轉換；溫度通道與單位／位置未建立整機校準；HR 雖可讀到來源值，尚無本 App 目標場景的獨立參考驗證。故目前**沒有任何一項可升格為醫療級輸出或疾病診斷**。

建議按以下依賴順序推進，前一關未過不得以後一關的 AI 或 UI 宣稱取代：

1. **產品責任人／監管顧問**：選定單一首發市場與單一首發醫療宣稱；出具分類／法規路徑意見。沒有這項決策，無法設定正確的性能指標。
2. **小米／LifeQ 或具權限的硬體團隊**：取得零售 Watch 2 Pro 對應韌體的 sensor contract（通道、電極、欄位、時鐘、增益、濾波、量測位置、單位、OEM 校準及支援模式）；若不能提供，安排可追溯參考設備的同步台架量測。
3. **獨立實驗室**：先做 ECG/PPG/BioZ/溫度各自的台架特性與校準可行性，建立量測不確定度及接受界線；不得用在別款晶片評估板上的係數移植。
4. **演算法／臨床團隊**：在映射與時鐘已驗證後，凍結算法；建立去識別且有參考標註、受試者隔離的開發／獨立驗證集，依宣稱做統計和臨床評估。
5. **品質／法規負責人**：補齊 QMS、軟體生命週期、風險、人因、資安、標籤、提交文件及上市後流程，再由主管機關確認適用授權。

### 目前可以由程式團隊完成

- 確保原始資料不可變，時間／欄位／單位缺證據就保持未驗證；在分析輸出保留來源、版本、時間來源和品質狀態。
- 建立資料契約、重播工具、獨立測試、需求—測試追溯與可重現發布紀錄。
- 在所有畫面及匯出中阻擋未驗證的 mV、Ω、臨床 °C、SpO₂、AF／疾病結論；保留研究波形及明確限制。
- 以參考儀器匯入格式保存配對校準資料與 holdout 結果，但只在授權、同步、參考來源與驗證條件完整時才解鎖對應研究換算。

### 不能由程式／AI 單獨完成

取得 OEM 私有感測契約或改變受保護韌體；產生可追溯校準與臨床參考資料；招募及倫理審查；證明本產品對預期族群的醫療性能；建立實際運作中的 QMS；或取得任何主管機關上市授權。這些是要達成「醫療用途」的外部必要工作，不是再加一個演算法即可替代。

## 官方主要來源

- [FDA：General Wellness: Policy for Low Risk Devices (January 2026, final)](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/general-wellness-policy-low-risk-devices)；一般健康政策只適用於與疾病診斷、治療、緩解或預防無關的低風險用途；不能靠加上「wellness」或免責文字改變實際用途。
- [FDA：Device Software Functions and Mobile Medical Applications](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/policy-device-software-functions-and-mobile-medical-applications)；[FDA intended medical purpose navigator](https://www.fda.gov/medical-devices/digital-health-center-excellence/step-1-software-function-intended-medical-purpose)
- [FDA：QMSR](https://www.fda.gov/medical-devices/postmarket-requirements-devices/quality-management-system-regulation-qmsr)；[FDA：Premarket Submissions for Device Software Functions](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/content-premarket-submissions-device-software-functions)
- [FDA：Diagnostic ECG Guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/diagnostic-ecg-guidance-including-non-alarming-st-segment-measurement-guidance-industry)；[FDA：Arrhythmia Detector and Alarm Special Controls](https://www.fda.gov/medical-devices/guidance-documents-medical-devices-and-radiation-emitting-products/arrhythmia-detector-and-alarm-class-ii-special-controls-guidance-document-industry-and-fda-staff)
- [FDA-recognized IEC 60601-2-25 / IEC 60601-2-47 records](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfStandards/results.cfm?referencenumber=60601-2-&sortcolumn=pdd&start_search=11)
- [FDA-recognized ISO 80601-2-56 standard record (clinical thermometers)](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfStandards/detail.cfm?standard__identification_no=39379)；實際適用性仍按 intended use、量測位置及產品類型確認，不能把手腕／板溫當成臨床體溫。
- [FDA：Pulse oximeter medical-purpose testing draft guidance (Jan 2025; draft, not for implementation)](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/pulse-oximeters-medical-purposes-non-clinical-and-clinical-performance-testing-labeling-and)；[FDA-recognized ISO 80601-2-61](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfpcd/classification.cfm?ID=DQA)
- [FDA：Pulse Oximeters - Premarket Notification Submissions (510(k), final guidance)](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/pulse-oximeters-premarket-notification-submissions-510ks-guidance-industry-and-food-and-drug)；該文件談的是特定醫療用途脈搏血氧儀與提交證據，不是任何手錶 raw PPG 轉 SpO₂ 的通用演算法。
- [FDA：Cuffless blood-pressure device draft guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/cuffless-non-invasive-blood-pressure-measuring-devices-clinical-performance-testing-and-evaluation)；[FDA-recognized ISO 81060-2 listing](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfStandards/results.cfm?ascapilotyn=off&category=&effectivedatefrom=&effectivedateto=&organization=&pagenum=10&productcode=&recognitionnumber=&referencenumber=&sortcolumn=pa&start_search=211&supportingdocsyn=off&title=)
- [FDA-recognized ISO 80601-2-56 clinical thermometer listing](https://www.accessdata.fda.gov/scripts/cdrh/cfdocs/cfStandards/results.cfm?ascapilotyn=off&category=&effectivedatefrom=&effectivedateto=&organization=&pagenum=25&productcode=&recognitionnumber=&referencenumber=&regulationnumber=&sortcolumn=std&start_search=926&supportingdocsyn=off&title=)
- [European Commission: MDCG medical-device software guidance index](https://health.ec.europa.eu/medical-devices-sector/new-regulations/guidance-mdcg-endorsed-documents-and-other-guidance_en)；[IMDRF: SaMD Clinical Evaluation](https://www.imdrf.org/documents/software-medical-device-samd-clinical-evaluation)

## 2026-10 法規路徑交叉核對

以下是官方要求的工程整理，不是對本產品的分類決定。是否屬醫療器材、風險等級及送審路徑，仍取決於正式預期用途、宣稱、使用者、病患風險與銷售市場。

| 市場 | 已核對的核心門檻 | 本專案可由程式團隊先做 | 目前不能在此 repo 宣稱已達成 |
|---|---|---|---|
| 美國 FDA | 軟體若取得／分析生理訊號並輸出臨床相關解讀，可能屬 device software function；須按具體產品和分類確認上市路徑。適用製造商須遵守 2026-02-02 生效的 QMSR。ECG/心律宣稱須提交適用的安全與性能證據；FDA arrhythmia guidance 提及在獨立資料庫評估演算法，並報告 sensitivity/specificity。 | 鎖定宣稱草案；需求—風險—測試追溯；保留版本／來源／原始資料；建立可重現單元與整合測試；整理預先指定的驗收統計計畫。 | FDA 分類／上市許可、QMS 實際運作與稽核、最終手錶硬體性能、參考儀器配對研究、臨床效能、上市後流程。論文或 AI review 不能替代。 |
| 歐盟 MDR | 軟體是否為醫療器材及 Rule 11 等級依預期用途判定；提供診斷／治療決策資訊或監測生理參數可導致 IIa/IIb/III 等不同分類；依適用路徑需符合 MDR 的技術文件、品質與臨床評估及 conformity assessment。MDCG 2019-11 rev.1（2025）是軟體資格／分類指引。 | 建立用途／風險檔案骨架、軟體生命週期證據、資料與演算法版本追溯、風險控制驗證。 | 最終分類決定、臨床評估、品質系統、必要的公告機構評估及 CE 合規聲明；目前沒有可支持產品級醫療宣稱的獨立臨床資料。 |
| 台灣 TFDA | 《醫療器材管理法》包含軟體；依用途與風險分級，並按適用分類辦理查驗登記／許可及品質系統要求。TFDA 已發布醫用軟體分類分級、醫療器材軟體確效及 AI/ML 醫材軟體技術指引。 | 將預期用途、風險、軟體確效、資料來源與模型版本文件化；整理查驗資料清單與測試證據。 | TFDA 對本產品的正式分類／許可、製造商 QMS/QMS 檢查、適用的臨床／性能驗證與正式標示；不能由本機程式或參考論文自行授予。 |

### 對「並達成」的可執行結論

- **本次已完成：**核對 FDA、歐盟及 TFDA 官方條件，並將可程式化工作與外部法規／臨床依賴拆開記錄；官方條件沒有一個可把現有研究輸出直接「升級成醫療級」的軟體開關。
- **本 repo 尚可持續完成：**工程追溯、來源與單位閘門、時間戳契約、研究輸出標籤、回歸測試、需求—測試矩陣及可重現建置。完成這些只表示軟體工程準備度，不表示通過醫療器材驗證。
- **達成醫療用途的阻塞外部關卡：**產品責任人選定首發市場和單一宣稱；Xiaomi/LifeQ 提供零售機 sensor contract 或授權 raw access；實驗室以最終手錶與可追溯參考儀器完成台架校準；臨床／演算法團隊以獨立、受試者隔離且專家標註資料做效能驗證；製造商建立適用 QMS；法規專業人員確認並完成上市程序。這些都不能由 Codex、子代理或程式碼代替簽核與實測。
- **目前狀態仍為未達成：**本專案不是醫療器材核准產品；不得將 ECG/AF、SpO₂、體溫、BioZ 或其他研究推估輸出標成醫療正常／異常或醫療校準值。

### 2026-10-10 最新 FDA 代理複核與執行門檻

- **代理複核範圍：**獨立本機代理以 FDA 官方來源複核本矩陣，未接觸或傳送任何個人感測器資料。代理確認：產品宣稱／預期用途與最終裝置決定適用路徑；QMSR、論文、晶片規格或軟體測試都不是上市授權或產品級性能證明。
- **政策更新：**FDA《General Wellness: Policy for Low Risk Devices》已於 2026 年 1 月發布最終版；只涵蓋與疾病診斷、治療、緩解或預防無關的低風險生活型態功能。必須按每項軟體功能與實際宣稱評估，不能只靠更名或免責聲明規避醫材判定。
- **血氧文件狀態：**2025 年醫療用途脈搏血氧測試文件是草案，不能當作最終強制要求；應同時核對最終 510(k) guidance、FDA 認可標準及監管顧問對本產品特定用途的適用判定。
- **可由本專案立即完成的 gate：**(a) 每一輸出鎖定 intended-use statement 與非醫療／研究標籤；(b) 資料契約固定來源、映射、時鐘、單位、韌體／App 版本；(c) 所有轉換公式具輸入前置條件、拒絕原因及回歸測試；(d) 建立需求—風險—控制—測試—版本追溯；(e) 確保 build artifact 可追溯至受控版本並保存測試報告。以上只構成軟體工程證據，不等同合規 QMS。
- **外部完成 gate：**產品負責人與監管顧問確認首發市場及宣稱；小米／LifeQ 或授權硬體方確認零售手錶訊號契約；校準實驗室以最終硬體及可追溯參考設備完成測試；臨床／演算法團隊依預先鎖定方案完成代表性、獨立資料驗證；責任製造商運作適用 QMS 並向主管機關完成必要程序。任何一項未完成，狀態維持 NOT MET。
- **FDA 官方核對：**[QMSR（2026-02-02 生效）](https://www.fda.gov/medical-devices/postmarket-requirements-devices/quality-management-system-regulation-qmsr)適用於擬商業分銷的成品製造商；[一般健康政策（2026-01 final）](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/general-wellness-policy-low-risk-devices)；[FDA CDS FAQ](https://www.fda.gov/medical-devices/software-medical-device-samd/clinical-decision-support-software-frequently-asked-questions-faqs)說明多功能產品仍須逐個看受監管的 device function；[最終脈搏血氧 510(k) guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/pulse-oximeters-premarket-notification-submissions-510ks-guidance-industry-and-food-and-drug)。
- **決策：**本次完成的是法規條件複核與軟體團隊可執行門檻更新；本專案「達成醫療級」仍是 NOT MET。下一個真正能解除關鍵阻塞的證據不是再加 AI，而是取得正式 intended-use／市場決策及 Watch 2 Pro 專屬 sensor contract，之後才可安排最終裝置的參考實驗室配對驗證。

### 初步軟體風險—控制—測試追溯（非正式 ISO 14971 風險檔）

此表是 repo 內可查證控制的工程盤點，不是製造商核准的危害分析、殘餘風險接受或正式設計紀錄；正式醫療器材工作仍須由責任製造商納入其 QMS、預期用途及全產品風險管理。

| 可預見的失效／可能傷害 | repo 內可查證的軟體控制 | 測試證據 | 尚缺的產品級證據／狀態 |
|---|---|---|---|
| 時鐘或取樣率未知卻算出 bpm／RR／HRV，造成錯誤安心或不必要擔憂 | ECG 引擎只有可信時鐘及取樣率才能換算 RR／HR；未校正時使用索引時間軸並回報阻擋原因 | `health-engine/src/test/kotlin/com/jaysato/xiaomihealthbridge/engine/EcgCoreResearchEngineTest.kt`；`mobile/src/test/java/com/jaysato/xiaomihealthbridge/ClinicalVerdictTest.kt` | 手錶事件欄位契約與 ADC 時鐘未確認；測試資料不能證明零售硬體正確性；**NOT MET** |
| raw code 被誤顯示為 mV、Ω、°C 或 SpO₂ | 未驗證 ECG profile/欄位映射時不輸出 mV；BioZ 保存明示未映射／未校準 raw code；原始 SpO₂／溫度使用相對索引與未知單位 | `EcgCoreResearchEngineTest.kt`；`mobile/src/test/java/com/jaysato/xiaomihealthbridge/BiozRawFieldResearchTest.kt`；`mobile/src/test/java/com/jaysato/xiaomihealthbridge/RawPreviewResearchTest.kt` | 無最終手錶可追溯校準、OEM 合約與實驗室配對；**NOT MET** |
| 舊資料或未可信時間戳被當作目前讀值 | UI 狀態將 stale、未來時間、時間未知分開，匯出保留狀態而非只看全域快照時間 | `mobile/src/test/java/com/jaysato/xiaomihealthbridge/SensorDisplayStateTest.kt`；`mobile/src/test/java/com/jaysato/xiaomihealthbridge/HealthAnalysisFreshnessPolicyTest.kt` | 仍須逐頁／逐匯出端到端驗收並用新鮮手錶資料驗證；目前本輪無新量測；**PARTIAL** |
| 事件缺值、真實零值或預覽截斷混淆，導致衍生序列看似完整 | raw 與 derived 分層；保留零值、缺口標籤、計數與品質指標，不把估算寫回 raw | `RawPreviewResearchTest.kt`；`BiozRawFieldResearchTest.kt`；Room instrumentation 測試僅在明確執行設備測試時可作持久化證據 | 手機端 Room instrumentation／最終匯出 UI 仍須實機跑完；研究相對指標非診斷；**PARTIAL** |
| 動作造成 PPG 波形／脈率誤差，卻被當作生理真值 | PPG-motion 指標僅在同 session、共用單調時鐘及對齊條件成立時產生，並標為研究探索值 | `health-engine/src/test/kotlin/com/jaysato/xiaomihealthbridge/engine/PpgMotionEvidenceEngineTest.kt`；`mobile/src/test/java/com/jaysato/xiaomihealthbridge/PpgMotionPreviewResearchTest.kt` | 未與手腕佩戴狀態、同步 ECG 參考、預期運動族群及最終裝置作性能驗證；**NOT MET** |
| 研究結果被使用者理解為疾病排除／診斷 | `ClinicalVerdict` 輸出非診斷限制；品質或資料資格不符時限制 ECG 研究心率呈現 | `ClinicalVerdictTest.kt`；`EcgResearchDisplayTest.kt` | 全 App 全文案、外部宣傳、使用者理解與人因驗證未完成；標籤不能替代法規判定；**PARTIAL** |

此盤點支持「已有部分軟體風險控制」而非「風險已可接受」；每個 PARTIAL／NOT MET 須有責任人、接受準則、客觀測試紀錄及適用的製造商／法規簽核，才可關閉。

### 新增核對的官方來源

- [FDA QMSR（2026-02-02 生效）](https://www.fda.gov/medical-devices/postmarket-requirements-devices/quality-management-system-regulation-qmsr)；[FDA Device Software Functions policy](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/policy-device-software-functions-and-mobile-medical-applications)；[FDA premarket software documentation guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/content-premarket-submissions-device-software-functions)
- [FDA Arrhythmia Detector and Alarm Special Controls](https://www.fda.gov/medical-devices/guidance-documents-medical-devices-and-radiation-emitting-products/arrhythmia-detector-and-alarm-class-ii-special-controls-guidance-document-industry-and-fda-staff)；[FDA medical-purpose pulse-oximeter draft guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/pulse-oximeters-medical-purposes-non-clinical-and-clinical-performance-testing-labeling-and)
- [EU MDR 2017/745 Annex VIII, Rule 11](https://eur-lex.europa.eu/eli/reg/2017/745/oj)；[European Commission MDCG 2019-11 rev.1 (June 2025)](https://health.ec.europa.eu/latest-updates/update-mdcg-2019-11-rev1-qualification-and-classification-software-regulation-eu-2017745-and-2025-06-17_en)
- [TFDA 醫用軟體分類分級參考指引](https://www.fda.gov.tw/tc/includes/getfile.ashx?id=f637443989833238169)；[TFDA 醫療器材軟體確效指引](https://www.fda.gov.tw/tc/includes/GetFile.ashx?id=f637553892654662304)；[TFDA AI/ML 醫療器材軟體查驗登記技術指引](https://www.fda.gov.tw/tc/includes/GetFile.ashx?id=f637648024805180943&type=1)；[TFDA 查驗登記專區](https://www.fda.gov.tw/tc/siteContent.aspx?sid=12014)

### 獨立代理核對摘要

三個子代理分別核對 FDA、EU MDR、TFDA 官方資料；Codex 將其結果與上列官方原文交叉檢查。這是法規路徑調研，不是代理替產品做分類或認證。

- **FDA：**路徑可能是 510(k)、De Novo、PMA 或豁免，取決於完整產品、預期用途及風險。常見 OTC ECG 軟體分類不能直接支援 AF 診斷宣稱；代理指出其定義不包含提供診斷。Arrhythmia guidance 要求將演算法在適當且獨立的 ECG 資料上評估，並依宣稱報告性能。FDA 上市授權、QMSR、產品級參考驗證均非程式碼可自行生成。
- **EU：**MDR Rule 11 分級依軟體用途及監測風險決定；消費級穿戴裝置作為輸入，不會免除醫療用途軟體／系統的證據責任。Class IIa 及以上等適用情況涉及公告機構合格評定；分類不能先驗固定為某一等級。
- **台灣：**不應只因 ECG 字樣就把本 App 指定為特定 TFDA 品項；先按明確用途與產品架構申請屬性／分類判定。TFDA 軟體確效文件不等同臨床證據，AI/ML 新興產品可能需更多性能或臨床資料。許可、QMS/QSD 與必要的臨床證據均需責任製造商、實驗室／臨床單位及主管機關參與。
- **共同建議：**先由產品責任人確定一個市場和一個具體宣稱，再與監管顧問取得分類意見；其後取得 Xiaomi/LifeQ 對 Watch 2 Pro 零售機的訊號鏈契約、同步參考設備配對資料、最終硬體性能報告與獨立驗證資料。未完成前維持研究用途呈現。
