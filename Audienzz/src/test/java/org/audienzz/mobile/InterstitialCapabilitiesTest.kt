package org.audienzz.mobile

import android.app.Activity
import android.os.Looper
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.audienzz.mobile.InterstitialCapabilities.Format
import org.audienzz.mobile.api.config.*
import org.audienzz.mobile.di.MainComponent
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.manager.RemoteConfigManager
import org.audienzz.mobile.original.AudienzzInterstitialAdHandler
import org.audienzz.mobile.original.callbacks.*
import org.audienzz.mobile.util.AppForegroundMonitor
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.prebid.mobile.api.data.AdFormat
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Interstitial formats and API frameworks are backend-controlled (`prebidConfig.format`,
 * `prebidConfig.apis`), validated identically on every platform, and resolved per accepted load.
 */
@RunWith(RobolectricTestRunner::class)
class InterstitialCapabilitiesTest {

    // region Validation table (the same rows are asserted by iOS's InterstitialCapabilitiesTests)

    private data class Row(val format: String?, val apis: List<Int>?, val expectFormat: Format, val expectApis: List<Int>)

    private val table = listOf(
        Row(null, null, Format.BANNER_AND_VIDEO, listOf(3, 5, 6, 7)),
        Row("banner", listOf(7), Format.BANNER, listOf(7)),
        Row("video", listOf(3, 7), Format.VIDEO, listOf(3, 7)),
        Row("bannerAndVideo", listOf(5), Format.BANNER_AND_VIDEO, listOf(5)),
        Row("BANNER", null, Format.BANNER_AND_VIDEO, listOf(3, 5, 6, 7)),
        Row("native", listOf(3), Format.BANNER_AND_VIDEO, listOf(3)),
        Row("", emptyList(), Format.BANNER_AND_VIDEO, listOf(3, 5, 6, 7)),
        Row(null, listOf(1, 2, 4), Format.BANNER_AND_VIDEO, listOf(3, 5, 6, 7)),
        Row(null, listOf(7, 3, 7, 99), Format.BANNER_AND_VIDEO, listOf(7, 3)),
        Row(null, listOf(-1, 0), Format.BANNER_AND_VIDEO, listOf(3, 5, 6, 7)),
    )

    @Test fun `the shared validation table`() {
        for (row in table) {
            val caps = InterstitialCapabilities.resolve(row.format, row.apis)
            assertEquals("format for ${row.format}", row.expectFormat, caps.format)
            assertEquals("apis for ${row.apis}", row.expectApis, caps.apis)
        }
    }

    // endregion

    // region Decoding the backend payload

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(prebidConfig: String) = json.decodeFromString(
        RemoteAdUnitConfig.serializer(),
        """{"id":47,"config":{"adType":"interstitial"},
            "gamConfig":{"adUnitPath":"/1/int","adSizes":["320x480"]},
            "prebidConfig":$prebidConfig}""",
    )

    @Test fun `the schema decodes`() {
        val config = decode("""{"placementId":"p","adSizes":["320x480"],"format":"video","apis":[7,3]}""")
        assertEquals("the placement type stays separate from its format", "interstitial", config.config.adType)
        assertEquals(
            InterstitialCapabilities(Format.VIDEO, listOf(7, 3)),
            InterstitialCapabilities.resolve(config.prebidConfig),
        )
    }

    @Test fun `malformed values fall back without failing the config`() {
        val wrongTypes = decode("""{"placementId":"p","adSizes":[],"format":5,"apis":"3,5"}""")
        assertNull(wrongTypes.prebidConfig.format)
        assertNull(wrongTypes.prebidConfig.apis)
        assertEquals(InterstitialCapabilities.DEFAULT, InterstitialCapabilities.resolve(wrongTypes.prebidConfig))

        val mixed = decode("""{"placementId":"p","adSizes":[],"apis":[3,"5",true,6.5,7.0,null]}""")
        assertEquals("only integral numbers survive", listOf(3, 7), mixed.prebidConfig.apis)

        val nulls = decode("""{"placementId":"p","adSizes":[],"format":null,"apis":null}""")
        assertEquals(InterstitialCapabilities.DEFAULT, InterstitialCapabilities.resolve(nulls.prebidConfig))

        val objects = decode("""{"placementId":"p","adSizes":[],"format":["banner"],"apis":{"a":1}}""")
        assertEquals(InterstitialCapabilities.DEFAULT, InterstitialCapabilities.resolve(objects.prebidConfig))
    }

