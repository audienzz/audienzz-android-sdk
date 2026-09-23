package org.audienzz.mobile.original

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import io.mockk.*
import org.audienzz.mobile.AudienzzInterstitialAdUnit
import org.audienzz.mobile.AudienzzResultCode
import org.audienzz.mobile.original.callbacks.AudienzzFullScreenContentCallback
import org.audienzz.mobile.original.callbacks.AudienzzInterstitialAdLoadCallback
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InterstitialCallbackOwnershipTest {
    @After fun cleanup() { unmockkAll() }

    private fun load(onLoaded: (AdManagerInterstitialAd) -> Unit,
                     events: AudienzzFullScreenContentCallback): AdManagerInterstitialAd {
        val unit = mockk<AudienzzInterstitialAdUnit>(relaxed = true)
        every { unit.fetchDemand(any(), any()) } answers {
            secondArg<(AudienzzResultCode?) -> Unit>()(AudienzzResultCode.NO_BIDS)
        }
        val ad = mockk<AdManagerInterstitialAd>(relaxed = true)
        var delegate: FullScreenContentCallback? = null
        every { ad.fullScreenContentCallback } answers { delegate }
        every { ad.fullScreenContentCallback = any() } answers { delegate = firstArg() }
        AudienzzInterstitialAdHandler(unit, "/probe").load(
            adLoadCallback = object : AudienzzInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: AdManagerInterstitialAd) { onLoaded(ad) }
            },
            fullScreenContentCallback = events,
            resultCallback = { _, _, listener -> listener.onAdLoaded(ad) },
        )
        return ad
    }

    @Test fun `show from loaded callback already has terminal failure listener`() {
        var failed = 0
        load(onLoaded = { ad ->
            assertNotNull(ad.fullScreenContentCallback)
            ad.fullScreenContentCallback!!.onAdFailedToShowFullScreenContent(AdError(1, "presenter", "google"))
        }, events = object : AudienzzFullScreenContentCallback() {
            override fun onAdFailedToShowFullScreenContent(error: AdError) { failed++ }
        })
        assertEquals(1, failed)
    }

    @Test fun `publisher callback installed inside loaded is preserved without duplication`() {
        var direct = 0
        var fallback = 0
        val ad = load(onLoaded = { loaded ->
            loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { direct++ }
            }
        }, events = object : AudienzzFullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { fallback++ }
        })
        ad.fullScreenContentCallback!!.onAdDismissedFullScreenContent()
        assertEquals(1, direct)
        assertEquals(0, fallback)
    }
}
