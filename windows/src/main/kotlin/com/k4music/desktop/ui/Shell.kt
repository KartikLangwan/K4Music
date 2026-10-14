package com.k4music.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.app_name
import com.k4music.desktop.res.chevron_back
import com.k4music.desktop.res.desktop_back
import com.k4music.desktop.res.discover
import com.k4music.desktop.res.globe
import com.k4music.desktop.res.home
import com.k4music.desktop.res.library
import com.k4music.desktop.res.search
import com.k4music.desktop.res.settings
import com.k4music.desktop.ui.screens.ScreenContent
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private class RootItem(val screen: Screen, val title: StringResource, val icon: DrawableResource)

private val roots = listOf(
    RootItem(Screen.Home, Res.string.home, Res.drawable.home),
    RootItem(Screen.Discover, Res.string.discover, Res.drawable.globe),
    RootItem(Screen.Search(), Res.string.search, Res.drawable.search),
    RootItem(Screen.Library, Res.string.library, Res.drawable.library)
)

/** Window frame: sidebar | content | optional queue/lyrics panel, player bar along the bottom. */
@Composable
fun Shell(panel: MutableState<SidePanel?>, playerOpen: MutableState<Boolean>, searchFocus: FocusRequester) {
    val navigator = LocalNavigator.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                Sidebar(navigator)
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (navigator.canGoBack) IconButton(onClick = navigator::back, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
                        Icon(painterResource(Res.drawable.chevron_back), stringResource(Res.string.desktop_back))
                    }
                    Box(Modifier.weight(1f).padding(horizontal = 28.dp, vertical = 16.dp)) {
                        ScreenContent(navigator.current, searchFocus)
                    }
                }
                panel.value?.let { which ->
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(min = 320.dp, max = 380.dp).fillMaxHeight()) {
                        when (which) {
                            SidePanel.Queue -> QueuePanel()
                            SidePanel.Lyrics -> LyricsPanel()
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NowPlayingBar(panel.value, { panel.value = it }, onOpenPlayer = { playerOpen.value = true })
        }
        AnimatedVisibility(
            playerOpen.value,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            NowPlaying(panel.value) { playerOpen.value = false }
        }
        DialogHost()
    }
}

@Composable
private fun Sidebar(navigator: Navigator) {
    val current = navigator.stack.first()
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(232.dp).fillMaxHeight()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(Res.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 16.dp)
            )
            roots.forEach { item -> NavItem(item.title, item.icon, sameRoot(current, item.screen)) { navigator.root(item.screen) } }
            Box(Modifier.weight(1f))
            NavItem(Res.string.settings, Res.drawable.settings, current == Screen.Settings) { navigator.root(Screen.Settings) }
        }
    }
}

private fun sameRoot(a: Screen, b: Screen) = a == b || (a is Screen.Search && b is Screen.Search)

@Composable
private fun NavItem(title: StringResource, icon: DrawableResource, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(stringResource(title)) },
        icon = { Icon(painterResource(icon), null, Modifier.size(22.dp)) },
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
