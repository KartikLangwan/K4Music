package com.k4music.desktop

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UpdaterSignerTest {
    @Test
    fun signedFileHasSignerAndAlteredOrUnsignedHasNone() = runBlocking {
        val system = java.io.File(java.io.File(System.getenv("SystemRoot") ?: "C:/Windows", "System32"), "cmd.exe")
        if (!system.isFile) return@runBlocking // not Windows
        assertNotNull(Updater.signer(system)) // cmd.exe is catalog-signed on some builds; copy below is not
        val plain = java.io.File.createTempFile("unsigned-", ".exe").apply { writeText("MZ not a real exe"); deleteOnExit() }
        assertNull(Updater.signer(plain))
    }
}
