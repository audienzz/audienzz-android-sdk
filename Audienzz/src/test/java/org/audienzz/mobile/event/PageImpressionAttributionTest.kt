package org.audienzz.mobile.event

import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import io.mockk.*
import kotlinx.coroutines.test.StandardTestDispatcher
import org.audienzz.mobile.AudienzzInterstitialAdUnit
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.AudienzzAdUnit
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.api.rendering.AudienzzInterstitialAdUnit as RenderingInterstitial
import org.audienzz.mobile.api.rendering.listeners.AudienzzInterstitialAdUnitListener
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.audienzz.mobile.refresh.RefreshBlockReason
import org.audienzz.mobile.screen.ScreenAdCoordinator
import org.audienzz.mobile.screen.screenAdCoordinatorOverride
import org.audienzz.mobile.util.AppForegroundMonitor
import org.prebid.mobile.api.rendering.InterstitialAdUnit as PrebidRenderingInterstitial
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.event.entity.*
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.event.network.mapper.EventNetworkMapper
import org.audienzz.mobile.original.AudienzzInterstitialAdHandler
import org.audienzz.mobile.original.callbacks.AudienzzInterstitialAdLoadCallback
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.serialization.json.*

@RunWith(RobolectricTestRunner::class)
class PageImpressionAttributionTest {
    private val dispatcher = StandardTestDispatcher()
    private val sent = mutableListOf<EventNetwork>()
    private lateinit var logger: EventLoggerImpl
    private val json = Json { encodeDefaults = false; explicitNulls = false }

