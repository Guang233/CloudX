package com.guang.cloudx.logic.model

/** Main-loop confined. Tokens isolate attempts; resource keys protect shared legacy song checkpoints. */
class DownloadQueueState<T>(
    private val resourceKey: (T) -> Any? = { null },
) {
    data class Attempt<T>(
        val id: Long,
        val generation: Long,
        val task: T,
    )

    private val pending = linkedMapOf<Long, T>()
    private val running = linkedMapOf<Long, Attempt<T>>()
    private var generation = 0L
    val active: List<Attempt<T>> get() = running.values.toList()
    val queuedCount: Int get() = pending.size
    val isIdle: Boolean get() = running.isEmpty() && pending.isEmpty()

    fun contains(id: Long): Boolean = id in running || id in pending

    fun enqueue(
        id: Long,
        task: T,
    ) {
        if (!contains(id)) pending[id] = task
    }

    fun removeQueued(ids: Collection<Long>) {
        ids.forEach { pending.remove(it) }
    }

    /** Lowering the limit never interrupts existing workers; new starts wait for a free slot. */
    fun startNext(limit: Int = 1): Attempt<T>? {
        if (running.size >= limit.coerceIn(1, DownloadConcurrency.MAX_SONGS)) return null
        val occupied = running.values.mapNotNull { resourceKey(it.task) }.toSet()
        val entry = pending.entries.firstOrNull { resourceKey(it.value) !in occupied } ?: return null
        pending.remove(entry.key)
        return Attempt(entry.key, ++generation, entry.value).also { running[it.id] = it }
    }

    fun finish(token: Long): Boolean {
        val attempt = running.values.firstOrNull { it.generation == token } ?: return false
        running.remove(attempt.id)
        return true
    }
}
