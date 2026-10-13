package com.k4music.android.ui.screens.player

import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.k4music.android.Database
import com.k4music.android.LocalBottomNavigationHeight
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.android.preferences.PlayerPreferences
import com.k4music.android.query
import com.k4music.android.service.PlayerService
import com.k4music.android.transaction
import com.k4music.android.ui.components.BottomSheet
import com.k4music.android.ui.components.BottomSheetState
import com.k4music.android.ui.components.LocalMenuState
import com.k4music.android.ui.components.PressFeedbackIconButton
import com.k4music.android.ui.components.rememberBottomSheetState
import com.k4music.android.ui.components.themed.BaseMediaItemMenu
import com.k4music.android.ui.components.themed.SecondaryTextButton
import com.k4music.android.ui.components.themed.SliderDialog
import com.k4music.android.ui.components.themed.SliderDialogBody
import com.k4music.android.ui.modifiers.PinchDirection
import com.k4music.android.ui.modifiers.onSwipe
import com.k4music.android.ui.modifiers.pinchToToggle
import com.k4music.android.ui.screens.audioRoute
import com.k4music.android.utils.DisposableListener
import com.k4music.android.utils.Pip
import com.k4music.android.utils.forceSeekToNext
import com.k4music.android.utils.forceSeekToPrevious
import com.k4music.android.utils.playbackFraction
import com.k4music.android.utils.playingSong
import com.k4music.android.utils.positionAndDurationState
import com.k4music.android.utils.rememberPipHandler
import com.k4music.android.utils.seamlessPlay
import com.k4music.android.utils.shouldBePlaying
import com.k4music.android.utils.thumbnail
import com.k4music.app.R
import com.k4music.compose.persist.PersistMapCleanup
import com.k4music.compose.routing.OnGlobalRoute
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.favoritesIcon
import com.k4music.core.ui.utils.isLandscape
import com.k4music.core.ui.utils.px
import com.k4music.providers.innertube.models.NavigationEndpoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged

private val MiniPlayerHeight = 56.dp

