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
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.original.AudienzzInterstitialAdHandler
import org.audienzz.mobile.original.callbacks.AudienzzFullScreenContentCallback
import org.audienzz.mobile.original.callbacks.AudienzzInterstitialAdLoadCallback
import org.audienzz.mobile.util.getActivity
import java.util.UUID
import java.lang.ref.WeakReference
import org.audienzz.mobile.util.AppForegroundMonitor
import org.audienzz.mobile.util.AudienzzDiagnostics

/**
 * Remote fullscreen inventory.
 *
 * Three verbs, and each says exactly what it does:
 *
 *  * [prefetch] obtains and retains one ad. It never presents.
 *  * [show] presents ready inventory at the publisher's current opportunity. If nothing is ready,
 *    the app is not in the foreground, or the publisher rules this opportunity out, that outcome is
 *    reported and NOTHING is scheduled — the reader will not be interrupted later, out of context.
 *  * [prefetchAndShow] asks for presentation when the load completes, or presents inventory already
 *    in hand. This is the only entry point that presents something the publisher did not explicitly
 *    time, and it is opted into by name.
 *
 * Repeated prefetches for the same owner coalesce onto the load in flight and reuse valid ready
 * inventory; repeated presentation calls cannot show twice or start a parallel request.
 *
 * **Migration.** `loadAd()` is gone: it loaded *and* presented, which a method named "load" should
 * not decide. Use [prefetchAndShow] where you relied on that, [prefetch] where you only wanted the
 * inventory. `preload()` is [prefetch]; `showAtOpportunity(activity, eligible)` is
 * [show].
 */
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
    /** Stable logical placement, shared with replacement handlers. */
    var requestContext = org.audienzz.mobile.targeting.AudienzzAdRequestContext()

    private var interstitialAdHandler: AudienzzInterstitialAdHandler? = null
    private var loadedInterstitialAd: AdManagerInterstitialAd? = null
    private val scope = CoroutineScope(
        Dispatchers.Main + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "CoroutineScope exception", throwable)
            if (!destroyed) {
                failLoad(throwable.toString(), throwable.message ?: "Interstitial load failed")
            }
        },
    )

    internal var configDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** Test seam: sees each ad unit a load builds, with the capabilities it will request. */
    internal var onAdUnitBuilt: ((AudienzzInterstitialAdUnit) -> Unit)? = null

    private var generation = 0
    private var loading = false
    private var presenting = false
    private var destroyed = false
    private var disposeAfterPresentation = false

    /**
     * Set by [prefetchAndShow] only. An ordinary [prefetch] can never set it, which is what
     * guarantees a prefetch cannot surprise the reader with a presentation.
     */
    private var showWhenLoaded = false
    private var loadId = ""
    private var loadedAt: Long? = null
    private var recordedImpression = false

    /**
     * Backstop for "at most one discard per load". The primary guarantee is that every discard
     * site nulls [loadedInterstitialAd] immediately after reporting, so the null check below
     * already rejects a second report; this flag keeps that true if a future release path forgets
     * to null. It is deliberately not independently covered by a test — no reachable sequence
     * currently exercises it alone.
     */
    private var discardReported = false
    internal var now: () -> Long = { SystemClock.elapsedRealtime() }

    val isReady: Boolean
        get() = !destroyed && !presenting && loadedInterstitialAd != null &&
            loadedAt?.let { now() - it < 3_600_000L } == true

    /**
     * Obtain and retain one ad, without displaying it.
     *
     * A call made while a load is in flight joins it; a call made while valid inventory is already
     * in hand does nothing. Neither spends another request, and neither can lead to a presentation.
     */
    fun prefetch() = requestLoad(showWhenLoaded = false)

    /**
     * Ask for presentation as soon as the load completes, or present inventory already in hand.
     *
     * Subject to the same guards as [show]: a backgrounded app, expired inventory or another
     * interstitial already on screen still cancel the presentation, reported through
     * [Events.onError]. Like [prefetch], repeated calls coalesce rather than starting a second
     * request.
     */
    fun prefetchAndShow() = requestLoad(showWhenLoaded = true)

    /**
     * Call on the main thread at a publisher-approved transition, after checking frequency caps.
     * False means this opportunity was skipped; it is never queued for a later load completion —
     * that is what [prefetchAndShow] is for, and it has to be asked for.
     * True means presentation was submitted; onOpened/onFailedToShow report Google's outcome.
     */
    @JvmOverloads
    fun show(activity: Activity, eligible: Boolean = true): Boolean {
        val reason = skipReason(activity, eligible)
        if (reason != null) { emit("opportunitySkipped", reason); return false }
        return present(activity)
    }

    /** Why this opportunity cannot be taken, or null when it can. */
    private fun skipReason(activity: Activity?, eligible: Boolean): String? = when {
        !eligible -> "ineligible"
        !isReady -> "notReady"
        activity == null || !AppForegroundMonitor.isForeground ||
            activity.isFinishing || activity.isDestroyed -> "inactive"
        activePresentation?.get() != null -> "anotherInterstitialPresenting"
        else -> null
    }

    /**
     * The single loading path behind both [prefetch] and [prefetchAndShow]. Whether a presentation
     * follows is a property of the request, not a second loading system.
     */
    private fun requestLoad(showWhenLoaded: Boolean) {
        if (destroyed) {
            events?.onError("Interstitial is destroyed")
            return
        }
        // The presentation intent is recorded ONLY on a path that accepts the request. Recording
        // it up front meant a call rejected because something was already on screen left the
        // intent behind, and the next ordinary prefetch presented on its back — the one thing a
        // prefetch promises never to do.
        if (isReady) {
            // Already in hand: this is the same request, answered instantly. Presenting here is
            // what makes a second prefetchAndShow reuse inventory instead of buying more.
            if (showWhenLoaded) presentWhenLoaded()
            return
        }
        if (presenting) {
            // Rejected: another presentation owns the screen, and this request is over.
            if (showWhenLoaded) {
                events?.onError("Another interstitial is already presenting")
            }
            return
        }
        // Coalesce onto the request in flight rather than reporting an error: asking twice for the
        // same thing is exactly what a publisher does across a screen's lifecycle.
        if (loading) {
            if (showWhenLoaded) this.showWhenLoaded = true
            return
        }
        if (showWhenLoaded) this.showWhenLoaded = true
        startLoad()
    }

    /**
     * Present what was just loaded, under the same guards an explicit [show] would apply.
     *
     * Unlike [show] there is no return value for the caller to inspect, so a guard that cancels the
     * presentation is also reported on [Events.onError] — this is the presentation the publisher
     * asked for when they called [prefetchAndShow].
     */
    private fun presentWhenLoaded() {
        showWhenLoaded = false
        // Nothing to do if an ad is already on screen — and emphatically not a failed
        // presentation: the branch below discards held inventory, which must never happen to an
        // ad the reader is looking at. Parity with the iOS owner, where the same shape also broke
        // callback matching.
        if (presenting) return
        val activity = context.getActivity()
        val reason = skipReason(activity, eligible = true)
        if (reason != null) {
            emit("opportunitySkipped", reason)
            emit("showFailed", reason)
            reportDiscardIfUnused("presentationFailed")
            loadedInterstitialAd = null
            loadedAt = null
            events?.onError("Interstitial could not be presented for ConfigId $configId: $reason")
            return
        }
        if (!present(activity!!)) {
            events?.onError("Another interstitial is already presenting")
        }
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
            finishPresentation(discardReason = "presentationFailed")
            events?.onError(error.message ?: "Interstitial presentation failed")
            false
        }
    }

    private fun emit(event: String, reason: String? = null) {
        // The interstitial funnel already names every step; diagnostics just mirrors it into the
        // same greppable stream as banners, so one capture shows both.
        AudienzzDiagnostics.log(
            "interstitial", event, "config" to configId, "loadId" to loadId, "reason" to reason,
        )
        events?.onLifecycleEvent(mapOf("event" to event, "loadId" to loadId, "timestampMillis" to System.currentTimeMillis(),
            "loadAgeMillis" to loadedAt?.let { now() - it },
            "configId" to configId, "responseId" to loadedInterstitialAd?.responseInfo?.responseId,
            "reason" to reason))
    }

    private fun startLoad() {
        // Reaching here with inventory in hand means it aged out: the guard above already
        // established that nothing is presenting, so isReady can only be false because the
        // hour-long GAM lifetime elapsed. That response was filled and never seen.
        reportDiscardIfUnused("expired")
        loadedInterstitialAd = null
        loadedAt = null
        recordedImpression = false
        discardReported = false
        loading = true
        val token = ++generation
        loadId = UUID.randomUUID().toString()
        emit("loadRequested")
        scope.launch {
            val manager = MainComponent.Companion.remoteConfigManager
            if (manager == null) {
                Log.e(TAG, "RemoteConfigManager is not initialized")
                failLoad(
                    "RemoteConfigManager is not initialized",
                    "RemoteConfigManager is not initialized — call initializeRemoteSdk first",
                )
                return@launch
            }

            val config = withContext(configDispatcher) {
                manager.getAdUnitConfig(configId)
            }

            if (config == null) {
                Log.e(TAG, "Config not found for ID: $configId")
                failLoad("Remote config not found", "Remote config not found for ID: $configId")
                return@launch
            }

            if (!destroyed && token == generation) setupInterstitial(config, token)
        }
    }

    /**
     * Every way a load can end without inventory.
     *
     * The presentation intent is cleared HERE rather than at each exit, because that is what kept
     * being missed: a load that failed on a missing remote config took an early return, and once
     * the configuration arrived the next ordinary [prefetch] presented on the back of that dead
     * request. A request that produced no ad is over, however it ended.
     */
    private fun failLoad(reason: String, message: String) {
        loading = false
        showWhenLoaded = false
        emit("loadFailed", reason)
        events?.onError(message)
    }

    private fun setupInterstitial(config: RemoteAdUnitConfig, token: Int) {
        // The sizes the backend configured for this placement, largest first — the same source
        // the remote banner uses for its Prebid ad unit. Without them the ad unit fell back to a
        // hardcoded 1x1, so a 320x480 interstitial asked the exchange for a 1x1 slot.
        val prebidSizes = config.prebidConfig.adSizes
            .sortedByDescending { it.width * it.height }
            .map { AudienzzAdSize(it.width, it.height) }
            .toSet()
        if (prebidSizes.isEmpty()) {
            Log.w(TAG, "No prebid adSizes in remote config for id=$configId — requesting 1x1")
        }

        val interstitial = AudienzzInterstitialAdUnit(
            configId = config.prebidConfig.placementId,
            adSizes = prebidSizes,
        )
        // Formats and API frameworks: backend-controlled, resolved from the config fetched for
        // THIS load. A config that changes while an ad is ready, loading or on screen affects only
        // the next accepted load, so it cannot discard inventory, interrupt a presentation or cause
        // a request of its own.
        interstitial.capabilities = InterstitialCapabilities.resolve(config.prebidConfig)
        onAdUnitBuilt?.invoke(interstitial)

        val interstitialHandler = AudienzzInterstitialAdHandler(
            adUnit = interstitial,
            adUnitId = config.gamConfig.adUnitPath,
            requestContext = requestContext,
        )

        interstitialAdHandler = interstitialHandler

        interstitialAdHandler?.load(
            adLoadCallback = object : AudienzzInterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(loadError: LoadAdError) {
                    if (destroyed || token != generation || !loading) return
                    loading = false
                    // Same invariant as [failLoad] — a request that produced no ad is over, so a
                    // later prefetch must not inherit its presentation. Reported separately
                    // because this failure has a real [LoadAdError] to hand to [Events.onFailed].
                    showWhenLoaded = false
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
                    discardReported = false
                    emit("loaded")
                    // Consumed here rather than inside the presentation, so a callback that
                    // presents or destroys cannot leave the request standing and have an unrelated
                    // later prefetch inherit it.
                    val presentOnCompletion = showWhenLoaded
                    showWhenLoaded = false
                    events?.onLoaded()
                    // The ready ad reserves inventory across reentrant publisher callbacks.
                    if (presentOnCompletion && !destroyed && token == generation && isReady &&
                        !presenting
                    ) {
                        presentWhenLoaded()
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
                    // Presented and dismissed with no impression callback in between: the
                    // creative was on screen but Google never counted it. Distinct from a
                    // presentation that failed outright.
                    finishPresentation(discardReason = "dismissedWithoutImpression")
                    events?.onClosed()
                    super.onAdDismissedFullScreenContent()
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    if (destroyed || token != generation || !presenting) return
                    emit("showFailed", p0.toString())
                    finishPresentation(discardReason = "presentationFailed")
                    events?.onFailedToShow(p0)
                    super.onAdFailedToShowFullScreenContent(p0)
                }

                override fun onAdImpression() {
                    if (destroyed || token != generation || !presenting) return
                    recordedImpression = true
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

    private fun finishPresentation(discardReason: String? = null) {
        if (discardReason != null) reportDiscardIfUnused(discardReason)
        presenting = false
        if (activePresentation?.get() === this) activePresentation = null
        loadedAt = null
        loadedInterstitialAd = null
        if (disposeAfterPresentation) destroy()
    }

    fun destroy() = destroy("disposed")

    /**
     * As [destroy], but records *why* held inventory is being released.
     *
     * A bridge that tears an owner down in order to build its successor knows that is a
     * replacement; from inside this class it is indistinguishable from an ordinary disposal.
     * Only the discard reason changes — teardown is identical.
     */
    fun destroy(reason: String) {
        if (presenting) {
            disposeAfterPresentation = true
            emit("disposeDeferred")
            return
        }
        destroyed = true
        loading = false
        showWhenLoaded = false
        generation++
        emit("disposed")
        reportDiscardIfUnused(reason)
        // M6: drop the loaded ad and handler so a destroyed instance can't retain/show a stale ad.
        loadedInterstitialAd = null
        interstitialAdHandler = null
        scope.cancel()
    }

    /**
     * Reports, at most once per load, that inventory which loaded successfully was released
     * without ever recording an impression.
     *
     * This is the event that makes the load-to-impression gap visible from inside the SDK:
     * `loaded` without a matching `impression` is otherwise silent, and expiry in particular was
     * only ever evaluated lazily inside [isReady], so an ad could age out with nothing recorded
     * anywhere.
     *
     * It deliberately does not fire for a load that failed (there was no inventory) or for
     * inventory that already recorded an impression (it was used).
     *
     * It is a diagnostic, not a billing record. It counts what this SDK handed to, and took back
     * from, the ad server — not Ad Manager's responses-served or render rate, which are measured
     * server-side across demand sources this SDK cannot see. Use it to find *which* placements and
     * *which* reasons dominate, then confirm magnitude in Ad Manager reporting.
     *
     * A terminal event is not guaranteed: if the process is killed while inventory is held,
     * nothing is emitted for it, so these counts are a lower bound.
     */
    private fun reportDiscardIfUnused(reason: String) {
        if (loadedInterstitialAd == null || recordedImpression || discardReported) return
        discardReported = true
        emit("discardedWithoutImpression", reason)
    }

    companion object {
        private const val TAG = "AudienzzRemoteConfigInterstitial"
        private var activePresentation: WeakReference<AudienzzRemoteConfigInterstitial>? = null
    }
}
