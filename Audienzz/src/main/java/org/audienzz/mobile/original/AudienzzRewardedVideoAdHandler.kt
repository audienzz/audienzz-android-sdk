package org.audienzz.mobile.original

import android.content.Context
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
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.event.util.adSubtype

class AudienzzRewardedVideoAdHandler(
    private val adUnit: AudienzzRewardedVideoAdUnit,
    private val adUnitId: String,
) {

    init {
        eventLogger?.adCreation(
            adUnitId = adUnitId,
            adType = AdType.REWARDED,
            adSubtype = adUnit.adFormats.adSubtype,
            apiType = ApiType.ORIGINAL,
        )
    }

    @JvmOverloads fun load(
        context: Context,
        request: AdManagerAdRequest = AdManagerAdRequest.Builder().build(),
        listener: RewardedAdLoadCallback,
        resultCallback: ((AudienzzResultCode?) -> Unit)? = null,
    ) {
        eventLogger?.bidRequest(
            adUnitId = adUnitId,
            adType = AdType.REWARDED,
            adSubtype = adUnit.adFormats.adSubtype,
            apiType = ApiType.ORIGINAL,
            autorefreshTime = adUnit.autoRefreshTime.toLong(),
            isAutorefresh = adUnit.autoRefreshTime > 0,
            isRefresh = false,
        )
        adUnit.fetchDemand(request) { resultCode ->
            eventLogger?.bidWinner(
                adUnitId = adUnitId,
                adType = AdType.REWARDED,
                adSubtype = adUnit.adFormats.adSubtype,
                apiType = ApiType.ORIGINAL,
                autorefreshTime = adUnit.autoRefreshTime.toLong(),
                isAutorefresh = adUnit.autoRefreshTime > 0,
                isRefresh = false,
                resultCode = resultCode?.toString(),
                targetKeywords = adUnit.keywords.toList(),
            )
            resultCallback?.invoke(resultCode)
            if (resultCode == AudienzzResultCode.SUCCESS) {
                RewardedAd.load(
                    context,
                    adUnitId,
                    request,
                    createAdListener(listener),
                )
            }
        }
    }

    private fun createAdListener(listener: RewardedAdLoadCallback?): RewardedAdLoadCallback {
        return object : RewardedAdLoadCallback() {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                listener?.onAdLoaded(rewardedAd)
                rewardedAd.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdClicked() {
                            eventLogger?.adClick(adUnitId)
                        }

                        override fun onAdDismissedFullScreenContent() {
                            super.onAdDismissedFullScreenContent()
                            eventLogger?.closeAd(adUnitId)
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
