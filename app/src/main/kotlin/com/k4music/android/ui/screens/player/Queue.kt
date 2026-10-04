package com.k4music.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.k4music.android.Database
import com.k4music.app.R
import com.k4music.android.models.Playlist
import com.k4music.android.models.SongPlaylistMap
import com.k4music.android.preferences.AppearancePreferences
import com.k4music.android.preferences.PlayerPreferences
import com.k4music.android.service.PlayerService
import com.k4music.android.transaction
import com.k4music.android.ui.components.BottomSheet
import com.k4music.android.ui.components.BottomSheetState
import com.k4music.android.ui.components.GlassPanel
import com.k4music.android.ui.components.LocalMenuState
import com.k4music.android.ui.components.MusicBars
import com.k4music.android.ui.components.themed.BaseMediaItemMenu
import com.k4music.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import com.k4music.android.ui.components.themed.HorizontalDivider
import com.k4music.android.ui.components.themed.IconButton
import com.k4music.android.ui.components.themed.Menu
import com.k4music.android.ui.components.themed.MenuEntry
import com.k4music.android.ui.components.themed.QueuedMediaItemMenu
import com.k4music.android.ui.components.themed.ReorderHandle
import com.k4music.android.ui.components.themed.SecondaryTextButton
import com.k4music.android.ui.components.themed.TextFieldDialog
import com.k4music.android.ui.components.themed.TextToggle
import com.k4music.android.ui.items.SongItem
import com.k4music.android.ui.items.SongItemPlaceholder
import com.k4music.android.ui.modifiers.swipeToClose
import com.k4music.android.utils.DisposableListener
import com.k4music.android.utils.addNext
import com.k4music.android.utils.asMediaItem
import com.k4music.android.utils.enqueue
import com.k4music.android.utils.medium
import com.k4music.android.utils.networkResult
import com.k4music.android.ui.components.MusicEmptyState
import com.k4music.android.utils.onFirst
import com.k4music.android.utils.semiBold
import com.k4music.android.utils.shouldBePlaying
import com.k4music.android.utils.playOrder
import com.k4music.android.utils.smoothScrollToTop
import com.k4music.android.utils.windows
import com.k4music.compose.persist.persist
import com.k4music.compose.reordering.animateItemPlacement
import com.k4music.compose.reordering.draggedItem
import com.k4music.compose.reordering.rememberReorderingState
import com.k4music.core.data.enums.PlaylistSortBy
import com.k4music.core.data.enums.SortOrder
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.onOverlay
import com.k4music.core.ui.utils.roundedShape
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.NextBody
import com.k4music.providers.innertube.requests.nextPage
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun Queue(
    layoutState: BottomSheetState,
    binder: PlayerService.Binder,
    beforeContent: @Composable RowScope.() -> Unit,
    afterContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp
    ),
    scrollConnection: NestedScrollConnection = remember(layoutState::preUpPostDownNestedScrollConnection),
    windowInsets: WindowInsets = WindowInsets.systemBars
) {
    val [colorPalette, typography, _, thumbnailShape] = LocalAppearance.current
    val menuState = LocalMenuState.current

    val horizontalBottomPaddingValues = windowInsets
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        .asPaddingValues()

    var suggestions by persist<Result<List<MediaItem>?>?>(tag = "queue/suggestions")

    var mediaItemIndex by remember(binder) {
        mutableIntStateOf(if (binder.player.mediaItemCount == 0) -1 else binder.player.currentMediaItemIndex)
    }

    var timeline by remember(binder) { mutableStateOf(binder.player.currentTimeline) }
    var shuffled by remember(binder) { mutableStateOf(binder.player.shuffleModeEnabled) }
    // Shown in the order songs will actually play; `order[i]` is the window index of row i.
    val order = remember(timeline, shuffled) { timeline.playOrder(shuffled) }
    val allWindows = remember(timeline) { timeline.windows }
    val windows = remember(order, allWindows) { order.map { allWindows[it] } }
    var shouldBePlaying by remember(binder) { mutableStateOf(binder.player.shouldBePlaying) }
    val activeMediaId = allWindows.getOrNull(mediaItemIndex)?.mediaItem?.mediaId
    var suggestionRetry by remember { mutableIntStateOf(0) }
    val queueInteraction = remember { MutableInteractionSource() }
    val queuePressed by queueInteraction.collectIsPressedAsState()

    val lazyListState = rememberLazyListState()
    val reorderingState = rememberReorderingState(
        lazyListState = lazyListState,
        key = windows,
        onDragEnd = binder.player::moveMediaItem
    )

    val visibleSuggestions by remember(binder) {
        derivedStateOf {
            suggestions
                ?.getOrNull()
                .orEmpty()
                .distinctBy { it.mediaId }
                .filter { windows.none { window -> window.mediaItem.mediaId == it.mediaId } }
        }
    }

    val shouldLoadSuggestions by remember {
        derivedStateOf {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }
    }

    LaunchedEffect(activeMediaId, suggestionRetry) {
        suggestions = null
    }

    LaunchedEffect(activeMediaId, shouldLoadSuggestions, suggestionRetry) {
        val id = activeMediaId ?: return@LaunchedEffect
        if (!shouldLoadSuggestions || suggestions != null) return@LaunchedEffect
        // Local and Saavn IDs are not valid YouTube recommendation IDs.
        suggestions = if (id.startsWith("local:") || id.startsWith("saavn:")) {
            Result.success(emptyList())
        } else networkResult {
            Innertube.nextPage(NextBody(videoId = id))?.map { page ->
                page.itemsPage?.items?.map { it.asMediaItem }
            }
        }
    }

    binder.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItemIndex =
                    if (binder.player.mediaItemCount == 0) -1 else binder.player.currentMediaItemIndex
            }

            override fun onTimelineChanged(newTimeline: Timeline, reason: Int) {
                timeline = newTimeline
                mediaItemIndex =
                    if (binder.player.mediaItemCount == 0) -1 else binder.player.currentMediaItemIndex
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldBePlaying = binder.player.shouldBePlaying
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                shuffled = shuffleModeEnabled
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                shouldBePlaying = binder.player.shouldBePlaying
            }
        }
    }

    BottomSheet(
        state = layoutState,
        modifier = modifier.fillMaxSize(),
        collapsedContent = { innerModifier ->
            GlassPanel(
                shape = shape,
                elevation = 8.dp,
                backgroundAlpha = 0.76f,
                borderAlpha = 0.16f,
                modifier = Modifier
                    .fillMaxSize()
                    .then(innerModifier)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontalBottomPaddingValues)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    beforeContent()

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (queuePressed) colorPalette.accent.copy(alpha = 0.30f) else colorPalette.text.copy(alpha = 0.08f))
                            .clickable(interactionSource = queueInteraction, indication = ripple(), onClick = { layoutState.expandSoft() })
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.playlist),
                                contentDescription = stringResource(R.string.up_next),
                                colorFilter = ColorFilter.tint(colorPalette.accent),
                                modifier = Modifier.size(16.dp)
                            )
                            BasicText(
                                text = stringResource(R.string.up_next),
                                style = typography.xs.semiBold
                            )
                            BasicText(text = "·", style = typography.xs.semiBold)
                            BasicText(
                                text = pluralStringResource(R.plurals.song_count_plural, windows.size, windows.size),
                                style = typography.xxs.medium
                            )
                            Image(
                                painter = painterResource(R.drawable.chevron_up),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    afterContent()
                }
            }
        }
    ) {
        val playingRow = order.indexOf(mediaItemIndex)
        val musicBarsTransition = updateTransition(
            targetState = if (reorderingState.isDragging) -1 else playingRow,
            label = ""
        )

        LaunchedEffect(Unit) {
            lazyListState.scrollToItem(playingRow.coerceAtLeast(0))
        }

        Column {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(colorPalette.background1)
                    .weight(1f)
            ) {
                LookaheadScope {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = windowInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .asPaddingValues(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.nestedScroll(scrollConnection)
                    ) {
                        itemsIndexed(
                            items = windows,
                            key = { _, window -> window.uid.hashCode() },
                            contentType = { _, _ -> ContentType.Window }
                        ) { i, window ->
                            val windowIndex = order.getOrElse(i) { i }
                            val isPlayingThisMediaItem = mediaItemIndex == windowIndex

                            SongItem(
                                song = window.mediaItem,
                                thumbnailSize = Dimensions.thumbnails.song,
                                onThumbnailContent = {
                                    musicBarsTransition.AnimatedVisibility(
                                        visible = { it == i },
                                        enter = fadeIn(tween(800)),
                                        exit = fadeOut(tween(800))
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .background(
                                                    color = Color.Black.copy(alpha = 0.25f),
                                                    shape = thumbnailShape
                                                )
                                                .size(Dimensions.thumbnails.song)
                                        ) {
                                            if (shouldBePlaying) MusicBars(
                                                color = colorPalette.onOverlay,
                                                modifier = Modifier.height(24.dp)
                                            ) else Image(
                                                painter = painterResource(R.drawable.play),
                                                contentDescription = null,
                                                colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                },
                                trailingContent = {
                                    // Display order is the shuffle order while shuffling, so
                                    // dragging would not mean what it shows.
                                    if (!shuffled) ReorderHandle(
                                        reorderingState = reorderingState,
                                        index = i
                                    )
                                },
                                modifier = Modifier
                                    .combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                QueuedMediaItemMenu(
                                                    mediaItem = window.mediaItem,
                                                    indexInQueue = if (isPlayingThisMediaItem) null
                                                    else windowIndex,
                                                    onDismiss = menuState::hide
                                                )
                                            }
                                        },
                                        onClick = {
                                            if (isPlayingThisMediaItem) {
                                                if (shouldBePlaying) binder.player.pause() else binder.player.play()
                                            } else {
                                                binder.player.seekToDefaultPosition(windowIndex)
                                                // After a failure the player is idle and must be prepared again.
                                                if (binder.player.playbackState == Player.STATE_IDLE) binder.player.prepare()
                                                binder.player.playWhenReady = true
                                            }
                                        }
                                    )
                                    .animateItemPlacement(reorderingState)
                                    .draggedItem(
                                        reorderingState = reorderingState,
                                        index = i
                                    )
                                    .background(colorPalette.background1)
                                    .let {
                                        if (PlayerPreferences.horizontalSwipeToRemoveItem && !isPlayingThisMediaItem)
                                            it.swipeToClose(
                                                key = windows,
                                                delay = 100.milliseconds,
                                                requireUnconsumed = true
                                            ) {
                                                if (windowIndex in 0 until binder.player.mediaItemCount) binder.player.removeMediaItem(windowIndex)
                                            }
                                        else it
                                    },
                                clip = !reorderingState.isDragging,
                                hideExplicit = !isPlayingThisMediaItem && AppearancePreferences.hideExplicit
                            )
                        }

                        item(
                            key = "divider",
                            contentType = { ContentType.Divider }
                        ) {
                            if (visibleSuggestions.isNotEmpty()) HorizontalDivider(
                                modifier = Modifier.padding(start = 28.dp + Dimensions.thumbnails.song)
                            )
                        }

                        items(
                            items = visibleSuggestions,
                            key = { "suggestion_${it.mediaId}" },
                            contentType = { ContentType.Suggestion }
                        ) {
                            SongItem(
                                song = it,
                                thumbnailSize = Dimensions.thumbnails.song,
                                modifier = Modifier.clickable {
                                    menuState.display {
                                        BaseMediaItemMenu(
                                            onDismiss = { menuState.hide() },
                                            mediaItem = it,
                                            onEnqueue = { binder.player.enqueue(it) },
                                            onPlayNext = { binder.player.addNext(it) }
                                        )
                                    }
                                },
                                trailingContent = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(
                                            space = 12.dp,
                                            alignment = Alignment.End
                                        )
                                    ) {
                                        IconButton(
                                            icon = R.drawable.play_skip_forward,
                                            color = colorPalette.text,
                                            onClick = {
                                                binder.player.addNext(it)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        IconButton(
                                            icon = R.drawable.enqueue,
                                            color = colorPalette.text,
                                            onClick = {
                                                binder.player.enqueue(it)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )
                        }

                        item(
                            key = "loading",
                            contentType = { ContentType.Placeholder }
                        ) {
                            if (activeMediaId != null && (binder.isLoadingRadio || suggestions == null))
                                Column(modifier = Modifier.shimmer()) {
                                    repeat(3) { index ->
                                        SongItemPlaceholder(
                                            thumbnailSize = Dimensions.thumbnails.song,
                                            modifier = Modifier
                                                .alpha(1f - index * 0.125f)
                                                .fillMaxWidth()
                                        )
                                    }
                                }
                            else if (suggestions?.isFailure == true) MusicEmptyState(
                                title = stringResource(R.string.music_unavailable),
                                message = stringResource(R.string.connection_retry_message),
                                action = stringResource(R.string.retry),
                                onAction = { suggestionRetry++ }
                            )
                        }
                    }
                }

                FloatingActionsContainerWithScrollToTop(
                    lazyListState = lazyListState,
                    visible = !reorderingState.isDragging,
                    insets = windowInsets.only(WindowInsetsSides.Horizontal)
                )
            }

            Row(
                modifier = Modifier
                    .clickable(onClick = layoutState::collapseSoft)
                    .background(colorPalette.background2.copy(alpha = 0.82f))
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(horizontalBottomPaddingValues)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val repeat = PlayerPreferences.repeatMode
                TextToggle(
                    state = repeat != PlayerPreferences.RepeatMode.Off,
                    toggleState = { PlayerPreferences.repeatMode = repeat.next },
                    name = stringResource(
                        when (repeat) {
                            PlayerPreferences.RepeatMode.Off -> R.string.repeat_off
                            PlayerPreferences.RepeatMode.All -> R.string.repeat_all
                            PlayerPreferences.RepeatMode.One -> R.string.repeat_one
                        }
                    )
                )

                Spacer(modifier = Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.chevron_down),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.text),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.weight(1f))

                BasicText(
                    text = pluralStringResource(
                        id = R.plurals.song_count_plural,
                        count = windows.size,
                        windows.size
                    ),
                    style = typography.xxs.medium,
                    modifier = Modifier
                        .clip(16.dp.roundedShape)
                        .clickable {
                            fun addToPlaylist(playlist: Playlist, index: Int) = transaction {
                                val playlistId = Database
                                    .insert(playlist)
                                    .takeIf { it != -1L } ?: playlist.id

                                windows.forEachIndexed { i, window ->
                                    val mediaItem = window.mediaItem

                                    Database.insert(mediaItem)
                                    Database.insert(
                                        SongPlaylistMap(
                                            songId = mediaItem.mediaId,
                                            playlistId = playlistId,
                                            position = index + i
                                        )
                                    )
                                }
                            }

                            menuState.display {
                                var isCreatingNewPlaylist by rememberSaveable { mutableStateOf(false) }

                                val playlistPreviews by remember {
                                    Database
                                        .playlistPreviews(
                                            sortBy = PlaylistSortBy.DateAdded,
                                            sortOrder = SortOrder.Descending
                                        )
                                        .onFirst { isCreatingNewPlaylist = it.isEmpty() }
                                }.collectAsState(initial = null, context = Dispatchers.IO)

                                if (isCreatingNewPlaylist) TextFieldDialog(
                                    hintText = stringResource(R.string.enter_playlist_name_prompt),
                                    onDismiss = { isCreatingNewPlaylist = false },
                                    onAccept = { text ->
                                        menuState.hide()
                                        addToPlaylist(Playlist(name = text), 0)
                                    }
                                )

                                Menu {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                            .fillMaxWidth()
                                    ) {
                                        BasicText(
                                            text = stringResource(R.string.add_queue_to_playlist),
                                            style = typography.m.semiBold,
                                            overflow = TextOverflow.Ellipsis,
                                            maxLines = 2,
                                            modifier = Modifier.weight(weight = 2f, fill = false)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        SecondaryTextButton(
                                            text = stringResource(R.string.new_playlist),
                                            onClick = { isCreatingNewPlaylist = true },
                                            alternative = true,
                                            modifier = Modifier.weight(weight = 1f, fill = false)
                                        )
                                    }

                                    if (playlistPreviews?.isEmpty() == true)
                                        Spacer(modifier = Modifier.height(160.dp))

                                    playlistPreviews?.forEach { playlistPreview ->
                                        MenuEntry(
                                            icon = R.drawable.playlist,
                                            text = playlistPreview.playlist.name,
                                            secondaryText = pluralStringResource(
                                                id = R.plurals.song_count_plural,
                                                count = playlistPreview.songCount,
                                                playlistPreview.songCount
                                            ),
                                            onClick = {
                                                menuState.hide()
                                                addToPlaylist(
                                                    playlistPreview.playlist,
                                                    playlistPreview.songCount
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        .background(colorPalette.background1)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@JvmInline
private value class ContentType private constructor(val value: Int) {
    companion object {
        val Window = ContentType(value = 0)
        val Divider = ContentType(value = 1)
        val Suggestion = ContentType(value = 2)
        val Placeholder = ContentType(value = 3)
    }
}
