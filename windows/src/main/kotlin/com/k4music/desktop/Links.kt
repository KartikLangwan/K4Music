package com.k4music.desktop

import com.k4music.desktop.player.PlayerController
import com.k4music.desktop.player.toTrack
import com.k4music.desktop.ui.Navigator
import com.k4music.desktop.ui.Screen
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.requests.song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** YouTube / YouTube Music links (pasted, dropped, or passed to a second launch), as on Android. */
object Links {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Opens [text] when it is a YouTube link; false otherwise. */
    fun open(text: String, navigator: Navigator, player: PlayerController): Boolean {
        val uri = runCatching { java.net.URI(text.trim()) }.getOrNull() ?: return false
        val host = uri.host ?: return false
        if (!host.endsWith("youtube.com") && host != "youtu.be") return false
        val params = uri.rawQuery.orEmpty().split('&')
            .mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val segments = uri.path.orEmpty().trim('/').split('/')
        val videoId = when {
            host == "youtu.be" -> segments.firstOrNull()?.takeIf { it.isNotBlank() }
            segments.firstOrNull() == "watch" -> params["v"]
            else -> null
        }
        when {
            videoId != null -> scope.launch {
                Innertube.song(videoId)?.getOrNull()?.toTrack()?.let { player.play(listOf(it)) }
            }
            segments.firstOrNull() == "playlist" -> params["list"]?.let { navigator.go(Screen.Playlist("VL$it")) } ?: return false
            segments.firstOrNull() == "channel" -> segments.getOrNull(1)?.let { navigator.go(Screen.Artist(it)) } ?: return false
            else -> return false
        }
        return true
    }
}
