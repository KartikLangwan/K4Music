package com.k4music.android.utils

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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

fun Player.shuffleQueue() {
    if (currentMediaItemIndex !in 0 until mediaItemCount) return
    val mediaItems = currentTimeline
        .mediaItems
        .toMutableList()
        .apply { removeAt(currentMediaItemIndex) }
        .shuffled()

    safeClearQueue()
    addMediaItems(mediaItems)
}

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

@Suppress("NestedBlockDepth") // TODO
fun Player.forceSeekToPrevious(
    hideExplicit: Boolean = AppearancePreferences.hideExplicit,
    seekToStart: Boolean = true
): Unit = when {
    seekToStart && currentPosition > maxSeekToPreviousPosition -> seekToPrevious()

    hideExplicit -> if (mediaItemCount <= 1) {
        forceSeekToPrevious(hideExplicit = false)
    } else {
        adjacentPlayableIndex(mediaItemCount, currentMediaItemIndex, -1) {
            getMediaItemAt(it).mediaMetadata.extras?.songBundle?.explicit != true
        }?.let { seekTo(it, C.TIME_UNSET) } ?: pause()
    }

    // fall back to default behavior if there is only a single song

    hasPreviousMediaItem() -> seekToPreviousMediaItem()

    mediaItemCount > 0 -> seekTo(mediaItemCount - 1, C.TIME_UNSET)

    else -> {}
}

fun Player.forceSeekToNext() {
    if (mediaItemCount == 0) return
    val index = adjacentPlayableIndex(mediaItemCount, currentMediaItemIndex, 1) {
        !AppearancePreferences.hideExplicit || getMediaItemAt(it).mediaMetadata.extras?.songBundle?.explicit != true
    }
    if (index != null) seekTo(index, C.TIME_UNSET) else pause()
}

/** A bounded search also handles queues where every song is filtered out. */
internal fun adjacentPlayableIndex(count: Int, current: Int, direction: Int, playable: (Int) -> Boolean): Int? {
    if (count <= 0) return null
    val start = current.coerceIn(0, count - 1)
    for (step in 1..count) {
        val index = Math.floorMod(start + step * direction, count)
        if (playable(index)) return index
    }
    return null
}

fun Player.addNext(mediaItem: MediaItem) = when (playbackState) {
    Player.STATE_IDLE, Player.STATE_ENDED -> forcePlay(mediaItem)
    else -> addMediaItem(currentMediaItemIndex + 1, mediaItem)
}

fun Player.enqueue(mediaItem: MediaItem) = when (playbackState) {
    Player.STATE_IDLE, Player.STATE_ENDED -> forcePlay(mediaItem)
    else -> addMediaItem(mediaItemCount, mediaItem)
}

fun Player.enqueue(mediaItems: List<MediaItem>) = when (playbackState) {
    Player.STATE_IDLE, Player.STATE_ENDED -> forcePlayFromBeginning(mediaItems)
    else -> addMediaItems(mediaItemCount, mediaItems)
}

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
