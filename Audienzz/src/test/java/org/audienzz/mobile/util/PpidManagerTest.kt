package org.audienzz.mobile.util

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import org.audienzz.mobile.AudienzzPrebidMobile
import org.audienzz.mobile.api.config.PublisherConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A PPID is sent unless the backend turns it off for this publisher — there is no app-facing
 * opt-out. One switch decides it: `ppidEnabled`, a top-level boolean on `GET /publishers/{id}`.
 * Absent means enabled. The app only decides *which* identifier is used, by supplying its own
 * through [PpidManager.setPublisherPpid]; with none supplied the SDK generates and persists a UUID.
 *
 * Getting the default wrong is expensive in both directions: defaulting off silently drops every
 * PPID (which is exactly what shipped before, costing frequency capping and cross-session
 * targeting), and ignoring the switch would keep sending an identifier for a publisher who has
 * turned it off.
 */
@RunWith(RobolectricTestRunner::class)
class PpidManagerTest {

    private lateinit var stored: MutableMap<String, Any>
    private lateinit var manager: PpidManager

    @Before
    fun setUp() {
        stored = mutableMapOf()
        manager = PpidManager(fakePreferences())
        manager.setPublisherPpid(null)
        AudienzzPrebidMobile.applyBackendPpidConfig(ppidEnabled = null)
    }

    @After
    fun tearDown() {
        manager.setPublisherPpid(null)
        AudienzzPrebidMobile.applyBackendPpidConfig(ppidEnabled = null)
    }

    private fun fakePreferences(): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val key = slot<String>()
        val stringValue = slot<String>()
        val longValue = slot<Long>()
        every { editor.putString(capture(key), capture(stringValue)) } answers {
            stored[key.captured] = stringValue.captured
            editor
        }
        every { editor.putLong(capture(key), capture(longValue)) } answers {
            stored[key.captured] = longValue.captured
            editor
        }

        val preferences = mockk<SharedPreferences>(relaxed = true)
        every { preferences.edit() } returns editor
        every { preferences.getString(any(), any()) } answers {
            stored[firstArg<String>()] as? String ?: secondArg()
        }
        every { preferences.getLong(any(), any()) } answers {
            stored[firstArg<String>()] as? Long ?: secondArg()
        }
        return preferences
    }

    @Test
    fun `generates and sends a PPID when the backend says nothing`() {
        // An absent switch means enabled. This is the default every publisher gets.
        assertNotNull(manager.getPpid())
    }

    @Test
    fun `keeps the same generated PPID across calls`() {
        val first = manager.getPpid()

        assertEquals(first, manager.getPpid())
    }

    @Test
    fun `a publisher-supplied PPID wins over the generated one`() {
        val generated = manager.getPpid()
        manager.setPublisherPpid("hashed-email")

        assertEquals("hashed-email", manager.getPpid())
        assertEquals("and is not the generated one", false, generated == "hashed-email")
    }

    @Test
    fun `clearing the publisher PPID falls back to the generated one`() {
        val generated = manager.getPpid()
        manager.setPublisherPpid("hashed-email")
        manager.setPublisherPpid(null)

        assertEquals(generated, manager.getPpid())
    }

    @Test
    fun `the switch suppresses the generated PPID`() {
        AudienzzPrebidMobile.applyBackendPpidConfig(ppidEnabled = false)

        assertNull(manager.getPpid())
    }

    @Test
    fun `the switch suppresses a publisher-supplied PPID too`() {
        // It is a per-publisher privacy switch, so honouring it only for the SDK's own identifier
        // would miss the point entirely.
        manager.setPublisherPpid("hashed-email")
        AudienzzPrebidMobile.applyBackendPpidConfig(ppidEnabled = false)

        assertNull(manager.getPpid())
    }

    @Test
    fun `a publisher config carrying automaticPpidEnabled still parses and ignores it`() {
        // `automaticPpidEnabled` is gone from the model. The backend never sent it, but a payload
        // carrying it must still decode — this pins both that unknown keys are tolerated and that
        // nobody reintroduces the field as a second gate.
        val payload = """
            {
              "id": 35,
              "prebidServer": {
                "url": "https://ib.adnxs.com/openrtb2/prebid",
                "accountId": "3927",
                "statusUrl": "https://ib.adnxs.com/status"
              },
              "ppidEnabled": true,
              "automaticPpidEnabled": false
            }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val config = json.decodeFromString(PublisherConfig.serializer(), payload)

        assertEquals(true, config.ppidEnabled)

        // Re-encoding is what discriminates: a model that still carried the field would decode
        // this payload just as happily and write the key straight back out.
        val reencoded = json.encodeToString(PublisherConfig.serializer(), config)
        assertEquals(false, reencoded.contains("automaticPpidEnabled"))
    }

    @Test
    fun `the setter can clear a previously set PPID on a reused builder`() {
        // The request builder is reused across auctions, so a PPID set on an earlier one stays
        // until something overwrites it. GMA annotates the setter non-null, so the SDK clears it
        // reflectively — this pins that the public method still accepts null, which is the whole
        // basis for that approach.
        val method = com.google.android.gms.ads.admanager.AdManagerAdRequest.Builder::class.java
            .getMethod("setPublisherProvidedId", String::class.java)
        val builder = com.google.android.gms.ads.admanager.AdManagerAdRequest.Builder()

        builder.setPublisherProvidedId("previous-identifier")
        method.invoke(builder, null)

        // Reaching here without an exception is the assertion: a throwing setter would mean the
        // SDK cannot clear the identifier and would keep sending it after consent is withdrawn.
        assertNotNull(builder.build())
    }

    @Test
    fun `the switch explicitly set to true behaves as enabled`() {
        AudienzzPrebidMobile.applyBackendPpidConfig(ppidEnabled = true)

        assertNotNull(manager.getPpid())
    }
}
