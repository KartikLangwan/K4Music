package com.k4music.android.ui.screens.settings

import com.k4music.android.ui.screens.audioRoute

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k4music.android.Brand
import com.k4music.android.LocalPlayerAwareWindowInsets
import com.k4music.android.ui.components.GlassPanel
import com.k4music.app.BuildConfig
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance

@Composable
fun SettingsDashboard(onCategory: (Int) -> Unit) {
    val palette = LocalAppearance.current.colorPalette
    val bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    // Tab categories open in place; Audio effects is its own screen.
    val categories = listOf<Pair<Triple<Int, Int, Int>, () -> Unit>>(
        Triple(R.string.appearance, R.string.settings_appearance_summary, R.drawable.color_palette) to { onCategory(1) },
        Triple(R.string.player, R.string.settings_playback_summary, R.drawable.play) to { onCategory(2) },
        Triple(R.string.audio_effects, R.string.audio_effects_settings_description, R.drawable.equalizer) to { audioRoute.global() },
        Triple(R.string.cache, R.string.settings_storage_summary, R.drawable.download) to { onCategory(3) },
        Triple(R.string.database, R.string.settings_library_summary, R.drawable.server) to { onCategory(4) },
        Triple(R.string.other, R.string.settings_system_summary, R.drawable.shapes) to { onCategory(5) },
        Triple(R.string.user_guide, R.string.settings_help_summary, R.drawable.help_outline) to { onCategory(6) },
        Triple(R.string.about, R.string.settings_about_summary, R.drawable.information) to { onCategory(7) }
    )
    LazyColumn(contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, bottom + 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "intro") {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_headline), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = palette.text)
                Text(stringResource(R.string.settings_description), style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            }
        }
        categories.forEach { (category, open) ->
            val (title, subtitle, icon) = category
            item(key = title) {
                Surface(onClick = open, shape = RoundedCornerShape(20.dp), color = palette.background1, contentColor = palette.text) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(shape = CircleShape, color = palette.accent.copy(alpha = .12f)) {
                            Icon(painterResource(icon), null, Modifier.padding(11.dp).size(22.dp), tint = palette.accent)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                        }
                        Icon(painterResource(R.drawable.chevron_forward), null, Modifier.size(18.dp), tint = palette.textSecondary)
                    }
                }
            }
        }
        item(key = "brand") {
            GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 0.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${Brand.APP_NAME} · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall, color = palette.text)
                    Text(Brand.CREATOR, style = MaterialTheme.typography.bodySmall, color = palette.accent)
                    Text("@${Brand.INSTAGRAM_HANDLE}", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
            }
        }
    }
}
