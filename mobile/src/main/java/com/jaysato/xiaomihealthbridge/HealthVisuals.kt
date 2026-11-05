package com.jaysato.xiaomihealthbridge

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View

object HealthVisuals {
    val background = Color.rgb(5, 6, 9)
    val secondary = Color.rgb(147, 151, 164)
    val blue = Color.rgb(112, 178, 255)

    fun panel(context: Context, radius: Int = 24, elevated: Boolean = false): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, if(elevated)
            intArrayOf(Color.rgb(39, 43, 54), Color.rgb(22, 25, 34))
            else intArrayOf(Color.rgb(29, 31, 38), Color.rgb(21, 23, 29))).apply {
            cornerRadius = radius * context.resources.displayMetrics.density
            setStroke(1, Color.argb(if(elevated) 38 else 14, 255, 255, 255))
        }

    fun interactive(context: Context, radius: Int = 18) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(30, 160, 190, 255)), panel(context, radius),
        GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = radius * context.resources.displayMetrics.density })
}

class WatchPortrait(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = RadialGradient(96f, 105f, 90f, intArrayOf(0x444D6BB7, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    private val bezel = LinearGradient(30f, 55f, 150f, 165f,
        intArrayOf(0xFFABB4C6.toInt(), 0xFF454E5E.toInt(), 0xFF737E92.toInt()), null, Shader.TileMode.CLAMP)
    private val labelTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val signal = Path().apply {
        moveTo(49f, 115f); lineTo(68f, 115f); lineTo(79f, 99f)
        lineTo(91f, 134f); lineTo(103f, 107f); lineTo(114f, 115f); lineTo(131f, 115f)
    }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        canvas.save()
        val scale = minOf(width / 180f, height / 220f)
        canvas.translate((width - 180 * scale) / 2, (height - 220 * scale) / 2)
        canvas.scale(scale, scale)
        paint.shader = glow
        canvas.drawCircle(96f, 105f, 90f, paint)
        paint.shader = null
        canvas.rotate(-14f, 90f, 110f)
        paint.color = Color.rgb(34, 40, 52)
        canvas.drawRoundRect(66f, 8f, 114f, 212f, 18f, 18f, paint)
        paint.color = Color.rgb(60, 67, 82)
        canvas.drawRoundRect(70f, 8f, 74f, 212f, 2f, 2f, paint)
        paint.shader = bezel
        canvas.drawCircle(90f, 110f, 64f, paint)
        paint.shader = null
        paint.color = Color.rgb(10, 13, 20)
        canvas.drawCircle(90f, 110f, 59f, paint)
        paint.color = Color.rgb(110, 119, 140)
        canvas.drawRoundRect(152f, 97f, 158f, 115f, 3f, 3f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(57, 66, 83)
        canvas.drawCircle(90f, 110f, 54f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(249, 252, 255)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 9f
        paint.typeface = labelTypeface
        canvas.drawText("HEALTH BRIDGE", 90f, 85f, paint)
        paint.color = Color.rgb(255, 92, 126)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(signal, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(111, 186, 221)
        canvas.drawCircle(90f, 147f, 2f, paint)
        canvas.restore()
    }
}
