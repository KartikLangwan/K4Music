package com.k4music.android

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.work.Configuration
import com.k4music.app.BuildConfig
import com.k4music.app.R
import com.k4music.android.preferences.AppearancePreferences
import com.k4music.android.preferences.DataPreferences
import com.k4music.android.service.PlayerService
import com.k4music.android.service.ServiceNotifications
import com.k4music.android.service.downloadState
import com.k4music.android.ui.components.BottomSheetMenu
import com.k4music.android.ui.components.rememberBottomSheetState
import com.k4music.android.ui.components.themed.LinearProgressIndicator
import com.k4music.android.ui.screens.albumRoute
import com.k4music.android.ui.screens.artistRoute
import com.k4music.android.ui.screens.home.HomeScreen
import com.k4music.android.ui.components.StartupScreen
import com.k4music.android.ui.theme.K4Theme
import com.k4music.android.ui.screens.player.Player
import com.k4music.android.ui.screens.player.Thumbnail
import com.k4music.android.ui.screens.playlistRoute
import com.k4music.android.ui.screens.searchResultRoute
import com.k4music.android.ui.screens.settingsRoute
import com.k4music.android.utils.CrashGuard
import com.k4music.android.utils.DisposableListener
import com.k4music.android.utils.KeyedCrossfade
import com.k4music.android.utils.LocalMonetCompat
import com.k4music.android.utils.asMediaItem
import com.k4music.android.utils.collectProvidedBitmapAsState
import com.k4music.android.utils.forcePlay
import com.k4music.android.utils.intent
import com.k4music.android.utils.invokeOnReady
import com.k4music.android.utils.isInPip
import com.k4music.android.utils.maybeEnterPip
import com.k4music.android.utils.maybeExitPip
import com.k4music.android.utils.setDefaultPalette
import com.k4music.android.utils.shouldBePlaying
import com.k4music.android.utils.toast
import com.k4music.compose.persist.LocalPersistMap
import com.k4music.compose.persist.PersistMap
import com.k4music.compose.preferences.PreferencesHolder
import com.k4music.core.ui.Darkness
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.SystemBarAppearance
import com.k4music.core.ui.amoled
import com.k4music.core.ui.appearance
import com.k4music.core.ui.rippleConfiguration
import com.k4music.core.ui.shimmerTheme
import com.k4music.core.ui.utils.activityIntentBundle
import com.k4music.core.ui.utils.isAtLeastAndroid12
import com.k4music.core.ui.utils.isAtLeastAndroid13
import com.k4music.core.ui.utils.isAtLeastAndroid17
import com.k4music.core.ui.utils.songBundle
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.BrowseBody
import com.k4music.providers.innertube.requests.playlistPage
import com.k4music.providers.innertube.requests.song
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.bitmapFactoryExifOrientationStrategy
import coil3.decode.ExifOrientationStrategy
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.kieronquinn.monetcompat.core.MonetActivityAccessException
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.interfaces.MonetColorsChangedListener
import com.valentinilk.shimmer.LocalShimmerTheme
import dev.kdrag0n.monet.theme.ColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob

private const val TAG = "MainActivity"
private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, error -> Log.e("K4Music", "Unhandled background error", error) })

// Viewmodel in order to avoid recreating the entire Player state (WORKAROUND)
class MainViewModel : ViewModel() {
    var binder: PlayerService.Binder? by mutableStateOf(null)
    var ready by mutableStateOf(false)
    var startupError: String? by mutableStateOf(null)

    suspend fun awaitBinder(): PlayerService.Binder =
        withTimeout(15_000) { binder ?: snapshotFlow { binder }.filterNotNull().first() }
}

