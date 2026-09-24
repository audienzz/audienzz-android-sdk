package org.audienzz.mobile.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-wide foreground/background signal, driven by started activity identities.
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

    /**
     * Identities of the activities observed started, not a bare count.
     *
     * A counter breaks after late initialization: register while activity A is already started,
     * start B (count 0 → 1), then stop A — the count returns to 0 and reports background even though
     * B is still visible, destroying B's loaders. Tracking identities means stopping an activity we
     * never saw start is simply ignored.
     */
    private val startedActivities = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<Activity, Boolean>(),
    )

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
        get() = !hasObservedLifecycle || startedActivities.isNotEmpty()

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    override fun onActivityStarted(activity: Activity) {
        val wasForeground = isForeground
        hasObservedLifecycle = true
        startedActivities.add(activity)
        if (!wasForeground) {
            listeners.forEach { it.onEnterForeground() }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        val wasTracked = startedActivities.remove(activity)
        if (!wasTracked && hasObservedLifecycle) {
            // An activity we never saw start, stopping after we already have a reliable picture:
            // it was running before the SDK registered and something else is visible now. Ignoring
            // it is what stops the count going false-negative while another activity is up.
            return
        }
        if (!wasTracked) {
            // The sole activity that was already running when the SDK registered is now stopping,
            // and we have observed nothing else. This IS the first background transition — treating
            // it as "unknown, ignore" left isForeground stuck true, so refresh never stopped and the
            // next start never reported foreground either.
            hasObservedLifecycle = true
        }
        if (startedActivities.isEmpty()) {
            listeners.forEach { it.onEnterBackground() }
        }
    }

    /** Drops all observed state. Tests only — the monitor is a process-wide singleton. */
    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
        startedActivities.clear()
        hasObservedLifecycle = false
        listeners.clear()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    // Initialization from Dart/JS can miss the host's first onStart. A translucent AdActivity
    // then pauses that host without stopping it. If only the ad is tracked, stopping the ad
    // falsely backgrounds the whole process, and a resumed host never receives another onStart.
    // Both callbacks prove the activity has started. Seed its identity idempotently; only onStop
    // removes it. This also preserves the host when the overlay stops before the host resumes.
    override fun onActivityResumed(activity: Activity) = onActivityStarted(activity)
    override fun onActivityPaused(activity: Activity) = onActivityStarted(activity)
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
