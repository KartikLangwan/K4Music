package com.k4music.android.playlistimport

import androidx.media3.common.MediaItem
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PlaylistImportTest {
    @Before
    fun fastRetries() {
        retryPauseMs = 0
    }

    private fun track(id: String, title: String = "Song $id", artist: String = "Artist") =
        ImportedTrack(id, title, artist, MediaItem.Builder().setMediaId(id).build())

    private fun rows(vararg ids: String) = ids.map { YtRow(track(it), null) }

    private fun expectError(kind: ImportError, block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("expected $kind")
        } catch (error: PlaylistImportException) {
            assertEquals(kind, error.error)
        }
    }

    // ---- link parsing ----

    @Test
    fun youtubeLinks() {
        val id = "PLabcdefghijklmnop"
        assertEquals(id, YouTubeMusicSource.playlistIdOf("https://music.youtube.com/playlist?list=$id"))
        assertEquals(id, YouTubeMusicSource.playlistIdOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=$id&index=2"))
        assertEquals(id, YouTubeMusicSource.playlistIdOf("  https://youtube.com/playlist?list=$id&si=xyz  "))
        assertEquals(id, YouTubeMusicSource.playlistIdOf("https://music.youtube.com/browse/VL$id".replace("browse/VL$id", "playlist?list=VL$id")))
        assertEquals(id, YouTubeMusicSource.playlistIdOf(id))
        assertNull(YouTubeMusicSource.playlistIdOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeMusicSource.playlistIdOf("https://example.com/playlist?list=$id"))
        assertNull(YouTubeMusicSource.playlistIdOf("hello"))
        assertNull(YouTubeMusicSource.playlistIdOf(""))
    }

    @Test
    fun spotifyLinks() {
        val id = "37i9dQZF1DXcBWIGoYBM5M"
        assertEquals(id, SpotifySource.playlistIdOf("https://open.spotify.com/playlist/$id?si=abc"))
        assertEquals(id, SpotifySource.playlistIdOf("https://open.spotify.com/intl-de/playlist/$id"))
        assertEquals(id, SpotifySource.playlistIdOf("https://open.spotify.com/embed/playlist/$id"))
        assertEquals(id, SpotifySource.playlistIdOf("spotify:playlist:$id"))
        assertNull(SpotifySource.playlistIdOf("https://open.spotify.com/track/$id"))
        assertNull(SpotifySource.playlistIdOf("https://open.spotify.com/playlist/short"))
        assertNull(SpotifySource.playlistIdOf("https://music.youtube.com/playlist?list=PLabcdefghijklmnop"))
    }

    // ---- YouTube response shapes (regression: playlists stopped after the first ~100 tracks) ----

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun youtubeContinuationInNewResponseShapeIsDecoded() {
        val response = json.decodeFromString<com.k4music.providers.innertube.models.ContinuationResponse>(
            """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[
                 {"musicResponsiveListItemRenderer":{"flexColumns":[]}},
                 {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"NEXT"}}}}]}}]}"""
        )
        val items = response.onResponseReceivedActions!!.first().appendContinuationItemsAction!!.continuationItems!!
        assertEquals(2, items.size)
        assertEquals("NEXT", items.last().continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token)
    }

    // ---- pagination / retry ----

    @Test
    fun paginationKeepsEveryItemInOrder() = runBlocking {
        val pages = mapOf("t1" to (listOf(4, 5, 6) to "t2"), "t2" to (listOf(7, 8) to "t3"), "t3" to (listOf(9) to null))
        val seenProgress = ArrayList<Int>()
        val all = collectAllPages(listOf(1, 2, 3), "t1", onProgress = { seenProgress += it }) { pages.getValue(it) }
        assertEquals((1..9).toList(), all)
        assertEquals(listOf(3, 6, 8, 9), seenProgress)
    }

    @Test
    fun largePlaylistOver1000TracksIsComplete() = runBlocking {
        val all = collectAllPages((0 until 100).toList(), "p1") { token ->
            val page = token.removePrefix("p").toInt()
            val items = (page * 100 until page * 100 + 100).toList()
            items to if (page < 24) "p${page + 1}" else null
        }
        assertEquals(2500, all.size)
        assertEquals((0 until 2500).toList(), all)
    }

    @Test
    fun transientPageFailureIsRetried() = runBlocking {
        var calls = 0
        val all = collectAllPages(listOf(1), "t") {
            if (++calls < 3) throw IOException("flaky") else listOf(2) to null
        }
        assertEquals(listOf(1, 2), all)
        assertEquals(3, calls)
    }

    @Test
    fun persistentPageFailureAbortsInsteadOfTruncating() {
        expectError(ImportError.Network) {
            collectAllPages(listOf(1, 2, 3), "t") { throw IOException("down") }
        }
    }

    @Test
    fun repeatedTokenDoesNotLoopForever() = runBlocking {
        expectError(ImportError.Network) {
            collectAllPages(listOf(1), "same") { listOf(2) to "same" }
        }
    }

    @Test
    fun pageLimitReportsIncompleteImport() = runBlocking {
        expectError(ImportError.Network) {
            collectAllPages(listOf(1), "first", maxPages = 1) { listOf(2) to "next" }
        }
    }

    // ---- matching ----

    private fun cand(title: String, artist: String, ms: Long? = null) = Candidate(title, artist, ms, title)

    @Test
    fun matchingIgnoresEditionTailsAndAccents() {
        val best = pickBestMatch("Kesariya", "Arijit Singh", 268_000, listOf(
            cand("Kesariya (From \"Brahmastra\")", "Arijit Singh", 268_000),
            cand("Kesariya Tera", "Someone Else", 100_000)
        ))
        assertEquals("Kesariya (From \"Brahmastra\")", best?.title)
        assertNotNull(pickBestMatch("Beyoncé", "Beyoncé", null, listOf(cand("Beyonce", "Beyonce"))))
    }

    @Test
    fun matchingPrefersExactTitleAndRejectsWrongSongs() {
        val best = pickBestMatch("Believer", "Imagine Dragons", 204_000, listOf(
            cand("Believer (Live)", "Imagine Dragons", 250_000),
            cand("Believer", "Imagine Dragons", 204_000)
        ))
        assertEquals("Believer", best?.title)
        assertNull(pickBestMatch("Believer", "Imagine Dragons", null, listOf(cand("Shape of You", "Ed Sheeran"))))
        assertNull(pickBestMatch("Believer", "Imagine Dragons", 204_000, listOf(cand("Believer", "Totally Other Band", 400_000))))
        assertNull(pickBestMatch("", "x", null, listOf(cand("a", "x"))))
    }

    @Test
    fun matchingRequiresArtistAgreementWhenBothAreKnown() {
        assertNull(pickBestMatch("Tum Hi Ho", "Arijit Singh", 262_000, listOf(cand("Tum Hi Ho", "T-Series", 263_000))))
        assertNotNull(pickBestMatch("Tum Hi Ho", "Arijit Singh", 262_000, listOf(cand("Tum Hi Ho", "", 263_000))))
        assertNull(pickBestMatch("Song", "Arijit Singh", null, listOf(cand("Song", "Someone Singh"))))
    }

    @Test
    fun matchingRejectsDifferentTitlesAndRecordingEditionsDespiteEqualDuration() {
        assertNull(pickBestMatch("One", "Metallica", 240_000, listOf(cand("One Thing", "Metallica", 240_000))))
        assertNull(pickBestMatch("Believer", "Imagine Dragons", 204_000, listOf(cand("Believer (Live)", "Imagine Dragons", 204_000))))
        assertNotNull(pickBestMatch("Believer (Live)", "Imagine Dragons", 204_000, listOf(cand("Believer (Live)", "Imagine Dragons", 204_000))))
    }

    // ---- importer ----

    @Test
    fun matchingPreservesPartsSubtitlesAndRejectsConflictingDurations() {
        assertNull(pickBestMatch("Title - Part 1", "Artist", 180_000, listOf(cand("Title - Part 2", "Artist", 180_000))))
        assertNull(pickBestMatch("Title (Part 1)", "Artist", null, listOf(cand("Title (Part 2)", "Artist"))))
        assertNull(pickBestMatch("Title", "Artist", 180_000, listOf(cand("Title", "Artist", 240_000))))
        assertNotNull(pickBestMatch("Title", "Artist", 180_000, listOf(cand("Title - 2020 Remaster", "Artist", 182_000))))
    }

    private class FakeSource(private val result: FetchedPlaylist) : PlaylistSource {
        override val name = "fake"
        override fun accepts(link: String) = link.startsWith("fake:")
        override suspend fun fetch(link: String, progress: (ImportProgress) -> Unit) = result
    }

    private fun fetched(tracks: List<ImportedTrack>, failed: List<FailedTrack> = emptyList()) =
        FetchedPlaylist("P", "fake", null, null, tracks.size + failed.size, tracks, failed)

    @Test
    fun importerRejectsUnknownLinks() {
        expectError(ImportError.InvalidLink) { PlaylistImporter(listOf(FakeSource(fetched(listOf(track("a"))))))
            .prepare("https://example.com/x") {} }
        expectError(ImportError.InvalidLink) { PlaylistImporter(emptyList()).prepare("") {} }
    }

    @Test
    fun importerCollapsesDuplicatesKeepingFirstOrder() = runBlocking {
        val plan = PlaylistImporter(listOf(FakeSource(fetched(listOf(track("a"), track("b"), track("a"), track("c"), track("b"))))))
            .prepare("fake:1") {}
        assertEquals(listOf("a", "b", "c"), plan.fetched.tracks.map { it.key })
        assertEquals(2, plan.duplicates)
    }

    @Test
    fun emptyPlaylistIsAnErrorButAllFailedIsReported() {
        expectError(ImportError.Empty) { PlaylistImporter(listOf(FakeSource(fetched(emptyList())))).prepare("fake:1") {} }
        val plan = runBlocking {
            PlaylistImporter(listOf(FakeSource(fetched(emptyList(), listOf(FailedTrack("x", "y", "nope")))))).prepare("fake:1") {}
        }
        assertEquals(1, plan.fetched.failed.size)
        assertEquals(1, plan.fetched.total)
    }

    // ---- YouTube source ----

    @Test
    fun youtubeImportsAllPagesInOrderWithAccurateTotals() = runBlocking {
        val source = YouTubeMusicSource(
            loadFirst = { YtFirstPage("My Mix", "thumb", 7, true, rows("a", "b", "c"), "n1") },
            loadNext = { token -> if (token == "n1") YtNextPage(rows("d", "e", "f"), "n2") else YtNextPage(rows("g"), null) }
        )
        val progress = ArrayList<Int>()
        val result = source.fetch("PLabcdefghijklmnop") { progress += it.done }
        assertEquals("My Mix", result.name)
        assertEquals("VLPLabcdefghijklmnop", result.browseId)
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g"), result.tracks.map { it.key })
        assertEquals(7, result.total)
        assertTrue(result.failed.isEmpty())
        assertEquals(listOf(3, 6, 7), progress)
    }

    @Test
    fun youtubeReportsHiddenAndUnreadableTracks() = runBlocking {
        val source = YouTubeMusicSource(
            loadFirst = {
                YtFirstPage("Mix", null, 5, true, rows("a", "b") + YtRow(null, FailedTrack("Broken", "X", "Track has no playable id")), null)
            },
            loadNext = { error("unused") }
        )
        val result = source.fetch("PLabcdefghijklmnop") {}
        assertEquals(2, result.tracks.size)
        assertEquals(3, result.failed.size)
        assertEquals("Broken", result.failed.first().title)
        assertEquals(5, result.total)
    }

    @Test
    fun youtubePrivatePlaylistIsUnavailable() {
        val source = YouTubeMusicSource(loadFirst = { YtFirstPage(null, null, null, false, emptyList(), null) }, loadNext = { error("unused") })
        expectError(ImportError.Unavailable) { source.fetch("PLabcdefghijklmnop") {} }
    }

    @Test
    fun youtubeNetworkFailureThenRetrySucceeds() = runBlocking {
        var calls = 0
        val source = YouTubeMusicSource(
            loadFirst = { if (++calls < 3) throw IOException("offline") else YtFirstPage("Mix", null, 1, true, rows("a"), null) },
            loadNext = { error("unused") }
        )
        assertEquals(1, source.fetch("PLabcdefghijklmnop") {}.tracks.size)
    }

    @Test
    fun youtubeTotalNetworkFailureIsReportedAsNetwork() {
        val source = YouTubeMusicSource(loadFirst = { throw IOException("offline") }, loadNext = { error("unused") })
        expectError(ImportError.Network) { source.fetch("PLabcdefghijklmnop") {} }
    }

    @Test
    fun youtubeMidwayFailureAbortsWithoutSavingPartialData() {
        val source = YouTubeMusicSource(
            loadFirst = { YtFirstPage("Mix", null, 300, true, rows("a", "b"), "n1") },
            loadNext = { throw IOException("dropped") }
        )
        expectError(ImportError.Network) { source.fetch("PLabcdefghijklmnop") {} }
    }

    // ---- Spotify source ----

    private fun sample() = javaClass.getResourceAsStream("/spotify_embed_sample.html")!!.bufferedReader().readText()

    @Test
    fun spotifyEmbedIsParsedWithOrderUnicodeAndCover() {
        val embed = SpotifySource.parseEmbed(sample())!!
        assertEquals("Today’s Top Hits", embed.name)
        assertEquals(listOf("Patient Zero", "the cure", "Nicole Kidman", "Earrings", "BbY WOW"), embed.tracks.map { it.title })
        assertEquals("Beyoncé, Jay-Z", embed.tracks[1].artist)
        assertEquals(225868L, embed.tracks[0].durationMs)
        assertNotNull(embed.thumbnail)
    }

    @Test
    fun spotifyErrorPageAndGarbageAreRejected() {
        val errorPage = """<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"status":500,"title":"Page not available"}}}</script>"""
        assertNull(SpotifySource.parseEmbed(errorPage))
        assertNull(SpotifySource.parseEmbed("<html>nothing</html>"))
        assertNull(SpotifySource.parseEmbed("""<script id="__NEXT_DATA__">{broken</script>"""))
    }

    @Test
    fun spotifySongCount() {
        assertEquals(250, SpotifySource.songCountOf("""<meta name="music:song_count" content="250"/>"""))
        assertNull(SpotifySource.songCountOf("<html></html>"))
    }

    private fun spotifyClient(embed: String?, page: String?, embedCode: Int = 200): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val url = chain.request().url.toString()
            val (code, body) = when {
                "/embed/" in url -> (if (embed == null) 503 else embedCode) to embed.orEmpty()
                else -> (if (page == null) 503 else 200) to page.orEmpty()
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("text/html".toMediaType())).build()
        }).build()

    private val link = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"

    @Test
    fun manualReviewOffersBoundedDistinctAlternativesWithoutAutomaticallyImportingThem() = runBlocking {
        val alternatives = (0..11).map { index -> Candidate("Alternative $index", "Artist", 200_000L,
            MediaItem.Builder().setMediaId("option-$index").build()) }
        val source = SpotifySource(spotifyClient(null, null), { alternatives + alternatives })
        val options = source.candidates(FailedTrack("Wanted", "Artist", "No confident match"))
        assertEquals(8, options.size)
        assertEquals((0..7).map { "option-$it" }, options.map { it.payload.mediaId })
        assertNull(pickBestMatch("Wanted", "Artist", 200_000, options))
    }
    private val titles = listOf("Patient Zero", "the cure", "Nicole Kidman", "Earrings", "BbY WOW")
    private val ALL_ARTISTS = "Taylor Swift Beyonce Jay-Z ADELA Malcolm Todd KAROL G Judeline rusowsky"
    private val resolveAll: suspend (String) -> List<Candidate<MediaItem>> = { query ->
        listOf(Candidate(query.substringBefore("  "), "", null, MediaItem.Builder().setMediaId("yt-$query").build()))
    }

    @Test
    fun spotifyEndToEndMatchesOrderAndReportsShortfall() = runBlocking {
        val matcher: suspend (String) -> List<Candidate<MediaItem>> = { query ->
            val title = listOf("Patient Zero", "the cure", "Nicole Kidman", "Earrings", "BbY WOW").first { query.startsWith(it) }
            listOf(Candidate(title, ALL_ARTISTS, null, MediaItem.Builder().setMediaId("id-$title").build()))
        }
        val source = SpotifySource(spotifyClient(sample(), """<meta name="music:song_count" content="250"/>"""), matcher)
        val result = source.fetch(link) {}
        assertEquals(5, result.tracks.size)
        assertEquals(listOf("id-Patient Zero", "id-the cure", "id-Nicole Kidman", "id-Earrings", "id-BbY WOW"), result.tracks.map { it.key })
        assertEquals(250, result.total)
        assertEquals(245, result.notExposed)
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun spotifyUnmatchedTracksAreNamedNotDropped() = runBlocking {
        val matcher: suspend (String) -> List<Candidate<MediaItem>> = { query ->
            if (query.startsWith("Earrings")) emptyList()
            else listOf(Candidate(titles.first { query.startsWith(it) }, ALL_ARTISTS, null,
                MediaItem.Builder().setMediaId("id-$query").build()))
        }
        val source = SpotifySource(spotifyClient(sample(), null), matcher)
        val result = source.fetch(link) {}
        assertEquals(4, result.tracks.size)
        assertEquals(listOf("Earrings"), result.failed.map { it.title })
        assertEquals("No matching song found", result.failed.single().reason)
        assertEquals(5, result.total)
        assertEquals(0, result.notExposed)

        // retry resolves only the failed track
        val retried = SpotifySource(spotifyClient(sample(), null), { listOf(Candidate("Earrings", ALL_ARTISTS, null,
            MediaItem.Builder().setMediaId("id-e").build())) }).retry(result.failed) {}
        assertEquals(listOf("id-e"), retried.first.map { it.key })
        assertTrue(retried.second.isEmpty())
    }

    @Test
    fun spotifySearchFailureIsMarkedRetryable() = runBlocking {
        val source = SpotifySource(spotifyClient(sample(), null), { throw IOException("search down") })
        val result = source.fetch(link) {}
        assertTrue(result.tracks.isEmpty())
        assertEquals(5, result.failed.size)
        assertEquals("Search failed (network)", result.failed.first().reason)
    }

    @Test
    fun spotifyPrivateAndOfflineAreDistinguished() {
        expectError(ImportError.Unavailable) {
            SpotifySource(spotifyClient("""<script id="__NEXT_DATA__">{"props":{"pageProps":{"status":500}}}</script>""", null), resolveAll).fetch(link) {}
        }
        expectError(ImportError.Network) { SpotifySource(spotifyClient(null, null), resolveAll).fetch(link) {} }
        expectError(ImportError.InvalidLink) { SpotifySource(spotifyClient(null, null), resolveAll).fetch("https://open.spotify.com/track/abc") {} }
    }

    @Test
    fun spotifyWrongCandidatesAreFailuresNotMatches() = runBlocking {
        val twice = sample() // same song title/artist repeated via matcher mapping everything to one id
        val source = SpotifySource(spotifyClient(twice, null), { listOf(Candidate("Patient Zero", "Taylor Swift", null,
            MediaItem.Builder().setMediaId("same").build())) })
        val plan = PlaylistImporter(listOf(source)).prepare(link) {}
        assertEquals(1, plan.fetched.tracks.size)
        assertEquals(4, plan.duplicates + plan.fetched.failed.size)
        assertFalse(plan.fetched.tracks.isEmpty())
    }
}
