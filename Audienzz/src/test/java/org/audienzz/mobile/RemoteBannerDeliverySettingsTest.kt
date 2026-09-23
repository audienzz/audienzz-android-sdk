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
 * Lazy loading and the prefetch margin of a remote-config banner are backend-driven only: the ad
 * config's `lazyLoad` and `prefetchDistanceDp`, else the SDK defaults. There is no publisher
 * override, so one placement behaves the same in every app and on every platform.
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

    @Test fun `the ad config reaches the handler`() {
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
