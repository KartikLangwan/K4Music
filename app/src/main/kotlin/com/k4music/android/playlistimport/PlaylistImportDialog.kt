package com.k4music.android.playlistimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.k4music.android.Database
import com.k4music.android.models.Playlist
import com.k4music.android.ui.components.themed.CircularProgressIndicator
import com.k4music.android.ui.components.themed.DefaultDialog
import com.k4music.android.ui.components.themed.DialogTextButton
import com.k4music.android.ui.components.themed.TextField
import com.k4music.android.utils.center
import com.k4music.android.utils.medium
import com.k4music.android.utils.semiBold
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val importer = PlaylistImporter(listOf(YouTubeMusicSource(), SpotifySource()))

private sealed interface ImportState {
    data object Input : ImportState
    data class Working(val progress: ImportProgress?) : ImportState
    data class Failure(val message: String, val retryable: Boolean) : ImportState
    data class Done(
        val name: String,
        val playlistId: Long,
        val imported: Int,
        val total: Int,
        val duplicates: Int,
        val notExposed: Int,
        val truncated: Boolean,
        val failed: List<FailedTrack>,
        val canRetryFailed: Boolean,
        val retrying: Boolean = false
    ) : ImportState
}

/** Paste-a-link playlist import: reads, matches and saves the playlist, then reports exactly what happened. */
@Composable
fun PlaylistImportDialog(onDismiss: () -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current
    val scope = rememberCoroutineScope()
    var link by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<ImportState>(ImportState.Input) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun start() {
        if (job?.isActive == true || link.isBlank()) return
        state = ImportState.Working(null)
        job = scope.launch {
            state = try {
                val plan = importer.prepare(link) { state = ImportState.Working(it) }
                val fetched = plan.fetched
                state = ImportState.Working(null)
                // One transaction: a failure here leaves the library exactly as it was.
                val (id, added) = withContext(Dispatchers.IO) {
                    Database.createPlaylist(
                        Playlist(name = fetched.name, browseId = fetched.browseId, thumbnail = fetched.thumbnail),
                        fetched.tracks.map { it.mediaItem }
                    )
                }
                ImportState.Done(
                    name = fetched.name,
                    playlistId = id,
                    imported = added,
                    total = fetched.total ?: (fetched.tracks.size + fetched.failed.size),
                    duplicates = plan.duplicates,
                    notExposed = fetched.notExposed,
                    truncated = fetched.truncated,
                    failed = fetched.failed,
                    canRetryFailed = importer.sourceFor(link)?.canRetryFailed == true && fetched.failed.isNotEmpty()
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: PlaylistImportException) {
                ImportState.Failure(error.message.orEmpty(), retryable = error.error == ImportError.Network)
            } catch (error: Exception) {
                ImportState.Failure(error.message ?: "Unexpected error while importing.", retryable = true)
            }
        }
    }

    fun retryFailed(done: ImportState.Done) {
        if (job?.isActive == true) return
        val source = importer.sourceFor(link) ?: return
        state = done.copy(retrying = true)
        job = scope.launch {
            state = try {
                val (tracks, stillFailed) = source.retry(done.failed) { }
                val added = withContext(Dispatchers.IO) {
                    Database.addToPlaylist(
                        Playlist(id = done.playlistId, name = done.name),
                        tracks.distinctBy { it.key }.map { it.mediaItem }
                    )
                }
                done.copy(
                    imported = done.imported + added,
                    failed = stillFailed,
                    canRetryFailed = stillFailed.isNotEmpty(),
                    retrying = false
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                done.copy(retrying = false)
            }
        }
    }

    DefaultDialog(onDismiss = { job?.cancel(); onDismiss() }) {
        BasicText(
            text = stringResource(R.string.import_title),
            style = typography.s.semiBold.copy(color = colorPalette.text),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        when (val current = state) {
            ImportState.Input -> {
                TextField(
                    value = link,
                    onValueChange = { link = it },
                    textStyle = typography.xs.semiBold,
                    singleLine = true,
                    maxLines = 1,
                    hintText = stringResource(R.string.import_hint),
                    keyboardActions = KeyboardActions(onDone = { start() }),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    DialogTextButton(text = stringResource(R.string.cancel), onClick = onDismiss)
                    DialogTextButton(
                        text = stringResource(R.string.import_playlist),
                        primary = true,
                        enabled = link.isNotBlank(),
                        onClick = ::start
                    )
                }
            }

            is ImportState.Working -> {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                val progress = current.progress
                BasicText(
                    text = when {
                        progress == null -> stringResource(R.string.import_saving)
                        progress.phase == ImportPhase.Fetching -> stringResource(R.string.import_reading, progress.done)
                        else -> stringResource(R.string.import_matching, progress.done, progress.total ?: progress.done)
                    },
                    style = typography.xs.medium.center.copy(color = colorPalette.textSecondary),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                DialogTextButton(text = stringResource(R.string.cancel), onClick = { job?.cancel(); onDismiss() })
            }

            is ImportState.Failure -> {
                BasicText(
                    text = stringResource(R.string.import_error_title),
                    style = typography.xs.semiBold.copy(color = colorPalette.red)
                )
                BasicText(
                    text = current.message,
                    style = typography.xs.medium.center.copy(color = colorPalette.text),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    DialogTextButton(text = stringResource(R.string.import_close), onClick = onDismiss)
                    DialogTextButton(
                        text = if (current.retryable) stringResource(R.string.retry) else stringResource(R.string.import_title),
                        primary = true,
                        onClick = if (current.retryable) ({ start() }) else ({ state = ImportState.Input })
                    )
                }
            }

            is ImportState.Done -> {
                BasicText(
                    text = current.name,
                    style = typography.xs.medium.center.copy(color = colorPalette.textSecondary)
                )
                BasicText(
                    text = stringResource(R.string.import_result_count, current.imported, current.total),
                    style = typography.m.semiBold.copy(color = colorPalette.text),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                if (current.duplicates > 0) Note(stringResource(R.string.import_duplicates, current.duplicates))
                if (current.truncated) Note(stringResource(R.string.import_maybe_truncated))
                if (current.notExposed > 0) Note(stringResource(R.string.import_not_provided, current.notExposed))
                if (current.failed.isNotEmpty()) {
                    Note(stringResource(R.string.import_failed_header, current.failed.size))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        current.failed.forEach {
                            val name = listOf(it.title, it.artist).filter(String::isNotBlank).joinToString(" — ")
                            BasicText(
                                text = name + "\n" + it.reason,
                                style = typography.xxs.medium.copy(color = colorPalette.textSecondary)
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    if (current.canRetryFailed) DialogTextButton(
                        text = stringResource(R.string.import_retry_failed),
                        enabled = !current.retrying,
                        onClick = { retryFailed(current) }
                    )
                    DialogTextButton(text = stringResource(R.string.done), primary = true, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    val (colorPalette, typography) = LocalAppearance.current
    BasicText(
        text = text,
        style = typography.xxs.medium.center.copy(color = colorPalette.textSecondary),
        modifier = Modifier.padding(top = 2.dp)
    )
}