class MainActivity : ComponentActivity(), MonetColorsChangedListener {
    private val vm: MainViewModel by viewModels()
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is PlayerService.Binder) vm.binder = service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            vm.binder = null
            // Android retains this binding and reconnects it automatically.
        }

        override fun onBindingDied(name: ComponentName?) {
            releaseService()
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) connectService()
        }

        override fun onNullBinding(name: ComponentName?) {
            releaseService()
        }
    }

    private var _monet: MonetCompat? by mutableStateOf(null)

    private fun connectService() {
        if (serviceBound || !vm.ready) return
        serviceBound = runCatching {
            bindService(intent<PlayerService>(), serviceConnection, BIND_AUTO_CREATE)
        }.onFailure { Log.e(TAG, "Unable to bind playback service", it) }.getOrDefault(false)
    }

    private fun releaseService() {
        if (serviceBound) runCatching { unbindService(serviceConnection) }
            .onFailure { Log.w(TAG, "Unable to release playback binding", it) }
        serviceBound = false
        vm.binder = null
    }

    private fun initializeApp() = lifecycleScope.launch {
        vm.startupError = null
        try {
            withContext(Dispatchers.IO) { DatabaseDependency.verifyOrRebuild() }
            vm.ready = true
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) connectService()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Library startup initial attempt failed, attempting safe auto-recovery", error)
            val recovered = runCatching {
                withContext(Dispatchers.IO) {
                    val dbDir = File(filesDir.parentFile, "databases")
                    File(dbDir, "data.db-wal").delete()
                    File(dbDir, "data.db-shm").delete()
                    DatabaseDependency.verifyOrRebuild()
                }
                true
            }.getOrDefault(false)

            if (recovered) {
                Log.i(TAG, "Library auto-recovery succeeded")
                vm.ready = true
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) connectService()
            } else {
                vm.startupError = getString(R.string.library_startup_error)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        try {
            MonetCompat.setup(this)
            _monet = MonetCompat.getInstance()
            _monet?.setDefaultPalette()
            _monet?.addMonetColorsChangedListener(
                listener = this,
                notifySelf = false
            )
            _monet?.updateMonetColors()
        } catch (e: Exception) {
            Log.e(TAG, "MonetCompat unavailable, skipping dynamic colors", e)
            _monet = null
        }
        // Always render immediately; media permissions and wallpaper colors are optional.
        setContent()
        if (!vm.ready) initializeApp()

        intent?.let { handleIntent(it) }
        addOnNewIntentListener(::handleIntent)
    }

    @Composable
    fun AppWrapper(
        modifier: Modifier = Modifier,
        content: @Composable BoxWithConstraintsScope.() -> Unit
    ) = with(AppearancePreferences) {
        val sampleBitmap = vm.binder.collectProvidedBitmapAsState()
        val appearance = appearance(
            source = colorSource,
            mode = colorMode,
            darkness = darkness,
            fontFamily = fontFamily,
            materialAccentColor = _monet?.let {
                runCatching { Color(it.getAccentColor(this@MainActivity)) }.getOrNull()
            },
            sampleBitmap = sampleBitmap,
            applyFontPadding = applyFontPadding,
            thumbnailRoundness = thumbnailRoundness.dp,
            customAccent = com.k4music.android.ui.theme.parseHexColor(accentColorHex)
        )

        SystemBarAppearance(palette = appearance.colorPalette)

        BoxWithConstraints(
            modifier = Modifier.background(appearance.colorPalette.background0) then modifier.fillMaxSize()
        ) {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalPlayerServiceBinder provides vm.binder,
                LocalCredentialManager provides Dependencies.credentialManager,
                LocalIndication provides ripple(),
                LocalRippleConfiguration provides rippleConfiguration(appearance = appearance),
                LocalShimmerTheme provides shimmerTheme(),
                LocalLayoutDirection provides LayoutDirection.Ltr,
                LocalPersistMap provides Dependencies.application.persistMap,
                LocalMonetCompat provides _monet
            ) {
                K4Theme(appearance = appearance) { content() }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    @OptIn(ExperimentalLayoutApi::class)
    fun setContent() = setContent {
        val windowInsets = WindowInsets.systemBars

        AppWrapper(
            modifier = Modifier.padding(
                WindowInsets
                    .displayCutout
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues()
            )
        ) {
            if (!vm.ready) {
                StartupScreen(error = vm.startupError, onRetry = { initializeApp() })
                return@AppWrapper
            }
            val density = LocalDensity.current
            val bottomDp = with(density) { windowInsets.getBottom(density).toDp() }

            var hasBottomNav by remember { mutableStateOf(false) }
            val bottomNavHeight = if (hasBottomNav) 72.dp else 0.dp

            val imeVisible = WindowInsets.isImeVisible
            val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
            val animatedBottomDp by animateDpAsState(
                targetValue = if (imeVisible) 0.dp else (bottomDp + bottomNavHeight),
                label = ""
            )

            val playerBottomSheetState = rememberBottomSheetState(
                key = (vm.binder to hasBottomNav),
                dismissedBound = 0.dp,
                collapsedBound = Dimensions.items.collapsedPlayerHeight + bottomNavHeight + bottomDp,
                expandedBound = maxHeight
            )
            LaunchedEffect(vm.binder) {
                if (vm.binder?.player?.currentMediaItem != null && playerBottomSheetState.dismissed) {
                    playerBottomSheetState.collapseSoft()
                }
            }

            val playerAwareWindowInsets = remember(
                bottomDp,
                animatedBottomDp,
                playerBottomSheetState.value,
                imeVisible,
                imeBottomDp
            ) {
                val bottom =
                    if (imeVisible) imeBottomDp.coerceAtLeast(playerBottomSheetState.value)
                    else playerBottomSheetState.value.coerceIn(
                        animatedBottomDp.coerceAtMost(playerBottomSheetState.collapsedBound)..playerBottomSheetState.collapsedBound
                    )

                windowInsets
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                    .add(WindowInsets(bottom = bottom))
            }

            val pip = isInPip(
                onChange = {
                    if (!it || vm.binder?.player?.shouldBePlaying != true) return@isInPip
                    playerBottomSheetState.expandSoft()
                }
            )

            KeyedCrossfade(state = pip) { currentPip ->
                if (currentPip) Thumbnail(
                    isShowingLyrics = true,
                    onShowLyrics = { },
                    isShowingStatsForNerds = false,
                    onShowStatsForNerds = { },
                    onOpenDialog = { },
                    likedAt = null,
                    setLikedAt = { },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    shouldShowSynchronizedLyrics = true,
                    setShouldShowSynchronizedLyrics = { },
                    showLyricsControls = false
                ) else CompositionLocalProvider(
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalSetBottomNavigation provides { hasBottomNav = it },
                    LocalHasBottomNavigation provides hasBottomNav
                ) {
                    val isDownloading by downloadState.collectAsState()

                    Box {
                        HomeScreen()
                    }

                    AnimatedVisibility(
                        visible = isDownloading,
                        modifier = Modifier.padding(playerAwareWindowInsets.asPaddingValues())
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }

                    CompositionLocalProvider(
                        LocalAppearance provides LocalAppearance.current.let {
                            if (it.colorPalette.isDark && AppearancePreferences.darkness == Darkness.AMOLED) {
                                it.copy(colorPalette = it.colorPalette.amoled())
                            } else it
                        }
                    ) {
                        Player(
                            layoutState = playerBottomSheetState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

                    BottomSheetMenu(
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            vm.binder?.player.DisposableListener(key = playerBottomSheetState) {
                object : Player.Listener {
                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) = when {
                        mediaItem == null -> {
                            maybeExitPip()
                            playerBottomSheetState.dismissSoft()
                        }

                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                            mediaItem.mediaMetadata.extras?.songBundle?.isFromPersistentQueue != true -> {
                            if (AppearancePreferences.openPlayer) playerBottomSheetState.expandSoft()
                            else Unit
                        }

                        playerBottomSheetState.dismissed -> playerBottomSheetState.collapseSoft()

                        else -> Unit
                    }
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun handleIntent(intent: Intent) = lifecycleScope.launch(CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Unable to open external request", error)
        toast(getString(R.string.error_message))
    }) {
        val extras = intent.extras?.activityIntentBundle

        when (intent.action) {
            Intent.ACTION_SEARCH -> {
                val query = extras?.query ?: return@launch
                extras.query = null

                searchResultRoute.ensureGlobal(query)
            }

            Intent.ACTION_APPLICATION_PREFERENCES -> settingsRoute.ensureGlobal()

            Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
                val uri = intent.data
                    ?: runCatching { extras?.text?.toUri() }.getOrNull()
                    ?: return@launch

                intent.data = null
                extras?.text = null

                handleUrl(uri, vm.awaitBinder())
            }

            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                val query = when (extras?.mediaFocus) {
                    null, "vnd.android.cursor.item/*" -> extras?.query ?: extras?.text

                    MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> extras.genre

                    MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> extras.artist

                    MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> extras.album

                    "vnd.android.cursor.item/audio" -> listOfNotNull(
                        extras.album,
                        extras.artist,
                        extras.genre,
                        extras.title
                    ).joinToString(separator = " ")

                    @Suppress("deprecation")
                    MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> extras.playlist

                    else -> null
                }

                if (!query.isNullOrBlank()) vm.awaitBinder().playFromSearch(query)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _monet?.removeMonetColorsChangedListener(this)
        _monet = null

        removeOnNewIntentListener(::handleIntent)
    }

    override fun onStop() {
        releaseService()
        super.onStop()
    }

    override fun onMonetColorsChanged(
        monet: MonetCompat,
        monetColors: ColorScheme,
        isInitialChange: Boolean
    ) {
        if (!isInitialChange) recreate()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (AppearancePreferences.autoPip && vm.binder?.player?.shouldBePlaying == true) maybeEnterPip()
    }
}

@Suppress("CyclomaticComplexMethod")
context(context: Context)
fun handleUrl(
    uri: Uri,
    binder: PlayerService.Binder?
) {
    val path = uri.pathSegments.firstOrNull()
    Log.d(TAG, "Opening url: $uri ($path)")

    coroutineScope.launch {
        when (path) {
            "search" -> uri.getQueryParameter("q")?.let { query ->
                searchResultRoute.ensureGlobal(query)
            }

            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                val browseId = "VL$playlistId"

                if (playlistId.startsWith("OLAK5uy_")) Innertube.playlistPage(
                    body = BrowseBody(browseId = browseId)
                )
                    ?.getOrNull()
                    ?.let { page ->
                        page.songsPage?.items?.firstOrNull()?.album?.endpoint?.browseId
                            ?.let { albumRoute.ensureGlobal(it) }
                    } ?: withContext(Dispatchers.Main) {
                    context.toast(context.getString(R.string.error_url, uri))
                }
                else playlistRoute.ensureGlobal(
                    p0 = browseId,
                    p1 = uri.getQueryParameter("params"),
                    p2 = null,
                    p3 = playlistId.startsWith("RDCLAK5uy_")
                )
            }

            "channel", "c" -> uri.lastPathSegment?.let { channelId ->
                artistRoute.ensureGlobal(channelId)
            }

            else -> when {
                path == "watch" -> uri.getQueryParameter("v")

                uri.host == "youtu.be" -> path

                else -> {
                    withContext(Dispatchers.Main) {
                        context.toast(context.getString(R.string.error_url, uri))
                    }
                    null
                }
            }?.let { videoId ->
                Innertube.song(videoId)?.getOrNull()?.let { song ->
                    withContext(Dispatchers.Main) {
                        binder?.player?.forcePlay(song.asMediaItem)
                    }
                }
            }
        }
    }
}

val LocalPlayerServiceBinder = staticCompositionLocalOf<PlayerService.Binder?> { null }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No player insets provided") }
val LocalSetBottomNavigation = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
val LocalHasBottomNavigation = staticCompositionLocalOf { false }
val LocalCredentialManager = staticCompositionLocalOf { Dependencies.credentialManager }

class MainApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        Dependencies.init(this)
        CrashGuard.install(filesDir)

        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .let {
                        if (isAtLeastAndroid12) it.detectUnsafeIntentLaunch()
                        else it
                    }
                    .let {
                        if (isAtLeastAndroid17) it.detectImplicitUriPermissionGrant()
                        else it
                    }
                    .penaltyLog()
                    .build()
            )
        }
        MonetCompat.debugLog = BuildConfig.DEBUG
        runCatching { MonetCompat.enablePaletteCompat() }
            .onFailure { Log.w(TAG, "Wallpaper palette unavailable", it) }
        ServiceNotifications.createAll()
    }

    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(this)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.1)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("coil"))
                .maxSizeBytes(DataPreferences.coilDiskCacheMaxSize.bytes)
                .build()
        }
        .bitmapFactoryExifOrientationStrategy(ExifOrientationStrategy.IGNORE)
        .let { if (BuildConfig.DEBUG) it.logger(DebugLogger()) else it }
        .build()

    val persistMap = PersistMap()

    override val workManagerConfiguration = Configuration.Builder()
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
        .build()
}

object Dependencies {
    lateinit var application: MainApplication
        private set

    val py by lazy {
        if (!Python.isStarted()) Python.start(AndroidPlatform(application))
        Python.getInstance()
    }

    private val module by lazy { py.getModule("download") }

    val quickjsPath by lazy {
        File(application.applicationInfo.nativeLibraryDir, "libqjs.so")
            .also { if (!it.canExecute()) it.setExecutable(true) }
    }

    fun runDownload(id: String): String = module
        .callAttr("download", quickjsPath.absolutePath, id)
        .toString()

    fun upgradeYoutubeDl(packageName: String = "yt-dlp"): Boolean {
        val success = runCatching { module.callAttr("upgrade", packageName) }
            .also { it.exceptionOrNull()?.printStackTrace() }
            .isSuccess
        if (!success) Log.e("Python", "Upgrading $packageName resulted in non-zero exit code!")
        return success
    }

    val credentialManager by lazy { CredentialManager.create(application) }

    internal fun init(application: MainApplication) {
        this.application = application
    }
}

open class GlobalPreferencesHolder : PreferencesHolder(Dependencies.application, "preferences")
