package org.audienzz.mobile.event

import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.admanager.AdManagerAdView
import io.mockk.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.json.*
import org.audienzz.mobile.*
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.event.network.mapper.EventNetworkMapper
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.screen.*
import org.audienzz.mobile.util.AppForegroundMonitor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Cold-start ordering and analytics, using the real logger/mapper and installed GAM listener. */
@RunWith(RobolectricTestRunner::class)
class ColdStartPageTest {
    private val dispatcher = StandardTestDispatcher()
    private val sent = mutableListOf<EventNetwork>()
    private lateinit var logger: EventLoggerImpl
    private lateinit var coordinator: ScreenAdCoordinator
    private lateinit var handler: AudienzzAdViewHandler
    private val replies = mutableListOf<(AudienzzResultCode?) -> Unit>()
    private var listener: AdListener = object : AdListener() {}
    private var googleLoads = 0

    @Before fun setup() {
        AudienzzPrebidMobile.resetPendingPageReportsForTesting()
        AppForegroundMonitor.resetForTesting()
        AudienzzPrebidMobile.sdkInitializedOverride = false
        coordinator = ScreenAdCoordinator()
        screenAdCoordinatorOverride = coordinator
        val sender = mockk<EventBatcher>(relaxed = true)
        every { sender.enqueue(capture(sent)) } just Runs
        logger = EventLoggerImpl(sender, EventNetworkMapper(RuntimeEnvironment.getApplication()),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), dispatcher)
        mockkObject(MainComponent.Companion)
        every { MainComponent.eventLogger } returns logger
        every { MainComponent.screenAdCoordinator } returns coordinator
        val view = mockk<AdManagerAdView>(relaxed = true)
        every { view.isAttachedToWindow } returns true
        every { view.adUnitId } returns "/fixture/cold-start"
        every { view.responseInfo } returns null
        every { view.adListener } answers { listener }
        every { view.adListener = any() } answers { listener = firstArg() }
        val unit = mockk<AudienzzAdUnit>(relaxed = true)
        every { unit.fetchDemand(any(), any()) } answers { replies += secondArg<(AudienzzResultCode?) -> Unit>() }
        handler = AudienzzAdViewHandler(view, unit)
        handler.setScreen("A")
    }
    @After fun cleanup() {
        handler.destroy()
        AudienzzPrebidMobile.resetPendingPageReportsForTesting()
        AudienzzPrebidMobile.sdkInitializedOverride = null
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
        unmockkAll()
    }
    private fun load() = handler.load(withLazyLoading = false) { _, _ -> googleLoads++ }
    private fun ready() {
        AudienzzPrebidMobile.sdkInitializedOverride = true
        coordinator.resumeAllAfterSdkInit()
    }
    private fun events(label: String): List<EventNetwork> {
        dispatcher.scheduler.runCurrent()
        val json = Json { encodeDefaults = false; explicitNulls = false }
        sent.forEach { event ->
            val wire = json.encodeToJsonElement(EventNetwork.serializer(), event).jsonObject
            println("COLD_START $label " + buildJsonObject {
                listOf("event_type", "event_id", "page_impression_id", "session_seq", "event_timestamp").forEach { key ->
                    wire[key]?.let { put(key, it) }
                }
                put("attributes", buildJsonObject {
                    listOf("auction_id", "refresh", "slot_reload").forEach { key ->
                        event.attributes[key]?.let { put(key, it) }
                    }
                })
            })
        }
        return sent
    }

    @Test fun latePageKeepsCanceledInitialDeliveryInitial() {
        assertNull(logger.capturePageContext().pageImpressionId)
        load(); assertEquals(0, replies.size)
        ready(); assertEquals(1, replies.size)
        AudienzzPrebidMobile.pageImpression("A")
        assertEquals(2, replies.size)
        replies[0](AudienzzResultCode.NO_BIDS)
        assertEquals(0, googleLoads)
        replies[1](AudienzzResultCode.NO_BIDS); listener.onAdLoaded()
        assertEquals(1, googleLoads)
        val all = events("late-page")
        assertEquals(listOf("bidRequest", "pageImpression", "bidRequest", "bidResponse", "noBid"), all.map { it.eventType })
        assertEquals(listOf(0, 1, 2, 3, 4), all.map { it.sessionSeq })
        val bids = all.filter { it.eventType == "bidRequest" }
        assertNull(bids[0].pageImpressionId)
        val page = requireNotNull(all[1].pageImpressionId)
        assertEquals(page, bids[1].pageImpressionId)
        assertEquals("false", bids[0].attributes["refresh"])
        assertEquals("false", bids[1].attributes["refresh"])
        assertEquals("0", bids[1].attributes["slot_reload"])
        val firstAuction = requireNotNull(bids[0].attributes["auction_id"])
        val secondAuction = requireNotNull(bids[1].attributes["auction_id"])
        assertNotEquals(firstAuction, secondAuction)
        assertEquals(secondAuction, all.single { it.eventType == "bidResponse" }.attributes["auction_id"])
    }

    @Test fun pageDuringInitializationBeforeLoadProducesOneAttributedInitialAuction() {
        AudienzzPrebidMobile.pageImpression("A")
        val page = requireNotNull(logger.capturePageContext().pageImpressionId)
        assertEquals(1, coordinator.epoch)
        load(); assertEquals(0, replies.size)
        ready(); assertEquals(1, replies.size)
        replies.single()(AudienzzResultCode.NO_BIDS); listener.onAdLoaded()
        val all = events("early-page")
        assertEquals(listOf("pageImpression", "bidRequest", "bidResponse", "noBid"), all.map { it.eventType })
        assertTrue(all.all { it.pageImpressionId == page })
        assertEquals("false", all.single { it.eventType == "bidRequest" }.attributes["refresh"])
        assertEquals(1, googleLoads)
    }

    @Test fun initializationDrainsEarlyPagesBeforePrebidCanResumeAds() = assertInitializationDrainsPages(remote = false)
    @Test fun remoteInitializationAlsoDrainsEarlyPages() = assertInitializationDrainsPages(remote = true)

    private fun assertInitializationDrainsPages(remote: Boolean) {
        screenAdCoordinatorOverride = null
        every { MainComponent.eventLogger } returns null
        every { MainComponent.screenAdCoordinator } returns null
        val beforeReport = System.currentTimeMillis()
        AudienzzPrebidMobile.pageImpression("old-page")
        AudienzzPrebidMobile.pageImpression("A")
        val afterReport = System.currentTimeMillis()
        Thread.sleep(10) // Distinguish occurrence time from later initialization/drain time.
        assertTrue(events("before-graph").isEmpty())
        assertEquals(0, coordinator.epoch)
        every { MainComponent.init(any()) } answers {
            every { MainComponent.eventLogger } returns logger
            every { MainComponent.screenAdCoordinator } returns coordinator
        }
        mockkStatic(com.google.android.gms.ads.MobileAds::class)
        every { com.google.android.gms.ads.MobileAds.initialize(any(), any()) } just Runs
        mockkStatic(org.prebid.mobile.PrebidMobile::class)
        every { org.prebid.mobile.PrebidMobile.initializeSdk(any(), any<String>(), any()) } answers {
            // Assert at the real initialization call site, not just by invoking the drain helper.
            assertEquals(2, coordinator.epoch)
            assertEquals("A", logger.capturePageContext().screenName)
            assertEquals(listOf("pageImpression", "pageImpression"), events("during-init").map { it.eventType })
        }
        if (remote) {
            every { MainComponent.remoteConfigManager } returns null // No HTTP in this startup test.
            AudienzzPrebidMobile.initializeRemoteSdk(RuntimeEnvironment.getApplication(), "fixture", null)
        } else {
            AudienzzPrebidMobile.initializeSdk(RuntimeEnvironment.getApplication(), "fixture", sdkInitializationListener = null)
        }
        assertEquals(2, coordinator.epoch)
        assertEquals("A", logger.capturePageContext().screenName)
        val page = requireNotNull(logger.capturePageContext().pageImpressionId)
        load(); ready()
        assertEquals(1, replies.size)
        replies.single()(AudienzzResultCode.NO_BIDS); listener.onAdLoaded()
        val all = events("after-init")
        assertEquals(listOf("pageImpression", "pageImpression", "bidRequest", "bidResponse", "noBid"), all.map { it.eventType })
        assertNotNull(all[0].pageImpressionId)
        assertNotEquals(all[0].pageImpressionId, all[1].pageImpressionId)
        assertTrue(all.take(2).all { java.time.Instant.parse(it.eventTimestamp).toEpochMilli() in beforeReport..afterReport })
        assertTrue(all.drop(1).all { it.pageImpressionId == page })
        AudienzzPrebidMobile.drainPendingPageReports()
        assertEquals(5, events("no-replay").size)
    }

    @Test fun retiredGoogleLoadKeepsReplacementInitialButCompletedLoadsRefresh() {
        load(); ready()
        replies[0](AudienzzResultCode.NO_BIDS)
        assertEquals(1, googleLoads)
        AudienzzPrebidMobile.pageImpression("A")
        assertEquals(1, replies.size) // No overlapping Google request.
        listener.onAdLoaded() // Retired Google completion only unblocks the replacement.
        assertEquals(2, replies.size)
        replies[1](AudienzzResultCode.NO_BIDS); listener.onAdLoaded()
        handler.reloadAd()
        assertEquals(3, replies.size)
        replies[2](AudienzzResultCode.NO_BIDS); listener.onAdLoaded()
        val bids = events("google-retirement").filter { it.eventType == "bidRequest" }
        assertEquals(listOf("false", "false", "true"), bids.map { it.attributes["refresh"] })
        assertEquals(3, googleLoads)
    }

    @Test fun terminalNoFillCountsAsACompletedDeliveryForTheNextRequest() {
        AudienzzPrebidMobile.pageImpression("A")
        load(); ready(); replies[0](AudienzzResultCode.NO_BIDS)
        listener.onAdFailedToLoad(com.google.android.gms.ads.LoadAdError(3, "no fill", "fixture", null, null))
        handler.reloadAd(); assertEquals(2, replies.size)
        replies[1](AudienzzResultCode.NO_BIDS); listener.onAdLoaded()
        assertEquals(listOf("false", "true"), events("no-fill").filter { it.eventType == "bidRequest" }.map { it.attributes["refresh"] })
    }

    @Test fun loggerPreservesSuppliedPageOccurrenceTime() {
        logger.onScreenResumed("early", 1234567890000L)
        assertEquals("2009-02-13T23:31:30.000Z", events("timestamp").single().eventTimestamp)
    }
}
