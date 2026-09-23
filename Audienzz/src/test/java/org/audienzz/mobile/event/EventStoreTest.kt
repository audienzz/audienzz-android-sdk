package org.audienzz.mobile.event

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.serialization.json.Json
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The outbox is the only thing standing between a foreground crash and a lost event, so the cases
 * that matter are the ugly ones: a process that died mid-write, a backlog that outgrows its cap,
 * and a batch that settled while newer events were already queued behind it.
 */
internal class EventStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        context = mockk()
        every { context.filesDir } returns folder.root
    }

    private fun store(maxLines: Int = 500) =
        EventStore(context, maxLines, Json { ignoreUnknownKeys = true })

    private fun file() = File(File(folder.root, "audienzz"), "events.jsonl")

    private fun event(id: String) = EventNetwork(
        eventType = "adClick",
        companyId = "company",
        source = "android-sdk",
        eventId = id,
        pageImpressionId = "page",
        sessionId = "session",
        sessionStartTimestamp = 1_789_978_756L,
        sessionSeq = 0,
        eventTimestamp = "2026-09-22T00:00:00.000Z",
        locale = "en-US",
        zoneOffsetSeconds = 0,
        screenHeight = 800,
        screenWidth = 400,
        viewportHeight = 800,
        viewportWidth = 400,
        deviceId = null,
        userAgent = null,
        osName = "Android",
        deviceCategory = "Smartphone",
        browserName = "Android WebView",
        sdkName = "android",
        sdkVersion = "0.2.2",
        appPackageName = "org.example",
        appVersion = "1.0",
        appTitle = "Example",
        screenName = "Screen",
        pageUrl = null,
        visitorId = "visitor",
        attributes = mapOf("ad_unit_id" to "/1234/unit"),
    )

    @Test
    fun `an appended event is readable by a fresh store — this is what surviving process death means`() {
        store().append(event("a"))

        // A SEPARATE instance, as the next launch would be: nothing in memory carries over.
        assertEquals(listOf("a"), store().loadAll().map { it.eventId })
    }

    @Test
    fun `events come back in the order they were appended`() {
        val store = store()
        listOf("a", "b", "c").forEach { store.append(event(it)) }

        assertEquals(listOf("a", "b", "c"), store().loadAll().map { it.eventId })
    }

    @Test
    fun `the full payload survives the round trip, not just the id`() {
        store().append(event("a"))

        val restored = store().loadAll().single()
        assertEquals(mapOf("ad_unit_id" to "/1234/unit"), restored.attributes)
        assertEquals(1_789_978_756L, restored.sessionStartTimestamp)
        assertEquals("android-sdk", restored.source)
    }

    @Test
    fun `one event is one line`() {
        val store = store()
        repeat(3) { store.append(event(it.toString())) }

        assertEquals(3, file().readLines().filter { it.isNotBlank() }.size)
    }

    @Test
    fun `a torn last line does not cost the intact events in front of it`() {
        // Exactly what a process killed mid-append leaves behind.
        val store = store()
        store.append(event("a"))
        store.append(event("b"))
        file().appendText("""{"eventType":"adClick","compa""")

        assertEquals(listOf("a", "b"), store().loadAll().map { it.eventId })
    }

    @Test
    fun `removeOldest drops the front, which is the batch that just settled`() {
        val store = store()
        listOf("a", "b", "c").forEach { store.append(event(it)) }

        store.removeOldest(2)

        assertEquals(listOf("c"), store().loadAll().map { it.eventId })
    }

    @Test
    fun `removing a settled batch keeps events appended behind it`() {
        // The producer keeps appending while a batch is in flight; those must not be swept away
        // when it lands.
        val store = store()
        listOf("a", "b").forEach { store.append(event(it)) }
        store.append(event("late"))

        store.removeOldest(2)

        assertEquals(listOf("late"), store().loadAll().map { it.eventId })
    }

    @Test
    fun `removing everything leaves no file behind`() {
        val store = store()
        store.append(event("a"))

        store.removeOldest(1)

        assertTrue("an emptied store should not leave a stale file", !file().exists())
        assertEquals(emptyList<String>(), store().loadAll().map { it.eventId })
    }

    @Test
    fun `the store is capped and drops the oldest when it overflows`() {
        // The channel's own drop-oldest does not cover this: an event dropped from a full channel
        // is never consumed, so nothing would ever remove its line.
        val store = store(maxLines = 3)
        listOf("a", "b", "c", "d", "e").forEach { store.append(event(it)) }

        assertEquals(listOf("c", "d", "e"), store().loadAll().map { it.eventId })
    }

    @Test
    fun `an absent store reads as empty rather than failing`() {
        assertEquals(emptyList<String>(), store().loadAll().map { it.eventId })
        assertEquals(0, store().count())
    }
}
