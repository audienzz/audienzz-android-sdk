package org.audienzz.mobile.mediation

import com.google.android.gms.ads.admanager.AdManagerAdRequest
import kotlinx.serialization.json.Json
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.AudienzzTargetingParams
import org.audienzz.mobile.api.config.PublisherConfig
import org.audienzz.mobile.targeting.AudienzzAdRequestContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Per-publisher Meta mediation: `au_meta=1` goes on GAM requests only when the app ships the Meta
 * adapter and the publisher config has not switched it off.
 */
@RunWith(RobolectricTestRunner::class)
class MetaMediationTest {

    @Before fun setup() {
        MetaMediation.resetForTest()
        AudienzzTargetingParams.clearGlobalTargeting()
    }

    @After fun cleanup() {
        MetaMediation.resetForTest()
        MetaMediation.applyTargeting()
        AudienzzTargetingParams.clearGlobalTargeting()
    }

    private fun withAdapter(present: Boolean) {
        MetaMediation.adapterLookup = { present }
    }

    private fun requestKey(): String? =
        AudienzzAdRequestContext.buildPublisherRequest(AdManagerAdRequest.Builder())
            .customTargeting
            .getString(MetaMediation.TARGETING_KEY)

    @Test
    fun coreSdkAloneDoesNotShipTheAdapter() {
        // The real classpath check: the core module's tests do not have com.audienzz:sdk-meta.
        assertFalse(MetaMediation.isAdapterPresent)
        MetaMediation.applyTargeting()
        assertNull(requestKey())
    }

    @Test
    fun adapterWithoutBackendValueBids() {
        withAdapter(true)
        AudienzzPrebidMobile.applyBackendMetaMediationConfig(null)
        assertTrue(MetaMediation.isActive)
        assertEquals("1", requestKey())
    }

    @Test
    fun backendSwitchesItOffAndOnAgain() {
        withAdapter(true)
        AudienzzPrebidMobile.applyBackendMetaMediationConfig(false)
        assertNull(requestKey())
        AudienzzPrebidMobile.applyBackendMetaMediationConfig(true)
        assertEquals("1", requestKey())
    }

    @Test
    fun backendCannotTurnItOnWithoutTheAdapter() {
        withAdapter(false)
        AudienzzPrebidMobile.applyBackendMetaMediationConfig(true)
        assertFalse(MetaMediation.isActive)
        assertNull(requestKey())
    }

    @Test
    fun publisherCannotRemoveTheSdkKey() {
        withAdapter(true)
        MetaMediation.applyTargeting()
        AudienzzTargetingParams.removeGlobalTargeting(MetaMediation.TARGETING_KEY)
        AudienzzTargetingParams.clearGlobalTargeting()
        assertEquals("1", requestKey())

        AudienzzPrebidMobile.applyBackendMetaMediationConfig(false)
        val request = AudienzzAdRequestContext.buildPublisherRequest(
            AdManagerAdRequest.Builder().addCustomTargeting(MetaMediation.TARGETING_KEY, "1"),
        )
        // Off means off for the SDK's own key; a publisher who sets it themselves owns that choice.
        assertEquals("1", request.customTargeting.getString(MetaMediation.TARGETING_KEY))
        assertNull(requestKey())
    }

    @Test
    fun adapterLookupRunsOnce() {
        var lookups = 0
        MetaMediation.adapterLookup = {
            lookups++
            true
        }
        repeat(3) { MetaMediation.applyTargeting() }
        assertEquals(1, lookups)
    }

    @Test
    fun publisherConfigCarriesTheSwitchThroughTheCache() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }
        fun parse(extra: String) = json.decodeFromString<PublisherConfig>(
            """{"id":35,"prebidServer":{"url":"https://example.test","accountId":1}$extra}""",
        )
        assertNull(parse("").mediation?.meta?.enabled)
        assertNull(parse(""","mediation":{}""").mediation?.meta?.enabled)
        val off = parse(""","mediation":{"meta":{"enabled":false}}""")
        assertEquals(false, off.mediation?.meta?.enabled)
        val cached = json.decodeFromString<PublisherConfig>(
            json.encodeToString(PublisherConfig.serializer(), off),
        )
        assertEquals(false, cached.mediation?.meta?.enabled)
    }
}
