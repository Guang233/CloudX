package com.guang.cloudx

import com.guang.cloudx.logic.model.DownloadQueueState
import org.junit.Assert.*
import org.junit.Test

class DownloadQueueStateTest {
    @Test fun twoSongsRunAndFreeSlotsRefillInOrder() {
        val queue = DownloadQueueState<String>()
        (1L..4L).forEach { queue.enqueue(it, "$it") }
        val first = requireNotNull(queue.startNext(2))
        val second = requireNotNull(queue.startNext(2))
        assertEquals(2, queue.active.size)
        assertEquals(2, queue.queuedCount)
        assertNull(queue.startNext(2))
        queue.finish(first.generation)
        assertEquals(3L, queue.startNext(2)?.id)
        assertTrue(queue.active.contains(second))
        assertNull(queue.startNext(2))
    }

    @Test fun reducingLimitDrainsExistingWorkersWithoutCancellation() {
        val queue = DownloadQueueState<String>()
        (1L..5L).forEach { queue.enqueue(it, "$it") }
        val attempts = (1..3).map { requireNotNull(queue.startNext(3)) }
        assertNull(queue.startNext(1))
        assertEquals(3, queue.active.size)
        queue.finish(attempts[0].generation)
        queue.finish(attempts[1].generation)
        assertNull(queue.startNext(1))
        queue.finish(attempts[2].generation)
        assertEquals(4L, queue.startNext(1)?.id)
    }

    @Test fun raisingLimitAddsSlotsWithoutDuplicatingTasks() {
        val queue = DownloadQueueState<String>()
        (1L..5L).forEach { queue.enqueue(it, "$it") }
        val first = requireNotNull(queue.startNext(1))
        assertNull(queue.startNext(1))
        repeat(3) { assertNotNull(queue.startNext(4)) }
        assertEquals(4, queue.active.size)
        assertTrue(queue.active.contains(first))
        assertNull(queue.startNext(100)) // Never exceeds the hard resource limit.
    }

    @Test fun lateCompletionDoesNotStopOtherOrResumedSongs() {
        val queue = DownloadQueueState<String>()
        queue.enqueue(1, "one")
        queue.enqueue(2, "two")
        val old = requireNotNull(queue.startNext(2))
        val other = requireNotNull(queue.startNext(2))
        queue.finish(old.generation)
        queue.enqueue(1, "resumed")
        val resumed = requireNotNull(queue.startNext(2))
        assertFalse(queue.finish(old.generation))
        assertEquals(listOf(other, resumed), queue.active)
    }

    @Test fun sharedLegacySongArtifactsNeverHaveTwoWriters() {
        val queue = DownloadQueueState<String> { it }
        queue.enqueue(1, "same song")
        queue.enqueue(2, "same song")
        queue.enqueue(3, "different song")
        val first = requireNotNull(queue.startNext(2))
        assertEquals(3L, queue.startNext(2)?.id)
        queue.finish(first.generation)
        assertEquals(2L, queue.startNext(2)?.id)
    }

    @Test fun batchStopRemovesSelectedWorkersWithoutDisturbingOthers() {
        val queue = DownloadQueueState<String>()
        (1L..6L).forEach { queue.enqueue(it, "$it") }
        val active = (1..3).map { requireNotNull(queue.startNext(3)) }
        queue.removeQueued(listOf(1, 2, 4, 5))
        queue.finish(active[0].generation)
        queue.finish(active[1].generation)
        assertEquals(listOf(active[2]), queue.active)
        assertEquals(6L, queue.startNext(3)?.id)
        assertNull(queue.startNext(3))
    }

    @Test fun onlyOneActiveTaskAndFifoOrder() {
        val queue = DownloadQueueState<String>()
        queue.enqueue(1, "one")
        queue.enqueue(2, "two")
        val first = requireNotNull(queue.startNext())
        assertEquals("one", first.task)
        assertNull(queue.startNext())
        queue.finish(first.generation)
        val second = requireNotNull(queue.startNext())
        assertEquals("two", second.task)
        queue.finish(second.generation)
        assertTrue(queue.isIdle)
    }

    @Test fun batchRemovalDoesNotStartSelectedQueuedTasks() {
        val queue = DownloadQueueState<String>()
        (1L..4L).forEach { queue.enqueue(it, "$it") }
        val active = requireNotNull(queue.startNext())
        queue.removeQueued(listOf(1, 2, 3))
        assertEquals(listOf(active), queue.active)
        queue.finish(active.generation)
        assertEquals(4L, queue.startNext()?.id)
    }

    @Test fun staleCompletionCannotClearAnImmediatelyResumedAttempt() {
        val queue = DownloadQueueState<String>()
        queue.enqueue(1, "first attempt")
        val old = requireNotNull(queue.startNext())
        queue.finish(old.generation) // stop + join
        queue.enqueue(1, "resumed attempt")
        val resumed = requireNotNull(queue.startNext())
        assertFalse(queue.finish(old.generation)) // late completion command from old worker
        assertEquals(listOf(resumed), queue.active)
    }

    @Test fun duplicateCommandsNeverDuplicateWorkers() {
        val queue = DownloadQueueState<String>()
        queue.enqueue(1, "first")
        queue.enqueue(1, "duplicate while queued")
        val active = requireNotNull(queue.startNext())
        assertEquals("first", active.task)
        queue.enqueue(1, "duplicate while active")
        queue.finish(active.generation)
        assertNull(queue.startNext())
        assertTrue(queue.isIdle)
    }

    @Test fun pauseAllDrainsQueueWithoutScheduling() {
        val queue = DownloadQueueState<String>()
        (1L..20L).forEach { queue.enqueue(it, "$it") }
        val active = requireNotNull(queue.startNext())
        queue.removeQueued((1L..20L).toList())
        queue.finish(active.generation)
        assertNull(queue.startNext())
        assertTrue(queue.isIdle)
    }
}
