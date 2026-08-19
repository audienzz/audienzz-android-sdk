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
import org.audienzz.mobile.event.adCreation
import org.audienzz.mobile.event.adFailedToLoad
import org.audienzz.mobile.event.bidRequest
import org.audienzz.mobile.event.bidWinner
import org.audienzz.mobile.event.closeAd
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.eventLogger
import org.audienzz.mobile.original.callbacks.AudienzzFullScreenContentCallback
import org.audienzz.mobile.original.callbacks.AudienzzInterstitialAdLoadCallback

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
        adLoadCallback: AudienzzInterstitialAdLoadCallback?,
        fullScreenContentCallback: AudienzzFullScreenContentCallback?,
    ): AudienzzInterstitialAdLoadCallback {
        return object : AudienzzInterstitialAdLoadCallback() {
            override fun onAdLoaded(adManagerInterstitialAd: AdManagerInterstitialAd) {
                super.onAdLoaded(adManagerInterstitialAd)
                adLoadCallback?.onAdLoaded(adManagerInterstitialAd)
                // H5: the publisher may set their own FullScreenContentCallback on the ad inside
                // their onAdLoaded (the GAM-documented pattern). Capture whatever they set and
                // delegate to it so our analytics wrapper does not clobber their dismiss/fail/etc.
                // callbacks; fall back to the callback passed to load() when they didn't set one.
                val publisherDirectCallback = adManagerInterstitialAd.fullScreenContentCallback
                adManagerInterstitialAd.fullScreenContentCallback =
                    object : FullScreenContentCallback() {
                        override fun onAdClicked() {
                            super.onAdClicked()
                            if (publisherDirectCallback != null) {
                                publisherDirectCallback.onAdClicked()
                            } else {
                                fullScreenContentCallback?.onAdClicked()
                            }
                            eventLogger?.adClick(adUnitId)
                        }

                        override fun onAdDismissedFullScreenContent() {
                            super.onAdDismissedFullScreenContent()
                            if (publisherDirectCallback != null) {
                                publisherDirectCallback.onAdDismissedFullScreenContent()
                            } else {
                                fullScreenContentCallback?.onAdDismissedFullScreenContent()
                            }
                            eventLogger?.closeAd(adUnitId)
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            super.onAdFailedToShowFullScreenContent(error)
                            if (publisherDirectCallback != null) {
                                publisherDirectCallback.onAdFailedToShowFullScreenContent(error)
                            } else {
                                fullScreenContentCallback?.onAdFailedToShowFullScreenContent(error)
                            }
                        }

                        override fun onAdImpression() {
                            super.onAdImpression()
                            if (publisherDirectCallback != null) {
                                publisherDirectCallback.onAdImpression()
                            } else {
                                fullScreenContentCallback?.onAdImpression()
                            }
                        }

                        override fun onAdShowedFullScreenContent() {
                            super.onAdShowedFullScreenContent()
                            if (publisherDirectCallback != null) {
                                publisherDirectCallback.onAdShowedFullScreenContent()
                            } else {
                                fullScreenContentCallback?.onAdShowedFullScreenContent()
                            }
                        }
                    }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                adLoadCallback?.onAdFailedToLoad(loadAdError)
                eventLogger?.adFailedToLoad(adUnitId = adUnitId, errorMessage = loadAdError.message)
            }
        }
    }
}
