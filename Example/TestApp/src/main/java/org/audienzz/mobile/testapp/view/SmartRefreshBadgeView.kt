package org.audienzz.mobile.testapp.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Debug decorator that wraps an ad view and visualizes its smart-refresh state (parity with iOS):
 *
 *  - a 2dp solid border around the ad — green when refresh is active, grey when paused;
 *  - a small pill in the top-right corner — green "▶ REFRESH ACTIVE" / grey "⏸ REFRESH PAUSED";
 *  - a greppable logcat line on every transition, tagged [TAG]:
 *    `SmartRefresh: <label> → PAUSED` / `SmartRefresh: <label> → ACTIVE`.
 *
 * Defaults to paused until the first visibility signal. Wrap the ad view via [attachAdView], then
 * drive it from the SDK's `onSmartRefreshPausedChanged` callback with [setPaused].
 */
class SmartRefreshBadgeView(
    context: Context,
    private val label: String,
) : FrameLayout(context) {

    private val border = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        setStroke(dp(2), COLOR_PAUSED)
    }

    private val pill = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setPadding(dp(6), dp(3), dp(6), dp(3))
        layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END,
        ).apply {
            topMargin = dp(4)
            marginEnd = dp(4)
        }
    }

    private var paused = true

    init {
        foreground = border
        applyState()
    }

    /** Adds the ad view underneath the pill (pill stays on top of the ad). */
    fun attachAdView(adView: View) {
        addView(
            adView,
            0,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        if (pill.parent == null) addView(pill)
    }

    /** Flips the badge + border and logs the transition. Idempotent — repeats are ignored. */
    fun setPaused(newPaused: Boolean) {
        if (paused == newPaused && pill.text.isNotEmpty()) return
        paused = newPaused
        applyState()
        Log.d(TAG, "$label → ${if (paused) "PAUSED" else "ACTIVE"}")
    }

    private fun applyState() {
        val color = if (paused) COLOR_PAUSED else COLOR_ACTIVE
        border.setStroke(dp(2), color)
        pill.text = if (paused) "⏸ REFRESH PAUSED" else "▶ REFRESH ACTIVE"
        pill.background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(4).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val TAG = "SmartRefresh"
        private val COLOR_ACTIVE = Color.parseColor("#2E7D32") // green
        private val COLOR_PAUSED = Color.parseColor("#757575") // grey
    }
}
