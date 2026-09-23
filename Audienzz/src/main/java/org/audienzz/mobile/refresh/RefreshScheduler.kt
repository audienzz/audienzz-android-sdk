package org.audienzz.mobile.refresh

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Delayed execution for the refresh controller, injectable so tests drive time deterministically
 * rather than idling a real looper.
 */
internal interface RefreshScheduler {
    fun postDelayed(delayMillis: Long, action: () -> Unit)
    fun cancel()

    /**
     * Monotonic milliseconds. Durations must never be measured with wall-clock time: a device clock
     * change (or an NTP correction) would otherwise make a refresh either fire instantly or never.
     */
    fun nowMillis(): Long
}

/** Production scheduler: the main looper, and `elapsedRealtime` for durations. */
internal class MainLooperRefreshScheduler : RefreshScheduler {

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun postDelayed(delayMillis: Long, action: () -> Unit) {
        cancel()
        val runnable = Runnable {
            pending = null
            action()
        }
        pending = runnable
        handler.postDelayed(runnable, delayMillis)
    }

    override fun cancel() {
        // Removing through the same handler instance that posted it — removeCallbacks matches on
        // the target handler, so a freshly constructed one would silently fail to cancel.
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}
