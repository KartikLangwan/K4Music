package com.k4music.android.k4


import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/**
 * Offline taste model.
 *
 * Everything is computed on-device from the user's own play history — no
 * account, no network. It is a small weighted scorer rather than a black box:
 * each candidate gets points for the signals we *can* observe locally, and the
 * winning signal is reported back as the "why" shown under the card.
 *
 * Signals, in rough order of weight:
 *  - artist affinity, weighted by how recently that artist was played;
 *  - album affinity (same, for people who work through whole albums);
 *  - mood/genre tags inferred from title + album + artist keywords;
 *  - duration similarity (songs people play together tend to be similar length);
 *  - overall popularity in this user's own history, and favourites.
 *
 * ponytail: keyword mood buckets, not audio analysis — real audio features would
 * need a model file and a decode pass. Swap [moodTagsOf] for an embedding lookup
 * if that is ever worth the APK size.
 */
object K4Recommend {

    /**
     * One listening event. [weight] scales the recency weight (1.0 = a normal
     * listen); the autoplay anchor uses 0.5 so the finished track suggests the
     * tail instead of dictating it.
     */
    data class Played(
        val song: K4Song,
        val playedAt: Long,
        val weight: Double = 1.0
    )

    data class Recommendation(
        val song: K4Song,
        val score: Double,
        val reason: String
    )

    private const val HALF_LIFE_EVENTS = 12.0

    /** Keyword → mood bucket. Matched against "artist album title" as whole words. */
    private val MOOD_KEYWORDS: Map<String, List<String>> = mapOf(
        "chill" to listOf("chill", "lofi", "lo-fi", "ambient", "relax", "calm", "sleep", "soothing", "peace", "downtempo", "lounge"),
        "workout" to listOf("workout", "gym", "beast", "power", "energy", "pump", "hardcore", "hiit", "cardio"),
        "party" to listOf("party", "dance", "club", "bash", "rave", "floor", "boogie", "disco", "edm", "house", "techno"),
        "romance" to listOf("love", "romance", "heart", "pyaar", "ishq", "dil", "jashn", "romantic", "valentine"),
        "sad" to listOf("sad", "alone", "cry", "broken", "miss", "grief", "tears", "heartbreak", "melancholy"),
        "devotional" to listOf("bhajan", "aarti", "devotional", "praise", "worship", "mantra", "prasad", "spiritual", "kirtan"),
        "retro" to listOf("old", "classic", "retro", "vintage", "unplugged", "acoustic", "90s", "80s", "70s", "golden"),
        "bollywood" to listOf("bollywood", "hindi", "remix", "dj", "film", "movie", "soundtrack", "ost"),
        "punjabi" to listOf("punjabi", "punjab", "bhangra", "giddha", "folk"),
        "rap" to listOf("rap", "hiphop", "hip hop", "trap", "freestyle", "drill", "grime"),
        "indie" to listOf("indie", "alternative", "folk", "singer-songwriter", "bedroom pop"),
        "rnb" to listOf("r&b", "rnb", "soul", "neo-soul", "quiet storm"),
        "rock" to listOf("rock", "metal", "punk", "grunge", "alternative rock", "hard rock"),
        "electronic" to listOf("electronic", "synthwave", "synth", "electro", "idm", "ambient electronic"),
        "jazz" to listOf("jazz", "bebop", "swing", "fusion", "smooth jazz"),
        "classical" to listOf("classical", "orchestral", "symphony", "concerto", "sonata", "piano", "violin"),
        "reggae" to listOf("reggae", "dub", "ska", "dancehall"),
        "latin" to listOf("latin", "reggaeton", "salsa", "bachata", "merengue", "cumbia"),
        "kpop" to listOf("kpop", "k-pop", "korean", "kpop girl group", "kpop boy group"),
        "tamil" to listOf("tamil", "kollywood", "tamil cinema"),
        "telugu" to listOf("telugu", "tollywood", "telugu cinema"),
    )

    private fun textOf(s: K4Song): String =
        (s.artist + " " + s.album + " " + s.title).lowercase()

