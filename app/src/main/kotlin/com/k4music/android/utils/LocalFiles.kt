package com.k4music.android.utils

import android.content.ContentUris
import android.database.ContentObserver
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.k4music.android.Dependencies
import com.k4music.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

private object LocalFileChanges {
    // One MediaStore observer for all visible rows, released when local rows leave the screen.
    val changes by lazy {
        callbackFlow {
            val resolver = Dependencies.application.contentResolver
            val observer = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) { trySend(Unit) }
            }
            try { resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer) }
            catch (_: SecurityException) { close(); return@callbackFlow }
            trySend(Unit)
            awaitClose { resolver.unregisterContentObserver(observer) }
        }.conflate().shareIn(CoroutineScope(SupervisorJob() + Dispatchers.IO), SharingStarted.WhileSubscribed(5_000), 1)
    }
}

/** Permission denial is unknown, not evidence that a user's file was deleted. */
@Composable
internal fun localFileSubtitle(id: String, artist: String?): String? {
    if (!id.startsWith("local:")) return artist
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val available by produceState<Boolean?>(null, id, lifecycle) {
        val number = localMediaStoreId(id)
        if (number == null) { value = false; return@produceState }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            LocalFileChanges.changes.collectLatest {
                value = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openAssetFileDescriptor(
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, number), "r"
                        )?.use { true } ?: false
                    } catch (_: FileNotFoundException) { false }
                    catch (_: SecurityException) { null }
                    catch (_: java.io.IOException) { null }
                }
            }
        }
    }
    return if (available == false) listOfNotNull(artist?.takeIf { it.isNotBlank() }, stringResource(R.string.local_file_unavailable)).joinToString(" • ")
    else artist
}
