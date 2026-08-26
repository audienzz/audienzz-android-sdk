package org.audienzz.mobile.screen

import android.app.Activity
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.util.CurrentActivityTracker
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Screen-aware smart refresh (v2). Matches banner ad handlers to the Activity ("screen") they live
 * on and, on every [onScreenResumed] transition, pauses the previous screen's banners and
 * force-reloads the incoming screen's banners. Wired only under the smart-refresh-v2 feature flag —
 * [org.audienzz.mobile.AudienzzPrebidMobile.onScreenResumed] gates the call — so the legacy model is
 * untouched.
 *
 * Fragment note: publishers pass the host Activity for Fragments, so Fragments sharing one Activity
 * are treated as one screen (a Fragment swap triggers a transition only if onScreenResumed is called
 * again). Matching is by Activity object identity, so two instances of the same Activity class are
 * distinct screens.
 */
@Singleton
class ScreenAdCoordinator @Inject constructor(
    private val currentActivityTracker: CurrentActivityTracker,
) {

    /** Live smart-refresh handlers. Weak keys so a handler/adView/Activity chain is never pinned. */
    private val registry: MutableSet<AudienzzAdViewHandler> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<AudienzzAdViewHandler, Boolean>()))

    private var activeActivityRef: WeakReference<Activity>? = null

    /** The active screen: the last [onScreenResumed] Activity, else the current foreground Activity. */
    val activeActivity: Activity?
        get() = activeActivityRef?.get() ?: currentActivityTracker.currentActivity

    fun register(handler: AudienzzAdViewHandler) {
        registry.add(handler)
    }

    fun deregister(handler: AudienzzAdViewHandler) {
        registry.remove(handler)
    }

    /**
     * Hard screen transition. Every call releases the previous screen's banners and reloads the
     * incoming screen's already-loaded banners — even for the same Activity re-resuming (app
     * foreground) or another instance of the same class. Runs on the main thread (onResume).
     */
    fun onScreenResumed(activity: Activity) {
        activeActivityRef = WeakReference(activity)
        synchronized(registry) {
            for (handler in registry) {
                handler.onScreenActiveChanged(handler.isHostedBy(activity))
            }
        }
    }
}

/** Facade accessor mirroring `eventLogger` — resolves the DI singleton, null before init. */
internal val screenAdCoordinator: ScreenAdCoordinator?
    get() = MainComponent.screenAdCoordinator
