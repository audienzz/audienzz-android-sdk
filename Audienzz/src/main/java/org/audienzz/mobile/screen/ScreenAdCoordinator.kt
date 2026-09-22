package org.audienzz.mobile.screen

import org.audienzz.mobile.util.AudienzzDiagnostics
import android.os.Handler
import android.os.Looper
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.original.AudienzzAdViewHandler
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Page-scoped ad ownership. Matches banner ad handlers to the screen they live on and, on every
 * [onScreenResumed] transition, **releases** the previous page's banners — stopping their auction
 * and refresh entirely — and **recreates** the incoming page's, so returning to a screen shows a
 * fresh creative.
 *
 * This runs for every banner on every page impression; it is not gated on the smart-refresh-v2
 * flag, which now only selects the viewport gate used for scroll pause/resume.
 *
 * **Ordering contract:** call `pageImpression` *before* creating the screen's ads. A banner whose
 * host cannot be resolved when the sweep runs (because its view is not attached yet) is released
 * and then adopted once it attaches — see [adoptIfOnActiveScreen].
 *
 * A "screen" is an opaque token — an `Activity`, a `Fragment` (so ViewPager2 tabs and fragment
 * navigation are distinct screens), or any object a manual caller provides (e.g. a route key from
 * Flutter/React Native). Matching is by object identity, so two instances of the same class are
 * distinct screens. Each handler resolves its own host screen (its host Fragment when it lives in
 * one, else its host Activity) — see [AudienzzAdViewHandler.isHostedBy].
 */
@Singleton
class ScreenAdCoordinator @Inject constructor() {

    /** Live banner handlers. Weak keys so a handler/adView/screen chain is never pinned. */
    private val registry: MutableSet<AudienzzAdViewHandler> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<AudienzzAdViewHandler, Boolean>()))

    internal val requestLedger = org.audienzz.mobile.targeting.AdRequestLedger()

    private var activeScreenRef: WeakReference<Any>? = null

    /** The active screen token from the most recent [onScreenResumed], or null before the first. */
    val activeScreen: Any?
        get() = activeScreenRef?.get()

    /** Name reported with the active screen, reused for the foreground re-impression. */
    @Volatile
    var activeScreenName: String? = null
        private set

    /**
     * Monotonic page counter. A handler stamps it at [register]; a stamp older than [epoch] means the
     * banner was created before its screen's `pageImpression`.
     */
    @Volatile
    var epoch: Int = 0
        private set

    fun register(handler: AudienzzAdViewHandler) {
        registry.add(handler)
        val screen = activeScreen
        if (screen == null || handler.isHostedBy(screen)) handler.requestContext.register()
    }

    fun deregister(handler: AudienzzAdViewHandler) {
        registry.remove(handler)
    }

    /**
     * Prebid finished initializing — let every live banner take the load it deferred.
     *
     * Resuming through the registry rather than by queueing a closure per banner means a banner
     * destroyed while waiting is simply no longer here.
     */
    internal fun resumeAllAfterSdkInit() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { resumeAllAfterSdkInit() }
            return
        }
        val handlers = synchronized(registry) { registry.toList() }
        handlers.forEach { it.onSdkInitialized() }
    }

    /**
     * Hard page transition. Every call releases every banner that is not on the incoming page and
     * recreates the ones that are — including when the same screen resumes again (back navigation,
     * app foreground) or another instance of the same class appears. Runs on the main thread.
     */
    fun onScreenResumed(screen: Any, name: String? = null) {
        // The sweep touches View state (visibility blanking), Prebid timers and the GAM ad view, all
        // of which are main-thread-only. React Native @ReactMethod calls arrive on the NativeModules
        // thread, so hop if needed rather than trusting the caller.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { onScreenResumed(screen, name) }
            return
        }
        activeScreenRef = WeakReference(screen)
        activeScreenName = name
        epoch++
        synchronized(registry) {
            requestLedger.beginPage(epoch, registry.filter { it.isHostedBy(screen) }.map { it.requestContext })
            android.util.Log.d(
                TAG,
                "pageImpression \"$name\" epoch=$epoch screen=${screen.javaClass.simpleName}@${System.identityHashCode(screen)} — ${registry.size} banner(s) registered",
            )
            AudienzzDiagnostics.log(
                "page", "transition",
                "name" to name, "epoch" to epoch, "slots" to registry.size,
            )
            for (handler in registry) {
                val active = handler.isHostedBy(screen)
                AudienzzDiagnostics.log(
                    "slot", if (active) "recreate" else "release",
                    "unit" to handler.diagnosticLabel(),
                    "page" to name,
                    "epoch" to epoch,
                    "reason" to if (active) null else "otherPage",
                )
                handler.onPageActiveChanged(active, epoch)
            }
        }
    }

    /**
     * Repairs the one case the sweep genuinely gets wrong: a banner created *before* its screen's
     * `pageImpression` whose view was not attached when the sweep ran, so [AudienzzAdViewHandler
     * .isHostedBy] could not resolve a host and the banner was released — a dead slot.
     *
     * Called when the ad view attaches, once the host *can* be resolved. Event-driven rather than a
     * timing grace window, so it can never resurrect a previous page's ad.
     */
    fun adoptIfOnActiveScreen(handler: AudienzzAdViewHandler) {
        val screen = activeScreen ?: return
        if (!handler.isHostedBy(screen)) return
        android.util.Log.w(
            TAG,
            "banner was created before pageImpression for \"$activeScreenName\" and wasn't attached " +
                "when the page swept — adopted on attach. Call pageImpression() BEFORE creating " +
                "this screen's ads.",
        )
        handler.onPageActiveChanged(true, epoch)
    }

    private companion object {
        const val TAG = "ScreenAdCoordinator"
    }
}

/**
 * Test-only override. Installing a coordinator here lets a unit test exercise page transitions
 * without standing up the whole DI graph (which would also construct the event logger and its
 * networking). Null in production.
 */
@androidx.annotation.VisibleForTesting
internal var screenAdCoordinatorOverride: ScreenAdCoordinator? = null

/** Facade accessor mirroring `eventLogger` — resolves the DI singleton, null before init. */
internal val screenAdCoordinator: ScreenAdCoordinator?
    get() = screenAdCoordinatorOverride ?: MainComponent.screenAdCoordinator
