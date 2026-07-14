package org.audienzz.mobile.original

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.rewarded.RewardedAd
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.AudienzzRewardedVideoAdUnit
import org.audienzz.mobile.AudienzzTargetingParams
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

    // Full-screen viewability (viewability.start / viewability.success); cancelled on dismiss.
    private var viewabilityTimer: FullScreenViewabilityTimer? = null

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
        val requestStartMs = System.currentTimeMillis()
        eventLogger?.bidRequest(
            adUnitId = adUnitId,
            adType = AdType.REWARDED,
            adSubtype = AdSubtype.VIDEO,
            apiType = ApiType.ORIGINAL,
            autorefreshTime = adUnit.autoRefreshTime.toLong(),
            isAutorefresh = adUnit.autoRefreshTime > 0,
            isRefresh = false,
            adUnitCode = adUnit.configId,
            mediaTypes = "[\"video\"]",
            auctionId = currentAuctionId,
        )
        val ppid = AudienzzPrebidMobile.ppidManager?.getPpid()
        if (ppid != null) {
            gamRequestBuilder.setPublisherProvidedId(ppid)
        }

        val request = AudienzzTargetingParams.CUSTOM_TARGETING_MANAGER.applyToGamRequestBuilder(
            gamRequestBuilder,
        )
            .build()
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
                    auctionId = currentAuctionId,
                    adId = win?.adId,
                    timeToRespond = timeToRespond,
                    slotReload = 0,
                )
                lastRenderEconomics = economics
            } else {
                lastRenderEconomics = null
            }
            eventLogger?.bidResponse(
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
                    auctionId = currentAuctionId,
                )
            }
            resultCallback(
                resultCode,
                request,
                connectCallbacks(adLoadCallback, fullScreenContentCallback),
            )
        }
    }

    private fun connectCallbacks(
        adLoadCallback: AudienzzRewardedAdLoadCallback?,
        fullScreenContentCallback: AudienzzFullScreenContentCallback?,
    ): AudienzzRewardedAdLoadCallback {
        return object : AudienzzRewardedAdLoadCallback() {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                adLoadCallback?.onAdLoaded(rewardedAd)
                rewardedAd.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdClicked() {
                            super.onAdClicked()
                            eventLogger?.adClick(
                                adUnitId = adUnitId,
                                adType = AdType.REWARDED,
                                adSubtype = AdSubtype.VIDEO,
                                apiType = ApiType.ORIGINAL,
                                adUnitCode = adUnit.configId,
                                economics = renderEconomics(),
                            )
                            fullScreenContentCallback?.onAdClicked()
                        }

                        override fun onAdDismissedFullScreenContent() {
                            super.onAdDismissedFullScreenContent()
                            fullScreenContentCallback?.onAdDismissedFullScreenContent()
                            viewabilityTimer?.cancel()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            super.onAdFailedToShowFullScreenContent(error)
                            fullScreenContentCallback?.onAdFailedToShowFullScreenContent(error)
                            viewabilityTimer?.cancel()
                        }

                        override fun onAdImpression() {
                            super.onAdImpression()
                            fullScreenContentCallback?.onAdImpression()
                            // Full-screen ad objects expose no app-event listener, so the render
                            // winner is best-effort: the Prebid auction winner's economics if there
                            // was one, else an ad-server (direct) impression.
                            eventLogger?.adImpression(
                                adUnitId = adUnitId,
                                adType = AdType.REWARDED,
                                adSubtype = AdSubtype.VIDEO,
                                apiType = ApiType.ORIGINAL,
                                adUnitCode = adUnit.configId,
                                economics = renderEconomics(),
                            )
                        }

                        override fun onAdShowedFullScreenContent() {
                            super.onAdShowedFullScreenContent()
                            fullScreenContentCallback?.onAdShowedFullScreenContent()
                            FullScreenViewabilityTimer(
                                onStart = {
                                    eventLogger?.viewabilityStart(
                                        adUnitId = adUnitId,
                                        adType = AdType.REWARDED,
                                        adSubtype = AdSubtype.VIDEO,
                                        apiType = ApiType.ORIGINAL,
                                        adUnitCode = adUnit.configId,
                                        economics = renderEconomics(),
                                    )
                                },
                                onSuccess = {
                                    eventLogger?.viewabilitySuccess(
                                        adUnitId = adUnitId,
                                        adType = AdType.REWARDED,
                                        adSubtype = AdSubtype.VIDEO,
                                        apiType = ApiType.ORIGINAL,
                                        adUnitCode = adUnit.configId,
                                        economics = renderEconomics(),
                                    )
                                },
                            ).also { viewabilityTimer = it }.onShown()
                        }
                    }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
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
