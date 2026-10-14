package androidx.media3.common

/**
 * Desktop stand-in: the shared Android playlist-import sources (app/.../playlistimport) speak
 * MediaItem; on Windows the playable unit is [com.k4music.desktop.player.Track].
 */
typealias MediaItem = com.k4music.desktop.player.Track
