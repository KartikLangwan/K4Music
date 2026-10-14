package com.k4music.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k4music.desktop.player.toTrack
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.discover_description
import com.k4music.desktop.res.discover_genres_title
import com.k4music.desktop.res.discover_headline
import com.k4music.desktop.res.discover_indian_title
import com.k4music.desktop.res.genre_bollywood
import com.k4music.desktop.res.genre_hindi_description
import com.k4music.desktop.res.genre_hiphop
import com.k4music.desktop.res.genre_hiphop_description
import com.k4music.desktop.res.genre_indian_indie
import com.k4music.desktop.res.genre_indie_description
import com.k4music.desktop.res.genre_lofi
import com.k4music.desktop.res.genre_lofi_description
import com.k4music.desktop.res.genre_pop
import com.k4music.desktop.res.genre_pop_description
import com.k4music.desktop.res.genre_punjabi
import com.k4music.desktop.res.genre_punjabi_description
import com.k4music.desktop.res.genre_rock
import com.k4music.desktop.res.genre_rock_description
import com.k4music.desktop.res.genre_south
import com.k4music.desktop.res.genre_south_description
import com.k4music.desktop.res.more
import com.k4music.desktop.res.new_released_albums
import com.k4music.desktop.res.pick_your_mood
import com.k4music.desktop.res.play
import com.k4music.desktop.res.trending
import com.k4music.desktop.ui.Artwork
import com.k4music.desktop.ui.ErrorState
import com.k4music.desktop.ui.LocalNavigator
import com.k4music.desktop.ui.LocalPlayer
import com.k4music.desktop.ui.MediaCard
import com.k4music.desktop.ui.Screen
import com.k4music.desktop.ui.SectionHeader
import com.k4music.desktop.ui.Shelf
import com.k4music.desktop.ui.Skeleton
import com.k4music.desktop.ui.SongListSkeleton
import com.k4music.desktop.ui.SongRow
import com.k4music.desktop.ui.rememberPage
import com.k4music.desktop.ui.songActions
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.BrowseBody
import com.k4music.providers.innertube.requests.browse
import com.k4music.providers.innertube.requests.discoverPage
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// Same genre shortcuts and colours as Android's DiscoveryCategories.kt.
private class Category(val title: StringResource, val subtitle: StringResource, val query: String, val color: Long)

private val indian = listOf(
    Category(Res.string.genre_bollywood, Res.string.genre_hindi_description, "Bollywood Hindi hit songs", 0xFFECA5A8),
    Category(Res.string.genre_punjabi, Res.string.genre_punjabi_description, "Punjabi hit songs", 0xFFA7BFEF),
    Category(Res.string.genre_south, Res.string.genre_south_description, "Tamil Telugu Malayalam hit songs", 0xFFA5DCC2),
    Category(Res.string.genre_indian_indie, Res.string.genre_indie_description, "Indian indie music", 0xFFD6B3ED)
)
private val global = listOf(
    Category(Res.string.genre_pop, Res.string.genre_pop_description, "Pop hit songs", 0xFFE8BF84),
    Category(Res.string.genre_rock, Res.string.genre_rock_description, "Rock essentials songs", 0xFF9FBDE7),
    Category(Res.string.genre_hiphop, Res.string.genre_hiphop_description, "Hip hop hit songs", 0xFFD1ACDB),
    Category(Res.string.genre_lofi, Res.string.genre_lofi_description, "Lofi chill songs", 0xFF9AD0C9)
)

private fun moodPriority(title: String): Int {
    val name = title.lowercase(java.util.Locale.ROOT)
    return when {
        listOf("indian", "bollywood", "hindi", "punjabi", "tamil", "telugu").any(name::contains) -> 0
        listOf("pop", "rock", "hip", "indie", "lo-fi", "lofi", "chill").any(name::contains) -> 1
        else -> 2
    }
}

