package org.audienzz.mobile.original

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.rewarded.RewardedAd
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.AudienzzRewardedVideoAdUnit
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.event.adClick
import org.audienzz.mobile.event.adCreation
import org.audienzz.mobile.event.adFailedToLoad
import org.audienzz.mobile.event.bidRequest
import org.audienzz.mobile.event.bidWinner
import org.audienzz.mobile.event.closeAd
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.original.callbacks.AudienzzFullScreenContentCallback
import org.audienzz.mobile.original.callbacks.AudienzzRewardedAdLoadCallback

class AudienzzRewardedVideoAdHandler(
    private val adUnit: AudienzzRewardedVideoAdUnit,
    private val adUnitId: String,
) {

    init {
        eventLogger?.adCreation(
            adUnitId = adUnitId,
            adType = AdType.REWARDED,
            adSubtype = AdSubtype.VIDEO,
            apiType = ApiType.ORIGINAL,
        )
    }

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
        eventLogger?.bidRequest(
            adUnitId = adUnitId,
            adType = AdType.REWARDED,
            adSubtype = AdSubtype.VIDEO,
            apiType = ApiType.ORIGINAL,
            autorefreshTime = adUnit.autoRefreshTime.toLong(),
            isAutorefresh = adUnit.autoRefreshTime > 0,
            isRefresh = false,
        )
        val request = AudienzzTargetingParams.CUSTOM_TARGETING_MANAGER.applyToGamRequestBuilder(
            gamRequestBuilder,
        )
            .build()
        adUnit.fetchDemand(request) { resultCode ->
            if (resultCode == AudienzzResultCode.SUCCESS) {
                eventLogger?.bidWinner(
                    adUnitId = adUnitId,
                    adType = AdType.REWARDED,
                    adSubtype = AdSubtype.VIDEO,
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = adUnit.autoRefreshTime.toLong(),
                    isAutorefresh = adUnit.autoRefreshTime > 0,
                    isRefresh = false,
                    resultCode = resultCode.toString(),
                    targetKeywords = request.keywords.toList(),
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
                            eventLogger?.adClick(adUnitId)
                            fullScreenContentCallback?.onAdClicked()
                        }

                        override fun onAdDismissedFullScreenContent() {
                            super.onAdDismissedFullScreenContent()
                            eventLogger?.closeAd(adUnitId)
                            fullScreenContentCallback?.onAdDismissedFullScreenContent()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            super.onAdFailedToShowFullScreenContent(error)
                            fullScreenContentCallback?.onAdFailedToShowFullScreenContent(error)
                        }

                        override fun onAdImpression() {
                            super.onAdImpression()
                            fullScreenContentCallback?.onAdImpression()
                        }

                        override fun onAdShowedFullScreenContent() {
                            super.onAdShowedFullScreenContent()
                            fullScreenContentCallback?.onAdShowedFullScreenContent()
                        }
                    }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                eventLogger?.adFailedToLoad(
                    adUnitId = adUnitId,
                    errorMessage = loadAdError.message,
                )
                adLoadCallback?.onAdFailedToLoad(loadAdError)
            }
        }
    }
}
