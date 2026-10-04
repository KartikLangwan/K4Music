package com.k4music.android.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.k4music.app.R
import com.k4music.android.ui.screens.Route
import com.k4music.android.utils.bold
import com.k4music.android.utils.secondary
import com.k4music.android.utils.semiBold
import com.k4music.core.ui.LocalAppearance

@Route
@Composable
fun UserGuide() {
    val (colorPalette, typography) = LocalAppearance.current

    SettingsCategoryScreen(
        title = stringResource(R.string.user_guide),
        description = stringResource(R.string.user_guide_description)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Intro Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colorPalette.background2,
                                colorPalette.accent.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
                                colorPalette.accent.copy(alpha = 0.5f)
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.20f))
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.sparkles),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        BasicText(
                            text = "Welcome to K4 Music",
                            style = typography.m.bold.copy(color = Color.White)
                        )
                    }
                    BasicText(
                        text = "A modern, ultra-fast music experience with synchronized lyrics, advanced audio DSP, lossless offline caching, and customizable glassmorphic themes.",
                        style = typography.xs.copy(color = Color.White.copy(alpha = 0.90f))
                    )
                }
            }

            // Section 1: Gestures & Navigation
            GuideSection(
                title = "Gestures & Navigation",
                icon = R.drawable.shapes,
                items = listOf(
                    GuideItem(
                        icon = R.drawable.chevron_down,
                        title = "Collapse Player",
                        subtitle = "Swipe down on the now playing screen or tap the top-left chevron to minimize into the floating mini-player."
                    ),
                    GuideItem(
                        icon = R.drawable.list,
                        title = "Queue Reordering",
                        subtitle = "Open Up Next and drag tracks using the handle on the right. Swipe left or right to remove tracks."
                    ),
                    GuideItem(
                        icon = R.drawable.ellipsis_horizontal,
                        title = "Quick Song Actions",
                        subtitle = "Long-press any song across the app to open the quick action menu (Start Radio, Play Next, Share, Artist)."
                    ),
                    GuideItem(
                        icon = R.drawable.expand,
                        title = "Pinch to Picture-in-Picture",
                        subtitle = "Pinch outward on the album artwork to enter floating Picture-in-Picture mode."
                    )
                )
            )

            // Section 2: Audio & Sound Engine
            GuideSection(
                title = "Audio Engine & Controls",
                icon = R.drawable.speed,
                items = listOf(
                    GuideItem(
                        icon = R.drawable.speed,
                        title = "Speed & Pitch Sliders",
                        subtitle = "Tap the speed icon in the bottom player controls to adjust playback tempo (0.25x to 2x) and pitch in real-time."
                    ),
                    GuideItem(
                        icon = R.drawable.volume_up,
                        title = "Volume Boost & Normalization",
                        subtitle = "Enable Volume Normalization in Player Settings to balance quiet and loud tracks automatically."
                    ),
                    GuideItem(
                        icon = R.drawable.shuffle,
                        title = "Instant Queue Shuffle",
                        subtitle = "Tap the shuffle button in the player controls to randomize upcoming tracks without interrupting the active song."
                    ),
                    GuideItem(
                        icon = R.drawable.infinite,
                        title = "Loop Modes",
                        subtitle = "Toggle repeat single track or loop queue from the bottom control row."
                    )
                )
            )

            // Section 3: Lyrics & Karaoke
            GuideSection(
                title = "Synchronized Karaoke Lyrics",
                icon = R.drawable.musical_notes,
                items = listOf(
                    GuideItem(
                        icon = R.drawable.musical_notes,
                        title = "Live Scrolling Lyrics",
                        subtitle = "Tap the musical note icon on the player screen to reveal synchronized lyrics that follow along with the vocals."
                    ),
                    GuideItem(
                        icon = R.drawable.play,
                        title = "Tap to Seek",
                        subtitle = "Tap any lyric line to jump playback directly to that exact second in the song."
                    )
                )
            )

            // Section 4: Themes & Customization
            GuideSection(
                title = "Themes & Glassmorphism",
                icon = R.drawable.color_palette,
                items = listOf(
                    GuideItem(
                        icon = R.drawable.color_palette,
                        title = "Theme Color Accents",
                        subtitle = "Choose from 8 curated accent colors (Violet, Rose, Amber, Emerald, Cyan, Blue, Pink, Amethyst) in Appearance Settings."
                    ),
                    GuideItem(
                        icon = R.drawable.disc,
                        title = "AMOLED Pure Black",
                        subtitle = "Select AMOLED darkness for 100% true black backgrounds, reducing battery consumption on OLED displays."
                    ),
                    GuideItem(
                        icon = R.drawable.sparkles,
                        title = "Dynamic Artwork Swatches",
                        subtitle = "Set Color Source to Dynamic to automatically adapt the UI theme to each song's album artwork."
                    )
                )
            )

            // Section 5: Offline & Storage
            GuideSection(
                title = "Offline & Cache",
                icon = R.drawable.download,
                items = listOf(
                    GuideItem(
                        icon = R.drawable.server,
                        title = "Automatic Caching",
                        subtitle = "Played songs are saved to local cache for instant zero-data playback when offline."
                    ),
                    GuideItem(
                        icon = R.drawable.library,
                        title = "Device Local Music",
                        subtitle = "Browse audio files stored on your phone storage in the Local tab on the Home screen."
                    )
                )
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class GuideItem(
    @DrawableRes val icon: Int,
    val title: String,
    val subtitle: String
)

@Composable
private fun GuideSection(
    title: String,
    @DrawableRes icon: Int,
    items: List<GuideItem>,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val sectionShape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(sectionShape)
            .background(colorPalette.background1.copy(alpha = 0.70f))
            .border(1.dp, colorPalette.text.copy(alpha = 0.08f), sectionShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colorPalette.accent.copy(alpha = 0.15f))
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colorPalette.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            BasicText(
                text = title,
                style = typography.s.bold.copy(color = colorPalette.text)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colorPalette.text.copy(alpha = 0.07f))
                    ) {
                        Icon(
                            painter = painterResource(item.icon),
                            contentDescription = null,
                            tint = colorPalette.accent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = item.title,
                            style = typography.xs.semiBold.copy(color = colorPalette.text)
                        )
                        BasicText(
                            text = item.subtitle,
                            style = typography.xxs.secondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
