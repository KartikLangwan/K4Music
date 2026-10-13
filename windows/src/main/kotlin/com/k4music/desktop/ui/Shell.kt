package com.k4music.desktop.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.app_name
import com.k4music.desktop.res.discover
import com.k4music.desktop.res.globe
import com.k4music.desktop.res.home
import com.k4music.desktop.res.library
import com.k4music.desktop.res.search
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class Destination(val title: StringResource, val icon: DrawableResource) {
    Home(Res.string.home, Res.drawable.home),
    Discover(Res.string.discover, Res.drawable.globe),
    Search(Res.string.search, Res.drawable.search),
    Library(Res.string.library, Res.drawable.library)
}

/** Desktop frame: navigation sidebar on the left, the current destination on the right. */
@Composable
fun Shell() {
    var destination by remember { mutableStateOf(Destination.Home) }
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Sidebar(destination) { destination = it }
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            Text(stringResource(destination.title), style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
private fun Sidebar(current: Destination, onSelect: (Destination) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(232.dp).fillMaxHeight()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(Res.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 16.dp)
            )
            Destination.entries.forEach { entry ->
                NavigationDrawerItem(
                    label = { Text(stringResource(entry.title)) },
                    icon = { Icon(painterResource(entry.icon), null, Modifier.size(22.dp)) },
                    selected = entry == current,
                    onClick = { onSelect(entry) },
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