@Composable
fun DiscoverScreen() {
    val navigator = LocalNavigator.current
    val player = LocalPlayer.current
    val actions = songActions()
    val page = rememberPage("discover/page") { Innertube.discoverPage()?.getOrNull() }
    val catalogue = rememberPage("discover/moods") { Innertube.browse(BrowseBody(browseId = "FEmusic_moods_and_genres"))?.getOrNull() }
    val trending = page.value?.trending?.songs.orEmpty().mapNotNull { it.toTrack() }
    val moods = (page.value?.moods.orEmpty() + catalogue.value?.items.orEmpty().flatMap { it.items.filterIsInstance<Innertube.Mood.Item>() })
        .filter { !it.endpoint.browseId.isNullOrBlank() }
        .distinctBy { it.key }
        .sortedBy { moodPriority(it.title) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(stringResource(Res.string.discover_headline), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(Res.string.discover_description), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        when {
            trending.isNotEmpty() -> {
                Featured(trending.first().title, trending.first().artists, trending.first().thumbnailUrl) { player.play(trending, 0) }
                SectionHeader(
                    stringResource(Res.string.trending),
                    action = page.value?.trending?.endpoint?.browseId?.let { stringResource(Res.string.more) }
                ) { page.value?.trending?.endpoint?.browseId?.let { navigator.go(Screen.Playlist(it)) } }
                trending.drop(1).take(8).forEachIndexed { i, track ->
                    SongRow(track, onPlay = { player.play(trending, i + 1) }, actions = { actions(track) }, index = i)
                }
            }
            page.failed -> ErrorState(page.retry)
            else -> {
                Skeleton(Modifier.fillMaxWidth().height(220.dp).padding(top = 16.dp), 20.dp)
                Box(Modifier.padding(top = 16.dp)) { SongListSkeleton() }
            }
        }

        page.value?.newReleaseAlbums.orEmpty().mapNotNull { a -> a.info?.endpoint?.browseId?.let { a to it } }.takeIf { it.isNotEmpty() }?.let { albums ->
            SectionHeader(stringResource(Res.string.new_released_albums))
            Shelf {
                items(albums, key = { it.second }) { (album, id) ->
                    MediaCard(album.info?.name.orEmpty(), album.authors?.joinToString("") { it.name.orEmpty() }, album.thumbnail?.size(400), Modifier.width(164.dp)) {
                        navigator.go(Screen.Album(id))
                    }
                }
            }
        }

        if (moods.isNotEmpty()) {
            SectionHeader(stringResource(Res.string.pick_your_mood))
            TileGrid(moods.take(24)) { mood ->
                MoodTile(mood.title, Color(mood.stripeColor)) {
                    navigator.go(Screen.Mood(mood.endpoint.browseId!!, mood.endpoint.params, mood.title))
                }
            }
        }

        SectionHeader(stringResource(Res.string.discover_indian_title))
        TileGrid(indian) { c -> GenreTile(stringResource(c.title), stringResource(c.subtitle), Color(c.color)) { navigator.go(Screen.Search(c.query)) } }
        SectionHeader(stringResource(Res.string.discover_genres_title))
        TileGrid(global) { c -> GenreTile(stringResource(c.title), stringResource(c.subtitle), Color(c.color)) { navigator.go(Screen.Search(c.query)) } }
    }
}

/** A grid that wraps tiles to the window width. */
@Composable
private fun <T> TileGrid(items: List<T>, tile: @Composable (T) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / 220.dp).toInt().coerceIn(2, 6)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item -> Box(Modifier.weight(1f)) { tile(item) } }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Featured(title: String, subtitle: String?, artwork: String?, onPlay: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.08f))))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Artwork(artwork, 180.dp, Modifier.size(180.dp), corner = 14.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.trending).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onPlay, modifier = Modifier.padding(top = 8.dp)) {
                Icon(painterResource(Res.drawable.play), null, Modifier.size(18.dp))
                Text(stringResource(Res.string.play), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun MoodTile(title: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.width(6.dp).height(72.dp).background(color))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp, end = 12.dp)
        )
    }
}

@Composable
private fun GenreTile(title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f))))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF15131C))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF15131C).copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
