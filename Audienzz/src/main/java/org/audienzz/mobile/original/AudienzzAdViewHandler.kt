package org.audienzz.mobile.original

import android.util.Log
import android.view.View
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
import org.audienzz.mobile.event.adCreation
import org.audienzz.mobile.event.adFailedToLoad
import org.audienzz.mobile.event.bidRequest
import org.audienzz.mobile.event.bidWinner
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.event.util.adSubtype
import org.audienzz.mobile.util.adViewId
import org.audienzz.mobile.util.addContinuousVisibilityListener
import org.audienzz.mobile.util.addOnBecameVisibleOnScreenListenerWithMargin
import org.audienzz.mobile.util.dpToPx
import org.audienzz.mobile.util.sizeString

class AudienzzAdViewHandler(
    private val adView: AdManagerAdView,
    private val adUnit: AudienzzAdUnit,
) {

    private var isFirstDemandFetch = true
    private var scrollListener: ViewTreeObserver.OnScrollChangedListener? = null

    init {
        eventLogger?.adCreation(
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
     * @param withLazyLoading allows to postpone fetchDemand call until view is visible.
     * @param prefetchMarginTopDp distance in dp below the screen bottom at which the bid request
     * fires before the view scrolls into view. 0 = disabled (fire exactly at viewport edge).
     */
    @JvmOverloads fun load(
        withLazyLoading: Boolean = true,
        prefetchMarginTopDp: Int = 0,
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

        if (withLazyLoading) {
            val marginPx = adView.resources.dpToPx(prefetchMarginTopDp)
            Log.d(TAG, "[Prefetch] Lazy load armed for ${adView.adUnitId} (prefetchMarginTopDp=$prefetchMarginTopDp, marginPx=$marginPx)")
            adView.addOnBecameVisibleOnScreenListenerWithMargin(marginPx) {
                Log.d(TAG, "[Prefetch] Triggered fetchDemand for ${adView.adUnitId}")
                fetchDemand(request, callback)
            }
        } else {
            fetchDemand(request, callback)
        }
    }

    /**
     * Enables viewport-aware smart refresh: pauses Prebid autorefresh and GAM ad playback
     * when the view scrolls off-screen, and resumes both when it returns to the viewport.
     *
     * Call this once after the [adView] has been added to the window. To stop tracking,
     * call [disableSmartRefresh].
     */
    fun enableSmartRefresh() {
        if (scrollListener != null) return
        if (adView.isAttachedToWindow) {
            attachScrollListener()
        }
        adView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = attachScrollListener()
            override fun onViewDetachedFromWindow(v: View) = detachScrollListener()
        })
    }

    /**
     * Stops viewport-aware smart refresh tracking started by [enableSmartRefresh].
     */
    fun disableSmartRefresh() {
        detachScrollListener()
    }

    private fun attachScrollListener() {
        if (scrollListener != null) return
        Log.d(TAG, "[SmartRefresh] Listener attached for ${adView.adUnitId}")
        scrollListener = adView.addContinuousVisibilityListener(
            onBecameVisible = {
                Log.d(TAG, "[SmartRefresh] → VISIBLE: resumeAutoRefresh + adView.resume() for ${adView.adUnitId}")
                adUnit.resumeAutoRefresh()
                adView.resume()
            },
            onBecameHidden = {
                Log.d(TAG, "[SmartRefresh] → HIDDEN: stopAutoRefresh + adView.pause() for ${adView.adUnitId}")
                adUnit.stopAutoRefresh()
                adView.pause()
            },
        )
    }

    private fun detachScrollListener() {
        scrollListener?.let {
            if (adView.isAttachedToWindow) {
                adView.viewTreeObserver.removeOnScrollChangedListener(it)
            }
            scrollListener = null
            Log.d(TAG, "[SmartRefresh] Listener detached for ${adView.adUnitId}")
        }
    }

    companion object {
        private const val TAG = "AudienzzAdViewHandler"
    }

    private fun fetchDemand(
        request: AdManagerAdRequest,
        callback: (AdManagerAdRequest, AudienzzResultCode?) -> Unit,
    ) {
        val isAutorefresh = adUnit.autoRefreshTime > 0
        val autorefreshTime = adUnit.autoRefreshTime.toLong()
        val isRefresh = !isFirstDemandFetch
        isFirstDemandFetch = false

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
            setEventsListenerToAdView()
            callback.invoke(request, resultCode)
            eventLogger?.bidWinner(
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
                targetKeywords = request.keywords.toList(),
            )
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
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                actualListener?.onAdFailedToLoad(error)
                eventLogger?.adFailedToLoad(
                    adUnitId = adView.adUnitId,
                    errorMessage = error.message,
                )
            }

            override fun onAdSwipeGestureClicked() {
                actualListener?.onAdSwipeGestureClicked()
            }
        }
    }
}
