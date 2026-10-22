package com.k4music.android.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalMediaIdsTest {
    @Test fun foreignAndMalformedLocalIdsAreUnavailableInsteadOfThrowing() {
        assertEquals(17L, localMediaStoreId("local:17"))
        listOf("local:C:\\Music\\song.mp3", "local:/music/song.mp3", "local:", "local:-1", "local:999999999999999999999", "17", "saavn:17")
            .forEach { assertNull(it, localMediaStoreId(it)) }
    }
}
