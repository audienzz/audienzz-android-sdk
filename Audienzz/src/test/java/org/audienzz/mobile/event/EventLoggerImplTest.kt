package org.audienzz.mobile.event

import android.util.Log
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import org.audienzz.mobile.event.id.AdIdProvider
import org.audienzz.mobile.event.id.CompanyIdProvider
import org.audienzz.mobile.event.network.mapper.EventNetworkMapper
import org.audienzz.mobile.event.preferences.EventPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

internal class EventLoggerImplTest {

    private lateinit var logger: EventLogger

    @RelaxedMockK
    lateinit var batcher: EventBatcher

    private val mapper: EventNetworkMapper = mockk(relaxed = true)
    private val mapped = slot<EventDomain>()

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

        // Enrichment is a property of the DOMAIN event, so these assertions capture what the
        // logger hands the mapper. What the mapper then produces is EventNetworkMapper's contract,
        // covered by AnalyticsContractTest.
        every { mapper.toNetwork(capture(mapped)) } returns mockk(relaxed = true)

        logger = EventLoggerImpl(
            batcher = batcher,
            mapper = mapper,
            preferences = preferences,
            adIdProvider = adIdProvider,
            dispatcher = dispatcher,
            companyIdProvider = companyIdProvider,
        )
    }

    @Test
    fun logEvent_enqueuesEnrichedEvent() {
        every { preferences.getVisitorId() } returns mockUUID.toString()
        every { adIdProvider.getAdId() } returns mockAdId

        logger.logEvent(mockEvent)
        dispatcher.scheduler.runCurrent()

        verify(exactly = 1) { mapper.toNetwork(any()) }
        val enriched = mapped.captured
        assertEquals(mockUUID.toString(), enriched.uuid)
        assertEquals(mockUUID.toString(), enriched.visitorId)
        assertEquals(mockUUID.toString(), enriched.sessionId)
        assertEquals(mockCompanyId, enriched.companyId)
        assertEquals(mockAdId, enriched.deviceId)
        assertNotNull(enriched.sessionStartTimestamp)
    }

    @Test
    fun onScreenResumed_firesPageImpression() {
        logger.onScreenResumed("com.example.MainActivity")
        dispatcher.scheduler.runCurrent()

        verify(exactly = 1) { mapper.toNetwork(any()) }
        val impression = mapped.captured
        assertEquals(EventType.PAGE_IMPRESSION, impression.eventType)
        assertEquals("com.example.MainActivity", impression.screenName)
        assertNotNull(impression.pageImpressionId)
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

        verify(exactly = 2) { mapper.toNetwork(match { it.eventType == EventType.PAGE_IMPRESSION }) }
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

        verify(exactly = 1) {
            batcher.enqueue(any())
        }
    }
}
