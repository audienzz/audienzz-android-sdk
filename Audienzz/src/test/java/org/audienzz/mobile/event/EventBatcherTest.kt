package org.audienzz.mobile.event

import android.util.Log
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.audienzz.mobile.event.entity.EventDomain
import org.audienzz.mobile.event.entity.EventType
import org.audienzz.mobile.event.repository.remote.RemoteEventRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class EventBatcherTest {

    private lateinit var repository: RemoteEventRepository

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
    }

    private fun event(index: Int) =
        EventDomain(eventType = EventType.AD_CLICK, adUnitId = index.toString())

    @Test
    fun `flushes a full batch by size`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { repository.submitBatch(any()) } returns Unit
        val batcher = EventBatcher(repository, dispatcher)

        repeat(20) { batcher.enqueue(event(it)) }
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.submitBatch(match { it.size == 20 }) }
    }

    @Test
    fun `flushes a partial batch after the flush interval`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { repository.submitBatch(any()) } returns Unit
        val batcher = EventBatcher(repository, dispatcher)

        repeat(3) { batcher.enqueue(event(it)) }
        advanceUntilIdle() // lets the 5s debounce timer elapse in virtual time

        coVerify(exactly = 1) { repository.submitBatch(match { it.size == 3 }) }
    }

    @Test
    fun `explicit flush sends the pending buffer immediately`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { repository.submitBatch(any()) } returns Unit
        val batcher = EventBatcher(repository, dispatcher)

        repeat(2) { batcher.enqueue(event(it)) }
        batcher.flush()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.submitBatch(match { it.size == 2 }) }
    }

    @Test
    fun `retries a failed batch up to the cap then drops it`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { repository.submitBatch(any()) } throws IOException("no network")
        val batcher = EventBatcher(repository, dispatcher)

        batcher.enqueue(event(0))
        batcher.flush()
        advanceUntilIdle() // advances through the 2s/4s/8s backoff delays

        // 1 initial attempt + 3 retries, then dropped.
        coVerify(exactly = 4) { repository.submitBatch(any()) }
    }

    @Test
    fun `drops oldest events when the queue overflows`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sent = mutableListOf<List<EventDomain>>()
        coEvery { repository.submitBatch(capture(sent)) } returns Unit
        val batcher = EventBatcher(repository, dispatcher)

        // Enqueue 520 before the consumer runs (StandardTestDispatcher defers it): the 500-capacity
        // drop-oldest channel keeps the most recent 500 (indices 20..519).
        repeat(520) { batcher.enqueue(event(it)) }
        advanceUntilIdle()

        val delivered = sent.flatten().map { it.adUnitId }
        assertEquals(500, delivered.size)
        assertEquals((20..519).map { it.toString() }, delivered)
    }
}
