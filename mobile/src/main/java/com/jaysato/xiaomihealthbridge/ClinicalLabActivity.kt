package com.jaysato.xiaomihealthbridge

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Clinical-reference lab for PPG / ECG / BIOZ / temperature / full suite.
 * Medical-monitor visual language; non-diagnostic disclaimer always shown.
 */
class ClinicalLabActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var body: LinearLayout
    private lateinit var ecgEventRate: TextView
    private val cards = linkedMapOf<String, ModalityCard>()
    private var preferredSpo2Kind: String? = null
    private var onEcgCalibrationCsvPicked: ((Uri) -> Unit)? = null
    private val ecgCalibrationPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onEcgCalibrationCsvPicked?.invoke(it) }
    }

    private fun activeEcgResearchProfile(prefs: android.content.SharedPreferences): EcgResearchProfileStore.Profile? {
        return EcgResearchProfileStore(this).readActive(prefs)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        preferredSpo2Kind = intent.getStringExtra("preferred_spo2_kind")
            ?.takeIf { it in Spo2ChannelSelectionPolicy.KINDS }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(36))
            setBackgroundColor(BG)
        }
        body.addView(header())
        body.addView(Button(this).apply {
            text = "AILISA · PPG 保存證據"
            setOnClickListener { startActivity(Intent(this@ClinicalLabActivity, PpgEvidenceActivity::class.java)) }
        })
        body.addView(disclaimerBanner(), margins(top = 12, bottom = 16))
        body.addView(advancedSection("進階：ECG 校準工具", automaticEcgCalibrationPanel()), margins(bottom = 16))
        listOf(
            Modality("ppg", "PPG", "光學脈搏", Color.rgb(255, 90, 120), "/control/ppg-now"),
            Modality("ecg", "ECG", "心電參考", Color.rgb(0, 220, 160), "/control/ecg-now"),
            Modality("bioz", "BIOZ", "阻抗參考", Color.rgb(120, 180, 255), "/control/bioz-now"),
            Modality("oxygen", "SpO₂", "血氧參考", Color.rgb(0, 200, 180), "/control/oxygen-now"),
            Modality("temperature", "TEMP", "溫度原始值", Color.rgb(255, 170, 70), "/control/temperature-now"),
            Modality("suite", "SCAN", "全掃描", Color.rgb(140, 200, 255), "/control/sensor-suite")
        ).forEach { modality ->
            val card = ModalityCard(modality)
            cards[modality.id] = card
            body.addView(card.root, margins(bottom = 12))
        }
        body.addView(watchFeaturePanel(), margins(top = 8))
        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(body)
        })
        refreshAll()
        intent.getStringExtra("auto")?.let { auto ->
            cards[auto]?.measure()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun header(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@ClinicalLabActivity).apply {
            text = "‹  返回"
            textSize = 16f
            setTextColor(Color.rgb(120, 200, 255))
            setOnClickListener { finish() }
        })
        addView(TextView(this@ClinicalLabActivity).apply {
            text = "臨床參考實驗室"
            textSize = 28f
            setTextColor(Color.WHITE)
            setPadding(0, dp(14), 0, 0)
        })
        addView(TextView(this@ClinicalLabActivity).apply {
            text = "CLINICAL REFERENCE LAB  ·  MONITOR STYLE"
            textSize = 11f
            setTextColor(Color.rgb(0, 200, 150))
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun advancedSection(title: String, content: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(24, 31, 44))
        val details = LinearLayout(this@ClinicalLabActivity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        addView(TextView(this@ClinicalLabActivity).apply {
            text = "$title　⌄"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener {
                details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                text = if (details.visibility == View.VISIBLE) "$title　⌃" else "$title　⌄"
            }
        })
        addView(details)
        details.addView(content)
    }

    private fun researchCalibrationPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(24, 31, 44))
        setPadding(dp(14), dp(14), dp(14), dp(14))
        addView(TextView(this@ClinicalLabActivity).apply {
            text = "ECG 外部參考研究比較"
            textSize = 18f
            setTextColor(Color.WHITE)
        })
        addView(TextView(this@ClinicalLabActivity).apply {
            text = "需使用隔離的外部 ECG 參考設備，輸入至少五組同步配對的 raw:mV 點位與來源。僅供本研究頁比較顯示尺度；不替代小米裝置校準、獨立臨床驗證或醫療診斷。"
            textSize = 12f
            setTextColor(Color.rgb(170, 190, 210))
            setPadding(0, dp(6), 0, dp(8))
        })
        val reference = EditText(this@ClinicalLabActivity).apply {
            hint = "訓練集 raw:mV，至少 5 組；100:0.5,200:1.0,..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 145, 160))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(android.text.InputFilter.LengthFilter(8192))
        }
        val source = EditText(this@ClinicalLabActivity).apply {
            hint = "外部參考來源／連結"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 145, 160))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(android.text.InputFilter.LengthFilter(256))
        }
        val holdout = EditText(this@ClinicalLabActivity).apply {
            hint = "獨立留出集 raw:mV，另一批同步量測、至少 5 組"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 145, 160))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(android.text.InputFilter.LengthFilter(8192))
        }
        val instrument = EditText(this@ClinicalLabActivity).apply {
            hint = "參考 ECG 儀器廠牌與型號"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 145, 160))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(android.text.InputFilter.LengthFilter(256))
        }
        val protocol = EditText(this@ClinicalLabActivity).apply {
            hint = "測試方案 ID、儀器精度／解析度與設定"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 145, 160))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(android.text.InputFilter.LengthFilter(512))
        }
        val trainingCapture = EditText(this@ClinicalLabActivity).apply {
            hint = "訓練擷取／session ID"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 145, 160))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(android.text.InputFilter.LengthFilter(128))
        }
        val holdoutCapture = EditText(this@ClinicalLabActivity).apply {
            hint = "獨立留出擷取／session ID（須與訓練不同）"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 145, 160))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            filters = arrayOf(android.text.InputFilter.LengthFilter(128))
        }
        val tolerance = EditText(this@ClinicalLabActivity).apply {
            hint = "允許最大留出 RMSE（mV；依參考儀器規格／預先訂定方案）"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 145, 160))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val result = TextView(this@ClinicalLabActivity).apply {
            textSize = 13f
            setTextColor(Color.rgb(120, 230, 180))
            setPadding(0, dp(8), 0, 0)
        }
        val store = EcgResearchProfileStore(this@ClinicalLabActivity)
        var latestFit: EcgResearchCalibration.Fit? = null
        var latestValidation: EcgResearchCalibration.IndependentHoldoutValidation? = null
        var validatedTrainPairs: List<Pair<Double, Double>> = emptyList()
        var validatedHoldoutPairs: List<Pair<Double, Double>> = emptyList()
        var validatedMaximumRmse: Double? = null
        addView(TextView(this@ClinicalLabActivity).apply {
            text = "mV 研究 profile 需要兩批獨立同步參考：訓練集擬合，留出集只做驗證。留一交叉驗證不能解鎖。"
            textSize = 12f
            setTextColor(Color.rgb(170, 190, 210))
            setPadding(0, dp(4), 0, dp(8))
        })
        addView(reference)
        addView(holdout)
        addView(tolerance)
        addView(instrument)
        addView(protocol)
        addView(trainingCapture)
        addView(holdoutCapture)
        addView(source)
        addView(Button(this@ClinicalLabActivity).apply {
            text = "獨立驗證並建立研究 mV profile"
            isAllCaps = false
            setOnClickListener {
                val pairs = reference.text.toString().split(',').map { entry ->
                    val values = entry.trim().split(':')
                    if (values.size == 2) values[0].trim().toDoubleOrNull()?.let { raw ->
                        values[1].trim().toDoubleOrNull()?.let { millivolts -> raw to millivolts }
                    } else null
                }
                val holdoutPairs = holdout.text.toString().split(',').map { entry ->
                    val values = entry.trim().split(':')
                    if (values.size == 2) values[0].trim().toDoubleOrNull()?.let { raw ->
                        values[1].trim().toDoubleOrNull()?.let { millivolts -> raw to millivolts }
                    } else null
                }
                if (pairs.size < EcgResearchProfileStore.MIN_POINTS || pairs.any { it == null } ||
                    holdoutPairs.size < EcgResearchProfileStore.MIN_POINTS || holdoutPairs.any { it == null }) {
                    result.text = "訓練集與獨立留出集都需至少五組有效同步 raw:mV 配對點。"
                    return@setOnClickListener
                }
                val validPairs = pairs.filterNotNull()
                val validHoldoutPairs = holdoutPairs.filterNotNull()
                val maxRmse = tolerance.text.toString().trim().toDoubleOrNull()
                if (maxRmse == null || !maxRmse.isFinite() || maxRmse <= 0.0) {
                    result.text = "請填入參考儀器規格或預先訂定驗證方案的最大 RMSE（mV）。"
                    return@setOnClickListener
                }
                if (instrument.text.isNullOrBlank() || protocol.text.isNullOrBlank() ||
                    trainingCapture.text.isNullOrBlank() || holdoutCapture.text.isNullOrBlank() ||
                    trainingCapture.text.toString().trim() == holdoutCapture.text.toString().trim() ||
                    source.text.isNullOrBlank()) {
                    latestFit = null
                    latestValidation = null
                    result.text = "需填參考儀器、測試方案／精度、來源及不同的訓練與留出擷取 ID。這些是追溯欄位，不會自行證明校準有效。"
                    return@setOnClickListener
                }
                val fit = EcgResearchCalibration.fitRawToMillivolts(validPairs.map { it.first }, validPairs.map { it.second })
                val validation = EcgResearchCalibration.validateIndependentHoldout(
                    validPairs.map { it.first },
                    validPairs.map { it.second },
                    validHoldoutPairs.map { it.first },
                    validHoldoutPairs.map { it.second },
                    maxRmse
                )
                latestFit = fit.takeIf { it.usable && validation.usable && validation.passed }
                latestValidation = validation.takeIf { it.usable && validation.passed }
                validatedTrainPairs = validPairs
                validatedHoldoutPairs = validHoldoutPairs
                validatedMaximumRmse = maxRmse
                result.text = if (fit.usable) {
                    "研究結果：slope=${"%.8f".format(fit.slopeMvPerRaw)} mV/raw，offset=${"%.5f".format(fit.offsetMv)} mV，RMSE=${"%.5f".format(fit.rmseMv)} mV\n" +
                        if (validation.usable) {
                            "獨立留出 RMSE=${"%.5f".format(validation.rmseMv)} mV／門檻 ${"%.5f".format(maxRmse)} mV（${validation.heldOutPoints} 點）：${if (validation.passed) "通過" else "未通過，不能儲存"}。通過結果仍非 OEM 或臨床驗證。"
                        } else {
                            "${validation.note}；不能解鎖 mV profile。"
                        }
                } else fit.note
            }
        })
        addView(Button(this@ClinicalLabActivity).apply {
            text = "儲存通過獨立驗證的 profile"
            isAllCaps = false
            setOnClickListener {
                val fit = latestFit
                val validation = latestValidation
                if (fit == null || validation == null) {
                    result.text = "先輸入至少五組訓練配對與五組獨立留出配對，完成驗證。"
                } else if (store.validateAndSaveIndependent(
                        trainRaw = validatedTrainPairs.map { it.first },
                        trainReferenceMv = validatedTrainPairs.map { it.second },
                        holdoutRaw = validatedHoldoutPairs.map { it.first },
                        holdoutReferenceMv = validatedHoldoutPairs.map { it.second },
                        maximumAllowedRmseMv = validatedMaximumRmse ?: Double.NaN,
                        source = source.text.toString(),
                        referenceInstrument = instrument.text.toString(),
                        protocolId = protocol.text.toString(),
                        sensorProfileFingerprint = RawSensorStore.open(this@ClinicalLabActivity)
                            .getString("ecg_sensor_profile_fingerprint", "").orEmpty(),
                        trainingCaptureId = trainingCapture.text.toString(),
                        holdoutCaptureId = holdoutCapture.text.toString()
                    ) && store.read()?.let {
                        it.isEligibleForResearchMv && it.isBoundToSensorProfile(
                            RawSensorStore.open(this@ClinicalLabActivity)
                                .getString("ecg_sensor_profile_fingerprint", "")
                        )
                    } == true) {
                    val currentFingerprint = RawSensorStore.open(this@ClinicalLabActivity)
                        .getString("ecg_sensor_profile_fingerprint", "").orEmpty()
                    val profile = store.read()?.takeIf {
                        it.isEligibleForResearchMv && it.isBoundToSensorProfile(currentFingerprint)
                    }
                    val rawPrefs = RawSensorStore.open(this@ClinicalLabActivity)
                    val rawResearch = if (EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(
                        rawPrefs.getString("ecg_research_waveform_encoding", null),
                        rawPrefs.getString("ecg_research_decode_status", null)
                    )) SensorSuiteCsvParser.parseFloats(
                        rawPrefs.getString("ecg_research_waveform", "").orEmpty()
                    ) else emptyList()
                    val mV = profile?.takeIf {
                        rawPrefs.getString("ecg_research_waveform_encoding", "") == it.inputEncoding
                    }?.apply(rawResearch).orEmpty()
                    val waveformPersisted = mV.size >= 32 && rawPrefs.edit()
                            .putString("ecg_waveform", mV.joinToString(","))
                            .putString("ecg_waveform_encoding", "ecg_external_reference_mv")
                            .putString("ecg_status", "calibrated_external_reference")
                            .putString("ecg_calibration_profile_id", profile?.profileId.orEmpty())
                            .putString("ecg_calibration", "${rawPrefs.getString("ecg_calibration", "")};profile=${profile?.source};train_points=${profile?.points};holdout_points=${profile?.validatedPoints};holdout_rmse_mv=${profile?.validationRmseMv}")
                            .commit()
                    result.text = if (waveformPersisted) {
                        "已啟用研究 mV 預覽：訓練 ${fit.points} 點；獨立留出 ${validation.heldOutPoints} 點，RMSE=${"%.5f".format(validation.rmseMv)} mV。現有解碼波形已重算。此為研究參考轉換，不是 OEM／臨床校準。"
                    } else if (mV.size >= 32) {
                        "profile 已驗證，但 mV 波形未能保存；請重開本頁後確認狀態。"
                    } else {
                        "profile 已儲存且通過獨立留出驗證；目前沒有匹配格式的 ECG 研究波形可重算。下次收到該格式事件後套用。"
                    }
                    refreshAll()
                } else {
                    result.text = "目前只完成同批資料留一驗證，不能解鎖 mV；請另提供至少五組獨立同步 raw:mV 留出資料。"
                }
            }
        })
        val activeFingerprint = RawSensorStore.open(this@ClinicalLabActivity)
            .getString("ecg_sensor_profile_fingerprint", "").orEmpty()
        store.read()?.takeIf { it.isBoundToSensorProfile(activeFingerprint) }?.let { profile ->
            addView(TextView(this@ClinicalLabActivity).apply {
                text = "研究 profile：${profile.referenceInstrument} · 方案 ${profile.protocolId} · 訓練 ${profile.trainingCaptureId}／留出 ${profile.holdoutCaptureId} · RMSE ${"%.5f".format(profile.validationRmseMv)} / 門檻 ${"%.5f".format(profile.maximumAllowedRmseMv)} mV · 來源 ${profile.source}"
                textSize = 11f
                setTextColor(Color.rgb(170, 190, 210))
                setPadding(0, dp(4), 0, 0)
            })
        }
        addView(Button(this@ClinicalLabActivity).apply {
            text = "清除研究 mV profile並還原未縮放波形"
            isAllCaps = false
            setOnClickListener {
                store.clear()
                result.text = "研究 profile 已清除；既有 mV 預覽已降回原始研究波形（若仍保存）。"
                refreshAll()
            }
        })
        addView(result)
    }

    private fun automaticEcgCalibrationPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(24, 31, 44))
        setPadding(dp(14), dp(14), dp(14), dp(14))
        addView(TextView(this@ClinicalLabActivity).apply {
            text = "ECG 外部參考線性映射（研究尺度）"
            textSize = 18f
            setTextColor(Color.WHITE)
        })
        addView(TextView(this@ClinicalLabActivity).apply {
            text = "匯入同步 raw ECG 與外部參考 ECG CSV。程式會檢查配對格式、擬合 raw→mV 線性映射並評估留出集誤差；檔案來源與時間宣告無法由 App 認證，因此結果僅是匯入資料上的研究尺度映射，不代表手錶已完成計量或醫療校準。晶片規格、論文或 AI 不能代替這支手錶的實際 mV 參考量測。"
            textSize = 12f
            setTextColor(Color.rgb(170, 190, 210))
            setPadding(0, dp(6), 0, dp(8))
        })
        addView(TextView(this@ClinicalLabActivity).apply {
            text = "CSV 中繼資料需包含 #watch_sensor_profile_fingerprint=<64 位 SHA-256>；可在手錶 ECG 原始資料封包中取得。分開波形欄位：split,capture_id,stream,timestamp_ns,value；stream 為 raw 或 reference。訓練與留出需不同且時間區間不重疊；時間戳須來自 shared_monotonic_ns。程式會拒絕超出訓練 raw 範圍的留出資料，RMSE 門檻同時限制為留出參考振幅範圍的 5% 及 0.05 mV（App 研究閘門，非 IEC/FDA 符合聲明）。指紋、儀器與時間基準仍是匯入者宣告，App 無法認證。"
            textSize = 11f
            setTextColor(Color.rgb(210, 195, 150))
            setPadding(0, 0, 0, dp(8))
        })
        val result = TextView(this@ClinicalLabActivity).apply {
            text = "尚未匯入同步參考資料；目前只提供未校準相對波形。"
            textSize = 13f
            setTextColor(Color.rgb(120, 230, 180))
            setPadding(0, dp(8), 0, 0)
        }
        addView(Button(this@ClinicalLabActivity).apply {
            text = "複製目前手錶 ECG 來源指紋"
            isAllCaps = false
            setOnClickListener {
                val fingerprint = RawSensorStore.open(this@ClinicalLabActivity)
                    .getString("ecg_sensor_profile_fingerprint", "").orEmpty()
                if (!fingerprint.matches(Regex("[0-9a-f]{64}"))) {
                    result.text = "尚未收到含有效來源指紋的 ECG 快照；請先由手錶完成一次 ECG 擷取並同步。"
                } else {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "ECG source fingerprint",
                            "#watch_sensor_profile_fingerprint=$fingerprint"
                        )
                    )
                    result.text = "指紋已複製。它只代表 App 可觀測的設定摘要，不驗證 CSV 來源或校準有效性。"
                }
            }
        })
        onEcgCalibrationCsvPicked = { uri -> processEcgCalibrationCsv(uri, result) }
        addView(Button(this@ClinicalLabActivity).apply {
            text = "匯入配對 CSV，計算研究尺度 mV 映射"
            isAllCaps = false
            setOnClickListener { ecgCalibrationPicker.launch(arrayOf("text/*", "application/csv")) }
        })
        addView(Button(this@ClinicalLabActivity).apply {
            text = "清除研究 mV profile並還原未縮放波形"
            isAllCaps = false
            setOnClickListener {
                EcgResearchProfileStore(this@ClinicalLabActivity).clear()
                result.text = "研究 mV profile 已清除；回復原始相對尺度。"
                refreshAll()
            }
        })
        val activeFingerprint = RawSensorStore.open(this@ClinicalLabActivity)
            .getString("ecg_sensor_profile_fingerprint", "").orEmpty()
        EcgResearchProfileStore(this@ClinicalLabActivity).read()
            ?.takeIf { it.isBoundToSensorProfile(activeFingerprint) }?.let { profile ->
            addView(TextView(this@ClinicalLabActivity).apply {
                text = "已載入研究 profile：${profile.referenceInstrument} · ${profile.protocolId} · 留出 RMSE ${"%.5f".format(profile.validationRmseMv)} mV · 檔案 ${profile.source}"
                textSize = 11f
                setTextColor(Color.rgb(170, 190, 210))
                setPadding(0, dp(4), 0, 0)
            })
        }
        addView(result)
        ecgEventRate = TextView(this@ClinicalLabActivity).apply {
            textSize = 12f
            setTextColor(Color.rgb(170, 190, 210))
            setPadding(0, dp(8), 0, 0)
        }
        addView(ecgEventRate)
    }

    private fun processEcgCalibrationCsv(uri: Uri, result: TextView) {
        val csv = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= EcgPairedReferenceCsv.MAX_CHARACTERS) {
                    val count = input.read(buffer, 0, minOf(buffer.size, EcgPairedReferenceCsv.MAX_CHARACTERS + 1 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                if (bytes.size > EcgPairedReferenceCsv.MAX_CHARACTERS) null else bytes.toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
        if (csv == null) {
            result.text = "無法讀取 CSV，或檔案超過 8 MiB。"
            return
        }
        val parsed = EcgPairedReferenceCsv.parse(csv)
        val dataset = parsed.dataset
        if (dataset == null) {
            result.text = "配對資料拒絕：${parsed.error ?: "格式錯誤"}。尚未建立或套用 mV profile。"
            return
        }
        if (!dataset.alignedFromSeparateTraces) {
            result.text = "此 CSV 只有逐列配對宣告，無法驗證兩條訊號是否同步；請匯入分開的 raw/reference 時序波形，未建立或套用 mV profile。"
            return
        }
        val currentFingerprint = RawSensorStore.open(this).getString("ecg_sensor_profile_fingerprint", "").orEmpty()
        if (currentFingerprint.isBlank() || dataset.sensorProfileFingerprint != currentFingerprint) {
            result.text = "校準來源指紋與目前手錶 ECG 感測設定不符或缺失；拒絕建立／套用 mV profile。請以目前配對手錶重新收集同步參考資料。"
            return
        }
        val trainRaw = dataset.training.map { it.raw }
        val trainMv = dataset.training.map { it.millivolts }
        val holdoutRaw = dataset.holdout.map { it.raw }
        val holdoutMv = dataset.holdout.map { it.millivolts }
        val fit = EcgResearchCalibration.fitRawToMillivolts(trainRaw, trainMv)
        val validation = EcgResearchCalibration.validateIndependentHoldout(
            trainRaw, trainMv, holdoutRaw, holdoutMv, dataset.maximumAllowedRmseMv
        )
        if (!fit.usable || !validation.usable || !validation.passed) {
            result.text = "自動擬合／獨立驗證未通過：${if (!fit.usable) fit.note else validation.note}；留出 RMSE ${validation.rmseMv?.let { "%.5f".format(it) } ?: "未計算"} mV。未解鎖 mV。"
            return
        }
        val store = EcgResearchProfileStore(this)
        val saved = store.validateAndSaveIndependent(
            trainRaw = trainRaw,
            trainReferenceMv = trainMv,
            holdoutRaw = holdoutRaw,
            holdoutReferenceMv = holdoutMv,
            maximumAllowedRmseMv = dataset.maximumAllowedRmseMv,
            source = "paired_csv_sha256:${dataset.sha256}",
            referenceInstrument = dataset.referenceInstrument,
            protocolId = dataset.protocolId,
            sensorProfileFingerprint = dataset.sensorProfileFingerprint,
            trainingCaptureId = dataset.trainingCaptureId,
            holdoutCaptureId = dataset.holdoutCaptureId,
            maximumPairSkewNanos = dataset.maximumPairSkewNanos,
            trainingRawTimestampsNanos = dataset.training.map { it.rawTimestampNanos },
            trainingReferenceTimestampsNanos = dataset.training.map { it.referenceTimestampNanos },
            holdoutRawTimestampsNanos = dataset.holdout.map { it.rawTimestampNanos },
            holdoutReferenceTimestampsNanos = dataset.holdout.map { it.referenceTimestampNanos }
        )
        val profile = store.read()?.takeIf {
            saved && it.isEligibleForResearchMv && it.isBoundToSensorProfile(currentFingerprint)
        }
        if (profile == null) {
            result.text = "計算完成但 profile 重驗／儲存失敗；未套用 mV。"
            return
        }
        val rawPrefs = RawSensorStore.open(this)
        val rawResearch = if (EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(
            rawPrefs.getString("ecg_research_waveform_encoding", null),
            rawPrefs.getString("ecg_research_decode_status", null)
        )) SensorSuiteCsvParser.parseFloats(rawPrefs.getString("ecg_research_waveform", "").orEmpty()) else emptyList()
        val mV = profile.takeIf {
            rawPrefs.getString("ecg_research_waveform_encoding", "") == it.inputEncoding
        }?.apply(rawResearch).orEmpty()
        val persisted = mV.size >= 32 && rawPrefs.edit()
            .putString("ecg_waveform", mV.joinToString(","))
            .putString("ecg_waveform_encoding", "ecg_external_reference_mv")
            .putString("ecg_status", "calibrated_external_reference")
            .putString("ecg_calibration_profile_id", profile.profileId)
            .putString("ecg_calibration", "paired_csv_sha256:${dataset.sha256};train_points=${dataset.training.size};holdout_points=${dataset.holdout.size};holdout_rmse_mv=${validation.rmseMv}")
            .commit()
        result.text = "自動擬合完成；留出資料符合匯入檔宣告的 RMSE 門檻：訓練 ${dataset.training.size} 點、獨立留出 ${dataset.holdout.size} 點，RMSE=${"%.5f".format(validation.rmseMv)} mV。" +
            (if (persisted) "現有相同編碼波形已轉成研究 mV 尺度。" else "profile 已保存；目前沒有相同編碼波形可重算。") +
            " 檔案時間戳及配對宣告未經外部稽核；不是 OEM／臨床校準或診斷。"
        refreshAll()
    }

    private fun disclaimerBanner(): TextView = TextView(this).apply {
        text = ClinicalVerdict.DISCLAIMER
        textSize = 13f
        setTextColor(Color.rgb(255, 210, 140))
        setBackgroundColor(Color.rgb(36, 28, 16))
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun refreshAll() {
        cards.values.forEach { it.refresh() }
        refreshEcgEventRate()
    }

    private fun refreshEcgEventRate() {
        val prefs = RawSensorStore.open(this@ClinicalLabActivity)
        val timestamps = prefs
            .getString("ecg_event_timestamps_ns", "").orEmpty().split(',').mapNotNull(String::toLongOrNull)
        val sampleCounts = prefs
            .getString("ecg_event_sample_counts", "").orEmpty().split(',').mapNotNull(String::toIntOrNull)
        val waveformEncoding = prefs.getString("ecg_waveform_encoding", "").orEmpty()
        val rate = EcgResearchCalibration.estimateEventRateNs(timestamps)
        val pointRate = EcgResearchCalibration.estimatePackedPointRateNs(timestamps, sampleCounts, waveformEncoding)
        val halPeriod = prefs.getFloat("ecg_hal_sample_period_s", Float.NaN)
        ecgEventRate.text = rate.hz?.let {
                "Sensor HAL ECG 事件頻率：%.1f Hz（%d 個事件）\n波形點速率：%s\nHAL TYPE_SAMPLING：%s（非裝置級 ADC 校準）".format(
                it, timestamps.size, pointRate.hz?.let { hz -> "%.1f 點/秒".format(hz) } ?: "尚無可信估算",
                halPeriod.takeIf { period -> period.isFinite() && period > 0f }
                    ?.let { period -> "%.3f ms".format(period * 1000f) } ?: "未提供"
            )
        } ?: "尚無足夠 ECG 事件時間戳可估算頻率"
    }

    private inner class ModalityCard(val modality: Modality) {
        val chart = MedicalChartView(this@ClinicalLabActivity)
        val verdictTitle = TextView(this@ClinicalLabActivity)
        val verdictBody = TextView(this@ClinicalLabActivity)
        val meta = TextView(this@ClinicalLabActivity)
        lateinit var measureButton: Button
        val root: LinearLayout = LinearLayout(this@ClinicalLabActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(14, 22, 34))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(LinearLayout(this@ClinicalLabActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(MedicalIconView(this@ClinicalLabActivity, modality.id, modality.color), LinearLayout.LayoutParams(dp(42), dp(42)))
                addView(LinearLayout(this@ClinicalLabActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, 0, 0)
                    addView(TextView(this@ClinicalLabActivity).apply {
                        text = "${modality.badge}  ${modality.title}"
                        textSize = 18f
                        setTextColor(Color.WHITE)
                    })
                    addView(TextView(this@ClinicalLabActivity).apply {
                        text = "原始資料與分析狀態"
                        textSize = 12f
                        setTextColor(Color.rgb(140, 170, 190))
                    })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Button(this@ClinicalLabActivity).apply {
                    this@ModalityCard.measureButton = this
                    text = "量測"
                    textSize = 13f
                    isAllCaps = false
                    setTextColor(Color.BLACK)
                    setBackgroundColor(modality.color)
                    setOnClickListener { measure() }
                })
            })
            if (modality.id == "ecg") {
                addView(TextView(this@ClinicalLabActivity).apply {
                    text = "原廠量測提示：將中指與無名指接觸手錶兩個按鍵，手臂平放並保持靜止；心率低於 50 或高於 150 次／分時，房顫結果可能無法判定。Mi Fitness 的 ECG 卡片、量測提醒與手錶啟用狀態分開判斷；本研究頁不會變更原廠授權，也不提供房顫診斷。"
                    textSize = 12f
                    setTextColor(Color.rgb(255, 210, 140))
                    setPadding(0, dp(10), 0, 0)
                })
            }
            addView(chart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160)).apply {
                topMargin = dp(12)
            })
            addView(verdictTitle.apply {
                textSize = 20f
                setTextColor(modality.color)
                setPadding(0, dp(12), 0, 0)
            })
            addView(verdictBody.apply {
                textSize = 14f
                setTextColor(Color.rgb(220, 230, 240))
                setPadding(0, dp(6), 0, 0)
            })
            addView(meta.apply {
                textSize = 12f
                setTextColor(Color.rgb(130, 155, 175))
                setPadding(0, dp(8), 0, 0)
            })
            addView(TextView(this@ClinicalLabActivity).apply {
                text = ClinicalVerdict.DISCLAIMER
                textSize = 11f
                setTextColor(Color.rgb(200, 160, 100))
                setPadding(0, dp(10), 0, 0)
            })
            setOnClickListener {
                if (modality.id != "suite" && modality.id != "temperature") {
                    startActivity(Intent(this@ClinicalLabActivity, RawSensorDetailActivity::class.java).putExtra("kind", kindKey()))
                }
            }
        }

        fun measure() {
            measureButton.isEnabled = false
            measureButton.text = "連線中…"
            verdictTitle.text = "等待手錶回覆"
            verdictBody.text = "正在搜尋手錶並傳送量測命令；命令送達不代表量測已完成。"
            val delay = when (modality.id) {
                "suite" -> 210_000L
                "temperature" -> 50_000L
                "ppg" -> 40_000L
                else -> 35_000L
            }
            sendWatchCommand(modality.command) { outcome ->
                measureButton.isEnabled = true
                measureButton.text = "量測"
                if (outcome !is WearCommandDispatcher.Outcome.Sent) {
                    verdictTitle.text = "量測命令未送達"
                    verdictBody.text = WearCommandDispatcher.feedback(outcome)
                    return@sendWatchCommand
                }
                verdictTitle.text = "命令已送達 · 等待資料"
                verdictBody.text = WearCommandDispatcher.feedback(
                    outcome,
                    "量測命令已送達手錶；等待感測資料同步。送達不代表量測完成。"
                )
                handler.postDelayed({ refresh(); refreshEcgEventRate() }, delay)
                handler.postDelayed({ refresh(); refreshEcgEventRate() }, minOf(12_000L, delay / 2))
            }
        }

        fun refresh() {
            val prefs = RawSensorStore.open(this@ClinicalLabActivity)
            val feature = getSharedPreferences(WearDataProcessor.FEATURE_PREFS, MODE_PRIVATE)
            val wear = when {
                prefs.contains("${kindKey()}_wear_qualified") -> prefs.getBoolean("${kindKey()}_wear_qualified", false)
                feature.contains("worn") -> feature.getBoolean("worn", false)
                else -> null
            }
            val verdict = when (modality.id) {
                "ppg" -> {
                    val values = wave("ppg")
                    val rate = prefs.getFloat("ppg_sample_rate", 0f)
                    val status = prefs.getString("ppg_status", "not_measured").orEmpty()
                    val score = prefs.getInt("ppg_quality_score", 0)
                    val cal = prefs.getString("ppg_calibration", "").orEmpty()
                    val pr = Regex("pr=([0-9.]+)").find(cal)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                    val confidence = prefs.getInt("ppg_confidence_score", -1).takeIf { it in 1..100 }
                        ?: Regex("confidence=([0-9]+)").find(cal)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    val source = Regex("source=([^;\\n]+)").find(cal)?.groupValues?.getOrNull(1) ?: "PPG_RAW_OR_RESEARCH"
                    val timestampSource = Regex("timestamp=([^;\\n]+)").find(cal)?.groupValues?.getOrNull(1) ?: "UNKNOWN"
                    val calibrationStatus = Regex("calibration=([^;\\n]+)").find(cal)?.groupValues?.getOrNull(1) ?: "UNVERIFIED"
                    val healthServicesRate = feature.getFloat("whs_heart_rate", Float.NaN)
                        .takeIf {
                            it.isFinite() && it in 30f..220f &&
                                HealthAnalysisFreshnessPolicy.isFreshMeasurement(
                                    feature.getLong("whs_heart_rate_timestamp", 0L),
                                    feature.getString("whs_heart_rate_timestamp_source", null),
                                    System.currentTimeMillis()
                                )
                        }
                        ?.toDouble()
                    val displayWaveform = SensorSuiteCsvParser.parseFloats(
                        prefs.getString("ppg_display_waveform", "").orEmpty()
                    ).ifEmpty { values }
                    chart.setWaveform(displayWaveform.takeLast(256), modality.color, "ppg")
                    ClinicalVerdict.forPpg(
                        score, rawFiniteCount(prefs, "ppg"), rate, status, wear, pr,
                        hasReceivedSignal = receivedDataCount(prefs, "ppg") > 0,
                        healthServicesHeartRateBpm = healthServicesRate,
                        confidenceScore = confidence,
                        source = source,
                        timestampSource = timestampSource,
                        calibrationStatus = calibrationStatus,
                        indexPeriodSamples = prefs.getInt("ppg_dominant_period_samples", 0).takeIf { it > 0 },
                        periodCorrelation = prefs.getString("ppg_period_correlation", null)?.toDoubleOrNull(),
                        relativeAcDcRatio = prefs.getFloat("ppg_formula_relative_ac_dc", Float.NaN)
                            .takeIf { it.isFinite() }?.toDouble()
                    )
                }
                "ecg" -> {
                    var values = wave("ecg")
                    val researchValues = if (EcgPayloadInterpretationPolicy.canUsePersistedResearchWaveform(
                        prefs.getString("ecg_research_waveform_encoding", null),
                        prefs.getString("ecg_research_decode_status", null)
                    ))
                        SensorSuiteCsvParser.parseFloatsWithGapMarkers(prefs.getString("ecg_research_waveform", "").orEmpty()) else emptyList()
                    val rawValues = rawWave("ecg")
                    val encoding = prefs.getString("ecg_waveform_encoding", "").orEmpty()
                    val waveformVerified = EcgPayloadInterpretationPolicy.canInterpretAsWaveform(encoding)
                    val researchWaveform = EcgPayloadInterpretationPolicy.canInterpretAsResearchWaveform(encoding)
                    val currentFingerprint = prefs.getString("ecg_sensor_profile_fingerprint", "").orEmpty()
                    val profile = EcgResearchProfileStore(this@ClinicalLabActivity).read()
                        ?.takeIf { it.isBoundToSensorProfile(currentFingerprint) }
                    val activeProfile = profile?.takeIf {
                        encoding == "ecg_external_reference_mv" &&
                            prefs.getString("ecg_calibration_profile_id", "") == it.profileId
                    }
                    var isProfileMv = activeProfile != null && researchValues.isNotEmpty() && researchValues.all(Float::isFinite)
                    if (isProfileMv) {
                        val recalculated = activeProfile?.apply(researchValues).orEmpty()
                        if (recalculated.size == researchValues.size) values = recalculated else isProfileMv = false
                    }
                    val externalProfile = activeProfile.takeIf { isProfileMv }
                    if (encoding == "ecg_external_reference_mv" && !isProfileMv) {
                        values = researchValues.ifEmpty { rawValues }
                    }
                    val rate = prefs.getFloat("ecg_sample_rate", 0f)
                    val status = if (researchWaveform && (encoding != "ecg_external_reference_mv" || isProfileMv)) prefs.getString("ecg_status", "research_signal_unscaled").orEmpty()
                        else "research_signal_unscaled"
                    val score = if (researchWaveform) prefs.getInt("ecg_quality_score", SensorHealthAlgorithms.waveformQuality(values, rate, "ecg").score) else 0
                    val cal = prefs.getString("ecg_calibration", "").orEmpty()
                    val hr = if (researchWaveform) Regex("hr=([0-9.]+)").find(cal)?.groupValues?.getOrNull(1)?.toDoubleOrNull() else null
                    val rr = if (researchWaveform) Regex("rr=([0-9]+)").find(cal)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0 else 0
                    val displayResearchWaveform = researchValues.ifEmpty { values }
                    chart.setWaveform(if (isProfileMv) values.takeLast(256)
                        else displayResearchWaveform.takeLast(256), modality.color,
                        if (isProfileMv) "研究 mV（外部 profile）" else if (waveformVerified) "ecg" else "研究波形 · 相對／索引單位")
                    ClinicalVerdict.forEcg(
                        score,
                        maxOf(rawFiniteCount(prefs, "ecg"), displayResearchWaveform.count(Float::isFinite)),
                        rate,
                        if (isProfileMv) "calibrated_external_reference" else status,
                        hr,
                        rr,
                        wear,
                        if (!researchWaveform) listOf("ECG event.values 格式未映射；保留原始事件，等待研究解碼")
                        else externalProfile?.let { listOf("外部參考 profile ${it.profileId}：${it.source} · 訓練 ${it.points} 點 · 獨立留出 ${it.validatedPoints} 點 · RMSE ${"%.5f".format(it.validationRmseMv)} mV（研究尺度預覽）") }.orEmpty(),
                        waveformEncoding = encoding,
                        timelineSource = prefs.getString("ecg_research_timeline_source", "NORMALIZED_INDEX").orEmpty(),
                        externalCalibrationVerified = isProfileMv,
                        rhythmPatternTitle = prefs.getString("ecg_research_rhythm_pattern_title", null)
                    )
                }
                "bioz" -> {
                    val values = wave("bioz")
                    val status = prefs.getString("bioz_status", "not_measured").orEmpty()
                    val score = prefs.getInt("bioz_quality_score", 0)
                    val cal = prefs.getString("bioz_calibration", "").orEmpty()
                    val z = Regex("Z=([0-9.]+)").find(cal)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                    val r = Regex("R=([0-9.]+)").find(cal)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                    val x = Regex("X=([0-9.]+)").find(cal)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                    val hyd = Regex("hydration=([0-9]+)").find(cal)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    val waveformPosition = prefs.getInt("bioz_waveform_median_position_index", -1).takeIf { it in 0..100 }
                        ?: Regex("waveform_median_position=([0-9]+)").find(cal)?.groupValues?.getOrNull(1)?.toIntOrNull()
                            ?.takeIf { it in 0..100 }
                    val change = prefs.getFloat("bioz_relative_change_percent", Float.NaN).toDouble()
                        .takeIf(Double::isFinite)
                        ?: Regex("relative_change_percent=([-+0-9.]+)").find(cal)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                            ?.takeIf(Double::isFinite)
                    val stability = prefs.getInt("bioz_stability_score", -1).takeIf { it in 0..100 }
                        ?: Regex("stability=([0-9]+)").find(cal)?.groupValues?.getOrNull(1)?.toIntOrNull()
                            ?.takeIf { it in 0..100 }
                    val confidence = prefs.getInt("bioz_confidence_score", -1).takeIf { it in 1..100 }
                        ?: Regex("confidence=([0-9]+)").find(cal)?.groupValues?.getOrNull(1)?.toIntOrNull()
                            ?.takeIf { it in 1..100 }
                    val rawChannels = BiozRawChannelEvidence.decode(cal)
                    val biozResearch = BiomedicalCalibration.calibrateBioz(
                        rawChannels.mapNotNull { it.second?.takeIf(Double::isFinite) },
                        values
                    )
                    val displayMetrics = BiozResearchDisplayPolicy.resolve(
                        biozResearch,
                        BiozResearchDisplayMetrics(score, waveformPosition, change, stability, confidence)
                    )
                    chart.setWaveform(values.takeLast(256), modality.color, "bioz")
                    ClinicalVerdict.forBioz(
                        displayMetrics.qualityScore ?: 0, z, r, x, hyd, status,
                        comparisons = biozResearch.comparisons,
                        rawSampleCount = rawFiniteCount(prefs, "bioz"),
                        hasReceivedSignal = receivedDataCount(prefs, "bioz") > 0,
                        waveformMedianPositionIndex = displayMetrics.waveformMedianPositionIndex,
                        relativeChangePercent = displayMetrics.relativeChangePercent,
                        stabilityScore = displayMetrics.stabilityScore,
                        confidenceScore = displayMetrics.confidenceScore,
                        timestampSource = prefs.getString("bioz_timestamp_source", null),
                        source = prefs.getString("bioz_source_sensor", "UNKNOWN").orEmpty(),
                        measuredAtMillis = prefs.getLong("bioz_time", 0L),
                        rawChannelValues = rawChannels
                    )
                }
                "temperature" -> {
                    val now = System.currentTimeMillis()
                    fun rawChannel(kind: String, value: Double?) = TemperaturePresentation.Channel(
                        kind, value, prefs.getLong("${kind}_time", 0L),
                        prefs.getString("${kind}_timestamp_source", null),
                        prefs.getString("${kind}_status", "not_measured").orEmpty(),
                        rawFiniteCount(prefs, kind), wave(kind).filter { it.isFinite() }.takeLast(256)
                    )
                    val temperatureChannels = listOf(
                        rawChannel("thermometer", SensorSuiteCsvParser.parse(
                            prefs.getString("thermometer_latest", "").orEmpty()
                        ).firstOrNull()),
                        TemperaturePresentation.Channel(
                        "feature temperature / ${feature.getString("temperature_source", "unknown")}",
                        feature.getFloat("temperature", Float.NaN).toDouble(),
                        feature.getLong("temperature_timestamp", 0L),
                        feature.getString("temperature_timestamp_source", null),
                        "feature_snapshot", 0, emptyList(),
                        feature.getString("temperature_unit_status", "unverified").orEmpty(),
                        feature.getString("temperature_aggregation_method", "unreported").orEmpty(),
                        feature.getInt("temperature_aggregation_sample_count", 0)
                    ),
                        rawChannel("body_temperature_raw",
                            prefs.getFloat("body_temperature_raw_average", Float.NaN).toDouble())
                    )
                    val selected = TemperaturePresentation.select(temperatureChannels, now)
                    val measured = selected?.waveform.orEmpty().ifEmpty {
                        selected?.value?.let { listOf(it.toFloat()) }.orEmpty()
                    }
                    chart.setWaveform(measured, modality.color, "temperature raw / 單位未驗證")
                    val result = ClinicalVerdict.forTemperature(
                        selected?.value, selected?.count ?: 0, selected?.status ?: "not_measured", wear,
                        source = selected?.source ?: "NO_FINITE_TEMPERATURE_SOURCE",
                        measuredAtMillis = selected?.measuredAt ?: 0L,
                        timestampSource = selected?.timestampSource,
                        calibrationStatus = selected?.unitStatus ?: "UNVERIFIED"
                    )
                    val details = selected?.let {
                        val time = if (it.measuredAt > 0L) java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss Z", java.util.Locale.getDefault()
                        ).format(java.util.Date(it.measuredAt)) else "unknown"
                        "量測時間=$time · ${TemperaturePresentation.metadata(it, now)}" +
                            ResearchSignalSummary.describe(ResearchSignalSummary.analyze(measured.map { value -> value.toDouble() }, false))
                    } ?: "無有限溫度原始值；量測時間與來源不可用"
                    val channelReadings = temperatureChannels.joinToString("\n") { channel ->
                        "${channel.source}：${channel.value?.takeIf { it.isFinite() }?.let { "%.3f".format(it) } ?: "無有限值"}" +
                            "（原始編碼；單位未驗證） · ${TemperaturePresentation.freshness(channel, now)}"
                    }
                    result.copy(findings = result.findings + details + channelReadings)
                }
                "oxygen" -> {
                    val rawKind = Spo2ChannelSelectionPolicy.selectForStoredData(
                        preferredSpo2Kind,
                        Spo2ChannelSelectionPolicy.KINDS.associateWith { receivedDataCount(prefs, it) },
                        Spo2ChannelSelectionPolicy.KINDS.associateWith { prefs.getLong("${it}_time", 0L) }
                    )
                    val rawValues = rawKind?.let(::wave).orEmpty()
                    val rawStatus = rawKind?.let { prefs.getString("${it}_status", "has_data") }.orEmpty()
                    chart.setWaveform(rawValues.takeLast(256), modality.color, "spo2 raw / a.u.")
                    val verdict = ClinicalVerdict.forRawSpO2(
                        rawKind?.let { rawFiniteCount(prefs, it) } ?: 0,
                        rawStatus,
                        wear,
                        hasReceivedSignal = rawKind != null,
                        researchEvidenceScore = rawKind?.let { selectedKind ->
                            val raw = rawValues.filter { it.isFinite() && it != 524287f }
                            val finiteRatio = if (rawValues.isEmpty()) null else raw.size.toDouble() / rawValues.size
                            val timeEvidence = if (HealthAnalysisFreshnessPolicy.hasTrustedSensorTimestamp(
                                    prefs.getString("${selectedKind}_timestamp_source", null)
                                ) && prefs.getLong("${selectedKind}_time", 0L) > 0L
                            ) 100 else 0
                            ResearchQualityIndexCalculator.calculate(
                                finiteRatio = finiteRatio,
                                sampleCount = raw.size,
                                waveformStability = null,
                                sourceEvidence = 80,
                                timeEvidence = timeEvidence
                            ).score
                        },
                        source = rawKind?.let { prefs.getString("${it}_source_sensor", it) } ?: "NO_SPO2_SOURCE",
                        measuredAtMillis = rawKind?.let { prefs.getLong("${it}_time", 0L) } ?: 0L,
                        timestampSource = rawKind?.let { prefs.getString("${it}_timestamp_source", null) },
                        calibrationStatus = "OEM_PERCENTAGE_CURVE_UNVERIFIED"
                    )
                    val otherAvailable = if (rawKind == null && preferredSpo2Kind in Spo2ChannelSelectionPolicy.KINDS) {
                        Spo2ChannelSelectionPolicy.otherAvailableKinds(
                            preferredSpo2Kind,
                            Spo2ChannelSelectionPolicy.KINDS.associateWith { receivedDataCount(prefs, it) }
                        )
                    } else emptyList()
                    if (otherAvailable.isEmpty()) verdict else verdict.copy(
                        findings = verdict.findings + "目前選定的 ${if (preferredSpo2Kind == "spo2_raw") "SpO₂ 原始通道" else "小米 SpO₂ 通道"} 無資料；另有 ${otherAvailable.joinToString("、") { if (it == "spo2_raw") "SpO₂ 原始通道" else "小米 SpO₂ 通道" }} 已收到原始資料。請返回感測資料整理改選；原始通道不代表血氧百分比。"
                    )
                }
                else -> {
                    // suite
                    null
                }
            } ?: run {
                    val with = WatchSensorCatalog.items.count { receivedDataCount(prefs, it.kind) > 0 }
                    val worn = if (feature.contains("worn")) feature.getBoolean("worn", false) else null
                    val ppgValues = wave("ppg")
                    val ecgValues = wave("ecg")
                    val activeEcgProfile = activeEcgResearchProfile(prefs)
                    val savedEcgStatus = prefs.getString("ecg_status", "not_measured").orEmpty()
                    val ecgStatus = if (savedEcgStatus == "calibrated_external_reference" && activeEcgProfile == null) {
                        "research_signal_unscaled"
                    } else savedEcgStatus
                    val biozValues = wave("bioz")
                    val now = System.currentTimeMillis()
                    val tempC = feature.getFloat("temperature", Float.NaN).toDouble().takeIf {
                        it.isFinite() && HealthAnalysisFreshnessPolicy.isFreshSensor(
                            feature.getLong("temperature_timestamp", 0L),
                            feature.getString("temperature_timestamp_source", null), now, 24 * 60 * 60 * 1000L
                        )
                    } ?: prefs.getFloat("body_temperature_raw_average", Float.NaN).toDouble().takeIf {
                        it.isFinite() && HealthAnalysisFreshnessPolicy.isFreshSensor(
                            prefs.getLong("body_temperature_raw_time", 0L),
                            prefs.getString("body_temperature_raw_timestamp_source", null), now, 24 * 60 * 60 * 1000L
                        )
                    }
                    val biozCal = prefs.getString("bioz_calibration", "").orEmpty()
                    val ecgCal = prefs.getString("ecg_calibration", "").orEmpty()
                    val ppgCal = prefs.getString("ppg_calibration", "").orEmpty()
                    chart.setWaveform(
                        when {
                            ppgValues.isNotEmpty() -> ppgValues.takeLast(256)
                            ecgValues.isNotEmpty() -> ecgValues.takeLast(256)
                            biozValues.isNotEmpty() -> biozValues.takeLast(256)
                            else -> emptyList()
                        },
                        modality.color,
                        "scan"
                    )
                    ClinicalVerdict.forSuite(
                        withData = with,
                        total = WatchSensorCatalog.items.size,
                        worn = worn,
                        ppg = ClinicalVerdict.forPpg(
                            prefs.getInt("ppg_quality_score", 0),
                            rawFiniteCount(prefs, "ppg"),
                            prefs.getFloat("ppg_sample_rate", 0f),
                            prefs.getString("ppg_status", "not_measured").orEmpty(),
                            worn,
                            Regex("pr=([0-9.]+)").find(ppgCal)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                            hasReceivedSignal = receivedDataCount(prefs, "ppg") > 0,
                            indexPeriodSamples = prefs.getInt("ppg_dominant_period_samples", 0).takeIf { it > 0 },
                            periodCorrelation = prefs.getString("ppg_period_correlation", null)?.toDoubleOrNull(),
                            relativeAcDcRatio = prefs.getFloat("ppg_formula_relative_ac_dc", Float.NaN)
                                .takeIf { it.isFinite() }?.toDouble()
                        ).takeIf { receivedDataCount(prefs, "ppg") > 0 },
                        ecg = ClinicalVerdict.forEcg(
                            prefs.getInt("ecg_quality_score", 0),
                            rawFiniteCount(prefs, "ecg"),
                            prefs.getFloat("ecg_sample_rate", 0f),
                            ecgStatus,
                            Regex("hr=([0-9.]+)").find(ecgCal)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                            Regex("rr=([0-9]+)").find(ecgCal)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0,
                            worn,
                            waveformEncoding = if (activeEcgProfile != null) "ecg_external_reference_mv"
                                else prefs.getString("ecg_research_waveform_encoding", ""),
                            timelineSource = prefs.getString("ecg_research_timeline_source", "NORMALIZED_INDEX").orEmpty(),
                            externalCalibrationVerified = activeEcgProfile != null
                        ).takeIf { receivedDataCount(prefs, "ecg") > 0 },
                        bioz = ClinicalVerdict.forBioz(
                            prefs.getInt("bioz_quality_score", 0),
                            Regex("Z=([0-9.]+)").find(biozCal)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                            Regex("R=([0-9.]+)").find(biozCal)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                            Regex("X=([0-9.]+)").find(biozCal)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                            Regex("hydration=([0-9]+)").find(biozCal)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                            prefs.getString("bioz_status", "not_measured").orEmpty(),
                            rawSampleCount = rawFiniteCount(prefs, "bioz"),
                            hasReceivedSignal = receivedDataCount(prefs, "bioz") > 0,
                            timestampSource = prefs.getString("bioz_timestamp_source", null),
                            source = prefs.getString("bioz_source_sensor", "UNKNOWN").orEmpty(),
                            measuredAtMillis = prefs.getLong("bioz_time", 0L)
                        ).takeIf { biozValues.isNotEmpty() || biozCal.isNotBlank() },
                        temperature = ClinicalVerdict.forTemperature(
                            tempC,
                            rawFiniteCount(prefs, "body_temperature_raw"),
                            prefs.getString("body_temperature_raw_status", if (tempC != null) "has_data" else "not_measured").orEmpty(),
                            worn
                        ).takeIf { tempC != null }
                    )
            }
            val outcome = outcomeFor(prefs)
            verdictTitle.text = outcome.label
            verdictBody.text = "${outcome.conclusion}\n\n${outcome.nextAction}"
            meta.text = "${verdict.title} · ${modality.command}"
            verdictBody.setOnClickListener {
                startActivity(Intent(this@ClinicalLabActivity, RawSensorDetailActivity::class.java).putExtra("kind", kindKey()))
            }
        }

        private fun outcomeFor(
            prefs: android.content.SharedPreferences
        ): UserFacingMeasurementOutcome.Outcome {
            if (modality.id == "suite") {
                val received = WatchSensorCatalog.items.sumOf { receivedDataCount(prefs, it.kind) }
                return UserFacingMeasurementOutcome.from(
                    state = if (received > 0) SensorDisplayState.RAW_ONLY else SensorDisplayState.NOT_MEASURED,
                    metricKind = "suite",
                    sourceStatus = if (received > 0) "has_data" else "not_measured"
                )
            }
            val kind = kindKey()
            val status = prefs.getString("${kind}_status", "not_measured").orEmpty()
            val state = SensorDisplayState.from(
                samples = receivedDataCount(prefs, kind),
                status = status,
                measuredAt = prefs.getLong("${kind}_time", 0L),
                now = System.currentTimeMillis(),
                freshnessMs = SensorDisplayState.APP_DISPLAY_FRESHNESS_MILLIS,
                kind = kind,
                timestampSource = prefs.getString("${kind}_timestamp_source", null)
            )
            return UserFacingMeasurementOutcome.from(state, kind, status)
        }

        private fun wave(kind: String): List<Float> {
            val prefs = RawSensorStore.open(this@ClinicalLabActivity)
            if (kind == "bioz") {
                val indexed = SensorSuiteCsvParser.parseFloatsWithGapMarkers(
                    prefs.getString("bioz_display_waveform", "").orEmpty()
                )
                if (indexed.isNotEmpty()) return indexed
            }
            return SensorSuiteCsvParser.parseFloats(prefs.getString("${kind}_waveform", "").orEmpty())
        }

        private fun rawFiniteCount(prefs: android.content.SharedPreferences, kind: String): Int =
            if (RawSensorPayloadPolicy.isBiomedicalKind(kind)) {
                prefs.getInt("${kind}_raw_value_count", -1).takeIf { it >= 0 } ?: 0
            } else {
                prefs.getInt("${kind}_samples", 0).coerceAtLeast(0)
            }

        private fun receivedDataCount(prefs: android.content.SharedPreferences, kind: String): Int =
            RawSensorPayloadPolicy.displayPresenceCount(
                prefs.getInt("${kind}_count_contract_version", 0),
                prefs.getInt("${kind}_raw_value_count", -1).takeIf { it >= 0 },
                prefs.getInt("${kind}_event_count", 0),
                prefs.getInt("${kind}_preview_point_count", 0),
                prefs.getInt("${kind}_samples", 0)
            )

        private fun rawWave(kind: String): List<Float> {
            val prefs = RawSensorStore.open(this@ClinicalLabActivity)
            return SensorSuiteCsvParser.parseFloats(prefs.getString("${kind}_raw_waveform", "").orEmpty())
        }

        private fun kindKey(): String {
            if (modality.id == "oxygen") {
                val prefs = RawSensorStore.open(this@ClinicalLabActivity)
                return Spo2ChannelSelectionPolicy.selectForStoredData(
                    preferredSpo2Kind,
                    Spo2ChannelSelectionPolicy.KINDS.associateWith { receivedDataCount(prefs, it) },
                    Spo2ChannelSelectionPolicy.KINDS.associateWith { prefs.getLong("${it}_time", 0L) }
                ) ?: preferredSpo2Kind ?: "mi_spo2"
            }
            return when (modality.id) {
            "temperature" -> "body_temperature_raw"
            "suite" -> "ppg"
            else -> modality.id
            }
        }
    }

    private fun watchFeaturePanel(): LinearLayout {
        val features = WatchFeatureInventory.entries()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(14, 22, 34))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(TextView(this@ClinicalLabActivity).apply {
                text = "手錶功能開通狀態"
                textSize = 18f
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@ClinicalLabActivity).apply {
                text = "可公開 API 已盡量開啟；原廠私有／無感測器項目標示為無法開啟。"
                textSize = 12f
                setTextColor(Color.rgb(140, 170, 190))
                setPadding(0, dp(6), 0, dp(10))
            })
            features.forEach { item ->
                addView(TextView(this@ClinicalLabActivity).apply {
                    text = "${item.statusGlyph}  ${item.name}\n    ${item.detail}"
                    textSize = 13f
                    setTextColor(
                        when (item.state) {
                            WatchFeatureInventory.State.ON -> Color.rgb(90, 220, 160)
                            WatchFeatureInventory.State.PARTIAL -> Color.rgb(255, 200, 100)
                            WatchFeatureInventory.State.OFF -> Color.rgb(255, 120, 120)
                            WatchFeatureInventory.State.UNAVAILABLE -> Color.rgb(130, 140, 155)
                        }
                    )
                    setPadding(0, dp(6), 0, dp(6))
                })
            }
            addView(Button(this@ClinicalLabActivity).apply {
                text = "開啟手錶可公開功能"
                isAllCaps = false
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(0, 120, 100))
                setOnClickListener { sendWatchCommand("/control/enable-open-features") }
            }, margins(top = 10))
            addView(Button(this@ClinicalLabActivity).apply {
                text = "重新註冊背景同步／被動監測"
                isAllCaps = false
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(30, 70, 90))
                setOnClickListener { sendWatchCommand("/control/register-passive") }
            }, margins(top = 8))
        }
    }

    private fun sendWatchCommand(
        path: String,
        onOutcome: (WearCommandDispatcher.Outcome) -> Unit = { outcome ->
            if (outcome !is WearCommandDispatcher.Outcome.Sent) {
                android.widget.Toast.makeText(
                    this,
                    WearCommandDispatcher.feedback(outcome),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    ) = WearCommandDispatcher.send(this, path, onOutcome)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun margins(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    data class Modality(val id: String, val badge: String, val title: String, val color: Int, val command: String)

    companion object {
        private val BG = Color.rgb(6, 10, 16)
    }
}

/** Simple medical glyph icons drawn with Path. */
class MedicalIconView(
    context: android.content.Context,
    private val kind: String,
    private val accent: Int
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent))
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, width * 0.46f, fill)
        when (kind) {
            "ppg", "ecg" -> {
                val path = Path()
                path.moveTo(width * 0.15f, cy)
                path.lineTo(width * 0.35f, cy)
                path.lineTo(width * 0.42f, cy - height * 0.28f)
                path.lineTo(width * 0.52f, cy + height * 0.30f)
                path.lineTo(width * 0.60f, cy - height * 0.12f)
                path.lineTo(width * 0.70f, cy)
                path.lineTo(width * 0.85f, cy)
                canvas.drawPath(path, paint)
            }
            "bioz" -> {
                canvas.drawCircle(cx - width * 0.15f, cy, width * 0.12f, paint)
                canvas.drawCircle(cx + width * 0.15f, cy, width * 0.12f, paint)
                canvas.drawLine(cx - width * 0.03f, cy, cx + width * 0.03f, cy, paint)
            }
            "temperature" -> {
                canvas.drawRoundRect(cx - 6f, height * 0.18f, cx + 6f, height * 0.62f, 6f, 6f, paint)
                canvas.drawCircle(cx, height * 0.72f, width * 0.14f, paint)
            }
            "oxygen" -> {
                canvas.drawCircle(cx, cy, width * 0.18f, paint)
                canvas.drawArc(
                    cx - width * 0.28f, cy - height * 0.28f, cx + width * 0.28f, cy + height * 0.28f,
                    200f, 140f, false, paint
                )
            }
            else -> {
                canvas.drawCircle(cx, cy, width * 0.22f, paint)
                canvas.drawCircle(cx, cy, width * 0.10f, paint)
            }
        }
    }
}

