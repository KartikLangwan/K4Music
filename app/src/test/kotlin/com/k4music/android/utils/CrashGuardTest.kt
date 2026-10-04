package com.k4music.android.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The crash guard is the last line of defence: whatever goes wrong, the cause
 * must land on disk and the log must stay bounded. A crash that leaves no trace
 * is a crash we cannot fix.
 */
class CrashGuardTest {

    private fun crashGuardLogIn(dir: File) = File(dir, "crashes.log")

    @Test
    fun `an uncaught crash is written to disk`() {
        val dir = Files.createTempDirectory("k4-crash").toFile()

        CrashGuard.install(dir)
        val handler = requireNotNull(Thread.getDefaultUncaughtExceptionHandler())

        handler.uncaughtException(
            Thread.currentThread(),
            IllegalStateException("boom-marker")
        )

        val log = crashGuardLogIn(dir)
        assertTrue("crash was not recorded", log.exists())
        assertTrue(log.readText().contains("boom-marker"))
    }

    @Test
    fun `the crash log never grows past its cap`() {
        val dir = Files.createTempDirectory("k4-crash-cap").toFile()
        crashGuardLogIn(dir).writeText("x".repeat(300 * 1024))

        CrashGuard.install(dir)
        val handler = requireNotNull(Thread.getDefaultUncaughtExceptionHandler())

        handler.uncaughtException(Thread.currentThread(), IllegalStateException("boom-marker"))

        val length = crashGuardLogIn(dir).length()
        assertTrue("log grew to $length bytes", length < 300 * 1024)
        assertFalse(crashGuardLogIn(dir).readText().isBlank())
    }
}