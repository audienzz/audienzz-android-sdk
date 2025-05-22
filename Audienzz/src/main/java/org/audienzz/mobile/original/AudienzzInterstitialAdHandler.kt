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

    @Deprecated("Use load() without context")
    @JvmOverloads
    fun load(
        context: Context,
        request: AdManagerAdRequest = AdManagerAdRequest.Builder().build(),
        adLoadCallback: AdManagerInterstitialAdLoadCallback,
        resultCallback: ((AudienzzResultCode?) -> Unit)? = null,
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
                    targetKeywords = request.keywords.toList(),
                )
                AdManagerInterstitialAd.load(
                    context,
                    adUnitId,
                    request,
                    connectAdLoadCallback(adLoadCallback),
                )
            }
            resultCallback?.invoke(resultCode)
        }
    }

    @Deprecated("Will be removed in next versions")
    private fun connectAdLoadCallback(adLoadCallback: AdManagerInterstitialAdLoadCallback?):
        AdManagerInterstitialAdLoadCallback {
        return object : AdManagerInterstitialAdLoadCallback() {
            override fun onAdLoaded(adManagerInterstitialAd: AdManagerInterstitialAd) {
                super.onAdLoaded(adManagerInterstitialAd)
                adLoadCallback?.onAdLoaded(adManagerInterstitialAd)
                adManagerInterstitialAd.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdClicked() {
                            super.onAdClicked()
                            eventLogger?.adClick(adUnitId)
                        }

                        override fun onAdDismissedFullScreenContent() {
                            super.onAdDismissedFullScreenContent()
                            eventLogger?.closeAd(adUnitId)
                        }
                    }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                eventLogger?.adFailedToLoad(adUnitId = adUnitId, errorMessage = loadAdError.message)
                adLoadCallback?.onAdFailedToLoad(loadAdError)
            }
        }
    }

    /**
     * @param fullScreenContentCallback use for work with callbacks from Interstitial ad
     * @param resultCallback return result code, request and callback for Interstitial ad.
     * Then it is required to load GAM ad.
     */
    @JvmOverloads fun load(
        request: AdManagerAdRequest = AdManagerAdRequest.Builder().build(),
        adLoadCallback: AdManagerInterstitialAdLoadCallback,
        fullScreenContentCallback: AudienzzFullScreenContentCallback? = null,
        resultCallback: (
        (
            AudienzzResultCode?,
            AdManagerAdRequest,
            AdManagerInterstitialAdLoadCallback,
        ) -> Unit
        ),
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
        adLoadCallback: AdManagerInterstitialAdLoadCallback?,
        fullScreenContentCallback: AudienzzFullScreenContentCallback?,
    ): AdManagerInterstitialAdLoadCallback {
        return object : AdManagerInterstitialAdLoadCallback() {
            override fun onAdLoaded(adManagerInterstitialAd: AdManagerInterstitialAd) {
                super.onAdLoaded(adManagerInterstitialAd)
                adLoadCallback?.onAdLoaded(adManagerInterstitialAd)
                adManagerInterstitialAd.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdClicked() {
                            super.onAdClicked()
                            eventLogger?.adClick(adUnitId)
                            fullScreenContentCallback?.onAdClickedAd()
                        }

                        override fun onAdDismissedFullScreenContent() {
                            super.onAdDismissedFullScreenContent()
                            eventLogger?.closeAd(adUnitId)
                            fullScreenContentCallback?.onAdDismissedFullScreen()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            super.onAdFailedToShowFullScreenContent(error)
                            fullScreenContentCallback?.onAdFailedToShowFullScreen(error)
                        }

                        override fun onAdImpression() {
                            super.onAdImpression()
                            fullScreenContentCallback?.onAdImpression()
                        }

                        override fun onAdShowedFullScreenContent() {
                            super.onAdShowedFullScreenContent()
                            fullScreenContentCallback?.onAdShowedFullScreen()
                        }
                    }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                eventLogger?.adFailedToLoad(adUnitId = adUnitId, errorMessage = loadAdError.message)
                adLoadCallback?.onAdFailedToLoad(loadAdError)
            }
        }
    }
}
