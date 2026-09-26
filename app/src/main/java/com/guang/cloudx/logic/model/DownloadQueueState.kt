package com.guang.cloudx.logic.model

/** Confined to the service command loop; completion tokens prevent old attempts clearing new ones. */
class DownloadQueueState<T> {
    data class Attempt<T>(
        val id: Long,
        val generation: Long,
        val task: T,
    )

    private val pending = linkedMapOf<Long, T>()
    private var generation = 0L
    var active: Attempt<T>? = null
        private set
    val isIdle: Boolean get() = active == null && pending.isEmpty()

    fun contains(id: Long): Boolean = active?.id == id || id in pending

    fun enqueue(
        id: Long,
        task: T,
    ) {
        if (!contains(id)) pending[id] = task
    }

    fun removeQueued(ids: Collection<Long>) {
        ids.forEach { pending.remove(it) }
    }

    fun startNext(): Attempt<T>? {
        if (active != null) return null
        val entry = pending.entries.firstOrNull() ?: return null
        pending.remove(entry.key)
        return Attempt(entry.key, ++generation, entry.value).also { active = it }
    }

    fun finish(token: Long): Boolean {
        if (active?.generation != token) return false
        active = null
        return true
    }
}
