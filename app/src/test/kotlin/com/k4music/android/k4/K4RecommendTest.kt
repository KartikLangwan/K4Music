package com.k4music.android.k4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offline taste model. Locks the three properties the product depends on:
 * recommendations follow the user's own artists, they never repeat what was
 * just played, and Home never surfaces offline/device tracks.
 */
class K4RecommendTest {

    private fun song(
        id: String,
        artist: String,
        album: String = "Album",
        title: String = "Track $id",
        durationMs: Long = 200_000,
        isLocal: Boolean = false,
        isFavorite: Boolean = false,
        filePath: String? = null
    ) = K4Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        contentUri = "https://cdn.example/$id.m4a",
        artworkUri = null,
        isLocal = isLocal,
        isFavorite = isFavorite,
        filePath = filePath
    )

    private fun played(vararg songs: K4Song) = songs.mapIndexed { i, s ->
        K4Recommend.Played(s, playedAt = 1_000L - i)
    }

    private val library = listOf(
        song("a1", artist = "Neon Drive", album = "Midnight Run"),
        song("a2", artist = "Neon Drive", album = "Midnight Run"),
        song("b1", artist = "Velvet Static", album = "Slow Burn"),
        song("c1", artist = "Paper Radio", album = "Daylight"),
        song("d1", artist = "Glass Harbor", album = "Tides", durationMs = 60_000)
    )

    @Test
    fun recommendsTheArtistsTheUserActuallyPlays() {
        val heard = played(song("a1", "Neon Drive", "Midnight Run"))
        val result = K4Recommend.recommend(
            library = library,
            history = heard,
            excludeIds = setOf("a1")
        )
        assertEquals("a2", result.first().song.id)
        assertTrue(result.first().reason.contains("Neon Drive"))
    }

    @Test
    fun neverRepeatsSomethingAlreadyPlayed() {
        val heard = played(song("a1", "Neon Drive"), song("b1", "Velvet Static"))
        val ids = K4Recommend.recommend(library, heard, excludeIds = setOf("a1", "b1"))
            .map { it.song.id }
        assertFalse(ids.contains("a1"))
        assertFalse(ids.contains("b1"))
    }

    @Test
    fun homeShelvesExcludeOfflineAndDownloadedSongs() {
        val owned = library +
            song("loc", "Device Artist", isLocal = true) +
            song("dl", "Downloaded Artist", filePath = "/music/dl.mp3")
        val result = K4Recommend.recommend(owned, played(song("a1", "Neon Drive")))
        assertFalse("MediaStore rows belong in Library", result.any { it.song.isLocal })
        assertFalse("downloaded tracks belong in Library/Downloads", result.any { it.song.filePath != null })
        val withOwned = K4Recommend.recommend(
            owned, played(song("a1", "Neon Drive")), excludeOffline = false
        )
        assertTrue(withOwned.any { it.song.isLocal })
        assertTrue(withOwned.any { it.song.filePath != null })
    }

    @Test
    fun popularAlsoHidesDownloadedTracks() {
        val owned = library + song("dl", "Downloaded Artist", filePath = "/music/dl.mp3")
        val result = K4Recommend.popular(
            library = owned,
            playCounts = mapOf("b1" to 9, "dl" to 20),
            limit = 10
        ).map { it.id }
        assertFalse(result.contains("dl"))
        assertTrue(result.contains("b1"))
    }

    @Test
    fun similarQueriesDescribeTheTrackNotRepeatIt() {
        val queries = K4Recommend.similarQueriesFor(
            song("c", artist = "Neon Drive", album = "Midnight Run", title = "Chill Night Drive")
        )
        assertTrue("has chill mood: $queries", queries.contains("chill music"))
        assertTrue("has Midnight Run album: $queries", queries.contains("Midnight Run"))
        assertFalse("should not contain artist Neon Drive: $queries", queries.contains("Neon Drive"))
        assertEquals(queries.distinct().size, queries.size)
        assertTrue(K4Recommend.similarQueriesFor(null).isEmpty())
    }

    @Test
    fun similarQueriesSkipUnknownLabels() {
        val queries = K4Recommend.similarQueriesFor(
            song("u", artist = "Unknown Artist", album = "Unknown Album", title = "Lo-Fi Rain")
        )
        assertFalse(queries.contains("Unknown Artist"))
        assertFalse(queries.contains("Unknown Album"))
        assertTrue("mood keyword should still seed the search", queries.isNotEmpty())
    }

    @Test
    fun autoplayExcludesTheCurrentSongAndRecentOnes() {
        val current = song("a2", "Neon Drive", "Midnight Run")
        val picks = K4Recommend.autoplayNext(
            library = library,
            history = played(song("a1", "Neon Drive", "Midnight Run")),
            current = current,
            recentIds = setOf("a1")
        ).map { it.song.id }
        assertFalse("must not replay the current track", picks.contains("a2"))
        assertFalse("must not replay a recent track", picks.contains("a1"))
        assertTrue("should still produce a tail", picks.isNotEmpty())
    }

    @Test
    fun autoplayLeadsWithTheCurrentArtist() {
        val current = song("b1", "Velvet Static", "Slow Burn")
        val picks = K4Recommend.autoplayNext(
            library = library + song("b2", "Velvet Static", "Slow Burn", title = "Slow Burn II"),
            history = played(song("a1", "Neon Drive")),
            current = current
        )
        assertEquals("Velvet Static", picks.first().song.artist)
        assertNotEquals("the finished track itself must not repeat", "b1", picks.first().song.id)
    }

    @Test
    fun popularRanksByPlayCountAndSkipsOffline() {
        val lib = library + song("loc", "Device", isLocal = true)
        val result = K4Recommend.popular(
            library = lib,
            playCounts = mapOf("b1" to 9, "c1" to 4),
            limit = 10
        ).map { it.id }
        assertEquals(listOf("b1", "c1"), result)
        assertFalse(result.contains("loc"))
    }

    @Test
    fun popularIsEmptyWithoutAnySignal() {
        assertTrue(K4Recommend.popular(library).isEmpty())
    }

    @Test
    fun resultsAreStableAcrossCalls() {
        val heard = played(song("a1", "Neon Drive"))
        val first = K4Recommend.recommend(library, heard).map { it.song.id }
        val second = K4Recommend.recommend(library, heard).map { it.song.id }
        assertEquals(first, second)
    }

    @Test
    fun moodTagsAreInferredFromMetadata() {
        val tags = K4Recommend.moodTagsOf(
            song("x", "Chill Beats", album = "Lo-Fi Study", title = "Late Night Drive")
        )
        assertTrue("expected chill mood", tags.contains("chill"))
        assertTrue(K4Recommend.moodTagsOf(song("y", "Party Crew", title = "Dance Floor Anthem")).contains("party"))
    }

    @Test
    fun similarMoodOutranksUnrelatedTracks() {
        val heard = played(song("h", "Chill Beats", album = "Lo-Fi Study", title = "Rainy Window"))
        val result = K4Recommend.recommend(
            library = library + song("m", "Chill Beats", album = "Lo-Fi Study", title = "Soft Rain"),
            history = heard,
            excludeIds = setOf("h")
        )
        assertNotEquals("unrelated artist should not top the list", "d1", result.first().song.id)
    }

    @Test
    fun emptyLibraryIsHandled() {
        assertTrue(K4Recommend.recommend(emptyList(), played()).isEmpty())
        assertTrue(K4Recommend.recommend(library, played(), limit = 0).isEmpty())
    }
}

