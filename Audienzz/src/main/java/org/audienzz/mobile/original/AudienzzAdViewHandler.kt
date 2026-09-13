package org.audienzz.mobile.original

import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.admanager.AppEventListener
import org.audienzz.mobile.AudienzzAdUnit
import org.audienzz.mobile.AudienzzWinningBid
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.event.adClick
import org.audienzz.mobile.event.adImpression
import org.audienzz.mobile.event.bidRequest
import org.audienzz.mobile.event.bidResponse
import org.audienzz.mobile.event.RenderEconomics
import org.audienzz.mobile.event.bidWon
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.event.noBid
import org.audienzz.mobile.event.util.adSubtype
import org.audienzz.mobile.event.viewabilityStart
import org.audienzz.mobile.event.viewabilitySuccess
import org.audienzz.mobile.screen.screenAdCoordinator
import org.audienzz.mobile.util.AppForegroundMonitor
import org.audienzz.mobile.util.ViewabilityTracker
import org.audienzz.mobile.util.addContinuousVisibilityListener
import org.audienzz.mobile.util.adViewId
import org.audienzz.mobile.util.addOnBecameVisibleOnScreenListener
import org.audienzz.mobile.util.addPrefetchMarginListener
import org.audienzz.mobile.util.isRefreshEligible
import org.audienzz.mobile.util.isVisibleForSmartRefresh
import org.audienzz.mobile.util.noBidResultCode
import org.audienzz.mobile.util.prebidKeyword
import org.audienzz.mobile.util.sizesJson
import org.audienzz.mobile.util.unwrapActivity
import java.util.UUID

