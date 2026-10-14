package com.k4music.android.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.k4music.android.Brand
import com.k4music.android.ui.components.themed.DefaultDialog
import com.k4music.android.ui.screens.Route
import com.k4music.android.utils.CrashGuard
import com.k4music.app.BuildConfig
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance
import kotlin.time.Duration

// Retain the class for workers already persisted by older installations.
// Cancel old update checks until a verified K4 Music release repository exists.
class VersionCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        fun upsert(context: Context, period: Duration?) = runCatching {
            WorkManager.getInstance(context).cancelAllWorkByTag("version_check_worker")
        }
    }
    override suspend fun doWork(): Result = Result.success()
}

@Route
@Composable
fun About() = SettingsCategoryScreen(
    title = stringResource(R.string.about),
    description = "${Brand.APP_NAME} · v${BuildConfig.VERSION_NAME}"
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val linkError = stringResource(R.string.link_unavailable)
    fun openLink(url: String) {
        runCatching { uriHandler.openUri(url) }.onFailure {
            Toast.makeText(context, linkError, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) { VersionCheckWorker.upsert(context.applicationContext, null) }
    val palette = LocalAppearance.current.colorPalette
    com.k4music.android.ui.components.GlassPanel(
        shape = MaterialTheme.shapes.large,
        elevation = 4.dp,
        backgroundAlpha = 0.52f,
        borderAlpha = 0.16f,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Brand.APP_NAME, style = MaterialTheme.typography.headlineLarge, color = palette.text)
            Text(Brand.CREATOR, style = MaterialTheme.typography.titleMedium, color = palette.accent)
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        }
    }
    SettingsGroup(title = stringResource(R.string.creator)) {
        SettingsEntry(
            title = stringResource(R.string.website),
            text = "kartiklangwan.vercel.app",
            onClick = { openLink(Brand.WEBSITE_URL) }
        )
        SettingsEntry(
            title = stringResource(R.string.instagram),
            text = "@${Brand.INSTAGRAM_HANDLE}",
            onClick = { openLink(Brand.INSTAGRAM_URL) }
        )
        SettingsEntry(
            title = "GitHub",
            text = "KartikLangwan/K4Music",
            onClick = { openLink(Brand.GITHUB_URL) }
        )
    }
    var crashLogOpened by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(title = stringResource(R.string.diagnostics)) {
        SettingsEntry(
            title = stringResource(R.string.crash_log),
            text = stringResource(R.string.crash_log_description),
            onClick = { crashLogOpened = true }
        )
    }
    SettingsGroup(
        title = stringResource(R.string.open_source_credits),
        description = stringResource(R.string.upstream_credits)
    ) {
        SettingsEntry(
            title = "ViTune",
            text = stringResource(R.string.view_source),
            onClick = { openLink("https://github.com/25huizengek1/ViTune") }
        )
    }
    if (crashLogOpened) {
        val emptyLog = stringResource(R.string.no_crashes_recorded)
        val log = remember(crashLogOpened) { CrashGuard.readLog(context.filesDir).ifBlank { emptyLog } }
        DefaultDialog(onDismiss = { crashLogOpened = false }) {
            Text(stringResource(R.string.crash_log), style = MaterialTheme.typography.titleMedium)
            SelectionContainer {
                BasicText(
                    text = log,
                    style = LocalAppearance.current.typography.xxs,
                    modifier = Modifier.padding(top = 12.dp).heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
