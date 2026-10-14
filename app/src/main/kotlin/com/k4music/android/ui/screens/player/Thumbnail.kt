package com.k4music.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Left
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Right
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.k4music.android.Database
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.app.R
import com.k4music.android.preferences.PlayerPreferences
import com.k4music.android.service.LoginRequiredException
import com.k4music.android.service.PlayableFormatNotFoundException
import com.k4music.android.service.RestrictedVideoException
import com.k4music.android.service.UnplayableException
import com.k4music.android.service.VideoIdMismatchException
import com.k4music.android.service.isLocal
import com.k4music.android.ui.modifiers.onSwipe
import com.k4music.android.utils.findCause
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import java.net.SocketTimeoutException
import com.k4music.android.utils.forceSeekToNext
import com.k4music.android.utils.forceSeekToPrevious
import com.k4music.android.utils.isInPip
import com.k4music.android.utils.thumbnail
import com.k4music.android.utils.windowState
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.utils.px
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

@Composable
fun Thumbnail(
    isShowingLyrics: Boolean,
    onShowLyrics: (Boolean) -> Unit,
    isShowingStatsForNerds: Boolean,
    onShowStatsForNerds: (Boolean) -> Unit,
    onOpenDialog: () -> Unit,
    likedAt: Long?,
    setLikedAt: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
    shouldShowSynchronizedLyrics: Boolean = PlayerPreferences.isShowingSynchronizedLyrics,
    setShouldShowSynchronizedLyrics: (Boolean) -> Unit = {
        PlayerPreferences.isShowingSynchronizedLyrics = it
    },
    showLyricsControls: Boolean = true
) {
    val binder = LocalPlayerServiceBinder.current
    val [colorPalette, _, _, thumbnailShape] = LocalAppearance.current

    val [window, error] = windowState()

    val coroutineScope = rememberCoroutineScope()
    val transitionState = remember { SeekableTransitionState(false) }
    val transition = rememberTransition(transitionState)
    val opacity by transition.animateFloat(label = "") { if (it) 1f else 0f }
    val scale by transition.animateFloat(
        label = "",
        transitionSpec = {
            spring(dampingRatio = Spring.DampingRatioLowBouncy)
        }
    ) { if (it) 1f else 0f }
    val isInPip = isInPip()

    AnimatedContent(
        targetState = window,
        transitionSpec = {
            val duration = 500
            val initial = initialState
            val target = targetState

            if (initial == null || target == null) return@AnimatedContent ContentTransform(
                targetContentEnter = fadeIn(tween(duration)),
                initialContentExit = fadeOut(tween(duration)),
                sizeTransform = null
            )

            val sizeTransform = SizeTransform(clip = false) { _, _ ->
                tween(durationMillis = duration, delayMillis = duration)
            }

            val direction = if (target.firstPeriodIndex < initial.firstPeriodIndex) Right else Left

            ContentTransform(
                targetContentEnter = slideIntoContainer(direction, tween(duration)) +
                    fadeIn(tween(duration)) +
                    scaleIn(tween(duration), 0.85f),
                initialContentExit = slideOutOfContainer(direction, tween(duration)) +
                    fadeOut(tween(duration)) +
                    scaleOut(tween(duration), 0.85f),
                sizeTransform = sizeTransform
            )
        },
        modifier = modifier.onSwipe(
            onSwipeLeft = {
                binder?.player?.forceSeekToNext()
            },
            onSwipeRight = {
                binder?.player?.forceSeekToPrevious(seekToStart = false)
            }
        ),
        contentAlignment = Alignment.Center,
        label = ""
    ) { currentWindow ->
        val shadowElevation by animateDpAsState(
            targetValue = if (window == currentWindow) 8.dp else 0.dp,
            animationSpec = tween(
                durationMillis = 500,
                easing = LinearEasing
            ),
            label = ""
        )
        val blurRadius by animateDpAsState(
            targetValue = if (isShowingLyrics || error != null || isShowingStatsForNerds) 8.dp else 0.dp,
            animationSpec = tween(500),
            label = ""
        )

        if (currentWindow != null) Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(if (isInPip) RectangleShape else thumbnailShape)
                .shadow(
                    elevation = shadowElevation,
                    shape = thumbnailShape,
                    clip = false
                )
        ) {
            var height by remember { mutableIntStateOf(0) }
            // Double tap: left third back 10 s, right third forward 10 s, middle likes the song.
            val currentLikedAt by rememberUpdatedState(likedAt)
            val currentBinder by rememberUpdatedState(binder)
            var seekHint by remember { mutableStateOf<Pair<Int, Long>?>(null) }
            LaunchedEffect(seekHint) { if (seekHint != null) { delay(700); seekHint = null } }

            AsyncImage(
                model = currentWindow.mediaItem.mediaMetadata.artworkUri
                    ?.thumbnail((Dimensions.thumbnails.player.song - 64.dp).px),
                placeholder = painterResource(id = R.drawable.ic_launcher_foreground),
                error = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onShowLyrics(true) },
                            onLongPress = { onShowStatsForNerds(true) },
                            onDoubleTap = { offset ->
                                val player = currentBinder?.player
                                val third = size.width / 3f
                                val step = when {
                                    player == null -> 0
                                    offset.x < third -> -1
                                    offset.x > third * 2 -> 1
                                    else -> 0
                                }
                                if (step != 0 && player != null) {
                                    val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                                    val target = (player.currentPosition + step * SEEK_STEP_MS)
                                        .coerceAtLeast(0L)
                                        .let { if (duration != null) it.coerceAtMost(duration - 500L) else it }
                                    player.seekTo(target)
                                    seekHint = step to System.nanoTime()
                                } else {
                                    if (currentLikedAt == null) setLikedAt(System.currentTimeMillis())

                                    coroutineScope.launch {
                                        val spec = tween<Float>(durationMillis = 500)
                                        transitionState.animateTo(true, spec)
                                        transitionState.animateTo(false, spec)
                                    }
                                }
                            }
                        )
                    }
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .background(colorPalette.background0)
                    .let {
                        if (blurRadius == 0.dp) it else it.blur(radius = blurRadius)
                    }
                    .animateContentSize()
                    .onGloballyPositioned { coords ->
                        coords.size.height.let { if (it > 0) height = it }
                    }
            )

            Lyrics(
                mediaId = currentWindow.mediaItem.mediaId,
                isDisplayed = isShowingLyrics && error == null,
                onDismiss = { onShowLyrics(false) },
                ensureSongInserted = { Database.insert(currentWindow.mediaItem) },
                mediaMetadataProvider = currentWindow.mediaItem::mediaMetadata,
                durationProvider = { binder?.player?.duration ?: C.TIME_UNSET },
                onOpenDialog = onOpenDialog,
                modifier = Modifier.height(height.px.dp),
                shouldShowSynchronizedLyrics = shouldShowSynchronizedLyrics,
                setShouldShowSynchronizedLyrics = setShouldShowSynchronizedLyrics,
                showControls = showLyricsControls
            )

            StatsForNerds(
                mediaId = currentWindow.mediaItem.mediaId,
                isDisplayed = isShowingStatsForNerds && error == null,
                onDismiss = { onShowStatsForNerds(false) },
                modifier = Modifier.height(height.px.dp)
            )

            seekHint?.let { (step, _) ->
                BasicText(
                    text = if (step < 0) "« 10s" else "10s »",
                    style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(if (step < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 24.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            Image(
                painter = painterResource(R.drawable.heart),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.accent),
                modifier = Modifier
                    .fillMaxSize(0.5f)
                    .aspectRatio(1f)
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        alpha = opacity,
                        shadowElevation = 8.dp.px.toFloat()
                    )
            )

            PlaybackError(
                isDisplayed = error != null,
                messageProvider = {
                    if (currentWindow.mediaItem.isLocal) stringResource(R.string.error_local_music_deleted)
                    else when {
                        error?.findCause<UnresolvedAddressException>() != null || error?.findCause<UnknownHostException>() != null || error?.findCause<SocketTimeoutException>() != null || error?.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> stringResource(R.string.error_network)
                        error?.findCause<PlayableFormatNotFoundException>() != null || error?.findCause<UnplayableException>() != null -> stringResource(R.string.error_unplayable)
                        error?.findCause<LoginRequiredException>() != null || error?.findCause<RestrictedVideoException>() != null || error?.findCause<InvalidResponseCodeException>()?.responseCode in setOf(401, 403) -> stringResource(R.string.error_server_restrictions)
                        error?.findCause<VideoIdMismatchException>() != null -> stringResource(R.string.error_id_mismatch)
                        else -> stringResource(R.string.error_unknown_playback)
                    }

                },
                onDismiss = { binder?.player?.prepare() },
                modifier = Modifier.height(height.px.dp)
            )
        }
    }
}

private const val SEEK_STEP_MS = 10_000L