    @Test fun `the values survive the local cache`() {
        val config = decode("""{"placementId":"p","adSizes":[],"format":"banner","apis":[7]}""")
        val cached = json.decodeFromString(RemoteAdUnitConfig.serializer(), json.encodeToString(RemoteAdUnitConfig.serializer(), config))
        assertEquals(InterstitialCapabilities(Format.BANNER, listOf(7)), InterstitialCapabilities.resolve(cached.prebidConfig))
    }

    // endregion

    // region Hand-built interstitials cannot override

    private fun formatsOf(unit: AudienzzInterstitialAdUnit) = unit.adFormats.toSet()
    private fun bannerApi(unit: AudienzzInterstitialAdUnit) = unit.bannerParameters?.api?.map { it.prebidApi.value }
    private fun videoApi(unit: AudienzzInterstitialAdUnit) = unit.videoParameters?.api?.map { it.prebidApi.value }

    /** The handler applies capabilities at the start of every accepted request. */
    private fun request(unit: AudienzzInterstitialAdUnit) {
        AudienzzInterstitialAdHandler(unit, "/gam/int").load(
            adLoadCallback = object : AudienzzInterstitialAdLoadCallback() {},
            resultCallback = { _, _, _ -> },
        )
    }

    @Test fun `with no configuration a hand-built interstitial asks for banner and video with every api`() {
        val unit = AudienzzInterstitialAdUnit("probe")
        request(unit)
        assertEquals("banner and video", setOf(AdFormat.INTERSTITIAL, AdFormat.VAST), formatsOf(unit))
        assertEquals(listOf(3, 5, 6, 7), bannerApi(unit))
        assertEquals(listOf(3, 5, 6, 7), videoApi(unit))
        assertEquals("a video request is always playable", listOf("video/mp4"), unit.videoParameters?.mimes)
        assertEquals(AdSubtype.MULTIFORMAT, unit.getSubType())
    }

    @Test fun `publisher api settings are ignored and other video settings kept`() {
        val unit = AudienzzInterstitialAdUnit("probe")
        unit.bannerParameters = AudienzzBannerParameters().apply { api = listOf(AudienzzSignals.Api.VPAID_1) }
        unit.videoParameters = AudienzzVideoParameters(listOf("video/mp4")).apply {
            api = listOf(AudienzzSignals.Api.VPAID_2)
            maxDuration = 42
        }
        request(unit)
        assertEquals(listOf(3, 5, 6, 7), bannerApi(unit))
        assertEquals(listOf(3, 5, 6, 7), videoApi(unit))
        assertEquals("unrelated video settings are kept", 42, unit.videoParameters?.maxDuration)
    }

    @OptIn(AudienzzBridgeApi::class)
    @Test fun `imp ortb cannot override the api list or add a format`() {
        val unit = AudienzzInterstitialAdUnit("probe")
        unit.impOrtbConfig = """{"banner":{"api":[1],"format":[{"w":320,"h":480}]},"video":{"api":[2]},"ext":{"k":"v"}}"""
        unit.setBackendCapabilities("banner", listOf(7))
        request(unit)

        assertEquals(setOf(AdFormat.INTERSTITIAL), formatsOf(unit))
        assertEquals(listOf(7), bannerApi(unit))
        val imp = JSONObject(unit.impOrtbConfig!!)
        assertFalse(imp.getJSONObject("banner").has("api"))
        assertTrue("sizes sent this way are kept", imp.getJSONObject("banner").has("format"))
        assertFalse("a banner-only interstitial cannot be made to request video", imp.has("video"))
        assertTrue(imp.has("ext"))
        assertEquals(AdSubtype.HTML, unit.getSubType())
    }

    @OptIn(AudienzzBridgeApi::class)
    @Test fun `bridge values are validated like the backend`() {
        val unit = AudienzzInterstitialAdUnit("probe")
        unit.setBackendCapabilities("native", listOf(1, 2))
        request(unit)
        assertEquals(setOf(AdFormat.INTERSTITIAL, AdFormat.VAST), formatsOf(unit))
        assertEquals(listOf(3, 5, 6, 7), bannerApi(unit))
    }

