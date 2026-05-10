package org.audienzz.mobile.original

import android.util.Log
import android.view.ViewTreeObserver
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.audienzz.mobile.AudienzzAdUnit
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.event.adClick
import org.audienzz.mobile.event.adImpression
import org.audienzz.mobile.event.bidRequest
import org.audienzz.mobile.event.bidResponse
import org.audienzz.mobile.event.bidWon
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.event.headerLoaded
import org.audienzz.mobile.event.noBid
import org.audienzz.mobile.event.util.adSubtype
import org.audienzz.mobile.util.addContinuousVisibilityListener
import org.audienzz.mobile.util.adViewId
import org.audienzz.mobile.util.addOnBecameVisibleOnScreenListener
import org.audienzz.mobile.util.addPrefetchMarginListener
import org.audienzz.mobile.util.sizeString

class AudienzzAdViewHandler(
    private val adView: AdManagerAdView,
    private val adUnit: AudienzzAdUnit,
) {
    companion object {
        private const val TAG = "AudienzzAdViewHandler"
    }

    private var isFirstDemandFetch = true

    // Smart refresh state
    private var smartRefreshListener: ViewTreeObserver.OnPreDrawListener? = null
    private var lastRefreshTime: Long = 0
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingRefreshRunnable: Runnable? = null
    private var storedRequest: AdManagerAdRequest? = null
    private var storedCallback: ((AdManagerAdRequest, AudienzzResultCode?) -> Unit)? = null

    init {
        eventLogger?.headerLoaded(
            adViewId = adView.adViewId,
            adUnitId = adView.adUnitId,
            sizes = adView.adSizes?.asIterable()?.sizeString,
            adType = AdType.BANNER,
            adSubtype = adUnit.adFormats.adSubtype,
            apiType = ApiType.ORIGINAL,
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
        val ppid = AudienzzPrebidMobile.ppidManager?.getPpid()
        if (ppid != null) {
            gamRequestBuilder.setPublisherProvidedId(ppid)
        }

        val request =
            AudienzzTargetingParams.CUSTOM_TARGETING_MANAGER
                .applyToGamRequestBuilder(gamRequestBuilder)
                .build()

        storedRequest = request
        storedCallback = callback

        if (withLazyLoading) {
            if (prefetchMarginDp > 0) {
                Log.d(TAG, "load() adUnitId=${adView.adUnitId} — lazy ON, prefetchMargin=${prefetchMarginDp}dp, waiting for view to enter range")
                adView.addPrefetchMarginListener(marginDp = prefetchMarginDp) {
                    Log.d(TAG, "load() adUnitId=${adView.adUnitId} — prefetch margin reached (${prefetchMarginDp}dp), starting fetchDemand")
                    fetchDemand(request, callback)
                }
            } else {
                Log.d(TAG, "load() adUnitId=${adView.adUnitId} — lazy ON, prefetchMargin=0 (exact visibility), waiting for view to appear")
                adView.addOnBecameVisibleOnScreenListener {
                    Log.d(TAG, "load() adUnitId=${adView.adUnitId} — view became visible, starting fetchDemand")
                    fetchDemand(request, callback)
                }
            }
        } else {
            Log.d(TAG, "load() adUnitId=${adView.adUnitId} — lazy OFF, starting fetchDemand immediately")
            fetchDemand(request, callback)
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
        Log.d(TAG, "enableSmartRefresh() adUnitId=${adView.adUnitId} — smart refresh enabled, refreshInterval=${adUnit.autoRefreshTime}ms")
        smartRefreshListener = adView.addContinuousVisibilityListener(
            onBecameVisible = {
                val request = storedRequest ?: run {
                    Log.w(TAG, "smartRefresh adUnitId=${adView.adUnitId} — became visible but storedRequest is null, skipping")
                    return@addContinuousVisibilityListener
                }
                val callback = storedCallback ?: run {
                    Log.w(TAG, "smartRefresh adUnitId=${adView.adUnitId} — became visible but storedCallback is null, skipping")
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
                    fetchDemand(request, callback)
                    adUnit.resumeAutoRefresh()
                } else {
                    Log.d(TAG, "smartRefresh adUnitId=${adView.adUnitId} — became visible, ad is fresh (elapsed=${elapsed}ms, remaining=${remaining}ms), scheduling refresh in ${remaining}ms")
                    val runnable = Runnable {
                        Log.d(TAG, "smartRefresh adUnitId=${adView.adUnitId} — scheduled refresh fired after ${remaining}ms delay")
                        fetchDemand(request, callback)
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
        val request = storedRequest ?: run {
            Log.w(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — storedRequest is null, skipping")
            return
        }
        val callback = storedCallback ?: run {
            Log.w(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — storedCallback is null, skipping")
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
            fetchDemand(request, callback)
            adUnit.resumeAutoRefresh()
        } else {
            Log.d(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — ad is fresh (elapsed=${elapsed}ms, remaining=${remaining}ms), scheduling refresh in ${remaining}ms")
            val runnable = Runnable {
                Log.d(TAG, "resumeSmartRefresh() adUnitId=${adView.adUnitId} — scheduled refresh fired after ${remaining}ms delay")
                fetchDemand(request, callback)
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
    }

    private fun fetchDemand(
        request: AdManagerAdRequest,
        callback: (AdManagerAdRequest, AudienzzResultCode?) -> Unit,
    ) {
        val isAutorefresh = adUnit.autoRefreshTime > 0
        val autorefreshTime = adUnit.autoRefreshTime.toLong()
        val isRefresh = !isFirstDemandFetch
        isFirstDemandFetch = false
        Log.d(TAG, "fetchDemand() adUnitId=${adView.adUnitId} — isRefresh=$isRefresh, autorefresh=${autorefreshTime}ms")

        eventLogger?.bidRequest(
            adViewId = adView.adViewId,
            adUnitId = adView.adUnitId,
            sizes = adView.adSizes?.asIterable()?.sizeString,
            adType = AdType.BANNER,
            adSubtype = adUnit.adFormats.adSubtype,
            apiType = ApiType.ORIGINAL,
            autorefreshTime = autorefreshTime,
            isAutorefresh = isAutorefresh,
            isRefresh = isRefresh,
        )
        adUnit.fetchDemand(request) { resultCode ->
            lastRefreshTime = System.currentTimeMillis()
            setEventsListenerToAdView()
            callback.invoke(request, resultCode)
            eventLogger?.bidResponse(
                adViewId = adView.adViewId,
                adUnitId = adView.adUnitId,
                sizes = adView.adSizes?.asIterable()?.sizeString,
                adType = AdType.BANNER,
                adSubtype = adUnit.adFormats.adSubtype,
                apiType = ApiType.ORIGINAL,
                autorefreshTime = autorefreshTime,
                isAutorefresh = isAutorefresh,
                isRefresh = isRefresh,
                resultCode = resultCode?.toString(),
            )
            if (resultCode == AudienzzResultCode.SUCCESS) {
                eventLogger?.bidWon(
                    adViewId = adView.adViewId,
                    adUnitId = adView.adUnitId,
                    sizes = adView.adSizes?.asIterable()?.sizeString,
                    adType = AdType.BANNER,
                    adSubtype = adUnit.adFormats.adSubtype,
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = autorefreshTime,
                    isAutorefresh = isAutorefresh,
                    isRefresh = isRefresh,
                    targetKeywords = request.keywords.toList(),
                )
            } else {
                eventLogger?.noBid(
                    adViewId = adView.adViewId,
                    adUnitId = adView.adUnitId,
                    sizes = adView.adSizes?.asIterable()?.sizeString,
                    adType = AdType.BANNER,
                    adSubtype = adUnit.adFormats.adSubtype,
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = autorefreshTime,
                    isAutorefresh = isAutorefresh,
                    isRefresh = isRefresh,
                    resultCode = resultCode?.toString(),
                )
            }
        }
    }

    private fun setEventsListenerToAdView() {
        val actualListener: AdListener? = adView.adListener
        adView.adListener = object : AdListener() {

            override fun onAdClicked() {
                actualListener?.onAdClicked()
                eventLogger?.adClick(adUnitId = adView.adUnitId)
            }

            override fun onAdLoaded() {
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
                )
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                actualListener?.onAdFailedToLoad(error)
            }

            override fun onAdSwipeGestureClicked() {
                actualListener?.onAdSwipeGestureClicked()
            }
        }
    }
}
