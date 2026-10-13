package com.k4music.android.utils

import androidx.media3.common.PlaybackException

/** Terminal-error recovery is bounded per track; a successful prepare cannot reset it. */
internal class PlaybackRetryBudget(private val limit: Int = 2) {
    private var mediaId: String? = null
    private var attempts = 0
    fun onMediaTransition(id: String?) {
        if (id != mediaId) { mediaId = id; attempts = 0 }
    }
    fun tryAcquire(id: String): Boolean {
        onMediaTransition(id)
        if (attempts >= limit) return false
        attempts++
        return true
    }
}

internal fun isRecoverablePlaybackFailure(code: Int, httpStatus: Int?): Boolean =
    if (httpStatus != null) httpStatus in setOf(403, 408, 410, 416, 429, 500, 502, 503, 504)
    else code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

internal enum class Recovery { Retry, Fallback, None }

/**
 * What to do about a failed stream; the song is never skipped. A lost connection retries
 * the same source; a broken source switches to the fallback (once); then plain retries.
 */
internal fun chooseRecovery(
    code: Int,
    httpStatus: Int?,
    id: String,
    fallbackAllowed: Boolean,
    retries: PlaybackRetryBudget,
    fallbacks: PlaybackRetryBudget
): Recovery = when {
    httpStatus == null && isRecoverablePlaybackFailure(code, null) && retries.tryAcquire(id) -> Recovery.Retry
    fallbackAllowed && fallbacks.tryAcquire(id) -> Recovery.Fallback
    isRecoverablePlaybackFailure(code, httpStatus) && retries.tryAcquire(id) -> Recovery.Retry
    else -> Recovery.None
}
