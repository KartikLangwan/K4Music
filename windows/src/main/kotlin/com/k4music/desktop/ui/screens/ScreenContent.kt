package com.k4music.desktop.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import com.k4music.desktop.ui.Screen

/** Shows the screen on top of the navigation stack; each keeps its own state while open. */
@Composable
fun ScreenContent(screen: Screen, searchFocus: FocusRequester) {
    key(screen) {
        when (screen) {
            Screen.Home -> HomeScreen()
            Screen.Discover -> DiscoverScreen()
            is Screen.Search -> SearchScreen(screen.query, searchFocus)
            Screen.Library -> LibraryScreen()
            is Screen.Album -> AlbumScreen(screen.browseId)
            is Screen.Artist -> ArtistScreen(screen.browseId)
            is Screen.Playlist -> PlaylistScreen(screen.browseId, screen.params)
            is Screen.Mood -> MoodScreen(screen.browseId, screen.params, screen.title)
            is Screen.LocalPlaylist -> LocalPlaylistScreen(screen.id)
            is Screen.BuiltIn -> BuiltInScreen(screen.kind)
            is Screen.PipedPlaylist -> PipedPlaylistScreen(screen.sessionId, screen.playlistId, screen.name)
            Screen.Settings -> SettingsScreen()
        }
    }
}
