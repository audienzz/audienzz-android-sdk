package org.audienzz.mobile.event

import android.util.Log
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.audienzz.mobile.util.AppForegroundMonitor
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class EventBatcherTest {
    private lateinit var repository: RemoteEventRepository
    private lateinit var store: EventStore
    private val senders = mutableListOf<EventBatcher>()
    @Before fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        repository = mockk()
        coEvery { repository.submitBatch(any()) } returns Unit
        store = mockk(relaxed = true)
        every { store.loadAll() } returns emptyList()
        every { store.append(any()) } returns EventStore.Admission.STORED
        every { store.acknowledge(any()) } returns true
        every { store.quarantine(any()) } returns true
    }
    @After fun tearDown() {
        senders.forEach { it.cancel(); AppForegroundMonitor.removeListener(it) }
        unmockkStatic(Log::class)
    }
    // runTest drains scheduled work before JUnit @After runs. Cancel even after an assertion
    // fails, otherwise a perpetual HTTP/storage/ack retry makes that cleanup advance forever.
    private fun batcherTest(block: suspend TestScope.() -> Unit) = runTest {
        try { block() } finally { senders.forEach { it.cancel() } }
    }

    private fun sender(scheduler: TestCoroutineScheduler, config: EventBatcher.Config = EventBatcher.Config(), backend: (() -> Int?)? = null) =
        EventBatcher(repository, store, StandardTestDispatcher(scheduler), config, { scheduler.currentTime }, { 1.0 }, backendBatchSize = backend).also { senders.add(it) }

    private fun event(index: Int) = EventNetwork(
        eventType = "adClick",
        companyId = "company",
        source = "android-sdk",
        eventId = index.toString(),
        pageImpressionId = "page",
        sessionId = "session",
        sessionStartTimestamp = 1_789_978_756L,
        sessionSeq = index,
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
        attributes = mapOf("auction_id" to "A"),
    )
    private fun auctionEvent(index: Int, auction: String?) = event(index).copy(
        attributes = auction?.let { mapOf("auction_id" to it) } ?: emptyMap())

    @Test fun `busy auction cannot delay another auction and batches never mix auction ids`() = batcherTest {
        val sender = sender(testScheduler)
        val a = auctionEvent(0, "A"); val b = auctionEvent(1, "B")
        val a2 = auctionEvent(2, "A"); val a3 = auctionEvent(3, "A")
        sender.enqueue(a); sender.enqueue(b); runCurrent()
        advanceTimeBy(1500); sender.enqueue(a2); runCurrent()
        advanceTimeBy(499); runCurrent()
        coVerify(exactly = 0) { repository.submitBatch(any()) }
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(b)) }
        advanceTimeBy(500); sender.enqueue(a3); runCurrent()
        advanceTimeBy(1999); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(any()) }
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(a, a2, a3)) }
        coVerify(exactly = 2) { repository.submitBatch(any()) }
    }

    @Test fun `missing and blank auction ids use a separate debounced bucket`() = batcherTest {
        val sender = sender(testScheduler)
        val page = auctionEvent(0, null).copy(eventType = "pageImpression")
        val blank = auctionEvent(1, " ")
        val ad = auctionEvent(2, "A"); val ad2 = auctionEvent(3, "A")
        sender.enqueue(ad); sender.enqueue(page); sender.enqueue(blank); runCurrent()
        advanceTimeBy(1000); sender.enqueue(ad2); runCurrent()
        advanceTimeBy(1000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(page, blank)) }
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(ad, ad2)) }
        coVerify(exactly = 2) { repository.submitBatch(any()) }
    }

    @Test fun `duplicate enqueue does not extend an auction debounce`() = batcherTest {
        val sender = sender(testScheduler)
        sender.enqueue(event(0)); runCurrent()
        advanceTimeBy(1500); sender.enqueue(event(0)); runCurrent()
        advanceTimeBy(500); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(0))) }
        verify(exactly = 1) { store.append(event(0)) }
    }
    @Test fun `cap counts events per auction instead of across the entire queue`() = batcherTest {
        val sender = sender(testScheduler)
        val a = (0..8).map { auctionEvent(it, "A") }
        val b = (9..17).map { auctionEvent(it, "B") }
        (a + b).forEach(sender::enqueue); runCurrent()
        advanceTimeBy(1999); runCurrent()
        coVerify(exactly = 0) { repository.submitBatch(any()) }
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(a) }
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(b) }
        coVerify(exactly = 2) { repository.submitBatch(any()) }
    }

    @Test fun `continuously full auction yields to an older quiet auction`() = batcherTest {
        val sender = sender(testScheduler)
        (0..9).forEach { sender.enqueue(event(it)) }; runCurrent()
        advanceTimeBy(100); val b = auctionEvent(100, "B"); sender.enqueue(b); runCurrent()
        advanceTimeBy(1800); (10..19).forEach { sender.enqueue(event(it)) }; runCurrent()
        advanceTimeBy(100); runCurrent()
        coVerify(exactly = 2) { repository.submitBatch(any()) }
        advanceTimeBy(1900); (20..29).forEach { sender.enqueue(event(it)) }; runCurrent()
        advanceTimeBy(100); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(b)) }
        coVerify(exactly = 3) { repository.submitBatch(any()) }
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch((20..29).map(::event)) }
    }

    @Test fun `restored backlog regroups by auction and new events still debounce`() = batcherTest {
        val oldA = auctionEvent(100, "A"); val oldB = auctionEvent(101, "B")
        val oldA2 = auctionEvent(102, "A")
        every { store.loadAll() } returns listOf(oldA, oldB, oldA2)
        val done = CompletableDeferred<Unit>()
        coEvery { repository.submitBatch(any()) } coAnswers { done.await() }
        val sender = sender(testScheduler); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(oldA, oldA2)) }
        advanceTimeBy(1900); sender.enqueue(event(0)); runCurrent()
        advanceTimeBy(100); done.complete(Unit); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(oldB)) }
        advanceTimeBy(1999); runCurrent()
        coVerify(exactly = 2) { repository.submitBatch(any()) }
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(0))) }
        coVerify(exactly = 3) { repository.submitBatch(any()) }
    }

    @Test fun `late event during an in flight post gets its own debounce`() = batcherTest {
        val done = CompletableDeferred<Unit>()
        coEvery { repository.submitBatch(any()) } coAnswers { done.await() }
        val sender = sender(testScheduler)
        sender.enqueue(event(0)); runCurrent()
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(0))) }
        advanceTimeBy(2900); sender.enqueue(event(1)); runCurrent()
        advanceTimeBy(100); done.complete(Unit); runCurrent()
        verify(exactly = 1) { store.acknowledge(listOf("0")) }
        advanceTimeBy(1899); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(any()) }
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(1))) }
    }

    @Test fun `background flush only forces events already queued`() = batcherTest {
        val done = CompletableDeferred<Unit>()
        coEvery { repository.submitBatch(any()) } coAnswers { done.await() }
        val sender = sender(testScheduler)
        sender.enqueue(event(0)); sender.onEnterBackground(); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(0))) }
        advanceTimeBy(1900); sender.enqueue(event(1)); sender.onEnterForeground(); runCurrent()
        advanceTimeBy(100); done.complete(Unit); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(any()) }
        advanceTimeBy(1899); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(any()) }
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(1))) }
    }


    @Test fun `persist first and debounce two seconds from latest event in the auction`() = batcherTest {
        val sender = sender(testScheduler)
        sender.enqueue(event(0))
        verify(exactly = 0) { store.append(any()) }
        runCurrent()
        verify(exactly = 1) { store.append(event(0)) }
        advanceTimeBy(1500); sender.enqueue(event(1)); runCurrent()
        coVerify(exactly = 0) { repository.submitBatch(any()) }
        advanceTimeBy(1999); runCurrent()
        coVerify(exactly = 0) { repository.submitBatch(any()) }
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(0), event(1))) }
        verify(exactly = 1) { store.acknowledge(listOf("0", "1")) }
        advanceTimeBy(60_000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(any()) }
    }

    @Test fun `count threshold sends at once but burst drains at most once per two seconds`() = batcherTest {
        val done = CompletableDeferred<Unit>()
        coEvery { repository.submitBatch(any()) } coAnswers { done.await() }
        val sender = sender(testScheduler)
        repeat(25) { sender.enqueue(event(it)) }
        runCurrent()
        coVerify(exactly = 1) { repository.submitBatch((0..9).map(::event)) }
        advanceTimeBy(1000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(any()) }
        done.complete(Unit); runCurrent()
        advanceTimeBy(999); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(any()) }
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch((10..19).map(::event)) }
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch((20..24).map(::event)) }
        verify(exactly = 3) { store.acknowledge(any()) }
    }

    @Test fun backendCannotExceedFifteen() = batcherTest {
        val sender = sender(testScheduler, backend = { 100 })
        repeat(14) { sender.enqueue(event(it)) }; runCurrent()
        coVerify(exactly = 0) { repository.submitBatch(any()) }
        sender.enqueue(event(14)); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch((0..14).map(::event)) }
    }

    @Test fun backendReductionSplitsRetryWithoutLosingEvents() = batcherTest {
        var limit: Int? = 10
        val done = CompletableDeferred<Unit>()
        coEvery { repository.submitBatch(any()) } coAnswers { done.await(); throw IOException("offline") }
        val sender = sender(testScheduler, backend = { limit })
        repeat(10) { sender.enqueue(event(it)) }; runCurrent()
        coVerify(exactly = 1) { repository.submitBatch((0..9).map(::event)) }
        limit = 3
        done.complete(Unit); runCurrent()
        coEvery { repository.submitBatch(any()) } returns Unit
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch((0..2).map(::event)) }
        verify(exactly = 1) { store.acknowledge(listOf("0", "1", "2")) }
        advanceTimeBy(6000); runCurrent()
        val sent = mutableListOf<List<EventNetwork>>()
        coVerify(exactly = 5) { repository.submitBatch(capture(sent)) }
        assertEquals((0..9).map(::event), sent.drop(1).flatten())
        assertTrue(sent.drop(1).all { it.size <= 3 })
    }

    @Test fun removingBackendFieldRestoresDefaultTen() = batcherTest {
        var limit: Int? = 15
        val sender = sender(testScheduler, backend = { limit })
        repeat(9) { sender.enqueue(event(it)) }; runCurrent()
        advanceTimeBy(1000); limit = null
        sender.enqueue(event(9)); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch((0..9).map(::event)) }
    }

    @Test fun `byte threshold respects serialized UTF8 size and quarantines oversized singleton`() = batcherTest {
        val sender = sender(testScheduler, EventBatcher.Config(batchBytes = 2000))
        val large = event(0).copy(attributes = mapOf("value" to "ж".repeat(1000)))
        sender.enqueue(large); sender.enqueue(event(1)); sender.enqueue(event(2)); runCurrent()
        advanceTimeBy(5000); runCurrent()
        verify(exactly = 1) { store.quarantine("0") }
        coVerify(exactly = 0) { repository.submitBatch(match { large in it }) }
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(1), event(2))) }
    }

    @Test fun `retry after overrides backoff and flush enqueue cannot bypass it`() = batcherTest {
        val error = retrofit2.HttpException(retrofit2.Response.error<Unit>(
            okhttp3.ResponseBody.create(null, ""), okhttp3.Response.Builder()
                .request(okhttp3.Request.Builder().url("https://example.invalid").build())
                .protocol(okhttp3.Protocol.HTTP_1_1).code(429).message("limited")
                .header("Retry-After", "30").build()))
        coEvery { repository.submitBatch(any()) } throws error
        val sender = sender(testScheduler)
        sender.enqueue(event(0)); sender.flush(); runCurrent()
        sender.enqueue(event(1)); repeat(20) { sender.flush() }; runCurrent()
        advanceTimeBy(29_999); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(any()) }
        verify(exactly = 0) { store.acknowledge(any()) }
        coEvery { repository.submitBatch(any()) } returns Unit
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 2) { repository.submitBatch(listOf(event(0))) }
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(1))) }
    }

    @Test fun `failure retries indefinitely with capped backoff and original payload`() = batcherTest {
        coEvery { repository.submitBatch(any()) } throws IOException("offline")
        val sender = sender(testScheduler)
        sender.enqueue(event(0)); sender.flush(); runCurrent()
        var attempts = 1
        for (delay in listOf(2000L, 4000L, 8000L, 16000L, 32000L, 60000L, 60000L)) {
            advanceTimeBy(delay - 1); runCurrent()
            coVerify(exactly = attempts) { repository.submitBatch(any()) }
            advanceTimeBy(1); runCurrent(); attempts++
            coVerify(exactly = attempts) { repository.submitBatch(listOf(event(0))) }
        }
        verify(exactly = 0) { store.acknowledge(any()) }
        sender.cancel()
    }

    @Test fun `restart replays unacknowledged batch before newer events`() = batcherTest {
        every { store.loadAll() } returns listOf(event(100), event(101))
        val sender = sender(testScheduler)
        sender.enqueue(event(0)); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(100), event(101))) }
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(0))) }
    }

    @Test fun `actual journal replays a lost acknowledgement after process restart`() = batcherTest {
        val directory = java.nio.file.Files.createTempDirectory("batch-restart").toFile()
        val context = mockk<android.content.Context>()
        every { context.filesDir } returns directory
        store = EventStore(context)
        val hung = CompletableDeferred<Unit>()
        coEvery { repository.submitBatch(any()) } coAnswers { hung.await() }
        val first = sender(testScheduler)
        try {
            first.enqueue(event(0)); first.enqueue(event(1)); first.flush(); runCurrent()
            coVerify(exactly = 1) { repository.submitBatch(listOf(event(0), event(1))) }
            first.cancel(); runCurrent()
            store = EventStore(context) // Fresh cache, as after process death.
            coEvery { repository.submitBatch(any()) } returns Unit
            val second = sender(testScheduler)
            runCurrent()
            coVerify(exactly = 2) { repository.submitBatch(listOf(event(0), event(1))) }
            assertTrue(EventStore(context).loadAll().isEmpty())
            second.cancel()
        } finally { first.cancel(); directory.deleteRecursively() }
    }

    @Test fun `September 25 backlog keeps its timestamp and does not return after acknowledgement`() = batcherTest {
        val directory = java.nio.file.Files.createTempDirectory("batch-old-events").toFile()
        val context = mockk<android.content.Context>()
        every { context.filesDir } returns directory
        val old = event(25).copy(eventTimestamp = "2026-09-25T12:00:00.000Z")
        try {
            assertEquals(EventStore.Admission.STORED, EventStore(context).append(old))
            store = EventStore(context)
            val first = sender(testScheduler); runCurrent()
            coVerify(exactly = 1) { repository.submitBatch(listOf(old)) }
            assertTrue(EventStore(context).loadAll().isEmpty())
            first.cancel(); runCurrent()
            store = EventStore(context)
            val restarted = sender(testScheduler); runCurrent()
            advanceTimeBy(86_400_000); runCurrent()
            coVerify(exactly = 1) { repository.submitBatch(any()) }
            restarted.cancel()
        } finally { directory.deleteRecursively() }
    }

    @Test fun `unwritable store retains event in memory and retries persistence before HTTP`() = batcherTest {
        every { store.append(any()) } returns EventStore.Admission.IO_ERROR
        val sender = sender(testScheduler)
        sender.enqueue(event(0)); sender.flush(); runCurrent()
        coVerify(exactly = 0) { repository.submitBatch(any()) }
        every { store.append(any()) } returns EventStore.Admission.STORED
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(0))) }
    }

    @Test fun `failed ack write retries locally while another auction proceeds`() = batcherTest {
        val ackAttempts = mutableListOf<Long>()
        every { store.acknowledge(any()) } answers { ackAttempts.add(testScheduler.currentTime); false }
        val sender = sender(testScheduler)
        sender.enqueue(event(0)); sender.flush(); runCurrent()
        val expectedAttempts = mutableListOf(0L)
        assertEquals(expectedAttempts, ackAttempts)
        val b = auctionEvent(1, "B")
        sender.enqueue(b); runCurrent()
        assertEquals(expectedAttempts, ackAttempts)
        coVerify(exactly = 1) { repository.submitBatch(any()) }
        // Count actual writes before and at every deadline, including repeated capped retries.
        // Flush/wake must not create extra attempts between those deadlines.
        val delays = listOf(2000L, 4000L, 8000L, 16000L, 32000L) + List(8) { 60_000L }
        for (delay in delays) {
            advanceTimeBy(delay - 1); sender.wake(); sender.flush(); runCurrent()
            assertEquals(expectedAttempts, ackAttempts)
            coVerify(exactly = if (expectedAttempts.size == 1) 1 else 2) { repository.submitBatch(any()) }
            advanceTimeBy(1); runCurrent()
            expectedAttempts.add(expectedAttempts.last() + delay)
            assertEquals(expectedAttempts, ackAttempts)
            coVerify(exactly = 1) { repository.submitBatch(listOf(event(0))) }
            coVerify(exactly = 1) { repository.submitBatch(listOf(b)) }
            coVerify(exactly = 2) { repository.submitBatch(any()) }
        }
        advanceTimeBy(600_000 - testScheduler.currentTime); runCurrent()
        assertEquals(expectedAttempts, ackAttempts)
        val acknowledged = mutableListOf<List<String>>()
        every { store.acknowledge(capture(acknowledged)) } returns true
        advanceTimeBy(60_000); runCurrent()
        assertEquals(listOf(listOf("0", "1")), acknowledged)
        // Nothing is resent after either local acknowledgement eventually succeeds.
        advanceTimeBy(60_000); runCurrent()
        coVerify(exactly = 2) { repository.submitBatch(any()) }
    }

    @Test fun `accepted batch replays after process death before local acknowledgement`() = batcherTest {
        val directory = java.nio.file.Files.createTempDirectory("accepted-restart").toFile()
        val context = mockk<android.content.Context>()
        every { context.filesDir } returns directory
        val parent = java.io.File(directory, "audienzz")
        val backup = java.io.File(directory, "saved")
        store = EventStore(context)
        val done = CompletableDeferred<Unit>()
        coEvery { repository.submitBatch(any()) } coAnswers { done.await() }
        val first = sender(testScheduler)
        try {
            first.enqueue(event(0)); first.flush(); runCurrent()
            assertTrue(parent.renameTo(backup)); parent.writeText("unwritable")
            done.complete(Unit); runCurrent() // HTTP succeeded, durable local ack fails.
            coVerify(exactly = 1) { repository.submitBatch(listOf(event(0))) }
            first.cancel(); runCurrent()
            assertTrue(parent.delete()); assertTrue(backup.renameTo(parent))
            store = EventStore(context)
            assertEquals(listOf(event(0)), store.loadAll())
            coEvery { repository.submitBatch(any()) } returns Unit
            val second = sender(testScheduler); runCurrent()
            coVerify(exactly = 2) { repository.submitBatch(listOf(event(0))) }
            assertTrue(EventStore(context).loadAll().isEmpty())
            second.cancel()
        } finally { first.cancel(); directory.deleteRecursively() }
    }

    @Test fun `late same auction event does not ride along with a scheduled flush`() = batcherTest {
        val sender = sender(testScheduler)
        val b = auctionEvent(0, "B"); val a1 = auctionEvent(1, "A"); val a2 = auctionEvent(2, "A")
        sender.enqueue(b); runCurrent(); advanceTimeBy(2000); runCurrent()
        advanceTimeBy(100); sender.enqueue(a1); sender.flush(); runCurrent()
        advanceTimeBy(400); sender.enqueue(a2); runCurrent()
        advanceTimeBy(1500); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(a1)) }
        coVerify(exactly = 2) { repository.submitBatch(any()) }
        advanceTimeBy(1999); runCurrent()
        coVerify(exactly = 2) { repository.submitBatch(any()) }
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(a2)) }
    }

    @Test fun `restored and fresh auctions both progress without waiting for the whole backlog`() = batcherTest {
        val backlog = (100..159).map { auctionEvent(it, "old-$it") }
        every { store.loadAll() } returns backlog
        val sender = sender(testScheduler); runCurrent()
        val fresh = (0..9).map { auctionEvent(it, "fresh-$it") }
        fresh.forEach(sender::enqueue); runCurrent()
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(fresh[0])) }
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(backlog[1])) }
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(fresh[1])) }
        advanceTimeBy(140_000); runCurrent()
        val sent = mutableListOf<List<EventNetwork>>()
        coVerify(exactly = 70) { repository.submitBatch(capture(sent)) }
        assertEquals((backlog + fresh).map { it.eventId }.toSet(), sent.flatten().map { it.eventId }.toSet())
    }

    @Test fun `new events in the same restored auction get their own fair turn`() = batcherTest {
        every { store.loadAll() } returns (100..159).map(::event)
        val sender = sender(testScheduler); runCurrent()
        sender.enqueue(event(0)); runCurrent()
        advanceTimeBy(1999); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(any()) }
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch(listOf(event(0))) }
        advanceTimeBy(2000); runCurrent()
        coVerify(exactly = 1) { repository.submitBatch((110..119).map(::event)) }
    }

    @Test fun `invalid batch is split and rejected singleton quarantined not deleted`() = batcherTest {
        val bad = retrofit2.HttpException(retrofit2.Response.error<Unit>(422, okhttp3.ResponseBody.create(null, "")))
        coEvery { repository.submitBatch(any()) } coAnswers {
            if (firstArg<List<EventNetwork>>().any { it.eventId == "1" }) throw bad
        }
        val sender = sender(testScheduler)
        repeat(4) { sender.enqueue(event(it)) }; sender.flush(); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        verify(exactly = 1) { store.quarantine("1") }
        verify(exactly = 0) { store.acknowledge(match { "1" in it }) }
        verify(exactly = 1) { store.acknowledge(listOf("0")) }
        verify(exactly = 1) { store.acknowledge(listOf("2", "3")) }
        coVerify(exactly = 5) { repository.submitBatch(any()) }
    }

    @Test fun `full store does not evict existing or send unpersisted event`() = batcherTest {
        every { store.append(any()) } returns EventStore.Admission.FULL
        val sender = sender(testScheduler)
        sender.enqueue(event(0)); sender.flush(); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        coVerify(exactly = 0) { repository.submitBatch(any()) }
        verify(exactly = 0) { store.acknowledge(any()) }
    }

    @Test fun `Retry After accepts seconds and HTTP date`() {
        assertEquals(30_000L, EventBatcher.retryAfterMs("30", 0))
        assertEquals(30_000L, EventBatcher.retryAfterMs("Thu, 01 Jan 1970 00:00:30 GMT", 0))
        assertEquals(0L, EventBatcher.retryAfterMs("invalid", 0))
    }
}