/**
 * Autoplay identity. The same song arrives from different providers under
 * different ids; without a track-level fingerprint it walks straight back into
 * the queue and users hear "the same song" again.
 */
class K4TrackFingerprintTest {

    private fun song(id: String, title: String, artist: String) = K4Song(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        durationMs = 1000,
        contentUri = "https://x/$id",
        artworkUri = null,
        isLocal = false
    )

    @Test
    fun sameTrackFromDifferentSourcesMatches() {
        val fromYoutube = song("yt_abc", "Kesariya", "Arijit Singh")
        val fromSaavn = song("saavn_xyz", "kesariya ", "Arijit Singh")
        assertEquals(trackFingerprint(fromYoutube), trackFingerprint(fromSaavn))
    }

    @Test
    fun featuredAndRemasteredDecorationsAreStripped() {
        val plain = song("a", "Shape of You", "Ed Sheeran")
        val decorated = song("b", "Shape of You (feat. Zion)", "Ed Sheeran")
        val remastered = song("c", "Shape of You - Remastered 2011", "Ed Sheeran")
        assertEquals(trackFingerprint(plain), trackFingerprint(decorated))
        assertEquals(trackFingerprint(plain), trackFingerprint(remastered))
    }

    @Test
    fun genuinelyDifferentTracksDoNotCollide() {
        val a = song("a", "Believer", "Imagine Dragons")
        val b = song("b", "Believer", "Coldplay")
        assertNotEquals(trackFingerprint(a), trackFingerprint(b))
    }

    @Test
    fun nonLatinTitlesSurviveFingerprinting() {
        val hindi = song("h", "Kesariya", "Arijit Singh")
        val hindi2 = song("h2", "केसरिया", "Arijit Singh")
        assertTrue(trackFingerprint(hindi).isNotBlank())
        assertTrue(trackFingerprint(hindi2).isNotBlank())
    }
}
