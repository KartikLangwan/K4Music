package com.k4music.android.k4

import com.k4music.android.models.Song as DbSong

/**
 * K4-side song row, ported from the K4Audio media row.
 *
 * Kept separate from K4Audio's Room [DbSong] on purpose: the merged app must
 * not change any existing Room schema (that would need a migration), so K4
 * logic works on this plain row and maps in/out at the boundary.
 */
data class K4Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val artworkUri: String?,
    val isLocal: Boolean = false,
    val isFavorite: Boolean = false,
    val filePath: String? = null,
    /** Proven existing library/cache key; catalogue id above remains unchanged. */
    val playbackKey: String? = null
)

fun K4Song.isOwnedLocally(): Boolean =
    isLocal || !filePath.isNullOrBlank() || contentUri.startsWith("file://")

fun onlineSourceOf(song: K4Song): String = when {
    song.id.startsWith("yt_") -> "YouTube"
    song.id.startsWith("saavn_") -> "JioSaavn"
    song.id.startsWith("local_") -> "Device"
    else -> "Online"
}

private val ONLINE_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
private val COMBINING_MARKS = Regex("\\p{Mn}+")

fun normalizeForMatch(raw: String): String {
    val decomposed = java.text.Normalizer.normalize(raw.lowercase(), java.text.Normalizer.Form.NFD)
    return decomposed.replace(COMBINING_MARKS, "")
        .replace(ONLINE_SEPARATORS, " ")
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

/** Closest match first, duplicate title+artist rows removed. Pure + deterministic. */
fun rankOnlineResults(query: String, songs: List<K4Song>): List<K4Song> {
    val q = normalizeForMatch(query)
    if (q.isEmpty()) return songs
    val tokens = q.split(' ').filter { it.isNotEmpty() }
    val scored = songs.mapIndexed { index, song ->
        val title = normalizeForMatch(song.title)
        val artist = normalizeForMatch(song.artist)
        var score = 0
        when {
            title == q -> score += 300
            title.startsWith(q) -> score += 180
            title.contains(" $q") -> score += 140
            title.contains(q) -> score += 90
        }
        when {
            artist == q -> score += 120
            artist.startsWith(q) -> score += 70
            artist.contains(" $q") -> score += 50
            artist.contains(q) -> score += 35
        }
        for (t in tokens) {
            when {
                title.startsWith(t) -> score += 18
                title.contains(" $t") -> score += 12
            }
            if (artist.contains(t)) score += 8
        }
        score -= (title.length - q.length).coerceAtLeast(0) / 8
        Triple(score, index, song)
    }
    return scored
        .sortedWith(compareByDescending<Triple<Int, Int, K4Song>> { it.first }.thenBy { it.second })
        .map { it.third }
        .distinctBy { normalizeForMatch(it.title) + "|" + normalizeForMatch(it.artist) }
}

/**
 * YouTube FIRST, JioSaavn second (locked merge decision).
 *
 * De-dup happens before ranking so a track on both catalogues survives once,
 * as the YouTube copy; the merged list is then ranked and capped.
 */
fun mergeOnlineResults(
    query: String,
    youtube: List<K4Song>,
    jiosaavn: List<K4Song>,
    limit: Int = Int.MAX_VALUE
): List<K4Song> {
    if (jiosaavn.isEmpty()) return rankOnlineResults(query, youtube).take(limit)
    if (youtube.isEmpty()) return rankOnlineResults(query, jiosaavn).take(limit)
    val seen = HashSet<String>(youtube.size + jiosaavn.size)
    val merged = ArrayList<K4Song>(youtube.size + jiosaavn.size)
    for (song in youtube + jiosaavn) {
        val key = normalizeForMatch(song.title) + "|" + normalizeForMatch(song.artist)
        if (seen.add(key)) merged.add(song)
    }
    return rankOnlineResults(query, merged).take(limit)
}

private val PAREN_TAIL_RE =
    Regex("""\s[\(\[\{][^\)\]\}]*\b(feat|ft|featuring|with|remix|remixed|edit|mix|live|acoustic|unplugged|version|cover|slowed|sped ?up|8d|reverb|lullaby|karaoke|orchestral|reprise|remaster\w*|deluxe|bonus track|film version|female version|male version)\b[^\)\]\}]*[\)\]\}]""")
private val DASH_TAIL_RE =
    Regex("""\s[-–—|/]\s*\b(feat|ft|featuring|with|remix|remixed|edit|mix|live|acoustic|unplugged|version|cover|slowed|sped ?up|8d|reverb|lullaby|karaoke|orchestral|reprise|remaster\w*|deluxe|bonus track|film version|female version|male version)\b.*""")
private val FEAT_TAIL_RE = Regex("""\s\b(feat\.?|ft\.?|featuring)\b.*""")
private val EDITION_WORD_RE = Regex("""\b(remaster\w*|deluxe edition|bonus track|film version)\b""")
private val NON_WORD_RE = Regex("""[^\p{L}\p{N} ]+""")
private val WS_RE = Regex("""\s+""")

/** Canonical track identity across catalogues: edition-stripped "title|artist". */
fun trackFingerprint(song: K4Song): String {
    fun clean(value: String): String {
        var v = " $value".lowercase()
        v = v.replace(PAREN_TAIL_RE, " ")
        v = v.replace(DASH_TAIL_RE, " ")
        v = v.replace(FEAT_TAIL_RE, " ")
        v = v.replace(EDITION_WORD_RE, " ")
        v = v.replace(NON_WORD_RE, " ")
        return v.trim().replace(WS_RE, " ")
    }
    val title = clean(song.title).ifEmpty { song.title.trim().lowercase() }
    val artist = clean(song.artist).ifEmpty { song.artist.trim().lowercase() }
    return "$title|$artist"
}

fun parseClockToMs(value: String): Long {
    var total = 0L
    for (part in value.split(':')) {
        val n = part.toIntOrNull() ?: return 0L
        total = total * 60 + n
    }
    return total * 1000L
}

/** K4Audio Room row -> K4 row (lossy: artist/album collapse to text, duration parsed). */
fun DbSong.toK4(favorite: Boolean = likedAt != null): K4Song = K4Song(
    id = id,
    title = title,
    artist = artistsText ?: "Unknown Artist",
    album = "Unknown Album",
    durationMs = durationText?.let { parseClockToMs(it) } ?: 0L,
    contentUri = "yt:$id",
    artworkUri = thumbnailUrl,
    isLocal = false,
    isFavorite = favorite
)
