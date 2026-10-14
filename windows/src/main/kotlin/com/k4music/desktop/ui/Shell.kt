package com.k4music.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.PlainTooltip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.k4music.android.models.PlaylistPreview
import com.k4music.core.data.enums.PlaylistSortBy
import com.k4music.core.data.enums.SortOrder
import com.k4music.desktop.data.Db
import com.k4music.desktop.prefs.NavPosition
import com.k4music.desktop.prefs.Settings
import com.k4music.desktop.res.*
import com.k4music.desktop.ui.screens.ScreenContent
import com.k4music.desktop.ui.screens.submitSearch
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A navigation button: [key] names it in Settings.hiddenNav (Home cannot be hidden). */
class NavDestination(val key: String, val screen: Screen, val title: StringResource, val icon: DrawableResource)

val navDestinations = listOf(
    NavDestination("Home", Screen.Home, Res.string.home, Res.drawable.home),
    NavDestination("Discover", Screen.Discover, Res.string.discover, Res.drawable.globe),
    NavDestination("Search", Screen.Search(), Res.string.search, Res.drawable.search),
    NavDestination("Library", Screen.Library, Res.string.library, Res.drawable.library)
)

private fun visibleDestinations(): List<NavDestination> {
    val hidden = Settings.hiddenNav.split(',').map { it.trim() }.toSet()
    return navDestinations.filter { it.key == "Home" || it.key !in hidden }
}

/** Which navigation button to highlight: the nearest navigation screen below the current page. */
private fun Navigator.section(): Screen =
    stack.lastOrNull { s -> navDestinations.any { sameRoot(s, it.screen) } } ?: stack.first()

private fun sameRoot(a: Screen, b: Screen) = a == b || (a is Screen.Search && b is Screen.Search)

/** Spacing between the window's rounded panels. */
private val Gap = 8.dp
private const val MIN_SIDEBAR = 220f
private const val MAX_SIDEBAR = 420f

/**
 * The gap between two panels, draggable to resize them (resize cursor on hover, a thin
 * accent line while hovered or dragged). [onDrag] gets the pointer movement in dp.
 */
@Composable
private fun Resizer(onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    Box(
        Modifier.width(Gap).fillMaxHeight()
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
            .draggable(
                rememberDraggableState { px -> onDrag(with(density) { px.toDp().value }) },
                Orientation.Horizontal,
                onDragStarted = { dragging = true },
                onDragStopped = { dragging = false; onDragEnd() }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (hovered || dragging) Box(
            Modifier.width(2.dp).fillMaxHeight().padding(vertical = 12.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
        )
    }
}
private val PanelShape = RoundedCornerShape(22.dp)

/**
 * Window frame, desktop-music-app style: top bar (history, home, search), side panel
 * (navigation + your library, collapsible), content, optional queue/lyrics panel, player bar.
 */
@Composable
fun Shell(
    panel: MutableState<SidePanel?>,
    playerOpen: MutableState<Boolean>,
    searchFocus: FocusRequester,
    fullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null
) {
    val navigator = LocalNavigator.current
    val colors = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        // Narrow windows (or a queue/lyrics panel on a medium one) get the icon-only side panel,
        // so the page itself never gets squeezed.
        val narrow = maxWidth < 1100.dp || (panel.value != null && maxWidth < 1440.dp)
        Column(Modifier.fillMaxSize()) {
            TopBar(navigator, searchFocus, fullscreen, onToggleFullscreen)
            val ambient = rememberAmbientColor()
            Row(Modifier.weight(1f).padding(horizontal = Gap)) {
                if (Settings.navPosition == NavPosition.Side || Settings.sidebarPlaylists) {
                    // Drag the edge to resize; drag it far left to shrink to icons (and back out to expand).
                    var width by remember { mutableStateOf(Settings.sidebarWidth.toFloat()) }
                    val collapsed = Settings.sidebarCollapsed || narrow
                    SidePanelColumn(navigator, collapsed, if (collapsed) 76.dp else width.dp)
                    Resizer(
                        onDrag = { delta ->
                            if (collapsed) {
                                if (delta > 0 && !narrow) { Settings.sidebarCollapsed = false; width = MIN_SIDEBAR }
                            } else {
                                width = (width + delta).coerceAtMost(MAX_SIDEBAR)
                                if (width < MIN_SIDEBAR - 60) { Settings.sidebarCollapsed = true; width = MIN_SIDEBAR }
                            }
                        },
                        onDragEnd = { Settings.sidebarWidth = width.coerceIn(MIN_SIDEBAR, MAX_SIDEBAR).toInt(); width = Settings.sidebarWidth.toFloat() }
                    )
                }
                ContentPanel(navigator, ambient, Modifier.weight(1f))
                panel.value?.let { which ->
                    var width by remember { mutableStateOf(Settings.sidePanelWidth.toFloat()) }
                    Resizer(
                        onDrag = { delta -> width = (width - delta).coerceIn(280f, 560f) },
                        onDragEnd = { Settings.sidePanelWidth = width.toInt() }
                    )
                    Surface(shape = PanelShape, color = colors.surface, modifier = Modifier.width(width.dp).fillMaxHeight()) {
                        when (which) {
                            SidePanel.Queue -> QueuePanel()
                            SidePanel.Lyrics -> LyricsPanel()
                        }
                    }
                }
            }
            if (Settings.navPosition == NavPosition.Bottom) BottomNav(navigator)
            NowPlayingBar(panel.value, { panel.value = it }, onOpenPlayer = { playerOpen.value = true }, ambient = ambient)
        }
        AnimatedVisibility(playerOpen.value, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            NowPlaying(panel.value) { playerOpen.value = false }
        }
        DialogHost()
    }
}

/** The page itself: a rounded panel tinted by the playing song's colours (or your picture). */
@Composable
private fun ContentPanel(navigator: Navigator, ambient: Color, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = PanelShape, color = colors.surface, modifier = modifier.fillMaxHeight()) {
        Box(Modifier.fillMaxSize()) {
            PageBackdrop(ambient)
            Box(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 20.dp)) {
                ScreenContent(navigator.current)
            }
        }
    }
}

