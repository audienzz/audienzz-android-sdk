package org.audienzz.mobile.event

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.audienzz.mobile.di.qualifier.IO
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import org.audienzz.mobile.event.id.AdIdProvider
import org.audienzz.mobile.event.id.CompanyIdProvider
import org.audienzz.mobile.event.preferences.EventPreferences
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.audienzz.mobile.util.CurrentActivityTracker
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EventLoggerImpl @Inject constructor(
    private val remoteRepository: RemoteEventRepository,
    private val currentActivityTracker: CurrentActivityTracker,
    private val preferences: EventPreferences,
    private val adIdProvider: AdIdProvider,
    private val companyIdProvider: CompanyIdProvider,
    @IO dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EventLogger, CoroutineScope {

    private val sessionId = generateUuidString()

    override val coroutineContext = dispatcher + SupervisorJob() +
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unexpected coroutine error", throwable)
        }

    private val loggedActivityImpressionsMap = ConcurrentHashMap<Int, Unit>()

    init {
        generateVisitorIdIfAbsent()
    }

    private fun generateVisitorIdIfAbsent() {
        if (preferences.getVisitorId() == null) {
            preferences.setVisitorId(generateUuidString())
        }
    }

    override fun logEvent(event: EventDomain) {
        launch {
            val eventWithIds = event.injectIds()
            Log.d(TAG, "logEvent: $eventWithIds")
            try {
                remoteRepository.submit(eventWithIds)
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to send event", throwable)
            }
            logPageImpression(event)
        }
    }

    private suspend fun logPageImpression(otherEvent: EventDomain) {
        if (otherEvent.eventType != EventType.HEADER_LOADED) return
        val activity = currentActivityTracker.currentActivity ?: run {
            Log.d(TAG, "Current activity is null")
            return
        }
        val identity = System.identityHashCode(currentActivityTracker.currentActivity)
        val isImpressionLogged = loggedActivityImpressionsMap.putIfAbsent(identity, Unit) != null
        if (isImpressionLogged) return
        val event = EventDomain(
            eventType = EventType.PAGE_IMPRESSION,
            adUnitId = otherEvent.adUnitId,
            adViewId = otherEvent.adViewId,
            screenName = activity.componentName.className,
        )
        val eventWithIds = event.injectIds()
        try {
            remoteRepository.submit(eventWithIds)
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to send page impression event", throwable)
        }
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
    }
}
