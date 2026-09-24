package org.audienzz.mobile.original

import com.google.android.gms.ads.admanager.AdManagerAdRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.api.data.AudienzzBidInfo
import org.audienzz.mobile.api.original.AudienzzPrebidAdUnit
import org.audienzz.mobile.api.original.AudienzzPrebidRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a multiformat caller hands to Google must carry everything: its own keys, global targeting,
 * the SDK's identification and Prebid's bid keys.
 *
 * The handler prepares its own request (the publisher's builder is never modified), so the caller
 * has to load THAT request. Rebuilding from the builder — what the example did — sent Google none of
 * the global, SDK or bid keys.
 */
@RunWith(RobolectricTestRunner::class)
class MultiformatTargetingTest {

    @Before fun setup() = AudienzzTargetingParams.clearGlobalTargeting()

    @After fun cleanup() {
        AudienzzTargetingParams.clearGlobalTargeting()
        unmockkAll()
    }

    @Test fun `the request handed to the caller carries every layer and the bid keys`() {
        AudienzzTargetingParams.addGlobalTargeting("section", "news")
        val unit = mockk<AudienzzPrebidAdUnit>(relaxed = true)
        every { unit.fetchDemand(any(), any(), any()) } answers {
            // What Prebid does: its bid keys go onto the request object it was given.
            org.prebid.mobile.Util.apply(hashMapOf("hb_pb" to "1.00", "hb_bidder" to "appnexus"), firstArg())
            thirdArg<(AudienzzBidInfo) -> Unit>()(mockk(relaxed = true))
        }
        val builder = AdManagerAdRequest.Builder().addCustomTargeting("category", "sports")
        var sent: AdManagerAdRequest? = null

        AudienzzMultiformatAdHandler(unit, "/gam/multi").load(builder, AudienzzPrebidRequest()) { _, request ->
            sent = request
        }

        val targeting = sent!!.customTargeting
        assertEquals("sports", targeting.getString("category"))
        assertEquals("news", targeting.getString("section"))
        assertTrue(targeting.getString("au_sdk")!!.startsWith("android"))
        assertEquals("the bid keys reach Google", "1.00", targeting.getString("hb_pb"))
        assertNull("the publisher's builder stays theirs", builder.build().customTargeting.getString("section"))
    }
}
