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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EventLoggerImpl @Inject constructor(
    private val remoteRepository: RemoteEventRepository,
    private val preferences: EventPreferences,
    private val adIdProvider: AdIdProvider,
    private val companyIdProvider: CompanyIdProvider,
    @IO dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EventLogger, CoroutineScope {

    private val sessionId = generateUuidString()
    private val sessionStartTimestamp = System.currentTimeMillis()

    @Volatile
    private var currentPageImpressionId: String? = null

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
        logEvent(
            EventDomain(
                eventType = EventType.PAGE_IMPRESSION,
                screenName = screenName,
            ),
        )
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
        )

    companion object {

        private const val TAG = "EventLogger"
    }
}
