package com.k4music.android.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.k4music.android.Database
import com.k4music.android.LocalPlayerAwareWindowInsets
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.app.R
import com.k4music.android.models.Song
import com.k4music.android.ui.components.LocalMenuState
import com.k4music.android.ui.components.ShimmerHost
import com.k4music.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import com.k4music.android.ui.components.themed.LayoutWithAdaptiveThumbnail
import com.k4music.android.ui.components.themed.NonQueuedMediaItemMenu
import com.k4music.android.ui.components.themed.SecondaryTextButton
import com.k4music.android.ui.items.SongItem
import com.k4music.android.ui.items.SongItemPlaceholder
import com.k4music.android.utils.asMediaItem
import com.k4music.android.utils.enqueue
import com.k4music.android.utils.forcePlayAtIndex
import com.k4music.android.utils.forcePlayFromBeginning
import com.k4music.android.utils.playingSong
import com.k4music.compose.persist.persist
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.utils.isLandscape

@Composable
fun ArtistLocalSongs(
    browseId: String,
    headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    thumbnailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current
    val (colorPalette) = LocalAppearance.current
    val menuState = LocalMenuState.current

    var songs by persist<List<Song>?>("artist/$browseId/localSongs")

    LaunchedEffect(Unit) {
        Database.artistSongs(browseId).collect { songs = it }
    }

    val lazyListState = rememberLazyListState()

    val [currentMediaId, playing] = playingSong(binder)

    LayoutWithAdaptiveThumbnail(
        thumbnailContent = thumbnailContent,
        modifier = modifier
    ) {
        Box {
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
                modifier = Modifier
                    .background(colorPalette.background0)
                    .fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = 0
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        headerContent {
                            SecondaryTextButton(
                                text = stringResource(R.string.enqueue),
                                enabled = !songs.isNullOrEmpty(),
                                onClick = {
                                    binder?.player?.enqueue(songs.orEmpty().map(Song::asMediaItem))
                                }
                            )
                        }

                        if (!isLandscape) thumbnailContent()
                    }
                }

                songs?.let { songs ->
                    itemsIndexed(
                        items = songs,
                        key = { _, song -> song.id }
                    ) { index, song ->
                        SongItem(
                            modifier = Modifier.combinedClickable(
                                onLongClick = {
                                    menuState.display {
                                        NonQueuedMediaItemMenu(
                                            onDismiss = menuState::hide,
                                            mediaItem = song.asMediaItem
                                        )
                                    }
                                },
                                onClick = {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayAtIndex(
                                        items = songs.map(Song::asMediaItem),
                                        index = index
                                    )
                                }
                            ),
                            song = song,
                            thumbnailSize = Dimensions.thumbnails.song,
                            isPlaying = playing && currentMediaId == song.id
                        )
                    }
                } ?: item(key = "loading") {
                    ShimmerHost {
                        repeat(4) {
                            SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                        }
                    }
                }
            }

            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                icon = R.drawable.shuffle,
                onClick = {
                    songs?.let { songs ->
                        if (songs.isNotEmpty()) {
                            binder?.stopRadio()
                            binder?.player?.forcePlayFromBeginning(
                                songs.shuffled().map(Song::asMediaItem)
                            )
                        }
                    }
                }
            )
        }
    }
}
