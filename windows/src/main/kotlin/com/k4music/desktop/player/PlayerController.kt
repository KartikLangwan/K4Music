package com.k4music.desktop.player

import com.k4music.desktop.AppLog
import com.k4music.desktop.k4.StreamFallback
import com.k4music.desktop.prefs.RepeatMode
import com.k4music.desktop.prefs.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.k4music.providers.sponsorblock.requests.segments
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** Where a YouTube Music radio starts: a song, or an artist's shuffle/radio endpoint. */
data class RadioSeed(
    val videoId: String? = null,
    val playlistId: String? = null,
    val params: String? = null,
    val playlistSetVideoId: String? = null
)

/** What the UI shows about playback. Position lives in its own flow so ticks redraw only the seek bar. */
data class PlayerState(
    /** Wall-clock time (ms) when playback pauses for the sleep timer, or null. */
    val sleepAt: Long? = null,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val durationSeconds: Double = 0.0,
    val error: String? = null,
    val source: StreamSource? = null
) {
    val current get() = queue.getOrNull(index)
    val hasNext get() = index + 1 < queue.size
}

/**
 * The single owner of playback. Every change runs on one serial dispatcher, so queue edits,
 * loads and mpv events never race. Failures follow [chooseRecovery]: retry or switch source,
 * never skip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerController(private val mpv: Mpv, private val resolver: StreamResolver = StreamResolver()) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val _position = MutableStateFlow(0.0)
    val position: StateFlow<Double> = _position.asStateFlow()

    /** Insertion order before shuffling, by track id, so turning shuffle off restores it. */
    private var unshuffled: List<Track>? = null
    private var loadJob: Job? = null
    @Volatile private var generation = 0L
    @Volatile private var queueGeneration = 0L
    internal val queueVersion get() = queueGeneration
    @Volatile private var closed = false
    @Volatile private var currentStream: Stream? = null
    @Volatile private var skipSegments: List<Pair<Double, Double>> = emptyList()
    private var sleepJob: Job? = null
    private val retries = PlaybackRetryBudget()
    private val fallbacks = PlaybackRetryBudget(limit = 1)
    // Listening time of the current song: only forward steps under 1.5 s count (seeks do not).
    @Volatile private var listenedTrack: Track? = null
    @Volatile private var listenedMs = 0L
    @Volatile private var lastPosition = 0.0

    /** Called when the queue changes (for persistence) and when a song starts (for history). */
    var onQueueChanged: (List<Track>, Int, Double) -> Unit = { _, _, _ -> }
    var onTrackStarted: (Track) -> Unit = {}
    /** A song stopped being current after [Long] ms of real listening (history, play time). */
    var onPlayed: (Track, Long) -> Unit = { _, _ -> }
    /** Asked for more songs when the queue runs out with repeat off (radio). */
    var onQueueEnding: suspend (Track) -> List<Track> = { emptyList() }
    /** The chosen sound output failed and playback moved to the Windows default (for a notice). */
    var onAudioDeviceLost: () -> Unit = {}
    @Volatile private var lastAudioRecovery = 0L
    /** Opens a radio for a seed; each call of the returned function gives the next page of songs. */
    var radioSource: (RadioSeed) -> (suspend () -> List<Track>) = { { emptyList() } }
    /** The radio feeding the queue, if the queue came from one (Android's YouTubeRadio). */
    private var radio: (suspend () -> List<Track>)? = null
    private var radioJob: Job? = null

    init {
        // Volume 0 while not muted is almost always an accident and makes the app seem broken.
        if (Settings.volume == 0 && !Settings.muted) Settings.volume = 50
        mpv.setVolume(Settings.volume)
        mpv.setMuted(Settings.muted)
        mpv.setAudioDevice(Settings.audioDevice)
        applyEffects()
        mpv.setListener(object : Mpv.Listener {
            override fun onPosition(seconds: Double) = dispatchEvent {
                // SponsorBlock: jump over a non-music segment the moment playback enters it.
                skipSegments.firstOrNull { seconds >= it.first && seconds < it.second - 0.5 }?.let { (_, end) ->
                    mpv.seek(end)
                    return@dispatchEvent
                }
                val step = seconds - lastPosition
                if (step > 0 && step < 1.5 && _state.value.playing) listenedMs += (step * 1000).toLong()
                lastPosition = seconds
                _position.value = seconds
            }
            override fun onDuration(seconds: Double) = dispatchEvent { _state.update { it.copy(durationSeconds = seconds) } }
            override fun onPaused(paused: Boolean) = dispatchEvent { _state.update { it.copy(playing = !paused) } }
            override fun onBuffering(buffering: Boolean) = dispatchEvent { _state.update { it.copy(buffering = buffering) } }
            override fun onLoaded() = dispatchEvent {
                _state.update { it.copy(buffering = false, error = null) }
                _state.value.current?.let { track -> onTrackStarted(track); prefetchNext() }
                // Keep a radio queue topped up, as Android does when three songs are left.
                _state.value.let { if (it.queue.size - it.index <= 3) fillRadio() }
            }
            override fun onAudioOutputFailed() = dispatchEvent {
                // A device that went away (Bluetooth off, headphones unplugged): never leave the
                // music silent. A chosen device falls back to the Windows default; the default
                // itself is reopened once, as Windows may have just switched it.
                val now = System.currentTimeMillis()
                if (now - lastAudioRecovery < 5_000) return@dispatchEvent
                lastAudioRecovery = now
                if (Settings.audioDevice != "auto") {
                    AppLog.w(TAG, "Audio output ${Settings.audioDevice} failed; using the Windows default")
                    Settings.audioDevice = "auto"
                    mpv.setAudioDevice("auto")
                    onAudioDeviceLost()
                } else {
                    AppLog.w(TAG, "Default audio output failed; reopening it")
                    mpv.reloadAudio()
                }
            }
            override fun onEnded(error: String?) {
                if (closed || currentStream == null) return
                val endedGeneration = generation
                scope.launch {
                    if (generation != endedGeneration) return@launch
                    val duration = _state.value.durationSeconds
                    when {
                        error != null -> onFailure(Failure.Source, error)
                        // A stream the server cut off reads as a normal end: resume it instead of moving on.
                        duration > 0 && lastPosition < duration - EARLY_END_SECONDS -> {
                            AppLog.w(TAG, "Stream ended early at ${lastPosition.toInt()}s of ${duration.toInt()}s; resuming")
                            onFailure(Failure.Network, "Stream ended early")
                        }
                        else -> onTrackFinished()
                    }
                }
            }
        })
    }

    // ---- Queue ----

    /** Native callbacks and listening counters share the same owner as queue transitions. */
    private fun dispatchEvent(block: () -> Unit) {
        val eventGeneration = generation
        scope.launch {
            if (!closed && currentStream != null && generation == eventGeneration) block()
        }
    }

    /** Preserve caller cancellation while atomically checking the queue on its serial owner. */
    internal suspend fun playIfUnchanged(track: Track, expectedVersion: Long) =
        withContext(scope.coroutineContext.minusKey(Job)) {
            if (queueGeneration == expectedVersion) playNow(listOf(track))
        }

    fun play(tracks: List<Track>, startIndex: Int = 0, startSeconds: Double = 0.0, autoPlay: Boolean = true) =
        scope.launch { playNow(tracks, startIndex, startSeconds, autoPlay) }

    /**
     * Plays [track] and fills the queue with related songs, as tapping a song does on Android:
     * the queue follows the song's mood, not other results that merely share its name.
     */
    fun playRadio(track: Track) = scope.launch {
        playNow(listOf(track))
        if (!track.isLocal && !track.isSaavn) {
            radio = radioSource(RadioSeed(videoId = track.id))
            fillRadio()
        }
    }

    /** Plays a radio from [seed] (an artist's Shuffle or Radio button). */
    fun playRadio(seed: RadioSeed) = scope.launch {
        val request = ++queueGeneration
        val source = radioSource(seed)
        val first = withContext(Dispatchers.IO) { runCatching { source() }.getOrDefault(emptyList()) }
        if (first.isEmpty() || request != queueGeneration) return@launch
        playNow(first)
        radio = source
    }

    /** Appends the radio's next page, skipping songs already queued. */
    private fun fillRadio() {
        val source = radio ?: return
        if (radioJob?.isActive == true) return
        radioJob = scope.launch {
            val more = withContext(Dispatchers.IO) { runCatching { source() }.getOrDefault(emptyList()) }
            if (radio !== source) return@launch
            val known = _state.value.queue.mapTo(HashSet()) { it.id }
            val fresh = more.filter { known.add(it.id) }.let { if (Settings.shuffleEnabled) it.shuffled() else it }
            if (fresh.isEmpty()) return@launch
            unshuffled = unshuffled?.plus(fresh)
            _state.update { it.copy(queue = it.queue + fresh) }
            changed()
        }
    }

    private fun playNow(tracks: List<Track>, startIndex: Int = 0, startSeconds: Double = 0.0, autoPlay: Boolean = true) {
        if (tracks.isEmpty() || closed) return
        queueGeneration++
        radio = null
        radioJob?.cancel()
        var queue = tracks
        var index = startIndex.coerceIn(0, tracks.lastIndex)
        unshuffled = null
        if (Settings.shuffleEnabled) {
            unshuffled = queue
            queue = shuffledFrom(queue, index)
            index = 0
        }
        _state.update { it.copy(queue = queue, index = index) }
        load(startSeconds, autoPlay)
    }

    /** Puts back a saved queue without touching the network; the first play loads it. */
    fun restore(tracks: List<Track>, index: Int, positionSeconds: Double, autoPlay: Boolean = false) = scope.launch {
        if (tracks.isEmpty()) return@launch
        val i = index.coerceIn(0, tracks.lastIndex)
        _state.update { it.copy(queue = tracks, index = i, durationSeconds = tracks[i].durationMs?.div(1000.0) ?: 0.0) }
        _position.value = positionSeconds
        if (autoPlay) load(positionSeconds)
    }

    fun playAt(index: Int) = scope.launch {
        if (index !in _state.value.queue.indices) return@launch
        _state.update { it.copy(index = index) }
        load()
    }

    /** "Play next": right after the current song; never replaces a non-empty queue. */
    fun playNext(track: Track) = scope.launch {
        val s = _state.value
        if (s.queue.isEmpty()) return@launch playNow(listOf(track))
        val queue = s.queue.toMutableList().apply { add(s.index + 1, track) }
        unshuffled = unshuffled?.plus(track)
        _state.update { it.copy(queue = queue) }
        changed()
    }

    fun addToQueue(tracks: List<Track>) = scope.launch {
        if (_state.value.queue.isEmpty()) return@launch playNow(tracks)
        unshuffled = unshuffled?.plus(tracks)
        _state.update { it.copy(queue = it.queue + tracks) }
        changed()
    }

    fun removeAt(index: Int) = scope.launch {
        val s = _state.value
        if (index !in s.queue.indices || index == s.index) return@launch
        val removed = s.queue[index]
        unshuffled = unshuffled?.let { list ->
            val idx = list.indexOf(removed)
            if (idx >= 0) list.toMutableList().apply { removeAt(idx) } else list
        }
        val queue = s.queue.toMutableList().apply { removeAt(index) }
        _state.update { it.copy(queue = queue, index = if (index < it.index) it.index - 1 else it.index) }
        changed()
    }

    fun move(from: Int, to: Int) = scope.launch {
        val s = _state.value
        if (from !in s.queue.indices || to !in s.queue.indices || from == to) return@launch
        val queue = s.queue.toMutableList().apply { add(to, removeAt(from)) }
        val index = when (s.index) {
            from -> to
            in (from + 1)..to -> s.index - 1
            in to until from -> s.index + 1
            else -> s.index
        }
        _state.update { it.copy(queue = queue, index = index) }
        changed()
    }

    fun clearQueue() = scope.launch {
        generation++
        queueGeneration++
        loadJob?.cancel()
        radio = null
        radioJob?.cancel()
        mpv.stop()
        currentStream = null
        unshuffled = null
        _state.value = PlayerState()
        _position.value = 0.0
        changed()
    }

    // ---- Transport ----

    fun togglePlay() = scope.launch {
        val s = _state.value
        when {
            s.current == null -> Unit
            loadJob?.isActive == true -> Unit // still resolving: a second press must not restart it
            s.error != null || currentStream == null -> load(_position.value)
            else -> setPaused(!mpv.isPaused())
        }
    }

    fun pause() = scope.launch { if (currentStream != null) setPaused(true) }

    private fun setPaused(paused: Boolean) {
        mpv.setPaused(paused)
        _state.update { it.copy(playing = !paused) }
        if (!paused) prefetchNext()
    }

    fun next() = scope.launch {
        val s = _state.value
        when {
            s.hasNext -> { _state.update { it.copy(index = it.index + 1) }; load() }
            Settings.repeatMode == RepeatMode.All && s.queue.isNotEmpty() -> { _state.update { it.copy(index = 0) }; load() }
        }
    }

    /** Restarts the song when past 3 s, otherwise goes back (wrapping only with Repeat all). */
    fun previous() = scope.launch {
        val s = _state.value
        when {
            _position.value > 3 || s.queue.isEmpty() -> mpv.seek(0.0)
            s.index > 0 -> { _state.update { it.copy(index = it.index - 1) }; load() }
            Settings.repeatMode == RepeatMode.All -> { _state.update { it.copy(index = it.queue.lastIndex) }; load() }
            else -> mpv.seek(0.0)
        }
    }

    /** "Stats for nerds": codec, bitrate and sample rate of what is playing now. */
    fun stats(): String? {
        val codec = mpv.getString("audio-codec-name") ?: return null
        val bitrate = mpv.getString("audio-bitrate")?.toDoubleOrNull()?.let { "${(it / 1000).toInt()} kbps" }
        val rate = mpv.getString("audio-params/samplerate")?.let { "$it Hz" }
        return listOfNotNull(codec, bitrate, rate).joinToString(" · ")
    }

    /** Gets a song's stream ready before it is clicked (hovered rows), so it starts at once. */
    fun prefetch(track: Track) = resolver.prefetch(track)

    fun seek(seconds: Double) { _position.value = seconds; mpv.seek(seconds) }
    fun setVolume(percent: Int) { Settings.volume = percent.coerceIn(0, 100); mpv.setVolume(Settings.volume) }
    fun setMuted(muted: Boolean) { Settings.muted = muted; mpv.setMuted(muted) }

    /** Sound outputs Windows offers now (refresh when Bluetooth or headphones connect). */
    fun audioDevices(): List<AudioDevice> = mpv.audioDevices()

    /** Plays through [device] from now on ("auto" = follow the Windows default). */
    fun setAudioDevice(device: String) {
        Settings.audioDevice = device
        mpv.setAudioDevice(device)
    }
    fun cycleRepeat() { Settings.repeatMode = Settings.repeatMode.next }

    fun setShuffle(enabled: Boolean) = scope.launch {
        if (Settings.shuffleEnabled == enabled) return@launch
        Settings.shuffleEnabled = enabled
        val s = _state.value
        val current = s.current ?: return@launch
        if (enabled) {
            unshuffled = s.queue
            _state.update { it.copy(queue = shuffledFrom(s.queue, s.index), index = 0) }
        } else {
            val restored = unshuffled?.filter { it in s.queue }.orEmpty() + s.queue.filterNot { it in unshuffled.orEmpty() }
            unshuffled = null
            _state.update { it.copy(queue = restored, index = restored.indexOf(current).coerceAtLeast(0)) }
        }
        changed()
    }

    private fun shuffledFrom(queue: List<Track>, index: Int): List<Track> {
        val current = queue[index]
        val rest = queue.toMutableList().apply { removeAt(index) }.shuffled()
        return listOf(current) + rest
    }

    // ---- Effects, sleep timer ----

    /** Re-applies speed and the audio filter chain (equalizer, effects, normalization, skip silence). */
    fun applyEffects() {
        mpv.setSpeed(Settings.speed.coerceIn(0.25f, 3f))
        mpv.setAudioFilters(AudioEffects.chain())
    }

    /** Pauses playback after [minutes]; null cancels the timer. */
    fun setSleepTimer(minutes: Int?) = scope.launch {
        sleepJob?.cancel()
        if (minutes == null) {
            _state.update { it.copy(sleepAt = null) }
            return@launch
        }
        val at = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepAt = at) }
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            setPaused(true)
            _state.update { it.copy(sleepAt = null) }
        }
    }

    private fun loadSponsorSegments(track: Track) {
        skipSegments = emptyList()
        if (!Settings.sponsorBlockEnabled || track.isLocal || track.isSaavn) return
        val wanted = Settings.sponsorBlockCategories.split(',').map { it.trim() }.toSet()
        scope.launch(Dispatchers.IO) {
            val segments = com.k4music.providers.sponsorblock.SponsorBlock.segments(
                videoId = track.id,
                categories = com.k4music.providers.sponsorblock.models.Category.entries.filter { it.serialName in wanted },
                actions = listOf(com.k4music.providers.sponsorblock.models.Action.Skip)
            )?.getOrNull().orEmpty()
            if (_state.value.current?.id == track.id) {
                skipSegments = segments.map { it.start.inWholeMilliseconds / 1000.0 to it.end.inWholeMilliseconds / 1000.0 }
            }
        }
    }

    // ---- Loading and recovery ----

    private fun flushListening() {
        val track = listenedTrack ?: return
        val ms = listenedMs
        listenedTrack = null
        listenedMs = 0
        if (ms > 0) onPlayed(track, ms)
    }

    private fun load(startSeconds: Double = 0.0, autoPlay: Boolean = true) {
        if (closed) return
        val loadingGeneration = ++generation
        val track = _state.value.current ?: return
        if (listenedTrack?.id != track.id) {
            flushListening()
            listenedTrack = track
        }
        lastPosition = startSeconds
        loadJob?.cancel()
        // The old song must go silent now, not after the new one resolves (yt-dlp can take seconds),
        // or its position ticks would show against the new song.
        val hadStream = currentStream != null
        currentStream = null
        if (hadStream) mpv.stop()
        retries.onMediaTransition(track.id)
        fallbacks.onMediaTransition(track.id)
        currentStream = null
        _position.value = startSeconds
        _state.update { it.copy(buffering = true, error = null, durationSeconds = track.durationMs?.div(1000.0) ?: 0.0) }
        changed()
        loadJob = scope.launch {
            try {
                val stream = resolver.resolve(track)
                if (generation != loadingGeneration || closed) return@launch
                currentStream = stream
                _state.update { it.copy(source = stream.source) }
                if (stream.source != StreamSource.Saavn) loadSponsorSegments(track) else skipSegments = emptyList()
                mpv.load(stream.url, startSeconds, stream.headers)
                setPaused(!autoPlay)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Could not resolve ${track.id}", e)
                onFailure(if (e is IOException || !online()) Failure.Network else Failure.Source, e.message)
            }
        }
    }

    private suspend fun onFailure(failure: Failure, message: String?) {
        val failedGeneration = generation
        val track = _state.value.current ?: return
        val stream = currentStream
        if (stream != null) resolver.reject(track, stream)
        val recovery = chooseRecovery(
            failure = failure,
            id = track.id,
            fallbackAllowed = !track.isSaavn && !track.isLocal && Settings.smartFallback,
            retries = retries,
            fallbacks = fallbacks
        )
        AppLog.w(TAG, "Playback failure for ${track.id} ($failure: $message) -> $recovery")
        when (recovery) {
            Recovery.Retry -> { delay(RECOVERY_DELAY_MS); if (generation == failedGeneration) load(_position.value) }
            Recovery.Fallback -> {
                delay(RECOVERY_DELAY_MS)
                if (generation == failedGeneration) { StreamFallback.force(track.id); load(_position.value) }
            }
            Recovery.None -> _state.update { it.copy(buffering = false, playing = false, error = message ?: "Playback failed") }
        }
    }

    private suspend fun onTrackFinished() {
        val s = _state.value
        when {
            Settings.repeatMode == RepeatMode.One -> load()
            s.hasNext -> { _state.update { it.copy(index = it.index + 1) }; load() }
            Settings.repeatMode == RepeatMode.All && s.queue.isNotEmpty() -> { _state.update { it.copy(index = 0) }; load() }
            else -> {
                val currentTrack = s.current
                val finishingGeneration = generation
                val finishingQueue = queueGeneration
                val more = currentTrack?.let { runCatching { onQueueEnding(it) }.getOrNull() }.orEmpty()
                    .filter { candidate -> _state.value.queue.none { it.id == candidate.id } }
                // Only apply radio if the user hasn't switched songs or changed playlists during lookup
                if (generation != finishingGeneration || queueGeneration != finishingQueue) return
                if (more.isNotEmpty()) {
                    _state.update { it.copy(queue = it.queue + more, index = it.index + 1) }
                    load()
                } else {
                    // End of the queue: rewind to the first song and pause (Android behaviour).
                    _state.update { it.copy(index = 0) }
                    load(autoPlay = false)
                }
            }
        }
    }

    /** Resolves the next two songs ahead of time so changing song is instant. */
    private fun prefetchNext() {
        val s = _state.value
        if (!s.playing) return
        (1..2).mapNotNull { s.queue.getOrNull(s.index + it) }.forEach(resolver::prefetch)
    }

    private fun changed() {
        queueGeneration++
        val s = _state.value
        onQueueChanged(s.queue, s.index, _position.value)
    }

    private suspend fun online(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { it.connect(InetSocketAddress("music.youtube.com", 443), 3_000) }
            true
        }.getOrDefault(false)
    }

    fun close() = runBlocking {
        // Join the serial playback dispatcher before disposing native resources.
        withContext(scope.coroutineContext.minusKey(Job)) {
            if (closed) return@withContext
            closed = true
            currentStream = null
            generation++
            scope.cancel()
            flushListening()
            changed()
            QueueStore.flush()
            resolver.close()
            mpv.close()
        }
    }

    private companion object {
        const val TAG = "Player"
        const val RECOVERY_DELAY_MS = 300L
        /** An end this far before the song's length is a broken stream, not the song finishing. */
        const val EARLY_END_SECONDS = 5.0
    }
}
