package com.k4music.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.chevron_down
import com.k4music.desktop.res.desktop_close_player
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Full-window player: large artwork over the living backdrop, with the queue or lyrics beside it. */
@Composable
fun NowPlaying(panel: SidePanel?, onClose: () -> Unit) {
    val player = LocalPlayer.current
    val state by player.state.collectAsState()
    val track = state.current
    val colors = MaterialTheme.colorScheme

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .livingBackdrop(colors.primary, state.playing)
            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to colors.background.copy(alpha = 0.85f)))
    ) {
        IconButton(onClick = onClose, modifier = Modifier.padding(16.dp).align(Alignment.TopStart)) {
            Icon(painterResource(Res.drawable.chevron_down), stringResource(Res.string.desktop_close_player))
        }
        Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 24.dp, top = 56.dp, bottom = 24.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Artwork(
                    track?.thumbnailUrl,
                    420.dp,
                    Modifier.widthIn(max = 460.dp).fillMaxWidth(0.8f).aspectRatio(1f),
                    corner = 20.dp
                )
                Text(
                    track?.title.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 28.dp)
                )
                Text(
                    track?.artists.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )
                SeekBar(player, state.durationSeconds, enabled = track != null, modifier = Modifier.widthIn(max = 560.dp))
                Transport(player, state.playing, state.buffering, enabled = track != null, large = true)
                val stats by androidx.compose.runtime.produceState<String?>(null, state.current?.id, state.buffering) {
                    while (true) { value = player.stats(); kotlinx.coroutines.delay(2_000) }
                }
                Text(
                    listOfNotNull(state.source?.label(), stats).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            if (panel != null) Box(Modifier.widthIn(min = 340.dp, max = 420.dp).fillMaxHeight()) {
                when (panel) {
                    SidePanel.Queue -> QueuePanel()
                    SidePanel.Lyrics -> LyricsPanel()
                }
            }
        }
    }
}

/**
 * The Android app's living backdrop: two soft glows in the accent (and a hue-shifted partner)
 * that drift while music plays and settle when paused. Drawn in the draw phase only.
 */
@Composable
fun Modifier.livingBackdrop(accent: Color, playing: Boolean): Modifier {
    val partner = remember(accent) {
        val hsb = java.awt.Color.RGBtoHSB(
            (accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt(), null
        )
        Color(java.awt.Color.HSBtoRGB((hsb[0] + 48f / 360f) % 1f, hsb[1], hsb[2]))
    }
    val phase = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (true) phase.animateTo(phase.value + 2f * PI.toFloat(), tween(18_000, easing = LinearEasing))
    }
    val energy by animateFloatAsState(if (playing) 1f else 0f, tween(1_200), label = "backdropEnergy")
    return drawBehind {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawBehind
        val a = phase.value
        val glow = 0.16f + 0.10f * energy
        fun glowAt(x: Float, y: Float, radius: Float, color: Color) = drawCircle(
            brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x, y), radius = radius),
            radius = radius,
            center = Offset(x, y)
        )
        glowAt(w * (0.28f + 0.14f * cos(a)), h * (0.20f + 0.08f * sin(a)), w * 0.6f, accent.copy(alpha = glow))
        glowAt(w * (0.75f + 0.12f * sin(a * 0.7f + 1f)), h * (0.55f + 0.10f * cos(a * 1.3f)), w * 0.5f, partner.copy(alpha = glow * 0.75f))
    }
}

private fun com.k4music.desktop.player.StreamSource.label() = when (this) {
    com.k4music.desktop.player.StreamSource.Local -> "On this PC"
    com.k4music.desktop.player.StreamSource.Direct, com.k4music.desktop.player.StreamSource.YtDlp -> "YouTube Music"
    com.k4music.desktop.player.StreamSource.Saavn -> "JioSaavn"
}
