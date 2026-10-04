package com.k4music.android.ui.screens.player

import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SnapshotMutationPolicy
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.k4music.android.Database
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.app.R
import com.k4music.android.models.ui.toUiMedia
import com.k4music.android.preferences.PlayerPreferences
import com.k4music.android.query
import com.k4music.android.service.PlayerService
import com.k4music.android.transaction
import com.k4music.android.ui.components.BottomSheet
import com.k4music.android.ui.components.PressFeedbackIconButton
import com.k4music.android.ui.components.BottomSheetState
import com.k4music.android.ui.components.LocalMenuState
import com.k4music.android.ui.components.rememberBottomSheetState
import com.k4music.android.ui.components.themed.BaseMediaItemMenu
import com.k4music.android.ui.components.themed.IconButton
import com.k4music.android.ui.components.themed.SecondaryTextButton
import com.k4music.android.ui.components.themed.SliderDialog
import com.k4music.android.ui.components.themed.SliderDialogBody
import com.k4music.android.ui.modifiers.PinchDirection
import com.k4music.android.ui.modifiers.onSwipe
import com.k4music.android.ui.modifiers.pinchToToggle
import com.k4music.android.utils.DisposableListener
import com.k4music.android.utils.Pip
import com.k4music.android.utils.forceSeekToNext
import com.k4music.android.utils.forceSeekToPrevious
import com.k4music.android.utils.positionAndDurationState
import com.k4music.android.utils.playbackFraction
import com.k4music.android.utils.rememberEqualizerLauncher
import com.k4music.android.utils.rememberPipHandler
import com.k4music.android.utils.seamlessPlay
import com.k4music.android.utils.secondary
import com.k4music.android.utils.semiBold
import com.k4music.android.utils.shouldBePlaying
import com.k4music.android.utils.thumbnail
import com.k4music.compose.persist.PersistMapCleanup
import com.k4music.compose.routing.OnGlobalRoute
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.ThumbnailRoundness
import com.k4music.core.ui.collapsedPlayerProgressBar
import com.k4music.core.ui.utils.isLandscape
import com.k4music.core.ui.utils.px
import com.k4music.core.ui.utils.roundedShape
import com.k4music.core.ui.utils.songBundle
import com.k4music.android.LocalHasBottomNavigation
import com.k4music.core.ui.favoritesIcon
import androidx.compose.foundation.border
import com.k4music.providers.innertube.models.NavigationEndpoint
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.absoluteValue

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
    val [colorPalette, typography, thumbnailCornerSize] = LocalAppearance.current
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
        val id = item?.mediaId
        likedAt = stamp
        if (item != null && id != null) query {
            if (Database.like(id, stamp) == 0) {
                Database.insert(item) { it.copy(likedAt = stamp) }
            }
        }
    }
    LaunchedEffect(mediaItem) {
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

    val (position, duration) = binder?.player.positionAndDurationState()
    val metadata = remember(mediaItem) { mediaItem?.mediaMetadata }
    val extras = remember(metadata) { metadata?.extras?.songBundle }

    val horizontalBottomPaddingValues = windowInsets
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        .asPaddingValues()

    OnGlobalRoute { if (layoutState.expanded) layoutState.collapseSoft() }

    if (mediaItem != null) BottomSheet(
        state = layoutState,
        modifier = modifier.fillMaxSize(),
        onDismiss = {
            binder?.let { onDismiss(it) }
            layoutState.dismissSoft()
        },
        backHandlerEnabled = !menuState.isDisplayed,
        collapsedContent = { innerModifier ->
            val hasBottomNav = LocalHasBottomNavigation.current
            val bottomNavOffset = if (hasBottomNav) 64.dp else 0.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomNavOffset + horizontalBottomPaddingValues.calculateBottomPadding())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.items.collapsedPlayerHeight)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    colorPalette.background2.copy(alpha = 0.95f),
                                    colorPalette.background1.copy(alpha = 0.98f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            colorPalette.accent.copy(alpha = 0.25f),
                            RoundedCornerShape(20.dp)
                        )
                        .then(innerModifier)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                            .let { modifier ->
                                if (horizontalSwipeToClose) modifier.onSwipe(
                                    animateOffset = true,
                                    onSwipeOut = { animationJob ->
                                        binder?.let { onDismiss(it) }
                                        animationJob.join()
                                        layoutState.dismissSoft()
                                    }
                                ) else modifier
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(46.dp)
                        ) {
                            AsyncImage(
                                model = metadata?.artworkUri?.thumbnail(Dimensions.thumbnails.song.px),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorPalette.background0)
                                    .size(46.dp),
                                error = painterResource(R.drawable.musical_notes)
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            AnimatedContent(
                                targetState = metadata?.title?.toString().orEmpty(),
                                label = "",
                                transitionSpec = { fadeIn() togetherWith fadeOut() }
                            ) { text ->
                                BasicText(
                                    text = text,
                                    style = typography.xs.semiBold.copy(color = colorPalette.text),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            AnimatedVisibility(visible = metadata?.artist != null) {
                                AnimatedContent(
                                    targetState = metadata?.artist?.toString().orEmpty(),
                                    label = "",
                                    transitionSpec = { fadeIn() togetherWith fadeOut() }
                                ) { text ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        BasicText(
                                            text = text,
                                            style = typography.xs.semiBold.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        AnimatedVisibility(visible = extras?.explicit == true) {
                                            Image(
                                                painter = painterResource(R.drawable.explicit),
                                                contentDescription = null,
                                                colorFilter = ColorFilter.tint(colorPalette.text),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PressFeedbackIconButton(
                                onClick = { updateLike(if (likedAt == null) System.currentTimeMillis() else null) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    painter = painterResource(if (likedAt == null) R.drawable.heart_outline else R.drawable.heart),
                                    contentDescription = stringResource(if (likedAt == null) R.string.like else R.string.unlike),
                                    tint = if (likedAt != null) colorPalette.favoritesIcon else colorPalette.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (isShowingPrevButtonCollapsed) PressFeedbackIconButton(
                                onClick = { binder?.player?.forceSeekToPrevious() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    painter = painterResource(R.drawable.play_skip_back),
                                    contentDescription = stringResource(R.string.previous_track),
                                    tint = colorPalette.text,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            PressFeedbackIconButton(
                                onClick = {
                                    if (shouldBePlaying) binder?.player?.pause()
                                    else {
                                        if (binder?.player?.playbackState == Player.STATE_IDLE) binder.player.prepare()
                                        binder?.player?.play()
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    painter = painterResource(if (shouldBePlaying) R.drawable.pause else R.drawable.play),
                                    contentDescription = stringResource(if (shouldBePlaying) R.string.pause else R.string.play),
                                    tint = colorPalette.text,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            PressFeedbackIconButton(
                                onClick = { binder?.player?.forceSeekToNext() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    painter = painterResource(R.drawable.play_skip_forward),
                                    contentDescription = stringResource(R.string.next_track),
                                    tint = colorPalette.text,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Sleek 2.5dp progress bar at the bottom of the card
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(colorPalette.text.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(playbackFraction(position, duration))
                                .background(colorPalette.accent)
                        )
                    }
                }
            }
        }
    ) {
        var isShowingStatsForNerds by rememberSaveable { mutableStateOf(false) }
        var isShowingLyricsDialog by rememberSaveable { mutableStateOf(false) }

        if (isShowingLyricsDialog) LyricsDialog(onDismiss = { isShowingLyricsDialog = false })

        val playerBottomSheetState = rememberBottomSheetState(
            dismissedBound = 64.dp + horizontalBottomPaddingValues.calculateBottomPadding(),
            expandedBound = layoutState.expandedBound
        )

        val containerModifier = Modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        colorPalette.background2,
                        colorPalette.accent.copy(alpha = 0.06f),
                        colorPalette.background1,
                        colorPalette.background0
                    )
                )
            )
            .padding(
                windowInsets
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    .asPaddingValues()
            )
            .padding(
                bottom = windowInsets
                    .only(WindowInsetsSides.Bottom)
                    .asPaddingValues()
                    .calculateBottomPadding() + 8.dp
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

        var audioDialogOpen by rememberSaveable { mutableStateOf(false) }
        var boostDialogOpen by rememberSaveable { mutableStateOf(false) }

        val queueBarHeight = 64.dp + horizontalBottomPaddingValues.calculateBottomPadding()

        val controlsContent: @Composable (modifier: Modifier) -> Unit = { innerModifier ->
            Controls(
                media = mediaItem?.toUiMedia(duration),
                binder = binder,
                likedAt = likedAt,
                setLikedAt = updateLike,
                shouldBePlaying = shouldBePlaying,
                position = position,
                modifier = innerModifier
            )
        }

        if (isLandscape) Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = containerModifier
                .padding(top = 28.dp, bottom = queueBarHeight + 4.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(0.66f)
                    .padding(bottom = 12.dp)
            ) {
                thumbnailContent(Modifier.padding(horizontal = 16.dp))
            }

            controlsContent(
                Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxHeight()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            )
        } else Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = containerModifier
                .fillMaxSize()
                .padding(bottom = queueBarHeight + 6.dp)
        ) {
            // Drag Handle Pill
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 2.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colorPalette.textSecondary.copy(alpha = 0.3f))
                    .align(Alignment.CenterHorizontally)
            )

            // Header Bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PressFeedbackIconButton(onClick = layoutState::collapseSoft) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.chevron_down),
                        contentDescription = stringResource(R.string.collapse_player),
                        tint = colorPalette.text
                    )
                }
                androidx.compose.material3.Text(
                    text = stringResource(R.string.now_playing).uppercase(),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = colorPalette.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                PressFeedbackIconButton(
                    onClick = {
                        val activeBinder = binder ?: return@PressFeedbackIconButton
                        mediaItem?.let {
                            menuState.display {
                                PlayerMenu(
                                    onDismiss = menuState::hide,
                                    mediaItem = it,
                                    binder = activeBinder,
                                    onShowSpeedDialog = { audioDialogOpen = true },
                                    onShowNormalizationDialog = {
                                        boostDialogOpen = true
                                    }.takeIf { volumeNormalization }
                                )
                            }
                        }
                    }
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.ellipsis_horizontal),
                        contentDescription = stringResource(R.string.more),
                        tint = colorPalette.text
                    )
                }
            }

            // Hero Artwork
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            ) {
                val artworkSize = maxWidth.coerceAtMost(maxHeight)
                thumbnailContent(
                    Modifier
                        .size(artworkSize)
                        .clip(RoundedCornerShape(22.dp))
                )
            }

            // Controls anchored neatly at the bottom with standard margins
            controlsContent(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        if (audioDialogOpen) SliderDialog(
            onDismiss = { audioDialogOpen = false },
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
            fun submit(state: Float) = transaction {
                mediaItem?.mediaId?.let { mediaId ->
                    Database.setLoudnessBoost(
                        songId = mediaId,
                        loudnessBoost = state.takeUnless { it == 0f }
                    )
                }
            }

            SliderDialog(
                onDismiss = { boostDialogOpen = false },
                title = stringResource(R.string.volume_boost)
            ) {
                SliderDialogBody(
                    provideState = {
                        val state = remember { mutableFloatStateOf(0f) }

                        LaunchedEffect(mediaItem) {
                            mediaItem?.mediaId?.let { mediaId ->
                                Database
                                    .loudnessBoost(mediaId)
                                    .distinctUntilChanged()
                                    .collect { state.floatValue = it ?: 0f }
                            }
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
            layoutState = playerBottomSheetState,
            binder = binder,
            beforeContent = {
                PressFeedbackIconButton(
                    onClick = { isShowingLyrics = !isShowingLyrics },
                    modifier = Modifier.size(40.dp)
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.musical_notes),
                        contentDescription = stringResource(R.string.lyrics),
                        tint = if (isShowingLyrics) colorPalette.accent else colorPalette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            afterContent = {
                PressFeedbackIconButton(
                    onClick = { audioDialogOpen = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.speed),
                        contentDescription = stringResource(R.string.playback_settings),
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
@OptIn(UnstableApi::class)
private fun PlayerMenu(
    binder: PlayerService.Binder,
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onShowSpeedDialog: (() -> Unit)? = null,
    onShowNormalizationDialog: (() -> Unit)? = null
) {
    val launchEqualizer by rememberEqualizerLauncher(audioSessionId = { binder.player.audioSessionId })

    BaseMediaItemMenu(
        mediaItem = mediaItem,
        onStartRadio = {
            binder.stopRadio()
            binder.player.seamlessPlay(mediaItem)
            binder.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
        },
        onGoToEqualizer = launchEqualizer,
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

