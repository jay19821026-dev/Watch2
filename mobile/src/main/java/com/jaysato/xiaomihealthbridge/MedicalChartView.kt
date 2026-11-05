package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Clinical-monitor style waveform with medical grid. */
class MedicalChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var values: List<Float> = emptyList()
    private var traceColor: Int = Color.rgb(0, 220, 160)
    private var modality: String = "ecg"
    private var axisDescription: String? = null

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 0, 220, 120)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val majorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 220, 120)
        strokeWidth = 1.6f
        style = Paint.Style.STROKE
    }
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3.2f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 180, 220, 200)
        textSize = 28f
    }

    fun setWaveform(samples: List<Float>, color: Int, kind: String, axisDescription: String? = null) {
        values = samples.takeLast(4096).toList()
        traceColor = color
        modality = kind
        this.axisDescription = axisDescription
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawColor(Color.rgb(8, 14, 22))
        val stepX = w / 20f
        val stepY = h / 8f
        var x = 0f
        var col = 0
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h, if (col % 5 == 0) majorGridPaint else gridPaint)
            x += stepX
            col++
        }
        var y = 0f
        var row = 0
        while (y <= h) {
            canvas.drawLine(0f, y, w, y, if (row % 2 == 0) majorGridPaint else gridPaint)
            y += stepY
            row++
        }
        canvas.drawLine(0f, h / 2f, w, h / 2f, baselinePaint)
        labelPaint.textSize = 12f * resources.displayMetrics.scaledDensity
        val title = when (modality) {
            "ecg" -> if (axisDescription == null) "ECG 心電波形" else "ECG 研究波形"
            "ppg" -> "PPG 脈波"
            "bioz" -> "BioZ 阻抗原始波形"
            else -> modality.uppercase()
        }
        canvas.drawText(title, 16f, labelPaint.textSize + 12f, labelPaint)
        val finiteValues = values.filterNot { RawWaveformDisplayPolicy.isGap(it, modality) }
        if (finiteValues.size < 2) {
            canvas.drawText("等待有效波形", 16f, h / 2f, labelPaint)
            return
        }
        val minV = finiteValues.min().toDouble()
        val maxV = finiteValues.max().toDouble()
        val span = max(1e-4, maxV - minV)
        tracePaint.color = traceColor
        val path = Path()
        var connected = false
        values.forEachIndexed { index, value ->
            if (RawWaveformDisplayPolicy.isGap(value, modality)) {
                connected = false
                return@forEachIndexed
            }
            val px = index * (w - 24f) / (values.size - 1).coerceAtLeast(1) + 12f
            val plotTop = labelPaint.textSize + 24f
            val plotBottom = h - labelPaint.textSize - 20f
            val py = (plotBottom - ((value.toDouble() - minV) / span) * max(1f, plotBottom - plotTop)).toFloat()
            if (!connected) path.moveTo(px, py) else path.lineTo(px, py)
            connected = true
        }
        canvas.drawPath(path, tracePaint)
        canvas.drawText(axisDescription ?: "相對幅度 · 樣本順序（非醫療標準刻度）", 16f, h - 8f, labelPaint)
    }

}
