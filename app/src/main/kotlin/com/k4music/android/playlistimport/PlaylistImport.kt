package com.k4music.android.playlistimport

import androidx.media3.common.MediaItem
import com.k4music.android.k4.normalizeForMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** A track ready to be stored: [key] is its identity (video id) used for ordering and de-duplication. */
data class ImportedTrack(val key: String, val title: String, val artist: String, val mediaItem: MediaItem)

/** A track the source listed but that could not be turned into a playable song. */
data class FailedTrack(val title: String, val artist: String, val reason: String, val durationMs: Long? = null)

/**
 * Everything a source found. [total] is what the source claims the playlist holds (null when unknown);
 * [notExposed] is how many of those the source refuses to hand out (Spotify's keyless embed caps at 100).
 */
data class FetchedPlaylist(
    val name: String,
    val source: String,
    val thumbnail: String?,
    val browseId: String?,
    val total: Int?,
    val tracks: List<ImportedTrack>,
    val failed: List<FailedTrack> = emptyList(),
    val notExposed: Int = 0,
    /** True when the source hit its cap and could not say how many tracks it withheld. */
    val truncated: Boolean = false
)

enum class ImportError { InvalidLink, Unavailable, Network, Empty }

class PlaylistImportException(val error: ImportError, message: String, cause: Throwable? = null) :
    Exception(message, cause)

enum class ImportPhase { Fetching, Matching }

data class ImportProgress(val phase: ImportPhase, val done: Int, val total: Int?)

/** One playlist provider. Add another source by implementing this and listing it in [PlaylistImporter]. */
interface PlaylistSource {
    val name: String
    fun accepts(link: String): Boolean
    suspend fun fetch(link: String, progress: (ImportProgress) -> Unit): FetchedPlaylist

    /** True when [retry] can try the failed tracks again (matching problems, unlike YouTube-hidden videos). */
    val canRetryFailed: Boolean get() = false

    suspend fun retry(failed: List<FailedTrack>, progress: (ImportProgress) -> Unit): Pair<List<ImportedTrack>, List<FailedTrack>> =
        emptyList<ImportedTrack>() to failed
}

/** [fetched] after de-duplication, plus how many repeated tracks were collapsed. */
data class ImportPlan(val fetched: FetchedPlaylist, val duplicates: Int)

class PlaylistImporter(private val sources: List<PlaylistSource>) {
    val sourceNames: List<String> get() = sources.map { it.name }

    fun sourceFor(link: String): PlaylistSource? = sources.firstOrNull { it.accepts(link.trim()) }

    suspend fun prepare(link: String, progress: (ImportProgress) -> Unit): ImportPlan {
        val trimmed = link.trim()
        val source = sourceFor(trimmed) ?: throw PlaylistImportException(
            ImportError.InvalidLink,
            "This is not a Spotify or YouTube Music playlist link."
        )
        val fetched = source.fetch(trimmed, progress)
        val unique = fetched.tracks.distinctBy { it.key }
        if (unique.isEmpty() && fetched.failed.isEmpty()) throw PlaylistImportException(
            ImportError.Empty,
            "This playlist has no tracks."
        )
        return ImportPlan(fetched.copy(tracks = unique), duplicates = fetched.tracks.size - unique.size)
    }
}

/** Base pause between retries; tests set it to 0. */
internal var retryPauseMs = 700L

/** Retries [block] with a growing pause; cancellation is never swallowed. */
suspend fun <T> retrying(attempts: Int = 3, pauseMs: Long = retryPauseMs, block: suspend () -> T): T {
    var last: Throwable? = null
    repeat(attempts) { attempt ->
        try {
            return block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: PlaylistImportException) {
            if (error.error != ImportError.Network) throw error
            last = error
        } catch (error: Exception) {
            last = error
        }
        if (attempt < attempts - 1) delay(pauseMs * (attempt + 1))
    }
    throw last ?: IllegalStateException("retrying() ran zero attempts")
}

/**
 * Follows continuation tokens until the source says it is done. A page that still fails after the retries
 * aborts the import: returning what we have would silently drop the rest of the playlist.
 */
suspend fun <T> collectAllPages(
    first: List<T>,
    firstToken: String?,
    maxPages: Int = 1000,
    onProgress: (Int) -> Unit = {},
    fetchNext: suspend (String) -> Pair<List<T>, String?>
): List<T> {
    val all = ArrayList<T>(first)
    onProgress(all.size)
    var token = firstToken
    val seen = HashSet<String>()
    var pages = 0
    while (token != null && pages++ < maxPages && seen.add(token)) {
        val (items, next) = try {
            retrying { fetchNext(token!!) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw PlaylistImportException(
                ImportError.Network,
                "Connection lost after ${all.size} tracks. Check your connection and retry.",
                error
            )
        }
        all += items
        onProgress(all.size)
        token = next
    }
    return all
}

/** A catalogue candidate for a wanted track. */
data class Candidate<T>(val title: String, val artist: String, val durationMs: Long?, val payload: T)

private val EDITION_PAREN = Regex("""\s[(\[].*?[)\]]""")
private val EDITION_DASH = Regex("""\s[-–—|/].*""")
private val EDITION_FEAT = Regex("""\b(feat|ft|featuring)\b.*""")
private val SPACES = Regex("""\s+""")

private fun coreTitle(raw: String): String {
    var v = " $raw".lowercase()
    v = v.replace(EDITION_PAREN, " ").replace(EDITION_DASH, " ").replace(EDITION_FEAT, " ")
    return normalizeForMatch(v).replace(SPACES, " ")
}

/**
 * Picks the candidate that really is the wanted song, or null. The title must match (ignoring
 * "(Live)", "- Remastered" style tails) and either an artist word or the duration (within 6 s) must agree.
 */
fun <T> pickBestMatch(
    title: String,
    artist: String,
    durationMs: Long?,
    candidates: List<Candidate<T>>
): Candidate<T>? {
    val wantTitle = coreTitle(title)
    if (wantTitle.isEmpty()) return null
    val wantArtists = normalizeForMatch(artist).split(' ').filter { it.length > 2 }

    return candidates.mapIndexedNotNull { index, c ->
        val have = coreTitle(c.title)
        val titleScore = when {
            have == wantTitle -> 3
            have.isNotEmpty() && (have.contains(wantTitle) || wantTitle.contains(have)) -> 1
            else -> return@mapIndexedNotNull null
        }
        val haveArtist = normalizeForMatch(c.artist)
        val artistOk = wantArtists.isEmpty() || wantArtists.any { it in haveArtist }
        val durationOk = durationMs != null && c.durationMs != null && kotlin.math.abs(durationMs - c.durationMs) <= 6_000
        if (!artistOk && !durationOk) return@mapIndexedNotNull null
        Triple(titleScore + (if (artistOk) 2 else 0) + (if (durationOk) 2 else 0), index, c)
    }.sortedWith(compareByDescending<Triple<Int, Int, Candidate<T>>> { it.first }.thenBy { it.second })
        .firstOrNull()?.third
}
