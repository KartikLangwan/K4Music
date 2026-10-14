package com.k4music.desktop.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.k4music.desktop.player.PlayerController

/** Every place the app can show. Root screens sit in the sidebar; the rest stack on top. */
sealed interface Screen {
    data object Home : Screen
    data object Discover : Screen
    data class Search(val query: String = "") : Screen
    data object Library : Screen
    data class Album(val browseId: String) : Screen
    data class Artist(val browseId: String) : Screen
    data class Playlist(val browseId: String, val params: String? = null, val maxDepth: Int? = null) : Screen
    data class Mood(val browseId: String, val params: String?, val title: String) : Screen
    data class LocalPlaylist(val id: Long) : Screen
    data class BuiltIn(val kind: BuiltInKind) : Screen
    data class PipedPlaylist(val sessionId: Long, val playlistId: String, val name: String) : Screen
    data object Settings : Screen
}

enum class BuiltInKind { Favorites, History, Downloads, Top, Local }

@Stable
class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current get() = stack.last()
    val canGoBack get() = stack.size > 1

    fun go(screen: Screen) {
        if (current != screen) stack.add(screen)
    }

    /** Sidebar destinations always land on their root, whatever is open. */
    fun root(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }

    fun back() {
        if (canGoBack) stack.removeAt(stack.lastIndex)
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }
val LocalPlayer = staticCompositionLocalOf<PlayerController> { error("No player") }
