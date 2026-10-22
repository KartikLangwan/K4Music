package com.k4music.desktop

import com.k4music.android.k4.JioSaavnApi
import com.k4music.android.k4.K4Song
import com.k4music.android.k4.preserveLegacySaavnKeys
import com.k4music.android.k4.saavnMediaKey
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.k4music.android.models.Event
import com.k4music.android.models.Lyrics
import com.k4music.android.models.Playlist
import com.k4music.desktop.data.K4Database
import com.k4music.desktop.data.newDatabase
import com.k4music.desktop.player.DownloadIndex
import com.k4music.desktop.player.Track
import com.k4music.desktop.player.toTrack
import kotlinx.coroutines.flow.first
import java.io.File
import java.nio.file.Files

class SaavnIdentityTest {
    private fun song(url: String) = K4Song("saavn_fixture01", "Fixture", "Artist", "Album", 180_000, url, null)

    @Test fun compatibleSearchPreservesRealLibraryRelationshipsAndTheExistingDownload() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("k4-saavn-compatibility").toFile()
        val room = Room.databaseBuilder<K4Database>(File(dir, "library.db").absolutePath) { newDatabase() }
            .setDriver(BundledSQLiteDriver()).build()
        try {
            val old = Track("saavn:https://aac.saavncdn.com/1/song_320.mp4", "Fixture", "Artist")
            room.library.insert(old, likedAt = 123L)
            room.library.incrementTotalPlayTimeMs(old.id, 15_000)
            val (playlistId, _) = room.library.createPlaylist(Playlist(name = "Keep"), listOf(old))
            room.library.insert(Event(songId = old.id, timestamp = 456L, playTime = 15_000))
            room.library.upsert(Lyrics(old.id, "Edited lyrics", "[00:01]Synced lyrics"))
            val audio = File(dir, "legacy.audio").apply { writeText("existing downloaded bytes") }
            val downloads = DownloadIndex(dir)
            downloads.publish(old.id, audio.name)

            val found = preserveLegacySaavnKeys(listOf(song("https://aac.saavncdn.com/1/song_96.mp4")), room.library.legacySaavnSongIds()).single().toTrack()
            room.library.insert(found)

            assertEquals(old.id, found.id)
            assertEquals(123L, room.library.song(found.id).first()?.likedAt)
            assertEquals(15_000L, room.library.song(found.id).first()?.totalPlayTimeMs)
            assertEquals(1, room.library.playlistSongCount(playlistId))
            assertEquals(old.id, room.library.history().first().single().id)
            assertEquals("Edited lyrics", room.library.lyrics(found.id).first()?.fixed)
            assertEquals("existing downloaded bytes", DownloadIndex(dir).file(found.id)?.readText())
            assertNull(room.library.song("saavn:id:fixture01").first())
        } finally { room.close(); dir.deleteRecursively() }
    }

    @Test fun catalogueIdentitySurvivesUrlChangesAndLegacyPlaybackReferencesRemainIntact() {
        val old = "saavn:https://aac.saavncdn.com/1/song_320.mp4"
        val current = song("https://aac.saavncdn.com/1/song_96.mp4")
        assertEquals("saavn:id:fixture01", current.saavnMediaKey())
        assertEquals(current.saavnMediaKey(), song("https://different.saavncdn.com/2/new_320.mp4").saavnMediaKey())
        val preserved = preserveLegacySaavnKeys(listOf(current), listOf(old)).single()
        assertEquals("saavn_fixture01", preserved.id)
        assertEquals(old, preserved.saavnMediaKey())
        // A different CDN file is not enough evidence to rewrite or merge a legacy recording.
        assertEquals(current.saavnMediaKey(), preserveLegacySaavnKeys(listOf(current), listOf("saavn:https://aac.saavncdn.com/1/other_320.mp4")).single().saavnMediaKey())
    }

    private fun encrypt(url: String): String = Cipher.getInstance("DES/ECB/PKCS5Padding").run {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec("38346591".toByteArray(), "DES"))
        Base64.getEncoder().encodeToString(doFinal(url.toByteArray(Charsets.UTF_8)))
    }

    @Test fun stableKeysResolveTheExactProviderIdAndLegacyKeysStillResolveWithoutMetadata() = runBlocking<Unit> {
        val requestedIds = ArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val metadata = request.url.queryParameter("__call") == "song.getDetails"
            val body = if (metadata) {
                requestedIds += request.url.queryParameter("pids").orEmpty()
                JSONObject().put("fixture01", JSONObject().put("id", "fixture01")
                    .put("encrypted_media_url", encrypt("https://aac.saavncdn.com/1/song_320.mp4"))).toString()
            } else "audio"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (metadata) 200 else 206)
                .message("Fixture").body(body.toResponseBody()).build()
        }.build()
        try {
            val api = JioSaavnApi(client)
            assertEquals("https://aac.saavncdn.com/1/song_96.mp4", api.resolveMediaKey("saavn:id:fixture01", quality = "96"))
            assertEquals(listOf("fixture01"), requestedIds)
            assertEquals("https://aac.saavncdn.com/1/song_320.mp4", api.resolveMediaKey("saavn:https://aac.saavncdn.com/1/song_160.mp4", quality = "320"))
            assertEquals(1, requestedIds.size)
            assertNull(api.resolveMediaKey("saavn:id:../../bad"))
            assertEquals(1, requestedIds.size)
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun mismatchedProviderResponsesCannotSubstituteAnotherSong() = runBlocking<Unit> {
        var requested = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requested++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body("""{"fixture01":{"id":"different-song","encrypted_media_url":"junk"}}""".toResponseBody()).build()
        }.build()
        try {
            assertNull(JioSaavnApi(client).resolveMediaKey("saavn:id:fixture01"))
            assertEquals(1, requested)
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }
}