    /** Mood buckets present in a song's metadata. */
    fun moodTagsOf(s: K4Song): Set<String> {
        val words = textOf(s).split(Regex("\\s+")).toSet()
        return MOOD_KEYWORDS.filterValues { keys -> keys.any { it in words } }.keys
    }

    /** Newer listens weigh more; a half-life of [HALF_LIFE_EVENTS] keeps it stable. */
    private fun recencyWeight(index: Int): Double =
        Math.pow(0.5, index / HALF_LIFE_EVENTS)

    private fun normalize(s: String): String =
        s.trim().lowercase()

    private class Profile(
        val artist: Map<String, Double>,
        val album: Map<String, Double>,
        val moods: Map<String, Double>,
        val avgDurationMs: Long,
        val totalWeight: Double
    )

    private fun buildProfile(history: List<Played>): Profile {
        val artist = HashMap<String, Double>()
        val album = HashMap<String, Double>()
        val moods = HashMap<String, Double>()
        var durationSum = 0.0
        var durationWeight = 0.0
        var total = 0.0

        history.forEachIndexed { index, played ->
            val w = recencyWeight(index) * played.weight
            total += w
            artist.merge(normalize(played.song.artist), w, Double::plus)
            album.merge(normalize(played.song.album), w, Double::plus)
            for (tag in moodTagsOf(played.song)) moods.merge(tag, w, Double::plus)
            val d = played.song.durationMs
            if (d > 0) {
                durationSum += d * w
                durationWeight += w
            }
        }
        return Profile(artist, album, moods, (durationSum / durationWeight).toLong(), total)
    }

    private fun bonusFor(
        candidate: K4Song,
        p: Profile,
        playCounts: Map<String, Int>,
        plays: Int
    ): Pair<Double, String> {
        var score = 0.0
        var reason = ""

        // Capped with diminishing returns: an uncapped artist term meant one
        // same-artist hit (+3.0) beat every other signal combined, so autoplay
        // was ten tracks by one artist. Now a different artist with two shared
        // moods + a favourite flag can win.
        val a = p.artist[normalize(candidate.artist)]
        if (a != null && a > 0) {
            score += 2.0 * minOf(a, 1.5)
            reason = "Because you play ${candidate.artist}"
        }

        val al = p.album[normalize(candidate.album)]
        if (al != null && al > 0) {
            score += 1.2 * minOf(al, 1.5)
            if (reason.isEmpty()) reason = "More from ${candidate.album}"
        }

        val tags = moodTagsOf(candidate)
        if (tags.isNotEmpty() && p.moods.isNotEmpty()) {
            val shared = tags.sumOf { p.moods[it] ?: 0.0 }
            if (shared > 0) {
                score += 0.8 * shared
                if (reason.isEmpty()) {
                    reason = "Fits your ${tags.first()} mood"
                }
            }
        }

        if (p.avgDurationMs > 0 && candidate.durationMs > 0) {
            val diff = abs(candidate.durationMs - p.avgDurationMs).toDouble() / p.avgDurationMs
            if (diff < 0.5) {
                score += 0.6 * (1.0 - diff * 2.0)
                if (reason.isEmpty()) reason = "Similar length to your picks"
            }
        }

        val count = playCounts[candidate.id] ?: 0
        if (count > 0) {
            // log keeps one much-repeated song from swamping everything.
            score += 0.4 * ln(1.0 + count.toDouble())
            if (reason.isEmpty()) reason = "You have played this before"
        }

        if (candidate.isFavorite) {
            score += 1.5
            if (reason.isEmpty()) reason = "One of your favourites"
        }

        // Mild boost for songs nobody here has played yet: exploration.
        if (count == 0 && plays > 0) score += 0.25

        return score to reason
    }

    // ===================== AUTOPLAY SIMILARITY SCORING =====================
    // Scores a candidate song against a SEED song (current track) for autoplay.
    // This is DIFFERENT from the taste-model scoring: it measures musical/contextual
    // similarity between two tracks, not affinity to user's overall history.

