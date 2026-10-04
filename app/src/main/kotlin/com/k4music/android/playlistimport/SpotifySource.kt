package com.k4music.android.playlistimport

import androidx.media3.common.MediaItem
import com.k4music.android.k4.parseClockToMs
import com.k4music.android.k4.searchSaavnSongs
import com.k4music.android.k4.toSongItem
import com.k4music.android.utils.asMediaItem
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.SearchBody
import com.k4music.providers.innertube.requests.searchPage
import com.k4music.providers.innertube.utils.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class WantedTrack(val title: String, val artist: String, val durationMs: Long?)

data class SpotifyEmbed(val name: String, val thumbnail: String?, val tracks: List<WantedTrack>)

/**
 * Spotify playlists without an account or API key. Spotify's public embed page lists the playlist's
 * tracks (at most the first 100); the public page states the real size, so the shortfall is reported
 * instead of hidden. Every track is then matched to a playable song (YouTube Music first, JioSaavn second).
 */
class SpotifySource(
    private val http: OkHttpClient = defaultClient,
    private val search: suspend (query: String) -> List<Candidate<MediaItem>> = ::searchCatalogues
) : PlaylistSource {
    override val name = "Spotify"

    override fun accepts(link: String) = playlistIdOf(link) != null

    override val canRetryFailed = true

    override suspend fun fetch(link: String, progress: (ImportProgress) -> Unit): FetchedPlaylist {
        val id = playlistIdOf(link) ?: throw PlaylistImportException(ImportError.InvalidLink, "No Spotify playlist id in this link.")

        val embedHtml = get("https://open.spotify.com/embed/playlist/$id")
            ?: throw PlaylistImportException(ImportError.Network, "Could not reach Spotify. Check your connection and retry.")
        val embed = parseEmbed(embedHtml) ?: throw PlaylistImportException(
            ImportError.Unavailable,
            "This Spotify playlist is private, deleted or not available."
        )
        // Only a bare user agent gets the server-rendered page that states the song count.
        val declared = get("https://open.spotify.com/playlist/$id", PAGE_UA)?.let(::songCountOf)

        progress(ImportProgress(ImportPhase.Fetching, embed.tracks.size, declared ?: embed.tracks.size))
        val (tracks, failed) = resolve(embed.tracks, progress)
        val total = (declared ?: embed.tracks.size).coerceAtLeast(embed.tracks.size)
        return FetchedPlaylist(
            name = embed.name,
            source = name,
            thumbnail = embed.thumbnail,
            browseId = null,
            total = total,
            tracks = tracks,
            failed = failed,
            notExposed = total - embed.tracks.size,
            truncated = declared == null && embed.tracks.size >= EMBED_LIMIT
        )
    }

    override suspend fun retry(
        failed: List<FailedTrack>,
        progress: (ImportProgress) -> Unit
    ) = resolve(failed.map { WantedTrack(it.title, it.artist, it.durationMs) }, progress)

    /** Matches in small parallel batches (gentle on the catalogue servers), keeping the playlist order. */
    private suspend fun resolve(
        wanted: List<WantedTrack>,
        progress: (ImportProgress) -> Unit
    ): Pair<List<ImportedTrack>, List<FailedTrack>> {
        val tracks = ArrayList<ImportedTrack>()
        val failed = ArrayList<FailedTrack>()
        var done = 0
        for (batch in wanted.chunked(BATCH)) {
            val results = coroutineScope { batch.map { async { match(it) } }.awaitAll() }
            batch.zip(results).forEach { (want, result) ->
                result.fold(
                    onSuccess = { tracks += it },
                    onFailure = { failed += FailedTrack(want.title, want.artist, it.message ?: "No match", want.durationMs) }
                )
            }
            done += batch.size
            progress(ImportProgress(ImportPhase.Matching, done, wanted.size))
        }
        return tracks to failed
    }

    private suspend fun match(want: WantedTrack): Result<ImportedTrack> {
        val query = "${want.title} ${want.artist}".trim()
        var searchFailed = false
        val candidates = try {
            retrying(attempts = 2, pauseMs = retryPauseMs) { withTimeout(SEARCH_TIMEOUT_MS) { search(query) } }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            searchFailed = true
            emptyList()
        }
        val best = pickBestMatch(want.title, want.artist, want.durationMs, candidates)
            ?: return Result.failure(
                PlaylistImportException(
                    if (searchFailed) ImportError.Network else ImportError.Empty,
                    if (searchFailed) "Search failed (network)" else "No matching song found"
                )
            )
        val item = best.payload
        return Result.success(ImportedTrack(item.mediaId, want.title, want.artist, item))
    }

    private suspend fun get(url: String, userAgent: String = UA): String? = withContext(Dispatchers.IO) {
        try {
            retrying {
                http.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute().use { response ->
                    when {
                        response.isSuccessful -> response.body?.string().orEmpty()
                        response.code in 400..499 && response.code != 429 -> ""
                        else -> throw IOException("HTTP ${response.code}")
                    }
                }
            }.ifEmpty { null }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
    }

    companion object {
        private const val BATCH = 5
        private const val EMBED_LIMIT = 100
        private const val PAGE_UA = "Mozilla/5.0"
        private const val SEARCH_TIMEOUT_MS = 15_000L
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

        private val ID_URL = Regex("""open\.spotify\.com/(?:intl-[A-Za-z-]+/)?(?:embed/)?playlist/([A-Za-z0-9]{22})""")
        private val ID_URI = Regex("""^spotify:playlist:([A-Za-z0-9]{22})$""")
        private val NEXT_DATA = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        private val SONG_COUNT = Regex("""music:song_count"\s+content="(\d+)"""")

        fun playlistIdOf(link: String): String? {
            val text = link.trim()
            return (ID_URL.find(text) ?: ID_URI.find(text))?.groupValues?.get(1)
        }

        fun songCountOf(html: String): Int? = SONG_COUNT.find(html)?.groupValues?.get(1)?.toIntOrNull()

        /** Reads the playlist name, cover and track list out of an embed page; null when the page is an error page. */
        fun parseEmbed(html: String): SpotifyEmbed? {
            val json = NEXT_DATA.find(html)?.groupValues?.get(1) ?: return null
            val entity = runCatching {
                Json.parseToJsonElement(json).jsonObject["props"]!!.jsonObject["pageProps"]!!.jsonObject["state"]!!
                    .jsonObject["data"]!!.jsonObject["entity"]!!.jsonObject
            }.getOrNull() ?: return null

            val list = entity["trackList"] as? JsonArray ?: return null
            val tracks = list.mapNotNull { element ->
                val track = element as? JsonObject ?: return@mapNotNull null
                val title = track["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (title.isEmpty()) return@mapNotNull null
                WantedTrack(
                    title = title,
                    artist = track["subtitle"]?.jsonPrimitive?.contentOrNull?.replace(' ', ' ')?.trim().orEmpty(),
                    durationMs = track["duration"]?.jsonPrimitive?.longOrNull
                )
            }
            val cover = runCatching {
                entity["coverArt"]!!.jsonObject["sources"]!!.jsonArray
                    .maxByOrNull { it.jsonObject["width"]?.jsonPrimitive?.intOrNull ?: 0 }
                    ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            return SpotifyEmbed(
                name = (entity["name"] ?: entity["title"])?.jsonPrimitive?.contentOrNull?.trim()
                    .orEmpty().ifBlank { "Spotify playlist" },
                thumbnail = cover,
                tracks = tracks
            )
        }
    }
}

private suspend fun searchCatalogues(query: String): List<Candidate<MediaItem>> {
    val youtube = Innertube.searchPage(
        body = SearchBody(query = query, params = Innertube.SearchFilter.Song.value),
        fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
    )?.getOrThrow()?.items.orEmpty().mapNotNull { item ->
        val key = runCatching { item.key }.getOrNull() ?: return@mapNotNull null
        Candidate(
            title = item.info?.name.orEmpty(),
            artist = item.authors?.joinToString("") { it.name.orEmpty() }.orEmpty(),
            durationMs = item.durationText?.let(::parseClockToMs)?.takeIf { it > 0 },
            payload = item.asMediaItem.also { check(it.mediaId == key) }
        )
    }
    // JioSaavn is only a fallback for tracks YouTube Music cannot offer; it never throws.
    return youtube + searchSaavnSongs(query).map { song ->
        Candidate(song.title, song.artist, song.durationMs.takeIf { it > 0 }, song.toSongItem().asMediaItem)
    }
}
