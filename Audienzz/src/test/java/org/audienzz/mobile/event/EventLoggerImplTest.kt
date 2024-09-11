package org.audienzz.mobile.event

import android.util.Log
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import org.audienzz.mobile.event.id.AdIdProvider
import org.audienzz.mobile.event.id.CompanyIdProvider
import org.audienzz.mobile.event.preferences.EventPreferences
import org.audienzz.mobile.event.repository.local.LocalEventRepository
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.audienzz.mobile.util.CurrentActivityTracker
import org.junit.Before
import org.junit.Test
import java.util.UUID

internal class EventLoggerImplTest {

    private lateinit var logger: EventLogger

    @RelaxedMockK
    lateinit var localRepository: LocalEventRepository

    @RelaxedMockK
    lateinit var remoteRepository: RemoteEventRepository

    @RelaxedMockK
    lateinit var currentActivityTracker: CurrentActivityTracker

    @RelaxedMockK
    lateinit var preferences: EventPreferences

    @RelaxedMockK
    lateinit var adIdProvider: AdIdProvider

    @RelaxedMockK
    lateinit var companyIdProvider: CompanyIdProvider

    private lateinit var dispatcher: TestDispatcher

    private val mockUUID = UUID.fromString("dac405ec-7019-4a7f-b260-5a3a94784308")

    private val mockAdId = "adId"
    private val mockAdUnitId = "adUnitId"
    private val mockCompanyId = "companyId"
    private val mockEvent = EventDomain(eventType = EventType.AD_CLICK, adUnitId = mockAdUnitId)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(UUID::class)
        every { UUID.randomUUID() } returns mockUUID

        every { preferences.getVisitorId() } returns null

        every { companyIdProvider.getCompanyId() } returns mockCompanyId

        dispatcher = StandardTestDispatcher()

        logger = EventLoggerImpl(
            localRepository = localRepository,
            remoteRepository = remoteRepository,
            currentActivityTracker = currentActivityTracker,
            preferences = preferences,
            adIdProvider = adIdProvider,
            dispatcher = dispatcher,
            companyIdProvider = companyIdProvider,
        )
    }

    @Test
    fun logEvent_saveLocally() {
        every { preferences.getVisitorId() } returns mockUUID.toString()
        every { adIdProvider.getAdId() } returns mockAdId
        val eventWithIds = mockEvent.copy(
            uuid = mockUUID.toString(),
            visitorId = mockUUID.toString(),
            sessionId = mockUUID.toString(),
            companyId = mockCompanyId,
            deviceId = mockAdId,
        )

        logger.logEvent(mockEvent)
        dispatcher.scheduler.runCurrent()

        coVerifySequence {
            localRepository.saveEvent(eventWithIds)
        }
    }

    @Test
    fun logEvent_screenImpression_adCreation() {
        logger.logEvent(mockEvent.copy(eventType = EventType.AD_CREATION))
        dispatcher.scheduler.runCurrent()

        coVerify {
            localRepository.saveEvent(
                match { it.eventType == EventType.SCREEN_IMPRESSION },
            )
        }
    }

    @Test
    fun logEvent_screenImpression_loggedOnce() {
        logger.logEvent(mockEvent.copy(eventType = EventType.AD_CREATION))
        logger.logEvent(mockEvent.copy(eventType = EventType.AD_CREATION))
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) {
            localRepository.saveEvent(
                match { it.eventType == EventType.SCREEN_IMPRESSION },
            )
        }
    }

    @Test
    fun logEvent_generateIds() {
        verifySequence {
            preferences.getVisitorId()
            preferences.setVisitorId(mockUUID.toString())
        }

        logger.logEvent(mockEvent)
        dispatcher.scheduler.runCurrent()

        verify {
            adIdProvider.getAdId()
            preferences.getVisitorId()
        }
    }

    @Test
    fun logEvent_sendBatch() {
        val localEvents = listOf(mockEvent)
        coEvery { localRepository.getEvents(any()) } returns localEvents

        dispatcher.scheduler.runCurrent()
        logger.logEvent(mockEvent)
        dispatcher.scheduler.advanceTimeBy(600)

        coVerify(exactly = 1) {
            localRepository.getEvents(100)
            remoteRepository.batchUpload(localEvents)
            localRepository.deleteEvents(localEvents)
        }
    }

    @Test
    fun logEvent_sendBatch_limitOverflow() {
        val firstBatch = List(100) { mockEvent }
        val secondBatch = List(10) { mockEvent }
        coEvery { localRepository.getEvents(any()) } returns firstBatch

        dispatcher.scheduler.runCurrent()
        logger.logEvent(mockEvent)
        dispatcher.scheduler.advanceTimeBy(600)
        coEvery { localRepository.getEvents(any()) } returns secondBatch
        dispatcher.scheduler.advanceTimeBy(600)

        coVerifySequence {
            remoteRepository.batchUpload(firstBatch)
            remoteRepository.batchUpload(secondBatch)
        }
    }

    @Test
    fun logEvent_sendBatch_recoversAfterError() {
        val localEvents = listOf(mockEvent)
        coEvery { localRepository.getEvents(any()) } returns localEvents
        coEvery { remoteRepository.batchUpload(any()) } throws Exception()

        dispatcher.scheduler.runCurrent()
        logger.logEvent(mockEvent)
        dispatcher.scheduler.advanceTimeBy(600)
        coVerify(exactly = 0) {
            localRepository.deleteEvents(any())
        }
        coEvery { remoteRepository.batchUpload(any()) } just runs
        dispatcher.scheduler.advanceTimeBy(600)

        coVerify(exactly = 1) {
            remoteRepository.batchUpload(localEvents)
        }
    }
}