    /** Weights for seed-based similarity scoring (tunable constants). */
    private const val W_ARTIST_SAME = 3.0      // Same artist
    private const val W_ARTIST_RELATED = 1.5   // Related artist (same mood/genre)
    private const val W_GENRE_MATCH = 2.0      // Shared mood/genre tags
    private const val W_LANGUAGE_MATCH = 1.5   // Same detected language
    private const val W_ALBUM_MATCH = 1.0      // Same album
    private const val W_ERA_MATCH = 0.5        // Same era
    private const val W_DURATION_SIM = 0.5     // Similar duration
    private const val W_FAVORITE_BONUS = 0.5   // User favorited this track
    private const val W_PLAYCOUNT_BONUS = 0.3  // User has played this before
    private const val W_DISCOVERY_BONUS = 0.2  // Never played before (exploration)

    // Penalties
    private const val P_TITLE_SIMILARITY = -1.0    // Penalize title-only similarity
    private const val P_SAME_ARTIST_EXCESS = -2.0  // Too many from same artist
    private const val P_RECENT_PLAY = -3.0         // Recently played
    private const val P_DUPLICATE = -10.0          // Duplicate track

    /**
     * Computes similarity score between a candidate and the seed (current) song.
     * Higher = more musically similar. Title similarity is PENALIZED.
     */
    fun seedSimilarityScore(
        candidate: K4Song,
        seed: K4Song,
        playCounts: Map<String, Int>,
        recentPrints: Set<String>,
        artistCounts: Map<String, Int>
    ): Pair<Double, String> {
        var score = 0.0
        val reasons = mutableListOf<String>()

        // 1. Same artist (strong signal but capped to avoid domination)
        val sameArtist = normalize(candidate.artist) == normalize(seed.artist)
        if (sameArtist) {
            val artistCount = artistCounts.getOrDefault(normalize(candidate.artist), 0)
            if (artistCount < MAX_PER_ARTIST_AUTOPLAY) {
                score += W_ARTIST_SAME
                reasons += "Same artist: ${candidate.artist}"
            } else {
                score += P_SAME_ARTIST_EXCESS
            }
        }

        // 2. Genre/mood overlap — primary musical similarity signal
        val seedMoods = moodTagsOf(seed)
        val candMoods = moodTagsOf(candidate)
        val sharedMoods = seedMoods.intersect(candMoods)
        if (sharedMoods.isNotEmpty()) {
            score += W_GENRE_MATCH * sharedMoods.size
            reasons += "Shared mood: ${sharedMoods.joinToString(", ")}"
        }

        // 3. Language match
        val seedLang = detectLanguage(seed)
        val candLang = detectLanguage(candidate)
        if (seedLang == candLang && seedLang != "english") { // english is default/fallback
            score += W_LANGUAGE_MATCH
            reasons += "Language: $seedLang"
        }

        // 4. Same album
        val sameAlbum = normalize(candidate.album) == normalize(seed.album) &&
                        candidate.album.isNotBlank() &&
                        !candidate.album.equals("Unknown Album", ignoreCase = true)
        if (sameAlbum) {
            score += W_ALBUM_MATCH
            reasons += "Same album: ${candidate.album}"
        }

        // 5. Era match
        val seedEra = detectEra(seed)
        val candEra = detectEra(candidate)
        if (seedEra.isNotBlank() && seedEra == candEra) {
            score += W_ERA_MATCH
            reasons += "Era: $seedEra"
        }

        // 6. Duration similarity
        if (seed.durationMs > 0 && candidate.durationMs > 0) {
            val diff = abs(candidate.durationMs - seed.durationMs).toDouble() / seed.durationMs
            if (diff < 0.3) {
                score += W_DURATION_SIM * (1.0 - diff / 0.3)
                reasons += "Similar length"
            }
        }

        // 7. User engagement signals
        val count = playCounts[candidate.id] ?: 0
        if (count > 0) {
            score += W_PLAYCOUNT_BONUS * ln(1.0 + count.toDouble())
            reasons += "Played before"
        }
        if (candidate.isFavorite) {
            score += W_FAVORITE_BONUS
            reasons += "Favorite"
        }
        if (count == 0) {
            score += W_DISCOVERY_BONUS
            reasons += "New discovery"
        }

        // 8. PENALTY: Title similarity (different songs with similar titles are NOT similar)
        val titleSim = titleSimilarity(normalize(candidate.title), normalize(seed.title))
        if (titleSim > 0.6 && !sameArtist) {
            score += P_TITLE_SIMILARITY * titleSim
            reasons += "Title similarity penalty"
        }

        // 9. PENALTY: Recently played (by fingerprint)
        val candPrint = trackFingerprint(candidate)
        if (candPrint in recentPrints) {
            score += P_RECENT_PLAY
            reasons += "Recently played"
        }

        // 10. PENALTY: Exact duplicate fingerprint
        if (candPrint == trackFingerprint(seed)) {
            score += P_DUPLICATE
            reasons += "Duplicate of current"
        }

        return score to reasons.joinToString("; ")
    }

