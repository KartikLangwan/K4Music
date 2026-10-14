package com.k4music.android.utils

import com.k4music.desktop.player.Track
import com.k4music.desktop.player.toTrack
import com.k4music.providers.innertube.Innertube

/** Desktop version of the Android `SongItem.asMediaItem` used by the shared import sources. */
val Innertube.SongItem.asMediaItem: Track
    get() = toTrack() ?: Track(id = info?.endpoint?.videoId.orEmpty(), title = info?.name.orEmpty())
