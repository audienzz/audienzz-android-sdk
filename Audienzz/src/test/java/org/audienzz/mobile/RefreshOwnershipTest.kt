package org.audienzz.mobile

import org.audienzz.mobile.api.data.AudienzzAdUnitFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.prebid.mobile.PrebidMobile
import org.robolectric.RobolectricTestRunner
import java.util.EnumSet

/**
 * The SDK owns refresh; Prebid must never own a timer.
 *
 * This is the load-bearing invariant of the migration and it is asserted against Prebid's real
 * configuration object rather than our own state. `BidLoader.setupRefreshTimer()` returns without
 * scheduling when `autoRefreshDelay` is 0 — on both its success and its failure path — so keeping
 * that field at 0 is what makes it impossible for a Prebid response to arm a refresh behind the
 * SDK's back. Every hidden-refresh-loop defect in this area traced back to that timer.
 */
@RunWith(RobolectricTestRunner::class)
class RefreshOwnershipTest {

    private fun bannerAdUnit() = AudienzzBannerAdUnit(
        configId = "config",
        width = 320,
        height = 50,
        adUnitFormats = EnumSet.of(AudienzzAdUnitFormat.BANNER),
    )

    /** True when Prebid's loader has a refresh runnable queued on its own handler. */
    private fun org.prebid.mobile.rendering.bidding.loader.BidLoader.hasArmedRefreshTimer(): Boolean {
        val task = javaClass.getDeclaredField("refreshTimerTask").apply { isAccessible = true }.get(this)
        val handler = task.javaClass.getDeclaredField("refreshHandler")
            .apply { isAccessible = true }.get(task) as? android.os.Handler ?: return false
        val runnable = task.javaClass.getDeclaredField("refreshRunnable")
            .apply { isAccessible = true }.get(task) as Runnable
        return handler.hasCallbacks(runnable)
    }

    private fun AudienzzBannerAdUnit.prebidConfiguration(): org.prebid.mobile.configuration.AdUnitConfiguration {
        val prebidAdUnit = generateSequence(this::class.java as Class<*>) { it.superclass }
            .first { klass -> klass.declaredFields.any { it.name == "adUnit" } }
            .getDeclaredField("adUnit")
            .apply { isAccessible = true }
            .get(this) as org.prebid.mobile.AdUnit
        val field = org.prebid.mobile.AdUnit::class.java.getDeclaredField("configuration")
        field.isAccessible = true
        return field.get(prebidAdUnit) as org.prebid.mobile.configuration.AdUnitConfiguration
    }

    /** Prebid's own view of the interval, which is what its timer reads. */
    private fun AudienzzBannerAdUnit.prebidAutoRefreshDelay(): Int = prebidConfiguration().autoRefreshDelay

    @Test
    fun `configuring an interval never reaches Prebid`() {
        val unit = bannerAdUnit()

        unit.setAutoRefreshInterval(30)

        assertEquals(
            "Prebid must have no interval, or its BidLoader can arm a refresh from its own " +
                "response handlers and schedule work the SDK cannot see",
            0,
            unit.prebidAutoRefreshDelay(),
        )
    }

    @Test
    fun `the configured interval is stored by the SDK`() {
        val unit = bannerAdUnit()

        unit.setAutoRefreshInterval(30)

        assertEquals(30_000L, unit.audienzzRefreshIntervalMillis)
    }

    @Test
    fun `an interval of zero disables refresh`() {
        val unit = bannerAdUnit()
        unit.setAutoRefreshInterval(30)

        unit.setAutoRefreshInterval(0)

        assertEquals("0 means no refresh, unchanged for publishers", 0L, unit.audienzzRefreshIntervalMillis)
        assertEquals(0, unit.prebidAutoRefreshDelay())
    }

    @Test
    fun `the interval is clamped to the supported range`() {
        val unit = bannerAdUnit()

        unit.setAutoRefreshInterval(5)

        assertEquals(
            "below Prebid's minimum, clamped the same way it always was",
            PrebidMobile.AUTO_REFRESH_DELAY_MIN.toLong(),
            unit.audienzzRefreshIntervalMillis,
        )
    }

    @Test
    fun `Prebid's real loader arms no timer for a configured banner`() {
        // The end-to-end version of the invariant, using Prebid's installed BidLoader and the
        // configuration our public API actually produces. A review probe demonstrated that a
        // failure response re-arms the timer when Prebid holds an interval; this asserts that for
        // our configuration it cannot, because Prebid holds no interval at all.
        val unit = bannerAdUnit()
        unit.setAutoRefreshInterval(30)
        val configuration = unit.prebidConfiguration()

        val loader = org.prebid.mobile.rendering.bidding.loader.BidLoader(
            configuration,
            io.mockk.mockk(relaxed = true),
        )
        // Deliver a failure through Prebid's own handler, which is where it re-arms.
        org.prebid.mobile.rendering.bidding.loader.BidLoader::class.java
            .getDeclaredMethod("failedToLoadBid", org.prebid.mobile.api.exceptions.AdException::class.java)
            .apply { isAccessible = true }
            .invoke(loader, org.prebid.mobile.api.exceptions.AdException("test", "refresh ownership"))

        assertEquals(
            "Prebid must not have armed a refresh after its own failure handler ran",
            false,
            loader.hasArmedRefreshTimer(),
        )
    }

    @Test
    fun `a bid response cannot re-enable Prebid's refresh`() {
        // The server can change some ad-unit settings through MobileSdkPassThrough. Refresh is not
        // one of them, and this fails if a Prebid upgrade ever adds it — which would silently give
        // the timer back to Prebid.
        val unit = bannerAdUnit()
        unit.setAutoRefreshInterval(30)
        val configuration = unit.prebidConfiguration()

        // Built the way a real bid response builds it, from server-supplied ext JSON.
        val serverExt = org.json.JSONObject(
            """{"prebid":{"passthrough":[{"type":"prebidmobilesdk","adconfiguration":{"ismuted":true,"autorefreshdelay":30000,"autoRefreshDelay":30000}}]}}"""
        )
        org.prebid.mobile.rendering.models.openrtb.bidRequests.MobileSdkPassThrough
            .create(serverExt)
            ?.modifyAdUnitConfiguration(configuration)

        assertEquals(0, configuration.autoRefreshDelay)
    }

    @Test
    fun `an interval above the maximum is clamped down`() {
        val unit = bannerAdUnit()

        unit.setAutoRefreshInterval(600)

        assertEquals(
            PrebidMobile.AUTO_REFRESH_DELAY_MAX.toLong(),
            unit.audienzzRefreshIntervalMillis,
        )
    }
}
