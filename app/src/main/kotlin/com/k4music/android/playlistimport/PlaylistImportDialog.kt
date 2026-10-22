package com.k4music.android.playlistimport

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import android.content.ClipboardManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.k4music.android.Database
import com.k4music.android.models.Playlist
import com.k4music.android.ui.components.themed.CircularProgressIndicator
import com.k4music.android.ui.components.themed.DefaultDialog
import com.k4music.android.ui.components.themed.DialogTextButton
import com.k4music.android.ui.components.themed.LinearProgressIndicator
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
        val retrying: Boolean = false,
        val error: String? = null
    ) : ImportState
}

/** Paste-a-link playlist import: reads, matches and saves the playlist, then reports exactly what happened. */
@Composable
fun PlaylistImportDialog(onDismiss: () -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var link by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<ImportState>(ImportState.Input) }
    var job by remember { mutableStateOf<Job?>(null) }
    var reviewIndex by remember { mutableStateOf<Int?>(null) }

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
        state = done.copy(retrying = true, error = null)
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
                done.copy(retrying = false, error = error.message ?: "Could not retry these tracks")
            }
        }
    }

    val source = importer.sourceFor(link)

    val done = state as? ImportState.Done
    val reviewed = reviewIndex?.let { done?.failed?.getOrNull(it) }
    if (reviewed != null && done != null && source != null) {
        ManualMatchDialog(reviewed, source, onDismiss = { reviewIndex = null }) { candidate ->
            val added = withContext(Dispatchers.IO) {
                Database.addToPlaylist(Playlist(id = done.playlistId, name = done.name), listOf(candidate.payload))
            }
            val remaining = done.failed.filterIndexed { index, _ -> index != reviewIndex }
            state = done.copy(imported = done.imported + added, duplicates = done.duplicates + (if (added == 0) 1 else 0),
                failed = remaining, canRetryFailed = remaining.isNotEmpty())
        }
    }

    DefaultDialog(
        onDismiss = { job?.cancel(); onDismiss() },
        horizontalAlignment = Alignment.Start,
        horizontalPadding = 20.dp
    ) {
        DialogHeader(
            icon = when (state) {
                is ImportState.Failure -> R.drawable.alert_circle
                is ImportState.Done -> R.drawable.sparkles
                else -> R.drawable.playlist
            },
            tint = if (state is ImportState.Failure) colorPalette.red else colorPalette.accent,
            title = stringResource(if (state is ImportState.Failure) R.string.import_error_title else R.string.import_title),
            subtitle = (state as? ImportState.Done)?.name ?: stringResource(R.string.import_hint)
        )

        when (val current = state) {
            ImportState.Input -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorPalette.background2)
                        .border(
                            width = 1.dp,
                            color = when {
                                link.isBlank() -> Color.Transparent
                                source == null -> colorPalette.red.copy(alpha = 0.6f)
                                else -> colorPalette.accent.copy(alpha = 0.6f)
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                        if (link.isEmpty()) BasicText(
                            text = "https://open.spotify.com/playlist/…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = typography.xs.medium.copy(color = colorPalette.textDisabled)
                        )
                        BasicTextField(
                            value = link,
                            onValueChange = { link = it },
                            singleLine = true,
                            textStyle = typography.xs.semiBold.copy(color = colorPalette.text),
                            cursorBrush = SolidColor(colorPalette.accent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (source != null) start() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (link.isBlank()) Pill(
                        text = stringResource(R.string.import_paste),
                        onClick = {
                            val clip = context.getSystemService(ClipboardManager::class.java)
                                ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
                            clip?.toString()?.trim()?.let { link = it }
                        }
                    ) else Image(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.import_clear),
                        colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { link = "" }
                            .padding(10.dp)
                            .size(16.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    importer.sourceNames.forEach { name ->
                        val detected = source?.name == name
                        BasicText(
                            text = if (detected) "✓ $name" else name,
                            style = typography.xxs.semiBold.copy(
                                color = if (detected) colorPalette.onAccent else colorPalette.textSecondary
                            ),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (detected) colorPalette.accent else colorPalette.background2)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                if (link.isNotBlank() && source == null) BasicText(
                    text = stringResource(R.string.import_link_unknown),
                    style = typography.xxs.medium.copy(color = colorPalette.red),
                    modifier = Modifier.padding(top = 8.dp)
                )

                DialogActions {
                    DialogTextButton(text = stringResource(R.string.cancel), onClick = onDismiss)
                    DialogTextButton(
                        text = stringResource(R.string.import_playlist),
                        primary = true,
                        enabled = source != null,
                        onClick = ::start
                    )
                }
            }

            is ImportState.Working -> {
                val progress = current.progress
                val total = progress?.total
                val fraction = if (progress != null && progress.phase != ImportPhase.Fetching && total != null && total > 0)
                    progress.done.toFloat() / total else null

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                ) {
                    if (fraction != null) BasicText(
                        text = "${(fraction * 100).toInt()}%",
                        style = typography.xxl.semiBold.copy(color = colorPalette.text)
                    ) else CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    LinearProgressIndicator(
                        progress = fraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .clip(CircleShape)
                    )
                    BasicText(
                        text = when {
                            progress == null -> stringResource(R.string.import_saving)
                            progress.phase == ImportPhase.Fetching -> stringResource(R.string.import_reading, progress.done)
                            else -> stringResource(R.string.import_matching, progress.done, progress.total ?: progress.done)
                        },
                        style = typography.xs.medium.center.copy(color = colorPalette.textSecondary),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                DialogActions {
                    DialogTextButton(text = stringResource(R.string.cancel), onClick = { job?.cancel(); onDismiss() })
                }
            }

            is ImportState.Failure -> {
                BasicText(
                    text = current.message,
                    style = typography.xs.medium.copy(color = colorPalette.text),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colorPalette.red.copy(alpha = 0.12f))
                        .padding(14.dp)
                )
                DialogActions {
                    DialogTextButton(text = stringResource(R.string.import_close), onClick = onDismiss)
                    DialogTextButton(
                        text = if (current.retryable) stringResource(R.string.retry) else stringResource(R.string.import_title),
                        primary = true,
                        onClick = if (current.retryable) ({ start() }) else ({ state = ImportState.Input })
                    )
                }
            }

            is ImportState.Done -> {
                current.error?.let { Note(it, warning = true) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colorPalette.accent.copy(alpha = 0.12f))
                        .padding(vertical = 16.dp)
                ) {
                    BasicText(
                        text = "${current.imported} / ${current.total}",
                        style = typography.xxl.semiBold.copy(color = colorPalette.accent)
                    )
                    BasicText(
                        text = stringResource(R.string.import_songs_added),
                        style = typography.xs.medium.copy(color = colorPalette.textSecondary),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (current.duplicates > 0) Note(stringResource(R.string.import_duplicates, current.duplicates))
                if (current.truncated) Note(stringResource(R.string.import_maybe_truncated))
                if (current.notExposed > 0) Note(stringResource(R.string.import_not_provided, current.notExposed))
                if (current.failed.isNotEmpty()) {
                    Note(stringResource(R.string.import_failed_header, current.failed.size), warning = true)
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colorPalette.background2)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        current.failed.forEachIndexed { index, failed ->
                            Column {
                                BasicText(
                                    text = listOf(failed.title, failed.artist).filter(String::isNotBlank).joinToString(" — "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = typography.xxs.semiBold.copy(color = colorPalette.text)
                                )
                                BasicText(
                                    text = failed.reason,
                                    style = typography.xxs.medium.copy(color = colorPalette.textSecondary)
                                )
                                if (current.canRetryFailed) DialogTextButton(
                                    text = stringResource(R.string.import_review), enabled = !current.retrying,
                                    onClick = { reviewIndex = index }
                                )
                            }
                        }
                    }
                }
                DialogActions {
                    if (current.canRetryFailed) DialogTextButton(
                        text = stringResource(if (current.retrying) R.string.import_retrying else R.string.import_retry_failed),
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
private fun ManualMatchDialog(track: FailedTrack, source: PlaylistSource, onDismiss: () -> Unit,
    onChoose: suspend (Candidate<androidx.media3.common.MediaItem>) -> Unit) {
    val (palette, typography) = LocalAppearance.current
    val scope = rememberCoroutineScope()
    var choices by remember(track) { mutableStateOf<List<Candidate<androidx.media3.common.MediaItem>>>(emptyList()) }
    var loading by remember(track) { mutableStateOf(true) }
    var saving by remember(track) { mutableStateOf(false) }
    var failure by remember(track) { mutableStateOf<String?>(null) }
    var attempt by remember(track) { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(track, attempt) {
        loading = true; failure = null
        try { choices = source.candidates(track) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { failure = error.message ?: "Could not load matches" }
        finally { loading = false }
    }
    DefaultDialog(onDismiss = { if (!saving) onDismiss() }, horizontalAlignment = Alignment.Start, horizontalPadding = 20.dp) {
        DialogHeader(R.drawable.search, palette.accent, stringResource(R.string.import_review_title), "${track.title} — ${track.artist}")
        Note(stringResource(R.string.import_review_hint))
        track.durationMs?.takeIf { it > 0 }?.let { Note(com.k4music.android.k4.formatDurationMs(it).orEmpty()) }
        if (loading || saving) CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(28.dp))
        failure?.let { Note(it, warning = true) }
        if (!loading && choices.isEmpty() && failure == null) Note(stringResource(R.string.import_review_empty))
        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            choices.forEach { candidate ->
                Column {
                    BasicText(candidate.title, style = typography.s.semiBold.copy(color = palette.text))
                    BasicText(listOfNotNull(candidate.artist, candidate.durationMs?.let { com.k4music.android.k4.formatDurationMs(it) }).joinToString(" • "),
                        style = typography.xs.medium.copy(color = palette.textSecondary))
                    DialogTextButton(stringResource(R.string.import_review_choose), enabled = !loading && !saving, onClick = {
                        saving = true
                        scope.launch {
                            try { onChoose(candidate); onDismiss() }
                            catch (error: CancellationException) { throw error }
                            catch (error: Exception) { failure = error.message ?: "Could not save this recording" }
                            finally { saving = false }
                        }
                    })
                }
            }
        }
        DialogActions {
            DialogTextButton(stringResource(R.string.retry), enabled = !loading && !saving, onClick = { attempt++ })
            DialogTextButton(stringResource(R.string.back), enabled = !saving, onClick = onDismiss)
        }
    }
}

@Composable
private fun DialogHeader(@DrawableRes icon: Int, tint: Color, title: String, subtitle: String) {
    val (colorPalette, typography) = LocalAppearance.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f))
                .padding(12.dp)
                .size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = title, style = typography.m.semiBold.copy(color = colorPalette.text))
            BasicText(
                text = subtitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = typography.xxs.medium.copy(color = colorPalette.textSecondary),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current
    BasicText(
        text = text,
        style = typography.xxs.semiBold.copy(color = colorPalette.accent),
        modifier = Modifier
            .clip(CircleShape)
            .background(colorPalette.accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun DialogActions(content: @Composable RowScope.() -> Unit) = Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    content = content
)

@Composable
private fun Note(text: String, warning: Boolean = false) {
    val (colorPalette, typography) = LocalAppearance.current
    BasicText(
        text = "• $text",
        style = typography.xxs.medium.copy(color = if (warning) colorPalette.red else colorPalette.textSecondary),
        modifier = Modifier.padding(top = 8.dp)
    )
}
