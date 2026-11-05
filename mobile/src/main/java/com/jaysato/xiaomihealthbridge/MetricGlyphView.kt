package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class MetricGlyphView(context: Context, private val metric: String, private val tint: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(width / 32f, height / 32f)
        paint.color = Color.argb(32, Color.red(tint), Color.green(tint), Color.blue(tint))
        paint.style = Paint.Style.FILL
        canvas.drawCircle(16f, 16f, 16f, paint)
        paint.color = tint
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val path = Path()
        when {
            metric.contains("心率") -> {
                path.moveTo(7f, 17f); path.lineTo(11f, 17f); path.lineTo(14f, 10f)
                path.lineTo(18f, 23f); path.lineTo(21f, 15f); path.lineTo(25f, 15f)
            }
            metric.contains("血氧") -> {
                path.moveTo(16f, 6f); path.cubicTo(12f, 12f, 8f, 15f, 9f, 20f)
                path.cubicTo(11f, 28f, 24f, 26f, 23f, 18f); path.cubicTo(22f, 14f, 18f, 9f, 16f, 6f)
            }
            metric.contains("睡眠") -> {
                path.moveTo(20f, 7f); path.cubicTo(4f, 4f, 3f, 27f, 21f, 24f)
                path.cubicTo(10f, 21f, 12f, 11f, 20f, 7f)
            }
            metric.contains("體重") -> {
                canvas.drawRoundRect(7f, 8f, 25f, 25f, 4f, 4f, paint)
                canvas.drawArc(11f, 10f, 21f, 19f, 200f, 140f, false, paint)
                path.moveTo(16f, 16f); path.lineTo(19f, 12f)
            }
            metric.contains("步數") -> {
                canvas.save(); canvas.rotate(-25f, 16f, 16f)
                canvas.drawRoundRect(8f, 6f, 14f, 17f, 3f, 3f, paint)
                canvas.drawRoundRect(18f, 15f, 24f, 26f, 3f, 3f, paint)
                canvas.restore()
            }
            metric.contains("熱量") -> {
                path.moveTo(17f, 6f); path.cubicTo(19f, 15f, 27f, 15f, 23f, 23f)
                path.cubicTo(14f, 32f, 4f, 21f, 11f, 14f); path.lineTo(12f, 19f); path.close()
            }
            else -> {
                path.moveTo(8f, 24f); path.lineTo(24f, 8f); path.lineTo(14f, 8f)
                path.moveTo(24f, 8f); path.lineTo(24f, 18f)
            }
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
