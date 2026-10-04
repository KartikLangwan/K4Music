package com.k4music.android.ui.screens.builtinplaylist

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.k4music.android.Database
import com.k4music.android.LocalPlayerAwareWindowInsets
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.app.R
import com.k4music.android.models.Song
import com.k4music.android.preferences.DataPreferences
import com.k4music.android.ui.components.LocalMenuState
import com.k4music.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import com.k4music.android.ui.components.themed.Header
import com.k4music.android.ui.components.themed.InHistoryMediaItemMenu
import com.k4music.android.ui.components.themed.NonQueuedMediaItemMenu
import com.k4music.android.ui.components.themed.SecondaryTextButton
import com.k4music.android.ui.components.themed.ValueSelectorDialog
import com.k4music.android.ui.items.SongItem
import com.k4music.android.ui.screens.home.HeaderSongSortBy
import com.k4music.android.utils.PlaylistDownloadIcon
import com.k4music.android.utils.asMediaItem
import com.k4music.android.utils.enqueue
import com.k4music.android.utils.cachedSongsFlow
import kotlinx.coroutines.flow.flowOf
import com.k4music.android.utils.forcePlayAtIndex
import com.k4music.android.utils.forcePlayFromBeginning
import com.k4music.android.utils.playingSong
import com.k4music.compose.persist.persistList
import com.k4music.core.data.enums.BuiltInPlaylist
import com.k4music.core.data.enums.SongSortBy
import com.k4music.core.data.enums.SortOrder
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.utils.enumSaver
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun BuiltInPlaylistSongs(
    builtInPlaylist: BuiltInPlaylist,
    modifier: Modifier = Modifier
) = with(DataPreferences) {
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    var songs by persistList<Song>("${builtInPlaylist.name}/songs")

    var sortBy by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(SongSortBy.DateAdded) }
    var sortOrder by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(SortOrder.Descending) }

    LaunchedEffect(binder, sortBy, sortOrder, builtInPlaylist) {
        when (builtInPlaylist) {
            BuiltInPlaylist.Favorites -> Database.favorites(
                sortBy = sortBy,
                sortOrder = sortOrder
            )

            BuiltInPlaylist.Offline ->
                Database
                    .songsWithContentLength(
                        sortBy = sortBy,
                        sortOrder = sortOrder
                    )
                    .flatMapLatest { songs ->
                        binder?.cachedSongsFlow(songs) ?: flowOf(emptyList())
                    }

            BuiltInPlaylist.Top -> combine(
                flow = topListPeriodProperty.stateFlow,
                flow2 = topListLengthProperty.stateFlow
            ) { period, length -> period to length }.flatMapLatest { [period, length] ->
                if (period.duration == null) Database
                    .songsByPlayTimeDesc(limit = length)
                    .distinctUntilChanged()
                    .cancellable()
                else Database
                    .trending(
                        limit = length,
                        period = period.duration.inWholeMilliseconds
                    )
                    .distinctUntilChanged()
                    .cancellable()
            }

            BuiltInPlaylist.History -> Database.history()
        }.collect { songs = it.toImmutableList() }
    }

    val lazyListState = rememberLazyListState()

    val [currentMediaId, playing] = playingSong(binder)

    Box(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {
                Header(
                    title = when (builtInPlaylist) {
                        BuiltInPlaylist.Favorites -> stringResource(R.string.favorites)

                        BuiltInPlaylist.Offline -> stringResource(R.string.offline)

                        BuiltInPlaylist.Top -> stringResource(
                            R.string.format_my_top_playlist,
                            topListLength
                        )

                        BuiltInPlaylist.History -> stringResource(R.string.history)
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    SecondaryTextButton(
                        text = stringResource(R.string.enqueue),
                        enabled = songs.isNotEmpty(),
                        onClick = {
                            binder?.player?.enqueue(songs.map(Song::asMediaItem))
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (builtInPlaylist != BuiltInPlaylist.Offline) PlaylistDownloadIcon(
                        songs = songs.map(Song::asMediaItem).toImmutableList()
                    )

                    if (builtInPlaylist.sortable) HeaderSongSortBy(
                        sortBy = sortBy,
                        setSortBy = { sortBy = it },
                        sortOrder = sortOrder,
                        setSortOrder = { sortOrder = it }
                    )

                    if (builtInPlaylist == BuiltInPlaylist.Top) {
                        var dialogShowing by rememberSaveable { mutableStateOf(false) }

                        SecondaryTextButton(
                            text = topListPeriod.displayName(),
                            onClick = { dialogShowing = true }
                        )

                        if (dialogShowing) ValueSelectorDialog(
                            onDismiss = { dialogShowing = false },
                            title = stringResource(
                                R.string.format_view_top_of_header,
                                topListLength
                            ),
                            selectedValue = topListPeriod,
                            values = DataPreferences.TopListPeriod.entries.toImmutableList(),
                            onValueSelect = { topListPeriod = it },
                            valueText = { it.displayName() }
                        )
                    }
                }
            }

            if (songs.isEmpty()) item(key = "empty-library") {
                com.k4music.android.ui.components.MusicEmptyState(
                    title = stringResource(if (builtInPlaylist == BuiltInPlaylist.Offline) R.string.offline_empty_title else R.string.library_empty_title),
                    message = stringResource(if (builtInPlaylist == BuiltInPlaylist.Offline) R.string.offline_empty_description else R.string.library_empty_description)
                )
            }
            itemsIndexed(
                items = songs,
                key = { _, song -> song.id },
                contentType = { _, song -> song }
            ) { index, song ->
                SongItem(
                    modifier = Modifier
                        .combinedClickable(
                            onLongClick = {
                                menuState.display {
                                    when (builtInPlaylist) {
                                        BuiltInPlaylist.Offline -> InHistoryMediaItemMenu(
                                            song = song,
                                            onDismiss = menuState::hide
                                        )

                                        BuiltInPlaylist.Favorites,
                                        BuiltInPlaylist.Top,
                                        BuiltInPlaylist.History -> NonQueuedMediaItemMenu(
                                            mediaItem = song.asMediaItem,
                                            onDismiss = menuState::hide
                                        )
                                    }
                                }
                            },
                            onClick = {
                                binder?.stopRadio()
                                binder?.player?.forcePlayAtIndex(
                                    items = songs.map(Song::asMediaItem),
                                    index = index
                                )
                            }
                        )
                        .animateItem(),
                    song = song,
                    index = if (builtInPlaylist == BuiltInPlaylist.Top) index else null,
                    thumbnailSize = Dimensions.thumbnails.song,
                    isPlaying = playing && currentMediaId == song.id
                )
            }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = {
                if (songs.isEmpty()) return@FloatingActionsContainerWithScrollToTop
                binder?.stopRadio()
                binder?.player?.forcePlayFromBeginning(
                    songs.shuffled().map(Song::asMediaItem)
                )
            }
        )
    }
}
