package org.audienzz.mobile.original

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import org.audienzz.mobile.AudienzzInterstitialAdUnit
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
import org.audienzz.mobile.event.noBid
import org.audienzz.mobile.event.viewabilityStart
import org.audienzz.mobile.event.viewabilitySuccess
import org.audienzz.mobile.original.callbacks.AudienzzFullScreenContentCallback
import org.audienzz.mobile.original.callbacks.AudienzzInterstitialAdLoadCallback
import org.audienzz.mobile.util.AD_SERVER_BIDDER
import org.audienzz.mobile.util.FullScreenViewabilityTimer
import org.audienzz.mobile.util.HB_BIDDER_KEY
import org.audienzz.mobile.util.HB_FORMAT_KEY
import org.audienzz.mobile.util.HB_PB_KEY
import org.audienzz.mobile.util.HB_SIZE_KEY
import org.audienzz.mobile.util.prebidKeyword

class AudienzzInterstitialAdHandler(
    private val adUnit: AudienzzInterstitialAdUnit,
    private val adUnitId: String,
) {

    // Prebid auction winner (hb_bidder), captured on bid success and reported on adImpression.
    private var prebidWinningBidder: String? = null

    // Full-screen viewability (viewability.start / viewability.success); cancelled on dismiss.
    private var viewabilityTimer: FullScreenViewabilityTimer? = null

    /**
     * @param fullScreenContentCallback use for work with callbacks from Interstitial ad
     * @param resultCallback return result code, request and callback for Interstitial ad.
     * Then it is required to load GAM ad.
     */
    @JvmOverloads fun load(
        gamRequestBuilder: AdManagerAdRequest.Builder = AdManagerAdRequest.Builder(),
        adLoadCallback: AudienzzInterstitialAdLoadCallback,
        fullScreenContentCallback: AudienzzFullScreenContentCallback? = null,
        resultCallback: (
        (
            AudienzzResultCode?,
            AdManagerAdRequest,
            AudienzzInterstitialAdLoadCallback,
        ) -> Unit
        ),
    ) {
        prebidWinningBidder = null
        val requestStartMs = System.currentTimeMillis()
        eventLogger?.bidRequest(
            adUnitId = adUnitId,
            adType = AdType.INTERSTITIAL,
            adSubtype = adUnit.getSubType(),
            apiType = ApiType.ORIGINAL,
            autorefreshTime = adUnit.autoRefreshTime.toLong(),
            isAutorefresh = adUnit.autoRefreshTime > 0,
            isRefresh = false,
        )
        val ppid = AudienzzPrebidMobile.ppidManager?.getPpid()
        if (ppid != null) {
            gamRequestBuilder.setPublisherProvidedId(ppid)
        }

        val request =
            AudienzzTargetingParams.CUSTOM_TARGETING_MANAGER.applyToGamRequestBuilder(
                gamRequestBuilder,
            )
                .build()
        adUnit.fetchDemand(request) { resultCode ->
            eventLogger?.bidResponse(
                adUnitId = adUnitId,
                adType = AdType.INTERSTITIAL,
                adSubtype = adUnit.getSubType(),
                apiType = ApiType.ORIGINAL,
                autorefreshTime = adUnit.autoRefreshTime.toLong(),
                isAutorefresh = adUnit.autoRefreshTime > 0,
                isRefresh = false,
                resultCode = resultCode?.toString(),
                timeToRespond = System.currentTimeMillis() - requestStartMs,
            )
            if (resultCode == AudienzzResultCode.SUCCESS) {
                prebidWinningBidder = request.prebidKeyword(HB_BIDDER_KEY)
                val win = adUnit.getWinningBid()
                eventLogger?.bidWon(
                    adUnitId = adUnitId,
                    adType = AdType.INTERSTITIAL,
                    adSubtype = adUnit.getSubType(),
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = adUnit.autoRefreshTime.toLong(),
                    isAutorefresh = adUnit.autoRefreshTime > 0,
                    isRefresh = false,
                    priceBucket = request.prebidKeyword(HB_PB_KEY),
                    hbSize = request.prebidKeyword(HB_SIZE_KEY),
                    hbFormat = request.prebidKeyword(HB_FORMAT_KEY),
                    cpm = win?.cpm,
                    currency = win?.currency,
                    creativeId = win?.creativeId,
                    auctionId = win?.auctionId,
                    adId = win?.adId,
                )
            } else {
                eventLogger?.noBid(
                    adUnitId = adUnitId,
                    adType = AdType.INTERSTITIAL,
                    adSubtype = adUnit.getSubType(),
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = adUnit.autoRefreshTime.toLong(),
                    isAutorefresh = adUnit.autoRefreshTime > 0,
                    isRefresh = false,
                    resultCode = resultCode?.toString(),
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
        adLoadCallback: AudienzzInterstitialAdLoadCallback?,
        fullScreenContentCallback: AudienzzFullScreenContentCallback?,
    ): AudienzzInterstitialAdLoadCallback {
        return object : AudienzzInterstitialAdLoadCallback() {
            override fun onAdLoaded(adManagerInterstitialAd: AdManagerInterstitialAd) {
                adLoadCallback?.onAdLoaded(adManagerInterstitialAd)
                super.onAdLoaded(adManagerInterstitialAd)
                adManagerInterstitialAd.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdClicked() {
                            super.onAdClicked()
                            fullScreenContentCallback?.onAdClicked()
                            eventLogger?.adClick(adUnitId)
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
                            // winner can't be confirmed: report the Prebid auction winner if there
                            // was one, else the ad server. winner_bidder_code is left unset.
                            eventLogger?.adImpression(
                                adUnitId = adUnitId,
                                adType = AdType.INTERSTITIAL,
                                adSubtype = adUnit.getSubType(),
                                apiType = ApiType.ORIGINAL,
                                bidderCode = prebidWinningBidder ?: AD_SERVER_BIDDER,
                            )
                        }

                        override fun onAdShowedFullScreenContent() {
                            super.onAdShowedFullScreenContent()
                            fullScreenContentCallback?.onAdShowedFullScreenContent()
                            FullScreenViewabilityTimer(
                                onStart = {
                                    eventLogger?.viewabilityStart(
                                        adUnitId = adUnitId,
                                        adType = AdType.INTERSTITIAL,
                                        adSubtype = adUnit.getSubType(),
                                        apiType = ApiType.ORIGINAL,
                                    )
                                },
                                onSuccess = {
                                    eventLogger?.viewabilitySuccess(
                                        adUnitId = adUnitId,
                                        adType = AdType.INTERSTITIAL,
                                        adSubtype = adUnit.getSubType(),
                                        apiType = ApiType.ORIGINAL,
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
}
