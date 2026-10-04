package com.k4music.android.utils

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.app.R
import com.k4music.android.service.PlayerService
import com.k4music.core.ui.utils.EqualizerIntentBundleAccessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@JvmInline
value class PlayerScope internal constructor(val player: Player)

@Composable
fun Player?.DisposableListener(
    key: Any? = Unit,
    listenerProvider: PlayerScope.() -> Player.Listener
) {
    val currentListenerProvider by rememberUpdatedState(listenerProvider)

    DisposableEffect(key, this) {
        this@DisposableListener?.run {
            val listener = PlayerScope(this).currentListenerProvider()

            addListener(listener)
            listener.onMediaItemTransition(
                /* mediaItem = */
                currentMediaItem,
                /* reason = */
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            )
            onDispose { removeListener(listener) }
        } ?: onDispose { }
    }
}

/**
 * Position and duration as a [State]: read it only inside the composables that draw progress so
 * the rest of the player does not recompose on every tick. Polls only while audio is playing.
 */
@Composable
fun Player?.positionAndDurationState(
    delay: Duration = 500.milliseconds
): State<Pair<Long, Long>> {
    val state = remember(this) {
        mutableStateOf(this?.let { currentPosition to duration } ?: (0L to C.TIME_UNSET))
    }
    var isPlaying by remember(this) { mutableStateOf(this?.isPlaying == true) }

    DisposableListener {
        object : Player.Listener {
            private fun sync() {
                state.value = player.currentPosition to player.duration
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = sync()
            override fun onPlaybackStateChanged(playbackState: Int) = sync()
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                sync()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) = sync()
        }
    }

    LaunchedEffect(this, isPlaying) {
        val player = this@positionAndDurationState ?: return@LaunchedEffect
        while (isActive && isPlaying) {
            delay(delay)
            state.value = player.currentPosition to player.duration
        }
    }

    return state
}

typealias WindowState = Pair<Timeline.Window?, PlaybackException?>

@Composable
fun windowState(
    binder: PlayerService.Binder? = LocalPlayerServiceBinder.current
): WindowState {
    var window by remember(binder) { mutableStateOf(binder?.player?.currentWindow) }
    var error by remember(binder) { mutableStateOf<PlaybackException?>(binder?.player?.playerError) }
    val state by remember(binder) {
        derivedStateOf(
            policy = object : SnapshotMutationPolicy<WindowState> {
                override fun equivalent(a: WindowState, b: WindowState) =
                    a.first === b.first && a.second == b.second
            }
        ) {
            window to error
        }
    }

    binder?.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                window = player.currentWindow
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                error = player.playerError
            }

            override fun onPlayerError(playbackException: PlaybackException) {
                error = playbackException
            }
        }
    }

    return state
}

@Composable
fun playingSong(
    binder: PlayerService.Binder? = LocalPlayerServiceBinder.current
): Pair<String?, Boolean> {
    var playing by remember(binder) { mutableStateOf(binder?.player?.shouldBePlaying == true) }
    var id: String? by remember(binder) { mutableStateOf(binder?.player?.currentMediaItem?.mediaId) }

    binder?.player.DisposableListener {
        object : Player.Listener {
            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int
            ) {
                super.onPlayWhenReadyChanged(playWhenReady, reason)

                playing = player.shouldBePlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playing = player.shouldBePlaying
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                super.onMediaItemTransition(mediaItem, reason)

                id = mediaItem?.mediaId
            }
        }
    }

    return id to playing
}

@Composable
fun rememberEqualizerLauncher(
    audioSessionId: () -> Int?,
    contentType: Int = AudioEffect.CONTENT_TYPE_MUSIC
): State<() -> Unit> {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val errorMsg = stringResource(R.string.no_equalizer_installed)

    return rememberUpdatedState {
        try {
            launcher.launch(
                Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).replaceExtras(
                    EqualizerIntentBundleAccessor.bundle {
                        audioSessionId()?.let { audioSession = it }
                        packageName = context.packageName
                        this.contentType = contentType
                    }
                )
            )
        } catch (_: ActivityNotFoundException) {
            context.toast(errorMsg)
        }
    }
}