// ---- Top bar ----

@Composable
private fun TopBar(navigator: Navigator, searchFocus: FocusRequester, fullscreen: Boolean, onToggleFullscreen: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp)) {
        Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundIcon(Res.drawable.chevron_back, stringResource(Res.string.desktop_back), navigator.canGoBack) { navigator.back() }
            RoundIcon(Res.drawable.chevron_forward, stringResource(Res.string.desktop_forward), navigator.canGoForward) { navigator.forward() }
        }
        Row(
            Modifier.align(Alignment.Center).width(520.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val home = navigator.current == Screen.Home
            Tip(stringResource(Res.string.home)) {
                IconButton(
                    onClick = { navigator.root(Screen.Home) },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = colors.surfaceContainer),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(painterResource(Res.drawable.home), null, Modifier.size(22.dp), tint = if (home) colors.onSurface else colors.onSurfaceVariant)
                }
            }
            SearchField(navigator, searchFocus, Modifier.weight(1f))
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onToggleFullscreen != null) RoundIcon(
                Res.drawable.expand,
                stringResource(if (fullscreen) Res.string.desktop_exit_fullscreen else Res.string.desktop_fullscreen),
                true,
                onClick = onToggleFullscreen
            )
            RoundIcon(Res.drawable.settings, stringResource(Res.string.settings), true, selected = navigator.current == Screen.Settings) {
                navigator.root(Screen.Settings)
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: DrawableResource, label: String, enabled: Boolean, selected: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Tip(label) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (selected) colors.primaryContainer else colors.surfaceContainer,
                disabledContainerColor = colors.surfaceContainer.copy(alpha = 0.5f)
            ),
            modifier = Modifier.size(36.dp)
        ) { Icon(painterResource(icon), label, Modifier.size(18.dp), tint = if (selected) colors.primary else colors.onSurface.copy(alpha = if (enabled) 1f else 0.35f)) }
    }
}

/**
 * The search pill. Typing shows results live (after a short pause); Enter also saves the query
 * to history and opens pasted YouTube links.
 */
