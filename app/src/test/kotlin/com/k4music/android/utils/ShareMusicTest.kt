package com.k4music.android.utils

import com.k4music.android.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareMusicTest {
    @Test fun `YouTube songs share their watch URL`() {
        assertEquals("Song — Artist\nhttps://music.youtube.com/watch?v=dQw4w9WgXcQ", musicShareText("dQw4w9WgXcQ", "Song", "Artist"))
    }

    @Test fun `Saavn streams share metadata without exposing internal URL keys`() {
        val text = musicShareText("saavn:https://cdn.example/audio_320.mp4", "Song", "Artist")
        assertTrue(text.contains("Song — Artist"))
        assertTrue(text.contains(Brand.WEBSITE_URL))
        assertFalse(text.contains("cdn.example"))
        assertFalse(text.contains("youtube.com"))
    }

    @Test fun `local songs with missing metadata still have a shareable message`() {
        assertEquals("${Brand.APP_NAME}\n${Brand.WEBSITE_URL}", musicShareText("local:17", "", ""))
    }
}
