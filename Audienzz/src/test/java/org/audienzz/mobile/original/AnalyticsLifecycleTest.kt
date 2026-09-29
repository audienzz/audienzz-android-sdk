package org.audienzz.mobile.original

import org.audienzz.mobile.AudienzzPrebidMobile
import android.app.Activity
import android.os.Looper
import android.view.ViewTreeObserver
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.admanager.AdManagerAdView
import io.mockk.*
import org.audienzz.mobile.*
import org.audienzz.mobile.screen.*
import org.audienzz.mobile.util.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AnalyticsLifecycleTest {
    private lateinit var handler: AudienzzAdViewHandler
    private lateinit var view: AdManagerAdView
    private lateinit var unit: AudienzzAdUnit
    private lateinit var host: Activity
    private val responses = mutableListOf<(AudienzzResultCode?) -> Unit>()
    private var gamLoads = 0
    private var gamListener: AdListener = object : AdListener() {}
    @Before fun setup() {
        // Robolectric never really initializes Prebid, and an uninitialized Prebid now
        // defers every auction — see AudienzzPrebidMobile.sdkInitializedOverride.
        AudienzzPrebidMobile.sdkInitializedOverride = true
        AppForegroundMonitor.resetForTesting()
        screenAdCoordinatorOverride = ScreenAdCoordinator()
        AudienzzPrebidMobile.observeForegroundRecovery()
        AudienzzPrebidMobile.pageImpression("A")
        host = mockk(relaxed = true)
        view = mockk(relaxed = true)
        every { view.isAttachedToWindow } returns true
        every { view.responseInfo } returns null // Google may omit a response ID; callbacks still dedupe per creative.
        every { view.adListener } answers { gamListener }
        every { view.adListener = any() } answers { gamListener = firstArg() }
        unit = mockk(relaxed = true)
        every { unit.audienzzRefreshIntervalMillis } returns 30_000L
        every { unit.autoRefreshTime } returns 30_000
        every { unit.fetchDemand(any(), any()) } answers { responses.add(secondArg()) }
        handler = AudienzzAdViewHandler(view, unit)
        handler.setScreen("A")
    }
    @After fun cleanup() {
        AudienzzPrebidMobile.sdkInitializedOverride = null
        handler.destroy()
        AudienzzPrebidMobile.pageImpression("cleanup")
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
        unmockkAll()
    }
    private fun load(lazy: Boolean = false) { handler.load(withLazyLoading = lazy) { _, _ -> gamLoads++ } }
    private fun idle(ms: Long) { shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS) }


    private fun capture(): MutableList<org.audienzz.mobile.event.entity.EventDomain> {
        val events = mutableListOf<org.audienzz.mobile.event.entity.EventDomain>()
        val logger = mockk<org.audienzz.mobile.event.EventLogger>(relaxed = true)
        every { logger.logEvent(capture(events)) } just Runs
        mockkObject(org.audienzz.mobile.di.MainComponent.Companion)
        every { org.audienzz.mobile.di.MainComponent.eventLogger } returns logger
        return events
    }

    private fun visible() {
        every { view.visibility } returns android.view.View.VISIBLE
        every { view.measuredHeight } returns 50
        every { view.getGlobalVisibleRect(any()) } answers {
            firstArg<android.graphics.Rect>().set(0, 0, 320, 50); true
        }
    }
    @Test fun duplicateGoogleBannerCallbackIsIgnored() {
        val events = capture(); visible(); load()
        responses.single()(AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded()
        repeat(3) { gamListener.onAdImpression() }
        val impressions = events.filter { it.eventType == org.audienzz.mobile.event.entity.EventType.AD_IMPRESSION }
        assertEquals(1, impressions.size)
        assertEquals(1, impressions.map { it.auctionId }.toSet().size)
    }
    @Test fun firstGoogleFillRetainsFirstLoadFlag() {
        val events = capture(); load()
        responses.single()(AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded(); gamListener.onAdImpression()
        assertEquals(0, events.single { it.eventType == org.audienzz.mobile.event.entity.EventType.BID_REQUEST }.slotReload)
        assertEquals(0, events.single { it.eventType == org.audienzz.mobile.event.entity.EventType.AD_IMPRESSION }.slotReload)
    }
    @Test fun repeatedLoadForSameResponsePreservesMeasurementAndNewResponseRearmsIt() {
        val events = capture(); visible()
        val info = mockk<com.google.android.gms.ads.ResponseInfo>()
        var responseId = "creative-A"
        every { info.responseId } answers { responseId }
        every { info.loadedAdapterResponseInfo } returns null
        every { view.responseInfo } returns info
        load(); responses.single()(AudienzzResultCode.NO_BIDS)
        gamListener.onAdLoaded(); gamListener.onAdImpression(); idle(100)
        gamListener.onAdLoaded(); gamListener.onAdImpression(); idle(900)
        assertEquals(1, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.AD_IMPRESSION })
        assertEquals(1, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.VIEWABILITY_SUCCESS })
        handler.reloadAd(); responses.last()(AudienzzResultCode.NO_BIDS)
        responseId = "creative-B"
        gamListener.onAdLoaded(); gamListener.onAdImpression(); idle(1_000)
        assertEquals(2, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.AD_IMPRESSION })
        assertEquals(2, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.VIEWABILITY_SUCCESS })
    }
    @Test fun pageReleaseCancelsViewabilitySuccessForBlankedAd() {
        val events = capture(); visible(); load()
        responses.single()(AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded(); gamListener.onAdImpression()
        assertEquals(1, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.VIEWABILITY_START })
        idle(100); AudienzzPrebidMobile.pageImpression("B"); idle(1_100)
        assertEquals(0, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.VIEWABILITY_SUCCESS })
    }
    @Test fun replacementMustEarnItsOwnViewabilitySuccess() {
        val events = capture(); visible(); load()
        responses.single()(AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded(); gamListener.onAdImpression()
        val original = events.single { it.eventType == org.audienzz.mobile.event.entity.EventType.AD_IMPRESSION }.auctionId
        idle(100); handler.reloadAd(); responses.last()(AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded()
        assertEquals(2, gamLoads)
        idle(1_100)
        assertEquals(0, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.VIEWABILITY_SUCCESS })
        assertEquals(1, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.AD_IMPRESSION })
        gamListener.onAdImpression(); idle(999)
        assertEquals(0, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.VIEWABILITY_SUCCESS })
        idle(1)
        val success = events.single { it.eventType == org.audienzz.mobile.event.entity.EventType.VIEWABILITY_SUCCESS }
        assertNotNull(original); assertNotNull(success.auctionId); assertNotEquals(original, success.auctionId)
    }
    @Test fun backgroundPreDrawCannotRearmViewabilityTimer() {
        visible()
        var draw: ViewTreeObserver.OnPreDrawListener? = null
        every { view.viewTreeObserver.addOnPreDrawListener(any()) } answers { draw = firstArg() }
        var successes = 0
        val tracker = ViewabilityTracker(view, onStart = {}, onSuccess = { successes++ })
        AppForegroundMonitor.onActivityStarted(host)
        tracker.start(); idle(100)
        AppForegroundMonitor.onActivityStopped(host)
        assertFalse(AppForegroundMonitor.isForeground)
        assertNotNull(draw); draw!!.onPreDraw(); idle(1_100)
        assertEquals(0, successes)
        AppForegroundMonitor.onActivityStarted(host); idle(999)
        assertEquals(0, successes); idle(1); assertEquals(1, successes)
        draw!!.onPreDraw(); idle(2_000); assertEquals(1, successes)
        tracker.stop()
    }
    @Test fun visibleCreativeKeepsItsMeasurementWhileReplacementIsOnlyAnAuction() {
        val events = capture(); visible(); load()
        responses.single()(AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded(); gamListener.onAdImpression()
        val original = events.single { it.eventType == org.audienzz.mobile.event.entity.EventType.AD_IMPRESSION }.auctionId
        assertNotNull(original)
        idle(100); handler.reloadAd()
        assertEquals(2, responses.size)
        assertEquals(1, gamLoads)
        idle(900)
        assertEquals(original, events.single { it.eventType == org.audienzz.mobile.event.entity.EventType.VIEWABILITY_SUCCESS }.auctionId)
        responses.last()(AudienzzResultCode.NO_BIDS); gamListener.onAdLoaded(); gamListener.onAdImpression(); idle(1_000)
        val successes = events.filter { it.eventType == org.audienzz.mobile.event.entity.EventType.VIEWABILITY_SUCCESS }
        assertEquals(2, successes.size)
        assertNotEquals(original, successes.last().auctionId)
    }

    @Test fun duplicateInterstitialImpressionCallbackIsIgnored() {
        val events = capture()
        val interstitialUnit = mockk<AudienzzInterstitialAdUnit>(relaxed = true)
        every { interstitialUnit.fetchDemand(any(), any()) } answers {
            secondArg<(AudienzzResultCode?) -> Unit>()(AudienzzResultCode.NO_BIDS)
        }
        val ad = mockk<com.google.android.gms.ads.admanager.AdManagerInterstitialAd>(relaxed = true)
        var callback: com.google.android.gms.ads.FullScreenContentCallback? = null
        every { ad.fullScreenContentCallback } answers { callback }
        every { ad.fullScreenContentCallback = any() } answers { callback = firstArg() }
        AudienzzInterstitialAdHandler(interstitialUnit, "/probe").load(
            adLoadCallback = object : org.audienzz.mobile.original.callbacks.AudienzzInterstitialAdLoadCallback() {},
            resultCallback = { _, _, listener -> listener.onAdLoaded(ad) })
        assertNotNull(callback)
        repeat(3) { callback!!.onAdImpression() }
        val impressions = events.filter { it.eventType == org.audienzz.mobile.event.entity.EventType.AD_IMPRESSION }
        assertEquals(1, impressions.size)
        assertEquals(1, impressions.map { it.auctionId }.toSet().size)
    }
    @Test fun noBidOncePerAuctionEvenWithDuplicateRepliesAndGoogleImpressions() {
        val events = capture()
        load()
        val codes = listOf(AudienzzResultCode.NO_BIDS, AudienzzResultCode.NETWORK_ERROR,
            AudienzzResultCode.TIMEOUT, AudienzzResultCode.SERVER_ERROR, AudienzzResultCode.SUCCESS)
        codes.forEachIndexed { index, code ->
            assertEquals(index + 1, responses.size)
            repeat(3) { responses[index](code) }
            gamListener.onAdLoaded()
            gamListener.onAdImpression()
            assertEquals(index + 1, gamLoads)
            val requests = events.filter { it.eventType == org.audienzz.mobile.event.entity.EventType.BID_REQUEST }
            val noBids = events.filter { it.eventType == org.audienzz.mobile.event.entity.EventType.NO_BID }
            assertEquals(index + 1, requests.size)
            assertEquals(index + 1, noBids.size)
            assertNotNull(noBids.last().auctionId)
            assertEquals(requests.last().auctionId, noBids.last().auctionId)
            val bidResponses = events.filter { it.eventType == org.audienzz.mobile.event.entity.EventType.BID_RESPONSE }
            assertEquals(index + 1, bidResponses.size)
            assertEquals(requests.last().auctionId, bidResponses.last().auctionId)
            assertEquals(if (code == AudienzzResultCode.SUCCESS) "NO_BIDS" else code.toString(), noBids.last().resultCode)
            idle(29_999)
            assertEquals("No Prebid-driven fast retry", index + 1, responses.size)
            if (index < codes.lastIndex) idle(1)
        }
        assertEquals(5, events.filter { it.eventType == org.audienzz.mobile.event.entity.EventType.NO_BID }.map { it.auctionId }.toSet().size)
    }
    @Test fun rewardedShownInsideLoadedCallbackIsMeasuredOnceAndCannotSucceedAfterDismissal() {
        val events = capture()
        val rewardedUnit = mockk<AudienzzRewardedVideoAdUnit>(relaxed = true)
        every { rewardedUnit.fetchDemand(any(), any()) } answers {
            secondArg<(AudienzzResultCode?) -> Unit>()(AudienzzResultCode.NO_BIDS)
        }
        val ad = mockk<com.google.android.gms.ads.rewarded.RewardedAd>(relaxed = true)
        var callback: com.google.android.gms.ads.FullScreenContentCallback? = null
        every { ad.fullScreenContentCallback } answers { callback }
        every { ad.fullScreenContentCallback = any() } answers { callback = firstArg() }
        AudienzzRewardedVideoAdHandler(rewardedUnit, "/probe").load(
            adLoadCallback = object : org.audienzz.mobile.original.callbacks.AudienzzRewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: com.google.android.gms.ads.rewarded.RewardedAd) {
                    assertNotNull("Analytics must be installed before a publisher can show", callback)
                    repeat(3) { callback!!.onAdShowedFullScreenContent(); callback!!.onAdImpression() }
                }
            }, resultCallback = { _, _, listener -> listener.onAdLoaded(ad) })
        assertEquals(1, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.AD_IMPRESSION })
        assertEquals(1, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.VIEWABILITY_START })
        idle(100); callback!!.onAdDismissedFullScreenContent(); idle(1_100)
        callback!!.onAdShowedFullScreenContent(); callback!!.onAdImpression(); idle(1_100)
        assertEquals(0, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.VIEWABILITY_SUCCESS })
        assertEquals(1, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.AD_IMPRESSION })
        assertEquals(1, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.VIEWABILITY_START })
    }
    @Test fun failedInitializationKeepsGoogleHandoffWithoutSyntheticPrebidEvents() {
        val events = capture()
        val prebid = mockk<org.prebid.mobile.InterstitialAdUnit>(relaxed = true)
        val realUnit = AudienzzInterstitialAdUnit(prebid)
        try {
            AudienzzPrebidMobile.completePrebidInitialization(org.prebid.mobile.api.data.InitializationStatus.FAILED)
            var googleHandoffs = 0
            AudienzzInterstitialAdHandler(realUnit, "/probe/interstitial").load(
                adLoadCallback = object : org.audienzz.mobile.original.callbacks.AudienzzInterstitialAdLoadCallback() {},
                resultCallback = { _, _, _ -> googleHandoffs++ })
            verify(exactly = 0) { prebid.fetchDemand(any<Any>(), any<org.prebid.mobile.OnCompleteListener>()) }
            assertEquals(1, googleHandoffs)
            val noBids = events.filter { it.eventType == org.audienzz.mobile.event.entity.EventType.NO_BID }
            assertEquals(0, noBids.size)
            assertEquals(0, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.BID_REQUEST })
            assertEquals(0, events.count { it.eventType == org.audienzz.mobile.event.entity.EventType.BID_RESPONSE })
        } finally { AudienzzPrebidMobile.completePrebidInitialization(org.prebid.mobile.api.data.InitializationStatus.SUCCEEDED); realUnit.destroy() }
    }
}