@Composable
fun Player(
    layoutState: BottomSheetState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp
    ),
    windowInsets: WindowInsets = WindowInsets.systemBars
) = with(PlayerPreferences) {
    val menuState = LocalMenuState.current
    val colorPalette = LocalAppearance.current.colorPalette
    val binder = LocalPlayerServiceBinder.current

    val pipHandler = rememberPipHandler()

    PersistMapCleanup(prefix = "queue/suggestions")

    var mediaItem by remember(binder) {
        mutableStateOf(
            value = binder?.player?.currentMediaItem,
            policy = neverEqualPolicy()
        )
    }
    var shouldBePlaying by remember(binder) { mutableStateOf(binder?.player?.shouldBePlaying == true) }

    var likedAt by remember(mediaItem?.mediaId) { mutableStateOf<Long?>(null) }
    val updateLike: (Long?) -> Unit = { stamp ->
        val item = mediaItem
        likedAt = stamp
        if (item != null) query {
            if (Database.like(item.mediaId, stamp) == 0) {
                Database.insert(item) { it.copy(likedAt = stamp) }
            }
        }
    }
    LaunchedEffect(mediaItem?.mediaId) {
        mediaItem?.mediaId?.let { mediaId ->
            Database
                .likedAt(mediaId)
                .distinctUntilChanged()
                .collect { likedAt = it }
        }
    }

    binder?.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(newMediaItem: MediaItem?, reason: Int) {
                mediaItem = newMediaItem
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldBePlaying = player.shouldBePlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                shouldBePlaying = player.shouldBePlaying
            }
        }
    }

    // Read only by the progress widgets, so ticks do not recompose the whole player.
    val progress = binder?.player.positionAndDurationState()

    val bottomPadding = windowInsets
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()

    OnGlobalRoute { if (layoutState.expanded) layoutState.collapseSoft() }

    val currentItem = mediaItem
    if (currentItem != null) BottomSheet(
        state = layoutState,
        modifier = modifier.fillMaxSize(),
        onDismiss = {
            binder?.let { onDismiss(it) }
            layoutState.dismissSoft()
        },
        backHandlerEnabled = !menuState.isDisplayed,
        collapsedContent = { innerModifier ->
            MiniPlayer(
                mediaItem = currentItem,
                binder = binder,
                shouldBePlaying = shouldBePlaying,
                likedAt = likedAt,
                onLike = { updateLike(if (likedAt == null) System.currentTimeMillis() else null) },
                progress = progress,
                bottomPadding = LocalBottomNavigationHeight.current + bottomPadding,
                modifier = innerModifier,
                onSwipeOut = if (horizontalSwipeToClose) { animationJob ->
                    binder?.let { onDismiss(it) }
                    animationJob.join()
                    layoutState.dismissSoft()
                } else null
            )
        }
    ) {
        var isShowingStatsForNerds by rememberSaveable { mutableStateOf(false) }
        var isShowingLyricsDialog by rememberSaveable { mutableStateOf(false) }

        if (isShowingLyricsDialog) LyricsDialog(onDismiss = { isShowingLyricsDialog = false })

        val queueSheetState = rememberBottomSheetState(
            dismissedBound = 64.dp + bottomPadding,
            expandedBound = layoutState.expandedBound
        )

        val containerModifier = Modifier
            .background(colorPalette.background0)
            .livingBackdrop(accent = colorPalette.accent, playing = shouldBePlaying)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to colorPalette.background1.copy(alpha = 0.35f),
                    1f to colorPalette.background0
                )
            )
            .padding(
                windowInsets
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    .asPaddingValues()
            )

        val thumbnailContent: @Composable (modifier: Modifier) -> Unit = { innerModifier ->
            Pip(
                numerator = 1,
                denominator = 1,
                modifier = innerModifier
            ) {
                Thumbnail(
                    isShowingLyrics = isShowingLyrics,
                    onShowLyrics = { isShowingLyrics = it },
                    isShowingStatsForNerds = isShowingStatsForNerds,
                    onShowStatsForNerds = { isShowingStatsForNerds = it },
                    onOpenDialog = { isShowingLyricsDialog = true },
                    likedAt = likedAt,
                    setLikedAt = updateLike,
                    modifier = Modifier
                        .nestedScroll(layoutState.preUpPostDownNestedScrollConnection)
                        .pinchToToggle(
                            key = isShowingLyricsDialog,
                            direction = PinchDirection.Out,
                            threshold = 1.05f,
                            onPinch = {
                                if (isShowingLyrics) isShowingLyricsDialog = true
                            }
                        )
                        .pinchToToggle(
                            key = isShowingLyricsDialog,
                            direction = PinchDirection.In,
                            threshold = .95f,
                            onPinch = {
                                pipHandler.enterPictureInPictureMode()
                            }
                        )
                )
            }
        }

        var speedDialogOpen by rememberSaveable { mutableStateOf(false) }
        var boostDialogOpen by rememberSaveable { mutableStateOf(false) }

        val queueBarHeight = 64.dp + bottomPadding

        val openMenu = {
            binder?.let { activeBinder ->
                menuState.display {
                    PlayerMenu(
                        onDismiss = menuState::hide,
                        mediaItem = currentItem,
                        binder = activeBinder,
                        onShowSpeedDialog = { speedDialogOpen = true },
                        onShowNormalizationDialog = { boostDialogOpen = true }.takeIf { volumeNormalization }
                    )
                }
            }
            Unit
        }

        val controlsContent: @Composable (modifier: Modifier) -> Unit = { innerModifier ->
            Controls(
                mediaItem = currentItem,
                binder = binder,
                likedAt = likedAt,
                setLikedAt = updateLike,
                shouldBePlaying = shouldBePlaying,
                progress = progress,
                modifier = innerModifier
            )
        }

        if (isLandscape) Column(containerModifier.fillMaxSize().padding(bottom = queueBarHeight)) {
            PlayerHeader(
                albumTitle = currentItem.mediaMetadata.albumTitle?.toString(),
                onCollapse = layoutState::collapseSoft,
                onMore = openMenu
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    thumbnailContent(Modifier.size(maxWidth.coerceAtMost(maxHeight)).clip(RoundedCornerShape(20.dp)))
                }
                controlsContent(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        } else Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = containerModifier
                .fillMaxSize()
                .padding(bottom = queueBarHeight + 8.dp)
        ) {
            PlayerHeader(
                albumTitle = currentItem.mediaMetadata.albumTitle?.toString(),
                onCollapse = layoutState::collapseSoft,
                onMore = openMenu
            )

            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                thumbnailContent(
                    Modifier
                        .size(maxWidth.coerceAtMost(maxHeight))
                        .clip(RoundedCornerShape(20.dp))
                )
            }

            controlsContent(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        if (speedDialogOpen) SliderDialog(
            onDismiss = { speedDialogOpen = false },
            title = stringResource(R.string.playback_settings)
        ) {
            SliderDialogBody(
                provideState = { remember(speed) { mutableFloatStateOf(speed) } },
                onSlideComplete = { speed = it },
                min = 0f,
                max = 2f,
                toDisplay = {
                    if (it <= 0.01f) stringResource(R.string.minimum_speed_value)
                    else stringResource(R.string.format_multiplier, "%.2f".format(it))
                },
                steps = 39,
                label = stringResource(R.string.playback_speed)
            )
            SliderDialogBody(
                provideState = { remember(pitch) { mutableFloatStateOf(pitch) } },
                onSlideComplete = { pitch = it },
                min = 0f,
                max = 2f,
                toDisplay = {
                    if (it <= 0.01f) stringResource(R.string.minimum_speed_value)
                    else stringResource(R.string.format_multiplier, "%.2f".format(it))
                },
                steps = 39,
                label = stringResource(R.string.playback_pitch)
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                SecondaryTextButton(
                    text = stringResource(R.string.reset),
                    onClick = {
                        speed = 1f
                        pitch = 1f
                    }
                )
            }
        }

        if (boostDialogOpen) {
            val mediaId = currentItem.mediaId
            fun submit(state: Float) = transaction {
                Database.setLoudnessBoost(
                    songId = mediaId,
                    loudnessBoost = state.takeUnless { it == 0f }
                )
            }

            SliderDialog(
                onDismiss = { boostDialogOpen = false },
                title = stringResource(R.string.volume_boost)
            ) {
                SliderDialogBody(
                    provideState = {
                        val state = remember { mutableFloatStateOf(0f) }

                        LaunchedEffect(mediaId) {
                            Database
                                .loudnessBoost(mediaId)
                                .distinctUntilChanged()
                                .collect { state.floatValue = it ?: 0f }
                        }

                        state
                    },
                    onSlideComplete = { submit(it) },
                    min = -20f,
                    max = 20f,
                    toDisplay = { stringResource(R.string.format_db, "%.2f".format(it)) }
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    SecondaryTextButton(
                        text = stringResource(R.string.reset),
                        onClick = { submit(0f) }
                    )
                }
            }
        }

        if (binder != null) Queue(
            layoutState = queueSheetState,
            binder = binder,
            beforeContent = {
                PressFeedbackIconButton(
                    onClick = { isShowingLyrics = !isShowingLyrics },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.text),
                        contentDescription = stringResource(R.string.lyrics),
                        tint = if (isShowingLyrics) colorPalette.accent else colorPalette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            afterContent = {
                PressFeedbackIconButton(
                    onClick = { audioRoute.global() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.equalizer),
                        contentDescription = stringResource(R.string.audio_effects),
                        tint = colorPalette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
            shape = shape
        )
    }
}

@Composable
private fun PlayerHeader(
    albumTitle: String?,
    onCollapse: () -> Unit,
    onMore: () -> Unit
) {
    val palette = LocalAppearance.current.colorPalette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PressFeedbackIconButton(onClick = onCollapse, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.chevron_down),
                contentDescription = stringResource(R.string.collapse_player),
                tint = palette.text
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.now_playing).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (!albumTitle.isNullOrBlank()) Text(
                text = albumTitle,
                style = MaterialTheme.typography.labelLarge,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        PressFeedbackIconButton(onClick = onMore, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.ellipsis_horizontal),
                contentDescription = stringResource(R.string.more),
                tint = palette.text
            )
        }
    }
}

