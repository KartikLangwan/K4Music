package com.k4music.android.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.k4music.android.Database
import com.k4music.android.internal
import com.k4music.app.R
import com.k4music.android.models.Song
import com.k4music.android.preferences.OrderPreferences
import com.k4music.android.service.LOCAL_KEY_PREFIX
import com.k4music.android.transaction
import com.k4music.android.ui.components.themed.SecondaryTextButton
import com.k4music.android.ui.screens.Route
import com.k4music.android.utils.AudioMediaCursor
import com.k4music.android.utils.hasPermission
import com.k4music.android.utils.medium
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.utils.isAtLeastAndroid13
import com.k4music.core.ui.utils.isCompositionLaunched
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.room.withTransaction

private val permission = if (isAtLeastAndroid13) Manifest.permission.READ_MEDIA_AUDIO
else Manifest.permission.READ_EXTERNAL_STORAGE

@Route
@Composable
fun HomeLocalSongs(onSearchClick: () -> Unit) = with(OrderPreferences) {
    val context = LocalContext.current
    val [_, typography] = LocalAppearance.current

    var hasPermission by remember(isCompositionLaunched()) {
        mutableStateOf(context.applicationContext.hasPermission(permission))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { hasPermission = it }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = context.hasPermission(permission)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                context.musicFilesAsFlow().collect()
            } catch (_: SecurityException) {
                hasPermission = false
            }
        }
    }

    if (hasPermission) HomeSongs(
        onSearchClick = onSearchClick,
        songProvider = {
            Database.songs(
                sortBy = localSongSortBy,
                sortOrder = localSongSortOrder,
                isLocal = true
            ).map { songs -> songs.filter { it.durationText != "0:00" } }
        },
        sortBy = localSongSortBy,
        setSortBy = { localSongSortBy = it },
        sortOrder = localSongSortOrder,
        setSortOrder = { localSongSortOrder = it },
        title = stringResource(R.string.local)
    ) else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = stringResource(R.string.local_music_access_description),
                modifier = Modifier.fillMaxWidth(0.75f),
                style = typography.m.medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryTextButton(
                text = stringResource(R.string.allow_audio_access),
                onClick = { launcher.launch(permission) }
            )
            SecondaryTextButton(
                text = stringResource(R.string.open_settings),
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }
            )
        }
    }
}

fun Context.musicFilesAsFlow(): Flow<List<Song>> = flow {
    var version: String? = null

    while (currentCoroutineContext().isActive) {
        if (!hasPermission(permission)) throw SecurityException("Audio permission revoked")
        val newVersion = runCatching { MediaStore.getVersion(applicationContext) }.getOrNull()

        if (version == null || version != newVersion) {

            // A MediaStore query can throw (revoked permission, OEM cursor
            // bugs). Skip this round and retry on the next tick instead of
            // taking the app down with it.
            runCatching {
                AudioMediaCursor.query(contentResolver) {
                    buildList<Song> {
                        while (next()) {
                            if (!isMusic || duration == 0) continue
                            add(
                                Song(
                                    id = "$LOCAL_KEY_PREFIX$id",
                                    title = name,
                                    artistsText = artist,
                                    durationText = duration.milliseconds.toComponents { minutes, seconds, _ ->
                                        "$minutes:${seconds.toString().padStart(2, '0')}"
                                    },
                                    thumbnailUrl = albumUri.toString()
                                )
                            )
                        }
                    }
                }
            }.onFailure { Log.e("HomeLocalSongs", "Local music query failed", it) }
                .getOrNull()
                ?.let { songs ->
                    Database.internal.withTransaction { songs.forEach(Database::insert) }
                    version = newVersion
                    emit(songs)
                }
        }
        delay(5.seconds)
    }
}.distinctUntilChanged().flowOn(Dispatchers.IO)
