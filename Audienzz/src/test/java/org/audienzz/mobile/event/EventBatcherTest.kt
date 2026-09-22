package org.audienzz.mobile.event

import android.util.Log
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.audienzz.mobile.event.network.entity.EventNetwork
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class EventBatcherTest {

    private lateinit var repository: RemoteEventRepository
    private lateinit var store: EventStore

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        repository = mockk()
        store = mockk(relaxed = true)
        every { store.loadAll() } returns emptyList()
    }

    /**
     * The batcher now carries the finished wire payload, so the identifying field a test can assert
     * on is `eventId` rather than a domain ad-unit id.
     */
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
        attributes = emptyMap(),
    )

    @Test
    fun `flushes a full batch by size`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { repository.submitBatch(any()) } returns Unit
        val batcher = EventBatcher(repository, store, dispatcher)

        repeat(20) { batcher.enqueue(event(it)) }
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.submitBatch(match { it.size == 20 }) }
    }

    @Test
    fun `flushes a partial batch after the flush interval`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { repository.submitBatch(any()) } returns Unit
        val batcher = EventBatcher(repository, store, dispatcher)

        repeat(3) { batcher.enqueue(event(it)) }
        advanceUntilIdle() // lets the 5s debounce timer elapse in virtual time

        coVerify(exactly = 1) { repository.submitBatch(match { it.size == 3 }) }
    }

    @Test
    fun `explicit flush sends the pending buffer immediately`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { repository.submitBatch(any()) } returns Unit
        val batcher = EventBatcher(repository, store, dispatcher)

        repeat(2) { batcher.enqueue(event(it)) }
        batcher.flush()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.submitBatch(match { it.size == 2 }) }
    }

    @Test
    fun `retries a failed batch up to the cap then drops it`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { repository.submitBatch(any()) } throws IOException("no network")
        val batcher = EventBatcher(repository, store, dispatcher)

        batcher.enqueue(event(0))
        batcher.flush()
        advanceUntilIdle() // advances through the 2s/4s/8s backoff delays

        // 1 initial attempt + 3 retries, then dropped.
        coVerify(exactly = 4) { repository.submitBatch(any()) }
    }

    @Test
    fun `drops oldest events when the queue overflows`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sent = mutableListOf<List<EventNetwork>>()
        coEvery { repository.submitBatch(capture(sent)) } returns Unit
        val batcher = EventBatcher(repository, store, dispatcher)

        // Enqueue 520 before the consumer runs (StandardTestDispatcher defers it): the 500-capacity
        // drop-oldest channel keeps the most recent 500 (indices 20..519).
        repeat(520) { batcher.enqueue(event(it)) }
        advanceUntilIdle()

        val delivered = sent.flatten().map { it.eventId }
        assertEquals(500, delivered.size)
        assertEquals((20..519).map { it.toString() }, delivered)
    }

    // ── persistence ─────────────────────────────────────────────────────────

    @Test
    fun `an event is written to disk as soon as it is enqueued`() = runTest {
        // Not on flush. The batcher already flushes on background, so the tidy path was never the
        // lossy one — a foreground crash was, and only a write that already happened survives it.
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { repository.submitBatch(any()) } returns Unit
        val batcher = EventBatcher(repository, store, dispatcher)

        batcher.enqueue(event(0))

        // Deliberately BEFORE advancing: the consumer has not run, nothing has been sent.
        verify(exactly = 1) { store.append(match { it.eventId == "0" }) }
        advanceUntilIdle()
    }

    @Test
    fun `a delivered batch is dropped from disk`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { repository.submitBatch(any()) } returns Unit
        val batcher = EventBatcher(repository, store, dispatcher)

        repeat(3) { batcher.enqueue(event(it)) }
        advanceUntilIdle()

        verify(exactly = 1) { store.removeOldest(3) }
    }

    @Test
    fun `a batch given up on is also dropped from disk`() = runTest {
        // Otherwise a permanently failing batch would be replayed by every future launch, and the
        // store would never drain.
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { repository.submitBatch(any()) } throws IOException("no network")
        val batcher = EventBatcher(repository, store, dispatcher)

        batcher.enqueue(event(0))
        advanceUntilIdle()

        verify(exactly = 1) { store.removeOldest(1) }
    }

    @Test
    fun `events left by a previous process are sent on startup`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sent = mutableListOf<List<EventNetwork>>()
        coEvery { repository.submitBatch(capture(sent)) } returns Unit
        every { store.loadAll() } returns listOf(event(100), event(101))

        EventBatcher(repository, store, dispatcher)
        advanceUntilIdle()

        assertEquals(listOf("100", "101"), sent.flatten().map { it.eventId })
        verify(exactly = 1) { store.removeOldest(2) }
    }

    @Test
    fun `a restored backlog larger than one batch is sent in batches`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sent = mutableListOf<List<EventNetwork>>()
        coEvery { repository.submitBatch(capture(sent)) } returns Unit
        every { store.loadAll() } returns (0 until 120).map { event(it) }

        EventBatcher(repository, store, dispatcher)
        advanceUntilIdle()

        // 50 + 50 + 20, not one oversized POST.
        assertEquals(listOf(50, 50, 20), sent.map { it.size })
    }
}
