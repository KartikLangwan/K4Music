package com.k4music.android.ui.screens.settings

import com.k4music.android.ui.screens.audioRoute

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k4music.android.Brand
import com.k4music.android.LocalPlayerAwareWindowInsets
import com.k4music.android.ui.components.GlassPanel
import com.k4music.android.preferences.PlayerPreferences
import com.k4music.app.BuildConfig
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance

private val faq = listOf(
    R.string.faq_q_playstore to R.string.faq_a_playstore,
    R.string.faq_q_update to R.string.faq_a_update,
    R.string.faq_q_backup to R.string.faq_a_backup,
    R.string.faq_q_downloads to R.string.faq_a_downloads,
    R.string.faq_q_download_how to R.string.faq_a_download_how,
    R.string.faq_q_import to R.string.faq_a_import,
    R.string.faq_q_quality to R.string.faq_a_quality,
    R.string.faq_q_unplayable to R.string.faq_a_unplayable,
    R.string.faq_q_contact to R.string.faq_a_contact
)

private class Category(val title: Int, val subtitle: Int, val icon: Int, val open: () -> Unit)

@Composable
fun SettingsDashboard(onCategory: (Int) -> Unit) {
    val palette = LocalAppearance.current.colorPalette
    val bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    var openFaq by rememberSaveable { mutableIntStateOf(-1) }
    // Tab categories open in place; Audio effects is its own screen.
    val sections = listOf(
        R.string.settings_section_sound to listOf(
            Category(R.string.player, R.string.settings_playback_summary, R.drawable.play) { onCategory(2) },
            Category(R.string.audio_effects, R.string.audio_effects_settings_description, R.drawable.equalizer) { audioRoute.global() }
        ),
        R.string.settings_section_look to listOf(
            Category(R.string.appearance, R.string.settings_appearance_summary, R.drawable.color_palette) { onCategory(1) }
        ),
        R.string.settings_section_library to listOf(
            Category(R.string.settings_backup_title, R.string.settings_backup_summary, R.drawable.server) { onCategory(4) },
            Category(R.string.cache, R.string.settings_storage_summary, R.drawable.download) { onCategory(3) }
        ),
        R.string.settings_section_more to listOf(
            Category(R.string.other, R.string.settings_system_summary, R.drawable.shapes) { onCategory(5) },
            Category(R.string.user_guide, R.string.settings_help_summary, R.drawable.help_outline) { onCategory(6) },
            Category(R.string.about, R.string.settings_about_summary, R.drawable.information) { onCategory(7) }
        )
    )

    LazyColumn(
        contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, bottom + 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "hero") { Hero(onPlayer = { onCategory(2) }) }
        sections.forEach { (header, categories) ->
            item(key = header) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(header))
                    Surface(shape = RoundedCornerShape(20.dp), color = palette.background1, contentColor = palette.text) {
                        Column {
                            categories.forEachIndexed { index, category ->
                                if (index > 0) HorizontalDivider(
                                    modifier = Modifier.padding(start = 70.dp),
                                    color = palette.textDisabled.copy(alpha = 0.18f)
                                )
                                CategoryRow(category)
                            }
                        }
                    }
                }
            }
        }
        item(key = "faq-title") { SectionLabel(stringResource(R.string.faq_title)) }
        itemsIndexed(faq, key = { _, (q, _) -> "faq-$q" }) { index, (question, answer) ->
            val open = openFaq == index
            Surface(
                onClick = { openFaq = if (open) -1 else index },
                shape = RoundedCornerShape(18.dp),
                color = if (open) palette.accent.copy(alpha = 0.10f) else palette.background1,
                contentColor = palette.text,
                modifier = Modifier.animateContentSize()
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(question),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painterResource(if (open) R.drawable.chevron_up else R.drawable.chevron_down),
                            null,
                            Modifier.size(18.dp),
                            tint = if (open) palette.accent else palette.textSecondary
                        )
                    }
                    AnimatedVisibility(visible = open) {
                        Text(
                            stringResource(answer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
        item(key = "brand") {
            GlassPanel(Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(20.dp), elevation = 0.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Brand.CREATOR, style = MaterialTheme.typography.bodySmall, color = palette.accent)
                    Text("@${Brand.INSTAGRAM_HANDLE}", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.SemiBold,
    color = LocalAppearance.current.colorPalette.accent,
    modifier = Modifier.padding(start = 6.dp, top = 10.dp)
)

/** App identity plus the one playback switch people most need to see at a glance. */
@Composable
private fun Hero(onPlayer: () -> Unit) {
    val palette = LocalAppearance.current.colorPalette
    val fallbackOn = PlayerPreferences.smartFallback
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(palette.accent.copy(alpha = 0.38f), palette.accent.copy(alpha = 0.10f), palette.background1)
                )
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = palette.accent) {
                    Icon(
                        painterResource(R.drawable.musical_notes),
                        null,
                        Modifier.padding(10.dp).size(24.dp),
                        tint = palette.onAccent
                    )
                }
                Column {
                    Text(Brand.APP_NAME, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.text)
                    Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
            }
            Text(stringResource(R.string.settings_headline), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = palette.text, modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.settings_description), style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            Surface(
                onClick = onPlayer,
                shape = CircleShape,
                color = (if (fallbackOn) palette.accent else palette.textDisabled).copy(alpha = 0.16f),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (fallbackOn) Color(0xFF3DDC84) else palette.textDisabled))
                    Text(
                        stringResource(if (fallbackOn) R.string.settings_fallback_on else R.string.settings_fallback_off),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.text
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category) {
    val palette = LocalAppearance.current.colorPalette
    Surface(onClick = category.open, color = Color.Transparent, contentColor = palette.text) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = palette.accent.copy(alpha = .14f)) {
                Icon(painterResource(category.icon), null, Modifier.padding(9.dp).size(22.dp), tint = palette.accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(category.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(category.subtitle), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
            Icon(painterResource(R.drawable.chevron_forward), null, Modifier.size(18.dp), tint = palette.textSecondary)
        }
    }
}
