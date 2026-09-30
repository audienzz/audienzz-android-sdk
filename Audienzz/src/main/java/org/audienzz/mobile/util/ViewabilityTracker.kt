package org.audienzz.mobile.util

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver

/** One creative's exposure. Start is emitted once; success requires a continuous visible second. */
internal class ViewabilityTracker(
    private val view: View,
    private val thresholdFraction: Float = 0.5f,
    private val successDurationMs: Long = 1_000L,
    private val isEligible: () -> Boolean = { true },
    private val onStart: () -> Unit,
    private val onSuccess: () -> Unit,
) : AppForegroundMonitor.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private var observer: ViewTreeObserver? = null
    private var running = false
    private var aboveThreshold = false
    private var startRecorded = false
    private var generation = 0
    private var pending: Runnable? = null
    private val preDraw = ViewTreeObserver.OnPreDrawListener { refreshVisibility(); true }
    private val attach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            observeDrawing()
            refreshVisibility()
        }
        override fun onViewDetachedFromWindow(v: View) {
            interruptExposure()
            removeDrawingObserver()
        }
    }

    fun start() {
        stop()
        running = true
        view.addOnAttachStateChangeListener(attach)
        observeDrawing()
        AppForegroundMonitor.addListener(this)
        refreshVisibility()
    }

    private fun visible(): Boolean = running && AppForegroundMonitor.isForeground &&
        view.isAttachedToWindow && isEligible() && view.visibleHeightFraction() >= thresholdFraction

    fun refreshVisibility() {
        if (!running) return
        if (!visible()) {
            interruptExposure()
        } else if (!aboveThreshold) {
            aboveThreshold = true
            val token = generation
            if (!startRecorded) {
                startRecorded = true
                onStart()
            }
            if (!running || token != generation) return
            val action = Runnable {
                if (!running || token != generation) return@Runnable
                // A timer is not proof the creative remained visible or on the active page.
                if (!visible()) { interruptExposure(); return@Runnable }
                stop()
                onSuccess()
            }
            pending = action
            handler.postDelayed(action, successDurationMs)
        }
    }

    override fun onEnterBackground() = interruptExposure()
    override fun onEnterForeground() = refreshVisibility()

    private fun interruptExposure() {
        generation++
        pending?.let(handler::removeCallbacks)
        pending = null
        aboveThreshold = false
    }
    private fun observeDrawing() {
        if (!running) return
        removeDrawingObserver()
        observer = view.viewTreeObserver.also { it.addOnPreDrawListener(preDraw) }
    }
    private fun removeDrawingObserver() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
        observer = null
    }
    fun stop() {
        running = false
        interruptExposure()
        AppForegroundMonitor.removeListener(this)
        removeDrawingObserver()
        view.removeOnAttachStateChangeListener(attach)
    }
}
