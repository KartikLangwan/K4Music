package com.k4music.android.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.k4music.app.R
import com.k4music.android.models.toUiMood
import com.k4music.android.preferences.UIStatePreferences
import com.k4music.android.ui.components.themed.Scaffold
import com.k4music.android.ui.screens.GlobalRoutes
import com.k4music.android.ui.screens.Route
import com.k4music.android.ui.screens.albumRoute
import com.k4music.android.ui.screens.artistRoute
import com.k4music.android.ui.screens.builtInPlaylistRoute
import com.k4music.android.ui.screens.builtinplaylist.BuiltInPlaylistScreen
import com.k4music.android.ui.screens.builtinplaylist.BuiltInPlaylistSongs
import com.k4music.core.data.enums.BuiltInPlaylist
import com.k4music.android.ui.screens.localPlaylistRoute
import com.k4music.android.ui.screens.localplaylist.LocalPlaylistScreen
import com.k4music.android.ui.screens.mood.MoodScreen
import com.k4music.android.ui.screens.mood.MoreAlbumsScreen
import com.k4music.android.ui.screens.mood.MoreMoodsScreen
import com.k4music.android.ui.screens.moodRoute
import com.k4music.android.ui.screens.pipedPlaylistRoute
import com.k4music.android.ui.screens.playlistRoute
import com.k4music.android.ui.screens.searchRoute
import com.k4music.android.ui.screens.searchResultRoute
import com.k4music.android.ui.screens.settingsRoute
import com.k4music.compose.persist.PersistMapCleanup
import com.k4music.compose.routing.Route0
import com.k4music.compose.routing.RouteHandler

private val moreMoodsRoute = Route0("moreMoodsRoute")
private val moreAlbumsRoute = Route0("moreAlbumsRoute")

@Route
@Composable
fun HomeScreen(
    rootRoute: com.k4music.compose.routing.Route?,
    setRootRoute: (com.k4music.compose.routing.Route?) -> Unit,
    rootArgs: Array<Any?>
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup("home/")

    RouteHandler(child = rootRoute, setChild = setRootRoute, parameters = rootArgs) {
        GlobalRoutes()

        localPlaylistRoute { playlistId ->
            LocalPlaylistScreen(playlistId = playlistId)
        }

        builtInPlaylistRoute { builtInPlaylist ->
            BuiltInPlaylistScreen(builtInPlaylist = builtInPlaylist)
        }

        moodRoute { mood ->
            MoodScreen(mood = mood)
        }

        moreMoodsRoute {
            MoreMoodsScreen()
        }

        moreAlbumsRoute {
            MoreAlbumsScreen()
        }

        Content {
            Scaffold(
                key = "home",
                topIconButtonId = R.drawable.settings,
                onTopIconButtonClick = { settingsRoute() },
                tabIndex = UIStatePreferences.homeScreenTabIndex,
                onTabChange = { UIStatePreferences.homeScreenTabIndex = it },
                tabColumnContent = {
                    tab(0, R.string.quick_picks, R.drawable.sparkles)
                    tab(1, R.string.discover, R.drawable.globe)
                    tab(2, R.string.songs, R.drawable.musical_notes)
                    tab(3, R.string.playlists, R.drawable.playlist)
                    tab(4, R.string.artists, R.drawable.person)
                    tab(5, R.string.albums, R.drawable.disc)
                    tab(6, R.string.local, R.drawable.library)
                    tab(7, R.string.offline, R.drawable.download)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    val onSearchClick = { searchRoute("") }
                    when (currentTabIndex) {
                        0 -> QuickPicks(
                            onAlbumClick = { albumRoute(it.key) },
                            onArtistClick = { artistRoute(it.key) },
                            onPlaylistClick = {
                                playlistRoute(
                                    p0 = it.key,
                                    p1 = null,
                                    p2 = null,
                                    p3 = it.channel?.name == "YouTube Music"
                                )
                            },
                            onSearchClick = onSearchClick,
                            onBrowseCategory = { searchResultRoute(it) }
                        )

                        1 -> HomeDiscovery(
                            onMoodClick = { mood -> moodRoute(mood.toUiMood()) },
                            onNewReleaseAlbumClick = { albumRoute(it) },
                            onSearchClick = onSearchClick,
                            onMoreMoodsClick = { moreMoodsRoute() },
                            onMoreAlbumsClick = { moreAlbumsRoute() },
                            onBrowseCategory = { searchResultRoute(it) },
                            onPlaylistClick = { playlistRoute(it, null, null, true) }
                        )

                        2 -> HomeSongs(
                            onSearchClick = onSearchClick
                        )

                        3 -> HomePlaylists(
                            onBuiltInPlaylist = { builtInPlaylistRoute(it) },
                            onPlaylistClick = { localPlaylistRoute(it.id) },
                            onPipedPlaylistClick = { session, playlist ->
                                pipedPlaylistRoute(
                                    p0 = session.apiBaseUrl.toString(),
                                    p1 = session.token,
                                    p2 = playlist.id.toString()
                                )
                            },
                            onSearchClick = onSearchClick
                        )

                        4 -> HomeArtistList(
                            onArtistClick = { artistRoute(it.id) },
                            onSearchClick = onSearchClick
                        )

                        5 -> HomeAlbums(
                            onAlbumClick = { albumRoute(it.id) },
                            onSearchClick = onSearchClick
                        )

                        6 -> HomeLocalSongs(
                            onSearchClick = onSearchClick
                        )
                        7 -> BuiltInPlaylistSongs(builtInPlaylist = BuiltInPlaylist.Offline)
                    }
                }
            }
        }
    }
}
