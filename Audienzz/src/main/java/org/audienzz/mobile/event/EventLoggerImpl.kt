package org.audienzz.mobile.event

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.di.qualifier.IO
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import org.audienzz.mobile.event.id.AdIdProvider
import org.audienzz.mobile.event.id.CompanyIdProvider
import org.audienzz.mobile.event.network.mapper.EventNetworkMapper
import org.audienzz.mobile.event.preferences.EventPreferences
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EventLoggerImpl @Inject constructor(
    private val batcher: EventBatcher,
    private val mapper: EventNetworkMapper,
    private val preferences: EventPreferences,
    private val adIdProvider: AdIdProvider,
    private val companyIdProvider: CompanyIdProvider,
    @IO dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EventLogger, CoroutineScope {

    private val sessionId = generateUuidString()
    /**
     * Unix time in **seconds**, fixed for the life of the session.
     *
     * It was milliseconds until this release, which is why historical rows are ~1e12 and new ones
     * are ~1e9. A consumer can tell them apart by magnitude — see `docs/analytics-contract.md` for
     * the migration rule. Durations (`time_to_respond`, `autorefresh_time`) are unchanged and
     * remain milliseconds; only this absolute timestamp moved.
     */
    private val sessionStartTimestamp = System.currentTimeMillis() / 1000

    // Monotonic per-session counter so the backend can order events regardless of the
    // order in which the async POSTs actually arrive. Starts at 0, +1 per logged event.
    private val sessionSequence = AtomicInteger(0)

    @Volatile
    private var currentPageImpressionId: String? = null

    @Volatile
    private var currentScreenName: String? = null

    override val coroutineContext = dispatcher + SupervisorJob() +
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unexpected coroutine error", throwable)
        }

    init {
        generateVisitorIdIfAbsent()
    }

    private fun generateVisitorIdIfAbsent() {
        if (preferences.getVisitorId() == null) {
            preferences.setVisitorId(generateUuidString())
        }
    }

    override fun onScreenResumed(screenName: String) {
        currentPageImpressionId = generateUuidString()
        currentScreenName = screenName
        logEvent(
            EventDomain(
                eventType = EventType.PAGE_IMPRESSION,
                screenName = screenName,
                // Guarded: reading Prebid targeting touches org.json, which is unavailable in plain
                // JVM unit tests; never let analytics setup crash a screen visit.
                consentString = runCatching { AudienzzTargetingParams.gdprConsentString }.getOrNull(),
            ),
        )
    }

    override fun logEvent(event: EventDomain) {
        // Safety net: if an ad event fires before any onScreenResumed (e.g. a banner prefetches
        // before the host Activity/Fragment's onResume), lazily start a page-impression id so the
        // event is never orphaned. onScreenResumed normally sets this first, so this rarely triggers.
        if (currentPageImpressionId == null && event.eventType != EventType.PAGE_IMPRESSION) {
            currentPageImpressionId = generateUuidString()
        }
        // Assign the sequence synchronously, in call order, before the coroutine launches.
        val sequencedEvent = event.copy(sessionSequence = sessionSequence.getAndIncrement())
        // Inject ids off the main thread (adId lookup can block), then map to the wire payload and
        // hand it to the batcher, which coalesces events and POSTs them to /submit/batch on
        // size/time/background triggers.
        //
        // Mapping here rather than at send time freezes the device/app context at event creation,
        // which matters once the batcher persists across process death: a restored event must carry
        // the app version it was produced under, not the one it was eventually delivered from.
        launch {
            batcher.enqueue(mapper.toNetwork(sequencedEvent.injectIds()))
        }
    }

    private fun generateUuidString() = UUID.randomUUID().toString()

    private fun EventDomain.injectIds(): EventDomain =
        copy(
            uuid = generateUuidString(),
            visitorId = preferences.getVisitorId(),
            companyId = companyIdProvider.getCompanyId(),
            sessionId = this@EventLoggerImpl.sessionId,
            sessionStartTimestamp = this@EventLoggerImpl.sessionStartTimestamp,
            deviceId = adIdProvider.getAdId(),
            pageImpressionId = currentPageImpressionId,
            screenName = screenName ?: currentScreenName,
            websiteId = websiteId ?: runCatching { AudienzzPrebidMobile.publisherId }.getOrNull(),
        )

    companion object {

        private const val TAG = "EventLogger"
    }
}
