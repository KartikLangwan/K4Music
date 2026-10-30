package com.k4music.desktop.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.k4music.android.models.Playlist
import com.k4music.android.playlistimport.ImportPhase
import com.k4music.android.playlistimport.ImportPlan
import com.k4music.android.playlistimport.PlaylistImporter
import com.k4music.android.playlistimport.SpotifySource
import com.k4music.android.playlistimport.YouTubeMusicSource
import com.k4music.core.data.enums.PlaylistSortBy
import com.k4music.core.data.enums.SortOrder
import com.k4music.desktop.data.Db
import com.k4music.desktop.player.Track
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.desktop_scan_found
import com.k4music.desktop.res.add_to_playlist
import com.k4music.desktop.res.cancel
import com.k4music.desktop.res.confirm
import com.k4music.desktop.res.delete
import com.k4music.desktop.res.desktop_song_count
import com.k4music.desktop.res.search
import com.k4music.desktop.res.import_close
import com.k4music.desktop.res.import_duplicates
import com.k4music.desktop.res.import_error_title
import com.k4music.desktop.res.import_failed_header
import com.k4music.desktop.res.import_hint
import com.k4music.desktop.res.import_matching
import com.k4music.desktop.res.import_playlist
import com.k4music.desktop.res.import_reading
import com.k4music.desktop.res.import_result_count
import com.k4music.desktop.res.import_saving
import com.k4music.desktop.res.import_not_provided
import com.k4music.desktop.res.import_maybe_truncated
import com.k4music.desktop.res.import_retry_failed
import com.k4music.desktop.res.import_title
import com.k4music.desktop.res.import_review
import com.k4music.desktop.res.import_review_title
import com.k4music.desktop.res.import_review_hint
import com.k4music.desktop.res.import_review_choose
import com.k4music.desktop.res.import_review_empty
import com.k4music.desktop.res.retry
import com.k4music.desktop.res.back
import com.k4music.desktop.res.new_playlist
import com.k4music.desktop.res.rename
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.k4music.desktop.res.desktop_update_title
import com.k4music.desktop.res.desktop_update_body
import com.k4music.desktop.res.desktop_update_starting
import com.k4music.desktop.res.desktop_update_downloading
import com.k4music.desktop.res.desktop_update_error
import com.k4music.desktop.res.desktop_update_now
import com.k4music.desktop.res.desktop_update_website
import com.k4music.desktop.res.desktop_update_later
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** App-wide dialogs and short messages, opened from anywhere and shown by [DialogHost]. */
object AppDialogs {
    sealed interface Dialog {
        // Runs in DialogHost's scope: the screen or dialog that opened this one may be gone by then.
        class Text(val title: String?, val initial: String, val multiline: Boolean = false, val onConfirm: suspend (String) -> Unit) : Dialog
        class Confirm(val subject: String, val title: String?, val onYes: () -> Unit) : Dialog
        class AddToPlaylist(val tracks: List<Track>) : Dialog
        data object Import : Dialog
        class Rename(val playlist: Playlist) : Dialog
        data object NewPlaylist : Dialog
        class Update(val update: com.k4music.desktop.Updater.Update) : Dialog
        data object CommandPalette : Dialog
    }

    var current by mutableStateOf<Dialog?>(null)
    var toast by mutableStateOf<String?>(null)

    fun openCommandPalette() { current = Dialog.CommandPalette }

    fun newPlaylist() { current = Dialog.NewPlaylist }
    fun renamePlaylist(playlist: Playlist) { current = Dialog.Rename(playlist) }
    fun confirm(subject: String, title: String? = null, onYes: () -> Unit) { current = Dialog.Confirm(subject, title, onYes) }
    fun addToPlaylist(tracks: List<Track>) { current = Dialog.AddToPlaylist(tracks) }
    fun importPlaylist() { current = Dialog.Import }
    fun show(message: String) { toast = message }

