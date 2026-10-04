package com.k4music.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance

private val ArtworkShape = RoundedCornerShape(12.dp)

@Composable
fun MusicArtwork(
    url: Any?,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.musical_notes,
    shape: androidx.compose.ui.graphics.Shape = ArtworkShape
) {
    val palette = LocalAppearance.current.colorPalette
    Box(modifier.clip(shape).background(palette.background2), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(28.dp), tint = palette.textDisabled)
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun MusicSectionTitle(
    title: String,
    action: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onAction: () -> Unit = {}
) {
    val palette = LocalAppearance.current.colorPalette
    Row(
        modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (action != null) TextButton(onClick = onAction) {
            Text(action, color = palette.textSecondary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun FeaturedMusicCard(
    eyebrow: String, title: String, subtitle: String, artwork: Any?, action: String,
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    @DrawableRes actionIcon: Int = R.drawable.play
) {
    val palette = LocalAppearance.current.colorPalette
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.background1)
            .background(Brush.horizontalGradient(listOf(palette.accent.copy(alpha = 0.22f), Color.Transparent)))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = palette.accent, fontWeight = FontWeight.SemiBold)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                shape = CircleShape
            ) {
                Icon(painterResource(actionIcon), null, Modifier.size(18.dp))
                Text(action, Modifier.padding(start = 6.dp), maxLines = 1)
            }
        }
        if (artwork != null) MusicArtwork(artwork, Modifier.size(112.dp), shape = RoundedCornerShape(16.dp))
    }
}

/** A square artwork card for horizontal shelves; the playing song is outlined in the accent. */
@Composable
fun MusicTile(
    title: String,
    subtitle: String,
    artwork: Any?,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 140.dp
) {
    val palette = LocalAppearance.current.colorPalette
    Column(
        Modifier
            .width(size)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box {
            MusicArtwork(
                artwork,
                Modifier
                    .size(size)
                    .border(if (playing) 2.dp else 0.dp, if (playing) palette.accent else Color.Transparent, ArtworkShape)
            )
            if (playing) Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                MusicBars(color = palette.onAccent, modifier = Modifier.height(12.dp))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (playing) palette.accent else palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (subtitle.isNotBlank()) Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A compact quick-access tile for the Home grid. */
@Composable
fun MusicShortcut(title: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalAppearance.current.colorPalette
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.background1)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), null, Modifier.size(18.dp), tint = palette.accent)
        }
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BrowseCategoryCard(title: String, subtitle: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalAppearance.current.colorPalette
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.background1)
            .background(Brush.linearGradient(listOf(color.copy(alpha = .38f), color.copy(alpha = .06f))))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 92.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.text, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MusicEmptyState(title: String, message: String, @DrawableRes icon: Int = R.drawable.musical_notes, action: String? = null, onAction: () -> Unit = {}) {
    val palette = LocalAppearance.current.colorPalette
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(palette.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), null, Modifier.size(28.dp), tint = palette.accent)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, textAlign = TextAlign.Center)
        if (action != null) Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
            shape = CircleShape,
            modifier = Modifier.padding(top = 6.dp)
        ) {
            Text(action)
        }
    }
}

/** Placeholder for a horizontal shelf of [MusicTile]s while it loads. */
@Composable
fun ShelfSkeleton(modifier: Modifier = Modifier, count: Int = 3, size: Dp = 140.dp) {
    val palette = LocalAppearance.current.colorPalette
    ShimmerHost(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(count) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(size).clip(ArtworkShape).background(palette.background2))
                    Box(Modifier.width(size * 0.8f).height(12.dp).clip(CircleShape).background(palette.background2))
                    Box(Modifier.width(size * 0.5f).height(10.dp).clip(CircleShape).background(palette.background2))
                }
            }
        }
    }
}