object WatchFeatureInventory {
    enum class State { ON, PARTIAL, OFF, UNAVAILABLE }

    data class Item(val name: String, val state: State, val detail: String) {
        val statusGlyph: String
            get() = when (state) {
                State.ON -> "[開]"
                State.PARTIAL -> "[部]"
                State.OFF -> "[關]"
                State.UNAVAILABLE -> "[無]"
            }
    }

    fun entries(): List<Item> = listOf(
        Item("心率 MeasureClient", State.ON, "手錶前景／掃描可量測並同步"),
        Item("步數／距離／卡路里被動同步", State.ON, "PassiveListener 已註冊公開型別"),
        Item("運動／GPS 步行", State.ON, "手錶可啟動步行工作階段"),
        Item("SpO₂ 原始擷取", State.PARTIAL, "HAL／OEM 頁可擷取；未公開百分比校準，Health Services 無標準型別"),
        Item("溫度原始通道", State.ON, "可擷取原始值；單位／整機校準未驗證，不換算體溫"),
        Item("PPG／ECG／BIOZ 原始訊號品質", State.ON, "研究實驗室＋手錶快捷鍵；不宣稱醫療校準"),
        Item("全感測器掃描", State.ON, "依序獨占 MAX86178"),
        Item("離腕／佩戴校正", State.ON, "Android off-body 語意"),
        Item("指南針／返航", State.ON, "手錶端獨立頁"),
        Item("跌倒風險（運動中）", State.ON, "步行／安全感測高衝擊提示已開"),
        Item("手勢／夜間省電", State.ON, "預設開啟；可在手錶主頁切換"),
        Item("靜止心率長期自動", State.UNAVAILABLE, "缺少可靠靜止狀態 API"),
        Item("HRV／逐拍 RR（Health Services）", State.UNAVAILABLE, "標準型別未暴露；僅波形研究估計"),
        Item("呼吸速率自動", State.UNAVAILABLE, "無公開感測器；可手動輸入"),
        Item("血壓硬體", State.UNAVAILABLE, "Watch 2 Pro 無血壓感測器／API"),
        Item("睡眠分期 REM／深睡", State.UNAVAILABLE, "HC 僅總時長級"),
        Item("壓力／PAI／身體電量（小米私有）", State.UNAVAILABLE, "無第三方 API，不破解 HAL")
    )
}
