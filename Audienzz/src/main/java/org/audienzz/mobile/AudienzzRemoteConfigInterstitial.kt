package org.audienzz.mobile

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import kotlinx.coroutines.CoroutineDispatcher
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
import java.util.UUID
import org.audienzz.mobile.util.AppForegroundMonitor

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

        /**
         * M6: SDK-internal failure that has no GAM [LoadAdError]/[AdError] to report — a missing
         * remote config, an uninitialized SDK, or no Activity available to show the interstitial.
         * Default no-op so existing implementers keep compiling.
         */
        fun onError(reason: String) {}

        fun onLifecycleEvent(event: Map<String, Any?>) {}
    }
    private var interstitialAdHandler: AudienzzInterstitialAdHandler? = null
    private var loadedInterstitialAd: AdManagerInterstitialAd? = null
    private val scope = CoroutineScope(
        Dispatchers.Main + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "CoroutineScope exception", throwable)
            if (!destroyed) {
                loading = false
                emit("loadFailed", throwable.toString())
                events?.onError(throwable.message ?: "Interstitial load failed")
            }
        },
    )

    internal var configDispatcher: CoroutineDispatcher = Dispatchers.IO
    private var generation = 0
    private var loading = false
    private var presenting = false
    private var destroyed = false
    private var disposeAfterPresentation = false
    private var loadId = ""

    private fun emit(event: String, reason: String? = null) {
        events?.onLifecycleEvent(mapOf("event" to event, "loadId" to loadId, "timestampMillis" to System.currentTimeMillis(),
            "configId" to configId, "responseId" to loadedInterstitialAd?.responseInfo?.responseId,
            "reason" to reason))
    }

    fun loadAd() {
        if (destroyed || loading || presenting) {
            events?.onError("Interstitial is destroyed or already loading/presenting")
            return
        }
        loading = true
        val token = ++generation
        loadId = UUID.randomUUID().toString()
        emit("loadRequested")
        scope.launch {
            val manager = MainComponent.Companion.remoteConfigManager
            if (manager == null) {
                Log.e(TAG, "RemoteConfigManager is not initialized")
                loading = false
                emit("loadFailed", "RemoteConfigManager is not initialized")
                events?.onError("RemoteConfigManager is not initialized — call initializeRemoteSdk first")
                return@launch
            }

            val config = withContext(configDispatcher) {
                manager.getAdUnitConfig(configId)
            }

            if (config == null) {
                Log.e(TAG, "Config not found for ID: $configId")
                loading = false
                emit("loadFailed", "Remote config not found")
                events?.onError("Remote config not found for ID: $configId")
                return@launch
            }

            if (!destroyed && token == generation) setupInterstitial(config, token)
        }
    }

    private fun setupInterstitial(config: RemoteAdUnitConfig, token: Int) {
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
                    if (destroyed || token != generation || !loading) return
                    loading = false
                    emit("loadFailed", loadError.toString())
                    Log.d(TAG, "onAdFailed, exception $loadError ConfigId $configId")
                    events?.onFailed(loadError)
                    super.onAdFailedToLoad(loadError)
                }

                override fun onAdLoaded(interstitialAd: AdManagerInterstitialAd) {
                    if (destroyed || token != generation || !loading) return
                    loading = false
                    Log.d(TAG, "Ad loaded, auto-showing. ConfigId $configId")
                    loadedInterstitialAd = interstitialAd
                    emit("loaded")
                    // Reserve the presentation before publisher callbacks to prevent reentrant load.
                    presenting = true
                    events?.onLoaded()
                    if (disposeAfterPresentation) {
                        presenting = false
                        destroy()
                        return
                    }
                    val activity = context.getActivity()
                    emit("showAttempted")
                    if (activity != null && !activity.isFinishing && !activity.isDestroyed && AppForegroundMonitor.isForeground) {
                        loadedInterstitialAd?.show(activity)
                    } else {
                        presenting = false
                        loadedInterstitialAd = null
                        emit("showFailed", "No foreground Activity")
                        // M6: a non-Activity context silently never shows — a paid auction with
                        // zero impressions. Surface it instead of swallowing.
                        Log.e(TAG, "No Activity context available to show interstitial for ConfigId $configId")
                        events?.onError("No Activity context available to show interstitial for ConfigId $configId")
                    }
                    super.onAdLoaded(interstitialAd)
                }
            },
            fullScreenContentCallback = object : AudienzzFullScreenContentCallback() {
                override fun onAdClicked() {
                    if (destroyed || token != generation || !presenting) return
                    events?.onClicked()
                    super.onAdClicked()
                }

                override fun onAdDismissedFullScreenContent() {
                    if (destroyed || token != generation || !presenting) return
                    emit("dismissed")
                    finishPresentation()
                    events?.onClosed()
                    super.onAdDismissedFullScreenContent()
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    if (destroyed || token != generation || !presenting) return
                    emit("showFailed", p0.toString())
                    finishPresentation()
                    events?.onFailedToShow(p0)
                    super.onAdFailedToShowFullScreenContent(p0)
                }

                override fun onAdImpression() {
                    if (destroyed || token != generation || !presenting) return
                    emit("impression")
                    super.onAdImpression()
                }

                override fun onAdShowedFullScreenContent() {
                    if (destroyed || token != generation || !presenting) return
                    emit("presented")
                    events?.onOpened()
                    super.onAdShowedFullScreenContent()
                }
            },
            resultCallback = { resultCode, request, listener ->
                if (!destroyed && token == generation) AdManagerInterstitialAd.load(
                    context,
                    config.gamConfig.adUnitPath,
                    request,
                    listener,
                )
            },
        )
    }

    private fun finishPresentation() {
        presenting = false
        loadedInterstitialAd = null
        if (disposeAfterPresentation) destroy()
    }

    fun destroy() {
        if (presenting) {
            disposeAfterPresentation = true
            emit("disposeDeferred")
            return
        }
        destroyed = true
        loading = false
        generation++
        emit("disposed")
        // M6: drop the loaded ad and handler so a destroyed instance can't retain/show a stale ad.
        loadedInterstitialAd = null
        interstitialAdHandler = null
        scope.cancel()
    }

    companion object {
        private const val TAG = "AudienzzRemoteConfigInterstitial"
    }
}
