package com.k4music.android.ui.screens.searchresult

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.android.k4.searchSaavnSongs
import com.k4music.android.k4.toSongItem
import com.k4music.app.R
import com.k4music.android.preferences.UIStatePreferences
import com.k4music.android.ui.components.LocalMenuState
import com.k4music.android.ui.components.themed.Header
import com.k4music.android.ui.components.themed.NonQueuedMediaItemMenu
import com.k4music.android.ui.components.themed.Scaffold
import com.k4music.android.ui.items.AlbumItem
import com.k4music.android.ui.items.AlbumItemPlaceholder
import com.k4music.android.ui.items.ArtistItem
import com.k4music.android.ui.items.ArtistItemPlaceholder
import com.k4music.android.ui.items.PlaylistItem
import com.k4music.android.ui.items.PlaylistItemPlaceholder
import com.k4music.android.ui.items.SongItem
import com.k4music.android.ui.items.SongItemPlaceholder
import com.k4music.android.ui.items.VideoItem
import com.k4music.android.ui.items.VideoItemPlaceholder
import com.k4music.android.ui.screens.GlobalRoutes
import com.k4music.android.ui.screens.Route
import com.k4music.android.ui.screens.albumRoute
import com.k4music.android.ui.screens.artistRoute
import com.k4music.android.ui.screens.playlistRoute
import com.k4music.android.utils.asMediaItem
import com.k4music.android.utils.forcePlay
import com.k4music.android.utils.playingSong
import com.k4music.compose.persist.LocalPersistMap
import com.k4music.compose.persist.PersistMapCleanup
import com.k4music.compose.routing.RouteHandler
import com.k4music.core.ui.Dimensions
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.ContinuationBody
import com.k4music.providers.innertube.models.bodies.SearchBody
import com.k4music.providers.innertube.requests.searchPage
import com.k4music.providers.innertube.utils.from

