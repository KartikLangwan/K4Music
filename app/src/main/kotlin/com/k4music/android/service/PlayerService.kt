package com.k4music.android.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.audiofx.LoudnessEnhancer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.text.format.DateUtils
import androidx.annotation.OptIn
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import com.k4music.android.Database
import com.k4music.android.Dependencies
import com.k4music.android.MainActivity
import com.k4music.app.R
import com.k4music.android.models.Event
import com.k4music.android.models.Format
import com.k4music.android.models.QueuedMediaItem
import com.k4music.android.models.Song
import com.k4music.android.models.SongWithContentLength
import com.k4music.android.preferences.AppearancePreferences
import com.k4music.android.preferences.DataPreferences
import com.k4music.android.preferences.PlayerPreferences
import com.k4music.android.query
import com.k4music.android.transaction
import com.k4music.android.utils.ActionReceiver
import com.k4music.android.utils.ConditionalCacheDataSourceFactory
import com.k4music.android.utils.GlyphInterface
import com.k4music.android.utils.InvincibleService
import com.k4music.android.utils.TimerJob
import com.k4music.android.utils.YouTubeDLResponse
import com.k4music.android.utils.YouTubeRadio
import com.k4music.android.utils.activityPendingIntent
import com.k4music.android.utils.asDataSource
import com.k4music.android.utils.broadcastPendingIntent
import com.k4music.android.utils.defaultDataSource
import com.k4music.android.utils.findCause
import com.k4music.android.utils.findNextMediaItemById
import com.k4music.android.utils.forcePlayFromBeginning
import com.k4music.android.utils.forceSeekToNext
import com.k4music.android.utils.forceSeekToPrevious
import com.k4music.android.utils.get
import com.k4music.android.k4.isSaavnKey
import com.k4music.android.k4.resolveSaavnStream
import com.k4music.android.k4.StreamFallback
import com.k4music.android.k4.parseClockToMs
import com.k4music.android.k4.findSaavnStream
import com.k4music.android.utils.handleUnknownErrors
import com.k4music.android.utils.intent
import com.k4music.android.utils.mediaItems
import com.k4music.android.utils.PlaybackRetryBudget
import com.k4music.android.utils.Recovery
import com.k4music.android.utils.chooseRecovery
import com.k4music.android.utils.progress
import com.k4music.android.utils.retryIf
import com.k4music.android.utils.shuffleFromCurrent
import com.k4music.android.utils.readOnlyWhen
import com.k4music.android.utils.setPlaybackPitch
import com.k4music.android.utils.shouldBePlaying
import com.k4music.android.utils.thumbnail
import com.k4music.android.utils.timer
import com.k4music.android.utils.toast
import com.k4music.compose.preferences.SharedPreferencesProperty
import com.k4music.core.data.enums.ExoPlayerDiskCacheSize
import com.k4music.core.data.utils.UriCache
import com.k4music.core.ui.utils.EqualizerIntentBundleAccessor
import com.k4music.core.ui.utils.isAtLeastAndroid10
import com.k4music.core.ui.utils.isAtLeastAndroid12
import com.k4music.core.ui.utils.isAtLeastAndroid13
import com.k4music.core.ui.utils.isAtLeastAndroid6
import com.k4music.core.ui.utils.isAtLeastAndroid8
import com.k4music.core.ui.utils.isAtLeastAndroid9
import com.k4music.core.ui.utils.songBundle
import com.k4music.core.ui.utils.streamVolumeFlow
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.NavigationEndpoint
import com.k4music.providers.innertube.models.bodies.PlayerBody
import com.k4music.providers.innertube.models.bodies.SearchBody
import com.k4music.providers.innertube.requests.player
import com.k4music.providers.innertube.requests.searchPage
import com.k4music.providers.innertube.utils.from
import com.k4music.providers.sponsorblock.SponsorBlock
import com.k4music.providers.sponsorblock.models.Action
import com.k4music.providers.sponsorblock.models.Category
import com.k4music.providers.sponsorblock.requests.segments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import android.os.Binder as AndroidBinder
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import android.util.Log

const val LOCAL_KEY_PREFIX = "local:"
private const val TAG = "PlayerService"

@get:OptIn(UnstableApi::class)
val DataSpec.isLocal get() = key?.startsWith(LOCAL_KEY_PREFIX) == true

val MediaItem.isLocal get() = mediaId.startsWith(LOCAL_KEY_PREFIX)
val Song.isLocal get() = id.startsWith(LOCAL_KEY_PREFIX)

private const val LIKE_ACTION = "com.k4music.android.LIKE"
private const val LOOP_ACTION = "com.k4music.android.LOOP"

@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass", "TooManyFunctions") // intended in this class: it is a service
@OptIn(UnstableApi::class)
class PlayerService : InvincibleService(), Player.Listener, PlaybackStatsListener.Callback {
    private lateinit var mediaSession: MediaSession
    private lateinit var cache: Cache
    private lateinit var player: ExoPlayer

    private val defaultActions =
        PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_REWIND or
            PlaybackState.ACTION_PLAY_FROM_SEARCH

    private val stateBuilder
        get() = PlaybackState.Builder().setActions(
            defaultActions.let {
                if (isAtLeastAndroid12) it or PlaybackState.ACTION_SET_PLAYBACK_SPEED else it
            }
        ).addCustomAction(
            PlaybackState.CustomAction.Builder(
                /* action = */
                LIKE_ACTION,
                /* name   = */
                getString(R.string.like),
                /* icon   = */
                if (isLikedState.value) R.drawable.heart else R.drawable.heart_outline
            ).build()
        ).addCustomAction(
            PlaybackState.CustomAction.Builder(
                /* action = */
                LOOP_ACTION,
                /* name   = */
                getString(R.string.repeat),
                /* icon   = */
                PlayerPreferences.repeatMode.icon
            ).build()
        )

    private val playbackStateMutex = Mutex()
    private val metadataBuilder = MediaMetadata.Builder()

    private var timerJob: TimerJob? by mutableStateOf(null)
    private var radio: YouTubeRadio? = null

