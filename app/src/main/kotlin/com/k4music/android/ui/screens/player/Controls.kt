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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.common.Player
import com.k4music.android.Database
import com.k4music.android.models.Info
import com.k4music.android.models.ui.UiMedia
import com.k4music.android.preferences.PlayerPreferences
import com.k4music.android.service.PlayerService
import com.k4music.android.ui.components.FadingRow
import com.k4music.android.ui.components.SeekBar
import com.k4music.android.ui.components.PlayerActionButton
import com.k4music.android.ui.screens.artistRoute
import com.k4music.android.utils.DisposableListener
import com.k4music.android.utils.bold
import com.k4music.android.utils.forceSeekToNext
import com.k4music.android.utils.forceSeekToPrevious
import com.k4music.android.utils.secondary
import com.k4music.android.utils.semiBold
import com.k4music.android.utils.shuffleQueue
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.favoritesIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Controls(
    media: UiMedia?,
    binder: PlayerService.Binder?,
    likedAt: Long?,
    setLikedAt: (Long?) -> Unit,
    shouldBePlaying: Boolean,
    position: Long,
    modifier: Modifier = Modifier
) {
    if (media == null || binder == null) return
    val palette = LocalAppearance.current.colorPalette
    var buffering by remember(binder) { mutableStateOf(binder.player.playbackState == Player.STATE_BUFFERING) }
    binder.player.DisposableListener {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MediaInfo(media, Modifier.weight(1f))
            PlayerActionButton(
                icon = if (likedAt == null) R.drawable.heart_outline else R.drawable.heart,
                label = stringResource(if (likedAt == null) R.string.like else R.string.unlike),
                onClick = { setLikedAt(if (likedAt == null) System.currentTimeMillis() else null) },
                active = likedAt != null, toggle = true,
                tint = if (likedAt != null) palette.favoritesIcon else palette.textSecondary
            )
        }
        SeekBar(binder, position, media, alwaysShowDuration = true, color = palette.accent,
            backgroundColor = palette.text.copy(alpha = 0.12f), scrubberRadius = 5.dp, shape = RoundedCornerShape(99.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PlayerActionButton(R.drawable.shuffle, stringResource(R.string.shuffle), { binder.player.shuffleQueue() }, tint = palette.textSecondary)
            PlayerActionButton(R.drawable.play_skip_back, stringResource(R.string.previous_track), binder.player::forceSeekToPrevious, iconSize = 30.dp)
            PlayerActionButton(
                icon = if (shouldBePlaying) R.drawable.pause else R.drawable.play,
                label = stringResource(if (shouldBePlaying) R.string.pause else R.string.play),
                onClick = {
                    if (shouldBePlaying) binder.player.pause() else {
                        if (binder.player.playbackState == Player.STATE_IDLE) binder.player.prepare()
                        binder.player.play()
                    }
                }, filled = true, loading = buffering, buttonSize = 68.dp, iconSize = 30.dp
            )
            PlayerActionButton(R.drawable.play_skip_forward, stringResource(R.string.next_track), binder.player::forceSeekToNext, iconSize = 30.dp)
            PlayerActionButton(
                R.drawable.infinite, stringResource(R.string.repeat_track),
                { PlayerPreferences.trackLoopEnabled = !PlayerPreferences.trackLoopEnabled },
                active = PlayerPreferences.trackLoopEnabled, toggle = true, tint = if (PlayerPreferences.trackLoopEnabled) palette.accent else palette.textSecondary
            )
        }
    }
}
@Composable
private fun MediaInfo(media: UiMedia, modifier: Modifier = Modifier) {
    val (colorPalette, typography) = LocalAppearance.current

    var artistInfo: List<Info>? by remember(media.id) { mutableStateOf(null) }
    var maxHeight by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(media) {
        withContext(Dispatchers.IO) {
            artistInfo = Database
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
                style = typography.m.bold,
                    maxLines = 1
                )
            }
        }

        AnimatedContent(
            targetState = media to artistInfo,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) { [media, state] ->
            state?.let { artists ->
                FadingRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        ,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    artists.fastForEachIndexed { i, artist ->
                        if (i == artists.lastIndex && artists.size > 1) BasicText(
                            text = " & ",
                            style = typography.s.semiBold.secondary
                        )
                        BasicText(
                            text = artist.name.orEmpty(),
                            style = typography.s.bold.secondary,
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
                            colorFilter = ColorFilter.tint(colorPalette.text),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            } ?: FadingRow(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = media.artist,
                    style = typography.s.semiBold.secondary,
                    maxLines = 1,
                    modifier = Modifier.onGloballyPositioned { maxHeight = it.size.height }
                )
                if (media.explicit) {
                    Spacer(Modifier.width(4.dp))

                    Image(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette.text),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

