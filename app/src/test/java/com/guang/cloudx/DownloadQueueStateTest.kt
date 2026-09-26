package com.guang.cloudx

import com.guang.cloudx.logic.model.DownloadQueueState
import org.junit.Assert.*
import org.junit.Test

class DownloadQueueStateTest {
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
        assertEquals(active, queue.active)
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
        assertEquals(resumed, queue.active)
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