    private lateinit var bitmapProvider: BitmapProvider

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, error -> Log.e("K4Music", "Unhandled background error", error) })
    private var preferenceUpdaterJob: Job? = null
    private var volumeNormalizationJob: Job? = null
    private var sponsorBlockJob: Job? = null
    private var playbackRecoveryJob: Job? = null
    private val playbackRetryBudget = PlaybackRetryBudget()
    private val playbackFallbackBudget = PlaybackRetryBudget(limit = 1)

    override var isInvincibilityEnabled by mutableStateOf(false)

    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val audioEffects = AudioEffects()

    private val binder = Binder()

    private var isNotificationStarted = false
    override val notificationId get() = ServiceNotifications.default.notificationId!!
    private val notificationActionReceiver = NotificationActionReceiver()

    private val mediaItemState = MutableStateFlow<MediaItem?>(null)
    private val isLikedState = mediaItemState
        .flatMapLatest { item ->
            item?.mediaId?.let {
                Database
                    .likedAt(it)
                    .distinctUntilChanged()
                    .cancellable()
            } ?: flowOf(null)
        }
        .map { it != null }
        .onEach {
            updateNotification()
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private val glyphInterface by lazy { GlyphInterface(applicationContext) }

    private var poiTimestamp: Long? by mutableStateOf(null)

    override fun onBind(intent: Intent?): AndroidBinder {
        super.onBind(intent)
        return binder
    }

    @Suppress("CyclomaticComplexMethod")
    override fun onCreate() {
        super.onCreate()

        glyphInterface.tryInit()
        notificationActionReceiver.register(flags = ContextCompat.RECEIVER_EXPORTED)

        bitmapProvider = BitmapProvider(
            getBitmapSize = {
                (512 * resources.displayMetrics.density)
                    .roundToInt()
                    .coerceAtMost(AppearancePreferences.maxThumbnailSize)
            },
            getColor = { isSystemInDarkMode ->
                if (isSystemInDarkMode) Color.BLACK else Color.WHITE
            },
            context = this
        )

        cache = createCache(this)
        player = ExoPlayer.Builder(this, createRendersFactory(), createMediaSourceFactory())
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(
                /* audioAttributes = */
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */
                PlayerPreferences.handleAudioFocus
            )
            .setUsePlatformDiagnostics(false)
            .build()
            .apply {
                skipSilenceEnabled = PlayerPreferences.skipSilence
                addListener(this@PlayerService)
                addAnalyticsListener(
                    PlaybackStatsListener(
                        /* keepHistory = */
                        false,
                        /* callback = */
                        this@PlayerService
                    )
                )
            }

        updateRepeatMode()
        player.shuffleModeEnabled = PlayerPreferences.shuffleEnabled
        audioEffects.attach(player.audioSessionId)
        maybeRestorePlayerQueue()

        mediaSession = MediaSession(baseContext, TAG).apply {
            setCallback(SessionCallback())
            setPlaybackState(stateBuilder.build())
            setSessionActivity(activityPendingIntent<MainActivity>())
            isActive = true
        }

        coroutineScope.launch {
            var first = true
            combine(mediaItemState, isLikedState) { mediaItem, _ ->
                // work around NPE in other processes
                if (first) {
                    first = false
                    return@combine
                }

                if (mediaItem == null) return@combine
                withContext(Dispatchers.Main) {
                    updatePlaybackState()
                    updateNotification()
                }
            }.collect()
        }

        maybeResumePlaybackWhenDeviceConnected()

        coroutineScope.launch(Dispatchers.IO) {
            delay(PYTHON_WARM_UP_DELAY_MS) // after launch work settles
            runCatching { Dependencies.warmUp() }.onFailure { Log.w(TAG, "yt-dlp warm-up failed", it) }
        }

        preferenceUpdaterJob = coroutineScope.launch {
            fun <T : Any> subscribe(
                prop: SharedPreferencesProperty<T>,
                callback: (T) -> Unit
            ) = launch { prop.stateFlow.collectLatest { handler.post { callback(it) } } }

            subscribe(AppearancePreferences.isShowingThumbnailInLockscreenProperty) {
                maybeShowSongCoverInLockScreen()
            }

            subscribe(PlayerPreferences.bassBoostLevelProperty) { audioEffects.apply() }
            subscribe(PlayerPreferences.bassBoostProperty) {
                audioEffects.apply()
                if (!it) maybeNormalizeVolume()
            }
            subscribe(PlayerPreferences.reverbProperty) { audioEffects.apply() }
            subscribe(PlayerPreferences.equalizerEnabledProperty) { audioEffects.apply() }
            subscribe(PlayerPreferences.equalizerLevelsProperty) { audioEffects.apply() }
            subscribe(PlayerPreferences.virtualizerModeProperty) { audioEffects.apply() }
            subscribe(PlayerPreferences.virtualizerStrengthProperty) { audioEffects.apply() }
            subscribe(PlayerPreferences.stereoWideningProperty) { audioEffects.apply() }
            subscribe(PlayerPreferences.shuffleEnabledProperty) { enabled ->
                if (enabled && !player.shuffleModeEnabled) player.shuffleFromCurrent()
                player.shuffleModeEnabled = enabled
            }
            subscribe(PlayerPreferences.isInvincibilityEnabledProperty) {
                this@PlayerService.isInvincibilityEnabled = it
            }
            subscribe(PlayerPreferences.pitchProperty) {
                player.setPlaybackPitch(it.coerceAtLeast(0.01f))
            }
            subscribe(PlayerPreferences.queueLoopEnabledProperty) {
                updateRepeatMode()
                updateNotification()
            }
            subscribe(PlayerPreferences.resumePlaybackWhenDeviceConnectedProperty) {
                maybeResumePlaybackWhenDeviceConnected()
            }
            subscribe(PlayerPreferences.skipSilenceProperty) { player.skipSilenceEnabled = it }
            subscribe(PlayerPreferences.speedProperty) {
                player.setPlaybackSpeed(it.coerceAtLeast(0.01f))
            }
            subscribe(PlayerPreferences.trackLoopEnabledProperty) {
                updateRepeatMode()
                updateNotification()
            }
            subscribe(PlayerPreferences.volumeNormalizationBaseGainProperty) { maybeNormalizeVolume() }
            subscribe(PlayerPreferences.volumeNormalizationProperty) { maybeNormalizeVolume() }
            subscribe(PlayerPreferences.sponsorBlockEnabledProperty) { maybeSponsorBlock() }

            launch {
                val audioManager = getSystemService<AudioManager>()
                val stream = AudioManager.STREAM_MUSIC

                val min = when {
                    audioManager == null -> 0
                    isAtLeastAndroid9 -> audioManager.getStreamMinVolume(stream)
                    else -> 0
                }

                streamVolumeFlow(stream).collectLatest {
                    if (PlayerPreferences.stopOnMinimumVolume && it == min) handler.post(player::pause)
                }
            }
        }
    }

    private fun updateRepeatMode() {
        player.repeatMode = when (PlayerPreferences.repeatMode) {
            PlayerPreferences.RepeatMode.One -> Player.REPEAT_MODE_ONE
            PlayerPreferences.RepeatMode.All -> Player.REPEAT_MODE_ALL
            PlayerPreferences.RepeatMode.Off -> Player.REPEAT_MODE_OFF
        }
    }

    // Media3 assigns the session id asynchronously; effects bound to an old (or unset) id do nothing.
    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        audioEffects.attach(audioSessionId)
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        maybeNormalizeVolume()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.shouldBePlaying || PlayerPreferences.stopWhenClosed) {
            broadcastPendingIntent<NotificationDismissReceiver>().send()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // Covers every play entry point (UI, notification, headset, Android Auto) for a restored queue.
        if (playWhenReady && player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
        maybeSavePlayerQueue()
    }

    override fun onDestroy() {
        playbackRecoveryJob?.cancel()
        runCatching {
            maybeSavePlayerQueue()

            player.removeListener(this)
            player.stop()
            player.release()

            unregisterReceiver(notificationActionReceiver)

            mediaSession.isActive = false
            mediaSession.release()
            cache.release()

            loudnessEnhancer?.release()
            audioEffects.release()
            preferenceUpdaterJob?.cancel()

            coroutineScope.cancel()
            glyphInterface.close()
        }

        super.onDestroy()
    }

    override fun shouldBeInvincible() = !player.shouldBePlaying

    override fun onConfigurationChanged(newConfig: Configuration) {
        handler.post {
            if (!bitmapProvider.setDefaultBitmap() || player.currentMediaItem == null) return@post
            updateNotification()
        }

        super.onConfigurationChanged(newConfig)
    }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {
        val totalPlayTimeMs = playbackStats.totalPlayTimeMs
        if (totalPlayTimeMs < 5000) return

        if (eventTime.windowIndex !in 0 until eventTime.timeline.windowCount) return
        val mediaItem = eventTime.timeline[eventTime.windowIndex].mediaItem

        if (!DataPreferences.pausePlaytime) {
            query {
                runCatching {
                    Database.incrementTotalPlayTimeMs(mediaItem.mediaId, totalPlayTimeMs)
                }
            }
        }

        if (!DataPreferences.pauseHistory) {
            query {
                runCatching {
                    Database.insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = System.currentTimeMillis(),
                            playTime = totalPlayTimeMs
                        )
                    )
                }
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // A replaced queue gets a random shuffle order from Exo, which can place songs behind
        // the starting one where they would never play.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && player.shuffleModeEnabled) {
            player.shuffleFromCurrent()
        }

        if (
            AppearancePreferences.hideExplicit &&
            mediaItem?.mediaMetadata?.extras?.songBundle?.explicit == true
        ) {
            if (player.currentTimeline.mediaItems.all { it.mediaMetadata.extras?.songBundle?.explicit == true }) {
                player.pause()
                mediaItemState.update { mediaItem }
                toast(getString(R.string.queue_filtered_message))
                return
            }
            player.forceSeekToNext()
            return
        }

        playbackRecoveryJob?.cancel()
        playbackRetryBudget.onMediaTransition(mediaItem?.mediaId)
        playbackFallbackBudget.onMediaTransition(mediaItem?.mediaId)
        mediaItemState.update { mediaItem }

        maybeRecoverPlaybackError()
        maybeNormalizeVolume()
        maybeProcessRadio()

        with(bitmapProvider) {
            when {
                mediaItem == null -> load(null)
                mediaItem.mediaMetadata.artworkUri == lastUri -> bitmapProvider.load(lastUri)
            }
        }

        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
            updateMediaSessionQueue(player.currentTimeline)
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
        updateMediaSessionQueue(timeline)
        maybeSavePlayerQueue()
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        Log.w(TAG, "Playback failure ${error.errorCodeName}", error)

        val current = player.currentMediaItem ?: return
        if (current.isLocal) return
        val id = current.mediaId
        val recovery = chooseRecovery(
            code = error.errorCode,
            httpStatus = error.findCause<InvalidResponseCodeException>()?.responseCode,
            id = id,
            fallbackAllowed = !isSaavnKey(id) && PlayerPreferences.smartFallback,
            retries = playbackRetryBudget,
            fallbacks = playbackFallbackBudget
        )
        if (recovery == Recovery.None) return
        if (recovery == Recovery.Fallback) {
            Log.i(TAG, "Switching $id to the JioSaavn fallback after ${error.errorCodeName}")
            StreamFallback.force(id)
        }

        val position = player.currentPosition.coerceAtLeast(0L)
        playbackRecoveryJob?.cancel()
        playbackRecoveryJob = coroutineScope.launch(Dispatchers.Main) {
            delay(RECOVERY_DELAY_MS)
            if (player.currentMediaItem?.mediaId != id || player.playerError == null) return@launch
            player.seekTo(position)
            player.prepare()
        }
    }

    private fun updateMediaSessionQueue(timeline: Timeline) = mediaSession.setQueue(
        List(timeline.windowCount) { index ->
            val mediaItem = timeline.getWindow(index, Timeline.Window()).mediaItem
            MediaSession.QueueItem(
                MediaDescription.Builder()
                    .setMediaId(mediaItem.mediaId)
                    .setTitle(mediaItem.mediaMetadata.title)
                    .setSubtitle(mediaItem.mediaMetadata.artist)
                    .setIconUri(mediaItem.mediaMetadata.artworkUri)
                    .build(),
                index.toLong()
            )
        }
    )

    private fun maybeRecoverPlaybackError() {
        if (player.playerError != null) player.prepare()
    }

    private fun maybeProcessRadio() {
        if (player.mediaItemCount - player.currentMediaItemIndex > 3) return

        radio?.let { radio ->
            coroutineScope.launch(Dispatchers.Main) {
                player.addMediaItems(radio.process())
            }
        }
    }

    private fun maybeSavePlayerQueue() {
        if (!PlayerPreferences.persistentQueue) return

        val mediaItems = player.currentTimeline.mediaItems
        val mediaItemIndex = player.currentMediaItemIndex
        val mediaItemPosition = player.currentPosition

        transaction {
            runCatching {
                Database.clearQueue()
                Database.insert(
                    mediaItems.mapIndexed { index, mediaItem ->
                        QueuedMediaItem(
                            mediaItem = mediaItem,
                            position = if (index == mediaItemIndex) mediaItemPosition else null
                        )
                    }
                )
            }
        }
    }

    private fun maybeRestorePlayerQueue() {
        if (!PlayerPreferences.persistentQueue) return

        transaction {
            val queue = Database.queue()
            if (queue.isEmpty()) return@transaction
            Database.clearQueue()

            val index = queue
                .indexOfFirst { it.position != null }
                .coerceIn(0, (queue.size - 1).coerceAtLeast(0))

            handler.post {
                runCatching {
                    player.setMediaItems(
                        /* mediaItems = */
                        queue.map { item ->
                            item.mediaItem.buildUpon()
                                .setUri(item.mediaItem.mediaId)
                                .setCustomCacheKey(item.mediaItem.mediaId)
                                .build()
                                .apply {
                                    mediaMetadata.extras?.songBundle?.apply {
                                        isFromPersistentQueue = true
                                    }
                                }
                        },
                        /* startIndex = */
                        index,
                        /* startPositionMs = */
                        queue.getOrNull(index)?.position ?: C.TIME_UNSET
                    )
                    // Not prepared here: resolving a stream at launch competes with the UI.
                    // onPlayWhenReadyChanged prepares on the first play.

                    runCatching {
                        isNotificationStarted = true
                        startForeground()
                    }
                }.onFailure { Log.w(TAG, "Failed to restore player queue on startup", it) }
            }
        }
    }

    private fun maybeNormalizeVolume() {
        if (!PlayerPreferences.volumeNormalization) {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            volumeNormalizationJob?.cancel()
            volumeNormalizationJob?.invokeOnCompletion { volumeNormalizationJob = null }
            player.volume = 1f
            return
        }

        // Session 0 is the global output mix; never attach to it.
        if (player.audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        runCatching {
            if (loudnessEnhancer == null) loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
        }.onFailure { return }

        val songId = player.currentMediaItem?.mediaId ?: return
        volumeNormalizationJob?.cancel()
        volumeNormalizationJob = coroutineScope.launch {
            runCatching {
                fun Float?.toMb() = ((this ?: 0f) * 100).toInt()

                Database.loudnessDb(songId).cancellable().collectLatest { loudness ->
                    val loudnessMb = loudness.toMb().let {
                        if (it !in -2000..2000) {
                            withContext(Dispatchers.Main) {
                                toast(
                                    getString(
                                        R.string.loudness_normalization_extreme,
                                        getString(R.string.format_db, (it / 100f).toString())
                                    )
                                )
                            }

                            0
                        } else {
                            it
                        }
                    }

                    Database.loudnessBoost(songId).cancellable().collectLatest { boost ->
                        withContext(Dispatchers.Main) {
                            loudnessEnhancer?.setTargetGain(
                                PlayerPreferences.volumeNormalizationBaseGain.toMb() + boost.toMb() - loudnessMb
                            )
                            loudnessEnhancer?.enabled = true
                        }
                    }
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod") // TODO: evaluate CyclomaticComplexMethod threshold
    private fun maybeSponsorBlock() {
        poiTimestamp = null

        if (!PlayerPreferences.sponsorBlockEnabled) {
            sponsorBlockJob?.cancel()
            sponsorBlockJob?.invokeOnCompletion { sponsorBlockJob = null }
            return
        }

        sponsorBlockJob?.cancel()
        sponsorBlockJob = coroutineScope.launch {
            mediaItemState.onStart { emit(mediaItemState.value) }.collectLatest { mediaItem ->
                poiTimestamp = null
                val videoId = mediaItem?.mediaId
                    ?.removePrefix("https://youtube.com/watch?v=")
                    ?.takeIf { it.isNotBlank() } ?: return@collectLatest

                SponsorBlock
                    .segments(videoId)
                    ?.onSuccess { segments ->
                        poiTimestamp =
                            segments.find { it.category == Category.PoiHighlight }?.start?.inWholeMilliseconds
                    }
                    ?.map { segments ->
                        segments
                            .sortedBy { it.start.inWholeMilliseconds }
                            .filter { it.action == Action.Skip }
                    }
                    ?.mapCatching { segments ->
                        suspend fun posMillis() =
                            withContext(Dispatchers.Main) { player.currentPosition }

                        suspend fun speed() =
                            withContext(Dispatchers.Main) { player.playbackParameters.speed }

                        suspend fun seek(millis: Long) =
                            withContext(Dispatchers.Main) { player.seekTo(millis) }

                        val ctx = currentCoroutineContext()
                        val lastSegmentEnd =
                            segments.lastOrNull()?.end?.inWholeMilliseconds ?: return@mapCatching

                        @Suppress("LoopWithTooManyJumpStatements")
                        do {
                            if (lastSegmentEnd < posMillis()) {
                                yield()
                                continue
                            }

                            val nextSegment =
                                segments.firstOrNull { posMillis() < it.end.inWholeMilliseconds }
                                    ?: continue

                            // Wait for next segment
                            if (nextSegment.start.inWholeMilliseconds > posMillis()) {
                                val timeNextSegment =
                                    nextSegment.start.inWholeMilliseconds - posMillis()
                                val speed = speed().toDouble()
                                delay((timeNextSegment / speed).milliseconds)
                            }

                            if (posMillis().milliseconds !in nextSegment.start..nextSegment.end) {
                                // Player is not in the segment for some reason, maybe the user seeked in the meantime
                                yield()
                                continue
                            }

                            seek(nextSegment.end.inWholeMilliseconds)
                        } while (ctx.isActive)
                    }?.onFailure {
                        it.printStackTrace()
                    }
            }
        }
    }

    private fun maybeShowSongCoverInLockScreen() = handler.post {
        val bitmap = if (isAtLeastAndroid13 || AppearancePreferences.isShowingThumbnailInLockscreen) {
            bitmapProvider.bitmap
        } else {
            null
        }
        val uri = player.mediaMetadata.artworkUri?.toString()?.thumbnail(512)

        metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ART_URI, uri)

        metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, uri)

        if (isAtLeastAndroid13 && player.currentMediaItemIndex == 0) {
            metadataBuilder.putText(
                MediaMetadata.METADATA_KEY_TITLE,
                "${player.mediaMetadata.title} "
            )
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun maybeResumePlaybackWhenDeviceConnected() {
        if (!isAtLeastAndroid6) return

        if (!PlayerPreferences.resumePlaybackWhenDeviceConnected) {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            audioDeviceCallback = null
            return
        }
        if (audioManager == null) audioManager = getSystemService<AudioManager>()

        audioDeviceCallback = object : AudioDeviceCallback() {
            private fun canPlayMusic(audioDeviceInfo: AudioDeviceInfo) =
                audioDeviceInfo.isSink && (
                    audioDeviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        audioDeviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        audioDeviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    )
                    .let {
                        if (!isAtLeastAndroid8) {
                            it
                        } else {
                            it || audioDeviceInfo.type == AudioDeviceInfo.TYPE_USB_HEADSET
                        }
                    }

            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                if (!player.isPlaying && addedDevices.any(::canPlayMusic)) player.play()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = Unit
        }

        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, handler)
    }

    private fun openEqualizer() =
        EqualizerIntentBundleAccessor.sendOpenEqualizer(player.audioSessionId)

    private fun closeEqualizer() =
        EqualizerIntentBundleAccessor.sendCloseEqualizer(player.audioSessionId)

    private fun updatePlaybackState() = coroutineScope.launch {
        playbackStateMutex.withLock {
            withContext(Dispatchers.Main) {
                mediaSession.setPlaybackState(
                    stateBuilder
                        .setActiveQueueItemId(player.currentMediaItemIndex.toLong())
                        .setState(
                            player.androidPlaybackState,
                            player.currentPosition,
                            player.playbackParameters.speed,
                            SystemClock.elapsedRealtime()
                        )
                        .setBufferedPosition(player.bufferedPosition)
                        .build()
                )
            }
        }
    }

    private val Player.androidPlaybackState
        get() = when (playbackState) {
            Player.STATE_BUFFERING -> if (playWhenReady) PlaybackState.STATE_BUFFERING else PlaybackState.STATE_PAUSED
            Player.STATE_READY -> if (playWhenReady) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            Player.STATE_ENDED -> PlaybackState.STATE_STOPPED
            Player.STATE_IDLE -> PlaybackState.STATE_NONE
            else -> PlaybackState.STATE_NONE
        }

    // legacy behavior may cause inconsistencies, but not available on sdk 24 or lower
    @Suppress("DEPRECATION")
    override fun onEvents(player: Player, events: Player.Events) {
        if (player.duration != C.TIME_UNSET) {
            mediaSession.setMetadata(
                metadataBuilder
                    .putText(
                        MediaMetadata.METADATA_KEY_TITLE,
                        player.mediaMetadata.title?.toString().orEmpty()
                    )
                    .putText(
                        MediaMetadata.METADATA_KEY_ARTIST,
                        player.mediaMetadata.artist?.toString().orEmpty()
                    )
                    .putText(
                        MediaMetadata.METADATA_KEY_ALBUM,
                        player.mediaMetadata.albumTitle?.toString().orEmpty()
                    )
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, player.duration)
                    .build()
            )
        }

        updatePlaybackState()

        if (
            !events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_IS_LOADING_CHANGED,
                Player.EVENT_MEDIA_METADATA_CHANGED
            )
        ) {
            return
        }

        val notification = notification()

        if (notification == null) {
            isNotificationStarted = false
            makeInvincible(false)
            stopForeground(false)
            closeEqualizer()
            ServiceNotifications.default.cancel(this)
            return
        }

        if (player.shouldBePlaying && !isNotificationStarted) {
            isNotificationStarted = true
            startForegroundService(this@PlayerService, intent<PlayerService>())
            startForeground()
            makeInvincible(false)
            openEqualizer()
        } else {
            if (!player.shouldBePlaying) {
                isNotificationStarted = false
                stopForeground(false)
                makeInvincible(true)
                closeEqualizer()
            }
            updateNotification()
        }
    }

    private fun notification(): (NotificationCompat.Builder.() -> NotificationCompat.Builder)? {
        if (player.currentMediaItem == null) return null

        val mediaMetadata = player.mediaMetadata

        bitmapProvider.load(mediaMetadata.artworkUri) {
            maybeShowSongCoverInLockScreen()
            updateNotification()
        }

        return {
            @Suppress("DEPRECATION")
            this
                .setContentTitle(mediaMetadata.title?.toString().orEmpty())
                .setContentText(mediaMetadata.artist?.toString().orEmpty())
                .setSubText(player.playerError?.message)
                .setLargeIcon(bitmapProvider.bitmap)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setSmallIcon(
                    player.playerError?.let { R.drawable.alert_circle } ?: R.drawable.app_icon
                )
                .setOngoing(false)
                .setContentIntent(
                    activityPendingIntent<MainActivity>(flags = PendingIntent.FLAG_UPDATE_CURRENT)
                )
                .setDeleteIntent(broadcastPendingIntent<NotificationDismissReceiver>())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .addAction(
                    R.drawable.play_skip_back,
                    getString(R.string.skip_back),
                    notificationActionReceiver.previous.pendingIntent
                )
                .let {
                    if (player.shouldBePlaying) {
                        it.addAction(
                            R.drawable.pause,
                            getString(R.string.pause),
                            notificationActionReceiver.pause.pendingIntent
                        )
                    } else {
                        it.addAction(
                            R.drawable.play,
                            getString(R.string.play),
                            notificationActionReceiver.play.pendingIntent
                        )
                    }
                }
                .addAction(
                    R.drawable.play_skip_forward,
                    getString(R.string.skip_forward),
                    notificationActionReceiver.next.pendingIntent
                )
                .addAction(
                    if (isLikedState.value) R.drawable.heart else R.drawable.heart_outline,
                    getString(R.string.like),
                    notificationActionReceiver.like.pendingIntent
                )
                .addAction(
                    PlayerPreferences.repeatMode.icon,
                    getString(R.string.repeat),
                    notificationActionReceiver.loop.pendingIntent
                )
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(MediaSessionCompat.Token.fromToken(mediaSession.sessionToken))
                )
        }
    }

    private fun updateNotification() = runCatching {
        handler.post {
            notification()?.let { ServiceNotifications.default.sendNotification(this, it) }
        }
    }

    override fun startForeground() {
        notification()
            ?.let { ServiceNotifications.default.startForeground(this, it) }
    }

    private fun createMediaSourceFactory() = DefaultMediaSourceFactory(
        /* dataSourceFactory = */
        createYouTubeDataSourceResolverFactory(
            findMediaItem = { videoId ->
                withContext(Dispatchers.Main) {
                    player.findNextMediaItemById(videoId)
                }
            },
            context = applicationContext,
            cache = cache
        ),
        /* extractorsFactory = */
        DefaultExtractorsFactory()
    ).setLoadErrorHandlingPolicy(
        object : DefaultLoadErrorHandlingPolicy() {
            override fun isEligibleForFallback(exception: IOException) = true
        }
    )

    private fun createRendersFactory() = object : DefaultRenderersFactory(this) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink {
            val minimumSilenceDuration =
                PlayerPreferences.minimumSilence.coerceIn(1000L..2_000_000L)

            @Suppress("DEPRECATION")
            return DefaultAudioSink.Builder(applicationContext)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                .setAudioOffloadSupportProvider(
                    DefaultAudioOffloadSupportProvider(applicationContext)
                )
                .setAudioProcessorChain(
                    DefaultAudioProcessorChain(
                        arrayOf(audioEffects.widener),
                        SilenceSkippingAudioProcessor(
                            /* minimumSilenceDurationUs = */
                            minimumSilenceDuration,
                            /* silenceRetentionRatio = */
                            0.01f,
                            /* maxSilenceToKeepDurationUs = */
                            minimumSilenceDuration,
                            /* minVolumeToKeepPercentageWhenMuting = */
                            0,
                            /* silenceThresholdLevel = */
                            256
                        ),
                        SonicAudioProcessor()
                    )
                )
                .build()
                .apply {
                    if (isAtLeastAndroid10) setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED)
                }
        }
    }

    @Stable
    inner class Binder : AndroidBinder() {
        val player: ExoPlayer
            get() = this@PlayerService.player

        val cache: Cache
            get() = this@PlayerService.cache

        val mediaSession
            get() = this@PlayerService.mediaSession

        val audioEffects: StateFlow<AudioCapabilities?>
            get() = this@PlayerService.audioEffects.capabilities

        val sleepTimerMillisLeft: StateFlow<Long?>?
            get() = timerJob?.millisLeft

        private var radioJob: Job? = null

        var isLoadingRadio by mutableStateOf(false)
            private set

        var invincible
            get() = isInvincibilityEnabled
            set(value) {
                isInvincibilityEnabled = value
            }

        val poiTimestamp get() = this@PlayerService.poiTimestamp

        fun setBitmapListener(listener: ((Bitmap?) -> Unit)?) = bitmapProvider.setListener(listener)

        @kotlin.OptIn(FlowPreview::class)
        fun startSleepTimer(delayMillis: Long) {
            timerJob?.cancel()

            timerJob = coroutineScope.timer(delayMillis) {
                ServiceNotifications.sleepTimer.sendNotification(this@PlayerService) {
                    this
                        .setContentTitle(getString(R.string.sleep_timer_ended))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true)
                        .setShowWhen(true)
                        .setSmallIcon(R.drawable.app_icon)
                }

                handler.post {
                    player.pause()
                    player.stop()

                    glyphInterface.glyph {
                        turnOff()
                    }
                }
            }.also { job ->
                glyphInterface.progress(
                    job
                        .millisLeft
                        .takeWhile { it != null }
                        .debounce(500.milliseconds)
                        .map { ((it ?: 0L) / delayMillis.toFloat() * 100).toInt() }
                )
            }
        }

        fun cancelSleepTimer() {
            timerJob?.cancel()
            timerJob = null
        }

        fun setupRadio(endpoint: NavigationEndpoint.Endpoint.Watch?) =
            startRadio(endpoint = endpoint, justAdd = true)

        fun playRadio(endpoint: NavigationEndpoint.Endpoint.Watch?) =
            startRadio(endpoint = endpoint, justAdd = false)

        private fun startRadio(endpoint: NavigationEndpoint.Endpoint.Watch?, justAdd: Boolean) {
            radioJob?.cancel()
            radio = null

            YouTubeRadio(
                endpoint?.videoId,
                endpoint?.playlistId,
                endpoint?.playlistSetVideoId,
                endpoint?.params
            ).let { radioData ->
                isLoadingRadio = true
                radioJob = coroutineScope.launch {
                    val items = radioData.process().let { Database.filterBlacklistedSongs(it) }

                    withContext(Dispatchers.Main) {
                        if (justAdd) {
                            player.addMediaItems(items.drop(1))
                        } else {
                            player.forcePlayFromBeginning(items)
                        }
                    }

                    radio = radioData
                    isLoadingRadio = false
                }
            }
        }

        fun stopRadio() {
            isLoadingRadio = false
            radioJob?.cancel()
            radio = null
        }

        /**
         * This method should ONLY be called when the application (sc. activity) is in the foreground!
         */
        fun restartForegroundOrStop() {
            player.pause()
            isInvincibilityEnabled = false
            stopSelf()
        }

        fun handleCustomAction(action: String) = when (action) {
            LIKE_ACTION -> likeAction()
            LOOP_ACTION -> loopAction()
            else -> Unit
        }

        fun isCached(song: SongWithContentLength) =
            song.contentLength?.let { cache.isCached(song.song.id, 0L, it) } ?: false

        fun playFromSearch(query: String) {
            coroutineScope.launch {
                Innertube.searchPage(
                    body = SearchBody(
                        query = query,
                        params = Innertube.SearchFilter.Song.value
                    ),
                    fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
                )
                    ?.getOrNull()
                    ?.items
                    ?.firstOrNull()
                    ?.info
                    ?.endpoint
                    ?.let { playRadio(it) }
            }
        }
    }

    private fun likeAction() = mediaItemState.value?.let { mediaItem ->
        query {
            runCatching {
                val newLikedAt = if (isLikedState.value) null else System.currentTimeMillis()
                if (
                    Database.like(
                        songId = mediaItem.mediaId,
                        likedAt = newLikedAt
                    ) == 0
                ) {
                    Database.insert(mediaItem) { it.copy(likedAt = newLikedAt) }
                }
            }
        }
    }.let { }

    private fun loopAction() {
        PlayerPreferences.repeatMode = PlayerPreferences.repeatMode.next
    }

    private inner class SessionCallback : MediaSession.Callback() {
        override fun onPlay() = player.play()
        override fun onPause() = player.pause()
        override fun onSkipToPrevious() = runCatching(player::forceSeekToPrevious).let { }
        override fun onSkipToNext() = runCatching(player::forceSeekToNext).let { }
        override fun onSeekTo(pos: Long) = player.seekTo(pos)
        override fun onStop() = player.pause()
        override fun onRewind() = player.seekToDefaultPosition()
        override fun onSkipToQueueItem(id: Long) =
            runCatching { player.seekToDefaultPosition(id.toInt()) }.let { }

        override fun onSetPlaybackSpeed(speed: Float) {
            PlayerPreferences.speed = speed.coerceIn(0.01f..2f)
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            if (query.isNullOrBlank()) return
            binder.playFromSearch(query)
        }

        override fun onCustomAction(action: String, extras: Bundle?) {
            super.onCustomAction(action, extras)
            when (action) {
                LIKE_ACTION -> likeAction()
                LOOP_ACTION -> loopAction()
            }
        }
    }

    inner class NotificationActionReceiver internal constructor() :
        ActionReceiver("com.k4music.android") {
        val pause by action { _, _ ->
            player.pause()
        }
        val play by action { _, _ ->
            player.play()
        }
        val next by action { _, _ ->
            player.forceSeekToNext()
        }
        val previous by action { _, _ ->
            player.forceSeekToPrevious()
        }
        val like by action { _, _ ->
            likeAction()
        }
        val loop by action { _, _ ->
            loopAction()
        }
    }

    class NotificationDismissReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = with(context) {
            stopService(intent<PlayerService>())
            Unit
        }
    }

    companion object {
        private const val DEFAULT_CACHE_DIRECTORY = "exoplayer"
        private const val DEFAULT_CHUNK_LENGTH = 512 * 1024L
        private const val YT_DLP_TIMEOUT_MS = 15_000L
        private const val RECOVERY_DELAY_MS = 300L
        private const val PYTHON_WARM_UP_DELAY_MS = 8_000L
        private val ytDlpScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun createDatabaseProvider(context: Context) = StandaloneDatabaseProvider(context)
        fun createCache(
            context: Context,
            directoryName: String = DEFAULT_CACHE_DIRECTORY,
            size: ExoPlayerDiskCacheSize = DataPreferences.exoPlayerDiskCacheMaxSize
        ) = with(context) {
            val cacheEvictor = when (size) {
                ExoPlayerDiskCacheSize.Unlimited -> NoOpCacheEvictor()
                else -> LeastRecentlyUsedCacheEvictor(size.bytes)
            }

            val directory = cacheDir.resolve(directoryName).apply {
                if (!exists()) mkdir()
            }

            SimpleCache(directory, cacheEvictor, createDatabaseProvider(context))
        }

        @Suppress("CyclomaticComplexMethod")
        fun createYouTubeDataSourceResolverFactory(
            context: Context,
            cache: Cache,
            chunkLength: Long? = DEFAULT_CHUNK_LENGTH,
            findMediaItem: suspend (videoId: String) -> MediaItem? = { null },
            uriCache: UriCache<String, Long?> = UriCache()
        ): DataSource.Factory {
            val rejectedDirectStreams = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val streamHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
            return ResolvingDataSource.Factory(
            ConditionalCacheDataSourceFactory(
                cacheDataSourceFactory = cache.readOnlyWhen { PlayerPreferences.pauseCache }.asDataSource,
                upstreamDataSourceFactory = context.defaultDataSource,
                shouldCache = { !it.isLocal }
            )
        ) { dataSpec ->
            val mediaId = dataSpec.key?.removePrefix("https://youtube.com/watch?v=")
                ?: error("A key must be set")

            fun DataSpec.ranged(contentLength: Long?) = contentLength?.let {
                if (chunkLength == null) return@let null

                val remaining = contentLength - dataSpec.position
                if (remaining <= 0L) return@let null
                val length = remaining.coerceAtMost(chunkLength).let { available ->
                    if (dataSpec.length > 0L) available.coerceAtMost(dataSpec.length) else available
                }
                // Media3 generates the Range header from position/length; never shift the seek twice.
                this.subrange(0L, length)
            } ?: this

            fun DataSpec.withRequestHeaders(headers: Map<String, String>) = buildUpon()
                .setHttpRequestHeaders(headers + httpRequestHeaders)
                .build()

            // YouTube could not serve this song: play the same track from JioSaavn under the same
            // cache key. The old bytes are purged first so two sources never mix in one file.
            fun saavnFallback(): DataSpec? {
                runCatching { cache.removeResource(mediaId) }
                uriCache.remove(mediaId)
                val item = runCatching { runBlocking(Dispatchers.IO) { findMediaItem(mediaId) } }.getOrNull()
                val song = if (item != null) null
                else runCatching { runBlocking(Dispatchers.IO) { Database.song(mediaId).first() } }.getOrNull()
                val title = item?.mediaMetadata?.title?.toString() ?: song?.title
                val artist = item?.mediaMetadata?.artist?.toString() ?: song?.artistsText.orEmpty()
                val durationText = item?.mediaMetadata?.extras?.songBundle?.durationText ?: song?.durationText
                val url = title?.let {
                    runCatching {
                        runBlocking(Dispatchers.IO) { findSaavnStream(it, artist, durationText?.let(::parseClockToMs)?.takeIf { ms -> ms > 0 }) }
                    }.getOrNull()
                }
                StreamFallback.resolved(mediaId, url)
                if (url == null) {
                    Log.w(TAG, "No JioSaavn fallback for $mediaId ($title)")
                    return null
                }
                Log.i(TAG, "Playing $mediaId from JioSaavn fallback")
                uriCache.push(key = mediaId, meta = null, uri = url.toUri())
                return dataSpec.withUri(url.toUri())
            }

            val fallbackOn = PlayerPreferences.smartFallback
            if (!fallbackOn && StreamFallback.url(mediaId) != null) {
                // Fallback turned off: forget the JioSaavn copy and its cached bytes, back to YouTube.
                StreamFallback.drop(mediaId)
                uriCache.remove(mediaId)
                runCatching { cache.removeResource(mediaId) }
            }

            if (!dataSpec.isLocal && fallbackOn && StreamFallback.isForced(mediaId)) {
                saavnFallback() ?: throw UnplayableException()
            } else if (
                dataSpec.isLocal || (
                    chunkLength != null && cache.isCached(
                        /* key = */
                        mediaId,
                        /* position = */
                        dataSpec.position,
                        /* length = */
                        chunkLength
                    )
                    )
            ) {
                dataSpec
            } else {
                uriCache[mediaId]?.let { cachedUri ->
                    dataSpec
                        .withUri(cachedUri.uri)
                        .withRequestHeaders(streamHeaders[mediaId].orEmpty())
                        .ranged(cachedUri.meta)
                } ?: run {
                    if (isSaavnKey(mediaId)) {
                        // K4Music: JioSaavn-backed item — resolve its CDN url once
                        // and remember it, skipping the YouTube/yt-dlp path entirely.
                        val cdn = runBlocking(Dispatchers.IO) { resolveSaavnStream(mediaId) }
                            ?: throw UnplayableException()
                        uriCache.push(key = mediaId, meta = null, uri = cdn.toUri())
                        return@run dataSpec.withUri(cdn.toUri())
                    }

                    StreamFallback.url(mediaId)?.let { saved ->
                        uriCache.push(key = mediaId, meta = null, uri = saved.toUri())
                        return@run dataSpec.withUri(saved.toUri())
                    }

                    val body = if (mediaId in rejectedDirectStreams) null else runBlocking(Dispatchers.IO) {
                        com.k4music.android.utils.networkResult { Innertube.player(PlayerBody(videoId = mediaId)) }
                    }.getOrNull()
                    val youtubeFormat = body?.streamingData?.highestQualityFormat

                    val directUrl = youtubeFormat?.url?.takeIf { it.isNotBlank() && mediaId !in rejectedDirectStreams }
                    val uri: android.net.Uri
                    val formatId: String?
                    val bitrate: Long?
                    val contentLength: Long?

                    if (directUrl != null) {
                        uri = directUrl.toUri()
                        formatId = youtubeFormat.itag.toString()
                        bitrate = youtubeFormat.bitrate
                        contentLength = youtubeFormat.contentLength
                    } else {
                        // yt-dlp can hang on a slow network; past the deadline JioSaavn takes over.
                        val job = ytDlpScope.async { Dependencies.runDownload(mediaId) }
                        val info = runCatching {
                            runBlocking { withTimeoutOrNull(YT_DLP_TIMEOUT_MS) { job.await() } }
                                ?: error("yt-dlp timed out for $mediaId")
                        }.mapCatching {
                            YouTubeDLResponse.fromString(it)
                        }.onFailure { Log.w(TAG, "yt-dlp failed for $mediaId", it) }.getOrNull()
                        if (info?.id != null && info.id != mediaId) throw VideoIdMismatchException()
                        val format = info?.formats?.firstOrNull { it.formatId == info.formatId }
                        uri = (info?.url?.takeIf { it.isNotBlank() } ?: format?.url?.takeIf { it.isNotBlank() })?.toUri()
                            ?: return@run (if (fallbackOn) saavnFallback() else null) ?: throw UnplayableException()
                        formatId = info?.formatId
                        bitrate = format?.abr?.let { it * 1000 }?.toLong()
                        contentLength = info?.fileSize?.takeIf { it > 0L }
                        if (streamHeaders.size >= 64) streamHeaders.clear()
                        streamHeaders[mediaId] = info?.httpHeaders.orEmpty()
                    }

                    val mediaItem = runCatching {
                        runBlocking(Dispatchers.IO) { findMediaItem(mediaId) }
                    }.getOrNull()

                    val extras = mediaItem?.mediaMetadata?.extras?.songBundle
                    if (extras?.durationText == null) {
                        body
                            ?.streamingData
                            ?.highestQualityFormat
                            ?.approxDurationMs
                            ?.div(1000)
                            ?.let(DateUtils::formatElapsedTime)
                            ?.removePrefix("0")
                            ?.let { durationText ->
                                extras?.durationText = durationText
                                Database.updateDurationText(mediaId, durationText)
                            }
                    }

                    transaction {
                        runCatching {
                            mediaItem?.let(Database::insert)
                            Database.insert(
                                Format(
                                    songId = mediaId,
                                    itag = formatId?.toIntOrNull() ?: youtubeFormat?.itag,
                                    mimeType = youtubeFormat?.mimeType,
                                    bitrate = bitrate,
                                    loudnessDb = body?.playerConfig?.audioConfig?.normalizedLoudnessDb,
                                    contentLength = contentLength,
                                    lastModified = youtubeFormat?.lastModified
                                )
                            )
                        }
                    }

                    uriCache.push(
                        key = mediaId,
                        meta = contentLength,
                        uri = uri
                    )

                    dataSpec
                        .withUri(uri)
                        .withRequestHeaders(streamHeaders[mediaId].orEmpty())
                        .ranged(contentLength)
                }
            }
        }.handleUnknownErrors { error, spec ->
            // Only an HTTP refusal proves a stream url bad; a dropped socket keeps it.
            val http = error.findCause<InvalidResponseCodeException>() ?: return@handleUnknownErrors
            val id = (http.dataSpec.key ?: spec?.key)?.removePrefix("https://youtube.com/watch?v=")
                ?: return@handleUnknownErrors
            uriCache.remove(id)
            if ((http.responseCode == 403 || http.responseCode == 410) && !isSaavnKey(id) && StreamFallback.url(id) == null) {
                // A valid-looking Innertube URL can still be forbidden by the CDN.
                // Re-extract this exact video through yt-dlp on the next attempt.
                if (rejectedDirectStreams.size >= 64) rejectedDirectStreams.clear()
                rejectedDirectStreams.add(id)
            }
        }.retryIf(maxRetries = 2) { error ->
            val status = error.findCause<InvalidResponseCodeException>()?.responseCode
            if (status != null) status in setOf(403, 408, 410, 429, 500, 502, 503, 504)
            else error.findCause<IOException>() != null && error.findCause<java.io.FileNotFoundException>() == null && error.findCause<java.io.InterruptedIOException>()?.let { it !is java.net.SocketTimeoutException } != true
        }
        }
    }
}
