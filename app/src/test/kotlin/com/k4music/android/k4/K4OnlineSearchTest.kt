package com.k4music.android.k4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure helpers behind search accuracy (YouTube-first merge).
 * Plain JUnit, no Android — runs in milliseconds on every build.
 */
class K4OnlineSearchTest {

    private fun online(id: String, title: String, artist: String, album: String = "-") = K4Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = 200_000,
        contentUri = "https://aac.saavncdn.com/248/${id}_96.mp4",
        artworkUri = null,
        isLocal = false
    )

    @Test
    fun normalizeIgnoresCaseAccentsAndPunctuation() {
        assertEquals("beyonce", normalizeForMatch("Beyoncé!"))
        assertEquals("rolling in the deep", normalizeForMatch("  Rolling—in the   Deep  "))
        assertEquals("", normalizeForMatch("   "))
    }

    @Test
    fun exactTitleRanksFirst() {
        val results = listOf(
            online("a", "Believer (Live)", "Imagine Dragons"),
            online("b", "Believer", "Imagine Dragons"),
            online("c", "Believer", "Some Cover Band")
        )
        val ranked = rankOnlineResults("believer", results)
        assertEquals("b", ranked.first().id)
    }

    @Test
    fun duplicatesAreCollapsed() {
        val results = listOf(
            online("a", "Kesariya", "Arijit Singh"),
            online("b", "Kesariya", "Arijit Singh"),
            online("c", "Kesariya", "Pritam")
        )
        val ranked = rankOnlineResults("kesariya", results)
        assertEquals(2, ranked.size)
    }

    @Test
    fun artistQueryFindsArtistTracks() {
        val results = listOf(
            online("a", "Tum Hi Ho", "Arijit Singh"),
            online("b", "Shape of You", "Ed Sheeran"),
            online("c", "Kesariya", "Arijit Singh")
        )
        val ranked = rankOnlineResults("arijit singh", results)
        assertEquals(listOf("a", "c", "b"), ranked.map { it.id })
    }

    @Test
    fun blankQueryKeepsInputOrder() {
        val results = listOf(online("a", "One", "X"), online("b", "Two", "Y"))
        assertEquals(results, rankOnlineResults("", results))
        assertEquals(results, rankOnlineResults("   ", results))
    }

    @Test
    fun tiesKeepCatalogueOrder() {
        val results = listOf(
            online("a", "Zed", "Artist"),
            online("b", "Abe", "Artist")
        )
        assertEquals(listOf("a", "b"), rankOnlineResults("artist", results).map { it.id })
    }

    private fun saavn(id: String, title: String, artist: String) =
        online("saavn_$id", title, artist).copy(
            contentUri = "https://aac.saavncdn.com/248/${id}_96.mp4"
        )

    private fun yt(id: String, title: String, artist: String) = K4Song(
        id = "yt_$id",
        title = title,
        artist = artist,
        album = "",
        durationMs = 200_000,
        contentUri = "yt:$id",
        artworkUri = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        isLocal = false
    )

    @Test
    fun mergeAddsResultsMissingFromYoutube() {
        val merged = mergeOnlineResults(
            "kesariya",
            youtube = listOf(yt("bBBBBBBBBB", "Kesariya (Live)", "Arijit Singh")),
            jiosaavn = listOf(saavn("a", "Kesariya", "Arijit Singh"))
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun mergeLetsYoutubeWinADuplicate() {
        val merged = mergeOnlineResults(
            "kesariya",
            youtube = listOf(yt("bBBBBBBBBB", "Kesariya", "Arijit Singh")),
            jiosaavn = listOf(saavn("a", "Kesariya", "Arijit Singh"))
        )
        // Same title+artist from both providers collapses to the YouTube copy.
        assertEquals(1, merged.size)
        assertEquals("yt_bBBBBBBBBB", merged.first().id)
    }

    @Test
    fun mergeKeepsTheBestMatchesWhenCapped() {
        val youtube = (1..30).map { yt("y$it", "Track $it", "Artist") }
        val jiosaavn = listOf(saavn("exact1", "believer", "Imagine Dragons"))
        val merged = mergeOnlineResults("believer", youtube, jiosaavn, limit = 10)
        assertEquals(10, merged.size)
        assertEquals("saavn_exact1", merged.first().id)
    }

    @Test
    fun mergeWithNoJioSaavnIsJustYoutube() {
        val youtube = listOf(yt("bBBBBBBBBB", "Kesariya", "Arijit Singh"))
        assertEquals(1, mergeOnlineResults("kesariya", youtube, emptyList()).size)
    }

    @Test
    fun sourceLabelComesFromTheIdPrefix() {
        assertEquals("YouTube", onlineSourceOf(yt("aAAAAAAAAAA", "Song", "Artist")))
        assertEquals("JioSaavn", onlineSourceOf(saavn("a", "Song", "Artist")))
        assertEquals("Online", onlineSourceOf(online("unknown", "Song", "Artist")))
    }
}