/** Docked directly above the bottom bar; tapping it opens the full player. */
@Composable
private fun MiniPlayer(
    mediaItem: MediaItem,
    binder: PlayerService.Binder?,
    shouldBePlaying: Boolean,
    likedAt: Long?,
    onLike: () -> Unit,
    progress: State<Pair<Long, Long>>,
    bottomPadding: Dp,
    onSwipeOut: (suspend (Job) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppearance.current.colorPalette
    val metadata = mediaItem.mediaMetadata
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .clip(shape)
                .background(palette.background2)
                .then(modifier)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 2.dp)
                    .let {
                        if (onSwipeOut != null) it.onSwipe(animateOffset = true, onSwipeOut = onSwipeOut)
                        else it
                    }
            ) {
                AsyncImage(
                    model = metadata.artworkUri?.thumbnail(Dimensions.thumbnails.song.px),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.musical_notes),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.background1)
                )

                AnimatedContent(
                    targetState = metadata.title?.toString().orEmpty() to metadata.artist?.toString().orEmpty(),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "mini player text",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) { (title, artist) ->
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (artist.isNotBlank()) Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (PlayerPreferences.showLike) PressFeedbackIconButton(onClick = onLike, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(if (likedAt == null) R.drawable.heart_outline else R.drawable.heart),
                        contentDescription = stringResource(if (likedAt == null) R.string.like else R.string.unlike),
                        tint = if (likedAt != null) palette.favoritesIcon else palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (PlayerPreferences.isShowingPrevButtonCollapsed) PressFeedbackIconButton(
                    onClick = { binder?.player?.forceSeekToPrevious() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play_skip_back),
                        contentDescription = stringResource(R.string.previous_track),
                        tint = palette.text,
                        modifier = Modifier.size(20.dp)
                    )
                }
                PressFeedbackIconButton(
                    onClick = {
                        val player = binder?.player ?: return@PressFeedbackIconButton
                        if (shouldBePlaying) player.pause() else {
                            if (player.playbackState == Player.STATE_IDLE) player.prepare()
                            player.play()
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(if (shouldBePlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = stringResource(if (shouldBePlaying) R.string.pause else R.string.play),
                        tint = palette.text,
                        modifier = Modifier.size(24.dp)
                    )
                }
                PressFeedbackIconButton(
                    onClick = { binder?.player?.forceSeekToNext() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play_skip_forward),
                        contentDescription = stringResource(R.string.next_track),
                        tint = palette.text,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            MiniProgress(
                progress = progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(2.dp)
            )
        }
    }
}

/** Draws in the draw phase only: progress ticks never trigger recomposition. */
@Composable
private fun MiniProgress(progress: State<Pair<Long, Long>>, modifier: Modifier = Modifier) {
    val palette = LocalAppearance.current.colorPalette
    Box(
        modifier.drawBehind {
            val (position, duration) = progress.value
            val y = size.height / 2
            drawLine(palette.text.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
            val end = size.width * playbackFraction(position, duration)
            if (end > 0f) drawLine(palette.accent, Offset(0f, y), Offset(end, y), size.height, StrokeCap.Round)
        }
    )
}

@Composable
@OptIn(UnstableApi::class)
private fun PlayerMenu(
    binder: PlayerService.Binder,
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onShowSpeedDialog: (() -> Unit)? = null,
    onShowNormalizationDialog: (() -> Unit)? = null
) {
    // The menu belongs to the song it was opened for; close it when playback moves on.
    val (playingId, _) = playingSong(binder)
    LaunchedEffect(playingId) {
        if (playingId != null && playingId != mediaItem.mediaId) onDismiss()
    }

    BaseMediaItemMenu(
        mediaItem = mediaItem,
        onStartRadio = {
            binder.stopRadio()
            binder.player.seamlessPlay(mediaItem)
            binder.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
        },
        onGoToEqualizer = { audioRoute.global() },
        onShowSleepTimer = {},
        onDismiss = onDismiss,
        onShowSpeedDialog = onShowSpeedDialog,
        onShowNormalizationDialog = onShowNormalizationDialog
    )
}

private fun onDismiss(binder: PlayerService.Binder) {
    binder.stopRadio()
    binder.player.clearMediaItems()
}

/**
 * An aurora in the song's own accent behind the full player: two soft glows that drift
 * while music plays and settle when paused. Draw-phase only; frozen when the system
 * animation scale is off.
 */
@Composable
private fun Modifier.livingBackdrop(accent: Color, playing: Boolean): Modifier {
    val context = LocalContext.current
    val motion = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f
    }
    val partner = remember(accent) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
        hsv[0] = (hsv[0] + 48f) % 360f
        Color(android.graphics.Color.HSVToColor(hsv))
    }
    val phase = remember { Animatable(0f) }
    LaunchedEffect(playing, motion) {
        if (!playing || !motion) return@LaunchedEffect
        while (true) phase.animateTo(phase.value + 2f * PI.toFloat(), tween(BACKDROP_CYCLE_MS, easing = LinearEasing))
    }
    val energy by animateFloatAsState(if (playing) 1f else 0f, tween(1_200), label = "backdropEnergy")

    return drawBehind {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawBehind
        val a = phase.value
        val glow = 0.16f + 0.10f * energy
        fun glowAt(x: Float, y: Float, radius: Float, color: Color) = drawCircle(
            brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x, y), radius = radius),
            radius = radius,
            center = Offset(x, y)
        )
        glowAt(w * (0.28f + 0.14f * cos(a)), h * (0.16f + 0.05f * sin(a)), w * 0.95f, accent.copy(alpha = glow))
        glowAt(
            w * (0.78f + 0.12f * sin(a * 0.7f + 1f)),
            h * (0.40f + 0.07f * cos(a * 1.3f)),
            w * 0.8f,
            partner.copy(alpha = glow * 0.75f)
        )
    }
}

private const val BACKDROP_CYCLE_MS = 18_000
