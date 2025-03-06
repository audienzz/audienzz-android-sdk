package org.audienzz.mobile.original

import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import org.audienzz.mobile.AudienzzInterstitialAdUnit
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.event.adClick
import org.audienzz.mobile.event.adCreation
import org.audienzz.mobile.event.adFailedToLoad
import org.audienzz.mobile.event.bidRequest
import org.audienzz.mobile.event.bidWinner
import org.audienzz.mobile.event.closeAd
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.util.AudienzzFullScreenContentCallback

class AudienzzInterstitialAdHandler(
    private val adUnit: AudienzzInterstitialAdUnit,
    private val adUnitId: String,
) {

    init {
        eventLogger?.adCreation(
            adUnitId = adUnitId,
            adType = AdType.INTERSTITIAL,
            adSubtype = adUnit.getSubType(),
            apiType = ApiType.ORIGINAL,
        )
    }

    /**
     * @param manager use for work with listeners from Interstitial ad
     * @param onLoadRequest return request and listener for Interstitial load ad
     */
    @JvmOverloads fun load(
        @Suppress("UNUSED_PARAMETER") context: Context,
        request: AdManagerAdRequest = AdManagerAdRequest.Builder().build(),
        listener: AdManagerInterstitialAdLoadCallback,
        resultCallback: ((AudienzzResultCode?) -> Unit)? = null,
        manager: AudienzzFullScreenContentCallback? = null,
        onLoadRequest: ((AdManagerAdRequest, AdManagerInterstitialAdLoadCallback) -> Unit)? = null,
    ) {
        eventLogger?.bidRequest(
            adUnitId = adUnitId,
            adType = AdType.INTERSTITIAL,
            adSubtype = adUnit.getSubType(),
            apiType = ApiType.ORIGINAL,
            autorefreshTime = adUnit.autoRefreshTime.toLong(),
            isAutorefresh = adUnit.autoRefreshTime > 0,
            isRefresh = false,
        )
        adUnit.fetchDemand(request) { resultCode ->
            if (resultCode == AudienzzResultCode.SUCCESS) {
                eventLogger?.bidWinner(
                    adUnitId = adUnitId,
                    adType = AdType.INTERSTITIAL,
                    adSubtype = adUnit.getSubType(),
                    apiType = ApiType.ORIGINAL,
                    autorefreshTime = adUnit.autoRefreshTime.toLong(),
                    isAutorefresh = adUnit.autoRefreshTime > 0,
                    isRefresh = false,
                    resultCode = resultCode.toString(),
                    targetKeywords = adUnit.keywords.toList(),
                )
            }
            resultCallback?.invoke(resultCode)
            onLoadRequest?.invoke(request, connectListeners(listener, manager))
        }
    }

    private fun connectListeners(
        listener: AdManagerInterstitialAdLoadCallback?,
        manager: AudienzzFullScreenContentCallback?,
    ): AdManagerInterstitialAdLoadCallback {
        return object : AdManagerInterstitialAdLoadCallback() {
            override fun onAdLoaded(adManagerInterstitialAd: AdManagerInterstitialAd) {
                super.onAdLoaded(adManagerInterstitialAd)
                listener?.onAdLoaded(adManagerInterstitialAd)
                adManagerInterstitialAd.fullScreenContentCallback =
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
                super.onAdFailedToLoad(loadAdError)
                eventLogger?.adFailedToLoad(adUnitId = adUnitId, errorMessage = loadAdError.message)
                listener?.onAdFailedToLoad(loadAdError)
            }
        }
    }
}
