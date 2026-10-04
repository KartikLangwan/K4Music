package com.k4music.android.k4

import android.net.Uri
import android.util.Log




import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Keyless import: YouTube playlist links + Spotify links, no login/keys. */
class K4PlaylistImport(
    private val searchSongs: suspend (String, Int) -> List<K4Song>,
    client: OkHttpClient? = null
) {
    data class ImportResult(
        val name: String,
        val source: String,
        val songs: List<K4Song>,
        val unresolved: Int
    )

    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun import(link: String): ImportResult? {
        val url = link.trim()
        if (url.isEmpty()) return null
        return when {
            isYouTubePlaylist(url) -> importYouTubePlaylist(url)
            isSpotifyLink(url) -> importSpotifyLink(url)
            else -> null
        }
    }

    fun describe(link: String): String? {
        val url = link.trim()
        return when {
            isYouTubePlaylist(url) -> "YouTube playlist"
            url.contains("open.spotify.com/track", ignoreCase = true) -> "Spotify track"
            url.contains("open.spotify.com/album", ignoreCase = true) -> "Spotify album"
            url.contains("open.spotify.com/playlist", ignoreCase = true) -> "Spotify playlist"
            else -> null
        }
    }

    private fun isYouTubePlaylist(url: String): Boolean {
        val u = url.lowercase()
        if (!u.contains("youtube.com") && !u.contains("youtu.be") && !u.startsWith("list=")) return false
        return playlistIdOf(url) != null
    }

    private fun playlistIdOf(url: String): String? {
        if (url.startsWith("list=", ignoreCase = true)) {
            return url.substring(5).substringBefore('&').trim().takeIf { it.isNotEmpty() }
        }
        return runCatching {
            Uri.parse(url.trim()).getQueryParameter("list")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    // The browse/parse helpers below do blocking HTTP plus JSON work. Import is
    // triggered from the UI scope, so the whole paging loop hops to IO once
    // instead of ANR-ing for the length of the import.
    private suspend fun importYouTubePlaylist(url: String): ImportResult? = withContext(Dispatchers.IO) {
        val listId = playlistIdOf(url) ?: return@withContext null
        val name = playlistTitle(listId) ?: "YouTube playlist"
        val collected = ArrayList<K4Song>()
        var continuation: String? = null
        var pages = 0
        do {
            val page = withTimeoutOrNull(15_000L) { browsePlaylistPage(listId, continuation) }
                ?: break
            collected += page.songs
            continuation = page.continuation
            pages++
        } while (continuation != null && collected.size < MAX_IMPORT_TRACKS && pages < MAX_BROWSE_PAGES)
        if (collected.isEmpty()) return@withContext null
        val songs = collected.distinctBy { it.id }.take(MAX_IMPORT_TRACKS)
        Log.i(TAG, "imported YouTube playlist '$name': ${songs.size} tracks")
        ImportResult(name, "YouTube", songs, unresolved = 0)
    }

    private data class BrowsePage(val songs: List<K4Song>, val continuation: String?)

    private fun browseBody(listId: String, continuation: String?): JSONObject {
        val body = JSONObject().put(
            "context", JSONObject().put(
                "client", JSONObject()
                    .put("clientName", "WEB_REMIX")
                    .put("clientVersion", "1.20241001.01.00")
                    .put("hl", "en")
            )
        )
        if (continuation == null) body.put("browseId", "VL$listId")
        else body.put("continuation", continuation)
        return body
    }

    private fun browsePlaylistPage(listId: String, continuation: String?): BrowsePage? {
        val req = Request.Builder()
            .url(BROWSE_URL)
            .header("User-Agent", UA)
            .post(browseBody(listId, continuation).toString().toRequestBody(JSON_MEDIA))
            .build()
        val resp = runCatching {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()
            }
        }.getOrNull() ?: return null
        return try {
            val root = JSONObject(resp)
            val songs = ArrayList<K4Song>()
            collectPlaylistVideos(root, songs)
            BrowsePage(songs, findContinuation(root))
        } catch (e: Exception) {
            Log.w(TAG, "playlist browse parse: ${e.message}")
            null
        }
    }

    private fun playlistTitle(listId: String): String? {
        val req = Request.Builder()
            .url(BROWSE_URL)
            .header("User-Agent", UA)
            .post(browseBody(listId, null).toString().toRequestBody(JSON_MEDIA))
            .build()
        val resp = runCatching {
            http.newCall(req).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.getOrNull() ?: return null
        return runCatching {
            val root = JSONObject(resp)
            val header = root.optJSONObject("header")
                ?.optJSONObject("musicDetailHeaderRenderer")
                ?: root.optJSONObject("microformat")
                    ?.optJSONObject("microformatDataRenderer")
            header?.optString("title").orEmpty().trim().ifBlank { null }
        }.getOrNull()
    }

    private fun collectPlaylistVideos(node: Any?, out: MutableList<K4Song>) {
        if (out.size >= MAX_IMPORT_TRACKS) return
        when (node) {
            is JSONObject -> {
                val item = node.optJSONObject("musicResponsiveListItemRenderer")
                    ?: node.optJSONObject("playlistVideoRenderer")
                if (item != null) {
                    playlistVideoToSong(item)?.let {
                        if (out.none { s -> s.id == it.id }) out.add(it)
                    }
                    return
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectPlaylistVideos(node.opt(keys.next()), out)
                    if (out.size >= MAX_IMPORT_TRACKS) return
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectPlaylistVideos(node.opt(i), out)
                    if (out.size >= MAX_IMPORT_TRACKS) return
                }
            }
        }
    }

    private fun playlistVideoToSong(item: JSONObject): K4Song? {
        // YouTube Music list items carry the id in different places depending
        // on the renderer version. playlistItemData.videoId is where the app's
        // own catalogue parser finds it — without that fallback every item
        // returned null and imports silently resolved to zero tracks.
        val videoId = item.optString("videoId")
            .takeIf { it.length == 11 }
            ?: item.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.length == 11 }
            ?: item.optJSONObject("playlistItemData")
                ?.optString("videoId")
                ?.takeIf { it.length == 11 }
            ?: return null
        val columns = ArrayList<String>()
        val flex = item.optJSONArray("flexColumns")
        if (flex != null) {
            for (i in 0 until flex.length()) {
                val runs = flex.optJSONObject(i)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs") ?: continue
                val text = buildString {
                    for (j in 0 until runs.length()) append(runs.optJSONObject(j)?.optString("text").orEmpty())
                }.trim()
                if (text.isNotEmpty()) columns.add(text)
            }
        }
        val title = columns.firstOrNull()?.trim().orEmpty()
        if (title.isEmpty()) return null
        val parts = columns.drop(1)
            .flatMap { it.split('•') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val duration = parts.firstOrNull { DURATION_RE.matches(it) }?.let { parseClock(it) } ?: 0L
        val meta = parts.filterNot { DURATION_RE.matches(it) || VIEWS_RE.matches(it) }
            .dropWhile { it.lowercase() in TYPE_MARKERS }
        val artist = meta.firstOrNull() ?: "Unknown Artist"
        // Never store a blank album: it sorts/grouped as its own bucket and,
        // before the nav guard, an empty segment broke the album route outright.
        val album = meta.getOrNull(1)?.takeIf { !YEAR_RE.matches(it) }?.takeIf { it.isNotBlank() }
            ?: UNKNOWN_ALBUM
        return K4Song(
            id = "yt_$videoId",
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            contentUri = "yt:$videoId",
            artworkUri = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            isLocal = false,
            isFavorite = false
        )
    }

    private fun findContinuation(root: JSONObject): String? {
        val tokens = ArrayList<String>()
        collectContinuations(root, tokens)
        return tokens.firstOrNull()
    }

    private fun collectContinuations(node: Any?, out: MutableList<String>) {
        if (out.size >= 4) return
        when (node) {
            is JSONObject -> {
                val cont = node.optJSONObject("continuationItemRenderer")
                    ?.optJSONObject("continuationEndpoint")
                    ?.optJSONObject("continuationCommand")
                    ?.optString("token")
                if (!cont.isNullOrBlank()) {
                    out.add(cont)
                    return
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectContinuations(node.opt(keys.next()), out)
                    if (out.size >= 4) return
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectContinuations(node.opt(i), out)
                    if (out.size >= 4) return
                }
            }
        }
    }


    private fun isSpotifyLink(url: String): Boolean =
        url.contains("open.spotify.com/", ignoreCase = true)

    private suspend fun importSpotifyLink(url: String): ImportResult? =
        withTimeoutOrNull(60_000L) {
            val id = spotifyIdOf(url) ?: return@withTimeoutOrNull null
            val kind = when {
                url.contains("/track", ignoreCase = true) -> "track"
                url.contains("/album", ignoreCase = true) -> "album"
                else -> "playlist"
            }
            val tracks = spotifyTrackTitles(url, kind, id)
            if (tracks.isEmpty()) return@withTimeoutOrNull null
            val resolved = resolveAll(tracks)
            val label = "Spotify " + kind.replaceFirstChar { it.uppercase() }
            ImportResult(label, "Spotify", resolved.songs, resolved.unresolved)
        }

    private fun spotifyIdOf(url: String): String? = runCatching {
        Uri.parse(url.trim()).pathSegments.lastOrNull()
            ?.substringBefore('?')?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private data class TrackQuery(val title: String, val artist: String)


    private suspend fun spotifyTrackTitles(url: String, kind: String, id: String): List<TrackQuery> =
        withContext(Dispatchers.IO) {
            when (kind) {
                "track" -> {
                    val oembed = getText(
                        "https://open.spotify.com/oembed?url=" +
                            URLEncoder.encode("https://open.spotify.com/track/$id", "UTF-8")
                    )
                    val title = runCatching {
                        JSONObject(oembed ?: return@withContext emptyList()).optString("title")
                    }.getOrNull().orEmpty()
                    parseTrackTitle(title)?.let { listOf(it) } ?: emptyList()
                }
                else -> {
                    val html = getText("https://open.spotify.com/embed/$kind/$id")
                        ?: return@withContext emptyList()
                    parseEmbedTracks(html)
                }
            }
        }

    private fun parseTrackTitle(raw: String): TrackQuery? {
        val sep = listOf(" — ", " – ", " - ").firstOrNull { it in raw } ?: return null
        val title = raw.substringBefore(sep).trim()
        val artist = raw.substringAfter(sep).trim().substringBefore(',').trim()
        if (title.isEmpty() || artist.isEmpty()) return null
        return TrackQuery(title, artist)
    }


    private fun parseEmbedTracks(html: String): List<TrackQuery> {
        // Embed pages carry per-track blobs shaped like
        // {"uri":"spotify:track:…","name":"K4Song","artists":[{"name":"Artist"…
        // A manual scan (no regex) finds each artists block and pairs it with
        // the nearest preceding name, which is that track's title.
        val out = ArrayList<TrackQuery>()
        var idx = 0
        while (out.size < MAX_IMPORT_TRACKS) {
            val aKey = html.indexOf("\"artists\"", idx)
            if (aKey < 0) break
            val nameKey = html.indexOf("\"name\"", aKey)
            if (nameKey < 0 || nameKey - aKey > 64) {
                idx = aKey + 9
                continue
            }
            val artist = readJsonString(html, nameKey + 6)
            if (artist.isNullOrBlank()) {
                idx = nameKey + 6
                continue
            }
            val tKey = html.lastIndexOf("\"name\"", aKey)
            val title = if (tKey >= 0) readJsonString(html, tKey + 6) else null
            if (!title.isNullOrBlank()) {
                if (out.none { it.title.equals(title, true) && it.artist.equals(artist, true) }) {
                    out.add(TrackQuery(title.trim(), artist.trim()))
                }
            }
            idx = nameKey + 6
        }
        return out
    }

    /** Reads the `"value"` starting at [from] (which points just after `"name"`). */
    private fun readJsonString(html: String, from: Int): String? {
        var i = from
        while (i < html.length && html[i].isWhitespace()) i++
        if (i >= html.length || html[i] != ':') return null
        i++
        while (i < html.length && html[i].isWhitespace()) i++
        if (i >= html.length || html[i] != '"') return null
        i++
        val sb = StringBuilder()
        while (i < html.length) {
            val c = html[i]
            if (c == '\\' && i + 1 < html.length) {
                val n = html[i + 1]
                when (n) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'u' -> {
                        val hex = html.substring(i + 2, (i + 6).coerceAtMost(html.length))
                        sb.append(hex.toIntOrNull(16)?.toChar() ?: n)
                        i += 4
                    }
                    else -> sb.append(n)
                }
                i += 2
            } else if (c == '"') {
                return sb.toString()
            } else {
                sb.append(c)
                i++
            }
        }
        return null
    }


    private data class Resolved(val songs: List<K4Song>, val unresolved: Int)

    /**
     * Resolves each wanted track against the catalogue in small parallel
     * batches. The old version fanned out up to 100 simultaneous searches, so
     * big playlists collectively blew past the caller's timeout and the whole
     * import collapsed to null.
     */
    private suspend fun resolveAll(tracks: List<TrackQuery>): Resolved = coroutineScope {
        val out = ArrayList<K4Song>()
        for (batch in tracks.take(MAX_IMPORT_TRACKS).chunked(RESOLVE_BATCH)) {
            val jobs = batch.map { t ->
                async(Dispatchers.IO) {
                    val q = "${t.title} ${t.artist}"
                    runCatching { searchSongs(q, 5) }.getOrNull()
                        ?.firstOrNull { pickScore(it, t) >= 2 }
                }
            }
            out += jobs.awaitAll().filterNotNull()
        }
        val songs = out.distinctBy { it.id }
        Resolved(songs, tracks.size - songs.size)
    }

    private fun pickScore(song: K4Song, want: TrackQuery): Int {
        // Compare edition-stripped text: "Kesariya (Live)" must still match a
        // catalogue row titled "Kesariya", and vice versa. Without this,
        // remix/live/feat decorations scored 1 and the track was dropped.
        val st = stripEdition(normalizeForMatch(song.title))
        val wt = stripEdition(normalizeForMatch(want.title))
        var score = 0
        if (st == wt) score += 2
        else if (st.isNotEmpty() && wt.isNotEmpty() && (st.contains(wt) || wt.contains(st))) score += 1
        val sa = normalizeForMatch(song.artist)
        val wa = normalizeForMatch(want.artist)
        if (sa.isNotEmpty() && wa.isNotEmpty()) {
            val waTokens = wa.split(' ').filter { it.length > 2 }
            if (waTokens.any { it in sa }) score += 1
        }
        return score
    }

    /** Drops parenthetical/dash edition tails for matching purposes only. */
    private fun stripEdition(text: String): String {
        var v = " $text"
        v = v.replace(Regex("""\s[\(\[].*?[\)\]]"""), " ")
        v = v.replace(Regex("""\s[-–—|/].*"""), " ")
        v = v.replace(Regex("""\b(feat|ft|featuring|remix|live|acoustic|unplugged|version|cover|remaster\w*)\b.*"""), " ")
        return v.trim().replace(Regex("""\s+"""), " ")
    }

    private fun getText(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", UA).get().build()
        return runCatching {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()
            }
        }.getOrNull()
    }

    private fun parseClock(value: String): Long {
        var total = 0L
        for (part in value.split(':')) {
            val n = part.toIntOrNull() ?: return 0L
            total = total * 60 + n
        }
        return total * 1000L
    }

    companion object {
        private const val TAG = "K4Import"
        private const val UNKNOWN_ALBUM = "Unknown Album"
        private const val MAX_IMPORT_TRACKS = 100
        private const val MAX_BROWSE_PAGES = 6
        /** How many tracks per parallel resolve batch — small enough to avoid
         *  blowing the import timeout on large playlists. */
        private const val RESOLVE_BATCH = 10
        private const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val DURATION_RE = Regex("""\d{1,3}:\d{2}(?::\d{2})?""")
        private val VIEWS_RE = Regex("""[\d.,]+[KM]?\s*(?:views|plays)""", RegexOption.IGNORE_CASE)
        private val YEAR_RE = Regex("""\d{4}""")
        private val TYPE_MARKERS = setOf("song", "album", "single", "video", "ep", "episode")
    }
}

