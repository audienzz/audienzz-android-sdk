package org.audienzz.mobile.screen

import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.original.AudienzzAdViewHandler
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Screen-aware smart refresh (v2). Matches banner ad handlers to the screen they live on and, on
 * every [onScreenResumed] transition, pauses the previous screen's banners and force-reloads the
 * incoming screen's banners. Wired only under the smart-refresh-v2 feature flag.
 *
 * A "screen" is an opaque token — an `Activity`, a `Fragment` (so ViewPager2 tabs and fragment
 * navigation are distinct screens), or any object a manual caller provides (e.g. a route key from
 * Flutter/React Native). Matching is by object identity, so two instances of the same class are
 * distinct screens. Each handler resolves its own host screen (its host Fragment when it lives in
 * one, else its host Activity) — see [AudienzzAdViewHandler.isHostedBy].
 */
@Singleton
class ScreenAdCoordinator @Inject constructor() {

    /** Live smart-refresh handlers. Weak keys so a handler/adView/screen chain is never pinned. */
    private val registry: MutableSet<AudienzzAdViewHandler> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<AudienzzAdViewHandler, Boolean>()))

    private var activeScreenRef: WeakReference<Any>? = null

    /** The active screen token from the most recent [onScreenResumed], or null before the first. */
    val activeScreen: Any?
        get() = activeScreenRef?.get()

    fun register(handler: AudienzzAdViewHandler) {
        registry.add(handler)
    }

    fun deregister(handler: AudienzzAdViewHandler) {
        registry.remove(handler)
    }

    /**
     * Hard screen transition. Every call releases the previous screen's banners and reloads the
     * incoming screen's already-loaded banners — even for the same screen re-resuming (app
     * foreground) or another instance of the same class. Runs on the main thread.
     */
    fun onScreenResumed(screen: Any) {
        activeScreenRef = WeakReference(screen)
        synchronized(registry) {
            for (handler in registry) {
                handler.onScreenActiveChanged(handler.isHostedBy(screen))
            }
        }
    }
}

/** Facade accessor mirroring `eventLogger` — resolves the DI singleton, null before init. */
internal val screenAdCoordinator: ScreenAdCoordinator?
    get() = MainComponent.screenAdCoordinator
