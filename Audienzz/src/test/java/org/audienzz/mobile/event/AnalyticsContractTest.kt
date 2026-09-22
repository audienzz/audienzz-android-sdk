package org.audienzz.mobile.event

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.audienzz.mobile.event.entity.AdSubtype
import org.audienzz.mobile.event.entity.AdType
import org.audienzz.mobile.event.entity.ApiType
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import org.audienzz.mobile.event.id.AdIdProvider
import org.audienzz.mobile.event.id.AdIdProviderImpl
import org.audienzz.mobile.event.id.CompanyIdProvider
import org.audienzz.mobile.event.preferences.EventPreferences
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.event.network.mapper.EventNetworkMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The analytics contract, asserted on the SERIALIZED payload rather than on the domain object.
 *
 * Every question analytics asked was about what arrives at the collector — a type, a unit, a
 * present-or-absent key. A test that stops at `EventDomain` cannot answer any of them: it would
 * pass with the field renamed, retyped, or dropped by `explicitNulls = false`.
 */
@RunWith(RobolectricTestRunner::class)
class AnalyticsContractTest {

    private val json = Json { encodeDefaults = false; explicitNulls = false }

    private fun serialize(event: EventDomain): JsonObject {
        val context: Context = RuntimeEnvironment.getApplication()
        val network = EventNetworkMapper(context).toNetwork(event)
        return json.encodeToJsonElement(EventNetwork.serializer(), network) as JsonObject
    }

    private fun attributes(payload: JsonObject): Map<String, JsonPrimitive> =
        (payload["attributes"] as JsonObject).mapValues { it.value as JsonPrimitive }

    private fun adEvent(slotReload: Int?) = EventDomain(
        eventType = EventType.BID_RESPONSE,
        adUnitId = "/1234/unit",
        adUnitCode = "placement",
        adType = AdType.BANNER,
        adSubtype = AdSubtype.HTML,
        apiType = ApiType.ORIGINAL,
        auctionId = "auction-1",
        slotReload = slotReload,
        sessionStartTimestamp = 1_789_978_756L,
    )

    // ── slot_reload ─────────────────────────────────────────────────────────

    @Test
    fun `slot_reload serializes as a string, matching the other attributes`() {
        val attributes = attributes(serialize(adEvent(0)))
        val raw = attributes.getValue("slot_reload")
        assertTrue("slot_reload must stay a JSON string", raw.isString)
        assertEquals("0", raw.content)
    }

    @Test
    fun `slot_reload is binary - the emitted value is never above one`() {
        // The counter behind it may keep climbing; what is REPORTED may not.
        for (emitted in listOf(0, 1)) {
            assertEquals(emitted.toString(), attributes(serialize(adEvent(emitted))).getValue("slot_reload").content)
        }
    }

    // ── session_start_timestamp ─────────────────────────────────────────────

    @Test
    fun `session_start_timestamp is Unix seconds, not milliseconds`() {
        // Through the REAL logger, which is what chooses the unit. Asserting the mapper instead
        // only proves the field survives serialization: the mapper passes through whatever it is
        // handed, so a millisecond value would sail past.
        val batcher = mockk<EventBatcher>(relaxed = true)
        val preferences = mockk<EventPreferences>(relaxed = true)
        val companyIdProvider = mockk<CompanyIdProvider>(relaxed = true)
        val adIdProvider = mockk<AdIdProvider>(relaxed = true)
        every { preferences.getVisitorId() } returns "visitor"
        every { companyIdProvider.getCompanyId() } returns "company"
        every { adIdProvider.getAdId() } returns null

        val dispatcher = StandardTestDispatcher()
        val logger = EventLoggerImpl(
            batcher = batcher,
            mapper = EventNetworkMapper(RuntimeEnvironment.getApplication()),
            preferences = preferences,
            adIdProvider = adIdProvider,
            dispatcher = dispatcher,
            companyIdProvider = companyIdProvider,
        )
        // The logger now hands the batcher the finished wire payload, so this captures exactly what
        // would be POSTed rather than a domain object still awaiting mapping.
        val captured = slot<EventNetwork>()
        every { batcher.enqueue(capture(captured)) } returns Unit

        logger.logEvent(EventDomain(eventType = EventType.AD_CLICK, adUnitId = "/1234/unit"))
        dispatcher.scheduler.runCurrent()

        val emitted = json.encodeToJsonElement(
            EventNetwork.serializer(),
            captured.captured,
        ) as JsonObject
        val value = (emitted["session_start_timestamp"] as JsonPrimitive).content.toLong()
        // A seconds value for any plausible date is ~1.7e9; the millisecond form is ~1.7e12.
        assertTrue("expected Unix seconds, got $value", value in 1_000_000_000L..9_999_999_999L)
    }

