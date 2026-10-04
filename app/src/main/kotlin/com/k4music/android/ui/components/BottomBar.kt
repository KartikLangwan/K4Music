package com.k4music.android.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k4music.android.ui.components.themed.HorizontalDivider
import com.k4music.app.R
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance

enum class RootDestination(@param:StringRes val title: Int, @param:DrawableRes val icon: Int) {
    Home(R.string.home, R.drawable.home),
    Discover(R.string.discover, R.drawable.globe),
    Search(R.string.search, R.drawable.search),
    Library(R.string.library, R.drawable.library)
}

/** The app's persistent bottom navigation; it also paints the area behind the system bar. */
@Composable
fun BottomBar(
    selected: RootDestination?,
    onSelect: (RootDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppearance.current.colorPalette

    Column(modifier.fillMaxWidth().background(palette.background1)) {
        HorizontalDivider(color = palette.text.copy(alpha = 0.06f))
        NavigationBar(
            containerColor = Color.Transparent,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimensions.items.navigationBarHeight)
        ) {
            RootDestination.entries.forEach { destination ->
                val isSelected = destination == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(destination) },
                    icon = { Icon(painterResource(destination.icon), null, Modifier.size(22.dp)) },
                    label = {
                        Text(
                            text = stringResource(destination.title),
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = palette.accent,
                        selectedTextColor = palette.text,
                        indicatorColor = palette.accent.copy(alpha = 0.16f),
                        unselectedIconColor = palette.textSecondary,
                        unselectedTextColor = palette.textSecondary
                    )
                )
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
}