    /**
     * Jaccard-like title similarity (0.0 to 1.0).
     * Used only for PENALTY — we want DIFFERENT titles.
     */
    private fun titleSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val wordsA = a.split(Regex("\\s+")).toSet()
        val wordsB = b.split(Regex("\\s+")).toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0
        val intersection = wordsA.intersect(wordsB).size
        val union = wordsA.union(wordsB).size
        return intersection.toDouble() / union
    }

    /** At most this many tracks by one artist survive in an autoplay tail. */
    const val MAX_PER_ARTIST_AUTOPLAY = 2

    /** At most this many tracks sharing the same primary mood survive. */
    const val MAX_PER_MOOD_AUTOPLAY = 3

    /**
     * Diversity pass: walks the ranked list and keeps at most
     * [MAX_PER_ARTIST_AUTOPLAY] tracks per (normalised) artist and
     * [MAX_PER_MOOD_AUTOPLAY] per primary mood, preserving order, then tops up
     * with whatever is left so the tail stays full. Without this the top of the
     * ranking was routinely one artist or one mood.
     */
    fun diversify(picks: List<Recommendation>): List<Recommendation> {
        val kept = ArrayList<Recommendation>(picks.size)
        val deferred = ArrayList<Recommendation>()
        val perArtist = HashMap<String, Int>()
        val perMood = HashMap<String, Int>()
        for (r in picks) {
            val artistKey = normalize(r.song.artist)
            val artistCount = perArtist.getOrDefault(artistKey, 0)
            // Primary mood for diversity (first mood tag)
            val moodKey = moodTagsOf(r.song).firstOrNull() ?: ""
            val moodCount = perMood.getOrDefault(moodKey, 0)

            if (artistCount < MAX_PER_ARTIST_AUTOPLAY && moodCount < MAX_PER_MOOD_AUTOPLAY) {
                kept += r
                perArtist[artistKey] = artistCount + 1
                if (moodKey.isNotBlank()) perMood[moodKey] = moodCount + 1
            } else {
                deferred += r
            }
        }
        kept += deferred
        return kept
    }

    /**
     * Personalised picks for a shelf or an autoplay queue.
     *
     * @param excludeIds ids to never return (already queued, just played).
     * @param excludeOffline drop music the user already owns on the device —
     *   both MediaStore rows ([K4Song.isLocal]) and downloaded tracks
     *   ([K4Song.filePath]). Home is meant to surface music you *don't* have,
     *   like a catalogue would; owned music lives in Library/Downloads.
     */
    fun recommend(
        library: List<K4Song>,
        history: List<Played>,
        playCounts: Map<String, Int> = emptyMap(),
        excludeIds: Set<String> = emptySet(),
        excludeOffline: Boolean = true,
        limit: Int = 12,
        // Track fingerprints to exclude as well as ids. The same song exists
        // under several catalogue ids (yt_…, saavn_…, the MediaStore row), so
        // id-only exclusion let it straight back in through another source.
        excludePrints: Set<String> = emptySet()
    ): List<Recommendation> {
        if (library.isEmpty() || limit <= 0) return emptyList()
        val seen = excludeIds.toMutableSet()
        val candidates = library.filter { s ->
            if (s.id in seen) false
            else if (excludeOffline && s.isOwnedLocally()) false
            else if (trackFingerprint(s) in excludePrints) false
            else true
        }
        // Same track under two ids must not occupy two slots either.
        val deduped = candidates.distinctBy { trackFingerprint(it) }
        if (deduped.isEmpty()) return emptyList()

        if (history.isEmpty()) {
            // No taste data yet: favourites first, then a stable, non-random pick
            // so the shelf does not reshuffle on every recomposition.
            return candidates
                .sortedWith(
                    compareByDescending<K4Song> { it.isFavorite }
                        .thenByDescending { playCounts[it.id] ?: 0 }
                        .thenBy { it.title.lowercase() }
                )
                .take(limit)
                .map { Recommendation(it, 0.0, "Picked for you") }
        }

        val p = buildProfile(history)
        val scored = ArrayList<Recommendation>(deduped.size)
        for (s in deduped) {
            val (bonus, reason) = bonusFor(s, p, playCounts, p.totalWeight.toInt())
            // Small deterministic tie-break so equal scores don't shuffle.
            val jitter = ((s.id.hashCode().ushr(1) % 1000) / 1000.0) * 0.01
            scored += Recommendation(s, bonus + jitter, reason.ifEmpty { "Picked for you" })
        }
        return diversify(
            scored.sortedWith(compareByDescending<Recommendation> { it.score })
        ).take(limit)
    }

    /**
     * Autoplay picks: everything you just heard is excluded by id *and* by
     * track fingerprint, so a queue never repeats itself even when the same
     * song arrives under another provider's id. The finished track anchors the
     * profile so the session stays coherent — but the artist term is capped and
     * a diversity pass spreads the tail across artists, so "similar" wins over
     * "same".
     */
    fun autoplayNext(
        library: List<K4Song>,
        history: List<Played>,
        playCounts: Map<String, Int> = emptyMap(),
        current: K4Song?,
        recentIds: Set<String> = emptySet(),
        excludeOffline: Boolean = false,
        limit: Int = 10,
        excludePrints: Set<String> = emptySet()
    ): List<Recommendation> {
        val anchor = mutableListOf<Played>()
        if (current != null) {
            // The finished track anchors the profile. Weight 1.2 is strong enough
            // to promote its artist/mood above recent history (which gets ~0.94
            // at index 1), but the artist cap (2.0 * minOf(a, 1.5) = max 3.0)
            // prevents total domination. Old weight 1.0 was too strong; 0.5 too weak.
            anchor += Played(current, Long.MAX_VALUE, weight = 1.2)
        }
        anchor += history
        val exclude = recentIds + setOfNotNull(current?.id)
        val prints = excludePrints + setOfNotNull(current?.let { trackFingerprint(it) })
        return recommend(
            library = library,
            history = anchor,
            playCounts = playCounts,
            excludeIds = exclude,
            excludeOffline = excludeOffline,
            limit = limit,
            excludePrints = prints
        )
    }

    /**
     * "Popular right now", from this user's own listening: most played first,
     * favourites breaking ties. Never returns music already on the device.
     */
    fun popular(
        library: List<K4Song>,
        playCounts: Map<String, Int> = emptyMap(),
        limit: Int = 12,
        excludeOffline: Boolean = true
    ): List<K4Song> = library
        .filter { if (excludeOffline && it.isOwnedLocally()) false else true }
        .filter { (playCounts[it.id] ?: 0) > 0 || it.isFavorite }
        .sortedWith(
            compareByDescending<K4Song> { playCounts[it.id] ?: 0 }
                .thenByDescending { it.isFavorite }
                .thenBy { it.title.lowercase() }
        )
        .take(max(1, limit))

    /**
     * Generates search queries that find *different* songs in the same musical style.
     * NEVER uses the current song's title or artist name as a query — those return
     * the same song or same artist's catalogue. Instead, we derive queries from
     * mood/genre tags and album context only.
     *
     * Priority order (first = highest priority):
     * 1. Mood/genre + "music" — "chill music", "bollywood music" — finds different artists in same style
     * 2. Mood + language — "hindi chill music", "english electronic music" — more specific style
     * 3. Genre + era — "90s bollywood", "2020s electronic" — contextual discovery
     * 4. Album-only query — finds other tracks from the same album (different songs)
     */
    fun similarQueriesFor(song: K4Song?, limit: Int = 5): List<String> {
        if (song == null) return emptyList()
        val out = LinkedHashSet<String>()

        val moods = moodTagsOf(song)

        // 1. Pure mood/genre queries — these find DIFFERENT artists in the same style
        moods.forEach { out += "$it music" }

        // 2. Mood + language combos for more specific style matching
        val language = detectLanguage(song)
        if (language.isNotBlank()) {
            moods.forEach { out += "$language $it music" }
        }

        // 3. Genre + era combos
        val era = detectEra(song)
        if (era.isNotBlank()) {
            moods.forEach { out += "$era $it music" }
        }

        // 4. Album query — same album, usually different tracks (but only if not "Unknown Album")
        val album = song.album.trim()
        if (album.isNotEmpty() &&
            !album.equals("Unknown Album", ignoreCase = true) &&
            album.lowercase() != song.artist.lowercase()
        ) out += album

        // NO artist query — that returns the same artist's catalogue

        return out.take(limit)
    }

    /**
     * Simple language detection from artist/title/album text.
     * Returns broad language category for query augmentation.
     */
    private fun detectLanguage(song: K4Song): String {
        val text = "${song.title} ${song.artist} ${song.album}".lowercase()
        return when {
            text.matches(Regex(".*[\\u0900-\\u097F].*")) -> "hindi"
            text.matches(Regex(".*[\\u0B80-\\u0BFF].*")) -> "tamil"
            text.matches(Regex(".*[\\u0C00-\\u0C7F].*")) -> "telugu"
            text.matches(Regex(".*[\\u0A00-\\u0A7F].*")) -> "punjabi"
            text.matches(Regex(".*[\\u0600-\\u06FF].*")) -> "arabic"
            text.matches(Regex(".*[\\u4e00-\\u9fff].*")) -> "chinese"
            text.matches(Regex(".*[\\u3040-\\u30ff].*")) -> "japanese"
            text.matches(Regex(".*[\\uac00-\\ud7af].*")) -> "korean"
            else -> "english"
        }
    }

    /**
     * Simple era detection from title/album keywords.
     */
    private fun detectEra(song: K4Song): String {
        val text = "${song.title} ${song.album}".lowercase()
        return when {
            text.matches(Regex(".*\\b(90s|199\\d)\\b.*")) -> "90s"
            text.matches(Regex(".*\\b(80s|198\\d)\\b.*")) -> "80s"
            text.matches(Regex(".*\\b(2000s|200\\d)\\b.*")) -> "2000s"
            text.matches(Regex(".*\\b(2010s|201\\d)\\b.*")) -> "2010s"
            text.matches(Regex(".*\\b(2020s|202\\d)\\b.*")) -> "2020s"
            text.matches(Regex(".*\\b(retro|classic|oldies|golden)\\b.*")) -> "retro"
            else -> ""
        }
    }

    /** The list to hand to [PlayerManager.playSongs] for an autoplay tail. */
    fun autoplayQueue(
        library: List<K4Song>,
        history: List<Played>,
        playCounts: Map<String, Int> = emptyMap(),
        current: K4Song?,
        recentIds: Set<String> = emptySet(),
        excludeOffline: Boolean = false,
        excludePrints: Set<String> = emptySet(),
        size: Int = 10
    ): List<K4Song> = autoplayNext(
        library, history, playCounts, current, recentIds, excludeOffline, size, excludePrints
    ).map { it.song }
}

// trackFingerprint() + K4Song.isOwnedLocally() live in K4Models.kt (single canonical copy).
