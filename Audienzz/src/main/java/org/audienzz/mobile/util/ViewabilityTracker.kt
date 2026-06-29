package org.audienzz.mobile.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver

/**
 * Tracks viewability for a single ad [view] and reports two events through [onStart] / [onSuccess].
 *
 * Rules (per measurement session):
 * - [onStart] fires every time the visible fraction of [view] crosses **up** through
 *   [thresholdFraction] (default 50%), as long as [onSuccess] has not fired yet. This means a
 *   scroll-away before the success threshold followed by a scroll-back re-fires start.
 * - [onSuccess] fires **once**, when [view] stays at least [thresholdFraction] visible for
 *   [successDurationMs] continuous milliseconds (default 1s). The timer is cancelled if the view
 *   drops below the threshold before it elapses.
 * - After [onSuccess] fires the session is terminal — neither event fires again until [start] is
 *   called for a new creative (e.g. after a refresh).
 *
 * Visibility is sampled on every pre-draw of the view tree, and the success timer runs
 * independently on the main thread so it elapses even when the view is static. The tracker stops
 * itself when [view] is detached from the window.
 */
internal class ViewabilityTracker(
    private val view: View,
    private val thresholdFraction: Float = DEFAULT_THRESHOLD,
    private val successDurationMs: Long = DEFAULT_SUCCESS_DURATION_MS,
    private val onStart: () -> Unit,
    private val onSuccess: () -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var attachListener: View.OnAttachStateChangeListener? = null

    private var aboveThreshold = false
    private var successSent = false

    private val successRunnable: Runnable = Runnable {
        successSent = true
        onSuccess()
        // Session is terminal until the next start() call. stop() also clears this callback.
        stop()
    }

    /** Begins — or restarts — a viewability session for the current creative. */
    fun start() {
        stop()
        successSent = false
        aboveThreshold = false

        val preDraw = ViewTreeObserver.OnPreDrawListener {
            evaluate()
            true
        }
        preDrawListener = preDraw
        view.viewTreeObserver.addOnPreDrawListener(preDraw)

        val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) = stop()
        }
        attachListener = attach
        view.addOnAttachStateChangeListener(attach)

        // Evaluate immediately in case the ad is already on screen.
        evaluate()
    }

    private fun evaluate() {
        if (successSent) return
        val isAbove = view.visibleHeightFraction() >= thresholdFraction
        if (isAbove && !aboveThreshold) {
            aboveThreshold = true
            Log.d(TAG, "viewability.start — ${view.javaClass.simpleName} crossed ${(thresholdFraction * 100).toInt()}%")
            onStart()
            handler.postDelayed(successRunnable, successDurationMs)
        } else if (!isAbove && aboveThreshold) {
            aboveThreshold = false
            Log.d(TAG, "viewability — dropped below threshold before success, cancelling timer")
            handler.removeCallbacks(successRunnable)
        }
    }

    /** Stops the current session and releases listeners. Safe to call multiple times. */
    fun stop() {
        handler.removeCallbacks(successRunnable)
        preDrawListener?.let {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnPreDrawListener(it)
            }
        }
        preDrawListener = null
        attachListener?.let { view.removeOnAttachStateChangeListener(it) }
        attachListener = null
        aboveThreshold = false
    }

    companion object {
        private const val TAG = "ViewabilityTracker"
        private const val DEFAULT_THRESHOLD = 0.5f
        private const val DEFAULT_SUCCESS_DURATION_MS = 1_000L
    }
}
