package com.k4music.android.utils

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class DataSourceRecoveryTest {
    private class FailingSource : DataSource {
        override fun addTransferListener(listener: TransferListener) = Unit
        override fun open(spec: DataSpec): Long = throw IOException("connection failed")
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw IOException("connection lost")
        override fun getUri(): Uri? = null
        override fun close() = Unit
    }
    @Test fun `stream read errors invalidate resolution without hiding network exception`() {
        var reported: Throwable? = null
        val source = CatchingDataSourceFactory(DataSource.Factory { FailingSource() }) { reported = it }.createDataSource()
        try { source.read(ByteArray(1), 0, 1); fail("Expected network error") } catch (error: IOException) {
            assertSame(error, reported)
            assertEquals("connection lost", error.message)
        }
    }
    @Test fun `ring buffer replaces bounded entries without exposing a mutable iterator`() {
        val buffer = com.k4music.core.data.utils.RingBuffer<String?>(2) { null }
        buffer += "first"
        val snapshot = buffer.iterator()
        buffer += "second"
        buffer += "third"
        assertEquals(listOf("first", null), snapshot.asSequence().toList())
        assertEquals(listOf("third", "second"), buffer.toList())
    }
}
