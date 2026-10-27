package org.audienzz.mobile.original

import android.app.Activity
import android.os.Looper
import android.view.View
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
}