@Composable
private fun SearchField(navigator: Navigator, focus: FocusRequester, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val player = LocalPlayer.current
    val currentQuery = (navigator.current as? Screen.Search)?.query.orEmpty()
    var text by remember { mutableStateOf(currentQuery) }
    var focused by remember { mutableStateOf(false) }
    // Leaving search (or picking a history entry) updates the field.
    LaunchedEffect(currentQuery, navigator.current is Screen.Search) {
        if (!focused || navigator.current !is Screen.Search) text = currentQuery
    }
    LaunchedEffect(text) {
        if (!focused || text.trim() == currentQuery || text.isBlank() || text.startsWith("http")) return@LaunchedEffect
        delay(450)
        val screen = Screen.Search(text.trim())
        if (navigator.current is Screen.Search) navigator.replace(screen) else navigator.go(screen)
    }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier.height(48.dp).clip(CircleShape)
            .background(if (focused || hovered) colors.surfaceContainerHigh else colors.surfaceContainer)
            .hoverable(interaction)
            .clickable { runCatching { focus.requestFocus() } }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(painterResource(Res.drawable.search), null, Modifier.size(20.dp), tint = if (focused) colors.onSurface else colors.onSurfaceVariant)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(
                stringResource(Res.string.desktop_search_hint),
                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (submitSearch(text, navigator, player)) text = "" }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused && navigator.current !is Screen.Search) navigator.go(Screen.Search(text.trim()))
                }.onPreviewKeyEventEnter { if (submitSearch(text, navigator, player)) text = "" }
            )
        }
        if (text.isNotEmpty()) Icon(
            painterResource(Res.drawable.close), stringResource(Res.string.clear),
            Modifier.size(18.dp).clip(CircleShape).clickable { text = ""; if (navigator.current is Screen.Search) navigator.replace(Screen.Search()) },
            tint = colors.onSurfaceVariant
        )
    }
}

/** Desktop text fields do not always raise the IME search action: Enter submits too. */
private fun Modifier.onPreviewKeyEventEnter(action: () -> Unit) = onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { action(); true } else false
}

// ---- Side panel ----

@Composable
private fun SidePanelColumn(navigator: Navigator, collapsed: Boolean, width: Dp) {
    val current = navigator.section()
    Column(
        Modifier.width(width).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(Gap)
    ) {
        if (Settings.navPosition == NavPosition.Side) Surface(shape = PanelShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // K4 mark: the brand sits where other players put nothing.
                Row(
                    Modifier.fillMaxWidth().padding(start = if (collapsed) 0.dp else 6.dp, top = 2.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, K4Pink))),
                        contentAlignment = Alignment.Center
                    ) { Text("K4", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
                    if (!collapsed) Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                visibleDestinations().forEach { d ->
                    SideItem(stringResource(d.title), collapsed, sameRoot(current, d.screen), icon = d.icon, strong = true) { navigator.root(d.screen) }
                }
            }
        }
        if (Settings.sidebarPlaylists) LibraryCard(navigator, collapsed, Modifier.weight(1f))
        else if (Settings.navPosition == NavPosition.Side) Box(Modifier.weight(1f))
    }
}

