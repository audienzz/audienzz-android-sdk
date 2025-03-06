package org.audienzz.mobile.original

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.AudienzzRewardedVideoAdUnit
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
import org.audienzz.mobile.util.AudienzzFullScreenContentCallback

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

    @JvmOverloads fun load(
        context: Context,
        request: AdManagerAdRequest = AdManagerAdRequest.Builder().build(),
        listener: RewardedAdLoadCallback,
        resultCallback: ((AudienzzResultCode?) -> Unit),
        manager: AudienzzFullScreenContentCallback? = null,
        requestCallback: ((AdManagerAdRequest, RewardedAdLoadCallback) -> Unit),
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
        adUnit.fetchDemand(request) { resultCode ->
            if (resultCode == AudienzzResultCode.SUCCESS) {
                val result = resultCode.let { it }.toString()
                eventLogger?.bidWinner(
                    adUnitId = adUnitId,
                    adType = AdType.REWARDED,
                    adSubtype = AdSubtype.VIDEO,
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = adUnit.autoRefreshTime.toLong(),
                    isAutorefresh = adUnit.autoRefreshTime > 0,
                    isRefresh = false,
                    resultCode = result,
                    targetKeywords = adUnit.keywords.toList(),
                )
            }
            resultCallback.invoke(resultCode)
            requestCallback.invoke(request, connectListeners(listener, manager))
        }
        Log.d("one", "context will be remove $context)")
    }

    private fun connectListeners(
        listener: RewardedAdLoadCallback?,
        manager: AudienzzFullScreenContentCallback?,
    ): RewardedAdLoadCallback {
        return object : RewardedAdLoadCallback() {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                listener?.onAdLoaded(rewardedAd)
                rewardedAd.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdClicked() {
                            eventLogger?.adClick(adUnitId)
                            manager?.let { it.onAdClickedAd() }
                        }

                        override fun onAdDismissedFullScreenContent() {
                            super.onAdDismissedFullScreenContent()
                            eventLogger?.closeAd(adUnitId)
                            manager?.let { it.onAdDismissedFullScreen() }
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            super.onAdFailedToShowFullScreenContent(error)
                            manager?.let { it.onAdFailedToShowFullScreen(error) }
                        }

                        override fun onAdImpression() {
                            super.onAdImpression()
                            manager?.let { it.onAdImpression() }
                        }

                        override fun onAdShowedFullScreenContent() {
                            super.onAdShowedFullScreenContent()
                            manager?.let { it.onAdShowedFullScreen() }
                        }
                    }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                eventLogger?.adFailedToLoad(
                    adUnitId = adUnitId,
                    errorMessage = loadAdError.message,
                )
                listener?.onAdFailedToLoad(loadAdError)
            }
        }
    }
}
