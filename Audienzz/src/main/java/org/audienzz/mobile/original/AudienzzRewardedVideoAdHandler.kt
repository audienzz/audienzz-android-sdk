package org.audienzz.mobile.original

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.rewarded.RewardedAd
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.AudienzzRewardedVideoAdUnit
import org.audienzz.mobile.event.AnalyticsPageContext
import org.audienzz.mobile.event.RenderEconomics
import org.audienzz.mobile.event.adClick
import org.audienzz.mobile.event.adImpression
import org.audienzz.mobile.event.bidRequest
import org.audienzz.mobile.event.bidResponse
import org.audienzz.mobile.event.bidWon
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.event.noBid
import org.audienzz.mobile.event.viewabilityStart
import org.audienzz.mobile.event.viewabilitySuccess
import org.audienzz.mobile.original.callbacks.AudienzzFullScreenContentCallback
import org.audienzz.mobile.original.callbacks.AudienzzRewardedAdLoadCallback
import org.audienzz.mobile.targeting.AudienzzAdRequestContext
import org.audienzz.mobile.util.AD_SERVER_BIDDER
import org.audienzz.mobile.util.FullScreenViewabilityTimer
import org.audienzz.mobile.util.HB_BIDDER_KEY
import org.audienzz.mobile.util.HB_FORMAT_KEY
import org.audienzz.mobile.util.HB_PB_KEY
import org.audienzz.mobile.util.HB_SIZE_KEY
import org.audienzz.mobile.util.noBidResultCode
import org.audienzz.mobile.util.prebidKeyword
import java.util.UUID

