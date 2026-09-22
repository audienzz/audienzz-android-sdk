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
import org.audienzz.mobile.util.AudienzzDiagnostics
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
import org.audienzz.mobile.refresh.AudienzzRefreshController
import org.audienzz.mobile.refresh.RefreshBlockReason
import org.audienzz.mobile.refresh.RefreshRequestReason
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

class AudienzzAdViewHandler @JvmOverloads constructor(
    private val adView: AdManagerAdView,
    private val adUnit: AudienzzAdUnit,
    val requestContext: org.audienzz.mobile.targeting.AudienzzAdRequestContext = org.audienzz.mobile.targeting.AudienzzAdRequestContext(),
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

    // Serialize Google loads across page changes, bounded by a watchdog if completion is lost.
    // Google callbacks have no request ID: after expiry, an extremely late result cannot be
    // distinguished from a newer load's result on this publisher-owned view.
    private data class GoogleLoad(val auction: Int, val refresh: Int)
    private var googleLoadTimeout: Runnable? = null
    private var creativePageGeneration = 0
    private var renderAuctionId: String? = null
    private var googleLoad: GoogleLoad? = null
    private var googleEventPageGeneration: Int? = null
    private val acceptsGoogleEvents: Boolean
        get() = googleEventPageGeneration == creativePageGeneration && screenActive && !refreshController.isDestroyed
    private var pendingLoadReason: RefreshRequestReason? = null

    private var isFirstDemandFetch = true
    private var installedAdListener: AdListener? = null
    private var installedAppEventListener: AppEventListener? = null
    private var publisherAdListener: AdListener? = null
    private var publisherAppEventListener: AppEventListener? = null

    // Smart refresh state
    private var smartRefreshListener: ViewTreeObserver.OnPreDrawListener? = null
    private var lastRefreshTime: Long = 0
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
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
    // How many times this slot has (re)loaded. Internal only.
    //
    // What is REPORTED is [emittedSlotReload], a binary flag. The counter itself used to be the
    // reported value, so a slot that refreshed four times emitted slot_reload 0,1,2,3 — the
    // collector's contract is "first load or not".
    private var slotReloadCount: Int = 0

    // Economics of the creative CURRENTLY ON SCREEN, snapshotted when Google confirmed it rendered.
    //
    // Render events must describe the creative the reader is actually looking at. Reading the most
    // recent auction instead meant that as soon as a replacement's Prebid response arrived — or as
    // soon as it failed and cleared these fields — a late impression, click or viewability callback
    // belonging to the creative still on screen was reported under the replacement's auction id,
    // cpm, creative and bidder.
    private var displayedEconomics: RenderEconomics? = null

    // The Prebid seat behind the DISPLAYED creative, and whether that seat's GAM line item is what
    // actually rendered. Snapshotted rather than read live, because starting the next auction
    // resets the live values — which would silently re-attribute a creative still on screen.
    private var displayedPrebidBidder: String? = null
    private var displayedPrebidLineItemWon: Boolean = false

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

    /** How this slot is named in an `AUDZ` diagnostics line. */
    internal fun diagnosticLabel(): String = adView.adUnitId ?: "unknown"

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
            retireCurrentAuction()
            pendingLoadReason = null
            refreshController.unblock(RefreshBlockReason.PAGE_INACTIVE, schedule = false)
            if (AppForegroundMonitor.isForeground) {
                refreshController.unblock(RefreshBlockReason.APP_BACKGROUND, schedule = false)
            }
            if (lastRefreshTime != 0L) {
                Log.d(TAG, "pageChange adUnitId=${adView.adUnitId} host=$host — ACTIVE, recreating (loaded before)")
                reloadForScreenChange()
            } else {
                Log.d(TAG, "pageChange adUnitId=${adView.adUnitId} host=$host — ACTIVE, never loaded — re-arming initial load")
                rearmInitialLoad()
            }
        } else {
            Log.d(TAG, "pageChange adUnitId=${adView.adUnitId} host=$host — INACTIVE, releasing")
            refreshController.block(RefreshBlockReason.PAGE_INACTIVE)
            releaseForPage()
        }
    }

    /**
     * Page release: stop everything. Cancels any pending stale-aware refresh and stops Prebid's
     * auto-refresh, so the handler issues no further auctions or GAM loads until its page returns.
     */
    private fun releaseForPage() {
        creativePageGeneration++
        // Bump the generation FIRST so a response already in flight is recognised as stale.
        auctionGeneration++
        retireCurrentAuction()
        // The creative is retired here, so blank it here too. Blanking only once the replacement
        // auction starts meant the outgoing creative was still on screen when the page came back —
        // the user saw the *previous* ad, then a blank, then the new one. Clearing it on the way out
        // means the slot is already empty on the way in.
        blankForReloadIfNeeded()
    }

    /**
     * Stop the refresh timer AND retire the Prebid loader behind it.
     *
     * Cancelling the timer is not enough on its own: a response still in flight re-arms it from both
     * Prebid's success and failure handlers, and Prebid's own refresh calls `load()` directly — it
     * never passes through [canStartAuction]. Without an in-flight successor to destroy it, that
     * loader keeps auctioning forever while every callback is dropped as stale.
     *
     * `AdUnit.destroy()` retires the loader properly (nulls its listeners, kills its timer task) and
     * leaves the ad unit reusable: the next [fetchDemand] builds a fresh loader.
     */
    private fun retireCurrentAuction() {
        // Retires the outstanding Prebid loader and any scheduled work, WITHOUT recording a block
        // reason. It used to route through pauseSmartRefresh(), which now means "not visible" — so
        // a page transition left the banner permanently blocked on a visibility reason that nothing
        // would ever clear, and the replacement it was supposed to issue never ran.
        //
        // Whether refresh is allowed afterwards is the caller's decision: a page release blocks
        // PAGE_INACTIVE, backgrounding blocks APP_BACKGROUND, and a page activation blocks nothing.
        refreshController.invalidatePending()
        pendingLoadReason = null
        adUnit.destroy()
        initialRequestGeneration = null
        // A cancelled replacement will never reach the Google callback that restores its blank, so
        // without this the slot would sit empty with nothing on the way to refill it. (It no longer
        // also stalls the next auction: blanking hides the ad view's children, not the ad view the
        // visibility gate reads. A caller that is retiring in order to replace — a page release, a
        // page activation — blanks again straight after.)
        restoreFromBlankIfNeeded()
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
            refreshController.block(RefreshBlockReason.PAGE_INACTIVE)
            releaseForPage()
        }

        // The impression owner must be registered before the banner's recovery listener.
        AudienzzPrebidMobile.observeForegroundReimpression()
        AppForegroundMonitor.addListener(foregroundListener)
        if (!adView.isAttachedToWindow) refreshController.block(RefreshBlockReason.DETACHED)
        if (!AppForegroundMonitor.isForeground) refreshController.block(RefreshBlockReason.APP_BACKGROUND)

        // A banner whose view isn't attached yet cannot resolve its host Fragment/Activity, so a page
        // sweep running in that window releases it. Re-check on attach.
        adView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                refreshController.unblock(RefreshBlockReason.DETACHED, schedule = false)
                // Any released banner is a candidate: the coordinator re-checks the host, which is
                // the thing that just became resolvable. Requiring an older epoch here excluded the
                // common case — created during the active epoch, before its Fragment was attached.
                if (!screenActive) {
                    screenAdCoordinator?.adoptIfOnActiveScreen(this@AudienzzAdViewHandler)
                }
                resumeEligibleWork()
            }

            override fun onViewDetachedFromWindow(v: View) {
                // A detached view cannot render, so a refresh into it would be an impression-less
                // request. Reattaching clears only this reason.
                refreshController.block(RefreshBlockReason.DETACHED)
            }
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
            refreshController.block(RefreshBlockReason.APP_BACKGROUND)
            // Invalidate in-flight auctions and retire the loader behind them: a response landing
            // while backgrounded re-arms Prebid's timer from both its success and failure handlers,
            // and that refresh calls load() directly without passing the auction gate.
            auctionGeneration++
            retireCurrentAuction()
            // The OTHER refresh owner. A GAM ad unit can carry a server-configured refresh rate,
            // which the GMA banner runs entirely on its own — the SDK cannot read it, and the
            // request it issues reuses the last auction's Prebid keywords. `BaseAdView.pause()` is
            // the one lever the GMA API gives us over it, and it is the call Google documents for
            // Activity.onPause; without it a backgrounded app keeps taking GAM refreshes nobody can
            // see. (No equivalent exists on iOS — GMA exposes no refresh, pause or resume there —
            // so the server-side setting is the only control on that platform.)
            runCatching { adView.pause() }
                .onFailure { Log.w(TAG, "adView.pause() failed for adUnitId=${adView.adUnitId}", it) }
        }

        override fun onEnterForeground() {
            // Symmetric with the pause above. Idempotent, so an app that already calls this from
            // its own Activity lifecycle is unaffected.
            runCatching { adView.resume() }
                .onFailure { Log.w(TAG, "adView.resume() failed for adUnitId=${adView.adUnitId}", it) }
            // Decide ownership BEFORE unblocking can schedule an overdue periodic request.
            if (AudienzzPrebidMobile.hasPendingForegroundReimpression) return
            refreshController.unblock(RefreshBlockReason.APP_BACKGROUND, schedule = false)
            resumeEligibleWork()

        }
    }

    /**
     * Force a fresh auction on screen activation (v2). Unlike [resumeSmartRefresh] (stale-aware),
     * this always refetches when the ad has loaded before — the "new pageImpression → reload"
     * semantics on screen change.
     */
    internal fun reloadForScreenChange() {
        if (storedCallback == null || lastRefreshTime == 0L) return
        // This transition owns the replacement, so any pending periodic refresh or retry is retired
        // rather than allowed to issue a second one for the same transition.
        refreshController.invalidatePending()
        fetchDemand(RefreshRequestReason.PAGE_IMPRESSION)
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
        auctionGeneration++
        retireCurrentAuction()
        fetchDemand(RefreshRequestReason.PAGE_IMPRESSION)
    }

    private var blankedForReload = false

    /**
     * Hide the current creative while its replacement is on the way, keeping the slot's size.
     *
     * Hides the ad view's **children** rather than the ad view itself. [AdManagerAdView] is a
     * ViewGroup whose children are the rendered creative, and the visibility gate
     * ([org.audienzz.mobile.util.isRefreshEligible]) rejects anything whose own `visibility` is not
     * `VISIBLE` — so blanking the ad view made the slot ineligible for the very auction meant to
     * refill it. That is why blanking used to be deferred until an auction was already starting,
     * and it is what made an earlier blank unsafe. Hiding the children leaves the gate's view of
     * the world untouched.
     *
     * `INVISIBLE` rather than transparency on purpose: a fully transparent creative would still be
     * laid out and still take touches, so a tap on an apparently empty slot would click the ad that
     * is on its way out.
     */
    private fun blankForReloadIfNeeded() {
        if (!AudienzzPrebidMobile.blankOnScreenReload || blankedForReload) return
        // Do not take ownership of visibility the publisher already set to hidden.
        if (adView.visibility != View.VISIBLE) return
        if (adView.childCount == 0) return
        for (i in 0 until adView.childCount) {
            adView.getChildAt(i).visibility = View.INVISIBLE
        }
        blankedForReload = true
        AudienzzDiagnostics.log(
            "slot", "blank",
            "adUnit" to adView.adUnitId,
            "children" to adView.childCount,
        )
    }

    /** Reveal a creative hidden by [blankForReloadIfNeeded]. No-op unless this slot blanked itself. */
    private fun restoreFromBlankIfNeeded() {
        if (!blankedForReload) return
        blankedForReload = false
        // Re-read the children: a freshly rendered creative may be a different child than the one
        // that was hidden.
        for (i in 0 until adView.childCount) {
            adView.getChildAt(i).visibility = View.VISIBLE
        }
        AudienzzDiagnostics.log(
            "slot", "reveal",
            "adUnit" to adView.adUnitId,
            "children" to adView.childCount,
        )
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
        // The configured cadence lives in the controller. Prebid is never given an interval, so it
        // schedules nothing on either its success or its failure path.
        adUnit.refreshIntervalObserver = { millis ->
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                refreshController.setIntervalMillis(millis)
            } else {
                refreshHandler.post {
                    if (!refreshController.isDestroyed) refreshController.setIntervalMillis(millis)
                }
            }
        }
        refreshController.setIntervalMillis(adUnit.audienzzRefreshIntervalMillis)
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
            fetchDemand(RefreshRequestReason.FIRST_LOAD)
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
        if (initialTriggerArmed || initialRequestGeneration == auctionGeneration) {
            Log.d(TAG, "rearmInitialLoad() adUnitId=${adView.adUnitId} — trigger already armed or request in flight")
            return
        }
        val lazy = lazyLoadConfig
        if (lazy == null) {
            fetchDemand(RefreshRequestReason.FIRST_LOAD)
            return
        }
        val (withLazyLoading, prefetchMarginDp) = lazy
        if (!withLazyLoading) {
            fetchDemand(RefreshRequestReason.FIRST_LOAD)
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

    /**
     * Generation of the in-flight first request, or null when none is live.
     *
     * A plain "already requested" boolean could never be cleared, so once a first auction was superseded
     * (same-page re-report, background) the banner refused to re-arm and stayed blank forever.
     * Comparing against [auctionGeneration] distinguishes a live request from an invalidated one.
     */
    private var initialRequestGeneration: Int? = null

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
        fetchDemand(RefreshRequestReason.FIRST_LOAD)
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
        builder.applyPublisherProvidedId(AudienzzPrebidMobile.ppidManager?.getPpid())
        AudienzzTargetingParams.CUSTOM_TARGETING_MANAGER.applyToGamRequestBuilder(builder)
        return requestContext.buildRequest(builder)
    }

    /**
     * Sets the PPID on the retained request builder, and — importantly — clears it when there is
     * none.
     *
     * The builder is deliberately reused across auctions so the publisher's own targeting survives,
     * which also means a PPID set on an earlier auction stays on it until something overwrites it.
     * Applying the PPID only when non-null therefore left the previous identifier on every
     * subsequent request after consent was withdrawn or the backend turned PPID off — exactly the
     * cases where it must stop being sent.
     *
     * GMA annotates the setter's parameter non-null, so clearing goes through the public method
     * reflectively. The method name is stable public API (not obfuscated) and the implementation is
     * a plain field assignment; if a future version rejects null we log and leave the builder as it
     * was rather than crash a publisher's ad load.
     */
    private fun AdManagerAdRequest.Builder.applyPublisherProvidedId(ppid: String?) {
        if (ppid != null) {
            setPublisherProvidedId(ppid)
            return
        }
        try {
            AdManagerAdRequest.Builder::class.java
                .getMethod("setPublisherProvidedId", String::class.java)
                .invoke(this, null)
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "Could not clear the PPID on the request builder: ${e.message}")
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not clear the PPID on the request builder: ${e.message}")
        }
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
        Log.d(
            TAG,
            "enableSmartRefresh() adUnitId=${adView.adUnitId} — viewport tracking enabled (v2=$useV2), " +
                "interval=${refreshController.intervalMillis}ms",
        )
        // The listener now only reports visibility. It used to compute the remaining interval and
        // post its own delayed fetch, which is one of the two schedulers that could each issue a
        // request for the same moment; the controller owns that decision and the stale-aware
        // resume behaviour is unchanged, because it measures the interval the same way.
        smartRefreshListener = adView.addContinuousVisibilityListener(
            useDirectionalGate = useV2,
            onBecameVisible = {
                resumeSmartRefresh()
            },
            onBecameHidden = {
                refreshController.block(RefreshBlockReason.NOT_VISIBLE)
            },
        )

        // The hidden edge is only delivered on a visible -> hidden transition. A banner prefetched
        // while off screen never produced that edge, so its refresh would have run at 0%
        // viewability. Take an initial level reading instead of waiting for an edge.
        val eligibleAtEnable = if (useV2) adView.isRefreshEligible() else adView.isVisibleForSmartRefresh()
        if (!eligibleAtEnable) {
            Log.d(TAG, "enableSmartRefresh() adUnitId=${adView.adUnitId} — not visible at enable time, blocking refresh until it enters the viewport")
            refreshController.block(RefreshBlockReason.NOT_VISIBLE)
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
    /**
     * Publisher pause. Durable and independent: a viewport resume, a page impression or a return to
     * the foreground will not undo it — only [resumeAutoRefresh] will.
     *
     * This replaces [org.audienzz.mobile.AudienzzAdUnit.stopAutoRefresh], which acted on Prebid's
     * `BidLoader` — an object `fetchDemand` replaces, so the call routinely stopped a loader that
     * had already been retired while its replacement kept auctioning.
     */
    fun stopAutoRefresh() {
        Log.d(TAG, "stopAutoRefresh() adUnitId=${adView.adUnitId} — publisher pause")
        refreshController.block(RefreshBlockReason.PUBLISHER)
    }

    /**
     * Clears the publisher pause. Refresh only actually resumes once nothing else is holding it —
     * the banner is on the active page, visible, attached, and the app is in the foreground.
     */
    fun resumeAutoRefresh() {
        Log.d(TAG, "resumeAutoRefresh() adUnitId=${adView.adUnitId} — publisher resume")
        refreshController.unblock(RefreshBlockReason.PUBLISHER, schedule = false)
        resumeEligibleWork()
    }

    fun pauseSmartRefresh() {
        Log.d(TAG, "pauseSmartRefresh() adUnitId=${adView.adUnitId} — viewport pause")
        refreshController.block(RefreshBlockReason.NOT_VISIBLE)
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
        if (storedCallback == null) {
            Log.w(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — not loaded yet (no callback), skipping")
            return
        }
        refreshController.unblock(RefreshBlockReason.NOT_VISIBLE, schedule = false)
        resumeEligibleWork()
    }

    /**
     * A cover reported by the host, which native geometry cannot see: a pointer-transparent veil,
     * a painted overlay, a platform-view occlusion only the framework knows about.
     *
     * Deliberately its own block. A viewport resume must never clear a cover, and clearing a cover
     * must never clear a viewport hold — on a RemoteBanner both writers are live at once, so
     * sharing one reason let each undo the other.
     */
    fun pauseForHostCover() {
        Log.d(TAG, "pauseForHostCover() adUnitId=${adView.adUnitId} — host reported a cover")
        refreshController.block(RefreshBlockReason.HOST_REPORTED_HIDDEN)
    }

    /** Clears only the host cover. A geometry hold, a publisher stop and a page release survive. */
    fun resumeFromHostCover() {
        if (storedCallback == null) {
            Log.w(TAG, "resumeFromHostCover() adUnitId=${adView.adUnitId} — not loaded yet, skipping")
            return
        }
        Log.d(TAG, "resumeFromHostCover() adUnitId=${adView.adUnitId} — host cleared the cover")
        refreshController.unblock(RefreshBlockReason.HOST_REPORTED_HIDDEN, schedule = false)
        resumeEligibleWork()
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
        screenAdCoordinator?.deregister(this)
        refreshController.destroy()
        adUnit.refreshIntervalObserver = null
        pendingLoadReason = null
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
        cancelGoogleLoadTimeout()
        googleLoad = null
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
    /**
     * The single owner of periodic refresh for this banner. Prebid is never given an interval, so
     * nothing else schedules a request.
     */
    internal val refreshController = AudienzzRefreshController { reason, generation ->
        onRefreshDue(reason, generation)
    }

    /** Issues the request the controller asked for, re-checking that it is still wanted. */
    private fun onRefreshDue(reason: RefreshRequestReason, generation: Int) {
        if (generation != refreshController.generation) return
        if (storedCallback == null) return
        fetchDemand(reason)
    }

    private fun canStartAuction(reason: RefreshRequestReason): Boolean {
        // The first two checks are belt-and-braces: APP_BACKGROUND and PAGE_INACTIVE block reasons
        // already cover them below, and the first-load exemption is deliberately narrow enough not
        // to let either through. Mutating these two away leaves the suite green for exactly that
        // reason — the block reasons are what carry the guarantee. They are kept because they are
        // cheap and because a future exemption widened by accident would otherwise go unnoticed
        // here rather than at the one place the decision is made.
        if (refreshController.isDestroyed || !screenActive || !AppForegroundMonitor.isForeground) return false
        if (AudienzzPrebidMobile.hasPendingForegroundReimpression) return false
        // A first load may prefetch before attachment / refresh visibility. Publisher, page and
        // foreground blocks still apply. Later requests must satisfy all eligibility conditions.
        return refreshController.blockReasons.none {
            reason != RefreshRequestReason.FIRST_LOAD ||
                (
                    it != RefreshBlockReason.DETACHED &&
                        it != RefreshBlockReason.NOT_VISIBLE &&
                        // Two ways of saying the same thing; splitting them must not quietly
                        // change when a bridge banner takes its first load.
                        it != RefreshBlockReason.HOST_REPORTED_HIDDEN
                    )
        }
    }

    private fun resumeEligibleWork() {
        if (refreshController.isDestroyed || !screenActive) return
        val pending = pendingLoadReason
        if (pending != null && pending != RefreshRequestReason.FIRST_LOAD) {
            fetchDemand(pending)
        } else if (lastRefreshTime == 0L) {
            rearmInitialLoad()
        } else {
            refreshController.scheduleNext()
        }
    }

    /** Returns false for a Google result from a page that has already been superseded. */
    private fun completeGoogleLoad(retryableFailure: Boolean): Boolean {
        val load = googleLoad ?: return acceptsGoogleEvents
        cancelGoogleLoadTimeout()
        googleLoad = null
        val current = load.auction == auctionGeneration && screenActive && !refreshController.isDestroyed
        if (current) {
            lastRefreshTime = System.currentTimeMillis()
            refreshController.onRequestCompleted(load.refresh, success = !retryableFailure)
        } else {
            // No replacement may use this GAM view until its previous load has terminated.
            resumeEligibleWork()
        }
        return current
    }

    private fun cancelGoogleLoadTimeout() {
        googleLoadTimeout?.let { refreshHandler.removeCallbacks(it) }
        googleLoadTimeout = null
    }

    private fun watchGoogleLoad(load: GoogleLoad) {
        cancelGoogleLoadTimeout()
        val timeout = Runnable {
            if (googleLoad !== load || refreshController.isDestroyed) return@Runnable
            googleLoad = null
            googleLoadTimeout = null
            Log.w(TAG, "Google banner load timed out after 120s; releasing the wait for ${adView.adUnitId}")
            // A missing callback is not a proven transport failure. Resume the configured cadence,
            // never the fast network-error retry loop. Explicit pending page work may run now.
            if (load.auction == auctionGeneration) {
                lastRefreshTime = System.currentTimeMillis()
                refreshController.onRequestCompleted(load.refresh, success = true)
                restoreFromBlankIfNeeded()
            } else {
                resumeEligibleWork()
            }
        }
        googleLoadTimeout = timeout
        refreshHandler.postDelayed(timeout, 120_000L)
    }

    private fun fetchDemand(reason: RefreshRequestReason = RefreshRequestReason.PERIODIC_REFRESH): Boolean {
        val callback = storedCallback ?: run {
            Log.w(TAG, "fetchDemand() adUnitId=${adView.adUnitId} — no stored callback, skipping")
            return false
        }
        if (!canStartAuction(reason) || googleLoad != null) {
            if (reason == RefreshRequestReason.FIRST_LOAD || reason == RefreshRequestReason.PAGE_IMPRESSION) {
                pendingLoadReason = reason
            }
            // No replacement is starting, so showing the previous creative beats an empty slot that
            // nothing will ever fill.
            restoreFromBlankIfNeeded()
            return false
        }
        if (refreshController.hasRequestInFlight) {
            restoreFromBlankIfNeeded()
            return false
        }
        // Usually already blank from releaseForPage(); this covers a page re-reported without an
        // intervening release.
        if (reason == RefreshRequestReason.PAGE_IMPRESSION) blankForReloadIfNeeded()
        pendingLoadReason = null
        // An auction is actually starting, so nothing is owed any more.
        // Every new auction supersedes the previous one.
        auctionGeneration++
        initialRequestGeneration = auctionGeneration
        val refreshGeneration = refreshController.onRequestStarted(reason)
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
            slotReload = emittedSlotReload,
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
        var responseDelivered = false
        adUnit.fetchDemand(request) { resultCode ->
            if (responseDelivered) return@fetchDemand
            responseDelivered = true
            // Stale-response guard. Prebid re-arms its refresh timer from both the success and the
            // failure handler, so a response that lands after a page release would restart the loop
            // and load a creative into a slot the user has left. Re-cancel the timer Prebid just
            // armed and drop the response.
            if (generationAtRequest != auctionGeneration || !screenActive || refreshController.isDestroyed) {
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
            // New auction → reset render-winner state until the GAM render / app event report back.
            prebidLineItemWon = false
            prebidWinningBidder = null
            lastWinningBid = null

            // Prebid reports SUCCESS even for an empty/error response (e.g. STORED_REQUEST_NOT_FOUND).
            // A real Prebid win always carries hb_bidder, so gate the win on it; otherwise it's a no-bid.
            val winningBidder = request.prebidKeyword(HB_BIDDER_KEY)
            val timeToRespond =
                System.currentTimeMillis() - requestStartMs
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
                    slotReload = emittedSlotReload,
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
                isRefresh = isRefresh,
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
                    isRefresh = isRefresh,
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
                    isRefresh = isRefresh,
                    // Prebid returns SUCCESS with empty targeting on a no-bid; report NO_BIDS so the
                    // funnel doesn't show a "successful" no-bid. Real failures keep their result code.
                    resultCode = noBidResultCode(resultCode),
                    slotReload = emittedSlotReload,
                    adUnitCode = adUnit.configId,
                    mediaTypes = mediaTypesJson(adUnit.adFormats.adSubtype),
                )
            }
            slotReloadCount++
            val load = GoogleLoad(generationAtRequest, refreshGeneration)
            googleLoad = load
            googleEventPageGeneration = creativePageGeneration
            renderAuctionId = currentAuctionId
            watchGoogleLoad(load)
            setEventsListenerToAdView()
            callback.invoke(request, resultCode)
        }
        return true
    }

    private fun setEventsListenerToAdView() {
        // Install or repair observation without wrapping our own listener. Repeated wrapping
        // multiplies publisher callbacks and analytics; listener replacement must still recover.
        if (installedAdListener != null && adView.adListener === installedAdListener && adView.appEventListener === installedAppEventListener) return
        // A publisher can replace either listener after setup. Capture that replacement rather
        // than nesting our previous wrapper; the next load repairs observation after a timeout.
        if (adView.adListener !== installedAdListener) publisherAdListener = adView.adListener
        if (adView.appEventListener !== installedAppEventListener) publisherAppEventListener = adView.appEventListener

        // GAM fires an app event when the Prebid line item wins the ad-server auction; absence of
        // it by impression time means a non-Prebid (Google/ad-server) creative rendered. Chain any
        // listener the publisher already set.
        val actualAppEventListener = publisherAppEventListener
        installedAppEventListener = AppEventListener { name, info ->
            if (!acceptsGoogleEvents) return@AppEventListener
            actualAppEventListener?.onAppEvent(name, info)
            if (name.equals(PREBID_APP_EVENT, ignoreCase = true)) {
                Log.d(TAG, "onAppEvent($name) — Prebid line item won for ${adView.adUnitId}")
                prebidLineItemWon = true
                // The app event can arrive either side of onAdLoaded. When it lands after, correct
                // the displayed snapshot in place — this is the creative on screen, so the
                // attribution belongs to it and not to whatever auction is running by then.
                if (displayedEconomics != null) {
                    displayedPrebidLineItemWon = true
                    if (displayedPrebidBidder == null) displayedPrebidBidder = prebidWinningBidder
                }
            }
        }

        adView.appEventListener = installedAppEventListener
        val actualListener: AdListener? = publisherAdListener
        installedAdListener = object : AdListener() {

            override fun onAdClicked() {
                if (!acceptsGoogleEvents) return
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
                if (!completeGoogleLoad(retryableFailure = false)) return
                // This is the moment the replacement becomes what the reader sees, so it is the
                // moment its economics become the ones render events describe.
                commitDisplayedCreative()
                restoreFromBlankIfNeeded()
                actualListener?.onAdLoaded()
            }

            override fun onAdOpened() {
                if (!acceptsGoogleEvents) return
                actualListener?.onAdOpened()
            }

            override fun onAdClosed() {
                if (!acceptsGoogleEvents) return
                actualListener?.onAdClosed()
            }

            override fun onAdImpression() {
                if (!acceptsGoogleEvents) return
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
                val retryable = error.code == com.google.android.gms.ads.AdRequest.ERROR_CODE_NETWORK_ERROR ||
                    error.code == com.google.android.gms.ads.AdRequest.ERROR_CODE_INTERNAL_ERROR
                if (!completeGoogleLoad(retryableFailure = retryable)) return
                restoreFromBlankIfNeeded()
                actualListener?.onAdFailedToLoad(error)
            }

            override fun onAdSwipeGestureClicked() {
                if (!acceptsGoogleEvents) return
                actualListener?.onAdSwipeGestureClicked()
            }
        }
        adView.adListener = requireNotNull(installedAdListener)
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
     * `slot_reload` as the collector defines it: `0` for a slot's first load, `1` for every load
     * after it. Serialized as a string, like the other `attributes` values.
     */
    private val emittedSlotReload: Int get() = if (slotReloadCount > 0) 1 else 0

    /**
     * Promote the pending auction's economics to "what is on screen".
     *
     * Called when Google confirms the creative was received, which is the moment the replacement
     * actually becomes the thing the reader sees. Until then the previous creative keeps its own
     * identity, so a late impression or viewability callback for it is reported under its own
     * auction — and a replacement that never arrives changes nothing at all.
     */
    private fun commitDisplayedCreative() {
        val base = lastRenderEconomics ?: RenderEconomics()
        displayedEconomics = base.copy(
            auctionId = base.auctionId ?: currentAuctionId,
            // The reported flag is binary and belongs to the creative, not the slot's current count.
            slotReload = base.slotReload ?: emittedSlotReload,
        )
        displayedPrebidBidder = prebidWinningBidder
        displayedPrebidLineItemWon = prebidLineItemWon
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
        if (displayedPrebidLineItemWon) {
            displayedPrebidBidder ?: PREBID_BIDDER
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
        // The DISPLAYED creative's snapshot, not the newest auction's. See [displayedEconomics].
        val base = displayedEconomics ?: RenderEconomics()
        val bidder = resolveBidderCode()
        return base.copy(
            bidderCode = bidder,
            // Ad server rendered — the Prebid bid's creative id would make the enricher misclassify a
            // direct-sold impression as RTB. Report the GAM creative id when available, else the "0"
            // stub (GMA exposes no served-creative id → "0").
            creativeId = if (bidder == AD_SERVER_BIDDER) "0" else base.creativeId,
            // Always carry the SDK-minted auction id, even on a direct fill with no Prebid economics.
            auctionId = base.auctionId ?: renderAuctionId,
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
