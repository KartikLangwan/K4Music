package com.k4music.android.utils

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Bound UI loading time while allowing navigation to cancel obsolete requests. */
suspend fun <T> networkResult(block: suspend () -> Result<T>?): Result<T> = try {
    withContext(Dispatchers.IO) {
        withTimeoutOrNull(20_000) { block() }
            ?: Result.failure(IOException("Music request did not complete"))
    }
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
