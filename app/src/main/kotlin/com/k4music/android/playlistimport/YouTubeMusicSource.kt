package com.k4music.android.playlistimport

import com.k4music.android.utils.asMediaItem
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.BrowseBody
import com.k4music.providers.innertube.models.bodies.ContinuationBody
import com.k4music.providers.innertube.requests.playlistPage
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Row of a YouTube playlist page: either a usable track or one that could not be read. */
data class YtRow(val track: ImportedTrack?, val failed: FailedTrack?)

data class YtFirstPage(
    val title: String?,
    val thumbnail: String?,
    val total: Int?,
    val exists: Boolean,
    val rows: List<YtRow>,
    val token: String?
)

data class YtNextPage(val rows: List<YtRow>, val token: String?)

/** YouTube Music / YouTube playlists through the app's existing Innertube client, fully paginated. */
class YouTubeMusicSource(
    private val loadFirst: suspend (browseId: String) -> YtFirstPage = ::innertubeFirstPage,
    private val loadNext: suspend (token: String) -> YtNextPage = ::innertubeNextPage
) : PlaylistSource {
    override val name = "YouTube Music"

    override fun accepts(link: String) = playlistIdOf(link) != null

    override suspend fun fetch(link: String, progress: (ImportProgress) -> Unit): FetchedPlaylist {
        val id = playlistIdOf(link) ?: throw PlaylistImportException(ImportError.InvalidLink, "No playlist id in this link.")
        val browseId = "VL$id"

        val first = try {
            retrying { loadFirst(browseId) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: PlaylistImportException) {
            throw error
        } catch (error: Exception) {
            throw PlaylistImportException(
                ImportError.Network,
                "Could not reach YouTube Music. Check your connection and retry.",
                error
            )
        }
        if (!first.exists) throw PlaylistImportException(
            ImportError.Unavailable,
            "This playlist is private, deleted or not available on YouTube Music."
        )

        val rows = collectAllPages(
            first = first.rows,
            firstToken = first.token,
            onProgress = { progress(ImportProgress(ImportPhase.Fetching, it, first.total)) }
        ) { token -> loadNext(token).let { it.rows to it.token } }

        val tracks = rows.mapNotNull { it.track }
        val failed = rows.mapNotNull { it.failed }
        // YouTube omits unavailable (greyed-out) videos from the list it serves; the header count includes them.
        val hidden = (first.total ?: 0) - tracks.size - failed.size
        return FetchedPlaylist(
            name = first.title?.takeIf { it.isNotBlank() } ?: "YouTube playlist",
            source = name,
            thumbnail = first.thumbnail,
            browseId = browseId,
            total = first.total ?: (tracks.size + failed.size),
            tracks = tracks,
            failed = failed + List(hidden.coerceAtLeast(0)) {
                FailedTrack("Unavailable video ${it + 1}", "", "Hidden by YouTube (removed, private or region-blocked)")
            }
        )
    }

    companion object {
        private val LIST_PARAM = Regex("""[?&]list=([A-Za-z0-9_-]+)""")
        private val BARE_ID = Regex("""^(?:VL)?((?:PL|OLAK5uy_|UU|LL|FL)[A-Za-z0-9_-]{8,})$""")
        private val HOSTS = Regex("""^(?:https?://)?(?:www\.|m\.|music\.)?(?:youtube\.com|youtu\.be)/""", RegexOption.IGNORE_CASE)

        fun playlistIdOf(link: String): String? {
            val text = link.trim()
            BARE_ID.find(text)?.let { return it.groupValues[1] }
            if (!HOSTS.containsMatchIn(text)) return null
            val id = LIST_PARAM.find(text)?.groupValues?.get(1) ?: return null
            return id.removePrefix("VL").takeIf { it.length >= 10 }
        }
    }
}

private fun rowOf(item: Innertube.SongItem): YtRow {
    val videoId = runCatching { item.info?.endpoint?.videoId }.getOrNull()
    val title = item.info?.name.orEmpty()
    val artist = item.authors?.joinToString("") { it.name.orEmpty() }.orEmpty()
    return if (videoId == null) YtRow(null, FailedTrack(title.ifBlank { "Unknown track" }, artist, "Track has no playable id"))
    else YtRow(ImportedTrack(videoId, title, artist, item.asMediaItem), null)
}

private val SONG_COUNT = Regex("""(\d[\d,.]*)\s+(?:songs?|tracks?|videos?)""", RegexOption.IGNORE_CASE)

private suspend fun innertubeFirstPage(browseId: String): YtFirstPage {
    val page = try {
        Innertube.playlistPage(BrowseBody(browseId = browseId))?.getOrThrow() ?: throw IOException("No response")
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // A 4xx from YouTube means the playlist does not exist or is private; anything else is transient.
        if (error.javaClass.simpleName == "ClientRequestException") {
            return YtFirstPage(null, null, null, exists = false, rows = emptyList(), token = null)
        }
        throw error
    }
    val exists = page.title != null || page.songsPage != null
    return YtFirstPage(
        title = page.title,
        thumbnail = page.thumbnail?.url,
        total = page.otherInfo?.let { SONG_COUNT.find(it)?.groupValues?.get(1)?.filter(Char::isDigit)?.toIntOrNull() },
        exists = exists,
        rows = page.songsPage?.items.orEmpty().map(::rowOf),
        token = page.songsPage?.continuation
    )
}

private suspend fun innertubeNextPage(token: String): YtNextPage {
    val page = Innertube.playlistPage(ContinuationBody(continuation = token))?.getOrThrow()
        ?: throw IOException("No response")
    return YtNextPage(rows = page.items.orEmpty().map(::rowOf), token = page.continuation)
}
