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

    /** Alternatives for explicit user review only; never substitute these automatically. */
    suspend fun candidates(track: FailedTrack): List<Candidate<MediaItem>> = emptyList()

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
    if (token != null) throw PlaylistImportException(
        ImportError.Network,
        "Playlist loading stopped after ${all.size} tracks before the final page. Retry to import the complete playlist."
    )
    return all
}

/** A catalogue candidate for a wanted track. */
data class Candidate<T>(val title: String, val artist: String, val durationMs: Long?, val payload: T)

// Strip known presentation metadata only. Part numbers and subtitles identify different songs.
private val EDITION_PAREN = Regex("""\s[(\[]\s*(?:from\s+.*|(?:\d{4}\s+)?remaster(?:ed)?(?:\s+\d{4})?|official\s+(?:audio|video|music video)|lyrics?(?:\s+video)?)\s*[)\]]""", RegexOption.IGNORE_CASE)
private val EDITION_DASH = Regex("""\s[-–—|]\s*(?:(?:\d{4}\s+)?remaster(?:ed)?(?:\s+\d{4})?|official\s+(?:audio|video|music video)|lyrics?(?:\s+video)?)\s*$""", RegexOption.IGNORE_CASE)
private val EDITION_FEAT = Regex("""\b(feat|ft|featuring)\b.*""")
private val RECORDING_EDITION = Regex("""\b(live|remix|cover|karaoke|instrumental|acoustic|slowed|nightcore|sped up)\b""", RegexOption.IGNORE_CASE)
private val SPACES = Regex("""\s+""")

private fun coreTitle(raw: String): String {
    var v = " $raw".lowercase()
    v = v.replace(EDITION_PAREN, " ").replace(EDITION_DASH, " ").replace(EDITION_FEAT, " ")
    return normalizeForMatch(v).replace(SPACES, " ")
}

/**
 * Picks the candidate that really is the wanted song, or null. The title must match (ignoring
 * album/remaster tails); recording editions must agree. Artist phrases must match when both are
 * known; a missing artist can be corroborated by duration (within 6 s).
 */
fun <T> pickBestMatch(
    title: String,
    artist: String,
    durationMs: Long?,
    candidates: List<Candidate<T>>
): Candidate<T>? {
    val wantTitle = coreTitle(title)
    if (wantTitle.isEmpty()) return null
    val wantArtists = artist.split(Regex("""\s*(?:,|&|;|\bfeat\.?|\bfeaturing|\bft\.?)\s*""", RegexOption.IGNORE_CASE))
        .map(::normalizeForMatch).filter { it.isNotBlank() }
    val wantedEdition = RECORDING_EDITION.findAll(title).map { it.value.lowercase() }.toSet()

    return candidates.mapIndexedNotNull { index, c ->
        // Duration coincidence cannot turn a different title or recording edition into this song.
        if (coreTitle(c.title) != wantTitle) return@mapIndexedNotNull null
        val candidateEdition = RECORDING_EDITION.findAll(c.title).map { it.value.lowercase() }.toSet()
        if (candidateEdition != wantedEdition) return@mapIndexedNotNull null
        val haveArtist = normalizeForMatch(c.artist)
        val artistOk = wantArtists.any { " $it " in " $haveArtist " }
        val durationOk = durationMs != null && c.durationMs != null && kotlin.math.abs(durationMs - c.durationMs) <= 6_000
        if (durationMs != null && c.durationMs != null && kotlin.math.abs(durationMs - c.durationMs) > 10_000) return@mapIndexedNotNull null
        if (wantArtists.isNotEmpty() && haveArtist.isNotBlank() && !artistOk) return@mapIndexedNotNull null
        if (!artistOk && !durationOk) return@mapIndexedNotNull null
        Triple(3 + (if (artistOk) 2 else 0) + (if (durationOk) 2 else 0), index, c)
    }.sortedWith(compareByDescending<Triple<Int, Int, Candidate<T>>> { it.first }.thenBy { it.second })
        .firstOrNull()?.third
}