    @Test
    fun `durations stay in milliseconds - only the absolute timestamp moved`() {
        val event = adEvent(0).copy(timeToRespond = 146L, autorefreshTime = 30_000L)
        val attributes = attributes(serialize(event))
        assertEquals("146", attributes.getValue("time_to_respond").content)
        assertEquals("30000", attributes.getValue("autorefresh_time").content)
    }

    // ── device_id ───────────────────────────────────────────────────────────

    @Test
    fun `device_id is omitted entirely when unavailable`() {
        val payload = serialize(adEvent(0).copy(deviceId = null))
        assertFalse(
            "an absent identity must be an absent key, not an empty or placeholder value",
            payload.containsKey("device_id"),
        )
    }

    @Test
    fun `the all-zero advertising id is not an identity`() {
        assertNull(AdIdProviderImpl.usableAdId("00000000-0000-0000-0000-000000000000"))
        assertNull(AdIdProviderImpl.usableAdId(""))
        assertNull(AdIdProviderImpl.usableAdId(null))
        // ...and a real one survives, normalized.
        assertEquals("ab12cd34-0000-1111-2222-333344445555",
            AdIdProviderImpl.usableAdId("AB12CD34-0000-1111-2222-333344445555"))
    }

    @Test
    fun `limited ad tracking means no identity, not a zeroed one`() {
        mockkStatic(AdvertisingIdClient::class)
        try {
            every { AdvertisingIdClient.getAdvertisingIdInfo(any()) } returns
                AdvertisingIdClient.Info("00000000-0000-0000-0000-000000000000", true)
            assertNull(AdIdProviderImpl(mockk(relaxed = true)).getAdId())
        } finally {
            unmockkStatic(AdvertisingIdClient::class)
        }
    }

    @Test
    fun `a failure to read reports nothing rather than the literal placeholder`() {
        mockkStatic(AdvertisingIdClient::class)
        try {
            every { AdvertisingIdClient.getAdvertisingIdInfo(any()) } throws IllegalStateException("no play services")
            // The provider used to fall back to the literal string "empty", which aggregates,
            // joins and counts in the collector exactly as if it were a device.
            assertNull(AdIdProviderImpl(mockk(relaxed = true)).getAdId())
        } finally {
            unmockkStatic(AdvertisingIdClient::class)
        }
    }

    @Test
    fun `advertising id is re-read, so a revocation stops being reported`() {
        mockkStatic(AdvertisingIdClient::class)
        try {
            every { AdvertisingIdClient.getAdvertisingIdInfo(any()) } returns
                AdvertisingIdClient.Info("ab12cd34-0000-1111-2222-333344445555", false)
            val provider = AdIdProviderImpl(mockk(relaxed = true))
            var clock = 1_000L
            provider.now = { clock }
            assertEquals("ab12cd34-0000-1111-2222-333344445555", provider.getAdId())

            // The user revokes. A provider that pinned the value for the process would keep
            // emitting an identifier the user has withdrawn.
            every { AdvertisingIdClient.getAdvertisingIdInfo(any()) } returns
                AdvertisingIdClient.Info("00000000-0000-0000-0000-000000000000", true)
            clock += 120_000L
            assertNull(provider.getAdId())
        } finally {
            unmockkStatic(AdvertisingIdClient::class)
        }
    }

    // ── noBid ───────────────────────────────────────────────────────────────

    @Test
    fun `a no-bid carries no bidder_code at all`() {
        val noBid = EventDomain(
            eventType = EventType.NO_BID,
            adUnitId = "/1234/unit",
            adUnitCode = "placement",
            adType = AdType.BANNER,
            adSubtype = AdSubtype.HTML,
            apiType = ApiType.ORIGINAL,
            auctionId = "auction-1",
            resultCode = "NO_BIDS",
            slotReload = 1,
        )
        val attributes = attributes(serialize(noBid))
        assertFalse(
            "a no-bid is auction-level: it cannot name a bidder, and must not invent one",
            attributes.containsKey("bidder_code"),
        )
        assertFalse(attributes.containsKey("winner_bidder_code"))
        assertEquals("NO_BIDS", attributes.getValue("result_code").content)
        // It still belongs to its auction and still says whether this was a first load.
        assertEquals("auction-1", attributes.getValue("auction_id").content)
        assertEquals("1", attributes.getValue("slot_reload").content)
    }
}
