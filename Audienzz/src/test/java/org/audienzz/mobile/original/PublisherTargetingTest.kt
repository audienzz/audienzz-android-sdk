package org.audienzz.mobile.original

import android.view.View
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import io.mockk.*
import org.audienzz.mobile.AudienzzAdUnit
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.screen.ScreenAdCoordinator
import org.audienzz.mobile.screen.screenAdCoordinatorOverride
import org.audienzz.mobile.targeting.AudienzzAdRequestContext
import org.audienzz.mobile.util.AppForegroundMonitor
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Publisher key-values and the SDK's live side by side: the SDK never removes a publisher's, and a
 * publisher can never remove or override the SDK's.
 *
 * Prebid's own keyword pass is the REAL one (`Util.apply`), run twice as consecutive auctions do.
 */
@RunWith(RobolectricTestRunner::class)
class PublisherTargetingTest {

    private val handlers = mutableListOf<AudienzzAdViewHandler>()

    @Before fun setup() {
        AudienzzPrebidMobile.sdkInitializedOverride = true
        AppForegroundMonitor.resetForTesting()
        screenAdCoordinatorOverride = ScreenAdCoordinator().also { it.onScreenResumed("article") }
        AudienzzTargetingParams.clearGlobalTargeting()
    }

    @After fun cleanup() {
        AudienzzTargetingParams.clearGlobalTargeting()
        AudienzzPrebidMobile.sdkInitializedOverride = null
        handlers.forEach { it.destroy() }
        screenAdCoordinatorOverride = null
        AppForegroundMonitor.resetForTesting()
        unmockkAll()
    }

    /** A publisher builder with its own keys — an `hb_` one, and attempts at the SDK's names. */
    private fun publisherBuilder() = AdManagerAdRequest.Builder()
        .addCustomTargeting("category", "sports")
        .addCustomTargeting("hb_custom", "keep")
        .addCustomTargeting("au_slot", "999")
        .addCustomTargeting("hb_refresh_count", "999")
        .addCustomTargeting("au_sdk", "spoofed")

    private fun AdManagerAdRequest.value(key: String): String? = customTargeting.getString(key)

    private fun prebidAuction(request: AdManagerAdRequest, bids: Map<String, String>) =
        org.prebid.mobile.Util.apply(HashMap(bids), request)

    private fun assertContract(sent: AdManagerAdRequest, refresh: Int = 0) {
        assertEquals("publisher per-request key", "sports", sent.value("category"))
        assertEquals("a publisher `hb_` key survives Prebid", "keep", sent.value("hb_custom"))
        assertEquals("publisher global key", "news", sent.value("section"))
        assertNotEquals("a publisher cannot override the SDK's keys", "999", sent.value("au_slot"))
        assertNotNull(sent.value("au_slot"))
        assertEquals(refresh.toString(), sent.value("hb_refresh_count"))
        assertTrue(sent.value("au_sdk").toString(), sent.value("au_sdk")!!.startsWith("android"))
    }

    @Test fun `both sides survive real Prebid auctions`() {
        AudienzzTargetingParams.addGlobalTargeting("section", "news")
        AudienzzTargetingParams.addGlobalTargeting("au_sdk", "spoofed-global")
        val builder = publisherBuilder()
        val request = AudienzzAdRequestContext().buildRequest(builder)
        prebidAuction(request, mapOf("hb_pb" to "1.00", "hb_bidder" to "appnexus"))
        prebidAuction(request, mapOf("hb_pb" to "2.00"))

        assertContract(request)
        assertEquals("2.00", request.value("hb_pb"))
    }

    @Test fun `the publisher's builder is not modified`() {
        AudienzzTargetingParams.addGlobalTargeting("section", "news")
        val builder = publisherBuilder()
        AudienzzAdRequestContext().buildRequest(builder)

        val untouched = builder.build().customTargeting
        assertNull("no global key in the publisher's builder", untouched.getString("section"))
        assertEquals("the publisher's own values are left as they were", "999", untouched.getString("au_slot"))
        assertEquals("spoofed", untouched.getString("au_sdk"))
    }

    @Test fun `global changes reach the next request built from the same builder`() {
        AudienzzTargetingParams.addGlobalTargeting("section", "news")
        val builder = publisherBuilder()
        val context = AudienzzAdRequestContext()
        assertEquals("news", context.buildRequest(builder).value("section"))

        AudienzzTargetingParams.removeGlobalTargeting("section")
        AudienzzTargetingParams.addGlobalTargeting("late", "1")
        val next = context.buildRequest(builder)

        assertNull("a removed global key is no longer sent", next.value("section"))
        assertEquals("1", next.value("late"))
        assertEquals("sports", next.value("category"))
    }

