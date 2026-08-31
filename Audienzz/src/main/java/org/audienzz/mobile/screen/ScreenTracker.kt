package org.audienzz.mobile.screen

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

/**
 * Automatic screen tracking. From a single [Application.ActivityLifecycleCallbacks] registration it
 * observes every Activity and — for `FragmentActivity`s — every Fragment (recursively), so screen
 * changes (Activity navigation, fragment navigation, and ViewPager2 tabs) fire [onScreen] with no
 * per-screen code from the integrator.
 *
 * The Activity and its resumed Fragment fire in the same main-thread turn, so the dispatch is
 * coalesced (post + removeCallbacks) to the most specific screen — the resumed Fragment when there
 * is one, else the Activity. Dialogs, invisible fragments, and non-primary child/sibling fragments
 * are filtered out. Integrators can still call the manual `onScreenResumed` API for anything this
 * can't see (e.g. Jetpack Compose destinations).
 */
internal class ScreenTracker(
    private val onScreen: (screen: Any, screenName: String) -> Unit,
) : Application.ActivityLifecycleCallbacks {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingScreen: Any? = null

    private val dispatchRunnable = Runnable {
        val screen = pendingScreen ?: return@Runnable
        pendingScreen = null
        onScreen(screen, screenName(screen))
    }

    private val fragmentCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            if (isTrackableFragment(f)) scheduleScreen(f)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        (activity as? FragmentActivity)?.supportFragmentManager
            ?.registerFragmentLifecycleCallbacks(fragmentCallbacks, true)
    }

    override fun onActivityResumed(activity: Activity) {
        // The Activity callback fires AFTER the Fragment one (it runs after onResume()), so instead
        // of scheduling the Activity — which would clobber the tab's Fragment in the coalesce window
        // and pause every fragment-hosted banner on return — resolve the Activity to its current
        // visible screen (the primary-navigation Fragment / tab) when it hosts one.
        scheduleScreen(currentScreenOf(activity))
    }

    /** An Activity's most specific current screen: its deepest visible Fragment (the current tab /
     *  destination), else the Activity itself when it hosts no trackable fragment. */
    private fun currentScreenOf(activity: Activity): Any {
        if (activity is FragmentActivity) {
            currentTrackableFragment(activity.supportFragmentManager)?.let { return it }
        }
        return activity
    }

    /** The deepest currently-showing trackable fragment: the primary-navigation fragment when one
     *  is set (Navigation component), else the resumed & visible fragment — ViewPager2's
     *  FragmentStateAdapter does NOT set a primary navigation fragment, so the tab must be found by
     *  its resumed/visible state. */
    private fun currentTrackableFragment(fm: FragmentManager): Fragment? {
        val candidate = fm.primaryNavigationFragment
            ?: fm.fragments.firstOrNull { it.isResumed && it.isVisible && it.view != null }
            ?: return null
        if (!isTrackableFragment(candidate)) return null
        return currentTrackableFragment(candidate.childFragmentManager) ?: candidate
    }

    override fun onActivityDestroyed(activity: Activity) {
        (activity as? FragmentActivity)?.supportFragmentManager
            ?.unregisterFragmentLifecycleCallbacks(fragmentCallbacks)
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    /** Coalesce the Activity + Fragment callbacks that fire together into one dispatch (last wins). */
    private fun scheduleScreen(screen: Any) {
        pendingScreen = screen
        mainHandler.removeCallbacks(dispatchRunnable)
        mainHandler.post(dispatchRunnable)
    }

    private fun isTrackableFragment(f: Fragment): Boolean {
        if (f is DialogFragment) return false
        if (f.view == null || !f.isVisible) return false
        // Only the primary navigation fragment is the current screen (ViewPager2 sets it to the
        // current page; Navigation sets it to the current destination). This filters out sibling
        // and child/component fragments that resume alongside the real screen.
        val primary = f.parentFragmentManager.primaryNavigationFragment
        return primary == null || primary === f
    }

    private fun screenName(screen: Any): String = when (screen) {
        is Activity -> screen.componentName.className
        else -> screen.javaClass.name
    }
}
