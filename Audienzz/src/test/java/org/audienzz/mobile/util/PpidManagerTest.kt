package org.audienzz.mobile.util

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.audienzz.mobile.AudienzzPrebidMobile
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
 * opt-out. Two switches arrive in the publisher config and they mean different things: the master
 * one is a privacy setting and suppresses the publisher's own identifier too, while the automatic
 * one governs only the identifier the SDK would invent.
 *
 * Getting the default wrong is expensive in both directions: defaulting off silently drops every
 * PPID (which is exactly what shipped before, costing frequency capping and cross-session
 * targeting), and ignoring the master switch would keep sending an identifier for a publisher who
 * has turned it off.
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
        AudienzzPrebidMobile.applyBackendPpidConfig(ppidEnabled = null, automaticPpidEnabled = null)
    }

    @After
    fun tearDown() {
        manager.setPublisherPpid(null)
        AudienzzPrebidMobile.applyBackendPpidConfig(ppidEnabled = null, automaticPpidEnabled = null)
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
        // Absent switches mean enabled. This is the default every publisher gets.
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
    fun `the master switch suppresses the generated PPID`() {
        AudienzzPrebidMobile.applyBackendPpidConfig(ppidEnabled = false, automaticPpidEnabled = null)

        assertNull(manager.getPpid())
    }

    @Test
    fun `the master switch suppresses a publisher-supplied PPID too`() {
        // It is a per-publisher privacy switch, so honouring it only for the SDK's own identifier
        // would miss the point entirely.
        manager.setPublisherPpid("hashed-email")
        AudienzzPrebidMobile.applyBackendPpidConfig(ppidEnabled = false, automaticPpidEnabled = null)

        assertNull(manager.getPpid())
    }

    @Test
    fun `the automatic switch suppresses only the generated PPID`() {
        AudienzzPrebidMobile.applyBackendPpidConfig(ppidEnabled = null, automaticPpidEnabled = false)

        assertNull(manager.getPpid())
    }

    @Test
    fun `a publisher-supplied PPID survives the automatic switch`() {
        // The publisher's own identifier is theirs to send; this switch governs only the one the
        // SDK would invent.
        manager.setPublisherPpid("hashed-email")
        AudienzzPrebidMobile.applyBackendPpidConfig(ppidEnabled = null, automaticPpidEnabled = false)

        assertEquals("hashed-email", manager.getPpid())
    }

    @Test
    fun `switches explicitly set to true behave as enabled`() {
        AudienzzPrebidMobile.applyBackendPpidConfig(ppidEnabled = true, automaticPpidEnabled = true)

        assertNotNull(manager.getPpid())
    }
}