    /** Scans the music folders and says how many files were found (a scan already running is left alone). */
    suspend fun scanLocalMusic() {
        if (com.k4music.desktop.data.LocalMusic.scanning.value) return
        val found = com.k4music.desktop.data.LocalMusic.scan()
        show(org.jetbrains.compose.resources.getPluralString(Res.plurals.desktop_scan_found, found, found))
    }

    /** Native Windows folder picker (runs on the AWT thread, never blocks the UI). */
    fun pickFolder(onPicked: (File) -> Unit) = javax.swing.SwingUtilities.invokeLater {
        runCatching { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()) }
        val chooser = javax.swing.JFileChooser().apply { fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY }
        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) onPicked(chooser.selectedFile)
    }
}

@Composable
fun DialogHost() {
    val scope = rememberCoroutineScope()
    when (val dialog = AppDialogs.current) {
        null -> Unit
        is AppDialogs.Dialog.Text -> TextDialog(dialog.title.orEmpty(), dialog.initial, dialog.multiline) { scope.launch { dialog.onConfirm(it) } }
        is AppDialogs.Dialog.NewPlaylist -> TextDialog(stringResource(Res.string.new_playlist), "") { name ->
            scope.launch { Db.library.insert(Playlist(name = name.trim())) }
        }
        is AppDialogs.Dialog.Rename -> TextDialog(stringResource(Res.string.rename), dialog.playlist.name) { name ->
            scope.launch { Db.library.update(dialog.playlist.copy(name = name.trim())) }
        }
        is AppDialogs.Dialog.Confirm -> AlertDialog(
            onDismissRequest = { AppDialogs.current = null },
            title = { Text(dialog.title ?: stringResource(Res.string.delete)) },
            text = { Text(dialog.subject) },
            confirmButton = { TextButton(onClick = { AppDialogs.current = null; dialog.onYes() }) { Text(stringResource(Res.string.confirm)) } },
            dismissButton = { TextButton(onClick = { AppDialogs.current = null }) { Text(stringResource(Res.string.cancel)) } }
        )
        is AppDialogs.Dialog.AddToPlaylist -> AddToPlaylistDialog(dialog.tracks)
        is AppDialogs.Dialog.Import -> ImportDialog()
        is AppDialogs.Dialog.Update -> UpdateDialog(dialog.update)
        is AppDialogs.Dialog.CommandPalette -> CommandPaletteDialog { AppDialogs.current = null }
    }
    // Short confirmation message at the bottom of the window.
    AppDialogs.toast?.let { message ->
        LaunchedEffect(message) { delay(2_500); AppDialogs.toast = null }
        Box(Modifier.fillMaxSize().padding(bottom = 110.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                Text(message, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun TextDialog(title: String, initial: String, multiline: Boolean = false, onConfirm: (String) -> Unit) {
    // A one-line value (a name to rename) starts selected, so typing replaces it.
    var value by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial, androidx.compose.ui.text.TextRange(if (multiline) initial.length else 0, initial.length)))
    }
    val text = value.text
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun confirm() { if (text.isNotBlank()) { AppDialogs.current = null; onConfirm(text) } }
    AlertDialog(
        onDismissRequest = { AppDialogs.current = null },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value, { value = it }, singleLine = !multiline, minLines = if (multiline) 10 else 1, maxLines = if (multiline) 18 else 1,
                // Enter confirms a name; in the multi-line lyrics editor it must stay a new line.
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (!multiline) confirm() }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = if (multiline) androidx.compose.ui.text.input.ImeAction.Default else androidx.compose.ui.text.input.ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = ::confirm) { Text(stringResource(Res.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = { AppDialogs.current = null }) { Text(stringResource(Res.string.cancel)) } }
    )
}

@Composable
private fun AddToPlaylistDialog(tracks: List<Track>) {
    val scope = rememberCoroutineScope()
    val newPlaylistTitle = stringResource(Res.string.new_playlist)
    val playlists by remember { Db.library.playlistPreviews(PlaylistSortBy.Name, SortOrder.Ascending) }.collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = { AppDialogs.current = null },
        title = { Text(stringResource(Res.string.add_to_playlist)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    Text(
                        "+ " + stringResource(Res.string.new_playlist),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable {
                            AppDialogs.current = AppDialogs.Dialog.Text(newPlaylistTitle, "") { name ->
                                Db.library.addToPlaylist(Playlist(name = name.trim()), tracks); AppDialogs.show(name.trim())
                            }
                        }.padding(12.dp)
                    )
                }
                items(playlists, key = { it.id }) { p ->
                    Column(Modifier.fillMaxWidth().clickable {
                        AppDialogs.current = null
                        scope.launch { Db.library.addToPlaylist(p.playlist, tracks); AppDialogs.show(p.name) }
                    }.padding(12.dp)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold)
                        Text(pluralStringResource(Res.plurals.desktop_song_count, p.songCount, p.songCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { AppDialogs.current = null }) { Text(stringResource(Res.string.cancel)) } }
    )
}

private val importer by lazy { PlaylistImporter(listOf(YouTubeMusicSource(), SpotifySource())) }

/** Spotify / YouTube Music playlist import, using the Android app's importer unchanged. */
@Composable
private fun ImportDialog() {
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var result by remember { mutableStateOf<List<String>?>(null) }
    var working by remember { mutableStateOf(false) }
    var importJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var completed by remember { mutableStateOf<com.k4music.android.playlistimport.FetchedPlaylist?>(null) }
    var playlistId by remember { mutableStateOf<Long?>(null) }
    var imported by remember { mutableStateOf(0) }
    var duplicates by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf<List<com.k4music.android.playlistimport.FailedTrack>>(emptyList()) }
    var reviewIndex by remember { mutableStateOf<Int?>(null) }

    suspend fun showResult() {
        val fetched = completed ?: return
        val total = fetched.total ?: (fetched.tracks.size + fetched.failed.size)
        result = listOfNotNull(
            getString(Res.string.import_result_count, imported, total),
            duplicates.takeIf { it > 0 }?.let { getString(Res.string.import_duplicates, it) },
            fetched.notExposed.takeIf { it > 0 }?.let { getString(Res.string.import_not_provided, it) },
            if (fetched.truncated) getString(Res.string.import_maybe_truncated) else null,
            failed.size.takeIf { it > 0 }?.let { getString(Res.string.import_failed_header, it) }
        )
        status = fetched.name
    }
    // Progress callbacks arrive off the UI coroutine; format them where getString can suspend.
    val progressEvents = remember { kotlinx.coroutines.flow.MutableSharedFlow<com.k4music.android.playlistimport.ImportProgress>(extraBufferCapacity = 64) }
    LaunchedEffect(Unit) {
        progressEvents.collect { p ->
            status = when (p.phase) {
                ImportPhase.Fetching -> getString(Res.string.import_reading, p.done)
                ImportPhase.Matching -> getString(Res.string.import_matching, p.done, p.total ?: 0)
            }
        }
    }

    val reviewed = reviewIndex?.let { failed.getOrNull(it) }
    val source = importer.sourceFor(link)
    if (reviewed != null && source != null) ManualMatchDialog(reviewed, source, onDismiss = { reviewIndex = null }) { candidate ->
        val id = requireNotNull(playlistId)
        val added = Db.library.addToPlaylist(Playlist(id = id, name = completed?.name.orEmpty()), listOf(candidate.payload))
        imported += added
        if (added == 0) duplicates++
        failed = failed.filterIndexed { index, _ -> index != reviewIndex }
        showResult()
    }

    AlertDialog(
        onDismissRequest = { importJob?.cancel(); AppDialogs.current = null },
        title = { Text(stringResource(Res.string.import_title)) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Focused at once, so the copied link can be pasted straight away.
                val linkFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { linkFocus.requestFocus() }
                OutlinedTextField(
                    link, { link = it }, singleLine = true, enabled = !working,
                    placeholder = { Text(stringResource(Res.string.import_hint)) }, modifier = Modifier.fillMaxWidth().focusRequester(linkFocus)
                )
                status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                result?.forEach { Text(it) }
                failed.forEachIndexed { index, track ->
                    Column {
                        Text("${track.title} — ${track.artist}", fontWeight = FontWeight.SemiBold)
                        Text(track.reason, style = MaterialTheme.typography.bodySmall)
                        if (source?.canRetryFailed == true) TextButton(enabled = !working, onClick = { reviewIndex = index }) {
                            Text(stringResource(Res.string.import_review))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (result == null) TextButton(enabled = !working && link.isNotBlank(), onClick = {
                working = true
                importJob = scope.launch {
                    try {
                        val plan: ImportPlan = importer.prepare(link) { p ->
                            progressEvents.tryEmit(p)
                            progress = p.total?.takeIf { it > 0 }?.let { p.done.toFloat() / it }
                        }
                        status = getString(Res.string.import_saving)
                        val fetched = plan.fetched
                        val (id, added) = Db.library.createPlaylist(
                            Playlist(name = fetched.name, browseId = fetched.browseId, thumbnail = fetched.thumbnail),
                            fetched.tracks.map { it.mediaItem }
                        )
                        completed = fetched
                        playlistId = id
                        imported = added
                        duplicates = plan.duplicates
                        failed = fetched.failed
                        showResult()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        status = "${getString(Res.string.import_error_title)}: ${e.message}"
                    } finally {
                        working = false
                        progress = null
                    }
                }
            }) { Text(stringResource(Res.string.import_playlist)) }
            else if (failed.isNotEmpty() && importer.sourceFor(link)?.canRetryFailed == true) {
                TextButton(enabled = !working, onClick = {
                    val source = importer.sourceFor(link) ?: return@TextButton
                    val id = playlistId ?: return@TextButton
                    working = true
                    importJob = scope.launch {
                        try {
                            val (matches, remaining) = source.retry(failed) { p -> progressEvents.tryEmit(p) }
                            imported += Db.library.addToPlaylist(Playlist(id = id, name = completed?.name.orEmpty()), matches.distinctBy { it.key }.map { it.mediaItem })
                            failed = remaining
                            showResult()
                        } catch (error: kotlinx.coroutines.CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            status = "${getString(Res.string.import_error_title)}: ${error.message}"
                        } finally { working = false }
                    }
                }) { Text(stringResource(Res.string.import_retry_failed)) }
            }
        },
        dismissButton = { TextButton(onClick = { importJob?.cancel(); AppDialogs.current = null }) { Text(stringResource(if (working) Res.string.cancel else Res.string.import_close)) } }
    )
}

@Composable
private fun ManualMatchDialog(track: com.k4music.android.playlistimport.FailedTrack,
    source: com.k4music.android.playlistimport.PlaylistSource, onDismiss: () -> Unit,
    onChoose: suspend (com.k4music.android.playlistimport.Candidate<Track>) -> Unit) {
    val scope = rememberCoroutineScope()
    var choices by remember(track) { mutableStateOf<List<com.k4music.android.playlistimport.Candidate<Track>>>(emptyList()) }
    var loading by remember(track) { mutableStateOf(true) }
    var saving by remember(track) { mutableStateOf(false) }
    var failure by remember(track) { mutableStateOf<String?>(null) }
    var attempt by remember(track) { mutableStateOf(0) }
    LaunchedEffect(track, attempt) {
        loading = true; failure = null
        try { choices = source.candidates(track) }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { failure = error.message ?: "Could not load matches" }
        finally { loading = false }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(Res.string.import_review_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${track.title} — ${track.artist}", fontWeight = FontWeight.SemiBold)
                track.durationMs?.takeIf { it > 0 }?.let { Text(com.k4music.desktop.player.formatDuration(it)) }
                Text(stringResource(Res.string.import_review_hint), style = MaterialTheme.typography.bodySmall)
                if (loading || saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!loading && choices.isEmpty() && failure == null) Text(stringResource(Res.string.import_review_empty))
                choices.forEach { candidate ->
                    Column {
                        Text(candidate.title, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(candidate.artist, candidate.durationMs?.let { com.k4music.desktop.player.formatDuration(it) }).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = !loading && !saving, onClick = {
                            saving = true
                            scope.launch {
                                try { onChoose(candidate); onDismiss() }
                                catch (error: kotlinx.coroutines.CancellationException) { throw error }
                                catch (error: Exception) { failure = error.message ?: "Could not save this recording" }
                                finally { saving = false }
                            }
                        }) { Text(stringResource(Res.string.import_review_choose)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !loading && !saving, onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(Res.string.back)) } }
    )
}

/**
 * "K4 Audio X is available": downloads the installer with a progress bar and starts it (the
 * installer closes the app, updates it and opens it again). Without a Windows installer in the
 * release, it offers the website instead.
 */
@Composable
private fun UpdateDialog(update: com.k4music.desktop.Updater.Update) {
    val scope = rememberCoroutineScope()
    var transfer by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var failed by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    val busy = progress != null && !failed
    AlertDialog(
        onDismissRequest = { transfer?.cancel(); AppDialogs.current = null },
        title = { Text(stringResource(Res.string.desktop_update_title, update.version)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.desktop_update_body, com.k4music.desktop.Updater.currentVersion))
                update.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 12, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                progress?.let { p ->
                    Text(stringResource(if (starting) Res.string.desktop_update_starting else Res.string.desktop_update_downloading), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }
                if (failed) Text(stringResource(Res.string.desktop_update_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            if (update.installerUrl != null) K4Button(enabled = !busy, onClick = {
                failed = false
                progress = 0f
                transfer = scope.launch {
                    try {
                    // Starting the installer can fail too (Windows Smart App Control blocks unsigned installers).
                    runCatching {
                        val file = com.k4music.desktop.Updater.download(update) { progress = it }
                        starting = true
                        com.k4music.desktop.Updater.install(file)
                    }.onSuccess { AppDialogs.current = null }
                        .onFailure {
                            if (it is kotlinx.coroutines.CancellationException) throw it
                            com.k4music.desktop.AppLog.w("Updater", "Update failed", it); starting = false; failed = true
                        }
                    } finally { progress = null }
                }
            }) { Text(stringResource(Res.string.desktop_update_now)) }
            else K4Button(onClick = {
                AppDialogs.current = null
                runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(com.k4music.desktop.ui.screens.WEBSITE)) }
            }) { Text(stringResource(Res.string.desktop_update_website)) }
        },
        dismissButton = {
            TextButton(onClick = { transfer?.cancel(); AppDialogs.current = null }) {
                Text(stringResource(if (busy) Res.string.cancel else Res.string.desktop_update_later))
            }
        }
    )
}

private data class PaletteAction(
    val title: String,
    val category: String,
    val shortcut: String? = null,
    val execute: () -> Unit
)

@Composable
private fun CommandPaletteDialog(onDismiss: () -> Unit) {
    val navigator = LocalNavigator.current
    val player = LocalPlayer.current
    val colors = MaterialTheme.colorScheme

    val actions = remember(navigator, player) {
        listOf(
            PaletteAction("Go to Home", "Navigation", "Alt+H") { navigator.root(Screen.Home) },
            PaletteAction("Discover New Music", "Navigation", "Alt+D") { navigator.root(Screen.Discover) },
            PaletteAction("Search Library & Online", "Navigation", "Ctrl+F") { navigator.go(Screen.Search()) },
            PaletteAction("Open Library", "Navigation", "Alt+L") { navigator.root(Screen.Library) },
            PaletteAction("Open Settings", "Navigation", "Ctrl+,") { navigator.root(Screen.Settings) },
            PaletteAction("Play / Pause", "Playback", "Space") { player.togglePlay() },
            PaletteAction("Next Track", "Playback", "Ctrl+Right") { player.next() },
            PaletteAction("Previous Track", "Playback", "Ctrl+Left") { player.previous() },
            PaletteAction("Toggle Shuffle", "Playback", null) { player.setShuffle(!com.k4music.desktop.prefs.Settings.shuffleEnabled) },
            PaletteAction("Toggle Mute", "Playback", null) { player.setMuted(!com.k4music.desktop.prefs.Settings.muted) },
            PaletteAction("Volume: 100%", "Audio", null) { player.setVolume(100); player.setMuted(false) },
            PaletteAction("Volume: 50%", "Audio", null) { player.setVolume(50); player.setMuted(false) },
            PaletteAction("Volume: 25%", "Audio", null) { player.setVolume(25); player.setMuted(false) },
            PaletteAction("Cycle Repeat Mode", "Playback", null) { player.cycleRepeat() },
            PaletteAction("Equalizer: Bass Boost", "Audio", null) {
                com.k4music.desktop.player.AudioEffects.setGains(com.k4music.desktop.player.AudioEffects.presets.getValue("Bass Boost"), "Bass Boost")
                player.applyEffects()
            },
            PaletteAction("Equalizer: Vocal", "Audio", null) {
                com.k4music.desktop.player.AudioEffects.setGains(com.k4music.desktop.player.AudioEffects.presets.getValue("Vocal"), "Vocal")
                player.applyEffects()
            },
            PaletteAction("Equalizer: Electronic", "Audio", null) {
                com.k4music.desktop.player.AudioEffects.setGains(com.k4music.desktop.player.AudioEffects.presets.getValue("Electronic"), "Electronic")
                player.applyEffects()
            },
            PaletteAction("Equalizer: Flat (Reset)", "Audio", null) {
                com.k4music.desktop.player.AudioEffects.setGains(com.k4music.desktop.player.AudioEffects.presets.getValue("Flat"), "Flat")
                player.applyEffects()
            },
            PaletteAction("Toggle Sidebar", "View", "Ctrl+B") { com.k4music.desktop.prefs.Settings.sidebarCollapsed = !com.k4music.desktop.prefs.Settings.sidebarCollapsed },
            PaletteAction("Clear Queue", "Queue", null) { player.clearQueue() }
        )
    }

    var query by remember { mutableStateOf("") }
    val filtered = remember(query, actions) {
        if (query.isBlank()) actions
        else actions.filter {
            it.title.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true)
        }
    }
    var selectedIndex by remember(filtered) { mutableStateOf(0) }
    val listState = rememberLazyListState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface.copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
            modifier = Modifier.width(540.dp).padding(16.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceContainer)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = org.jetbrains.compose.resources.painterResource(Res.drawable.search),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.primary
                    )
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it; selectedIndex = 0 },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            if (filtered.isNotEmpty()) {
                                                selectedIndex = (selectedIndex + 1) % filtered.size
                                            }
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (filtered.isNotEmpty()) {
                                                selectedIndex = (selectedIndex - 1 + filtered.size) % filtered.size
                                            }
                                            true
                                        }
                                        Key.Enter, Key.NumPadEnter -> {
                                            filtered.getOrNull(selectedIndex)?.let {
                                                onDismiss()
                                                it.execute()
                                            }
                                            true
                                        }
                                        Key.Escape -> {
                                            onDismiss()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filtered) { index, item ->
                        val isSelected = index == selectedIndex
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) colors.primaryContainer else Color.Transparent)
                                .clickable {
                                    onDismiss()
                                    item.execute()
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                item.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.width(76.dp)
                            )
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) colors.onSurface else colors.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier.weight(1f)
                            )
                            if (item.shortcut != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) colors.primary.copy(alpha = 0.2f) else colors.surfaceContainerHigh,
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Text(
                                        item.shortcut,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
