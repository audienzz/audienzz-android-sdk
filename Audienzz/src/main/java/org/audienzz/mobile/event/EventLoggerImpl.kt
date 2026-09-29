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
    private var currentPageContext = AnalyticsPageContext()

    override fun capturePageContext(): AnalyticsPageContext = currentPageContext

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
        val page = AnalyticsPageContext(generateUuidString(), screenName)
        currentPageContext = page
        logEvent(
            EventDomain(
                eventType = EventType.PAGE_IMPRESSION,
                pageContext = page,
                screenName = screenName,
                // Guarded: reading Prebid targeting touches org.json, which is unavailable in plain
                // JVM unit tests; never let analytics setup crash a screen visit.
                consentString = runCatching { AudienzzTargetingParams.gdprConsentString }.getOrNull(),
            ),
        )
    }

    override fun logEvent(event: EventDomain) {
        // Only an actual pageImpression creates an ID. Late ad callbacks carry their request's
        // snapshot, including the absence of a page when the integration loaded too early.
        val page = event.pageContext ?: capturePageContext()
        // Assign the sequence synchronously, in call order, before the coroutine launches.
        val context = AnalyticsContext.snapshot()
        val sequencedEvent = event.copy(
            sessionSequence = sessionSequence.getAndIncrement(),
            pageImpressionId = event.pageImpressionId ?: page.pageImpressionId,
            screenName = event.screenName ?: page.screenName,
            publisherId = context.publisherId,
            environment = context.environment,
        )
        // Inject ids off the main thread (adId lookup can block), then map to the wire payload and
        // hand it to the durable sender, which persists it and attempts delivery immediately.
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
            sessionId = this@EventLoggerImpl.sessionId,
            sessionStartTimestamp = this@EventLoggerImpl.sessionStartTimestamp,
            deviceId = adIdProvider.getAdId(),
        )

    companion object {

        private const val TAG = "EventLogger"
    }
}
