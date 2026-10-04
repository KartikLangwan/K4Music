package com.k4music.android.utils

/** Media3 uses negative sentinel durations until metadata arrives. */
fun playbackFraction(position: Long, duration: Long): Float {
    if (duration <= 0L || position <= 0L) return 0f
    return (position.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0).toFloat()
}
