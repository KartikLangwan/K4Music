package com.k4music.desktop.player

// Ported from app/.../utils/PlaybackRetryBudget.kt: same budgets, same order, never skips a song.

/** Terminal-error recovery is bounded per track; a successful load cannot reset it. */
class PlaybackRetryBudget(private val limit: Int = 2) {
    private var mediaId: String? = null
    private var attempts = 0

    fun onMediaTransition(id: String?) {
        if (id != mediaId) { mediaId = id; attempts = 0 }
    }

    fun reset() { mediaId = null; attempts = 0 }

    fun tryAcquire(id: String): Boolean {
        onMediaTransition(id)
        if (attempts >= limit) return false
        attempts++
        return true
    }
}

enum class Failure {
    /** No connection / timed out: the source itself may be fine. */
    Network,
    /** The source refused or could not be played (HTTP 403/410, unplayable, bad data). */
    Source
}

enum class Recovery { Retry, Fallback, None }

/**
 * What to do about a failed stream. A lost connection retries the same source; a broken source
 * switches to the fallback (once); then plain retries; then the error is shown. Never a skip.
 */
fun chooseRecovery(
    failure: Failure,
    id: String,
    fallbackAllowed: Boolean,
    retries: PlaybackRetryBudget,
    fallbacks: PlaybackRetryBudget
): Recovery = when {
    failure == Failure.Network && retries.tryAcquire(id) -> Recovery.Retry
    fallbackAllowed && fallbacks.tryAcquire(id) -> Recovery.Fallback
    retries.tryAcquire(id) -> Recovery.Retry
    else -> Recovery.None
}
