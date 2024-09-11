package org.audienzz.mobile.event

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.debounce
import org.audienzz.mobile.di.qualifier.IO
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import org.audienzz.mobile.event.entity.EventType.SCREEN_IMPRESSION
import org.audienzz.mobile.event.id.AdIdProvider
import org.audienzz.mobile.event.id.CompanyIdProvider
import org.audienzz.mobile.event.preferences.EventPreferences
import org.audienzz.mobile.event.repository.local.LocalEventRepository
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.audienzz.mobile.util.CurrentActivityTracker
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("OPT_IN_USAGE")
@Singleton
internal class EventLoggerImpl @Inject constructor(
    private val localRepository: LocalEventRepository,
    private val remoteRepository: RemoteEventRepository,
    private val currentActivityTracker: CurrentActivityTracker,
    private val preferences: EventPreferences,
    private val adIdProvider: AdIdProvider,
    private val companyIdProvider: CompanyIdProvider,
    @IO dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EventLogger, CoroutineScope {

    private val eventsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Int.MAX_VALUE)

    private val sessionId = generateUuidString()

    override val coroutineContext = dispatcher + SupervisorJob() +
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unexpected coroutine error", throwable)
        }

    private val loggedActivityImpressionsMap = ConcurrentHashMap<Int, Unit>()

    init {
        eventsFlow
            .debounce(DEBOUNCE_DURATION)
            .onEach {
                try {
                    sendCurrentBatch()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (throwable: Throwable) {
                    Log.e(TAG, "Failed to send events", throwable)
                }
            }
            .launchIn(this)
        generateVisitorIdIfAbsent()
    }

    private fun generateVisitorIdIfAbsent() {
        if (preferences.getVisitorId() == null) {
            preferences.setVisitorId(generateUuidString())
        }
    }

    private suspend fun sendCurrentBatch() {
        val eventsBatch = localRepository.getEvents(BATCH_LIMIT)
        remoteRepository.batchUpload(eventsBatch)
        localRepository.deleteEvents(eventsBatch)

        if (eventsBatch.size == BATCH_LIMIT) {
            eventsFlow.tryEmit(Unit)
        }
    }

    override fun logEvent(event: EventDomain) {
        launch {
            logEventInternal(event)
            logScreenImpression(event)
        }
    }

    private suspend fun logEventInternal(event: EventDomain) {
        val eventWithIds = event.injectIds()
        Log.d(TAG, "logEventInternal: $eventWithIds")
        localRepository.saveEvent(eventWithIds)
        eventsFlow.emit(Unit)
    }

    private suspend fun logScreenImpression(otherEvent: EventDomain) {
        if (otherEvent.eventType != EventType.AD_CREATION) return
        val activity = currentActivityTracker.currentActivity ?: run {
            Log.d(TAG, "Current activity is null")
            return
        }
        val identity = System.identityHashCode(currentActivityTracker.currentActivity)
        val isImpressionLogged = loggedActivityImpressionsMap.putIfAbsent(identity, Unit) != null
        if (isImpressionLogged) {
            return
        }
        val event = EventDomain(
            eventType = SCREEN_IMPRESSION,
            adUnitId = otherEvent.adUnitId,
            adViewId = otherEvent.adViewId,
            screenName = activity.componentName.className,
        )
        logEventInternal(event)
    }

    private fun generateUuidString() = UUID.randomUUID().toString()

    private fun EventDomain.injectIds(): EventDomain =
        copy(
            uuid = generateUuidString(),
            visitorId = preferences.getVisitorId(),
            companyId = companyIdProvider.getCompanyId(),
            sessionId = this@EventLoggerImpl.sessionId,
            deviceId = adIdProvider.getAdId(),
        )

    companion object {

        private const val TAG = "EventLogger"

        private val DEBOUNCE_DURATION = Duration.ofMillis(500)

        private const val BATCH_LIMIT = 100
    }
}
