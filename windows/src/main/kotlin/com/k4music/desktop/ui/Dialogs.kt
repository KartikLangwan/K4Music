package com.k4music.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.k4music.desktop.res.add_to_playlist
import com.k4music.desktop.res.cancel
import com.k4music.desktop.res.confirm
import com.k4music.desktop.res.delete
import com.k4music.desktop.res.desktop_song_count
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
import com.k4music.desktop.res.import_title
import com.k4music.desktop.res.new_playlist
import com.k4music.desktop.res.rename
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** App-wide dialogs and short messages, opened from anywhere and shown by [DialogHost]. */
object AppDialogs {
    sealed interface Dialog {
        class Text(val title: String?, val initial: String, val multiline: Boolean = false, val onConfirm: (String) -> Unit) : Dialog
        class Confirm(val subject: String, val onYes: () -> Unit) : Dialog
        class AddToPlaylist(val tracks: List<Track>) : Dialog
        data object Import : Dialog
        class Rename(val playlist: Playlist) : Dialog
        data object NewPlaylist : Dialog
    }

    var current by mutableStateOf<Dialog?>(null)
    var toast by mutableStateOf<String?>(null)

    fun newPlaylist() { current = Dialog.NewPlaylist }
    fun renamePlaylist(playlist: Playlist) { current = Dialog.Rename(playlist) }
    fun confirm(subject: String, onYes: () -> Unit) { current = Dialog.Confirm(subject, onYes) }
    fun addToPlaylist(tracks: List<Track>) { current = Dialog.AddToPlaylist(tracks) }
    fun importPlaylist() { current = Dialog.Import }
    fun show(message: String) { toast = message }

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
        is AppDialogs.Dialog.Text -> TextDialog(dialog.title.orEmpty(), dialog.initial, dialog.multiline) { dialog.onConfirm(it) }
        is AppDialogs.Dialog.NewPlaylist -> TextDialog(stringResource(Res.string.new_playlist), "") { name ->
            scope.launch { Db.library.insert(Playlist(name = name.trim())) }
        }
        is AppDialogs.Dialog.Rename -> TextDialog(stringResource(Res.string.rename), dialog.playlist.name) { name ->
            scope.launch { Db.library.update(dialog.playlist.copy(name = name.trim())) }
        }
        is AppDialogs.Dialog.Confirm -> AlertDialog(
            onDismissRequest = { AppDialogs.current = null },
            title = { Text(stringResource(Res.string.delete)) },
            text = { Text(dialog.subject) },
            confirmButton = { TextButton(onClick = { AppDialogs.current = null; dialog.onYes() }) { Text(stringResource(Res.string.confirm)) } },
            dismissButton = { TextButton(onClick = { AppDialogs.current = null }) { Text(stringResource(Res.string.cancel)) } }
        )
        is AppDialogs.Dialog.AddToPlaylist -> AddToPlaylistDialog(dialog.tracks)
        is AppDialogs.Dialog.Import -> ImportDialog()
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
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { AppDialogs.current = null },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it }, singleLine = !multiline, minLines = if (multiline) 10 else 1, maxLines = if (multiline) 18 else 1,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { AppDialogs.current = null; onConfirm(text) }) { Text(stringResource(Res.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = { AppDialogs.current = null }) { Text(stringResource(Res.string.cancel)) } }
    )
}

@Composable
private fun AddToPlaylistDialog(tracks: List<Track>) {
    val scope = rememberCoroutineScope()
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
                            AppDialogs.current = AppDialogs.Dialog.Text(null, "") { name ->
                                scope.launch { Db.library.addToPlaylist(Playlist(name = name), tracks); AppDialogs.show(name) }
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
                        Text(stringResource(Res.string.desktop_song_count, p.songCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    AlertDialog(
        onDismissRequest = { if (!working) AppDialogs.current = null },
        title = { Text(stringResource(Res.string.import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    link, { link = it }, singleLine = true, enabled = !working,
                    placeholder = { Text(stringResource(Res.string.import_hint)) }, modifier = Modifier.fillMaxWidth()
                )
                status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                result?.forEach { Text(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = !working && link.isNotBlank() && result == null, onClick = {
                working = true
                scope.launch {
                    try {
                        val plan: ImportPlan = importer.prepare(link) { p ->
                            progressEvents.tryEmit(p)
                            progress = p.total?.takeIf { it > 0 }?.let { p.done.toFloat() / it }
                        }
                        status = getString(Res.string.import_saving)
                        val fetched = plan.fetched
                        val (_, added) = Db.library.createPlaylist(
                            Playlist(name = fetched.name, browseId = fetched.browseId, thumbnail = fetched.thumbnail),
                            fetched.tracks.map { it.mediaItem }
                        )
                        val total = fetched.total ?: (fetched.tracks.size + fetched.failed.size)
                        result = listOfNotNull(
                            getString(Res.string.import_result_count, added, total),
                            plan.duplicates.takeIf { it > 0 }?.let { getString(Res.string.import_duplicates, it) },
                            fetched.failed.size.takeIf { it > 0 }?.let { getString(Res.string.import_failed_header, it) }
                        ) + fetched.failed.take(10).map { "• ${it.title} — ${it.artist}" }
                        status = fetched.name
                    } catch (e: Exception) {
                        status = "${getString(Res.string.import_error_title)}: ${e.message}"
                    } finally {
                        working = false
                        progress = null
                    }
                }
            }) { Text(stringResource(Res.string.import_playlist)) }
        },
        dismissButton = { TextButton(enabled = !working, onClick = { AppDialogs.current = null }) { Text(stringResource(Res.string.import_close)) } }
    )
}
