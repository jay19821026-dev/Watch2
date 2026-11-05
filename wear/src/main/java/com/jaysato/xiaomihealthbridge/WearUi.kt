package com.jaysato.xiaomihealthbridge

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max

/** Shared round-screen design system for every watch surface. */
object WearUi {
    const val BACKGROUND = "#050507"
    const val SURFACE = "#1C1C1F"
    const val SURFACE_HIGH = "#26262B"
    const val PRIMARY = "#64D2FF"
    const val TEXT = "#F7F7FA"
    const val SECONDARY = "#A7ADBA"

    fun installScrollable(activity: Activity, content: LinearLayout) {
        prepareContent(activity, content)
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isSaveEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(activity, 4), 0, dp(activity, 18))
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            post { scrollTo(0, 0) }
        }
        activity.setContentView(roundFrame(activity, scroll))
    }

    fun installScrollableWithPinnedAction(activity: Activity, content: LinearLayout, action: View) {
        prepareContent(activity, content)
        val frame = FrameLayout(activity)
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isSaveEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(activity, 4), 0, dp(activity, 82))
            addView(content, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        frame.addView(action, FrameLayout.LayoutParams(-1, dp(activity, 54), Gravity.BOTTOM).apply {
            leftMargin = dp(activity, 30)
            rightMargin = dp(activity, 30)
            bottomMargin = dp(activity, 22)
        })
        restyle(action)
        activity.setContentView(roundFrame(activity, frame))
    }

    fun installStatic(activity: Activity, content: View) {
        content.setPadding(dp(activity, 30), dp(activity, 26), dp(activity, 30), dp(activity, 30))
        restyle(content)
        activity.setContentView(roundFrame(activity, content))
    }

    fun card(context: Context, start: String = SURFACE_HIGH, end: String = SURFACE): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(start), Color.parseColor(end))).apply {
            cornerRadius = dp(context, 22).toFloat()
            setStroke(dp(context, 1), Color.argb(42, 255, 255, 255))
        }

    private fun prepareContent(context: Context, content: LinearLayout) {
        content.setBackgroundColor(Color.TRANSPARENT)
        content.clipToPadding = false
        content.setPadding(
            max(content.paddingLeft, dp(context, 26)),
            max(content.paddingTop, dp(context, 20)),
            max(content.paddingRight, dp(context, 26)),
            max(content.paddingBottom, dp(context, 52))
        )
        restyle(content)
    }

    private fun restyle(view: View) {
        when (view) {
            is Button -> {
                view.isAllCaps = false
                view.minHeight = dp(view.context, 48)
                view.minimumHeight = dp(view.context, 48)
                if (view.layoutParams is GridLayout.LayoutParams) {
                    view.setPadding(dp(view.context, 3), dp(view.context, 3), dp(view.context, 3), dp(view.context, 3))
                } else {
                    view.setPadding(dp(view.context, 15), dp(view.context, 8), dp(view.context, 15), dp(view.context, 8))
                }
                view.stateListAnimator = null
                view.elevation = dp(view.context, 2).toFloat()
                view.setOnTouchListener { target, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> target.animate().scaleX(.96f).scaleY(.96f).alpha(.86f).setDuration(90).start()
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(130).start()
                    }
                    false
                }
            }
            is TextView -> {
                view.includeFontPadding = false
                if (view.currentTextColor == Color.GRAY) view.setTextColor(Color.parseColor(SECONDARY))
                view.setLineSpacing(dp(view.context, 2).toFloat(), 1f)
            }
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) restyle(view.getChildAt(index))
    }

    private fun roundFrame(context: Context, content: View): FrameLayout = FrameLayout(context).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#0C111A"), Color.parseColor(BACKGROUND), Color.BLACK))
        addView(content, FrameLayout.LayoutParams(-1, -1))
        addView(View(context).apply {
            isClickable = false
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(135, 0, 0, 0), Color.TRANSPARENT))
        }, FrameLayout.LayoutParams(-1, dp(context, 34), Gravity.TOP))
        addView(View(context).apply {
            isClickable = false
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(190, 0, 0, 0), Color.TRANSPARENT))
        }, FrameLayout.LayoutParams(-1, dp(context, 40), Gravity.BOTTOM))
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
