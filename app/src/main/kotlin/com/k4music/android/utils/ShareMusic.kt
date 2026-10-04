package com.k4music.android.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import com.k4music.android.Brand
import com.k4music.app.R

/** Provider-specific stream IDs are internal; share metadata instead of an invalid YouTube link. */
internal fun musicShareText(id: String, title: String, artist: String): String {
    val metadata = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" — ")
    val destination = if (id.matches(Regex("[A-Za-z0-9_-]{11}"))) {
        "https://music.youtube.com/watch?v=$id"
    } else "${Brand.APP_NAME}\n${Brand.WEBSITE_URL}"
    return listOf(metadata, destination).filter { it.isNotBlank() }.joinToString("\n")
}

fun Context.shareMusic(mediaItem: MediaItem) {
    val text = musicShareText(
        mediaItem.mediaId,
        mediaItem.mediaMetadata.title?.toString().orEmpty(),
        mediaItem.mediaMetadata.artist?.toString().orEmpty()
    )
    try {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        toast(R.string.share_unavailable)
    } catch (_: SecurityException) {
        toast(R.string.share_unavailable)
    }
}