class AudienzzRewardedVideoAdHandler(
    private val adUnit: AudienzzRewardedVideoAdUnit,
    private val adUnitId: String,
) {

    // Prebid auction winner (hb_bidder), captured on bid success and reported on adImpression.
    private var prebidWinningBidder: String? = null
    // Winning-bid economics from the last auction, reused on adImpression/adClick/viewability.
    private var lastRenderEconomics: RenderEconomics? = null
    // SDK-generated auction id, minted at auction start and reused across every event of that auction.
    private var currentAuctionId: String? = null


    /**
     * @param fullScreenContentCallback use for work with callbacks from Rewarded ad
     * @param resultCallback return result code, request and callback for Rewarded ad.
     * Then it is required to load GAM ad.
     */
    @JvmOverloads fun load(
        gamRequestBuilder: AdManagerAdRequest.Builder = AdManagerAdRequest.Builder(),
        adLoadCallback: AudienzzRewardedAdLoadCallback,
        fullScreenContentCallback: AudienzzFullScreenContentCallback? = null,
        resultCallback: (
        (
            AudienzzResultCode?,
            AdManagerAdRequest,
            AudienzzRewardedAdLoadCallback,
        ) -> Unit
        ),
    ) {
        prebidWinningBidder = null
        // Mint the auction id up front so bidRequest and every later event of this auction share it.
        currentAuctionId = UUID.randomUUID().toString()
        val requestAuctionId = currentAuctionId
        val requestPage = eventLogger?.capturePageContext() ?: AnalyticsPageContext()
        val requestStartMs = System.currentTimeMillis()
        val runPrebid = !AudienzzPrebidMobile.prebidUnavailable
        if (runPrebid) eventLogger?.bidRequest(
            pageContext = requestPage,
            adUnitId = adUnitId,
            adType = AdType.REWARDED,
            adSubtype = AdSubtype.VIDEO,
            apiType = ApiType.ORIGINAL,
            autorefreshTime = adUnit.autoRefreshTime.toLong(),
            isAutorefresh = adUnit.autoRefreshTime > 0,
            isRefresh = false,
            adUnitCode = adUnit.configId,
            mediaTypes = "[\"video\"]",
            auctionId = requestAuctionId,
        )
        val ppid = AudienzzPrebidMobile.ppidManager?.getPpid()
        if (ppid != null) {
            gamRequestBuilder.setPublisherProvidedId(ppid)
        }

        // Global targeting and the SDK's keys go onto the built request, never the publisher's
        // builder (see AudienzzAdRequestContext.buildPublisherRequest).
        val request = AudienzzAdRequestContext.buildPublisherRequest(gamRequestBuilder)
        if (!runPrebid) {
            // No Prebid attempt happened. Keep Google's render funnel, without a synthetic noBid.
            resultCallback(null, request, connectCallbacks(adLoadCallback, fullScreenContentCallback,
                RenderEconomics(bidderCode = AD_SERVER_BIDDER, auctionId = requestAuctionId,
                    slotReload = 0, pageContext = requestPage)))
            return
        }
        adUnit.fetchDemand(request) { resultCode ->
            val timeToRespond = System.currentTimeMillis() - requestStartMs
            // Prebid reports SUCCESS even for an empty/error response (e.g. STORED_REQUEST_NOT_FOUND).
            // A real Prebid win always carries hb_bidder, so gate the win on it; otherwise it's a no-bid.
            val winningBidder = request.prebidKeyword(HB_BIDDER_KEY)
            var economics: RenderEconomics? = null
            if (resultCode == AudienzzResultCode.SUCCESS && winningBidder != null) {
                prebidWinningBidder = winningBidder
                val win = adUnit.getWinningBid()
                economics = RenderEconomics(
                    bidderCode = winningBidder,
                    winnerBidderCode = winningBidder,
                    winnerType = WINNER_TYPE_RTB,
                    priceBucket = request.prebidKeyword(HB_PB_KEY),
                    hbSize = request.prebidKeyword(HB_SIZE_KEY),
                    hbFormat = request.prebidKeyword(HB_FORMAT_KEY),
                    mediaType = request.prebidKeyword(HB_FORMAT_KEY) ?: "video",
                    size = request.prebidKeyword(HB_SIZE_KEY),
                    cpm = win?.cpm,
                    currency = win?.currency,
                    creativeId = win?.creativeId,
                    // Reuse the SDK-minted auction id (not Prebid's) so the whole funnel counts together.
                    auctionId = requestAuctionId,
                    adId = win?.adId,
                    timeToRespond = timeToRespond,
                    slotReload = 0,
                )
                lastRenderEconomics = economics
            } else {
                lastRenderEconomics = null
            }
            eventLogger?.bidResponse(
                auctionId = requestAuctionId,
                pageContext = requestPage,
                adUnitId = adUnitId,
                adType = AdType.REWARDED,
                adSubtype = AdSubtype.VIDEO,
                apiType = ApiType.ORIGINAL,
                autorefreshTime = adUnit.autoRefreshTime.toLong(),
                isAutorefresh = adUnit.autoRefreshTime > 0,
                isRefresh = false,
                resultCode = resultCode?.toString(),
                timeToRespond = timeToRespond,
                adUnitCode = adUnit.configId,
                economics = economics,
            )
            if (economics != null) {
                eventLogger?.bidWon(
                    pageContext = requestPage,
                    adUnitId = adUnitId,
                    adType = AdType.REWARDED,
                    adSubtype = AdSubtype.VIDEO,
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = adUnit.autoRefreshTime.toLong(),
                    isAutorefresh = adUnit.autoRefreshTime > 0,
                    isRefresh = false,
                    adUnitCode = adUnit.configId,
                    economics = economics,
                )
            } else {
                eventLogger?.noBid(
                    pageContext = requestPage,
                    adUnitId = adUnitId,
                    adType = AdType.REWARDED,
                    adSubtype = AdSubtype.VIDEO,
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = adUnit.autoRefreshTime.toLong(),
                    isAutorefresh = adUnit.autoRefreshTime > 0,
                    isRefresh = false,
                    resultCode = noBidResultCode(resultCode),
                    adUnitCode = adUnit.configId,
                    mediaTypes = "[\"video\"]",
                    auctionId = requestAuctionId,
                )
            }
            resultCallback(
                resultCode,
                request,
                connectCallbacks(adLoadCallback, fullScreenContentCallback,
                    renderEconomics().copy(auctionId = requestAuctionId, pageContext = requestPage)),
            )
        }
    }

    private fun connectCallbacks(
        adLoadCallback: AudienzzRewardedAdLoadCallback?,
        fullScreenContentCallback: AudienzzFullScreenContentCallback?,
        renderSnapshot: RenderEconomics,
    ): AudienzzRewardedAdLoadCallback {
        return object : AudienzzRewardedAdLoadCallback() {
            private var settled = false
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                if (settled) return
                settled = true
                // H5: delegate to the publisher's own FullScreenContentCallback if they set one on
                // the ad inside their onAdLoaded (GAM-documented pattern) instead of clobbering it;
                // fall back to the callback passed to load().
                var publisherDirectCallback = rewardedAd.fullScreenContentCallback
                val wrapper = object : FullScreenContentCallback() {
                        private var impressionRecorded = false
                        private var presented = false
                        private var terminal = false
                        private var viewabilityTimer: FullScreenViewabilityTimer? = null
                        override fun onAdClicked() {
                            if (terminal) return
                            super.onAdClicked()
                            if (publisherDirectCallback != null) {
                                publisherDirectCallback?.onAdClicked()
                            } else {
                                fullScreenContentCallback?.onAdClicked()
                            }
                            eventLogger?.adClick(
                                adUnitId = adUnitId,
                                adType = AdType.REWARDED,
                                adSubtype = AdSubtype.VIDEO,
                                apiType = ApiType.ORIGINAL,
                                adUnitCode = adUnit.configId,
                                economics = renderSnapshot,
                            )
                        }

                        override fun onAdDismissedFullScreenContent() {
                            if (terminal) return
                            terminal = true
                            viewabilityTimer?.cancel()
                            super.onAdDismissedFullScreenContent()
                            if (publisherDirectCallback != null) {
                                publisherDirectCallback?.onAdDismissedFullScreenContent()
                            } else {
                                fullScreenContentCallback?.onAdDismissedFullScreenContent()
                            }
                            viewabilityTimer?.cancel()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            if (terminal) return
                            terminal = true
                            viewabilityTimer?.cancel()
                            super.onAdFailedToShowFullScreenContent(error)
                            if (publisherDirectCallback != null) {
                                publisherDirectCallback?.onAdFailedToShowFullScreenContent(error)
                            } else {
                                fullScreenContentCallback?.onAdFailedToShowFullScreenContent(error)
                            }
                            viewabilityTimer?.cancel()
                        }

                        override fun onAdImpression() {
                            if (terminal || impressionRecorded) return
                            impressionRecorded = true
                            super.onAdImpression()
                            if (publisherDirectCallback != null) {
                                publisherDirectCallback?.onAdImpression()
                            } else {
                                fullScreenContentCallback?.onAdImpression()
                            }
                            // Full-screen ad objects expose no app-event listener, so the render
                            // winner is best-effort: the Prebid auction winner's economics if there
                            // was one, else an ad-server (direct) impression.
                            eventLogger?.adImpression(
                                adUnitId = adUnitId,
                                adType = AdType.REWARDED,
                                adSubtype = AdSubtype.VIDEO,
                                apiType = ApiType.ORIGINAL,
                                adUnitCode = adUnit.configId,
                                economics = renderSnapshot,
                            )
                        }

                        override fun onAdShowedFullScreenContent() {
                            if (terminal || presented) return
                            presented = true
                            super.onAdShowedFullScreenContent()
                            if (publisherDirectCallback != null) {
                                publisherDirectCallback?.onAdShowedFullScreenContent()
                            } else {
                                fullScreenContentCallback?.onAdShowedFullScreenContent()
                            }
                            if (terminal) return
                            FullScreenViewabilityTimer(
                                onStart = {
                                    eventLogger?.viewabilityStart(
                                        adUnitId = adUnitId,
                                        adType = AdType.REWARDED,
                                        adSubtype = AdSubtype.VIDEO,
                                        apiType = ApiType.ORIGINAL,
                                        adUnitCode = adUnit.configId,
                                        economics = renderSnapshot,
                                    )
                                },
                                onSuccess = {
                                    eventLogger?.viewabilitySuccess(
                                        adUnitId = adUnitId,
                                        adType = AdType.REWARDED,
                                        adSubtype = AdSubtype.VIDEO,
                                        apiType = ApiType.ORIGINAL,
                                        adUnitCode = adUnit.configId,
                                        economics = renderSnapshot,
                                    )
                                },
                            ).also { viewabilityTimer = it }.onShown()
                        }
                    }
                rewardedAd.fullScreenContentCallback = wrapper
                adLoadCallback?.onAdLoaded(rewardedAd)
                if (rewardedAd.fullScreenContentCallback !== wrapper) {
                    publisherDirectCallback = rewardedAd.fullScreenContentCallback
                    rewardedAd.fullScreenContentCallback = wrapper
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                if (settled) return
                settled = true
                super.onAdFailedToLoad(loadAdError)
                adLoadCallback?.onAdFailedToLoad(loadAdError)
            }
        }
    }

    /**
     * Economics reported on render events. Full-screen ads expose no app event, so the render winner
     * is best-effort: the Prebid auction winner's economics if there was one, else an ad-server
     * (direct) impression.
     */
    private fun renderEconomics(): RenderEconomics {
        val base = lastRenderEconomics ?: RenderEconomics()
        val bidder = prebidWinningBidder ?: AD_SERVER_BIDDER
        return base.copy(
            bidderCode = bidder,
            // Ad server rendered — zero the creative id so a direct-sold impression isn't
            // misclassified as RTB (GMA exposes no served-creative id → "0" stub).
            creativeId = if (bidder == AD_SERVER_BIDDER) "0" else base.creativeId,
            auctionId = base.auctionId ?: currentAuctionId,
        )
    }
}