@Route
@Composable
fun SearchResultScreen(query: String, onSearchAgain: () -> Unit) {
    val persistMap = LocalPersistMap.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup(prefix = "searchResults/$query/")

    val [currentMediaId, playing] = playingSong(binder)

    RouteHandler {
        GlobalRoutes()

        Content {
            val headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit = {
                Header(
                    title = query,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            persistMap?.clean("searchResults/$query/")
                            onSearchAgain()
                        }
                    }
                )
            }

            Scaffold(
                key = "searchresult",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = UIStatePreferences.searchResultScreenTabIndex,
                onTabChange = { UIStatePreferences.searchResultScreenTabIndex = it },
                tabColumnContent = {
                    tab(0, R.string.songs, R.drawable.musical_notes)
                    tab(1, R.string.albums, R.drawable.disc)
                    tab(2, R.string.artists, R.drawable.person)
                    tab(3, R.string.videos, R.drawable.film)
                    tab(4, R.string.playlists, R.drawable.playlist)
                }
            ) { tabIndex ->
                saveableStateHolder.SaveableStateProvider(tabIndex) {
                    when (tabIndex) {
                        0 -> ItemsPage(
                            tag = "searchResults/$query/songs",
                            provider = { continuation ->
                                val yt = if (continuation == null) Innertube.searchPage(
                                    body = SearchBody(
                                        query = query,
                                        params = Innertube.SearchFilter.Song.value
                                    ),
                                    fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
                                ) else Innertube.searchPage(
                                    body = ContinuationBody(continuation = continuation),
                                    fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
                                )
                                // YouTube Music first; when it comes back empty (blocked,
                                // region, no match), fill the Songs tab from JioSaavn.
                                val firstPage = yt?.getOrNull()?.items
                                if (continuation == null && firstPage.isNullOrEmpty()) {
                                    searchSaavnSongs(query)
                                        .map { it.toSongItem() }
                                        .takeIf { it.isNotEmpty() }
                                        ?.let { Result.success(Innertube.ItemsPage(it, null)) }
                                        ?: yt
                                } else yt
                            },
                            emptyItemsText = stringResource(R.string.no_search_results),
                            header = headerContent,
                            itemContent = { song ->
                                SongItem(
                                    song = song,
                                    thumbnailSize = Dimensions.thumbnails.song,
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
                                            binder?.player?.forcePlay(song.asMediaItem)
                                            binder?.setupRadio(song.info?.endpoint)
                                        }
                                    ),
                                    isPlaying = playing && currentMediaId == song.key
                                )
                            },
                            itemPlaceholderContent = {
                                SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                            }
                        )

                        1 -> ItemsPage(
                            tag = "searchResults/$query/albums",
                            provider = { continuation ->
                                if (continuation == null) {
                                    Innertube.searchPage(
                                        body = SearchBody(
                                            query = query,
                                            params = Innertube.SearchFilter.Album.value
                                        ),
                                        fromMusicShelfRendererContent = Innertube.AlbumItem::from
                                    )
                                } else {
                                    Innertube.searchPage(
                                        body = ContinuationBody(continuation = continuation),
                                        fromMusicShelfRendererContent = Innertube.AlbumItem::from
                                    )
                                }
                            },
                            emptyItemsText = stringResource(R.string.no_search_results),
                            header = headerContent,
                            itemContent = { album ->
                                AlbumItem(
                                    album = album,
                                    thumbnailSize = Dimensions.thumbnails.album,
                                    modifier = Modifier.clickable(onClick = { albumRoute(album.key) })
                                )
                            },
                            itemPlaceholderContent = {
                                AlbumItemPlaceholder(thumbnailSize = Dimensions.thumbnails.album)
                            }
                        )

                        2 -> ItemsPage(
                            tag = "searchResults/$query/artists",
                            provider = { continuation ->
                                if (continuation == null) {
                                    Innertube.searchPage(
                                        body = SearchBody(
                                            query = query,
                                            params = Innertube.SearchFilter.Artist.value
                                        ),
                                        fromMusicShelfRendererContent = Innertube.ArtistItem::from
                                    )
                                } else {
                                    Innertube.searchPage(
                                        body = ContinuationBody(continuation = continuation),
                                        fromMusicShelfRendererContent = Innertube.ArtistItem::from
                                    )
                                }
                            },
                            emptyItemsText = stringResource(R.string.no_search_results),
                            header = headerContent,
                            itemContent = { artist ->
                                ArtistItem(
                                    artist = artist,
                                    thumbnailSize = 64.dp,
                                    modifier = Modifier
                                        .clickable(onClick = { artistRoute(artist.key) })
                                )
                            },
                            itemPlaceholderContent = {
                                ArtistItemPlaceholder(thumbnailSize = 64.dp)
                            }
                        )

                        3 -> ItemsPage(
                            tag = "searchResults/$query/videos",
                            provider = { continuation ->
                                if (continuation == null) Innertube.searchPage(
                                    body = SearchBody(
                                        query = query,
                                        params = Innertube.SearchFilter.Video.value
                                    ),
                                    fromMusicShelfRendererContent = Innertube.VideoItem::from
                                ) else Innertube.searchPage(
                                    body = ContinuationBody(continuation = continuation),
                                    fromMusicShelfRendererContent = Innertube.VideoItem::from
                                )
                            },
                            emptyItemsText = stringResource(R.string.no_search_results),
                            header = headerContent,
                            itemContent = { video ->
                                VideoItem(
                                    video = video,
                                    thumbnailWidth = 128.dp,
                                    thumbnailHeight = 72.dp,
                                    modifier = Modifier.combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                NonQueuedMediaItemMenu(
                                                    mediaItem = video.asMediaItem,
                                                    onDismiss = menuState::hide
                                                )
                                            }
                                        },
                                        onClick = {
                                            binder?.stopRadio()
                                            binder?.player?.forcePlay(video.asMediaItem)
                                            binder?.setupRadio(video.info?.endpoint)
                                        }
                                    )
                                )
                            },
                            itemPlaceholderContent = {
                                VideoItemPlaceholder(
                                    thumbnailWidth = 128.dp,
                                    thumbnailHeight = 72.dp
                                )
                            }
                        )

                        4 -> ItemsPage(
                            tag = "searchResults/$query/playlists",
                            provider = { continuation ->
                                if (continuation == null) Innertube.searchPage(
                                    body = SearchBody(
                                        query = query,
                                        params = Innertube.SearchFilter.CommunityPlaylist.value
                                    ),
                                    fromMusicShelfRendererContent = Innertube.PlaylistItem::from
                                ) else Innertube.searchPage(
                                    body = ContinuationBody(continuation = continuation),
                                    fromMusicShelfRendererContent = Innertube.PlaylistItem::from
                                )
                            },
                            emptyItemsText = stringResource(R.string.no_search_results),
                            header = headerContent,
                            itemContent = { playlist ->
                                PlaylistItem(
                                    playlist = playlist,
                                    thumbnailSize = Dimensions.thumbnails.playlist,
                                    modifier = Modifier.clickable {
                                        playlistRoute(playlist.key, null, null, false)
                                    }
                                )
                            },
                            itemPlaceholderContent = {
                                PlaylistItemPlaceholder(thumbnailSize = Dimensions.thumbnails.playlist)
                            }
                        )
                    }
                }
            }
        }
    }
}
