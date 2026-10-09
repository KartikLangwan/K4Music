package com.k4music.android.ui.components.themed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k4music.android.Database
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.android.service.DownloadProgress
import com.k4music.android.service.PrecacheService
import com.k4music.android.service.downloadProgress
import com.k4music.android.utils.asMediaItem
import com.k4music.android.utils.medium
import com.k4music.android.utils.semiBold
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance
import kotlinx.coroutines.Dispatchers

/** Songs being downloaded right now (with live percentage) and failed ones that can be retried. */
@Composable
fun ActiveDownloads(modifier: Modifier = Modifier) {
    val (colorPalette, typography) = LocalAppearance.current
    val downloads by downloadProgress.collectAsState()
    if (downloads.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        BasicText(
            text = stringResource(R.string.downloads_in_progress, downloads.values.count { !it.failed }),
            style = typography.xs.semiBold.copy(color = colorPalette.text),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        downloads.values.sortedBy { it.id }.forEach { ActiveDownloadRow(it) }
    }
}

@Composable
private fun ActiveDownloadRow(download: DownloadProgress) {
    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val song by remember(download.id) { Database.song(download.id) }.collectAsState(null, Dispatchers.IO)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = song?.title ?: download.id,
                    style = typography.xs.semiBold.copy(color = colorPalette.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BasicText(
                    text = if (download.failed) stringResource(R.string.download_failed)
                    else stringResource(R.string.download_percent, download.percent),
                    style = typography.xxs.medium.copy(
                        color = if (download.failed) colorPalette.red else colorPalette.textSecondary
                    )
                )
            }
            if (download.failed) song?.let { current ->
                SecondaryTextButton(
                    text = stringResource(R.string.retry),
                    onClick = { PrecacheService.scheduleCache(context.applicationContext, current.asMediaItem) }
                )
            }
            IconButton(
                icon = R.drawable.close,
                color = colorPalette.textSecondary,
                onClick = { PrecacheService.remove(context, binder?.cache, listOf(download.id)) },
                modifier = Modifier.padding(start = 4.dp).size(20.dp)
            )
        }
        if (!download.failed) LinearProgressIndicator(
            progress = download.percent / 100f,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}
