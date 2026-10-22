package com.k4music.desktop

import com.k4music.providers.piped.Piped
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class ProviderBoundaryTest {
    @Test fun pipedRejectsPlaintextLoginBeforeSendingCredentials() = runBlocking<Unit> {
        val result = Piped.login(Url("http://127.0.0.1:1"), "test-user", "test-password")
        assertTrue(result?.exceptionOrNull() is IllegalArgumentException)
        assertTrue(result?.exceptionOrNull()?.message?.contains("HTTPS") == true)
    }
}
