package com.k4music.android.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.k4music.android.Database
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.app.R
import com.k4music.android.handleUrl
import com.k4music.android.models.Mood
import com.k4music.android.models.SearchQuery
import com.k4music.android.preferences.DataPreferences
import com.k4music.android.query
import com.k4music.android.ui.screens.album.AlbumScreen
import com.k4music.android.ui.screens.audio.AudioScreen
import com.k4music.android.ui.screens.artist.ArtistScreen
import com.k4music.android.ui.screens.pipedplaylist.PipedPlaylistScreen
import com.k4music.android.ui.screens.playlist.PlaylistScreen
import com.k4music.android.ui.screens.search.SearchScreen
import com.k4music.android.ui.screens.searchresult.SearchResultScreen
import com.k4music.android.ui.screens.settings.LogsScreen
import com.k4music.android.ui.screens.settings.SettingsScreen
import com.k4music.android.utils.toast
import com.k4music.compose.routing.Route0
import com.k4music.compose.routing.Route1
import com.k4music.compose.routing.Route3
import com.k4music.compose.routing.Route4
import com.k4music.compose.routing.RouteHandlerScope
import com.k4music.core.data.enums.BuiltInPlaylist
import io.ktor.http.Url
import java.util.UUID

/**
 * Marker class for linters that a composable is a route and should not be handled like a regular
 * composable, but rather as an entrypoint.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class Route

val albumRoute = Route1<String>("albumRoute")
val artistRoute = Route1<String>("artistRoute")
val builtInPlaylistRoute = Route1<BuiltInPlaylist>("builtInPlaylistRoute")
val localPlaylistRoute = Route1<Long>("localPlaylistRoute")
val logsRoute = Route0("logsRoute")
val pipedPlaylistRoute = Route3<String, String, String>("pipedPlaylistRoute")
val playlistRoute = Route4<String, String?, Int?, Boolean>("playlistRoute")
val moodRoute = Route1<Mood>("moodRoute")
val searchResultRoute = Route1<String>("searchResultRoute")
val searchRoute = Route1<String>("searchRoute")
val settingsRoute = Route0("settingsRoute")
val audioRoute = Route0("audioRoute")

@Composable
fun RouteHandlerScope.GlobalRoutes() {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current

    albumRoute { browseId ->
        AlbumScreen(browseId = browseId)
    }

    artistRoute { browseId ->
        ArtistScreen(browseId = browseId)
    }

    logsRoute {
        LogsScreen()
    }

    pipedPlaylistRoute { apiBaseUrl, sessionToken, playlistId ->
        PipedPlaylistScreen(
            apiBaseUrl = runCatching { Url(apiBaseUrl) }.getOrNull()
                ?: error("Invalid apiBaseUrl: $apiBaseUrl is not a valid Url"),
            sessionToken = sessionToken,
            playlistId = runCatching {
                UUID.fromString(playlistId)
            }.getOrNull() ?: error("Invalid playlistId: $playlistId is not a valid UUID")
        )
    }

    playlistRoute { browseId, params, maxDepth, shouldDedup ->
        PlaylistScreen(
            browseId = browseId,
            params = params,
            maxDepth = maxDepth,
            shouldDedup = shouldDedup
        )
    }

    settingsRoute {
        SettingsScreen()
    }

    audioRoute {
        AudioScreen()
    }

    searchRoute { initialTextInput ->
        SearchScreen(
            initialTextInput = initialTextInput,
            onSearch = { query ->
                searchResultRoute(query)

                if (!DataPreferences.pauseSearchHistory) query {
                    Database.insert(SearchQuery(query = query))
                }
            },
            onViewPlaylist = { url ->
                with(context) {
                    runCatching {
                        handleUrl(url.toUri(), binder)
                    }.onFailure {
                        toast(getString(R.string.error_url, url))
                    }
                }
            }
        )
    }

    searchResultRoute { query ->
        SearchResultScreen(
            query = query,
            onSearchAgain = { searchRoute(query) }
        )
    }
}
