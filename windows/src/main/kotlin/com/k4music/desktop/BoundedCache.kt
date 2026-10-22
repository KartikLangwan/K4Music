package com.k4music.desktop

/** Small synchronized LRU for disposable computed/network results, never authoritative user data. */
internal class BoundedCache<K, V>(private val capacity: Int) {
    init { require(capacity > 0) }
    private val values = object : LinkedHashMap<K, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > capacity
    }

    @Synchronized operator fun get(key: K): V? = values[key]
    @Synchronized operator fun set(key: K, value: V) { values[key] = value }
    @Synchronized fun remove(key: K): V? = values.remove(key)
    @Synchronized fun removeKeys(predicate: (K) -> Boolean) { values.keys.removeIf(predicate) }
}
