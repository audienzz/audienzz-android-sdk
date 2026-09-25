package org.audienzz.mobile

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.IntRange
import org.audienzz.mobile.api.data.AudienzzBidInfo
import org.prebid.mobile.AdUnit
import org.prebid.mobile.AudienzzBidResponseAccessor
import org.prebid.mobile.OnCompleteListener
import org.prebid.mobile.PrebidMobile.AUTO_REFRESH_DELAY_MAX
import org.prebid.mobile.PrebidMobile.AUTO_REFRESH_DELAY_MIN
import org.prebid.mobile.api.original.OnFetchDemandResult

/**
 * Economics of the Prebid auction winner, read from the retained `BidResponse` after a successful
 * `fetchDemand`. All nullable — populated only when there was a winning bid.
 */
internal data class AudienzzWinningBid(
    val cpm: Double?,
    val currency: String?,
    val creativeId: String?,
    val auctionId: String?,
    val adId: String?,
)

abstract class AudienzzAdUnit internal constructor(
    private val adUnit: AdUnit,
) {
    private val demandHandler = Handler(Looper.getMainLooper())
    private var cancelPendingDemand: (() -> Unit)? = null
    internal var demandTimeoutMillis: Long? = null

    /**
     * Returns the Prebid winning-bid economics (cpm, currency, creative id, auction id, ad id) from
     * the `BidResponse` Prebid retains on the original-API `AdUnit`, or null if there is none.
     * Call after a `fetchDemand` SUCCESS.
     */
    internal fun getWinningBid(): AudienzzWinningBid? {
        val response = AudienzzBidResponseAccessor.getBidResponse(adUnit) ?: return null
        // No actual winning bid (e.g. an empty/error response Prebid still reports as SUCCESS).
        val bid = response.winningBid ?: return null
        return AudienzzWinningBid(
            cpm = bid.price,
            currency = response.cur,
            creativeId = bid.crid,
            auctionId = response.id,
            adId = bid.id,
        )
    }
    var pbAdSlot: String?
        get() = adUnit.pbAdSlot
        set(value) {
            adUnit.pbAdSlot = value
        }

    var gpid: String?
        get() = adUnit.gpid
        set(value) {
            adUnit.gpid = value
        }

    var impOrtbConfig: String?
        get() = adUnit.impOrtbConfig
        set(value) {
            adUnit.impOrtbConfig = value
        }

    /**
     * Configured refresh interval in milliseconds; 0 disables refresh.
     *
     * Owned by Audienzz and deliberately never handed to Prebid. Prebid's `autoRefreshDelay` stays
     * 0, which is what makes `BidLoader.setupRefreshTimer()` return without scheduling anything on
     * either its success or its failure path — the SDK schedules every refresh itself instead, so
     * only one component owns the timer. A bid response cannot re-enable it either:
     * `MobileSdkPassThrough.modifyAdUnitConfiguration` can set mute, video duration, skip delay and
     * the close/skip button settings, but never the refresh delay.
     */
    @Volatile
    internal var audienzzRefreshIntervalMillis: Long = 0
        private set

    internal var refreshIntervalObserver: ((Long) -> Unit)? = null

    /** Kept for analytics reporting, which records the configured cadence. */
    internal val autoRefreshTime get() = audienzzRefreshIntervalMillis.toInt()

    internal val adFormats get() = adUnit.configuration.adFormats

    /** Prebid stored-request/config id — reported as analytics `ad_unit_code`. */
    internal val configId: String? get() = adUnit.configuration.configId

    fun setAutoRefreshInterval(
        @IntRange(
            from = AUTO_REFRESH_DELAY_MIN / 1000L,
            to = AUTO_REFRESH_DELAY_MAX / 1000L,
        ) seconds: Int,
    ) {
        // Stored here rather than forwarded to Prebid — see [audienzzRefreshIntervalMillis].
        // Clamped the same way Prebid clamps it, so the accepted range and the disabled behaviour
        // (0 = no refresh) are unchanged for publishers.
        audienzzRefreshIntervalMillis = when {
            seconds <= 0 -> 0
            else -> (seconds * 1000L)
                .coerceIn(AUTO_REFRESH_DELAY_MIN.toLong(), AUTO_REFRESH_DELAY_MAX.toLong())
        }
        refreshIntervalObserver?.invoke(audienzzRefreshIntervalMillis)
    }

    /**
     * Historic no-ops, kept so existing callers compile.
     *
     * Prebid's timer is never armed, so there is nothing here to stop or resume. Pausing and
     * resuming is expressed through the refresh controller's block reasons instead, which survive a
     * response and can be cleared independently.
     */
    @Deprecated(
        "Refresh is owned by the SDK. Use AudienzzAdViewHandler.resumeAutoRefresh(), which clears the " +
            "publisher pause without disturbing the other reasons refresh may be held for.",
    )
    fun resumeAutoRefresh() = Unit

    @Deprecated(
        "Refresh is owned by the SDK. Use AudienzzAdViewHandler.stopAutoRefresh(), which records a " +
            "durable publisher pause instead of acting on a Prebid BidLoader that fetchDemand replaces.",
    )
    fun stopAutoRefresh() = Unit

    fun destroy() {
        cancelPendingDemand?.invoke()
        cancelPendingDemand = null
        adUnit.destroy()
    }

    fun fetchDemand(
        adObj: Any,
        listener: (AudienzzResultCode?) -> Unit,
    ) {
        if (cancelPendingDemand != null) destroy()
        if (AudienzzPrebidMobile.prebidUnavailable) {
            listener(AudienzzResultCode.INVALID_CONTEXT)
            return
        }
        // A PBS outage must end at Google even if Prebid throws or never calls back.
        // Destroy the timed-out loader BEFORE handing off, so a late bid cannot mutate
        // Google's request or cancel a replacement started from the publisher callback.
        var settled = false
        lateinit var timeout: Runnable
        fun complete(result: AudienzzResultCode?, retire: Boolean = false) {
            if (settled) return
            settled = true
            demandHandler.removeCallbacks(timeout)
            cancelPendingDemand = null
            if (retire) adUnit.destroy()
            listener(result)
        }
        timeout = Runnable {
            Log.w("AudienzzDemand", "Prebid response deadline reached; continuing with Google demand")
            complete(AudienzzResultCode.TIMEOUT, retire = true)
        }
        cancelPendingDemand = {
            settled = true
            demandHandler.removeCallbacks(timeout)
        }
        demandHandler.postDelayed(timeout,
            demandTimeoutMillis ?: (AudienzzPrebidMobile.timeoutMillis.toLong().coerceAtLeast(1) + 250))
        try {
            adUnit.fetchDemand(adObj, OnCompleteListener { resultCode ->
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    complete(AudienzzResultCode.getResultCode(resultCode))
                } else {
                    demandHandler.post { complete(AudienzzResultCode.getResultCode(resultCode)) }
                }
            })
        } catch (error: RuntimeException) {
            // Do not swallow an exception raised by the publisher's synchronous callback.
            if (settled) throw error
            Log.w("AudienzzDemand", "Prebid request failed; continuing with Google demand", error)
            complete(AudienzzResultCode.SERVER_ERROR, retire = true)
        }
    }

    fun fetchDemand(listener: (AudienzzBidInfo) -> Unit) {
        val onFetchDemandResult =
            OnFetchDemandResult { bidInfo -> listener(AudienzzBidInfo(bidInfo)) }
        adUnit.fetchDemand(onFetchDemandResult)
    }

}
