package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.HealthAlgorithmEngine as ClinicalSensorFormulas

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * HRV / stress research page — completes when enough RR intervals exist (no fixed seconds).
 */
class WearHrvActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var probe: RawVitalsProbe? = null
    private var active = false
    private lateinit var headline: TextView
    private lateinit var detail: TextView
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(Color.BLACK)
            addView(TextView(context).apply {
                text = "HEART RATE VARIABILITY"; textSize = 10f; letterSpacing = .12f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(255, 180, 120))
            })
            addView(TextView(context).apply {
                text = "心率變化"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                setPadding(0, dp(6), 0, dp(6))
            })
            addView(TextView(context).apply {
                text = "戴好手錶並保持靜止，完成後查看本次結果。"
                textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.rgb(150, 158, 178))
            })
            addView(TextView(context).apply {
                text = "尚未量測"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                setPadding(0, dp(16), 0, dp(8))
            }.also { headline = it })
            addView(TextView(context).apply {
                text = "手錶貼合手腕，量測時保持不動。"
                textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.rgb(170, 180, 200))
            }.also { detail = it })
            addView(Button(context).apply {
                text = "開始 HRV 量測"; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#C47B3A"), Color.parseColor("#7A3E18"))
                ).apply { cornerRadius = dp(8).toFloat() }
                setOnClickListener { startCapture() }
            }.also { startButton = it }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(14) })
            addView(Button(context).apply {
                text = "查看原廠壓力量測"; isAllCaps = false; textSize = 13f
                contentDescription = "開啟小米健康的原廠壓力頁面"
                setOnClickListener {
                    stopCapture(publish = false, stopReason = "opened_oem_stress")
                    val result = runCatching {
                        startActivity(Intent().setClassName(
                            "com.xiaomi.wear.fitness",
                            "com.xiaomi.wear.fitness.health.stress.StressMainActivity"
                        ))
                    }
                    if (result.isFailure) {
                        detail.text = "原廠壓力頁無法開啟；請從小米健康手動查看。"
                    }
                }
            }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
            addView(TextView(context).apply {
                text = "本頁顯示心率變化參考；不代表疾病判斷。"
                textSize = 11f; setTextColor(Color.rgb(255, 190, 120)); setPadding(0, dp(14), 0, 0)
            })
        }
        WearUi.installScrollable(this, content)
    }

    override fun onStop() {
        super.onStop()
        // Keep until-data capture running across screen-off / ADB overlays.
        if (isFinishing) stopCapture(publish = false)
    }

    override fun onDestroy() {
        stopCapture(publish = false)
        super.onDestroy()
    }

    private fun startCapture() {
        stopCapture(publish = false)
        val kinds = listOf(
            RawVitalsProbe.Kind.MAX_HEART_RATE,
            RawVitalsProbe.Kind.HEART_RATE,
            RawVitalsProbe.Kind.PPG
        )
        var candidateTimeout: Runnable? = null
        fun tryKind(index: Int) {
            candidateTimeout?.let(handler::removeCallbacks)
            candidateTimeout = null
            if (index >= kinds.size) {
                active = false
                headline.text = "這次無法開始量測"
                detail.text = "請戴好手錶，開啟心率量測後再試一次。"
                startButton.text = "再次量測"
                return
            }
            val kind = kinds[index]
            val session = RawVitalsProbe(this, kind) { snap ->
                runOnUiThread {
                    if (!active) return@runOnUiThread
                    headline.text = "正在記錄心跳"
                    val optical = ClinicalSensorFormulas.opticalFromWaveform(snap.waveform.toList(), snap.sampleRateHz)
                    val hrv = ClinicalSensorFormulas.hrvFromRr(optical.rrIntervalsMs)
                    detail.text = "請保持靜止，量測即將完成。"
                    if (WearMeasurementPolicy.hasEnoughRrIntervalsForHrv(hrv.rrCount)) {
                        stopCapture(publish = true)
                    }
                }
            }
            if (!session.start()) {
                tryKind(index + 1)
                return
            }
            probe = session
            active = true
            startButton.text = "量測中…"
            headline.text = "量測中"
            detail.text = "請保持靜止。"
            val timeout = Runnable {
                if (!active || probe !== session) return@Runnable
                if (index + 1 < kinds.size) {
                    val candidateSnapshot = session.stop("candidate_timeout")
                    if (WearMeasurementPolicy.shouldPublishCandidateCapture(candidateSnapshot)) {
                        runCatching {
                            WearRawSensorPublisher.publish(
                                this@WearHrvActivity,
                                candidateSnapshot,
                                final = true,
                                chipPart = WearRawSensorPublisher.chipFor(kind),
                                featurePage = "HRV_CANDIDATE_TIMEOUT"
                            )
                        }
                    }
                    probe = null
                    active = false
                    tryKind(index + 1)
                } else {
                    stopCapture(publish = true, stopReason = "candidate_timeout")
                }
            }
            candidateTimeout = timeout
            handler.postDelayed(timeout, SENSOR_CANDIDATE_TIMEOUT_MILLIS)
        }
        tryKind(0)
    }

    private fun stopCapture(publish: Boolean, stopReason: String? = null) {
        handler.removeCallbacksAndMessages(null)
        active = false
        val session = probe ?: return
        probe = null
        val snap = session.stop(stopReason)
        val optical = ClinicalSensorFormulas.opticalFromWaveform(snap.waveform.toList(), snap.sampleRateHz)
        val hrv = ClinicalSensorFormulas.hrvFromRr(optical.rrIntervalsMs)
        val stressScore = if (WearMeasurementPolicy.canPublishResearchStressScore(
                snap.worn,
                ChipCalibrationProfiles.assessOpticalWaveform(snap.waveform.toList()).valid,
                hrv.rrCount
            )) ClinicalSensorFormulas.researchStressScore(hrv.nRmssd, hrv.bpm ?: optical.pulseRateBpm)
            else null
        val hasHrvReading = WearMeasurementPolicy.hasEnoughRrIntervalsForHrv(hrv.rrCount) &&
            hrv.rmssdMs?.isFinite() == true
        headline.text = when {
            hasHrvReading -> "HRV 量測完成"
            snap.samples > 0 || snap.eventCount > 0 -> "已收到脈搏訊號"
            else -> "這次沒有收到量測資料"
        }
        detail.text = if (hasHrvReading) WearHealthConclusion.forHrv(hrv.rmssdMs)
            else if (snap.samples > 0 || snap.eventCount > 0) "尚無可用的 HRV 讀值；可在手機查看訊號。"
            else "請戴好手錶，保持手腕靜止後重新量測。"
        startButton.text = "再次量測"
        if (publish && snap.samples > 0) {
            WearRawSensorPublisher.publish(
                this, snap, final = true,
                chipPart = WearRawSensorPublisher.chipFor(snap.kind),
                featurePage = "HRV",
                formulas = WearRawSensorPublisher.FormulaExtras(
                    pulseBpm = optical.pulseRateBpm,
                    rmssdMs = hrv.rmssdMs,
                    nRmssd = hrv.nRmssd,
                    sdnnMs = hrv.sdnnMs,
                    pnn50 = hrv.pNn50,
                    cvrr = hrv.cvRr,
                    rrCount = hrv.rrCount,
                    stressScore = stressScore,
                    note = optical.note,
                    citations = hrv.citations.joinToString(" | ")
                )
            )
            publishHrvExtras(snap, optical, hrv)
        }
    }

    private fun publishHrvExtras(
        snap: RawVitalsProbe.Snapshot,
        optical: ClinicalSensorFormulas.OpticalMetrics,
        hrv: ClinicalSensorFormulas.HrvMetrics
    ) {
        // Primary publish already carries formula extras; keep helper for API stability.
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val SENSOR_CANDIDATE_TIMEOUT_MILLIS = 15_000L
    }
}
