package org.audienzz.mobile.util

import android.os.Handler
import android.os.Looper

/**
 * Drives `viewability.start` / `viewability.success` for full-screen ads (interstitial, rewarded).
 *
 * A full-screen ad is 100% visible the whole time it is shown, so no visible-fraction sampling is
 * needed (unlike the banner [ViewabilityTracker]):
 * - [onShown] (call from `onAdShowedFullScreenContent`) fires [onStart] immediately and schedules
 *   [onSuccess] after [successDurationMs] of continuous display.
 * - [cancel] (call from `onAdDismissedFullScreenContent` / `onAdFailedToShowFullScreenContent`)
 *   stops a pending success so it cannot fire after the ad is gone.
 *
 * While shown, it observes [AppForegroundMonitor]: backgrounding the app cancels a pending success
 * so it never elapses off-screen, and returning to the foreground re-arms (re-firing start and
 * rescheduling the full continuous-view window) as long as success has not already been reported.
 */
internal class FullScreenViewabilityTimer(
    private val successDurationMs: Long = DEFAULT_SUCCESS_DURATION_MS,
    private val onStart: () -> Unit,
    private val onSuccess: () -> Unit,
) : AppForegroundMonitor.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private var succeeded = false
    private val successRunnable = Runnable {
        succeeded = true
        onSuccess()
        // Terminal for this presentation — stop observing so a later background/foreground is a no-op.
        AppForegroundMonitor.removeListener(this)
    }

    fun onShown() {
        handler.removeCallbacks(successRunnable)
        AppForegroundMonitor.addListener(this)
        onStart()
        handler.postDelayed(successRunnable, successDurationMs)
    }

    fun cancel() {
        handler.removeCallbacks(successRunnable)
        AppForegroundMonitor.removeListener(this)
    }

    override fun onEnterBackground() {
        if (succeeded) return
        handler.removeCallbacks(successRunnable)
    }

    override fun onEnterForeground() {
        if (succeeded) return
        // Still showing full-screen — restart the continuous-view window and re-fire start.
        onStart()
        handler.postDelayed(successRunnable, successDurationMs)
    }

    companion object {
        private const val DEFAULT_SUCCESS_DURATION_MS = 1_000L
    }
}
