package com.k4music.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.k4music.android.models.PipedSession
import com.k4music.desktop.data.Db
import com.k4music.desktop.player.Track
import com.k4music.desktop.player.formatDuration
import com.k4music.desktop.res.*
import com.k4music.desktop.ui.AppDialogs
import com.k4music.desktop.ui.LocalNavigator
import com.k4music.desktop.ui.MediaCard
import com.k4music.desktop.ui.Screen
import com.k4music.desktop.ui.SectionHeader
import com.k4music.desktop.ui.Shelf
import com.k4music.desktop.ui.rememberPage
import com.k4music.providers.piped.Piped
import com.k4music.providers.piped.models.Instance
import io.ktor.http.Url
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.util.UUID

/** Settings → Sync: Piped accounts (same model as Android's SyncSettings). */
@Composable
fun PipedAccounts() {
    val scope = rememberCoroutineScope()
    val sessions by remember { Db.library.pipedSessions() }.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    sessions.forEach { session ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(session.username, fontWeight = FontWeight.SemiBold)
                Text(session.apiBaseUrl.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { scope.launch { Db.library.delete(session) } }) { Text(stringResource(Res.string.delete)) }
        }
    }
    OutlinedButton(onClick = { adding = true }) { Text(stringResource(Res.string.desktop_piped_add)) }
    if (adding) PipedLoginDialog { adding = false }
}

@Composable
private fun PipedLoginDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val instances by produceState<List<Instance>?>(null) { value = Piped.getInstances()?.getOrNull().orEmpty() }
    var instance by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!working) onClose() },
        title = { Text(stringResource(Res.string.desktop_piped_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(instance, { instance = it }, label = { Text("API URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                instances?.takeIf { it.isNotEmpty() }?.let { list ->
                    LazyColumn(Modifier.heightIn(max = 140.dp)) {
                        items(list) { i ->
                            TextButton(onClick = { instance = i.apiBaseUrl.toString() }) { Text("${i.name} · ${i.locationsFormatted}") }
                        }
                    }
                }
                OutlinedTextField(username, { username = it }, label = { Text(stringResource(Res.string.desktop_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password, { password = it }, label = { Text(stringResource(Res.string.desktop_password)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !working && instance.isNotBlank() && username.isNotBlank() && password.isNotBlank(), onClick = {
                working = true
                scope.launch {
                    val url = runCatching { Url(instance.trim()) }.getOrNull()
                    val session = url?.let { Piped.login(it, username.trim(), password)?.getOrNull() }
                    if (session != null && url != null) {
                        Db.library.insert(PipedSession(apiBaseUrl = url, token = session.token, username = username.trim()))
                        onClose()
                    } else error = "Sign-in failed"
                    working = false
                }
            }) { Text(stringResource(Res.string.confirm)) }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onClose) { Text(stringResource(Res.string.cancel)) } }
    )
}

/** Library → Playlists: each signed-in Piped account's playlists. */
@Composable
fun PipedPlaylistsShelves() {
    val navigator = LocalNavigator.current
    val sessions by remember { Db.library.pipedSessions() }.collectAsState(initial = emptyList())
    sessions.forEach { session ->
        val page = rememberPage("piped/${session.id}") { Piped.playlist.list(session.toApiSession())?.getOrNull() }
        val playlists = page.value.orEmpty()
        if (playlists.isNotEmpty()) {
            SectionHeader("Piped · ${session.username}")
            Shelf {
                items(playlists, key = { it.id.toString() }) { p ->
                    MediaCard(p.name, stringResource(Res.string.desktop_song_count, p.videoCount), p.thumbnailUrl.toString(), Modifier.width(164.dp)) {
                        navigator.go(Screen.PipedPlaylist(session.id, p.id.toString(), p.name))
                    }
                }
            }
        }
    }
}

@Composable
fun PipedPlaylistScreen(sessionId: Long, playlistId: String, name: String) {
    val sessions by remember { Db.library.pipedSessions() }.collectAsState(initial = null)
    val session = sessions?.firstOrNull { it.id == sessionId }
    val page = rememberPage("piped/$sessionId/$playlistId") {
        session?.let { Piped.playlist.songs(it.toApiSession(), UUID.fromString(playlistId))?.getOrNull() }
    }
    val tracks = page.value?.videos.orEmpty().mapNotNull { v ->
        v.id?.let { id ->
            Track(id = id, title = v.title, artists = v.uploaderName, durationText = formatDuration(v.durationSeconds * 1000), thumbnailUrl = v.thumbnailUrl.toString())
        }
    }
    SongList(tracks, header = {
        CollectionHeader(name, stringResource(Res.string.desktop_song_count, tracks.size), page.value?.thumbnailUrl?.toString()) { PlayButtons(tracks) }
    })
}
