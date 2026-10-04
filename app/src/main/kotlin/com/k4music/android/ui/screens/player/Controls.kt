package com.k4music.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.k4music.android.Database
import com.k4music.android.models.Info
import com.k4music.android.models.ui.UiMedia
import com.k4music.android.models.ui.toUiMedia
import com.k4music.android.preferences.PlayerPreferences
import com.k4music.android.preferences.PlayerPreferences.RepeatMode
import com.k4music.android.service.PlayerService
import com.k4music.android.ui.components.FadingRow
import com.k4music.android.ui.components.PlayerActionButton
import com.k4music.android.ui.components.SeekBar
import com.k4music.android.ui.screens.artistRoute
import com.k4music.android.utils.DisposableListener
import com.k4music.android.utils.bold
import com.k4music.android.utils.forceSeekToNext
import com.k4music.android.utils.forceSeekToPrevious
import com.k4music.android.utils.secondary
import com.k4music.android.utils.semiBold
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.favoritesIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Controls(
    mediaItem: MediaItem?,
    binder: PlayerService.Binder?,
    likedAt: Long?,
    setLikedAt: (Long?) -> Unit,
    shouldBePlaying: Boolean,
    progress: State<Pair<Long, Long>>,
    modifier: Modifier = Modifier
) {
    if (mediaItem == null || binder == null) return
    val palette = LocalAppearance.current.colorPalette
    val media = remember(mediaItem) { mediaItem.toUiMedia(androidx.media3.common.C.TIME_UNSET) }

    var buffering by remember(binder) { mutableStateOf(binder.player.playbackState == Player.STATE_BUFFERING) }
    binder.player.DisposableListener {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MediaInfo(media, Modifier.weight(1f))
            PlayerActionButton(
                icon = if (likedAt == null) R.drawable.heart_outline else R.drawable.heart,
                label = stringResource(if (likedAt == null) R.string.like else R.string.unlike),
                onClick = { setLikedAt(if (likedAt == null) System.currentTimeMillis() else null) },
                active = likedAt != null,
                toggle = true,
                tint = if (likedAt != null) palette.favoritesIcon else palette.textSecondary
            )
        }

        PlayerSeekBar(binder = binder, mediaItem = mediaItem, progress = progress)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shuffle = PlayerPreferences.shuffleEnabled
            PlayerActionButton(
                icon = R.drawable.shuffle,
                label = stringResource(if (shuffle) R.string.shuffle_on else R.string.shuffle_off),
                onClick = { PlayerPreferences.shuffleEnabled = !shuffle },
                active = shuffle,
                toggle = true,
                tint = if (shuffle) palette.accent else palette.textSecondary,
                iconSize = 22.dp
            )
            PlayerActionButton(
                icon = R.drawable.play_skip_back,
                label = stringResource(R.string.previous_track),
                onClick = binder.player::forceSeekToPrevious,
                buttonSize = 56.dp,
                iconSize = 30.dp
            )
            PlayerActionButton(
                icon = if (shouldBePlaying) R.drawable.pause else R.drawable.play,
                label = stringResource(if (shouldBePlaying) R.string.pause else R.string.play),
                onClick = {
                    if (shouldBePlaying) binder.player.pause() else {
                        if (binder.player.playbackState == Player.STATE_IDLE) binder.player.prepare()
                        binder.player.play()
                    }
                },
                filled = true,
                loading = buffering,
                buttonSize = 72.dp,
                iconSize = 32.dp
            )
            PlayerActionButton(
                icon = R.drawable.play_skip_forward,
                label = stringResource(R.string.next_track),
                onClick = binder.player::forceSeekToNext,
                buttonSize = 56.dp,
                iconSize = 30.dp
            )
            val repeat = PlayerPreferences.repeatMode
            PlayerActionButton(
                icon = if (repeat == RepeatMode.One) R.drawable.repeat_one else R.drawable.repeat,
                label = stringResource(
                    when (repeat) {
                        RepeatMode.Off -> R.string.repeat_off
                        RepeatMode.All -> R.string.repeat_all
                        RepeatMode.One -> R.string.repeat_one
                    }
                ),
                onClick = { PlayerPreferences.repeatMode = repeat.next },
                active = repeat != RepeatMode.Off,
                toggle = true,
                tint = if (repeat != RepeatMode.Off) palette.accent else palette.textSecondary,
                iconSize = 22.dp
            )
        }
    }
}

/** The only part of the expanded player that recomposes on every progress tick. */
@Composable
private fun PlayerSeekBar(
    binder: PlayerService.Binder,
    mediaItem: MediaItem,
    progress: State<Pair<Long, Long>>
) {
    val palette = LocalAppearance.current.colorPalette
    val (position, duration) = progress.value
    val media = remember(mediaItem, duration) { mediaItem.toUiMedia(duration) }

    SeekBar(
        binder = binder,
        position = position,
        media = media,
        alwaysShowDuration = true,
        color = palette.accent,
        backgroundColor = palette.text.copy(alpha = 0.12f),
        scrubberRadius = 6.dp,
        shape = RoundedCornerShape(99.dp)
    )
}

@Composable
private fun MediaInfo(media: UiMedia, modifier: Modifier = Modifier) {
    val (colorPalette, typography) = LocalAppearance.current

    var artistInfo: List<Info>? by remember(media.id) { mutableStateOf(null) }

    LaunchedEffect(media.id) {
        artistInfo = withContext(Dispatchers.IO) {
            Database
                .songArtistInfo(media.id)
                .takeIf { it.isNotEmpty() }
        }
    }

    Column(horizontalAlignment = Alignment.Start, modifier = modifier) {
        AnimatedContent(
            targetState = media.title,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) { title ->
            FadingRow(modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = title,
                    style = typography.l.bold,
                    maxLines = 1
                )
            }
        }

        AnimatedContent(
            targetState = media to artistInfo,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) { (media, artists) ->
            FadingRow(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (artists == null) BasicText(
                    text = media.artist,
                    style = typography.s.semiBold.secondary,
                    maxLines = 1
                ) else artists.fastForEachIndexed { i, artist ->
                    if (i == artists.lastIndex && artists.size > 1) BasicText(
                        text = " & ",
                        style = typography.s.semiBold.secondary
                    )
                    BasicText(
                        text = artist.name.orEmpty(),
                        style = typography.s.semiBold.secondary,
                        modifier = Modifier.clickable { artistRoute.global(artist.id) }
                    )
                    if (i != artists.lastIndex && i + 1 != artists.lastIndex) BasicText(
                        text = ", ",
                        style = typography.s.semiBold.secondary
                    )
                }

                if (media.explicit) {
                    Spacer(Modifier.width(4.dp))
                    Image(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
