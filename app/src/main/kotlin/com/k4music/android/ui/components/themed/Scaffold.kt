package com.k4music.android.ui.components.themed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k4music.android.Brand
import com.k4music.android.LocalPlayerAwareWindowInsets
import com.k4music.android.WideLayoutBreakpoint
import com.k4music.android.ui.screens.searchRoute
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance
import kotlinx.coroutines.launch

/** Home tabs at or after this index belong to the Library destination. */
const val LIBRARY_FIRST_TAB = 2

/**
 * One adaptive shell for every screen: a flat top bar, optional tab chips and the content.
 * Phones navigate with the app-wide bottom bar; wide windows get a navigation rail here.
 */
@Composable
fun Scaffold(
    key: String,
    topIconButtonId: Int,
    onTopIconButtonClick: () -> Unit,
    tabIndex: Int,
    onTabChange: (Int) -> Unit,
    tabColumnContent: TabsBuilder.() -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("unused") tabsEditingTitle: String = stringResource(R.string.tabs),
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
    val isHome = key == "home"

    BoxWithConstraints(modifier.background(palette.background0).fillMaxSize()) {
        val wide = maxWidth >= WideLayoutBreakpoint

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
                        label = { Text(tab.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = palette.accent,
                            selectedTextColor = palette.text,
                            indicatorColor = palette.accent.copy(alpha = 0.16f),
                            unselectedIconColor = palette.textSecondary,
                            unselectedTextColor = palette.textSecondary
                        )
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(insets.only(WindowInsetsSides.Top).asPaddingValues())
                        .height(60.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isHome) IconButton(onClick = onTopIconButtonClick) {
                        Icon(
                            painter = painterResource(topIconButtonId),
                            contentDescription = stringResource(R.string.back),
                            tint = palette.text
                        )
                    }
                    Text(
                        text = when {
                            !isHome -> when (key) {
                                "settings" -> stringResource(R.string.settings)
                                "search" -> stringResource(R.string.search)
                                else -> tabs[selected].title()
                            }
                            wide || selected == 0 -> Brand.APP_NAME
                            selected == 1 -> stringResource(R.string.discover)
                            else -> stringResource(R.string.library)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.text,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (isHome) 12.dp else 0.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isHome) {
                        // Phones reach search from the bottom bar; the rail has no search entry.
                        if (wide) IconButton(onClick = { scope.launch { searchRoute.ensureGlobal("") } }) {
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

                val chipTabs = when {
                    wide || key == "settings" -> emptyList()
                    isHome -> if (selected >= LIBRARY_FIRST_TAB) tabs.indices.filter { it >= LIBRARY_FIRST_TAB } else emptyList()
                    else -> tabs.indices.toList()
                }
                if (chipTabs.size > 1) LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    items(chipTabs, key = { tabs[it].key }) { index ->
                        val tab = tabs[index]
                        val isSelected = selected == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { onTabChange(index) },
                            label = {
                                Text(
                                    text = tab.title(),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            leadingIcon = { Icon(painterResource(tab.icon), null, Modifier.size(16.dp)) },
                            shape = CircleShape,
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = palette.background2,
                                labelColor = palette.textSecondary,
                                iconColor = palette.textSecondary,
                                selectedContainerColor = palette.accent,
                                selectedLabelColor = palette.onAccent,
                                selectedLeadingIconColor = palette.onAccent
                            )
                        )
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    // The top bar already consumed the status bar; content only pads the bottom.
                    CompositionLocalProvider(LocalPlayerAwareWindowInsets provides insets.only(WindowInsetsSides.Bottom)) {
                        AnimatedContent(
                            targetState = selected,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "destination",
                            content = content
                        )
                    }
                }
            }
        }
    }
}
