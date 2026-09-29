package org.audienzz.mobile.util

import android.os.Handler
import android.os.Looper

/** One fullscreen presentation. Exposure restarts on foreground; duplicate shown callbacks do not. */
internal class FullScreenViewabilityTimer(
    private val successDurationMs: Long = 1_000L,
    private val onStart: () -> Unit,
    private val onSuccess: () -> Unit,
) : AppForegroundMonitor.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private var shown = false
    private var measuring = false
    private var terminal = false
    private var generation = 0
    private var pending: Runnable? = null

    fun onShown() {
        if (shown || terminal) return
        shown = true
        AppForegroundMonitor.addListener(this)
        resume()
    }
    private fun resume() {
        if (!shown || terminal || measuring || !AppForegroundMonitor.isForeground) return
        measuring = true
        val token = generation
        onStart()
        if (!shown || terminal || token != generation) return
        val action = Runnable {
            if (!shown || terminal || token != generation) return@Runnable
            if (!AppForegroundMonitor.isForeground) { pause(); return@Runnable }
            cancel()
            onSuccess()
        }
        pending = action
        handler.postDelayed(action, successDurationMs)
    }
    private fun pause() {
        generation++
        measuring = false
        pending?.let(handler::removeCallbacks)
        pending = null
    }
    fun cancel() {
        terminal = true
        shown = false
        pause()
        AppForegroundMonitor.removeListener(this)
    }
    override fun onEnterBackground() = pause()
    override fun onEnterForeground() = resume()
}
