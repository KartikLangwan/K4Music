package com.k4music.core.data.utils

import android.net.Uri

open class RingBuffer<T>(val size: Int, private val init: (index: Int) -> T) : Iterable<T> {
    private val list = MutableList(size, init)
    init { require(size > 0) }

    @get:Synchronized
    @set:Synchronized
    private var index = 0

    operator fun get(index: Int) = list.getOrNull(index)
    @Synchronized
    operator fun plusAssign(element: T) {
        list[index] = element
        index = (index + 1) % size
    }

    @Synchronized
    override fun iterator() = list.toList().iterator()

    @Synchronized
    fun clear() = list.indices.forEach {
        list[it] = init(it)
    }
}

class UriCache<Key : Any, Meta>(size: Int = 16, private val ttlMs: Long = 15 * 60 * 1000L) {
    init { require(size > 0 && ttlMs > 0) }
    private val capacity = size
    private val entries = LinkedHashMap<Key, CachedUri<Key, Meta>>()

    data class CachedUri<Key, Meta> internal constructor(
        val key: Key,
        val meta: Meta,
        val uri: Uri,
        internal val createdAtNanos: Long = System.nanoTime()
    )

    @Synchronized
    operator fun get(key: Key): CachedUri<Key, Meta>? {
        val entry = entries[key] ?: return null
        if ((System.nanoTime() - entry.createdAtNanos) / 1_000_000 >= ttlMs) {
            entries.remove(key)
            return null
        }
        return entry
    }

    @Synchronized
    fun push(key: Key, meta: Meta, uri: Uri) {
        entries.remove(key)
        entries[key] = CachedUri(key, meta, uri)
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    @Synchronized
    fun remove(key: Key) = entries.remove(key)

    @Synchronized
    fun clear() = entries.clear()
}
