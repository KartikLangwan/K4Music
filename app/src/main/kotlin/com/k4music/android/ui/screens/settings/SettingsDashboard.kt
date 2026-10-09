package com.k4music.android.ui.screens.settings

import com.k4music.android.ui.screens.audioRoute

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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

@Composable
fun SettingsDashboard(onCategory: (Int) -> Unit) {
    val palette = LocalAppearance.current.colorPalette
    val bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    var openFaq by rememberSaveable { mutableIntStateOf(-1) }
    // Tab categories open in place; Audio effects is its own screen.
    val categories = listOf<Pair<Triple<Int, Int, Int>, () -> Unit>>(
        Triple(R.string.settings_backup_title, R.string.settings_backup_summary, R.drawable.server) to { onCategory(4) },
        Triple(R.string.appearance, R.string.settings_appearance_summary, R.drawable.color_palette) to { onCategory(1) },
        Triple(R.string.player, R.string.settings_playback_summary, R.drawable.play) to { onCategory(2) },
        Triple(R.string.audio_effects, R.string.audio_effects_settings_description, R.drawable.equalizer) to { audioRoute.global() },
        Triple(R.string.cache, R.string.settings_storage_summary, R.drawable.download) to { onCategory(3) },
        Triple(R.string.other, R.string.settings_system_summary, R.drawable.shapes) to { onCategory(5) },
        Triple(R.string.user_guide, R.string.settings_help_summary, R.drawable.help_outline) to { onCategory(6) },
        Triple(R.string.about, R.string.settings_about_summary, R.drawable.information) to { onCategory(7) }
    )
    LazyColumn(contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, bottom + 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "intro") {
            Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_headline), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = palette.text)
                Text(stringResource(R.string.settings_description), style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            }
        }

        item(key = "faq-title") {
            SectionTitle(stringResource(R.string.faq_title))
        }
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

        item(key = "settings-title") {
            SectionTitle(stringResource(R.string.settings))
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
            GlassPanel(Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(20.dp), elevation = 0.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${Brand.APP_NAME} · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall, color = palette.text)
                    Text(Brand.CREATOR, style = MaterialTheme.typography.bodySmall, color = palette.accent)
                    Text("@${Brand.INSTAGRAM_HANDLE}", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = LocalAppearance.current.colorPalette.accent,
    modifier = Modifier.padding(top = 10.dp, start = 4.dp)
)
