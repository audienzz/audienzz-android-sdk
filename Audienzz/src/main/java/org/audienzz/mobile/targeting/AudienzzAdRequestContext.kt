package org.audienzz.mobile.targeting

import com.google.android.gms.ads.admanager.AdManagerAdRequest
import org.audienzz.mobile.screen.screenAdCoordinator
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Identity of one logical placement, retained when its native ad object is replaced.
 * SDK adapters share this object; publishers do not need to supply slot numbers.
 */
class AudienzzAdRequestContext {
    internal val registrationOrder = nextOrder.incrementAndGet()
    internal var bridgeIdentifier: String? = null

    internal fun register() = ledger.reserve(this)

    internal fun buildRequest(builder: AdManagerAdRequest.Builder): AdManagerAdRequest {
        val snapshot = ledger.nextRequest(this)
        val request = builder.build()
        GamTargetingSnapshot.detach(request)
        snapshot.targeting.forEach { (key, value) -> request.customTargeting.putString(key, value) }
        return request
    }

    companion object {
        private val nextOrder = AtomicLong()
        private val legacyLedger = AdRequestLedger()
        private val ledger get() = screenAdCoordinator?.requestLedger ?: legacyLedger

        /** Bridge-only identity, stable across native objects created for the same Dart/JS ad. */
        @JvmStatic @JvmOverloads
        fun forSlot(identifier: String, pageKey: String? = null): AudienzzAdRequestContext =
            ledger.forSlot(identifier).also {
                val active = screenAdCoordinator?.activeScreen
                // Reserve before adaptive layout or demand can defer native handler creation.
                if (pageKey == null || active == null || active == pageKey) it.register()
            }
    }
}

/** Immutable values for an admitted auction, not live properties of the currently active page. */
internal data class AdRequestSnapshot(val pageSequence: Int, val slot: Int, val refresh: Int) {
    val targeting: Map<String, String> get() = mapOf(
        "au_page_seq" to pageSequence.toString(),
        "au_slot" to slot.toString(),
        "au_refresh" to refresh.toString(),
    )
}

/** Page-local bookkeeping only. Entries retain no view, activity, ad unit or publisher data. */
internal class AdRequestLedger {
    private data class Entry(val slot: Int, var requests: Int = 0)
    private var pageSequence = 0
    private val entries = IdentityHashMap<AudienzzAdRequestContext, Entry>()
    private val bridgeSlots = mutableMapOf<String, AudienzzAdRequestContext>()

    @Synchronized fun beginPage(sequence: Int, retained: List<AudienzzAdRequestContext>) {
        pageSequence = sequence
        entries.clear()
        bridgeSlots.clear()
        // Coordinator registries are weak, unordered sets. Reserve BEFORE any recreation starts.
        retained.distinct().sortedBy { it.registrationOrder }.forEach { reserve(it) }
    }

    @Synchronized fun reserve(context: AudienzzAdRequestContext) {
        if (!entries.containsKey(context)) entries[context] = Entry(entries.size + 1)
        context.bridgeIdentifier?.let { bridgeSlots[it] = context }
    }

    @Synchronized fun forSlot(identifier: String): AudienzzAdRequestContext =
        bridgeSlots[identifier] ?: AudienzzAdRequestContext().also {
            it.bridgeIdentifier = identifier
            bridgeSlots[identifier] = it
        }

    @Synchronized fun nextRequest(context: AudienzzAdRequestContext): AdRequestSnapshot {
        reserve(context)
        val entry = entries.getValue(context)
        return AdRequestSnapshot(pageSequence, entry.slot, entry.requests++)
    }
}
