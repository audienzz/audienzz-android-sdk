package org.audienzz.mobile.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

class CurrentActivityTracker : Application.ActivityLifecycleCallbacks {

    private var currentActivityWeakReference: WeakReference<Activity>? = null

    val currentActivity: Activity? get() = currentActivityWeakReference?.get()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // nothing
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivityWeakReference = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        // nothing
    }

    override fun onActivityPaused(activity: Activity) {
        // nothing
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity == currentActivity) {
            currentActivityWeakReference = null
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // nothing
    }

    override fun onActivityDestroyed(activity: Activity) {
        // nothing
    }
}
