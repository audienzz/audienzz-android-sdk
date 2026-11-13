package org.audienzz.mobile.original

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * A Prebid bid rendered through a GAM creative of another size: the 300x250 bid in a 300x600
 * creative that was left off-centre, and the reverse that was cut off. GAM's view is real; GAM's
 * size choice is modelled by setting the sizes it would have picked.
 */
@RunWith(RobolectricTestRunner::class)
class PrebidCreativeSizerTest {

    private lateinit var adView: AdManagerAdView
    private lateinit var sizer: PrebidCreativeSizer

    /** Markup reads the sizer made, answered by [markupAnswers] in order (null once they run out). */
    private var markupReads = 0
    private var markupAnswers = mutableListOf<AdSize?>()
    private var pendingMarkupAnswer: ((AdSize?) -> Unit)? = null
    private var answerMarkupLater = false

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        adView = AdManagerAdView(activity)
        adView.setAdSizes(AdSize(300, 600), AdSize(300, 250))
        sizer = PrebidCreativeSizer(adView)
        sizer.findSizeInMarkup = { _: View, answer ->
            markupReads++
            if (answerMarkupLater) pendingMarkupAnswer = answer
            else answer(markupAnswers.removeFirstOrNull())
        }
    }

    private fun idle(millis: Long) =
        shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)

    /** GAM served [size] for the current request. */
    private fun googleServes(size: AdSize) {
        adView.setAdSizes(size)
        sizer.onGoogleLoaded()
    }

    private fun assertShows(width: Int, height: Int) {
        assertEquals("${width}x$height", adView.adSize!!.let { "${it.width}x${it.height}" })
    }

    @Test
    fun `a 300x250 bid in a 300x600 creative is shown at 300x250 once both signals arrive`() {
        sizer.beginLoad("300x250")
        sizer.onPrebidRendered()
        googleServes(AdSize(300, 600))
        assertShows(300, 250)
        assertEquals("the app event settled it; no markup read needed", 0, markupReads)
    }

    /** Lays [adView] out in a wrap-content parent, as a publisher's slot does. */
    private fun inWrapContentSlot(): android.widget.FrameLayout {
        val slot = android.widget.FrameLayout(adView.context)
        slot.addView(
            adView,
            android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        return slot
    }

    private fun layOut(slot: View, width: Int, height: Int) {
        // GAM's measure is a stub under Robolectric: model the size it would take from its adSize.
        // The slot first: its own layout places the child at the child's measured size.
        slot.layout(0, 0, width, height)
        adView.layout(0, 0, width, height)
    }

    @Test
    fun `restoring the sizes holds the view at the shown size until the next creative loads`() {
        val slot = inWrapContentSlot()
        sizer.beginLoad("320x50")
        sizer.onPrebidRendered()
        googleServes(AdSize(300, 250))
        layOut(slot, 320, 50)

        sizer.beginLoad(null)
        assertEquals(320, adView.layoutParams.width)
        assertEquals(
            "not GAM's first size (600) while the 50 dp creative still shows",
            50,
            adView.layoutParams.height,
        )

        googleServes(AdSize(300, 250))
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, adView.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, adView.layoutParams.height)
    }

    @Test
    fun `a failed load keeps the hold and a released banner drops it`() {
        val slot = inWrapContentSlot()
        sizer.beginLoad("320x50")
        sizer.onPrebidRendered()
        googleServes(AdSize(300, 250))
        layOut(slot, 320, 50)

        sizer.beginLoad(null)
        // No onGoogleLoaded: the load failed and the previous creative is still the one shown.
        sizer.beginLoad(null)
        assertEquals(50, adView.layoutParams.height)

        sizer.cancel()
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, adView.layoutParams.height)
    }

    @Test
    fun `a view that was never resized is not held`() {
        val slot = inWrapContentSlot()
        googleServes(AdSize(300, 250))
        layOut(slot, 300, 250)
        sizer.beginLoad(null)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, adView.layoutParams.height)
    }

    @Test
    fun `the app event after onAdLoaded resizes too`() {
        sizer.beginLoad("300x250")
        googleServes(AdSize(300, 600))
        sizer.onPrebidRendered()
        assertShows(300, 250)
    }

    @Test
    fun `a 300x600 bid in a 300x250 creative is enlarged, not cut off`() {
        sizer.beginLoad("300x600")
        sizer.onPrebidRendered()
        googleServes(AdSize(300, 250))
        assertShows(300, 600)
    }

    @Test
    fun `the app event before this load completes does not resize the previous creative`() {
        sizer.beginLoad("300x250")
        adView.setAdSizes(AdSize(300, 600))
        sizer.onPrebidRendered()
        assertShows(300, 600)
        assertFalse(sizer.resized)
    }

    @Test
    fun `a creative GAM served itself keeps GAM's size`() {
        sizer.beginLoad("300x250")
        googleServes(AdSize(300, 600))
        idle(2_000)
        assertShows(300, 600)
        assertTrue("it was looked for in the markup and not found", markupReads > 1)
    }

    @Test
    fun `without a Prebid win nothing is resized or read`() {
        sizer.beginLoad(null)
        sizer.onPrebidRendered()
        googleServes(AdSize(300, 600))
        idle(2_000)
        assertShows(300, 600)
        assertEquals(0, markupReads)
    }

    @Test
    fun `without the app event the size is found in the markup, retrying until the creative renders`() {
        markupAnswers = mutableListOf(null, null, AdSize(300, 250))
        sizer.beginLoad("300x250")
        googleServes(AdSize(300, 600))
        assertShows(300, 600)
        idle(sizer.probeRetryMs)
        assertShows(300, 600)
        idle(sizer.probeRetryMs)
        assertShows(300, 250)
        assertEquals(3, markupReads)
        idle(2_000)
        assertEquals("found: no further reads", 3, markupReads)
    }

    @Test
    fun `the markup is not read after the timeout`() {
        sizer.beginLoad("300x250")
        googleServes(AdSize(300, 600))
        idle(10_000)
        val reads = markupReads
        assertEquals(sizer.probeTimeoutMs / sizer.probeRetryMs + 1, reads.toLong())
    }

    @Test
    fun `a late markup answer cannot resize the next load`() {
        answerMarkupLater = true
        sizer.beginLoad("300x250")
        googleServes(AdSize(300, 600))
        val late = pendingMarkupAnswer!!
        sizer.beginLoad(null)
        adView.setAdSizes(AdSize(300, 600))
        late(AdSize(300, 250))
        assertShows(300, 600)
    }

    @Test
    fun `cancel stops a markup read in flight`() {
        answerMarkupLater = true
        sizer.beginLoad("300x250")
        googleServes(AdSize(300, 600))
        sizer.cancel()
        pendingMarkupAnswer!!(AdSize(300, 250))
        assertShows(300, 600)
    }

    @Test
    fun `the next request asks for every configured size again`() {
        // GAM keeps the configured list and serves the first size.
        sizer.beginLoad("300x250")
        sizer.onPrebidRendered()
        sizer.onGoogleLoaded()
        assertEquals(listOf(AdSize(300, 250)), adView.adSizes!!.toList())
        sizer.beginLoad("300x250")
        assertEquals(listOf(AdSize(300, 600), AdSize(300, 250)), adView.adSizes!!.toList())
    }

    @Test
    fun `off leaves every size to GAM`() {
        sizer.enabled = false
        sizer.beginLoad("300x250")
        sizer.onPrebidRendered()
        googleServes(AdSize(300, 600))
        idle(2_000)
        assertShows(300, 600)
        assertEquals(0, markupReads)
    }

    @Test
    fun `the same size is left alone`() {
        sizer.beginLoad("300x250")
        sizer.onPrebidRendered()
        googleServes(AdSize(300, 250))
        assertFalse(sizer.resized)
    }

    @Test
    fun `hb_size is read from Prebid's creative markup, JSON-escaped or not`() {
        val escaped = """"<script>ucTagData.targetingMap = {\"hb_size\":[\"300x250\"],\"hb_pb\":[\"0.50\"]};</script>""""
        assertEquals(AdSize(300, 250), PrebidCreativeSizer.sizeInMarkup(escaped))
        assertEquals(AdSize(320, 50), PrebidCreativeSizer.sizeInMarkup("var t = {'hb_size': ['320x50']};"))
    }

    @Test
    fun `markup without hb_size is not a Prebid creative, inline sizes included`() {
        assertNull(PrebidCreativeSizer.sizeInMarkup("""<div style="width:300px;height:250px;"></div>"""))
        assertNull(PrebidCreativeSizer.sizeInMarkup(null))
        assertNull(PrebidCreativeSizer.sizeInMarkup("null"))
    }

    @Test
    fun `hb_size targeting is parsed leniently`() {
        assertEquals(AdSize(300, 250), PrebidCreativeSizer.parseSize("300X250"))
        assertNull(PrebidCreativeSizer.parseSize("0x250"))
        assertNull(PrebidCreativeSizer.parseSize("fluid"))
    }

    @Test
    fun `the resize is reported once, with the Prebid size, also when found late in the markup`() {
        val reported = mutableListOf<String>()
        sizer.onResized = { reported += "${it.width}x${it.height}" }
        sizer.beginLoad("300x250")
        markupAnswers = mutableListOf(null, AdSize(300, 250))
        googleServes(AdSize(300, 600))
        assertEquals("nothing until the creative is known", emptyList<String>(), reported)
        idle(2_000)
        sizer.onPrebidRendered()
        assertEquals(listOf("300x250"), reported)
    }

    @Test
    fun `a creative kept at GAM's size is not reported as a resize`() {
        val reported = mutableListOf<AdSize>()
        sizer.onResized = { reported += it }
        sizer.beginLoad("300x250")
        googleServes(AdSize(300, 250))
        sizer.onPrebidRendered()
        sizer.beginLoad(null)
        googleServes(AdSize(300, 600))
        idle(2_000)
        assertEquals(emptyList<AdSize>(), reported)
    }
}