    @Test fun `unparseable imp ortb is dropped`() {
        assertNull(InterstitialCapabilities.sanitizedImpOrtb("{not json", Format.BANNER_AND_VIDEO))
        assertNull(InterstitialCapabilities.sanitizedImpOrtb(null, Format.BANNER_AND_VIDEO))
    }

    // endregion

    // region The remote interstitial: per accepted load, lifecycle preserved

    private lateinit var owner: AudienzzRemoteConfigInterstitial
    private lateinit var loaded: AudienzzInterstitialAdLoadCallback
    private lateinit var fullscreen: AudienzzFullScreenContentCallback
    private val activity = mockk<Activity>(relaxed = true)
    private var backend = RemotePrebidConfig("probe", listOf("320x480"))
    private var loads = 0
    private var configReads = 0
    private val built = mutableListOf<InterstitialCapabilities>()

    private fun remote(format: String?, apis: List<Int>?) = json.decodeFromString(
        RemotePrebidConfig.serializer(),
        buildString {
            append("""{"placementId":"probe","adSizes":["320x480"]""")
            if (format != null) append(""","format":"$format"""")
            if (apis != null) append(""","apis":${apis}""")
            append("}")
        },
    )

    private fun setupRemote() {
        AppForegroundMonitor.resetForTesting()
        val manager = mockk<RemoteConfigManager>()
        coEvery { manager.getAdUnitConfig(any()) } answers {
            configReads++
            RemoteAdUnitConfig(1, RemoteConfig("interstitial"), RemoteGamConfig("/probe", emptyList()), backend)
        }
        mockkObject(MainComponent.Companion)
        every { MainComponent.remoteConfigManager } returns manager
        mockkConstructor(AudienzzInterstitialAdHandler::class)
        every { anyConstructed<AudienzzInterstitialAdHandler>().load(any(), any(), any(), any()) } answers {
            loads++; loaded = secondArg(); fullscreen = thirdArg()
        }
        owner = AudienzzRemoteConfigInterstitial(activity, "probe", mockk(relaxed = true))
        owner.configDispatcher = Dispatchers.Main
        owner.onAdUnitBuilt = { built += it.capabilities }
    }

    @After fun cleanup() {
        // A presentation left open would hold the process-wide presentation slot for every later
        // test; end it the way Google would.
        if (::fullscreen.isInitialized) fullscreen.onAdDismissedFullScreenContent()
        if (::owner.isInitialized) owner.destroy()
        AppForegroundMonitor.resetForTesting()
        unmockkAll()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun prefetch() { owner.prefetch(); idle() }
    private fun deliver() { loaded.onAdLoaded(mockk<AdManagerInterstitialAd>(relaxed = true)); idle() }

    @Test fun `a remote interstitial with no configuration gets the defaults`() {
        setupRemote()
        prefetch()
        assertEquals(listOf(InterstitialCapabilities.DEFAULT), built)
    }

    @Test fun `the backend chooses format and apis`() {
        setupRemote()
        backend = remote("banner", listOf(7))
        prefetch()
        assertEquals(listOf(InterstitialCapabilities(Format.BANNER, listOf(7))), built)
    }

    @Test fun `a config change neither discards ready inventory nor requests`() {
        setupRemote()
        prefetch(); prefetch()
        assertEquals("coalesced prefetches resolve nothing new", 1, built.size)
        deliver()
        assertTrue(owner.isReady)

        backend = remote("video", listOf(7))
        prefetch()

        assertTrue("the ready ad is kept", owner.isReady)
        assertEquals("no request because the config changed", 1, loads)
        assertEquals(1, built.size)
    }

    @Test fun `a config change does not interrupt a presentation`() {
        setupRemote()
        prefetch(); deliver()
        assertTrue(owner.show(activity, eligible = true))
        fullscreen.onAdShowedFullScreenContent()

        backend = remote("video", listOf(7))
        prefetch()

        assertEquals("nothing is requested over a presentation", 1, loads)
        assertEquals(1, built.size)
    }

    @Test fun `the next accepted load uses the new config`() {
        setupRemote()
        prefetch(); deliver()
        assertTrue(owner.show(activity, eligible = true))
        fullscreen.onAdShowedFullScreenContent()
        fullscreen.onAdDismissedFullScreenContent()

        backend = remote("banner", listOf(7))
        prefetch()

        assertEquals(2, loads)
        assertEquals(InterstitialCapabilities(Format.BANNER, listOf(7)), built.last())
    }

    // endregion
}
