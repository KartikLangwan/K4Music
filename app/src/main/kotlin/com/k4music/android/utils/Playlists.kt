package com.k4music.android.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import com.k4music.android.Database
import com.k4music.android.models.Playlist
import com.k4music.android.transaction
import com.k4music.app.R

/** Adds [mediaItems] to [playlist] and tells the user what really happened (added or duplicate). */
fun Context.addToPlaylist(playlist: Playlist, mediaItems: List<MediaItem>) {
    val appContext = applicationContext
    transaction {
        val added = Database.addToPlaylist(playlist, mediaItems)
        val name = playlist.name.trim()
        val message = when {
            added == 0 -> appContext.getString(R.string.already_in_playlist, name)
            added == 1 -> appContext.getString(R.string.added_to_playlist, name)
            else -> appContext.getString(R.string.added_songs_to_playlist, added, name)
        }
        Handler(Looper.getMainLooper()).post { appContext.toast(message) }
    }
}
