package com.k4music.android.ui.components.themed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.k4music.android.utils.windowState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k4music.android.Brand
import com.k4music.android.LocalPlayerAwareWindowInsets
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.android.LocalSetBottomNavigation
import com.k4music.android.ui.screens.searchRoute
import com.k4music.app.R
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import kotlinx.coroutines.launch

/** One adaptive shell for all destinations, with clean navigation docking and proper insets. */
@Composable
fun Scaffold(
    key: String,
    topIconButtonId: Int,
    onTopIconButtonClick: () -> Unit,
    tabIndex: Int,
    onTabChange: (Int) -> Unit,
    tabColumnContent: TabsBuilder.() -> Unit,
    modifier: Modifier = Modifier,
    tabsEditingTitle: String = stringResource(R.string.tabs),
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    val tabs = TabsBuilder.rememberTabs(tabColumnContent)
    val scope = rememberCoroutineScope()
    if (tabs.isEmpty()) return
    val selected = tabIndex.coerceIn(tabs.indices)
    LaunchedEffect(selected, tabIndex) {
        if (selected != tabIndex) onTabChange(selected)
    }
    val palette = LocalAppearance.current.colorPalette
    val insets = LocalPlayerAwareWindowInsets.current
    val binder = LocalPlayerServiceBinder.current
    val (currentWindow, _) = windowState(binder)
    val isHome = key == "home"
    val homeDestinations = listOf(0, 1, 2, 7)
    val homeTitles = listOf(R.string.home, R.string.discover, R.string.library, R.string.offline)
    val homeIcons = listOf(R.drawable.sparkles, R.drawable.globe, R.drawable.library, R.drawable.download)
    val selectedDestination = when (selected) { 0 -> 0; 1 -> 1; 6, 7 -> 3; else -> 2 }

    val setBottomNav = LocalSetBottomNavigation.current

    BoxWithConstraints(modifier.background(palette.background0).fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val showBottomNav = !wide && isHome

        DisposableEffect(showBottomNav) {
            setBottomNav(showBottomNav)
            onDispose { setBottomNav(false) }
        }

        Row(Modifier.fillMaxSize().padding(insets.only(WindowInsetsSides.Horizontal).asPaddingValues())) {
            if (wide) NavigationRail(
                containerColor = palette.background1,
                modifier = Modifier.width(108.dp).padding(insets.only(WindowInsetsSides.Top).asPaddingValues())
            ) {
                IconButton(onClick = onTopIconButtonClick) {
                    Icon(
                        painter = painterResource(topIconButtonId),
                        contentDescription = stringResource(if (isHome) R.string.settings else R.string.back),
                        tint = palette.text
                    )
                }
                tabs.forEachIndexed { index, tab ->
                    NavigationRailItem(
                        selected = selected == index,
                        onClick = { onTabChange(index) },
                        icon = { Icon(painterResource(tab.icon), null, Modifier.size(22.dp)) },
                        label = { Text(tab.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                // Sleek edge-to-edge TopBar
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(palette.background1, palette.background0)))
                            .padding(insets.only(WindowInsetsSides.Top).asPaddingValues())
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isHome) IconButton(onClick = onTopIconButtonClick) {
                                Icon(
                                    painter = painterResource(topIconButtonId),
                                    contentDescription = stringResource(R.string.back),
                                    tint = palette.text
                                )
                            }
                            Text(
                                text = if (isHome) Brand.APP_NAME else when (key) {
                                    "settings" -> stringResource(R.string.settings)
                                    "search" -> stringResource(R.string.search)
                                    else -> tabs[selected].title()
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.text,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isHome) {
                                IconButton(onClick = { scope.launch { searchRoute.ensureGlobal("") } }) {
                                    Icon(
                                        painter = painterResource(R.drawable.search),
                                        contentDescription = stringResource(R.string.search),
                                        tint = palette.text
                                    )
                                }
                                IconButton(onClick = onTopIconButtonClick) {
                                    Icon(
                                        painter = painterResource(topIconButtonId),
                                        contentDescription = stringResource(R.string.settings),
                                        tint = palette.text
                                    )
                                }
                            }
                        }
                    }
                }

                // Filter chips only if more than 1 tab exists
                if (!wide && key != "settings" && (!isHome || selected >= 2) && tabs.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(tabs, key = { _, tab -> tab.key }) { index, tab ->
                            val shouldShow = !isHome || (selected in 2..5 && index in 2..5) || (selected >= 6 && index >= 6)
                            if (shouldShow) FilterChip(
                                selected = selected == index,
                                onClick = { onTabChange(index) },
                                label = { Text(tab.title(), fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal) },
                                leadingIcon = { Icon(painterResource(tab.icon), null, Modifier.size(18.dp)) }
                            )
                        }
                    }
                }

                // Screen content with proper insets
                val contentInsets = if (isHome) {
                    val hasSong = currentWindow != null
                    WindowInsets(bottom = if (hasSong) Dimensions.items.collapsedPlayerHeight + 16.dp else 16.dp)
                } else {
                    insets
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(LocalPlayerAwareWindowInsets provides contentInsets) {
                        AnimatedContent(
                            targetState = selected,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "destination",
                            content = content
                        )
                    }
                }

                // Bottom Navigation Bar with clean docking
                if (showBottomNav) {
                    Surface(
                        color = palette.background1.copy(alpha = 0.92f),
                        modifier = Modifier.fillMaxWidth().border(
                            0.5.dp, palette.text.copy(alpha = 0.08f), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                        ),
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                    ) {
                        Column {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                modifier = Modifier.fillMaxWidth().height(64.dp)
                            ) {
                                homeDestinations.forEachIndexed { index, destination ->
                                    val isSelected = selectedDestination == index
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { onTabChange(destination) },
                                        icon = {
                                            Icon(
                                                painter = painterResource(homeIcons[index]),
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(homeTitles[index]),
                                                maxLines = 1,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = palette.accent,
                                            selectedTextColor = palette.accent,
                                            indicatorColor = palette.accent.copy(alpha = 0.15f),
                                            unselectedIconColor = palette.textSecondary,
                                            unselectedTextColor = palette.textSecondary
                                        )
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(insets.only(WindowInsetsSides.Bottom).asPaddingValues())
                            )
                        }
                    }
                }
            }
        }
    }
}
