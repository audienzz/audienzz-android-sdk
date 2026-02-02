package org.audienzz.mobile

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.audienzz.mobile.api.config.RemoteAdUnitConfig
import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.original.AudienzzInterstitialAdHandler
import org.audienzz.mobile.original.callbacks.AudienzzFullScreenContentCallback
import org.audienzz.mobile.original.callbacks.AudienzzInterstitialAdLoadCallback
import org.audienzz.mobile.util.getActivity
import java.util.EnumSet

class AudienzzRemoteConfigInterstitial(
    private val context: Context,
    private val configId: String,
    private val events: Events? = null,
) {
    interface Events {
        fun onLoaded()
        fun onFailed(loadError: LoadAdError)
        fun onOpened()
        fun onClosed()
        fun onClicked()
        fun onFailedToShow(adError: AdError)
    }
    private var interstitialAdHandler: AudienzzInterstitialAdHandler? = null
    private var loadedInterstitialAd: AdManagerInterstitialAd? = null
    private val scope = CoroutineScope(
        Dispatchers.Main + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "CoroutineScope exception", throwable)
        },
    )

    fun loadAd() {
        scope.launch {
            val manager = MainComponent.Companion.remoteConfigManager
            if (manager == null) {
                Log.e(TAG, "RemoteConfigManager is not initialized")
                return@launch
            }

            val config = withContext(Dispatchers.IO) {
                manager.getAdUnitConfig(configId)
            }

            if (config == null) {
                Log.e(TAG, "Config not found for ID: $configId")
                return@launch
            }

            setupInterstitial(config)
        }
    }

    private fun setupInterstitial(config: RemoteAdUnitConfig) {
        val interstitial = AudienzzInterstitialAdUnit(
            configId = config.prebidConfig.placementId,
            adUnitFormats = EnumSet.of(AudienzzAdUnitFormat.BANNER),
        )

        val interstitialHandler = AudienzzInterstitialAdHandler(
            adUnit = interstitial,
            adUnitId = config.gamConfig.adUnitPath,
        )

        interstitialAdHandler = interstitialHandler

        interstitialAdHandler?.load(
            adLoadCallback = object : AudienzzInterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(loadError: LoadAdError) {
                    Log.d(TAG, "onAdFailed, exception $loadError ConfigId $configId")
                    events?.onFailed(loadError)
                    super.onAdFailedToLoad(loadError)
                }

                override fun onAdLoaded(interstitialAd: AdManagerInterstitialAd) {
                    Log.d(TAG, "Ad loaded, auto-showing. ConfigId $configId")
                    loadedInterstitialAd = interstitialAd
                    events?.onLoaded()
                    context.getActivity()?.let {
                        loadedInterstitialAd?.show(it)
                    }
                    super.onAdLoaded(interstitialAd)
                }
            },
            fullScreenContentCallback = object : AudienzzFullScreenContentCallback() {
                override fun onAdClicked() {
                    events?.onClicked()
                    super.onAdClicked()
                }

                override fun onAdDismissedFullScreenContent() {
                    loadedInterstitialAd = null
                    events?.onClosed()
                    super.onAdDismissedFullScreenContent()
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    loadedInterstitialAd = null
                    events?.onFailedToShow(p0)
                    super.onAdFailedToShowFullScreenContent(p0)
                }

                override fun onAdImpression() {
                    super.onAdImpression()
                }

                override fun onAdShowedFullScreenContent() {
                    events?.onOpened()
                    super.onAdShowedFullScreenContent()
                }
            },
            resultCallback = { resultCode, request, listener ->
                AdManagerInterstitialAd.load(
                    context,
                    config.gamConfig.adUnitPath,
                    request,
                    listener,
                )
            },
        )
    }

    fun destroy() {
        scope.cancel()
    }

    companion object {
        private const val TAG = "AudienzzRemoteConfigInterstitial"
    }
}
