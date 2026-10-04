package com.k4music.android.utils

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.k4music.android.preferences.AppearancePreferences
import com.k4music.core.ui.utils.songBundle
import kotlin.time.Duration

val Player.currentWindow: Timeline.Window?
    get() = currentTimeline.let { timeline ->
        currentMediaItemIndex.takeIf { it in 0 until timeline.windowCount }?.let { timeline[it] }
    }

inline val Timeline.windows: List<Timeline.Window>
    get() = List(windowCount) { this[it] }

inline val Timeline.mediaItems: List<MediaItem>
    get() = windows.map { it.mediaItem }

val Player.shouldBePlaying: Boolean
    get() = !(playbackState == Player.STATE_ENDED || !playWhenReady)

fun Player.removeMediaItems(range: IntRange) = removeMediaItems(range.first, range.last + 1)

fun Player.safeClearQueue() {
    if (currentMediaItemIndex > 0) removeMediaItems(0 until currentMediaItemIndex)
    if (currentMediaItemIndex < mediaItemCount - 1) {
        removeMediaItems(currentMediaItemIndex + 1 until mediaItemCount)
    }
}

fun Player.seamlessPlay(mediaItem: MediaItem) =
    if (mediaItem.mediaId == currentMediaItem?.mediaId) safeClearQueue() else forcePlay(mediaItem)

fun Player.forcePlay(mediaItem: MediaItem) {
    setMediaItem(mediaItem, true)
    playWhenReady = true
    prepare()
}

fun Player.forcePlayAtIndex(
    items: List<MediaItem>,
    index: Int
) {
    if (items.isEmpty()) return

    setMediaItems(items, index.coerceIn(items.indices), C.TIME_UNSET)
    playWhenReady = true
    prepare()
}

fun Player.forcePlayFromBeginning(items: List<MediaItem>) = forcePlayAtIndex(items, 0)

/** Window indices in the order they will play (the shuffle order when shuffling). */
fun Timeline.playOrder(shuffled: Boolean): List<Int> {
    if (isEmpty) return emptyList()
    val order = ArrayList<Int>(windowCount)
    var index = getFirstWindowIndex(shuffled)
    while (index != C.INDEX_UNSET && order.size < windowCount) {
        order += index
        index = getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
    }
    return order
}

/** Shuffle with the current song first, so every other song is still ahead of the listener. */
@OptIn(UnstableApi::class)
fun ExoPlayer.shuffleFromCurrent() {
    val count = mediaItemCount
    if (count == 0) return
    val current = currentMediaItemIndex.coerceIn(0, count - 1)
    val order = intArrayOf(current) + (0 until count).filter { it != current }.shuffled().toIntArray()
    setShuffleOrder(DefaultShuffleOrder(order, System.nanoTime()))
}

/** ExoPlayer drops inserted songs at random shuffle positions; put them where the user asked. */
@OptIn(UnstableApi::class)
private fun Player.pinInShuffleOrder(inserted: IntRange, next: Boolean) {
    if (!shuffleModeEnabled) return
    val player = this as? ExoPlayer ?: return
    val order = currentTimeline.playOrder(shuffled = true).filterNot { it in inserted }.toMutableList()
    val at = if (next) order.indexOf(currentMediaItemIndex) + 1 else order.size
    order.addAll(at.coerceIn(0, order.size), inserted.toList())
    player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
}

/**
 * Next/previous follow the shuffle order and wrap only when the whole queue repeats; a
 * single-song repeat still lets the listener skip, like the system media controls.
 */
private val Player.navigationRepeatMode
    get() = if (repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF

private fun Player.isPlayableAt(index: Int, hideExplicit: Boolean) =
    !hideExplicit || getMediaItemAt(index).mediaMetadata.extras?.songBundle?.explicit != true

fun Player.forceSeekToPrevious(seekToStart: Boolean = true) {
    val timeline = currentTimeline
    if (timeline.isEmpty) return
    if (seekToStart && currentPosition > maxSeekToPreviousPosition) return seekTo(0)

    val hideExplicit = AppearancePreferences.hideExplicit
    val previous = neighborIndex(
        count = timeline.windowCount,
        start = currentMediaItemIndex,
        step = { timeline.getPreviousWindowIndex(it, navigationRepeatMode, shuffleModeEnabled) },
        playable = { isPlayableAt(it, hideExplicit) }
    )
    // At the start of a non-repeating queue, "previous" restarts the current song.
    if (previous != null) seekTo(previous, C.TIME_UNSET) else seekTo(0)
}

fun Player.forceSeekToNext() {
    val timeline = currentTimeline
    if (timeline.isEmpty) return

    val hideExplicit = AppearancePreferences.hideExplicit
    val next = neighborIndex(
        count = timeline.windowCount,
        start = currentMediaItemIndex,
        step = { timeline.getNextWindowIndex(it, navigationRepeatMode, shuffleModeEnabled) },
        playable = { isPlayableAt(it, hideExplicit) }
    )
    if (next != null) return seekTo(next, C.TIME_UNSET)

    // Nothing ahead: rewind to the start of the queue and stop there.
    pause()
    val first = timeline.getFirstWindowIndex(shuffleModeEnabled)
    if (first != C.INDEX_UNSET && first != currentMediaItemIndex) seekTo(first, 0)
}

/** A bounded walk also handles queues where every song is filtered out. */
internal inline fun neighborIndex(
    count: Int,
    start: Int,
    step: (Int) -> Int,
    playable: (Int) -> Boolean
): Int? {
    var index = start
    repeat(count) {
        index = step(index)
        if (index == C.INDEX_UNSET) return null
        if (playable(index)) return index
    }
    return null
}

/**
 * Inserts never replace the queue: after a playback error or a stop the player is IDLE but the
 * queue is still the listener's. Only an empty queue starts fresh; a finished one plays the
 * new song straight away.
 */
private fun Player.insert(index: Int, mediaItems: List<MediaItem>, next: Boolean) {
    if (mediaItems.isEmpty()) return
    if (mediaItemCount == 0) return forcePlayFromBeginning(mediaItems)

    val ended = playbackState == Player.STATE_ENDED
    addMediaItems(index, mediaItems)
    pinInShuffleOrder(index until index + mediaItems.size, next)
    if (ended) {
        seekTo(index, C.TIME_UNSET)
        playWhenReady = true
    }
}

fun Player.addNext(mediaItem: MediaItem) =
    insert(currentMediaItemIndex + 1, listOf(mediaItem), next = true)

fun Player.enqueue(mediaItem: MediaItem) = enqueue(listOf(mediaItem))

fun Player.enqueue(mediaItems: List<MediaItem>) = insert(mediaItemCount, mediaItems, next = false)

fun Player.findNextMediaItemById(mediaId: String): MediaItem? = runCatching {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) return getMediaItemAt(i)
    }
    return null
}.getOrNull()

fun Player.setPlaybackPitch(pitch: Float) {
    playbackParameters = PlaybackParameters(playbackParameters.speed, pitch)
}

operator fun Timeline.get(
    index: Int,
    window: Timeline.Window = Timeline.Window(),
    positionProjection: Duration = Duration.ZERO
): Timeline.Window = getWindow(index, window, positionProjection.inWholeMicroseconds)