class AudienzzAdViewHandler(
    private val adView: AdManagerAdView,
    private val adUnit: AudienzzAdUnit,
) {
    companion object {
        private const val TAG = "AudienzzAdViewHandler"

        /** Prebid targeting keys describing the winning bid. */
        private const val HB_BIDDER_KEY = "hb_bidder"
        private const val HB_PB_KEY = "hb_pb"
        private const val HB_SIZE_KEY = "hb_size"
        private const val HB_FORMAT_KEY = "hb_format"

        /**
         * App-event name the GAM Prebid line item must send when it wins. If your GAM line item
         * uses a different key, change it here.
         */
        private const val PREBID_APP_EVENT = "Prebid"

        /** Fallback bidder_code when the Prebid line item won but hb_bidder was unavailable. */
        private const val PREBID_BIDDER = "prebid"

        /** bidder_code reported when the ad server (Google/AdX/direct) rendered instead of Prebid. */
        private const val AD_SERVER_BIDDER = "google"
    }

    private var isFirstDemandFetch = true
    private var eventListenerInstalled = false

    // Smart refresh state
    private var smartRefreshListener: ViewTreeObserver.OnPreDrawListener? = null
    private var lastRefreshTime: Long = 0
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingRefreshRunnable: Runnable? = null
    // M1: keep the request BUILDER (not a frozen request) so each auction rebuilds a fresh request
    // carrying the current PPID/consent/targeting rather than values baked in at first load().
    private var gamRequestBuilder: AdManagerAdRequest.Builder? = null
    private var storedCallback: ((AdManagerAdRequest, AudienzzResultCode?) -> Unit)? = null

    // Viewability tracking (viewability.start / viewability.success)
    private var viewabilityTracker: ViewabilityTracker? = null

    // Render-winner detection (bidder_code / winner_bidder_code on adImpression).
    // prebidWinningBidder = hb_bidder from the Prebid auction; prebidLineItemWon is flipped true
    // when the GAM Prebid line item announces itself via an app event. Both reset per auction.
    private var prebidWinningBidder: String? = null
    private var prebidLineItemWon: Boolean = false
    private var lastWinningBid: AudienzzWinningBid? = null

    // Winning-bid economics from the last auction, reused on adImpression/adClick/viewability.
    private var lastRenderEconomics: RenderEconomics? = null
    // SDK-generated auction id, minted at auction start and reused across every event of that
    // auction (bidRequest → bidResponse/bidWon/noBid → adImpression/adClick/viewability). Prebid
    // only assigns its own id after the request, so we pre-generate one for full-funnel counting.
    private var currentAuctionId: String? = null
    // Times this slot has (re)loaded — reported as slot_reload. First load = 0.
    private var slotReloadCount: Int = 0

    // Screen-aware smart refresh (v2). screenActive defaults true so legacy, and screens that never
    // call pageImpression, behave exactly as before; the coordinator flips it on screen changes.
    @Volatile
    private var screenActive = true
    // The ad's host "screen": its host Fragment when the adView lives inside one (so ViewPager2 tabs
    // and fragment navigation are distinct screens), else its host Activity. Resolved on demand
    // (transitions are infrequent) so it stays correct once the view is attached; a non-Activity,
    // non-Fragment context yields null and the ad is never matched to any screen (behaves as today).
    // Pinned once a host Fragment is definitively resolved (the ad belongs to exactly one screen and
    // never migrates). The Activity fallback is NOT cached, so an early resolution before the ad is
    // attached to its Fragment can still upgrade to the Fragment on the next call.
    private var cachedHostScreen: Any? = null

    /**
     * Caller-supplied screen token for hosts the SDK can't infer from the view tree — a Jetpack
     * Compose destination, or any custom navigation model. When set it wins over Fragment/Activity
     * resolution, so the ad belongs to whatever screen the integrator reported to `pageImpression`.
     * Typically a route-key `String`, matched by value (see [isHostedBy]).
     */
    internal var hostScreenOverride: Any? = null

    /**
     * Associate this ad with a screen the SDK can't infer from the view tree — a Jetpack Compose
     * destination, a custom navigation model, or a Flutter / React Native route. Pass the same token
     * you report to [AudienzzPrebidMobile.pageImpression]; it is matched by value, so the key
     * reported on the page impression and the one set here only have to be equal.
     *
     * Must be called **before** [load], which is when the ad joins the current page. A `null` token
     * clears the override and falls back to Fragment/Activity resolution.
     */
    fun setScreen(screenKey: Any?) {
        hostScreenOverride = screenKey
    }

    private fun resolveHostScreen(): Any? {
        hostScreenOverride?.let { return it }
        cachedHostScreen?.let { return it }
        val fragment = try {
            FragmentManager.findFragment<Fragment>(adView)
        } catch (e: IllegalStateException) {
            null // adView is not (yet) within a Fragment's view hierarchy
        }
        if (fragment != null) {
            cachedHostScreen = fragment
            return fragment
        }
        return adView.context.unwrapActivity()
    }

    /**
     * True when this ad lives on [screen]. Activities and Fragments match by object identity (no
     * `equals` override, so `==` collapses to `===`); an explicit [hostScreenOverride] such as a
     * route-key `String` matches by value, so the same key reported from two places still pairs up.
     */
    internal fun isHostedBy(screen: Any): Boolean {
        val host = resolveHostScreen() ?: return false
        return host === screen || host == screen
    }

    /**
     * Page transition. Active + already loaded → recreate (force a fresh auction); inactive →
     * release: stop the auction and refresh entirely and leave the slot dormant until its page comes
     * back. A never-loaded active banner is left for its normal lazy load.
     *
     * [screenActive] is what keeps the viewport gate, lazy load and [reloadAd] from reviving a
     * released banner in the meantime.
     */
    internal fun onPageActiveChanged(active: Boolean, epoch: Int) {
        screenActive = active
        val host = resolveHostScreen()?.javaClass?.simpleName ?: "none"
        if (active) {
            // Only an ACTIVE transition stamps the epoch. Stamping on release too would leave a
            // released banner at pageEpoch == coordinator.epoch, and the attach-time adoption test
            // (pageEpoch < epoch) could then never fire — which is exactly the case adoption exists
            // to repair: a banner released because its host wasn't resolvable yet.
            pageEpoch = epoch
            // A hard transition invalidates the outgoing auction even when the SAME page is
            // re-reported: an in-flight response from the previous visit must not load a creative
            // or overwrite this visit's auction analytics.
            auctionGeneration++
            if (lastRefreshTime != 0L) {
                Log.d(TAG, "pageChange adUnitId=${adView.adUnitId} host=$host — ACTIVE, recreating (loaded before)")
                reloadForScreenChange()
            } else {
                Log.d(TAG, "pageChange adUnitId=${adView.adUnitId} host=$host — ACTIVE, never loaded — re-arming initial load")
                rearmInitialLoad()
            }
        } else {
            Log.d(TAG, "pageChange adUnitId=${adView.adUnitId} host=$host — INACTIVE, releasing")
            releaseForPage()
        }
    }

    /**
     * Page release: stop everything. Cancels any pending stale-aware refresh and stops Prebid's
     * auto-refresh, so the handler issues no further auctions or GAM loads until its page returns.
     */
    private fun releaseForPage() {
        // Bump the generation FIRST so a response already in flight is recognised as stale and
        // cannot re-arm Prebid's timer or push a creative into a slot the user has left.
        auctionGeneration++
        pauseSmartRefresh()
    }

    /**
     * Incremented whenever this banner's liveness changes (page release, background). An auction
     * captures it at [fetchDemand] and every callback invocation re-checks it.
     *
     * Prebid's `BidLoader` re-arms its refresh timer from BOTH the success and the failure response
     * handler (`onResponse` / `failedToLoadBid` both call `setupRefreshTimer()`). So cancelling the
     * timer at release time is not enough: an auction started just before the release delivers its
     * response afterwards, re-arms the timer, and resurrects exactly the zombie loop page-scoping
     * exists to prevent.
     */
    @Volatile
    private var auctionGeneration: Int = 0

    /** The page epoch this banner was registered under; see [ScreenAdCoordinator.epoch]. */
    @Volatile
    private var pageEpoch: Int = 0

    /**
     * Join the page that is active at [load] time and start listening for the signals that can
     * change this banner's liveness: the app going to the background, and the ad view attaching
     * (which is when a host that wasn't resolvable during the page sweep finally resolves).
     *
     * Registration is unconditional — every banner is page-scoped, not just smart-refresh ones —
     * which is what lets the coordinator see banners created through the Flutter and React Native
     * bridges, since those never call [enableSmartRefresh].
     */
    private fun joinCurrentPage() {
        val coordinator = screenAdCoordinator
        coordinator?.register(this)
        pageEpoch = coordinator?.epoch ?: 0
        val active = coordinator?.activeScreen
        screenActive = active == null || isHostedBy(active)
        if (!screenActive) {
            Log.d(TAG, "joinCurrentPage() adUnitId=${adView.adUnitId} — built for a non-active page, released")
            releaseForPage()
        }

        AppForegroundMonitor.addListener(foregroundListener)

        // A banner whose view isn't attached yet cannot resolve its host Fragment/Activity, so a page
        // sweep running in that window releases it. Re-check on attach.
        adView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                // Any released banner is a candidate: the coordinator re-checks the host, which is
                // the thing that just became resolvable. Requiring an older epoch here excluded the
                // common case — created during the active epoch, before its Fragment was attached.
                if (!screenActive) {
                    screenAdCoordinator?.adoptIfOnActiveScreen(this@AudienzzAdViewHandler)
                }
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }

    /**
     * Prebid's refresh on Android is a `Handler.postDelayed` on the main Looper, which keeps running
     * while the app is in the background — and the original API hardcodes its visibility check to
     * always-true, so nothing else stops it. Left alone, a backgrounded app keeps auctioning and
     * GAM-loading indefinitely, producing requests that can never become impressions. (iOS gets this
     * for free: its refresh is a main-RunLoop `Timer`, which the OS freezes on backgrounding.)
     *
     * Coming back to the foreground is normally handled as a fresh page impression, which recreates
     * the active page's banners. An app that never calls `pageImpression` has no such transition, so
     * this restores its refresh directly — otherwise backgrounding once would silently kill refresh
     * for the rest of the process, changing behaviour for apps that don't use page impressions at
     * all.
     */
    private val foregroundListener = object : AppForegroundMonitor.Listener {
        override fun onEnterBackground() {
            Log.d(TAG, "background adUnitId=${adView.adUnitId} — stopping auto refresh")
            // Invalidate in-flight auctions as well: a response landing while backgrounded would
            // re-arm Prebid's timer (it re-arms from both its success and failure handlers).
            auctionGeneration++
            pauseSmartRefresh()
        }

        override fun onEnterForeground() {
            if (screenAdCoordinator?.activeScreen != null) {
                // Page-scoped app: the foreground page impression recreates this banner.
                return
            }
            Log.d(TAG, "foreground adUnitId=${adView.adUnitId} — no page impressions in use, resuming refresh")
            resumeSmartRefresh()
        }
    }

    /**
     * Force a fresh auction on screen activation (v2). Unlike [resumeSmartRefresh] (stale-aware),
     * this always refetches when the ad has loaded before — the "new pageImpression → reload"
     * semantics on screen change.
     */
    internal fun reloadForScreenChange() {
        if (storedCallback == null || lastRefreshTime == 0L) return
        pendingRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        pendingRefreshRunnable = null
        // Optionally blank the current creative (keeping the slot size — INVISIBLE reserves space)
        // so the refresh is visually obvious; restored when the fresh ad loads.
        if (AudienzzPrebidMobile.blankOnScreenReload) {
            adView.visibility = View.INVISIBLE
            blankedForReload = true
        }
        fetchDemand()
        adUnit.resumeAutoRefresh()
    }

    /**
     * Force a fresh auction now, ignoring the stale-aware timing of [resumeSmartRefresh].
     *
     * Public entry point for a manual reload — e.g. the React Native / Flutter bridges reloading a
     * banner when its screen (route/tab) becomes active again, or a publisher triggering a refresh
     * on demand. No-op before the handler has been set up (the initial load hasn't started yet).
     */
    fun reloadAd() {
        if (storedCallback == null) return
        // Never re-auction a banner the page sweep has released — the bridges broadcast reloads, and
        // without this a released banner on a kept-mounted route would come back to life.
        if (!screenActive) {
            Log.d(TAG, "reloadAd() adUnitId=${adView.adUnitId} — page released, skipping")
            return
        }
        pendingRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        pendingRefreshRunnable = null
        if (AudienzzPrebidMobile.blankOnScreenReload) {
            adView.visibility = View.INVISIBLE
            blankedForReload = true
        }
        fetchDemand()
        adUnit.resumeAutoRefresh()
    }

    private var blankedForReload = false

    private fun restoreFromBlankIfNeeded() {
        if (blankedForReload) {
            blankedForReload = false
            adView.visibility = View.VISIBLE
        }
    }

    /**
     * Executes ad loading if no request is running.
     *
     * @param withLazyLoading allows to postpone fetchDemand call until view is near the viewport.
     * @param prefetchMarginDp distance in dp before the view enters the viewport that triggers
     *   loading. Only used when [withLazyLoading] is true. Pass 0 to fire only when the view is
     *   exactly on screen (legacy behaviour). **Default: 400 dp.**
     *
     *   **RecyclerView note:** [prefetchMarginDp] has no practical effect inside a RecyclerView
     *   because RecyclerView only creates ViewHolders just before the item is displayed — the view
     *   is already positioned within the margin by the time [load] is called. For RecyclerView,
     *   use `withLazyLoading = false` and rely on [androidx.recyclerview.widget.RecyclerView]'s
     *   own item prefetch (`setItemPrefetchEnabled` / `setInitialPrefetchItemCount`).
     */
    @JvmOverloads fun load(
        withLazyLoading: Boolean = true,
        prefetchMarginDp: Int = 200,
        gamRequestBuilder: AdManagerAdRequest.Builder = AdManagerAdRequest.Builder(),
        callback: (AdManagerAdRequest, AudienzzResultCode?) -> Unit,
    ) {
        this.gamRequestBuilder = gamRequestBuilder
        storedCallback = callback
        lazyLoadConfig = withLazyLoading to prefetchMarginDp
        joinCurrentPage()

        if (withLazyLoading) {
            initialTriggerArmed = true
            if (prefetchMarginDp > 0) {
                Log.d(TAG, "load() adUnitId=${adView.adUnitId} — lazy ON, prefetchMargin=${prefetchMarginDp}dp, waiting for view to enter range")
                adView.addPrefetchMarginListener(marginDp = prefetchMarginDp) { onLazyTrigger() }
            } else {
                Log.d(TAG, "load() adUnitId=${adView.adUnitId} — lazy ON, prefetchMargin=0 (exact visibility), waiting for view to appear")
                adView.addOnBecameVisibleOnScreenListener { onLazyTrigger() }
            }
        } else if (screenActive) {
            Log.d(TAG, "load() adUnitId=${adView.adUnitId} — lazy OFF, starting fetchDemand immediately")
            fetchDemand()
        } else {
            // Built for a page the user has already left (async setup that finished after the
            // transition). Stay dormant; onPageActiveChanged re-arms this when the page returns.
            Log.d(TAG, "load() adUnitId=${adView.adUnitId} — lazy OFF but page not active, deferring")
        }
    }

    /**
     * Re-arm the very first load for a banner that never got one because its page wasn't active
     * when the trigger fired.
     *
     * The viewport helpers are one-shot: they remove their listener before invoking the callback, so
     * a callback rejected for an inactive page consumes the only trigger the banner had. Without
     * this the slot stays dormant forever — page activation does nothing, because activation only
     * recreates banners that have loaded before.
     */
    private fun rearmInitialLoad() {
        if (storedCallback == null || lastRefreshTime != 0L) return
        // Don't stack triggers. The original listener may never have fired (still outside the
        // margin), in which case it is still armed; and an initial request may already be in
        // flight, in which case lastRefreshTime is still 0 but a second fetch would double-auction.
        if (initialTriggerArmed || initialLoadRequested) {
            Log.d(TAG, "rearmInitialLoad() adUnitId=${adView.adUnitId} — trigger already armed or request in flight")
            return
        }
        val lazy = lazyLoadConfig
        if (lazy == null) {
            fetchDemand()
            return
        }
        val (withLazyLoading, prefetchMarginDp) = lazy
        if (!withLazyLoading) {
            fetchDemand()
        } else {
            initialTriggerArmed = true
            if (prefetchMarginDp > 0) {
                adView.addPrefetchMarginListener(marginDp = prefetchMarginDp) { onLazyTrigger() }
            } else {
                adView.addOnBecameVisibleOnScreenListener { onLazyTrigger() }
            }
        }
    }

    /** True while a one-shot lazy trigger is registered and unconsumed. */
    private var initialTriggerArmed: Boolean = false

    /** True once a first request has actually been issued, so a re-arm can't double-auction. */
    private var initialLoadRequested: Boolean = false

    /** Shared body for the lazy-load triggers, so [load] and [rearmInitialLoad] behave identically. */
    private fun onLazyTrigger() {
        // The helpers are one-shot: reaching here means the listener has already removed itself.
        initialTriggerArmed = false
        if (!screenActive) {
            Log.d(TAG, "load() adUnitId=${adView.adUnitId} — lazy trigger fired but page released, will re-arm on activation")
            return
        }
        if (lastRefreshTime != 0L) return
        Log.d(TAG, "load() adUnitId=${adView.adUnitId} — lazy trigger fired, starting fetchDemand")
        fetchDemand()
    }

    /** Lazy-load settings from [load], retained so [rearmInitialLoad] can re-register the trigger. */
    private var lazyLoadConfig: Pair<Boolean, Int>? = null

    /**
     * M1: builds a fresh [AdManagerAdRequest] from the retained builder, re-reading the PPID and
     * re-applying current global targeting so every auction (including refreshes) reflects the
     * latest PPID/consent/targeting instead of a snapshot frozen at first load. GAM's
     * addCustomTargeting/setPublisherProvidedId overwrite per key, so reusing the builder does not
     * duplicate values.
     */
    private fun buildRequest(): AdManagerAdRequest {
        val builder = gamRequestBuilder ?: AdManagerAdRequest.Builder()
        AudienzzPrebidMobile.ppidManager?.getPpid()?.let { builder.setPublisherProvidedId(it) }
        return AudienzzTargetingParams.CUSTOM_TARGETING_MANAGER
            .applyToGamRequestBuilder(builder)
            .build()
    }

    /**
     * Enables viewport-aware smart refresh: pauses auto-refresh when the view scrolls off-screen
     * and resumes — firing immediately if the creative is stale, or after the remaining interval
     * if not — when it returns to the viewport.
     *
     * Call once after [load]. Stop tracking with [disableSmartRefresh].
     */
    fun enableSmartRefresh() {
        if (smartRefreshListener != null) {
            Log.d(TAG, "enableSmartRefresh() adUnitId=${adView.adUnitId} — already enabled, skipping")
            return
        }
        val useV2 = AudienzzPrebidMobile.isSmartRefreshV2Enabled()
        Log.d(TAG, "enableSmartRefresh() adUnitId=${adView.adUnitId} — smart refresh enabled (v2=$useV2), refreshInterval=${adUnit.autoRefreshTime}ms")
        smartRefreshListener = adView.addContinuousVisibilityListener(
            useDirectionalGate = useV2,
            onBecameVisible = {
                // Screen-aware (v2): never auto-resume via the viewport gate while this ad's screen
                // is inactive — the screen coordinator owns pause/reload. Always true under legacy.
                if (!screenActive) {
                    return@addContinuousVisibilityListener
                }
                if (storedCallback == null) {
                    Log.w(TAG, "smartRefresh adUnitId=${adView.adUnitId} — became visible but not loaded yet (no callback), skipping")
                    return@addContinuousVisibilityListener
                }
                if (lastRefreshTime == 0L) {
                    Log.d(TAG, "smartRefresh adUnitId=${adView.adUnitId} — became visible before first load, skipping smart refresh")
                    return@addContinuousVisibilityListener
                }

                pendingRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }

                val refreshIntervalMs = adUnit.autoRefreshTime.toLong()
                if (refreshIntervalMs <= 0) {
                    Log.d(TAG, "smartRefresh adUnitId=${adView.adUnitId} — became visible, no refresh interval set, resuming auto-refresh only")
                    adUnit.resumeAutoRefresh()
                    return@addContinuousVisibilityListener
                }

                val elapsed = System.currentTimeMillis() - lastRefreshTime
                val remaining = maxOf(0L, refreshIntervalMs - elapsed)

                if (remaining == 0L) {
                    Log.d(TAG, "smartRefresh adUnitId=${adView.adUnitId} — became visible, ad is STALE (elapsed=${elapsed}ms >= interval=${refreshIntervalMs}ms), force-refreshing now")
                    fetchDemand()
                    adUnit.resumeAutoRefresh()
                } else {
                    Log.d(TAG, "smartRefresh adUnitId=${adView.adUnitId} — became visible, ad is fresh (elapsed=${elapsed}ms, remaining=${remaining}ms), scheduling refresh in ${remaining}ms")
                    val runnable = Runnable {
                        Log.d(TAG, "smartRefresh adUnitId=${adView.adUnitId} — scheduled refresh fired after ${remaining}ms delay")
                        fetchDemand()
                        adUnit.resumeAutoRefresh()
                    }
                    pendingRefreshRunnable = runnable
                    refreshHandler.postDelayed(runnable, remaining)
                }
            },
            onBecameHidden = {
                Log.d(TAG, "smartRefresh adUnitId=${adView.adUnitId} — became hidden, stopping auto-refresh and cancelling any pending refresh")
                pendingRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }
                pendingRefreshRunnable = null
                adUnit.stopAutoRefresh()
            },
        )

        // C3: onBecameHidden above is edge-triggered (visible -> hidden). A view that was
        // prefetched while off-screen and is still not on screen never produced that edge, so its
        // auto-refresh — armed by the prefetch fetchDemand — would loop forever at 0% viewability.
        // Do an initial *level* check here: if the view isn't refresh-eligible yet, stop refresh
        // now; the onBecameVisible edge will resume/refresh it (stale-aware) once its top is fully
        // on screen with >=50% visible.
        val eligibleAtEnable = if (useV2) adView.isRefreshEligible() else adView.isVisibleForSmartRefresh()
        if (!eligibleAtEnable) {
            Log.d(TAG, "enableSmartRefresh() adUnitId=${adView.adUnitId} — view not refresh-eligible at enable time (likely prefetched off-screen or top clipped), stopping auto-refresh until it enters the viewport")
            adUnit.stopAutoRefresh()
        }

        // Page registration happens in load(), unconditionally -- see joinCurrentPage().
    }

    /**
     * Called by the Flutter Dart visibility layer when the ad becomes hidden (< 20% on screen).
     * Cancels any pending scheduled refresh and stops Prebid's auto-refresh timer.
     *
     * Designed to be driven from outside (e.g. Flutter's RenderBox.localToGlobal() polling)
     * instead of the native [enableSmartRefresh] OnPreDrawListener, which is unreliable inside
     * Flutter because the platform view is never physically moved when a Flutter scroll occurs.
     */
    fun pauseSmartRefresh() {
        Log.d(TAG, "pauseSmartRefresh() adUnitId=${adView.adUnitId} — pausing, cancelling pending refresh")
        pendingRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        pendingRefreshRunnable = null
        adUnit.stopAutoRefresh()
    }

    /**
     * Called by the Flutter Dart visibility layer when the ad becomes visible (≥ 20% on screen).
     * Implements stale-aware logic identical to the [enableSmartRefresh] onBecameVisible block:
     * - If the ad content is stale (elapsed ≥ refresh interval) → force-fetch demand immediately.
     * - Otherwise → schedule the next fetch for the remaining interval, then resume auto-refresh.
     *
     * This corrects the plain [org.audienzz.mobile.AudienzzAdUnit.resumeAutoRefresh] call which
     * resets Prebid's timer to 0, ignoring however long the ad has already been displayed.
     */
    fun resumeSmartRefresh() {
        // A released banner must stay dormant: the Flutter/RN visibility layer and
        // AudienzzRemoteBannerView.onResume() both reach this, and without the guard either would
        // restart the timer — or immediately re-auction — for a page the user has left.
        if (!screenActive) {
            Log.d(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — page released, skipping")
            return
        }
        if (storedCallback == null) {
            Log.w(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — not loaded yet (no callback), skipping")
            return
        }

        pendingRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        pendingRefreshRunnable = null

        if (lastRefreshTime == 0L) {
            // Never completed a first fetch. Resuming the timer would restart the RETIRED loader,
            // whose callback still carries a superseded generation — so its response would be
            // dropped and the slot would stay blank forever. Issue a fresh request instead.
            Log.d(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — no prior fetch, starting a fresh one")
            fetchDemand()
            return
        }

        val refreshIntervalMs = adUnit.autoRefreshTime.toLong()
        if (refreshIntervalMs <= 0) {
            Log.d(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — no refresh interval set, resuming")
            adUnit.resumeAutoRefresh()
            return
        }

        val elapsed = System.currentTimeMillis() - lastRefreshTime
        val remaining = maxOf(0L, refreshIntervalMs - elapsed)

        if (remaining == 0L) {
            Log.d(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — ad is STALE (elapsed=${elapsed}ms >= interval=${refreshIntervalMs}ms), force-refreshing now")
            fetchDemand()
            adUnit.resumeAutoRefresh()
        } else {
            Log.d(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — ad is fresh (elapsed=${elapsed}ms, remaining=${remaining}ms), scheduling refresh in ${remaining}ms")
            val runnable = Runnable {
                Log.d(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — scheduled refresh fired after ${remaining}ms delay")
                fetchDemand()
                adUnit.resumeAutoRefresh()
            }
            pendingRefreshRunnable = runnable
            refreshHandler.postDelayed(runnable, remaining)
        }
    }

    /** Stops smart refresh tracking started by [enableSmartRefresh]. */
    fun disableSmartRefresh() {
        Log.d(TAG, "disableSmartRefresh() adUnitId=${adView.adUnitId}")
        smartRefreshListener?.let {
            if (adView.viewTreeObserver.isAlive) {
                adView.viewTreeObserver.removeOnPreDrawListener(it)
            }
        }
        smartRefreshListener = null
        pendingRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        pendingRefreshRunnable = null
        screenAdCoordinator?.deregister(this)
        AppForegroundMonitor.removeListener(foregroundListener)
        // disableSmartRefresh() is the teardown hook called from the ad view's destroy().
        viewabilityTracker?.stop()
        viewabilityTracker = null
    }

    /**
     * Releases all refresh and lifecycle resources held by this handler: stops smart refresh,
     * cancels any pending scheduled refresh, stops Prebid's auto-refresh and destroys the
     * underlying Prebid ad unit (tearing down its [org.prebid.mobile.BidLoader] so no further
     * auctions fire), and drops the retained request/callback.
     *
     * H1: without this there was no way to stop the refresh loop or release the
     * `BidLoader -> listener -> adView -> Activity` chain on view detach / Activity destroy when
     * smart refresh is off (the default). Call from the host's lifecycle teardown (e.g. Activity
     * `onDestroy` or a RecyclerView `onViewRecycled`). The handler must not be reused afterwards.
     */
    fun destroy() {
        Log.d(TAG, "destroy() adUnitId=${adView.adUnitId}")
        disableSmartRefresh()
        adUnit.stopAutoRefresh()
        adUnit.destroy()
        gamRequestBuilder = null
        storedCallback = null
    }

    /**
     * The one place an auction can start. Every entry point — first load, lazy trigger, viewport
     * resume, page activation, manual reload, Prebid's own refresh — funnels through [fetchDemand],
     * so this is the single gate that decides whether auctioning is legitimate right now. Guarding
     * the call sites individually is what let earlier revisions leak an auction through whichever
     * path was missed.
     */
    private fun canStartAuction(): Boolean {
        if (!screenActive) {
            Log.d(TAG, "auction blocked adUnitId=${adView.adUnitId} — page released")
            return false
        }
        if (!AppForegroundMonitor.isForeground) {
            Log.d(TAG, "auction blocked adUnitId=${adView.adUnitId} — app is backgrounded")
            return false
        }
        return true
    }

    private fun fetchDemand() {
        val callback = storedCallback ?: run {
            Log.w(TAG, "fetchDemand() adUnitId=${adView.adUnitId} — no stored callback, skipping")
            return
        }
        if (!canStartAuction()) return
        // Every new auction supersedes the previous one.
        auctionGeneration++
        initialLoadRequested = true
        val request = buildRequest()
        val isAutorefresh = adUnit.autoRefreshTime > 0
        val autorefreshTime = adUnit.autoRefreshTime.toLong()
        val isRefresh = !isFirstDemandFetch
        isFirstDemandFetch = false
        Log.d(TAG, "fetchDemand() adUnitId=${adView.adUnitId} — isRefresh=$isRefresh, autorefresh=${autorefreshTime}ms")

        val requestStartMs = System.currentTimeMillis()
        // Mint the auction id up front so bidRequest and every later event of this auction share it.
        currentAuctionId = UUID.randomUUID().toString()
        eventLogger?.bidRequest(
            adViewId = adView.adViewId,
            adUnitId = adView.adUnitId,
            sizes = adView.adSizes?.asIterable()?.sizesJson,
            auctionId = currentAuctionId,
            adType = AdType.BANNER,
            adSubtype = adUnit.adFormats.adSubtype,
            apiType = ApiType.ORIGINAL,
            autorefreshTime = autorefreshTime,
            isAutorefresh = isAutorefresh,
            isRefresh = isRefresh,
            adUnitCode = adUnit.configId,
            mediaTypes = mediaTypesJson(adUnit.adFormats.adSubtype),
        )
        // C1: Prebid's fetchDemand assigns a NEW BidLoader to AdUnit.bidLoader without retiring the
        // previous one, and a retired loader re-arms its own refresh timer from BOTH its success and
        // its failure handler. stopAutoRefresh() is not enough to stop it: that cancels whatever is
        // in AdUnit.bidLoader *now*, which after a replacement is the NEW loader — so the old one
        // keeps auctioning while repeatedly cancelling its successor. destroy() retires the loader
        // properly (it nulls the listeners and kills the timer task), which is what actually ends
        // the previous auction loop.
        adUnit.destroy()

        // Prebid re-invokes this listener on every auto-refresh without re-entering fetchDemand().
        // The first invocation pairs with the bidRequest above; each later one is a refresh auction
        // that emits its own bidRequest so the bidRequest/bidResponse funnel stays balanced.
        val generationAtRequest = auctionGeneration
        var isFirstAuction = true
        adUnit.fetchDemand(request) { resultCode ->
            // Stale-response guard. Prebid re-arms its refresh timer from both the success and the
            // failure handler, so a response that lands after a page release would restart the loop
            // and load a creative into a slot the user has left. Re-cancel the timer Prebid just
            // armed and drop the response.
            if (generationAtRequest != auctionGeneration || !screenActive) {
                // Drop only. Calling adUnit.stopAutoRefresh() here would cancel whatever loader is
                // current — after a reactivation that is the NEW auction's loader, so the stale
                // callback would repeatedly kill its own replacement. The superseded loader was
                // already retired by adUnit.destroy() when this auction's successor started.
                Log.d(
                    TAG,
                    "fetchDemand() adUnitId=${adView.adUnitId} — response superseded " +
                        "(gen $generationAtRequest vs $auctionGeneration, screenActive=$screenActive), dropping",
                )
                return@fetchDemand
            }
            val auctionIsRefresh = isRefresh || !isFirstAuction
            if (!isFirstAuction) {
                // A Prebid auto-refresh is a new auction — mint a fresh id for its funnel.
                currentAuctionId = UUID.randomUUID().toString()
                eventLogger?.bidRequest(
                    adViewId = adView.adViewId,
                    adUnitId = adView.adUnitId,
                    sizes = adView.adSizes?.asIterable()?.sizesJson,
            auctionId = currentAuctionId,
                    adType = AdType.BANNER,
                    adSubtype = adUnit.adFormats.adSubtype,
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = autorefreshTime,
                    isAutorefresh = isAutorefresh,
                    isRefresh = true,
                    adUnitCode = adUnit.configId,
                    mediaTypes = mediaTypesJson(adUnit.adFormats.adSubtype),
                )
            }
            // New auction → reset render-winner state until the GAM render / app event report back.
            prebidLineItemWon = false
            prebidWinningBidder = null
            lastWinningBid = null
            lastRefreshTime = System.currentTimeMillis()
            setEventsListenerToAdView()
            callback.invoke(request, resultCode)

            // Prebid reports SUCCESS even for an empty/error response (e.g. STORED_REQUEST_NOT_FOUND).
            // A real Prebid win always carries hb_bidder, so gate the win on it; otherwise it's a no-bid.
            val winningBidder = request.prebidKeyword(HB_BIDDER_KEY)
            val timeToRespond =
                if (isFirstAuction) System.currentTimeMillis() - requestStartMs else null
            var economics: RenderEconomics? = null
            if (resultCode == AudienzzResultCode.SUCCESS && winningBidder != null) {
                prebidWinningBidder = winningBidder
                val win = adUnit.getWinningBid()
                lastWinningBid = win
                economics = RenderEconomics(
                    bidderCode = winningBidder,
                    winnerBidderCode = winningBidder,
                    winnerType = WINNER_TYPE_RTB,
                    priceBucket = request.prebidKeyword(HB_PB_KEY),
                    hbSize = request.prebidKeyword(HB_SIZE_KEY),
                    hbFormat = request.prebidKeyword(HB_FORMAT_KEY),
                    mediaType = request.prebidKeyword(HB_FORMAT_KEY),
                    size = request.prebidKeyword(HB_SIZE_KEY),
                    cpm = win?.cpm,
                    currency = win?.currency,
                    creativeId = win?.creativeId,
                    // Reuse the SDK-minted auction id (not Prebid's) so the whole funnel counts together.
                    auctionId = currentAuctionId,
                    adId = win?.adId,
                    timeToRespond = timeToRespond,
                    slotReload = slotReloadCount,
                )
                lastRenderEconomics = economics
            } else {
                lastRenderEconomics = null
            }

            eventLogger?.bidResponse(
                adViewId = adView.adViewId,
                adUnitId = adView.adUnitId,
                sizes = adView.adSizes?.asIterable()?.sizesJson,
                adType = AdType.BANNER,
                adSubtype = adUnit.adFormats.adSubtype,
                apiType = ApiType.ORIGINAL,
                autorefreshTime = autorefreshTime,
                isAutorefresh = isAutorefresh,
                isRefresh = auctionIsRefresh,
                resultCode = resultCode?.toString(),
                // Only the initial auction has a measurable request→response delta; Prebid does not
                // expose the start time of an internal refresh.
                timeToRespond = timeToRespond,
                adUnitCode = adUnit.configId,
                economics = economics,
            )
            if (economics != null) {
                eventLogger?.bidWon(
                    adViewId = adView.adViewId,
                    adUnitId = adView.adUnitId,
                    sizes = adView.adSizes?.asIterable()?.sizesJson,
                    adType = AdType.BANNER,
                    adSubtype = adUnit.adFormats.adSubtype,
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = autorefreshTime,
                    isAutorefresh = isAutorefresh,
                    isRefresh = auctionIsRefresh,
                    adUnitCode = adUnit.configId,
                    economics = economics,
                )
            } else {
                eventLogger?.noBid(
                    adViewId = adView.adViewId,
                    adUnitId = adView.adUnitId,
                    sizes = adView.adSizes?.asIterable()?.sizesJson,
            auctionId = currentAuctionId,
                    adType = AdType.BANNER,
                    adSubtype = adUnit.adFormats.adSubtype,
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = autorefreshTime,
                    isAutorefresh = isAutorefresh,
                    isRefresh = auctionIsRefresh,
                    // Prebid returns SUCCESS with empty targeting on a no-bid; report NO_BIDS so the
                    // funnel doesn't show a "successful" no-bid. Real failures keep their result code.
                    resultCode = noBidResultCode(resultCode),
                    adUnitCode = adUnit.configId,
                    mediaTypes = mediaTypesJson(adUnit.adFormats.adSubtype),
                )
            }
            slotReloadCount++
            isFirstAuction = false
        }
    }

    private fun setEventsListenerToAdView() {
        // H2: install the analytics wrapper exactly once. This method runs in every auction's
        // completion (initial + each Prebid auto-refresh); re-wrapping each time nested the prior
        // wrapper, so after N refreshes a single real click fired adClick N+1 times (and re-invoked
        // the publisher's callbacks N+1 times). GAM reuses the same adView listener across
        // refreshes, so wrapping the publisher's listener once is sufficient.
        if (eventListenerInstalled) return
        eventListenerInstalled = true

        // GAM fires an app event when the Prebid line item wins the ad-server auction; absence of
        // it by impression time means a non-Prebid (Google/ad-server) creative rendered. Chain any
        // listener the publisher already set.
        val actualAppEventListener = adView.appEventListener
        adView.appEventListener = AppEventListener { name, info ->
            actualAppEventListener?.onAppEvent(name, info)
            if (name.equals(PREBID_APP_EVENT, ignoreCase = true)) {
                Log.d(TAG, "onAppEvent($name) — Prebid line item won for ${adView.adUnitId}")
                prebidLineItemWon = true
            }
        }

        val actualListener: AdListener? = adView.adListener
        adView.adListener = object : AdListener() {

            override fun onAdClicked() {
                actualListener?.onAdClicked()
                eventLogger?.adClick(
                    adUnitId = adView.adUnitId,
                    adType = AdType.BANNER,
                    adSubtype = adUnit.adFormats.adSubtype,
                    apiType = ApiType.ORIGINAL,
                    adUnitCode = adUnit.configId,
                    economics = renderEconomics(),
                )
            }

            override fun onAdLoaded() {
                restoreFromBlankIfNeeded()
                actualListener?.onAdLoaded()
            }

            override fun onAdOpened() {
                actualListener?.onAdOpened()
            }

            override fun onAdClosed() {
                actualListener?.onAdClosed()
            }

            override fun onAdImpression() {
                actualListener?.onAdImpression()
                eventLogger?.adImpression(
                    adUnitId = adView.adUnitId,
                    adType = AdType.BANNER,
                    adSubtype = adUnit.adFormats.adSubtype,
                    apiType = ApiType.ORIGINAL,
                    adUnitCode = adUnit.configId,
                    economics = renderEconomics(),
                )
                startViewabilityTracking()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                restoreFromBlankIfNeeded()
                actualListener?.onAdFailedToLoad(error)
            }

            override fun onAdSwipeGestureClicked() {
                actualListener?.onAdSwipeGestureClicked()
            }
        }
    }

    /**
     * Starts (or restarts, on a refreshed creative) viewability tracking for the rendered ad.
     * Fires `viewability.start` when the banner first becomes ≥50% visible and
     * `viewability.success` once it stays ≥50% visible for one continuous second.
     */
    private fun startViewabilityTracking() {
        val tracker = viewabilityTracker ?: ViewabilityTracker(
            view = adView,
            onStart = {
                eventLogger?.viewabilityStart(
                    adUnitId = adView.adUnitId,
                    adType = AdType.BANNER,
                    adSubtype = adUnit.adFormats.adSubtype,
                    apiType = ApiType.ORIGINAL,
                    adUnitCode = adUnit.configId,
                    economics = renderEconomics(),
                )
            },
            onSuccess = {
                eventLogger?.viewabilitySuccess(
                    adUnitId = adView.adUnitId,
                    adType = AdType.BANNER,
                    adSubtype = adUnit.adFormats.adSubtype,
                    apiType = ApiType.ORIGINAL,
                    adUnitCode = adUnit.configId,
                    economics = renderEconomics(),
                )
            },
        ).also { viewabilityTracker = it }
        tracker.start()
    }

    /**
     * Resolves which demand actually rendered in GAM, for `bidder_code` / `winner_bidder_code`:
     * - Prebid line item won (the GAM app event fired) → the Prebid winning bidder (`hb_bidder`)
     * - otherwise → the ad server ([AD_SERVER_BIDDER], i.e. Google/AdX/direct)
     *
     * **Reliability:** the Prebid case depends on the GAM Prebid line item being configured to emit
     * an app event named [PREBID_APP_EVENT]. Without that adops setup, every render is attributed to
     * [AD_SERVER_BIDDER]. `ResponseInfo` is read only for diagnostic logging.
     */
    private fun resolveBidderCode(): String =
        if (prebidLineItemWon) {
            prebidWinningBidder ?: PREBID_BIDDER
        } else {
            adView.responseInfo?.loadedAdapterResponseInfo?.adSourceName?.let { adSource ->
                Log.d(TAG, "adImpression — ad server rendered for ${adView.adUnitId}, adSource=$adSource")
            }
            AD_SERVER_BIDDER
        }

    /**
     * Economics reported on render events. The Prebid line item won the GAM auction only if its app
     * event fired; otherwise the ad server rendered — report a direct impression with no Prebid
     * economics (only the ad-server bidder code).
     */
    private fun renderEconomics(): RenderEconomics {
        // Always carry the winning-bid economics that were in play; bidder_code reflects the actual
        // render winner (Prebid line item when its GAM app event fired, else the ad server).
        val base = lastRenderEconomics ?: RenderEconomics()
        val bidder = resolveBidderCode()
        return base.copy(
            bidderCode = bidder,
            // Ad server rendered — the Prebid bid's creative id would make the enricher misclassify a
            // direct-sold impression as RTB. Report the GAM creative id when available, else the "0"
            // stub (GMA exposes no served-creative id → "0").
            creativeId = if (bidder == AD_SERVER_BIDDER) "0" else base.creativeId,
            // Always carry the SDK-minted auction id, even on a direct fill with no Prebid economics.
            auctionId = base.auctionId ?: currentAuctionId,
        )
    }
}

/** `media_types` as a JSON array string (web-schema parity), derived from the ad subtype. */
internal fun mediaTypesJson(subtype: AdSubtype): String = when (subtype) {
    AdSubtype.VIDEO -> "[\"video\"]"
    AdSubtype.MULTIFORMAT -> "[\"banner\",\"video\"]"
    else -> "[\"banner\"]"
}

/** `winner_type` values (web-clickstream parity), shared across the original-API handlers. */
internal const val WINNER_TYPE_RTB = "RTB"
internal const val WINNER_TYPE_DIRECT = "direct"
