package org.audienzz.mobile

import android.app.Activity
import android.os.SystemClock
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
import java.lang.ref.WeakReference
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
    private var loadedAt: Long? = null
    internal var now: () -> Long = { SystemClock.elapsedRealtime() }

    val isReady: Boolean
        get() = !destroyed && !presenting && loadedInterstitialAd != null &&
            loadedAt?.let { now() - it < 3_600_000L } == true

    /** Retain one ad for a later opportunity. Repeated preloads never replace ready inventory. */
    fun preload() {
        if (destroyed || loading || presenting || isReady) return
        startLoad(automaticallyShow = false)
    }

    /**
     * Call on the main thread at a publisher-approved transition, after checking frequency caps.
     * False means this opportunity was skipped; it is never queued for a later load completion.
     * True means presentation was submitted; onOpened/onFailedToShow report Google's outcome.
     */
    fun showAtOpportunity(activity: Activity, eligible: Boolean): Boolean {
        val reason = when {
            !eligible -> "ineligible"
            !isReady -> "notReady"
            !AppForegroundMonitor.isForeground || activity.isFinishing || activity.isDestroyed -> "inactive"
            activePresentation?.get() != null -> "anotherInterstitialPresenting"
            else -> null
        }
        if (reason != null) { emit("opportunitySkipped", reason); return false }
        return present(activity)
    }

    private fun present(activity: Activity): Boolean {
        val ad = loadedInterstitialAd ?: return false
        if (presenting || destroyed || activePresentation?.get() != null) return false
        presenting = true
        activePresentation = WeakReference(this)
        emit("showAttempted")
        return try {
            ad.show(activity)
            true
        } catch (error: RuntimeException) {
            emit("showFailed", error.toString())
            finishPresentation()
            events?.onError(error.message ?: "Interstitial presentation failed")
            false
        }
    }

    private fun emit(event: String, reason: String? = null) {
        events?.onLifecycleEvent(mapOf("event" to event, "loadId" to loadId, "timestampMillis" to System.currentTimeMillis(),
            "loadAgeMillis" to loadedAt?.let { now() - it },
            "configId" to configId, "responseId" to loadedInterstitialAd?.responseInfo?.responseId,
            "reason" to reason))
    }

    /** Legacy immediate-display API. Prefer preload() and showAtOpportunity() for new integrations. */
    fun loadAd() = startLoad(automaticallyShow = true)

    private fun startLoad(automaticallyShow: Boolean) {
        if (destroyed || loading || presenting || isReady) {
            events?.onError("Interstitial is destroyed or already loading/presenting")
            return
        }
        loadedInterstitialAd = null
        loadedAt = null
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

            if (!destroyed && token == generation) setupInterstitial(config, token, automaticallyShow)
        }
    }

    private fun setupInterstitial(config: RemoteAdUnitConfig, token: Int, automaticallyShow: Boolean) {
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
                    loadedInterstitialAd = interstitialAd
                    loadedAt = now()
                    emit("loaded")
                    events?.onLoaded()
                    // The ready ad reserves inventory across reentrant publisher callbacks.
                    if (automaticallyShow && !destroyed && token == generation && isReady) {
                        val activity = context.getActivity()
                        if (activity != null && !activity.isFinishing && !activity.isDestroyed && AppForegroundMonitor.isForeground) {
                            if (!present(activity)) {
                                events?.onError("Another interstitial is already presenting")
                            }
                        } else {
                            emit("showFailed", "No foreground Activity")
                            loadedInterstitialAd = null
                            loadedAt = null
                            events?.onError("No Activity context available to show interstitial for ConfigId $configId")
                        }
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
        if (activePresentation?.get() === this) activePresentation = null
        loadedAt = null
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
        private var activePresentation: WeakReference<AudienzzRemoteConfigInterstitial>? = null
    }
}