@Composable
private fun LibraryCard(navigator: Navigator, collapsed: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val playlists by remember { Db.library.playlistPreviews(PlaylistSortBy.DateAdded, SortOrder.Descending) }
        .collectAsState(initial = emptyList<PlaylistPreview>())
    val current = navigator.current
    Surface(shape = PanelShape, color = colors.surface, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = if (collapsed) 0.dp else 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!collapsed) Text(
                    stringResource(Res.string.desktop_your_library),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).clickable { navigator.root(Screen.Library) }
                )
                Box(Modifier.then(if (collapsed) Modifier.fillMaxWidth() else Modifier), contentAlignment = Alignment.Center) {
                    Tip(stringResource(if (collapsed) Res.string.desktop_expand_sidebar else Res.string.desktop_collapse_sidebar)) {
                        IconButton(onClick = { Settings.sidebarCollapsed = !collapsed }, modifier = Modifier.size(40.dp)) {
                            Icon(painterResource(Res.drawable.library), null, Modifier.size(22.dp), tint = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item {
                    SideItem(stringResource(Res.string.favorites), collapsed, current == Screen.BuiltIn(BuiltInKind.Favorites), icon = Res.drawable.heart, tile = true) {
                        navigator.go(Screen.BuiltIn(BuiltInKind.Favorites))
                    }
                }
                item {
                    SideItem(stringResource(Res.string.offline), collapsed, current == Screen.BuiltIn(BuiltInKind.Downloads), icon = Res.drawable.download, tile = true) {
                        navigator.go(Screen.BuiltIn(BuiltInKind.Downloads))
                    }
                }
                item {
                    SideItem(stringResource(Res.string.history), collapsed, current == Screen.BuiltIn(BuiltInKind.History), icon = Res.drawable.history, tile = true) {
                        navigator.go(Screen.BuiltIn(BuiltInKind.History))
                    }
                }
                items(playlists, key = { it.id }) { p ->
                    SideItem(
                        p.name, collapsed, current == Screen.LocalPlaylist(p.id),
                        subtitle = stringResource(Res.string.desktop_song_count, p.songCount),
                        artwork = p.thumbnail ?: ""
                    ) { navigator.go(Screen.LocalPlaylist(p.id)) }
                }
            }
        }
    }
}

/** One row of the side panel; collapsed it shows only the icon or artwork, with a tooltip. */
@Composable
private fun SideItem(
    title: String,
    collapsed: Boolean,
    selected: Boolean,
    icon: DrawableResource? = null,
    subtitle: String? = null,
    artwork: String? = null,
    tile: Boolean = false,
    strong: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    // Main navigation: the selected page is a violet-to-pink pill with white text.
    val pill = selected && strong
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Tip(title, enabled = collapsed) {
        Row(
            Modifier.fillMaxWidth().clip(if (strong) CircleShape else RoundedCornerShape(14.dp))
                .then(
                    if (pill) Modifier.background(Brush.linearGradient(listOf(colors.primary, K4Pink)))
                    else Modifier.background(
                        when {
                            selected -> colors.primaryContainer
                            hovered -> colors.surfaceContainer
                            else -> Color.Transparent
                        }
                    )
                )
                .hoverable(interaction)
                .clickable(onClick = onClick)
                .padding(horizontal = if (collapsed) 0.dp else 10.dp, vertical = if (artwork != null || tile) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(14.dp)
        ) {
            when {
                artwork != null -> Artwork(artwork.ifBlank { null }, 44.dp, Modifier.size(44.dp), corner = 22.dp)
                tile && icon != null -> Box(
                    Modifier.size(44.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) { Icon(painterResource(icon), null, Modifier.size(20.dp), tint = colors.primary) }
                icon != null -> Icon(
                    painterResource(icon), null, Modifier.size(24.dp),
                    tint = if (pill) Color.White else if (selected) colors.primary else colors.onSurfaceVariant
                )
            }
            if (!collapsed) Column(Modifier.weight(1f)) {
                Text(
                    title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = if (artwork != null || tile) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (pill) Color.White else if (selected) colors.onSurface else if (artwork != null || tile) colors.onSurface else colors.onSurfaceVariant
                )
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

// ---- Bottom navigation (Settings → Layout) ----

@Composable
private fun BottomNav(navigator: Navigator) {
    val colors = MaterialTheme.colorScheme
    val current = navigator.section()
    Surface(shape = PanelShape, color = colors.surface, modifier = Modifier.fillMaxWidth().padding(start = Gap, end = Gap, top = Gap)) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            visibleDestinations().forEach { d ->
                val selected = sameRoot(current, d.screen)
                Row(
                    Modifier.clip(CircleShape)
                        .background(if (selected) Brush.linearGradient(listOf(colors.primary, K4Pink)) else SolidColor(Color.Transparent))
                        .clickable { navigator.root(d.screen) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(painterResource(d.icon), null, Modifier.size(20.dp), tint = if (selected) Color.White else colors.onSurfaceVariant)
                    Text(
                        stringResource(d.title), style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Plain tooltip on hover (desktop convention for icon-only controls). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun Tip(label: String, enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) return content()
    androidx.compose.material3.TooltipBox(
        positionProvider = androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = androidx.compose.material3.rememberTooltipState()
    ) { content() }
}
