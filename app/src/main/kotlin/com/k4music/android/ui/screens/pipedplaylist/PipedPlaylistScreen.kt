package com.k4music.android.ui.screens.pipedplaylist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.k4music.app.R
import com.k4music.android.ui.components.themed.Scaffold
import com.k4music.android.ui.screens.GlobalRoutes
import com.k4music.android.ui.screens.Route
import com.k4music.compose.persist.PersistMapCleanup
import com.k4music.compose.routing.RouteHandler
import com.k4music.providers.piped.models.authenticatedWith
import io.ktor.http.Url
import java.util.UUID

@Route
@Composable
fun PipedPlaylistScreen(
    apiBaseUrl: Url,
    sessionToken: String,
    playlistId: UUID
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val session by remember { derivedStateOf { apiBaseUrl authenticatedWith sessionToken } }

    PersistMapCleanup(prefix = "pipedplaylist/$playlistId")

    RouteHandler {
        GlobalRoutes()

        Content {
            Scaffold(
                key = "pipedplaylist",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = 0,
                onTabChange = { },
                tabColumnContent = {
                    tab(0, R.string.songs, R.drawable.musical_notes)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> PipedPlaylistSongList(
                            session = session,
                            playlistId = playlistId
                        )
                    }
                }
            }
        }
    }
}
