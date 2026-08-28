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
    // call onScreenResumed, behave exactly as before; the coordinator flips it on screen changes.
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

    private fun resolveHostScreen(): Any? {
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

    /** True when this ad lives on [screen] (object identity, not class name). */
    internal fun isHostedBy(screen: Any): Boolean = resolveHostScreen() === screen

    /**
     * Screen-aware transition (v2). Active + already loaded → force a fresh auction; inactive →
     * pause (overrides viewport eligibility). A never-loaded active banner is left for its normal
     * lazy load.
     */
    internal fun onScreenActiveChanged(active: Boolean) {
        screenActive = active
        if (active) {
            if (lastRefreshTime != 0L) reloadForScreenChange()
        } else {
            pauseSmartRefresh()
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

        if (withLazyLoading) {
            if (prefetchMarginDp > 0) {
                Log.d(TAG, "load() adUnitId=${adView.adUnitId} — lazy ON, prefetchMargin=${prefetchMarginDp}dp, waiting for view to enter range")
                adView.addPrefetchMarginListener(marginDp = prefetchMarginDp) {
                    Log.d(TAG, "load() adUnitId=${adView.adUnitId} — prefetch margin reached (${prefetchMarginDp}dp), starting fetchDemand")
                    fetchDemand()
                }
            } else {
                Log.d(TAG, "load() adUnitId=${adView.adUnitId} — lazy ON, prefetchMargin=0 (exact visibility), waiting for view to appear")
                adView.addOnBecameVisibleOnScreenListener {
                    Log.d(TAG, "load() adUnitId=${adView.adUnitId} — view became visible, starting fetchDemand")
                    fetchDemand()
                }
            }
        } else {
            Log.d(TAG, "load() adUnitId=${adView.adUnitId} — lazy OFF, starting fetchDemand immediately")
            fetchDemand()
        }
    }

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

        // Screen-aware smart refresh (v2 only): register in the coordinator and set the initial
        // screen-active state so a banner built for a non-active screen starts paused. Under legacy,
        // registration is harmless and screenActive stays true (nothing pauses on screen change).
        screenAdCoordinator?.register(this)
        if (useV2) {
            val active = screenAdCoordinator?.activeScreen
            screenActive = active == null || isHostedBy(active)
            if (!screenActive) {
                pauseSmartRefresh()
            }
        }
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
        if (storedCallback == null) {
            Log.w(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — not loaded yet (no callback), skipping")
            return
        }

        pendingRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        pendingRefreshRunnable = null

        if (lastRefreshTime == 0L) {
            // First demand fetch hasn't completed yet — just restart Prebid's timer normally.
            Log.d(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — no prior fetch, resuming timer from scratch")
            adUnit.resumeAutoRefresh()
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

    private fun fetchDemand() {
        val callback = storedCallback ?: run {
            Log.w(TAG, "fetchDemand() adUnitId=${adView.adUnitId} — no stored callback, skipping")
            return
        }
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
        // C1: Prebid's fetchDemand spawns a NEW self-re-arming BidLoader on every call without
        // destroying the previous one. Stopping auto-refresh before each manual fetch guarantees
        // the prior loader is cancelled first, so at most one refresh loop is ever live — otherwise
        // each prefetch/scroll/resume fetch stacks another 30s auction loop that keeps auctioning
        // (and GAM-loading) the view until process death.
        adUnit.stopAutoRefresh()

        // Prebid re-invokes this listener on every auto-refresh without re-entering fetchDemand().
        // The first invocation pairs with the bidRequest above; each later one is a refresh auction
        // that emits its own bidRequest so the bidRequest/bidResponse funnel stays balanced.
        var isFirstAuction = true
        adUnit.fetchDemand(request) { resultCode ->
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
