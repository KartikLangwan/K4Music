package com.k4music.android.ui.screens.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.k4music.app.R
import com.k4music.android.ui.components.themed.Scaffold
import com.k4music.android.ui.screens.GlobalRoutes
import com.k4music.android.ui.screens.Route
import com.k4music.compose.persist.PersistMapCleanup
import com.k4music.compose.routing.RouteHandler

@Route
@Composable
fun PlaylistScreen(
    browseId: String,
    params: String?,
    shouldDedup: Boolean,
    maxDepth: Int? = null
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    PersistMapCleanup(prefix = "playlist/$browseId")

    RouteHandler {
        GlobalRoutes()

        Content {
            Scaffold(
                key = "playlist",
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
                        0 -> PlaylistSongList(
                            browseId = browseId,
                            params = params,
                            maxDepth = maxDepth,
                            shouldDedup = shouldDedup
                        )
                    }
                }
            }
        }
    }
}
