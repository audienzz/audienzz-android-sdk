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
 */
internal class FullScreenViewabilityTimer(
    private val successDurationMs: Long = DEFAULT_SUCCESS_DURATION_MS,
    private val onStart: () -> Unit,
    private val onSuccess: () -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val successRunnable = Runnable { onSuccess() }

    fun onShown() {
        cancel()
        onStart()
        handler.postDelayed(successRunnable, successDurationMs)
    }

    fun cancel() {
        handler.removeCallbacks(successRunnable)
    }

    companion object {
        private const val DEFAULT_SUCCESS_DURATION_MS = 1_000L
    }
}
