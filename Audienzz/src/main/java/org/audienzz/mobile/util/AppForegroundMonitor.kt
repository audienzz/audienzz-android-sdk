package org.audienzz.mobile.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-wide foreground/background signal, driven by activity start/stop counts.
 *
 * Registered once alongside the current-activity tracker in `AudienzzPrebidMobile`. Viewability
 * timers observe it so a pending `viewability.success` never elapses while the app is backgrounded
 * (which would otherwise report a "viewed" ad the user never actually saw).
 *
 * Callbacks are delivered on the main thread (the lifecycle callbacks run there).
 */
internal object AppForegroundMonitor : Application.ActivityLifecycleCallbacks {

    interface Listener {
        /** The app moved to the background (last started activity stopped). */
        fun onEnterBackground()

        /** The app returned to the foreground (first activity started again). */
        fun onEnterForeground()
    }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private var startedActivities = 0

    /**
     * Whether any activity lifecycle callback has been seen yet.
     *
     * Android does not replay lifecycle callbacks for an activity that already started, and the SDK
     * is usually initialized from within a running activity — always so for the Flutter and React
     * Native bridges, which init from Dart/JS. Until a transition is observed the counter says
     * "zero started activities", which is indistinguishable from backgrounded unless tracked
     * separately. Treating that unknown state as background made the auction gate reject every
     * first load on the launch screen.
     */
    private var hasObservedLifecycle = false

    /** True while the app is in the foreground, or while that is not yet known. */
    val isForeground: Boolean
        get() = !hasObservedLifecycle || startedActivities > 0

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    override fun onActivityStarted(activity: Activity) {
        val wasForeground = isForeground
        hasObservedLifecycle = true
        startedActivities++
        if (!wasForeground) {
            listeners.forEach { it.onEnterForeground() }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        hasObservedLifecycle = true
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) {
            listeners.forEach { it.onEnterBackground() }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
