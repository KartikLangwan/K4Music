package com.k4music.android.k4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaavnKeysTest {
    private fun song(url: String) = K4Song("saavn_provider123", "Title", "Artist", "Album", 180_000, url, null)

    @Test fun stableIdDoesNotDependOnStreamHostQualityOrToken() {
        val first = song("https://aac.saavncdn.com/1/song_96.mp4?token=old")
        val refreshed = first.copy(contentUri = "https://new.saavncdn.com/2/new_320.mp4?token=new")
        assertEquals("saavn:id:provider123", first.saavnMediaKey())
        assertEquals(first.saavnMediaKey(), refreshed.saavnMediaKey())
    }

    @Test fun qualityVariantsReuseExistingLibraryAndDownloadKeysWithoutChangingCatalogueIdentity() {
        val old = "saavn:https://aac.saavncdn.com/1/song_320.mp4"
        val fresh = song("https://aac.saavncdn.com/1/song_96.mp4")
        val preserved = preserveLegacySaavnKeys(listOf(fresh), listOf(old)).single()
        assertEquals(fresh.id, preserved.id)
        assertEquals(old, preserved.saavnMediaKey())
    }

    @Test fun similarMetadataOrUntrustedHostsDoNotMergeUnprovenRecordings() {
        val fresh = song("https://aac.saavncdn.com/1/song_96.mp4")
        listOf("saavn:https://evil.example/1/song_320.mp4", "saavn:https://aac.saavncdn.com/1/other_320.mp4", "saavn:https://aac.saavncdn.com:1234/1/song_320.mp4")
            .forEach { old -> assertEquals(fresh.saavnMediaKey(), preserveLegacySaavnKeys(listOf(fresh), listOf(old)).single().saavnMediaKey()) }
        assertNull(saavnCatalogueId("saavn:id:../../escape"))
        assertNull(saavnCatalogueId("saavn:https://aac.saavncdn.com/1/song_320.mp4"))
    }
}
