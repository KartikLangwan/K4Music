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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance
import coil3.compose.AsyncImage

@Composable
fun MusicArtwork(url: Any?, modifier: Modifier = Modifier, @DrawableRes icon: Int = R.drawable.musical_notes) {
    val palette = LocalAppearance.current.colorPalette
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(palette.background2), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(32.dp), tint = palette.accent.copy(alpha = .65f))
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun MusicSectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    val palette = LocalAppearance.current.colorPalette
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.text, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) {
            Text(action, color = palette.accent)
            Icon(painterResource(R.drawable.chevron_forward), null, Modifier.padding(start = 4.dp).size(14.dp), tint = palette.accent)
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
    GlassPanel(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), elevation = 2.dp, backgroundAlpha = .85f, borderAlpha = .12f) {
        Box(Modifier.fillMaxWidth().heightIn(min = 194.dp)) {
            AsyncImage(artwork, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().alpha(.20f))
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(palette.background1.copy(alpha = .96f), palette.background1.copy(alpha = .45f)))))
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.sparkles), null, Modifier.size(16.dp), tint = palette.accent)
                    Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = palette.accent)
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Button(onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent), shape = CircleShape) {
                        Icon(painterResource(actionIcon), null, Modifier.size(18.dp))
                        Text(action, Modifier.padding(start = 6.dp), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun MusicTile(title: String, subtitle: String, artwork: Any?, playing: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, enabled: Boolean = true) {
    val palette = LocalAppearance.current.colorPalette
    Column(
        Modifier.width(146.dp).clip(RoundedCornerShape(20.dp)).combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick).padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box {
            MusicArtwork(artwork, Modifier.size(136.dp).border(if (playing) 2.dp else 0.dp, if (playing) palette.accent else Color.Transparent, RoundedCornerShape(18.dp)))
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(32.dp).clip(CircleShape).background(palette.accent), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (playing) R.drawable.musical_notes else R.drawable.play), null, Modifier.size(16.dp), tint = palette.onAccent)
            }
        }
        Text(title, style = MaterialTheme.typography.titleSmall, color = palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MusicShortcut(title: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalAppearance.current.colorPalette
    GlassPanel(modifier, shape = RoundedCornerShape(18.dp), elevation = 0.dp, backgroundAlpha = .55f, borderAlpha = .09f) {
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = palette.accent)
            Text(title, style = MaterialTheme.typography.labelMedium, color = palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BrowseCategoryCard(title: String, subtitle: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalAppearance.current.colorPalette
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(color.copy(alpha = .20f), palette.background1))).border(.75.dp, color.copy(alpha = .30f), RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick).heightIn(min = 104.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(painterResource(R.drawable.musical_notes), null, Modifier.size(18.dp), tint = color)
            Icon(painterResource(R.drawable.chevron_forward), null, Modifier.size(14.dp), tint = palette.textSecondary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.text, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MusicEmptyState(title: String, message: String, @DrawableRes icon: Int = R.drawable.musical_notes, action: String? = null, onAction: () -> Unit = {}) {
    val palette = LocalAppearance.current.colorPalette
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), null, Modifier.size(40.dp), tint = palette.accent)
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.text)
        Text(message, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) TextButton(onClick = onAction) { Text(action, color = palette.accent) }
    }
}
