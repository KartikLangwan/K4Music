package com.k4music.android.k4

import com.k4music.desktop.k4.SAAVN_KEY_PREFIX
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.NavigationEndpoint
import com.k4music.providers.innertube.models.Thumbnail

// The JioSaavnBridge functions the shared Android sources call, backed by the desktop bridge.

suspend fun searchSaavnSongs(query: String): List<K4Song> = com.k4music.desktop.k4.searchSaavnSongs(query)

fun K4Song.toSongItem(): Innertube.SongItem = Innertube.SongItem(
    info = Innertube.Info(name = title, endpoint = NavigationEndpoint.Endpoint.Watch(videoId = saavnMediaKey())),
    authors = if (artist.isBlank()) null else listOf(Innertube.Info(name = artist, endpoint = null)),
    album = null,
    durationText = durationMs.takeIf { it > 0 }?.let { com.k4music.desktop.player.formatDuration(it) },
    explicit = false,
    thumbnail = artworkUri?.let { Thumbnail(url = it, height = null, width = null) }
)
