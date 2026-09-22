package org.audienzz.mobile

import android.app.Activity
import android.os.Looper
import io.mockk.*
import org.audienzz.mobile.api.config.*
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.manager.RemoteConfigManager
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Lazy loading and the prefetch margin are delivery decisions a publisher has to be able to make.
 *
 * [AudienzzRemoteBannerView] used to hardcode `withLazyLoading = true` and read the margin only from
 * the ad config, so a publisher whose slot auctioned too late had no lever at all.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteBannerDeliverySettingsTest {

    private lateinit var activity: Activity
    private var loadedLazy: Boolean? = null
    private var loadedMargin: Int? = null

    private fun config(lazyLoad: Boolean? = null, prefetchDp: Int? = null) = RemoteAdUnitConfig(
        1,
        RemoteConfig(
            adType = "banner",
            refreshTimeSeconds = 30,
            prefetchDistanceDp = prefetchDp,
            lazyLoad = lazyLoad,
        ),
        RemoteGamConfig("/1234/unit", listOf("320x50")),
        RemotePrebidConfig("placement", listOf("320x50")),
    )

    private fun seed(config: RemoteAdUnitConfig) {
        val manager = mockk<RemoteConfigManager>()
        coEvery { manager.getAdUnitConfig(any()) } returns config
        mockkObject(MainComponent.Companion)
        every { MainComponent.remoteConfigManager } returns manager
    }

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        loadedLazy = null
        loadedMargin = null
        mockkConstructor(AudienzzAdViewHandler::class)
        every {
            anyConstructed<AudienzzAdViewHandler>().load(any(), any(), any(), any())
        } answers {
            loadedLazy = firstArg()
            loadedMargin = secondArg()
        }
        every { anyConstructed<AudienzzAdViewHandler>().enableSmartRefresh() } just Runs
    }

    @After fun cleanup() = unmockkAll()

    private fun view(adConfigId: String = "remote-banner") =
        AudienzzRemoteBannerView(activity, adConfigId)

    /**
     * The config fetch hops to [Dispatchers.IO] before resuming on the main looper, so idling once
     * is not enough — pump until the handler has been driven, with a bound so a genuine failure
     * fails the assertion rather than hanging.
     */
    private fun pump(maxMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + maxMs
        while (System.currentTimeMillis() < deadline && loadedLazy == null) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun `exhausted slot keeps its current banner on repeat load and late config callback`() {
        seed(config())
        val view = view()
        view.loadAd()
        pump()
        assertNotNull("fixture must have built and loaded a real child", loadedLazy)
        assertEquals(1, view.childCount)
        val current = view.getChildAt(0)
        while (view.requestContext.hasBannerRequestBudget) {
            view.requestContext.buildBannerRequest(com.google.android.gms.ads.admanager.AdManagerAdRequest.Builder())
        }
        view.loadAd()
        assertSame(current, view.getChildAt(0))
        // A config lookup begun before the cap may return afterwards. Drive its actual handoff.
        val callback = AudienzzRemoteBannerView::class.java.getDeclaredMethod("createAdFromConfig", RemoteAdUnitConfig::class.java)
        callback.isAccessible = true
        callback.invoke(view, config())
        assertSame(current, view.getChildAt(0))
        verify(exactly = 0) { anyConstructed<AudienzzAdViewHandler>().destroy() }
        view.destroy()
    }

    // region Resolution precedence

    @Test fun `an ad config that says nothing gets the sdk defaults`() {
        val v = view()
        assertTrue(
            "remote-config banners wait for the viewport unless something asks otherwise",
            v.resolveLazyLoad(config()),
        )
        assertEquals(200, v.resolvePrefetchMarginDp(config()))
    }

    @Test fun `the ad config overrides the sdk defaults`() {
        val v = view()
        assertFalse(v.resolveLazyLoad(config(lazyLoad = false)))
        assertEquals(600, v.resolvePrefetchMarginDp(config(prefetchDp = 600)))
    }

    @Test fun `the publisher overrides the ad config`() {
        val v = view()
        v.lazyLoadOverride = false
        v.prefetchMarginDpOverride = 900
        assertFalse(v.resolveLazyLoad(config(lazyLoad = true)))
        assertEquals(900, v.resolvePrefetchMarginDp(config(prefetchDp = 600)))
    }

    @Test fun `eager loading stays reachable as an explicit choice`() {
        val v = view()
        v.lazyLoadOverride = false
        assertFalse("publisher override", v.resolveLazyLoad(config()))
        v.lazyLoadOverride = null
        assertFalse("backend alone", v.resolveLazyLoad(config(lazyLoad = false)))
    }

    @Test fun `clearing an override restores the ad config value`() {
        val v = view()
        v.lazyLoadOverride = false
        v.prefetchMarginDpOverride = 900
        v.lazyLoadOverride = null
        v.prefetchMarginDpOverride = null
        assertTrue(v.resolveLazyLoad(config(lazyLoad = true)))
        assertEquals(600, v.resolvePrefetchMarginDp(config(prefetchDp = 600)))
    }

    // endregion

    // region The resolved values reach the handler

    @Test fun `an unconfigured placement waits for the viewport`() {
        seed(config())
        view().loadAd()
        pump()
        assertEquals(true, loadedLazy)
        assertEquals(200, loadedMargin)
    }

    @Test fun `an explicitly eager placement reaches the handler eagerly`() {
        seed(config(lazyLoad = false))
        view().loadAd()
        pump()
        assertEquals(false, loadedLazy)
    }

    @Test fun `the publisher override reaches the handler`() {
        seed(config(lazyLoad = false))
        val v = view()
        v.lazyLoadOverride = true
        v.prefetchMarginDpOverride = 750
        v.loadAd()
        pump()
        assertEquals(true, loadedLazy)
        assertEquals(750, loadedMargin)
    }

    @Test fun `the ad config reaches the handler when the publisher says nothing`() {
        seed(config(lazyLoad = true, prefetchDp = 600))
        view().loadAd()
        pump()
        assertEquals(true, loadedLazy)
        assertEquals(600, loadedMargin)
    }

    // endregion

    @Test fun `lazyLoad decodes from the remote payload`() {
        val json = """
            {"id":1,
             "config":{"adType":"banner","lazyLoad":true,"prefetchDistanceDp":600},
             "gamConfig":{"adUnitPath":"/1234/unit","adSizes":["320x50"]},
             "prebidConfig":{"placementId":"placement","adSizes":["320x50"]}}
        """.trimIndent()
        val decoded = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(RemoteAdUnitConfig.serializer(), json)
        assertEquals(true, decoded.config.lazyLoad)
        assertEquals(600, decoded.config.prefetchDistanceDp)
    }
}