    @Before fun setup() {
        val sender = mockk<EventBatcher>(relaxed = true)
        every { sender.enqueue(capture(sent)) } just Runs
        logger = EventLoggerImpl(sender, EventNetworkMapper(RuntimeEnvironment.getApplication()),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), dispatcher)
        mockkObject(MainComponent.Companion)
        every { MainComponent.eventLogger } returns logger
    }
    @After fun cleanup() { org.audienzz.mobile.screen.screenAdCoordinatorOverride = null; unmockkAll() }

    private fun drain() = dispatcher.scheduler.runCurrent()
    private fun payload(event: EventNetwork): JsonObject = json.encodeToJsonElement(EventNetwork.serializer(), event).jsonObject
    private fun pageId(event: EventNetwork): String = payload(event).getValue("page_impression_id").jsonPrimitive.content

    @Test fun `installed rendering listener recovers banners without a new page`() {
        renderingDismissalPreservesPage(removePublisherListener = false)
    }

    @Test fun `removing publisher rendering listener retains SDK dismissal recovery`() {
        renderingDismissalPreservesPage(removePublisherListener = true)
    }

    private fun renderingDismissalPreservesPage(removePublisherListener: Boolean) {
        val coordinator = ScreenAdCoordinator()
        screenAdCoordinatorOverride = coordinator
        AppForegroundMonitor.resetForTesting()
        AudienzzPrebidMobile.sdkInitializedOverride = true
        val view = mockk<AdManagerAdView>(relaxed = true)
        every { view.isAttachedToWindow } returns true
        every { view.adUnitId } returns "/fixture/banner"
        every { view.responseInfo } returns null
        var googleListener: AdListener = object : AdListener() {}
        every { view.adListener } answers { googleListener }
        every { view.adListener = any() } answers { googleListener = firstArg() }
        val unit = mockk<AudienzzAdUnit>(relaxed = true)
        val replies = mutableListOf<(AudienzzResultCode?) -> Unit>()
        every { unit.fetchDemand(any(), any()) } answers { replies += secondArg<(AudienzzResultCode?) -> Unit>() }
        val handler = AudienzzAdViewHandler(view, unit)
        val requests = mutableListOf<AdManagerAdRequest>()

        // Capture what the real wrapper installs on Prebid, rather than constructing a
        // recovery helper or calling the coordinator directly.
        val prebid = mockk<PrebidRenderingInterstitial>(relaxed = true)
        var installed: InterstitialAdUnitListener? = null
        every { prebid.setInterstitialAdUnitListener(any()) } answers { installed = firstArg() }
        val owner = RenderingInterstitial(prebid, "/fixture/interstitial")
        val publisher = mockk<AudienzzInterstitialAdUnitListener>(relaxed = true)
        owner.setInterstitialAdUnitListener(publisher)
        if (removePublisherListener) owner.setInterstitialAdUnitListener(null)
        try {
            AudienzzPrebidMobile.pageImpression("A")
            val page = requireNotNull(logger.capturePageContext().pageImpressionId)
            handler.setScreen("A")
            handler.load(withLazyLoading = false) { request, _ -> requests += request }
            assertEquals(1, replies.size)
            replies.single()(AudienzzResultCode.NO_BIDS)
            googleListener.onAdLoaded()
            googleListener.onAdImpression()
            val first = requests.single().customTargeting
            val sequence = requireNotNull(first.getString("au_page_seq"))
            val slot = requireNotNull(first.getString("au_slot"))
            assertEquals("0", first.getString("hb_refresh_count"))

            val callback = requireNotNull(installed)
            callback.onAdDisplayed(prebid)
            assertTrue(RefreshBlockReason.INTERSTITIAL in handler.refreshController.blockReasons)
            assertEquals(1, replies.size)
            callback.onAdClosed(prebid)
            callback.onAdClosed(prebid) // duplicate terminal callback cannot recover twice
            assertFalse(RefreshBlockReason.INTERSTITIAL in handler.refreshController.blockReasons)
            assertEquals(2, replies.size)
            replies.last()(AudienzzResultCode.NO_BIDS)
            googleListener.onAdLoaded()
            googleListener.onAdImpression()
            assertEquals(2, requests.size)
            val replacement = requests.last().customTargeting
            assertEquals(sequence, replacement.getString("au_page_seq"))
            assertEquals(slot, replacement.getString("au_slot"))
            assertEquals("1", replacement.getString("hb_refresh_count"))

            drain()
            assertEquals(1, sent.count { it.eventType == "pageImpression" })
            assertEquals(2, sent.count { it.eventType == "bidRequest" })
            assertEquals(2, sent.count { it.eventType == "adImpression" })
            sent.forEach { assertEquals(page, pageId(it)) }
            assertEquals(page, logger.capturePageContext().pageImpressionId)
            assertEquals(1, coordinator.epoch)
            verify(exactly = if (removePublisherListener) 0 else 1) { publisher.onAdDisplayed(owner) }
        } finally {
            owner.destroy(); handler.destroy()
            AudienzzPrebidMobile.pageImpression("cleanup")
            AudienzzPrebidMobile.sdkInitializedOverride = null
            AppForegroundMonitor.resetForTesting()
        }
    }

    @Test fun `every ad event retains the actual page visit through navigation and delayed delivery`() {
        logger.onScreenResumed("A")
        val a = logger.capturePageContext()
        assertNotNull(a.pageImpressionId)
        logger.onScreenResumed("B")
        val b = logger.capturePageContext()
        logger.onScreenResumed("A") // back navigation is a new visit, not the old A id
        val returnedA = logger.capturePageContext()
        assertEquals(3, setOf(a.pageImpressionId, b.pageImpressionId, returnedA.pageImpressionId).size)
        for (type in EventType.entries.filter { it != EventType.PAGE_IMPRESSION }) {
            logger.logEvent(EventDomain(eventType = type, adUnitId = "/fixture", pageContext = a))
        }
        // Delivery has not run yet. Neither navigation nor async enrichment can rewrite the visit.
        assertTrue(sent.isEmpty())
        drain()
        assertEquals(11, sent.size) // three page impressions + all eight ad event types
        assertEquals(listOf(a.pageImpressionId, b.pageImpressionId, returnedA.pageImpressionId), sent.take(3).map(::pageId))
        sent.drop(3).forEach {
            assertEquals(pageId(sent.first()), pageId(it))
            assertEquals("A", it.screenName)
        }
        logger.logEvent(EventDomain(eventType = EventType.BID_REQUEST))
        logger.logEvent(EventDomain(eventType = EventType.BID_REQUEST)) // refresh is not a page visit
        drain()
        assertEquals(listOf(returnedA.pageImpressionId, returnedA.pageImpressionId), sent.takeLast(2).map(::pageId))
    }

    @Test fun `background bridge page report waits for the coordinator transition`() {
        val coordinator = org.audienzz.mobile.screen.ScreenAdCoordinator()
        org.audienzz.mobile.screen.screenAdCoordinatorOverride = coordinator
        try {
            org.audienzz.mobile.AudienzzPrebidMobile.pageImpression("A")
            val first = logger.capturePageContext()
            assertNotNull(first.pageImpressionId)
            val worker = Thread { org.audienzz.mobile.AudienzzPrebidMobile.pageImpression("B") }
            worker.start(); worker.join(2_000)
            assertFalse(worker.isAlive)
            assertEquals(first, logger.capturePageContext())
            assertEquals("A", coordinator.activeScreenName)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals("B", logger.capturePageContext().screenName)
            assertEquals("B", coordinator.activeScreenName)
            assertNotEquals(first.pageImpressionId, logger.capturePageContext().pageImpressionId)
        } finally { org.audienzz.mobile.screen.screenAdCoordinatorOverride = null }
    }

    @Test fun `foreground replacement keeps page attribution and discards the backgrounded auction`() {
        val coordinator = org.audienzz.mobile.screen.ScreenAdCoordinator()
        org.audienzz.mobile.screen.screenAdCoordinatorOverride = coordinator
        org.audienzz.mobile.util.AppForegroundMonitor.resetForTesting()
        org.audienzz.mobile.AudienzzPrebidMobile.sdkInitializedOverride = true
        val view = mockk<com.google.android.gms.ads.admanager.AdManagerAdView>(relaxed = true)
        every { view.isAttachedToWindow } returns true
        var listener: com.google.android.gms.ads.AdListener = object : com.google.android.gms.ads.AdListener() {}
        every { view.adListener } answers { listener }
        every { view.adListener = any() } answers { listener = firstArg() }
        val unit = mockk<org.audienzz.mobile.AudienzzAdUnit>(relaxed = true)
        val replies = mutableListOf<(AudienzzResultCode?) -> Unit>()
        every { unit.fetchDemand(any(), any()) } answers { replies += secondArg<(AudienzzResultCode?) -> Unit>() }
        val handler = org.audienzz.mobile.original.AudienzzAdViewHandler(view, unit)
        val host = mockk<android.app.Activity>(relaxed = true)
        var googleLoads = 0
        try {
            org.audienzz.mobile.AudienzzPrebidMobile.pageImpression("A")
            val page = logger.capturePageContext()
            assertNotNull(page.pageImpressionId)
            handler.setScreen("A")
            handler.load(withLazyLoading = false) { _, _ -> googleLoads++ }
            assertEquals(1, replies.size)
            org.audienzz.mobile.util.AppForegroundMonitor.onActivityStopped(host)
            replies[0](AudienzzResultCode.NO_BIDS)
            assertEquals(0, googleLoads)
            org.audienzz.mobile.util.AppForegroundMonitor.onActivityStarted(host)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertEquals(2, replies.size)
            replies[1](AudienzzResultCode.NO_BIDS)
            assertNotNull(listener)
            listener!!.onAdLoaded(); listener!!.onAdImpression()
            assertEquals(1, googleLoads)
            drain()
            assertEquals(1, sent.count { it.eventType == "pageImpression" })
            val bids = sent.filter { it.eventType == "bidRequest" }
            assertEquals(2, bids.size)
            val auctions = bids.map { payload(it).getValue("attributes").jsonObject.getValue("auction_id").jsonPrimitive.content }
            assertEquals(2, auctions.toSet().size)
            assertEquals(1, sent.count { it.eventType == "bidResponse" })
            assertEquals(1, sent.count { it.eventType == "adImpression" })
            sent.forEach { assertEquals(page.pageImpressionId, pageId(it)) }
            assertEquals(page, logger.capturePageContext())
            assertEquals(1, coordinator.epoch)
        } finally {
            handler.destroy()
            org.audienzz.mobile.AudienzzPrebidMobile.pageImpression("cleanup")
            org.audienzz.mobile.AudienzzPrebidMobile.sdkInitializedOverride = null
            org.audienzz.mobile.screen.screenAdCoordinatorOverride = null
            org.audienzz.mobile.util.AppForegroundMonitor.resetForTesting()
        }
    }

    @Test fun `missing page is not invented and an early load cannot adopt a later screen`() {
        val beforePage = logger.capturePageContext()
        logger.logEvent(EventDomain(eventType = EventType.BID_REQUEST, pageContext = beforePage))
        logger.onScreenResumed("first real visit")
        logger.logEvent(EventDomain(eventType = EventType.AD_IMPRESSION, pageContext = beforePage))
        drain()
        assertEquals(3, sent.size)
        assertFalse(payload(sent.first()).containsKey("page_impression_id"))
        assertFalse(payload(sent.last()).containsKey("page_impression_id"))
        assertTrue(payload(sent[1]).containsKey("page_impression_id"))
    }

    @Test fun `real interstitial callbacks keep prefetch page after navigation and owner reuse`() {
        val unit = mockk<AudienzzInterstitialAdUnit>(relaxed = true)
        val replies = mutableListOf<(AudienzzResultCode?) -> Unit>()
        every { unit.fetchDemand(any(), any()) } answers { replies += secondArg<(AudienzzResultCode?) -> Unit>() }
        val handler = AudienzzInterstitialAdHandler(unit, "/fixture/interstitial")
        val ads = mutableListOf<AdManagerInterstitialAd>()
        fun load() {
            handler.load(adLoadCallback = object : AudienzzInterstitialAdLoadCallback() {}, resultCallback = { _, _, callback ->
                val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
                var delegate: FullScreenContentCallback? = null
                every { ad.fullScreenContentCallback } answers { delegate }
                every { ad.fullScreenContentCallback = any() } answers { delegate = firstArg() }
                ads += ad
                callback.onAdLoaded(ad)
            })
        }
        val coordinator = org.audienzz.mobile.screen.ScreenAdCoordinator()
        org.audienzz.mobile.screen.screenAdCoordinatorOverride = coordinator
        org.audienzz.mobile.AudienzzPrebidMobile.pageImpression("A")
        load()
        org.audienzz.mobile.AudienzzPrebidMobile.pageImpression("B")
        replies.single()(AudienzzResultCode.NO_BIDS)
        assertEquals(1, ads.size)
        ads[0].fullScreenContentCallback!!.onAdShowedFullScreenContent()
        ads[0].fullScreenContentCallback!!.onAdImpression()
        ads[0].fullScreenContentCallback!!.onAdClicked()
        ads[0].fullScreenContentCallback!!.onAdDismissedFullScreenContent()
        assertEquals(2, coordinator.epoch)
        assertEquals("B", coordinator.activeScreen)
        load() // same owner accepts a second request on B
        replies[1](AudienzzResultCode.NO_BIDS)
        ads[1].fullScreenContentCallback!!.onAdImpression()
        drain()
        val pages = sent.filter { it.eventType == "pageImpression" }
        assertEquals(2, pages.size)
        val requests = sent.filter { it.eventType == "bidRequest" }
        val responses = sent.filter { it.eventType == "bidResponse" }
        val noBids = sent.filter { it.eventType == "noBid" }
        val impressions = sent.filter { it.eventType == "adImpression" }
        for (events in listOf(requests, responses, noBids, impressions)) {
            assertEquals(2, events.size)
            assertEquals(pages.map(::pageId), events.map(::pageId))
        }
        assertEquals(pageId(pages[0]), pageId(sent.single { it.eventType == "adClick" }))
        assertNotEquals(pageId(pages[0]), pageId(pages[1]))
    }
}
