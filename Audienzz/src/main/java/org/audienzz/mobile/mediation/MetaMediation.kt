package org.audienzz.mobile.mediation

import android.util.Log
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.initialization.InitializationStatus
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.util.AudienzzDiagnostics

/**
 * Meta Audience Network mediation, per publisher.
 *
 * Meta only bids through Ad Manager SDK Bidding: Google Mobile Ads finds the Meta adapter on the
 * classpath on its own, and the yield group that holds Meta decides which requests it bids on. So
 * the SDK has no Meta code path — it has a switch. Three things decide whether a publisher gets Meta:
 *
 * 1. **The app ships the adapter** (`com.audienzz:sdk-meta`, or `com.google.ads.mediation:facebook`
 *    added directly). Publishers without Meta never carry Meta's SDK.
 * 2. **The backend has not turned it off** — publisher config `mediation.meta.enabled`. Absent means
 *    on, because on a cold start the config is often not cached yet, and an app that ships the
 *    adapter means to use it.
 * 3. **Ad Manager maps the ad unit to a Meta placement**, in a yield group targeting `au_meta=1`.
 *
 * When 1 and 2 hold, every GAM request carries the reserved key `au_meta=1`; otherwise the key is
 * absent, the yield group does not match, and Meta does not bid. Signals are still collected by the
 * adapter when it is present — the key only decides whether its bid is used.
 */
internal object MetaMediation {

    const val ADAPTER_CLASS = "com.google.ads.mediation.facebook.FacebookMediationAdapter"
    const val TARGETING_KEY = "au_meta"
    private const val TARGETING_VALUE = "1"
    private const val TAG = "AudienzzMeta"
    private const val NOT_CONFIGURED = "NOT_CONFIGURED_IN_AD_MANAGER"

    /** Replaceable in tests; the real check runs once. */
    internal var adapterLookup: () -> Boolean = { isClassPresent(ADAPTER_CLASS) }

    private var adapterPresent: Boolean? = null

    /** Backend `mediation.meta.enabled`; null = not sent. */
    @Volatile
    private var backendEnabled: Boolean? = null

    val isAdapterPresent: Boolean
        get() = adapterPresent ?: adapterLookup().also { adapterPresent = it }

    val isActive: Boolean
        get() = isAdapterPresent && backendEnabled != false

    /** Applies the publisher config switch and updates the request key. */
    fun applyBackendConfig(enabled: Boolean?) {
        backendEnabled = enabled
        applyTargeting()
    }

    /** Sets or clears the reserved `au_meta` key so the next GAM request reflects [isActive]. */
    fun applyTargeting() {
        val manager = AudienzzTargetingParams.CUSTOM_TARGETING_MANAGER
        if (isActive) {
            manager.setReservedTargeting(TARGETING_KEY, TARGETING_VALUE)
        } else {
            manager.removeReservedTargeting(TARGETING_KEY)
        }
        Log.d(TAG, "Meta active=$isActive adapter=$isAdapterPresent backend=$backendEnabled")
        AudienzzDiagnostics.log(
            "mediation",
            "meta",
            "active" to isActive,
            "adapter" to isAdapterPresent,
            "backend" to (backendEnabled ?: "default"),
        )
    }

    /**
     * Logs whether Google Mobile Ads initialized the Meta adapter. Silent when it is not linked.
     *
     * Google only initializes adapters that Ad Manager has a mediation setup for in this app, so a
     * linked adapter missing from the status map means no Meta yield group covers the app yet.
     */
    fun logAdapterStatus(status: InitializationStatus) {
        if (!isAdapterPresent) return
        val adapter = status.adapterStatusMap[ADAPTER_CLASS]
        if (adapter == null) {
            Log.d(TAG, "Meta adapter linked, not initialized: no Meta mediation for this app yet")
            AudienzzDiagnostics.log("mediation", "metaAdapter", "state" to NOT_CONFIGURED)
            return
        }
        Log.d(TAG, "Meta adapter ${adapter.initializationState}: ${adapter.description}")
        AudienzzDiagnostics.log(
            "mediation",
            "metaAdapter",
            "state" to adapter.initializationState,
            "latencyMs" to adapter.latency,
            "description" to adapter.description.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Which ad source filled a GAM request, and what Meta did in it.
     *
     * Logged for every load, not only Meta wins, because the useful question while testing is
     * whether Meta took part at all: a Meta entry with an error or a loss is a working integration,
     * no Meta entry means the request never reached its yield group.
     */
    fun logAdSource(format: String, adUnitId: String?, responseInfo: ResponseInfo?, filled: Boolean) {
        if (!AudienzzDiagnostics.isEnabled || responseInfo == null) return
        val loaded = responseInfo.loadedAdapterResponseInfo
        val meta = responseInfo.adapterResponses.firstOrNull { it.adapterClassName == ADAPTER_CLASS }
        AudienzzDiagnostics.log(
            "gam",
            "adSource",
            "format" to format,
            "adUnit" to adUnitId,
            "filled" to filled,
            "source" to loaded?.adSourceName?.takeIf { filled },
            "adapter" to loaded?.adapterClassName?.takeIf { filled },
            "metaWon" to (filled && loaded?.adapterClassName == ADAPTER_CLASS),
            "metaInAuction" to (meta != null),
            "metaLatencyMs" to meta?.latencyMillis,
            "metaError" to meta?.adError?.let { "${it.code}:${it.message}" },
            "responseId" to responseInfo.responseId,
        )
    }

    @Suppress("SwallowedException")
    private fun isClassPresent(name: String): Boolean =
        try {
            Class.forName(name, false, MetaMediation::class.java.classLoader)
            true
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: LinkageError) {
            false
        }

    /** Test hook: forget the cached lookup and backend value. */
    internal fun resetForTest() {
        adapterPresent = null
        backendEnabled = null
        adapterLookup = { isClassPresent(ADAPTER_CLASS) }
    }
}
