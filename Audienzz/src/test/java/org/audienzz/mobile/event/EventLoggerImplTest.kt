package org.audienzz.mobile.event

import android.util.Log
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import org.audienzz.mobile.event.id.AdIdProvider
import org.audienzz.mobile.event.id.CompanyIdProvider
import org.audienzz.mobile.event.preferences.EventPreferences
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.junit.Before
import org.junit.Test
import java.util.UUID

internal class EventLoggerImplTest {

    private lateinit var logger: EventLogger

    @RelaxedMockK
    lateinit var remoteRepository: RemoteEventRepository

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
            remoteRepository = remoteRepository,
            preferences = preferences,
            adIdProvider = adIdProvider,
            dispatcher = dispatcher,
            companyIdProvider = companyIdProvider,
        )
    }

    @Test
    fun logEvent_submitDirectly() {
        every { preferences.getVisitorId() } returns mockUUID.toString()
        every { adIdProvider.getAdId() } returns mockAdId

        logger.logEvent(mockEvent)
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) {
            remoteRepository.submit(match {
                it.uuid == mockUUID.toString() &&
                    it.visitorId == mockUUID.toString() &&
                    it.sessionId == mockUUID.toString() &&
                    it.companyId == mockCompanyId &&
                    it.deviceId == mockAdId &&
                    it.sessionStartTimestamp != null
            })
        }
    }

    @Test
    fun onScreenResumed_firesPageImpression() {
        logger.onScreenResumed("com.example.MainActivity")
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) {
            remoteRepository.submit(
                match {
                    it.eventType == EventType.PAGE_IMPRESSION &&
                        it.screenName == "com.example.MainActivity" &&
                        it.pageImpressionId != null
                },
            )
        }
    }

    @Test
    fun onScreenResumed_newPageImpressionIdEachCall() {
        val ids = mutableListOf<String?>()
        every { UUID.randomUUID() } returnsMany listOf(
            mockUUID,
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
        )

        logger.onScreenResumed("com.example.ScreenA")
        logger.onScreenResumed("com.example.ScreenB")
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 2) {
            remoteRepository.submit(match { it.eventType == EventType.PAGE_IMPRESSION })
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
    fun logEvent_errorDoesNotCrash() {
        every { adIdProvider.getAdId() } returns mockAdId

        logger.logEvent(mockEvent)
        dispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) {
            remoteRepository.submit(any())
        }
    }
}
