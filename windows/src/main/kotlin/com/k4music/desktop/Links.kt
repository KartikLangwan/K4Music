package com.k4music.desktop

import com.k4music.desktop.player.PlayerController
import com.k4music.desktop.player.toTrack
import com.k4music.desktop.ui.Navigator
import com.k4music.desktop.ui.Screen
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.requests.song
import com.k4music.desktop.res.connection_retry_message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** YouTube / YouTube Music links (pasted, dropped, or passed to a second launch), as on Android. */
object Links {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: kotlinx.coroutines.Job? = null

    internal fun youtubeUri(text: String): java.net.URI? {
        val uri = runCatching { java.net.URI(text.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        return uri.takeIf {
            it.scheme.equals("https", true) && it.userInfo == null && (it.port == -1 || it.port == 443) &&
                (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be")
        }
    }

    /** Opens [text] when it is a YouTube link; false otherwise. */
    @Synchronized fun open(text: String, navigator: Navigator, player: PlayerController): Boolean {
        val uri = youtubeUri(text) ?: return false
        val host = uri.host.lowercase()
        val params = uri.rawQuery.orEmpty().split('&')
            .mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val segments = uri.path.orEmpty().trim('/').split('/')
        val videoId = when {
            host == "youtu.be" -> segments.firstOrNull()?.takeIf { it.isNotBlank() }
            segments.firstOrNull() == "watch" -> params["v"]
            else -> null
        }
        when {
            videoId != null -> {
                pending?.cancel()
                val version = player.queueVersion
                pending = scope.launch {
                    try {
                        val track = Innertube.song(videoId)?.getOrThrow()?.toTrack() ?: error("Song unavailable")
                        player.playIfUnchanged(track, version)
                    } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                    catch (error: Exception) {
                        AppLog.w("Links", "Could not open song link", error)
                        com.k4music.desktop.ui.AppDialogs.show(org.jetbrains.compose.resources.getString(com.k4music.desktop.res.Res.string.connection_retry_message))
                    }
                }
            }
            segments.firstOrNull() == "playlist" -> params["list"]?.let { pending?.cancel(); navigator.go(Screen.Playlist("VL$it")) } ?: return false
            segments.firstOrNull() == "channel" -> segments.getOrNull(1)?.let { pending?.cancel(); navigator.go(Screen.Artist(it)) } ?: return false
            else -> return false
        }
        return true
    }
}
