package com.k4music.android.utils

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import com.k4music.android.models.Song
import com.k4music.android.models.SongWithContentLength
import com.k4music.android.service.PlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/** Keep the Offline screen current when a download completes or the cache evicts a track. */
@OptIn(UnstableApi::class)
fun PlayerService.Binder.cachedSongsFlow(candidates: List<SongWithContentLength>): Flow<List<Song>> = callbackFlow {
    fun refresh() {
        trySend(candidates.filter { runCatching { isCached(it) }.getOrDefault(false) }.map { it.song })
    }
    val listener = object : Cache.Listener {
        override fun onSpanAdded(cache: Cache, span: CacheSpan) = refresh()
        override fun onSpanRemoved(cache: Cache, span: CacheSpan) = refresh()
        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) = Unit
    }
    val keys = candidates.map { it.song.id }.distinct()
    keys.forEach { runCatching { cache.addListener(it, listener) } }
    refresh()
    awaitClose { keys.forEach { runCatching { cache.removeListener(it, listener) } } }
}.distinctUntilChanged().flowOn(Dispatchers.IO)
