package org.audienzz.mobile

import android.app.Activity
import android.content.Context
import android.widget.FrameLayout
import com.google.android.gms.ads.LoadAdError
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.audienzz.mobile.api.config.RemoteAdUnitConfig
import org.audienzz.mobile.original.AudienzzAdViewHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Gallery (ePaper) ads: slots preloaded by page distance, kept across page transitions. */
@RunWith(RobolectricTestRunner::class)
class AudienzzGalleryAdsTest {

    private lateinit var activity: Activity
    private val configs = mutableMapOf<String, RemoteAdUnitConfig>()
    private val created = mutableListOf<AudienzzRemoteBannerView>()
    private val failures = mutableListOf<String>()
    private val galleries = mutableListOf<AudienzzGalleryAds>()

    private val slots = listOf(
        AudienzzGalleryAdSlot("ad-1", "a", pageIndex = 4),
        AudienzzGalleryAdSlot("ad-2", "b", pageIndex = 12),
    )

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        configs["a"] = config("a")
        configs["b"] = config("b")
    }

    @After fun cleanup() {
        galleries.forEach { it.destroy() }
        unmockkAll()
    }

    private fun config(id: String, preloadDistancePages: String? = null): RemoteAdUnitConfig {
        val distance = preloadDistancePages?.let { ""","preloadDistancePages":$it""" }.orEmpty()
        val json = """{"id":1,"config":{"adType":"banner","refreshTimeSeconds":30$distance},""" +
            """"gamConfig":{"adUnitPath":"/1/$id","adSizes":["300x250","300x600"]},""" +
            """"prebidConfig":{"placementId":"p-$id","adSizes":["300x250"]}}"""
        return Json.decodeFromString(RemoteAdUnitConfig.serializer(), json)
    }

    private fun gallery(gallerySlots: List<AudienzzGalleryAdSlot> = slots): AudienzzGalleryAds =
        AudienzzGalleryAds(
            activity,
            gallerySlots,
            screen = activity,
            configLookup = { id -> configs[id] },
            bannerFactory = { ctx: Context, id: String ->
                AudienzzRemoteBannerView(ctx, id).also { created += it }
            },
        ).also {
            it.listener = object : AudienzzGalleryAds.Listener {
                override fun onAdFailedToLoad(slotKey: String, error: LoadAdError?) {
                    failures += slotKey
                }
            }
            galleries += it
            idle()
        }

    /** Configs resolve on the next main-loop turn. */
    private fun idle() = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    private fun createdConfigs() = created.map {
        AudienzzRemoteBannerView::class.java.getDeclaredField("adConfigId")
            .apply { isAccessible = true }
            .get(it) as String
    }

    private fun screenOf(banner: AudienzzRemoteBannerView): Any? =
        AudienzzRemoteBannerView::class.java.getDeclaredField("pendingScreenKey")
            .apply { isAccessible = true }
            .get(banner)

    // --- config.preloadDistancePages ---

    @Test fun `preloadDistancePages is read leniently`() {
        assertNull(config("a").config.preloadDistancePages)
        assertEquals(3, config("a", "3").config.preloadDistancePages)
        assertEquals(0, config("a", "0").config.preloadDistancePages)
        assertEquals(2, config("a", "2.0").config.preloadDistancePages)
        assertNull(config("a", "-1").config.preloadDistancePages)
        assertNull(config("a", "1.5").config.preloadDistancePages)
        assertNull(config("a", "\"2\"").config.preloadDistancePages)
        assertNull(config("a", "true").config.preloadDistancePages)
        assertNull(config("a", "null").config.preloadDistancePages)
    }

    @Test fun `refreshOnBack is read leniently and off by default`() {
        fun parse(value: String?): Boolean {
            val field = value?.let { ""","refreshOnBack":$it""" }.orEmpty()
            return Json.decodeFromString(
                RemoteAdUnitConfig.serializer(),
                """{"id":1,"config":{"adType":"banner"$field},""" +
                    """"gamConfig":{"adUnitPath":"/1/a","adSizes":["300x250"]},""" +
                    """"prebidConfig":{"placementId":"p","adSizes":["300x250"]}}""",
            ).config.refreshOnBack
        }
        assertFalse(parse(null))
        assertFalse(parse("false"))
        assertTrue(parse("true"))
        assertFalse(parse("\"true\""))
        assertFalse(parse("1"))
        assertFalse(parse("null"))
    }

    // --- Preload distance ---

    @Test fun `nothing is requested before a position or far from the slots`() {
        val gallery = gallery()
        assertTrue(created.isEmpty())
        gallery.updatePosition(0f)
        gallery.updatePosition(1f)
        assertTrue("page 1 is 3 pages from slot 4", created.isEmpty())
    }

    @Test fun `a slot is requested once, within the default distance of 2`() {
        val gallery = gallery()
        gallery.updatePosition(1.9f)
        assertTrue(created.isEmpty())
        gallery.updatePosition(2f)
        assertEquals(listOf("a"), createdConfigs())
        gallery.updatePosition(4f)
        gallery.updatePosition(3f)
        assertEquals("requested once", listOf("a"), createdConfigs())
        gallery.updatePosition(10f)
        assertEquals(listOf("a", "b"), createdConfigs())
    }

    @Test fun `swiping backwards preloads too`() {
        val gallery = gallery()
        gallery.updatePosition(15f)
        assertTrue(created.isEmpty())
        gallery.updatePosition(14f)
        assertEquals(listOf("b"), createdConfigs())
    }

    @Test fun `the backend distance is per placement`() {
        configs["a"] = config("a", "0")
        configs["b"] = config("b", "12")
        val gallery = gallery()
        gallery.updatePosition(0f)
        assertEquals("b preloads on open", listOf("b"), createdConfigs())
        gallery.updatePosition(3.5f)
        assertEquals("a only on its own page", listOf("b"), createdConfigs())
        gallery.updatePosition(4f)
        assertEquals(listOf("b", "a"), createdConfigs())
    }

    // --- Each slot's banner ---

    @Test fun `a slot's banner is in gallery mode and owned by the gallery's screen`() {
        val gallery = gallery()
        gallery.updatePosition(4f)
        val banner = created.single()
        assertTrue(banner.galleryMode)
        assertSame(activity, screenOf(banner))
        assertSame(banner, gallery.adView("ad-1"))
        assertNull(gallery.adView("ad-2"))
    }

    @Test fun `gallery mode loads eagerly and keeps the ad across pages`() {
        mockkConstructor(AudienzzAdViewHandler::class)
        every { anyConstructed<AudienzzAdViewHandler>().load(any(), any(), any(), any()) } just Runs
        every { anyConstructed<AudienzzAdViewHandler>().enableSmartRefresh() } just Runs
        fun build(galleryMode: Boolean) {
            // The ad config says lazy; gallery mode overrides it.
            val lazyConfig = Json.decodeFromString(
                RemoteAdUnitConfig.serializer(),
                """{"id":1,"config":{"adType":"banner","lazyLoad":true},""" +
                    """"gamConfig":{"adUnitPath":"/1/a","adSizes":["300x250"]},""" +
                    """"prebidConfig":{"placementId":"p","adSizes":["300x250"]}}""",
            )
            val banner = AudienzzRemoteBannerView(activity, "a").also { it.galleryMode = galleryMode }
            AudienzzRemoteBannerView::class.java
                .getDeclaredMethod("createAdFromConfig", RemoteAdUnitConfig::class.java)
                .apply { isAccessible = true }
                .invoke(banner, lazyConfig)
        }
        build(galleryMode = true)
        verify { anyConstructed<AudienzzAdViewHandler>().keepsAdAcrossPages = true }
        verify(exactly = 1) {
            anyConstructed<AudienzzAdViewHandler>().load(false, any(), any(), any())
        }
        build(galleryMode = false)
        verify { anyConstructed<AudienzzAdViewHandler>().keepsAdAcrossPages = false }
        verify(exactly = 1) {
            anyConstructed<AudienzzAdViewHandler>().load(true, any(), any(), any())
        }
    }

    @Test fun `refreshOnBack makes a gallery ad reload on return, still preloaded eagerly`() {
        mockkConstructor(AudienzzAdViewHandler::class)
        every { anyConstructed<AudienzzAdViewHandler>().load(any(), any(), any(), any()) } just Runs
        every { anyConstructed<AudienzzAdViewHandler>().enableSmartRefresh() } just Runs
        val refreshing = Json.decodeFromString(
            RemoteAdUnitConfig.serializer(),
            """{"id":1,"config":{"adType":"banner","lazyLoad":true,"refreshOnBack":true},""" +
                """"gamConfig":{"adUnitPath":"/1/a","adSizes":["300x250"]},""" +
                """"prebidConfig":{"placementId":"p","adSizes":["300x250"]}}""",
        )
        val banner = AudienzzRemoteBannerView(activity, "a").also { it.galleryMode = true }
        AudienzzRemoteBannerView::class.java
            .getDeclaredMethod("createAdFromConfig", RemoteAdUnitConfig::class.java)
            .apply { isAccessible = true }
            .invoke(banner, refreshing)
        verify { anyConstructed<AudienzzAdViewHandler>().keepsAdAcrossPages = false }
        verify(exactly = 0) { anyConstructed<AudienzzAdViewHandler>().keepsAdAcrossPages = true }
        verify(exactly = 1) {
            anyConstructed<AudienzzAdViewHandler>().load(false, any(), any(), any())
        }
    }

    // --- Binding ---

    @Test fun `bind moves the ad between page views and reports a missing ad`() {
        val gallery = gallery()
        val first = FrameLayout(activity)
        val second = FrameLayout(activity)
        assertFalse("not in range yet", gallery.bind("ad-1", first))
        gallery.updatePosition(4f)
        assertTrue(gallery.bind("ad-1", first))
        assertSame(first, created.single().parent)
        assertTrue("binding the same page again is harmless", gallery.bind("ad-1", first))
        assertEquals(1, first.childCount)
        assertTrue(gallery.bind("ad-1", second))
        assertSame(second, created.single().parent)
        assertEquals("taken out of the recycled page", 0, first.childCount)
        val params = created.single().layoutParams as FrameLayout.LayoutParams
        assertEquals(android.view.Gravity.CENTER, params.gravity)
    }

    @Test fun `the slot's view is announced when it is created`() {
        val gallery = gallery()
        val announced = mutableListOf<String>()
        gallery.listener = object : AudienzzGalleryAds.Listener {
            override fun onAdViewCreated(slotKey: String) {
                announced += slotKey
                assertTrue("bindable from the callback", gallery.bind(slotKey, FrameLayout(activity)))
            }
        }
        gallery.updatePosition(4f)
        assertTrue("not inside the caller's scroll callback", announced.isEmpty())
        idle()
        assertEquals(listOf("ad-1"), announced)
    }

    // --- Failures and lifetime ---

    @Test fun `a placement without config fails once and is never requested`() {
        val gallery = gallery(listOf(AudienzzGalleryAdSlot("x", "missing", pageIndex = 4)))
        gallery.updatePosition(4f)
        gallery.updatePosition(5f)
        assertTrue(created.isEmpty())
        assertEquals("reported once, to a listener set after construction", listOf("x"), failures)
    }

    @Test fun `a slot waits for its config before it is judged`() {
        val gallery = AudienzzGalleryAds(
            activity,
            slots,
            screen = activity,
            configLookup = { id -> configs[id] },
            bannerFactory = { ctx: Context, id: String ->
                AudienzzRemoteBannerView(ctx, id).also { created += it }
            },
        ).also { galleries += it }
        gallery.updatePosition(4f)
        assertTrue("config not read yet", created.isEmpty())
        idle()
        assertEquals("judged as soon as it is", listOf("a"), createdConfigs())
    }

    @Test fun `a config that arrives late is picked up on the next position`() {
        val gallery = gallery(listOf(AudienzzGalleryAdSlot("x", "late", pageIndex = 4)))
        gallery.updatePosition(4f)
        idle()
        assertTrue(created.isEmpty())
        configs["late"] = config("late")
        gallery.updatePosition(4.2f)
        idle()
        assertEquals("loaded once its config exists", listOf("late"), createdConfigs())
        assertEquals("the miss was still reported once", listOf("x"), failures)
    }

    @Test fun `binding a recycled page takes out the other slot's ad`() {
        val gallery = gallery()
        val page = FrameLayout(activity)
        gallery.updatePosition(4f)
        gallery.updatePosition(12f)
        assertTrue(gallery.bind("ad-1", page))
        assertTrue(gallery.bind("ad-2", page))
        assertEquals("only ad-2 is in the page", 1, page.childCount)
        assertSame(gallery.adView("ad-2"), page.getChildAt(0))
    }

    @Test fun `binding a slot without an ad still clears the recycled page`() {
        val gallery = gallery()
        val page = FrameLayout(activity)
        gallery.updatePosition(4f)
        assertTrue(gallery.bind("ad-1", page))
        assertFalse("ad-2 is not in range yet", gallery.bind("ad-2", page))
        assertEquals("ad-1 must not show as if it were ad-2", 0, page.childCount)
    }

    @Test fun `destroy releases the ads and stops requesting`() {
        val gallery = gallery()
        val page = FrameLayout(activity)
        gallery.updatePosition(4f)
        gallery.bind("ad-1", page)
        gallery.destroy()
        assertEquals(0, page.childCount)
        assertNull(gallery.adView("ad-1"))
        gallery.updatePosition(12f)
        assertEquals(listOf("a"), createdConfigs())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate slot keys are rejected`() {
        gallery(listOf(AudienzzGalleryAdSlot("x", "a", 1), AudienzzGalleryAdSlot("x", "b", 2)))
    }
}
