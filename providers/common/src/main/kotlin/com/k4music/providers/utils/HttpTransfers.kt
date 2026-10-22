package com.k4music.providers.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Call
import okhttp3.Response

/** Cancellation closes a blocking call/body read immediately instead of waiting for its deadline. */
suspend fun <T> Call.useCancellable(block: suspend (Response) -> T): T = coroutineScope {
    val reader = async(Dispatchers.IO) {
        try { execute().use { block(it) } } catch (error: Exception) {
            // Cancelling OkHttp can surface IOException; retain the coroutine's cancellation cause.
            currentCoroutineContext().ensureActive()
            throw error
        }
    }
    try { reader.await() } finally { cancel() }
}