    @Test fun `clearing global targeting cannot remove the sdk keys`() {
        AudienzzTargetingParams.addGlobalTargeting("section", "news")
        AudienzzTargetingParams.clearGlobalTargeting()
        AudienzzTargetingParams.removeGlobalTargeting("au_sdk")
        val request = AudienzzAdRequestContext().buildRequest(AdManagerAdRequest.Builder())
        assertNotNull(request.value("au_sdk"))
        assertNotNull(request.value("au_page_seq"))
        assertNotNull(request.value("hb_refresh_count"))
    }

    @Test fun `a Prebid bid key wins over a publisher key of the same name`() {
        val request = AudienzzAdRequestContext().buildRequest(
            AdManagerAdRequest.Builder().addCustomTargeting("hb_pb", "0.01").addCustomTargeting("category", "sports"),
        )
        prebidAuction(request, mapOf("hb_pb" to "1.00"))
        assertEquals("1.00", request.value("hb_pb"))
        assertEquals("sports", request.value("category"))
    }

    @Test fun `multi-value global keys are encoded exactly as Google encodes them`() {
        AudienzzTargetingParams.addGlobalTargeting("tags", setOf("a", "b"))
        val request = AudienzzAdRequestContext().buildRequest(AdManagerAdRequest.Builder())
        val google = AdManagerAdRequest.Builder().addCustomTargeting("tags", listOf("a", "b")).build()
        assertEquals(
            google.customTargeting.get("tags")?.toString()?.split(",")?.toSet(),
            request.customTargeting.get("tags")?.toString()?.split(",")?.toSet(),
        )
    }

    @Test fun `rewarded and multiformat requests keep both sides too`() {
        AudienzzTargetingParams.addGlobalTargeting("section", "news")
        val builder = publisherBuilder()
        val request = AudienzzAdRequestContext.buildPublisherRequest(builder)
        prebidAuction(request, mapOf("hb_pb" to "1.00"))
        assertEquals("sports", request.value("category"))
        assertEquals("keep", request.value("hb_custom"))
        assertEquals("news", request.value("section"))
        assertTrue(request.value("au_sdk")!!.startsWith("android"))
        assertNull("the builder stays the publisher's", builder.build().customTargeting.getString("section"))
    }

    // region A banner across refreshes: its builder is retained, which is where keys leaked

    private fun banner(): Pair<AudienzzAdViewHandler, MutableList<AdManagerAdRequest>> {
        val view = mockk<AdManagerAdView>(relaxed = true)
        every { view.viewTreeObserver } returns View(RuntimeEnvironment.getApplication()).viewTreeObserver
        every { view.isAttachedToWindow } returns true
        var listener: AdListener = object : AdListener() {}
        every { view.adListener } answers { listener }
        every { view.adListener = any() } answers { listener = firstArg() }
        val unit = mockk<AudienzzAdUnit>(relaxed = true)
        val requests = mutableListOf<AdManagerAdRequest>()
        every { unit.fetchDemand(any(), any()) } answers {
            val request = firstArg<Any>() as AdManagerAdRequest
            prebidAuction(request, mapOf("hb_pb" to "1.00"))
            requests += request
            secondArg<(AudienzzResultCode?) -> Unit>()(AudienzzResultCode.SUCCESS)
            listener.onAdLoaded()
        }
        val handler = AudienzzAdViewHandler(view, unit)
        handler.setScreen("article")
        handlers += handler
        return handler to requests
    }

    @Test fun `a refreshing banner keeps both sides and follows global changes`() {
        AudienzzTargetingParams.addGlobalTargeting("section", "news")
        val builder = publisherBuilder()
        val (handler, requests) = banner()
        handler.load(withLazyLoading = false, gamRequestBuilder = builder) { _, _ -> }
        assertContract(requests.first())

        AudienzzTargetingParams.removeGlobalTargeting("section")
        handler.reloadAd()

        assertEquals(2, requests.size)
        assertNull("a removed global key is not left in the retained builder", requests.last().value("section"))
        assertEquals("sports", requests.last().value("category"))
        assertEquals("keep", requests.last().value("hb_custom"))
        assertEquals("1", requests.last().value("hb_refresh_count"))
        assertNull(builder.build().customTargeting.getString("section"))
    }

    // endregion
}
