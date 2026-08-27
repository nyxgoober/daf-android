package us.crafties.dafdroid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import kotlin.math.roundToInt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import us.crafties.dafdroid.audio.PcmPlayer
import us.crafties.dafdroid.audio.PlaybackState
import us.crafties.dafdroid.audio.PlayerStatus
import us.crafties.dafdroid.codec.AudioFileLoader
import us.crafties.dafdroid.codec.CodecProgressListener
import us.crafties.dafdroid.codec.DAFAudio
import us.crafties.dafdroid.codec.DAFCodec
import us.crafties.dafdroid.codec.SourceKind
import us.crafties.dafdroid.codec.WavCodec
import us.crafties.dafdroid.state.ConversionStage
import us.crafties.dafdroid.state.ConversionState
import us.crafties.dafdroid.state.AccentColor
import us.crafties.dafdroid.state.AppSettings
import us.crafties.dafdroid.state.SettingsStore
import us.crafties.dafdroid.state.ThemeMode
import us.crafties.dafdroid.state.LibraryStore
import us.crafties.dafdroid.state.Track
import us.crafties.dafdroid.ui.components.LiquidGlassBar
import us.crafties.dafdroid.ui.components.NowPlayingBar
import us.crafties.dafdroid.ui.screens.ConvertScreen
import us.crafties.dafdroid.ui.screens.ExportFormat
import us.crafties.dafdroid.ui.screens.PlayerScreen
import us.crafties.dafdroid.ui.screens.TrackInfoScreen
import us.crafties.dafdroid.ui.theme.DAFTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class AppTab { PLAYER, CONVERT }
private val FLOATING_OVERLAY_RESERVE = 210.dp
private val TOP_BAR_HEIGHT = 56.dp

class MainActivity : ComponentActivity() {

    private lateinit var player: PcmPlayer
    private val pendingIntentUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        player = PcmPlayer(lifecycleScope)
        pendingIntentUri.value = extractAudioUri(intent)

        setContent {
            var settings by remember { mutableStateOf(SettingsStore.load(this)) }
            fun updateSettings(next: AppSettings) {
                settings = next
                SettingsStore.save(this, next)
            }
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DAFTheme(darkTheme = darkTheme, accentSeed = settings.accentColor.seed) {
                DAFApp(
                    player = player,
                    initialIntentUri = pendingIntentUri.value,
                    consumeInitialIntentUri = { pendingIntentUri.value = null },
                    settings = settings,
                    onSettingsChange = ::updateSettings,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntentUri.value = extractAudioUri(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    private fun extractAudioUri(intent: Intent?): Uri? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
    }
}

@Composable
private fun DAFApp(
    player: PcmPlayer,
    initialIntentUri: Uri?,
    consumeInitialIntentUri: () -> Unit,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(AppTab.PLAYER) }
    var tracks by remember { mutableStateOf(listOf<Track>()) }
    var activeTrackId by remember { mutableStateOf<String?>(null) }
    var conversionState by remember { mutableStateOf(ConversionState()) }
    // Tracks the in-flight "Add to Player" action specifically, separate from
    // ConversionState — the convert screen used to reset to IDLE (hiding the button
    // and its card entirely) the instant "Add to Player" was tapped, while the actual
    // decode/encode/save work kept running unseen in the background. This flag keeps
    // the card visible with a spinner until that work genuinely finishes.
    var isAddingToPlayer by remember { mutableStateOf(false) }

    val playerStatus by player.status.collectAsState()

    val loadedUris = remember { mutableStateOf(setOf<String>()) }
    val pendingPermissionResult = remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermissionResult.value?.complete(granted)
        pendingPermissionResult.value = null
    }
    suspend fun ensureFileScopeStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) return true
        val deferred = CompletableDeferred<Boolean>()
        pendingPermissionResult.value = deferred
        permissionLauncher.launch(permission)
        return deferred.await()
    }
    val audioCache = remember { mutableStateMapOf<String, DAFAudio>() }
    var loadingTrackId by remember { mutableStateOf<String?>(null) }
    var infoTrack by remember { mutableStateOf<Track?>(null) }
    var infoVisible by remember { mutableStateOf(false) }
    var infoFileSize by remember { mutableStateOf<Long?>(null) }
    // Bumped on every openTrackInfo() call so the sheet below can be `key()`ed on it.
    // This forces TrackInfoScreen to fully discard and recreate its internal drag/
    // animation state (offsetY, isDismissing, liveDragOffset, etc.) on every open,
    // rather than reusing whatever instance is still around from a previous close —
    // closing the door on any "swipe-closed once, now stuck" state leak regardless
    // of its exact cause, instead of relying on every internal reset path being hit.
    var infoOpenGeneration by remember { mutableStateOf(0) }

    fun openTrackInfo(track: Track) {
        infoTrack = track
        infoVisible = true
        infoOpenGeneration++
    }
    BackHandler(enabled = infoVisible) { infoVisible = false }
    BackHandler(enabled = !infoVisible && tab == AppTab.CONVERT) { tab = AppTab.PLAYER }

    LaunchedEffect(infoTrack?.id, infoVisible) {
        val id = infoTrack?.id
        infoFileSize = null
        if (id != null && infoVisible) {
            infoFileSize = withContext(Dispatchers.IO) { LibraryStore.audioFileSize(context, id) }
        }
    }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { LibraryStore.loadManifest(context) }
        tracks = (loaded + tracks).distinctBy { it.id }
    }

    fun addLoadedTrackToLibrary(uri: Uri, switchToPlayerTab: Boolean, onComplete: () -> Unit = {}) {
        val key = uri.toString()
        if (key in loadedUris.value) {
            if (switchToPlayerTab) tab = AppTab.PLAYER
            onComplete()
            return
        }
        scope.launch {
            try {
                if (uri.scheme == "file" && !ensureFileScopeStoragePermission()) {
                    Toast.makeText(
                        context,
                        "Can't read that file without storage permission — try sharing it via \"Open with\" instead.",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                val (track, audio) = withContext(Dispatchers.Default) {
                    val loaded = AudioFileLoader.load(context, uri)
                    val dafBytes = DAFCodec.encode(loaded.audio)
                    val saved = LibraryStore.addTrack(
                        context = context,
                        dafBytes = dafBytes,
                        displayName = loaded.displayName,
                        sourceKind = loaded.sourceKind,
                        sampleRate = loaded.audio.sampleRate,
                        numChannels = loaded.audio.numChannels,
                        numFrames = loaded.audio.numFrames,
                    )
                    saved to loaded.audio
                }
                tracks = tracks + track
                audioCache[track.id] = audio 
                loadedUris.value = loadedUris.value + key
                if (switchToPlayerTab) tab = AppTab.PLAYER
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't load file: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                onComplete()
            }
        }
    }

    // Fast path for "Add to Player" from the convert screen: the DAF bytes and decoded
    // PCM already exist in ConversionState from the conversion that just ran, so this
    // writes them straight into the library instead of re-running addLoadedTrackToLibrary,
    // which would re-decode+re-encode the freshly-produced .daf cache file from scratch.
    fun addConvertedTrackToLibrary(state: ConversionState, switchToPlayerTab: Boolean, onComplete: () -> Unit = {}) {
        val dafBytes = state.outputBytes
        val audio = state.outputAudio
        if (dafBytes == null || audio == null) {
            onComplete()
            return
        }
        scope.launch {
            try {
                val track = withContext(Dispatchers.Default) {
                    LibraryStore.addTrack(
                        context = context,
                        dafBytes = dafBytes,
                        displayName = stripExt(state.sourceName) + ".daf",
                        sourceKind = state.outputSourceKind ?: SourceKind.OTHER,
                        sampleRate = audio.sampleRate,
                        numChannels = audio.numChannels,
                        numFrames = audio.numFrames,
                    )
                }
                tracks = tracks + track
                audioCache[track.id] = audio
                if (switchToPlayerTab) tab = AppTab.PLAYER
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't add track: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                onComplete()
            }
        }
    }

    fun removeTrackFromLibrary(track: Track) {
        if (activeTrackId == track.id) {
            player.pause()
            activeTrackId = null
        }
        tracks = tracks.filterNot { it.id == track.id }
        audioCache.remove(track.id)
        scope.launch(Dispatchers.IO) { LibraryStore.removeTrack(context, track.id) }
    }

    fun playTrack(track: Track) {
        val cached = audioCache[track.id]
        if (cached != null) {
            activeTrackId = track.id
            player.load(cached)
            player.play()
        } else if (loadingTrackId == null) {
            loadingTrackId = track.id
            scope.launch {
                try {
                    val audio = withContext(Dispatchers.Default) {
                        LibraryStore.loadAudio(context, track.id)
                    }
                    audioCache[track.id] = audio
                    activeTrackId = track.id
                    player.load(audio)
                    player.play()
                } catch (e: Exception) {
                    Toast.makeText(context, "Couldn't play ${track.displayName}: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    loadingTrackId = null
                }
            }
        }
    }

    fun togglePlayPause() {
        if (playerStatus.state == PlaybackState.PLAYING) player.pause() else player.play()
    }

    fun startConversion(uri: Uri, skipLossyWarning: Boolean = false) {
        val name = AudioFileLoader.displayNameOf(context, uri)
        if (!skipLossyWarning && AudioFileLoader.isLossyExtension(name)) {
            conversionState = ConversionState(stage = ConversionStage.WARNING_LOSSY, sourceName = name, pendingLossyUri = uri)
            return
        }

        scope.launch {
            conversionState = ConversionState(stage = ConversionStage.DECODING, sourceName = name)
            try {
                val loaded = withContext(Dispatchers.Default) {
                    AudioFileLoader.load(context, uri, CodecProgressListener { frac, msg ->
                        scope.launch(Dispatchers.Main) {
                            conversionState = conversionState.copy(progress = frac, log = appendLog(conversionState.log, msg))
                        }
                    })
                }
                val rawBytes = loaded.audio.numFrames.toLong() * loaded.audio.numChannels * 2

                conversionState = conversionState.copy(stage = ConversionStage.ENCODING, progress = 0f)
                val dafBytes = withContext(Dispatchers.Default) {
                    DAFCodec.encode(loaded.audio, CodecProgressListener { frac, msg ->
                        scope.launch(Dispatchers.Main) {
                            conversionState = conversionState.copy(progress = frac, log = appendLog(conversionState.log, msg))
                        }
                    })
                }

                val outFile = File(context.cacheDir, "${stripExt(name)}.daf")
                outFile.writeBytes(dafBytes)
                val outUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)

                conversionState = conversionState.copy(
                    stage = ConversionStage.DONE,
                    rawBytes = rawBytes,
                    dafBytes = dafBytes.size.toLong(),
                    outputUri = outUri,
                    outputName = outFile.name,
                    outputBytes = dafBytes,
                    outputAudio = loaded.audio,
                    outputSourceKind = loaded.sourceKind,
                )
            } catch (e: Exception) {
                conversionState = conversionState.copy(stage = ConversionStage.ERROR, errorMessage = e.message)
            }
        }
    }
    LaunchedEffect(initialIntentUri) {
        val uri = initialIntentUri ?: return@LaunchedEffect
        addLoadedTrackToLibrary(uri, switchToPlayerTab = true)
        consumeInitialIntentUri()
    }

    val openConvertPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) startConversion(uri)
    }
    val saveDAFLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-daf")) { uri ->
        val cachedUri = conversionState.outputUri
        if (uri != null && cachedUri != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openInputStream(cachedUri)?.use { input ->
                    context.contentResolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }
    val exportDAFLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-daf")) { uri ->
        val bytes = pendingExportBytes
        pendingExportBytes = null
        if (uri != null && bytes != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                withContext(Dispatchers.Main) { Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    val exportWavLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        val bytes = pendingExportBytes
        pendingExportBytes = null
        if (uri != null && bytes != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                withContext(Dispatchers.Main) { Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun exportTrack(track: Track, format: ExportFormat) {
        scope.launch {
            try {
                val audio = audioCache[track.id] ?: withContext(Dispatchers.Default) {
                    LibraryStore.loadAudio(context, track.id)
                }
                when (format) {
                    ExportFormat.DAF -> {
                        pendingExportBytes = withContext(Dispatchers.Default) { DAFCodec.encode(audio) }
                        exportDAFLauncher.launch("${stripExt(track.displayName)}.daf")
                    }
                    ExportFormat.WAV -> {
                        pendingExportBytes = withContext(Dispatchers.Default) { WavCodec.build(audio) }
                        exportWavLauncher.launch("${stripExt(track.displayName)}.wav")
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't export ${track.displayName}: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val activeTrack = tracks.firstOrNull { it.id == activeTrackId }

    // Background glass effect when player sheet is open — blur + desaturation + refraction
    // including titlebar. Animated so it eases in/out with sheet.
    // Uses a continuously-animated RenderEffect blur radius for a seamless crossfade
    // rather than toggling a fixed-radius effect on/off (which read as a hard cut).
    val sheetOpen = infoVisible && infoTrack != null
    val bgBlur by animateDpAsState(
        targetValue = if (sheetOpen) 20.dp else 0.dp,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "bg-blur",
    )
    val bgSat by animateFloatAsState(
        targetValue = if (sheetOpen) 0.72f else 1f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "bg-sat",
    )
    val bgRefraction by animateFloatAsState(
        targetValue = if (sheetOpen) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "bg-refraction",
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Tablet = landscape + at least 600dp wide (rotatable large screen). Uses window
            // size rather than orientation alone so foldables/ChromeOS resize correctly.
            val isTabletLandscape = maxWidth >= 600.dp && maxWidth > maxHeight
            Box(modifier = Modifier.fillMaxSize()) {
            // Background content — blurred/desaturated/refracted when sheet open.
            // On tablet the sheet is a side panel, but keep same glass treatment.
            val density = LocalDensity.current
            val blurPx = with(density) { bgBlur.toPx() }
            val bgRenderEffect = remember(blurPx, bgSat) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && blurPx > 0.5f) {
                    val r = blurPx.coerceIn(0f, 22f)
                    val cm = android.graphics.ColorMatrix().apply { setSaturation(bgSat) }
                    val blurEffect = AndroidRenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
                    val satEffect = AndroidRenderEffect.createColorFilterEffect(
                        android.graphics.ColorMatrixColorFilter(cm),
                        blurEffect,
                    )
                    satEffect.asComposeRenderEffect()
                } else null
            }
            val blurFallbackModifier = if (bgRenderEffect == null && blurPx > 0.5f) Modifier.blur(bgBlur) else Modifier
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurFallbackModifier)
                    .graphicsLayer {
                        val s = 1f + bgRefraction * 0.015f
                        scaleX = s
                        scaleY = s
                        if (bgRenderEffect != null) renderEffect = bgRenderEffect
                    },
            ) {
            if (isTabletLandscape) {
                TabletScaffold(
                    tab = tab,
                    onTabSelect = { tab = it },
                    tracks = tracks,
                    activeTrackId = activeTrackId,
                    loadingTrackId = loadingTrackId,
                    playerStatus = playerStatus,
                    conversionState = conversionState,
                    isAddingToPlayer = isAddingToPlayer,
                    onTrackClick = { track -> if (activeTrackId == track.id) togglePlayPause() else playTrack(track) },
                    onRemoveTrack = { track -> removeTrackFromLibrary(track) },
                    onExportTrack = { track, format -> exportTrack(track, format) },
                    onPickFile = { openConvertPicker.launch(arrayOf("*/*")) },
                    onSave = {
                        val outputName = conversionState.outputName
                        if (outputName.isNotEmpty()) saveDAFLauncher.launch(outputName)
                    },
                    onAddToPlayer = {
                        if (!isAddingToPlayer) {
                            isAddingToPlayer = true
                            addConvertedTrackToLibrary(conversionState, switchToPlayerTab = true) {
                                isAddingToPlayer = false
                                conversionState = ConversionState()
                            }
                        }
                    },
                    onConfirmLossy = {
                        val uri = conversionState.pendingLossyUri
                        if (uri != null) startConversion(uri, skipLossyWarning = true)
                    },
                    onCancelLossy = { conversionState = ConversionState() },
                    activeTrack = activeTrack,
                    onPlayPause = { togglePlayPause() },
                    onSeek = { fraction -> player.seekTo(fraction) },
                    onOpenInfo = { track -> openTrackInfo(track) },
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    sheetOpen = sheetOpen,
                )
            } else {
        Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val enter = fadeIn(tween(220)) + slideInHorizontally(tween(220)) { w -> if (forward) w / 5 else -w / 5 }
                val exit = fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { w -> if (forward) -w / 5 else w / 5 }
                enter togetherWith exit
            },
            label = "tab-content",
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = TOP_BAR_HEIGHT),
        ) { targetTab ->
            when (targetTab) {
                AppTab.PLAYER -> PlayerScreen(
                    tracks = tracks,
                    activeTrackId = activeTrackId,
                    loadingTrackId = loadingTrackId,
                    playerStatus = playerStatus,
                    onTrackClick = { track ->
                        if (activeTrackId == track.id) togglePlayPause() else playTrack(track)
                    },
                    onRemoveTrack = { track -> removeTrackFromLibrary(track) },
                    onExportTrack = { track, format -> exportTrack(track, format) },
                    bottomContentPadding = FLOATING_OVERLAY_RESERVE,
                )
                AppTab.CONVERT -> ConvertScreen(
                    state = conversionState,
                    isAddingToPlayer = isAddingToPlayer,
                    onPickFile = { openConvertPicker.launch(arrayOf("*/*")) },
                    onSave = {
                        val outputName = conversionState.outputName
                        if (outputName.isNotEmpty()) saveDAFLauncher.launch(outputName)
                    },
                    onAddToPlayer = {
                        if (!isAddingToPlayer) {
                            isAddingToPlayer = true
                            addConvertedTrackToLibrary(conversionState, switchToPlayerTab = true) {
                                isAddingToPlayer = false
                                conversionState = ConversionState()
                            }
                        }
                    },
                    onConfirmLossy = {
                        val uri = conversionState.pendingLossyUri
                        if (uri != null) startConversion(uri, skipLossyWarning = true)
                    },
                    onCancelLossy = { conversionState = ConversionState() },
                    bottomContentPadding = FLOATING_OVERLAY_RESERVE,
                )
            }
        }

        AppTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            settings = settings,
            onSettingsChange = onSettingsChange,
        )

        // Crossfade floating overlay with sheet — otherwise pill/mini pops while scrim/blur animate
        val overlayAlpha by animateFloatAsState(
            targetValue = if (sheetOpen) 0f else 1f,
            animationSpec = tween(if (sheetOpen) 180 else 260, easing = FastOutSlowInEasing),
            label = "phone-overlay-alpha",
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .graphicsLayer { alpha = overlayAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (activeTrack != null) {
                NowPlayingBar(
                    track = activeTrack,
                    status = playerStatus,
                    onPlayPauseClick = { togglePlayPause() },
                    onSeek = { fraction -> player.seekTo(fraction) },
                    onOpenInfo = { openTrackInfo(activeTrack) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            FloatingTabPill(current = tab, onSelect = { tab = it })
        }
            } // inner Box fillMaxSize phone
            }
            // Desaturation overlay — always composed so fade in and fade out are
            // symmetric, driven by animated bgSat.
            val desatProgress = ((1f - bgSat) / 0.28f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = desatProgress }
                    .background(Color(0xFF8A8FA3).copy(alpha = 0.10f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = bgRefraction * 0.9f }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.02f),
                                Color.Transparent,
                            )
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = TOP_BAR_HEIGHT)
                    .graphicsLayer { alpha = bgRefraction }
                    .background(Color.White.copy(alpha = 0.18f)),
            )
            } // bg blurred Box

        // Phone: full-screen bottom sheet. Tablet: side panel instead.
        if (!isTabletLandscape && infoTrack != null) {
            key(infoOpenGeneration) {
                TrackInfoScreen(
                    track = infoTrack!!,
                    status = playerStatus,
                    fileSizeBytes = infoFileSize,
                    visible = infoVisible,
                    onClose = { infoVisible = false },
                    onFullyClosed = { infoTrack = null },
                    onPlayPauseClick = { togglePlayPause() },
                    onSeek = { fraction -> player.seekTo(fraction) },
                    animatedBackground = settings.animatedBackground,
                )
            }
        }
        // Tablet side panel — Apple Music style: fixed-width panel on the right,
        // expands vertically (not horizontally) when opened.
        if (isTabletLandscape && infoTrack != null) {
            key(infoOpenGeneration) {
                TabletSidePlayerOverlay(
                    track = infoTrack!!,
                    status = playerStatus,
                    fileSizeBytes = infoFileSize,
                    visible = infoVisible,
                    onClose = { infoVisible = false },
                    onFullyClosed = { infoTrack = null },
                    onPlayPauseClick = { togglePlayPause() },
                    onSeek = { fraction -> player.seekTo(fraction) },
                    animatedBackground = settings.animatedBackground,
                )
            }
        }
        }
        }
    }
}

@Composable
private fun TabletScaffold(
    tab: AppTab,
    onTabSelect: (AppTab) -> Unit,
    tracks: List<Track>,
    activeTrackId: String?,
    loadingTrackId: String?,
    playerStatus: PlayerStatus,
    conversionState: ConversionState,
    isAddingToPlayer: Boolean,
    onTrackClick: (Track) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onExportTrack: (Track, ExportFormat) -> Unit,
    onPickFile: () -> Unit,
    onSave: () -> Unit,
    onAddToPlayer: () -> Unit,
    onConfirmLossy: () -> Unit,
    onCancelLossy: () -> Unit,
    activeTrack: Track?,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onOpenInfo: (Track) -> Unit,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    sheetOpen: Boolean,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        TabletSidebar(
            current = tab,
            onSelect = onTabSelect,
            settings = settings,
            onSettingsChange = onSettingsChange,
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppTopBar(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                )
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val enter = fadeIn(tween(220)) + slideInHorizontally(tween(220)) { w -> if (forward) w / 5 else -w / 5 }
                        val exit = fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { w -> if (forward) -w / 5 else w / 5 }
                        enter togetherWith exit
                    },
                    label = "tablet-tab-content",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { targetTab ->
                    when (targetTab) {
                        AppTab.PLAYER -> PlayerScreen(
                            tracks = tracks,
                            activeTrackId = activeTrackId,
                            loadingTrackId = loadingTrackId,
                            playerStatus = playerStatus,
                            onTrackClick = onTrackClick,
                            onRemoveTrack = onRemoveTrack,
                            onExportTrack = onExportTrack,
                            bottomContentPadding = 96.dp,
                        )
                        AppTab.CONVERT -> ConvertScreen(
                            state = conversionState,
                            isAddingToPlayer = isAddingToPlayer,
                            onPickFile = onPickFile,
                            onSave = onSave,
                            onAddToPlayer = onAddToPlayer,
                            onConfirmLossy = onConfirmLossy,
                            onCancelLossy = onCancelLossy,
                            bottomContentPadding = 96.dp,
                        )
                    }
                }
            }
            // Mini player — crossfades with expanded panel so it doesn't pop behind dim
            if (activeTrack != null) {
                val miniAlpha by animateFloatAsState(
                    targetValue = if (sheetOpen) 0f else 1f,
                    animationSpec = tween(if (sheetOpen) 180 else 260, easing = FastOutSlowInEasing),
                    label = "tablet-mini-alpha",
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = 16.dp)
                        .width(300.dp)
                        .graphicsLayer { alpha = miniAlpha },
                ) {
                    NowPlayingBar(
                        track = activeTrack,
                        status = playerStatus,
                        onPlayPauseClick = onPlayPause,
                        onSeek = onSeek,
                        onOpenInfo = { onOpenInfo(activeTrack) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabletSidebar(
    current: AppTab,
    onSelect: (AppTab) -> Unit,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Icon(imageVector = AppIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(16.dp))
        TabletSidebarItem(label = "Player", icon = Icons.Filled.GraphicEq, selected = current == AppTab.PLAYER, onClick = { onSelect(AppTab.PLAYER) })
        Spacer(Modifier.height(6.dp))
        TabletSidebarItem(label = "Convert", icon = Icons.Filled.SwapHoriz, selected = current == AppTab.CONVERT, onClick = { onSelect(AppTab.CONVERT) })
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        Spacer(Modifier.height(8.dp))
        Text(text = "Appearance · ${settings.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun TabletSidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun TabletSidePlayerOverlay(
    track: Track,
    status: PlayerStatus,
    fileSizeBytes: Long?,
    visible: Boolean,
    onClose: () -> Unit,
    onFullyClosed: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    animatedBackground: Boolean,
) {
    // Same scrim fade as phone — always composed, alpha animated both ways
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 420 else 320, easing = FastOutSlowInEasing),
        label = "tablet-scrim",
    )
    val rotation: Float
    if (animatedBackground) {
        val infiniteTransition = rememberInfiniteTransition(label = "tablet-player-bg")
        rotation = infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24000, easing = LinearEasing),
            ), label = "tablet-gradient-rotation",
        ).value
    } else rotation = 0f

    val contentReveal = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            contentReveal.snapTo(0f)
            contentReveal.animateTo(1f, tween(680, delayMillis = 120, easing = FastOutSlowInEasing))
        } else contentReveal.snapTo(0f)
    }
    val paneState = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    paneState.targetState = visible

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        // Dim overlay — fades in/out
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha }
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(enabled = scrimAlpha > 0.02f, onClick = onClose),
        )
        // Panel animates up from miniplayer corner (same position/size), expands vertically not horizontally
        AnimatedVisibility(
            visibleState = paneState,
            enter = androidx.compose.animation.slideInVertically(
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                initialOffsetY = { h -> h / 2 },
            ) + fadeIn(tween(280, easing = FastOutSlowInEasing)) + expandVertically(
                animationSpec = tween(420, easing = FastOutSlowInEasing), expandFrom = Alignment.Bottom,
            ),
            exit = androidx.compose.animation.slideOutVertically(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                targetOffsetY = { h -> h / 2 },
            ) + fadeOut(tween(240)) + shrinkVertically(
                animationSpec = tween(320, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Bottom,
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp)
                .width(360.dp),
        ) {
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onFullyClosed() } }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .graphicsLayer {
                        // anchor expand to miniplayer corner
                        transformOrigin = TransformOrigin(1f, 1f)
                    },
            ) {
                val gradientPrimary = MaterialTheme.colorScheme.primary
                val gradientSecondary = MaterialTheme.colorScheme.secondary
                val gradientTertiary = MaterialTheme.colorScheme.tertiary
                val gradientBackground = MaterialTheme.colorScheme.background
                val counterRotation = 360f - rotation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .drawBehind {
                            val orbit = 0.35f
                            val r1 = Math.toRadians(rotation.toDouble())
                            val cx1 = this.size.width * (0.5f + orbit * kotlin.math.cos(r1).toFloat())
                            val cy1 = this.size.height * (0.4f + orbit * kotlin.math.sin(r1).toFloat())
                            val r2 = Math.toRadians((counterRotation + 140f).toDouble())
                            val cx2 = this.size.width * (0.5f + orbit * kotlin.math.cos(r2).toFloat())
                            val cy2 = this.size.height * (0.6f + orbit * kotlin.math.sin(r2).toFloat())
                            val r3 = Math.toRadians((rotation + 240f).toDouble())
                            val cx3 = this.size.width * (0.5f + (orbit * 0.7f) * kotlin.math.cos(r3).toFloat())
                            val cy3 = this.size.height * (0.5f + (orbit * 0.7f) * kotlin.math.sin(r3).toFloat())
                            drawRect(color = gradientBackground)
                            drawRect(brush = Brush.radialGradient(listOf(gradientPrimary.copy(alpha = 0.22f), gradientPrimary.copy(alpha = 0.08f), Color.Transparent), center = Offset(cx1, cy1), radius = this.size.maxDimension * 0.75f))
                            drawRect(brush = Brush.radialGradient(listOf(gradientTertiary.copy(alpha = 0.18f), gradientTertiary.copy(alpha = 0.06f), Color.Transparent), center = Offset(cx2, cy2), radius = this.size.maxDimension * 0.65f))
                            drawRect(brush = Brush.radialGradient(listOf(gradientSecondary.copy(alpha = 0.16f), gradientSecondary.copy(alpha = 0.05f), Color.Transparent), center = Offset(cx3, cy3), radius = this.size.maxDimension * 0.55f))
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp)
                            .padding(top = 10.dp, bottom = 16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 6.dp, bottom = 12.dp)
                                .size(width = 36.dp, height = 5.dp)
                                .clip(RoundedCornerShape(50))
                                .graphicsLayer { alpha = contentReveal.value }
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(148.dp)
                                .graphicsLayer {
                                    alpha = contentReveal.value
                                    val slide = (1f - contentReveal.value) * 26f
                                    translationY = slide
                                    val scale = 0.92f + contentReveal.value * 0.08f
                                    scaleX = scale; scaleY = scale
                                }
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(imageVector = Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(54.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth().graphicsLayer {
                                alpha = contentReveal.value
                                translationY = (1f - contentReveal.value) * 14f
                            },
                        ) {
                            Text(text = track.displayName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 3, modifier = Modifier.fillMaxWidth())
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
                                Text(tabletKindLabel(track.sourceKind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                if (fileSizeBytes != null) Text(" · ${fmtBytesTablet(fileSizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth().graphicsLayer {
                                alpha = contentReveal.value
                                translationY = (1f - contentReveal.value) * 10f
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val frac = if (status.totalFrames > 0) status.positionFrames.toFloat() / status.totalFrames else 0f
                            androidx.compose.material3.Slider(value = frac, onValueChange = onSeek, modifier = Modifier.fillMaxWidth())
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(fmtTimeTablet(status.positionSeconds), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                Text(fmtTimeTablet(status.totalSeconds), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            }
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.animation.AnimatedContent(
                                targetState = status.state == PlaybackState.PLAYING,
                                transitionSpec = {
                                    (fadeIn(tween(220, easing = FastOutSlowInEasing)) + androidx.compose.animation.scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.7f)) togetherWith
                                        (fadeOut(tween(160)) + androidx.compose.animation.scaleOut(tween(160), targetScale = 0.7f))
                                }, label = "tablet-play-pause",
                            ) { isPlaying ->
                                us.crafties.dafdroid.ui.components.LiquidIconButton(onClick = onPlayPauseClick, size = 56.dp) {
                                    Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(26.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.graphicsLayer { alpha = contentReveal.value },
                        )
                        Spacer(Modifier.height(4.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth().graphicsLayer {
                                alpha = contentReveal.value
                                translationY = (1f - contentReveal.value) * 10f
                            },
                        ) {
                            val bitDepth = 16
                            val rawKbps = (track.sampleRate.toLong() * track.numChannels * bitDepth) / 1000.0
                            DetailRow("Sample rate", "${track.sampleRate} Hz")
                            DetailRow("Channels", if (track.numChannels == 1) "Mono" else if (track.numChannels == 2) "Stereo" else "${track.numChannels} ch")
                            DetailRow("Bit depth", "$bitDepth-bit")
                            DetailRow("Raw PCM bitrate", "${rawKbps.roundToInt()} kbps")
                            if (fileSizeBytes != null && track.durationSeconds > 0) {
                                val eff = (fileSizeBytes * 8.0 / 1000.0) / track.durationSeconds
                                DetailRow("Effective bitrate", "${eff.roundToInt()} kbps")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

private fun tabletKindLabel(kind: us.crafties.dafdroid.codec.SourceKind): String = when (kind) {
    us.crafties.dafdroid.codec.SourceKind.DAF -> "DAF · lossless"
    us.crafties.dafdroid.codec.SourceKind.WAV -> "WAV · converted to DAF"
    us.crafties.dafdroid.codec.SourceKind.OTHER -> "Audio · converted to DAF"
}
private fun fmtTimeTablet(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
private fun fmtBytesTablet(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
    else -> "%.2f MB".format(n / (1024.0 * 1024.0))
}

@Composable
private fun AppTopBar(
    modifier: Modifier = Modifier,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(TOP_BAR_HEIGHT)
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                SettingsMenuContent(settings = settings, onSettingsChange = onSettingsChange)
            }
        }
    }
}

@Composable
private fun SettingsMenuContent(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    Text(
        text = "Appearance",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    DropdownMenuItem(
        text = { Text("Light") },
        leadingIcon = { Icon(Icons.Filled.LightMode, contentDescription = null) },
        trailingIcon = { if (settings.themeMode == ThemeMode.LIGHT) Icon(Icons.Filled.Check, contentDescription = null) },
        onClick = { onSettingsChange(settings.copy(themeMode = ThemeMode.LIGHT)) },
    )
    DropdownMenuItem(
        text = { Text("Dark") },
        leadingIcon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
        trailingIcon = { if (settings.themeMode == ThemeMode.DARK) Icon(Icons.Filled.Check, contentDescription = null) },
        onClick = { onSettingsChange(settings.copy(themeMode = ThemeMode.DARK)) },
    )
    DropdownMenuItem(
        text = { Text("System default") },
        leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
        trailingIcon = { if (settings.themeMode == ThemeMode.SYSTEM) Icon(Icons.Filled.Check, contentDescription = null) },
        onClick = { onSettingsChange(settings.copy(themeMode = ThemeMode.SYSTEM)) },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    DropdownMenuItem(
        text = { Text("Animated background") },
        trailingIcon = {
            Switch(
                checked = settings.animatedBackground,
                onCheckedChange = { onSettingsChange(settings.copy(animatedBackground = it)) },
            )
        },
        onClick = { onSettingsChange(settings.copy(animatedBackground = !settings.animatedBackground)) },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    Text(
        text = "Accent color",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (accent in AccentColor.entries) {
            val selected = settings.accentColor == accent
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent.seed)
                    .clickable { onSettingsChange(settings.copy(accentColor = accent)) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = accent.label,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// Mirrors the waveform artwork in res/drawable/ic_launcher_foreground.xml (five
// vertical bars, rounded caps) so the in-app top bar icon matches the launcher icon
// exactly, rather than drifting from it as a separately hand-picked Material icon
// would. Drawn directly as an ImageVector — rendering the adaptive-icon XML itself
// would pull in its 108dp masking/background layers, which aren't meant for an
// arbitrary small toolbar size.
private val AppIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "AppIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 108f,
        viewportHeight = 108f,
    ).apply {
        path(
            fill = null,
            // Icon(tint = ...) applies a color filter over the whole vector, so this
            // stroke color is never actually seen — it only needs to be opaque so the
            // path renders as strokes at all.
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 8f,
            strokeLineCap = StrokeCap.Round,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(30f, 54f)
            lineTo(30f, 60f)
            moveTo(42f, 42f)
            lineTo(42f, 72f)
            moveTo(54f, 30f)
            lineTo(54f, 84f)
            moveTo(66f, 42f)
            lineTo(66f, 72f)
            moveTo(78f, 54f)
            lineTo(78f, 60f)
        }
    }.build()
}

@Composable
private fun FloatingTabPill(current: AppTab, onSelect: (AppTab) -> Unit) {
    // Bounds (in the pill's own coordinate space) of each tab item, captured via
    // onGloballyPositioned so the highlight can be drawn/animated independently of the
    // items themselves, rather than each item drawing its own background.
    val itemBounds = remember { mutableStateMapOf<AppTab, Rect>() }
    val targetBounds = itemBounds[current]

    // The highlight's own animated rect, driven toward targetBounds. Kept as raw
    // Animatable floats (not animateDpAsState per edge) so left/right/top/bottom move
    // together as one coherent, interruptible animation.
    val animatedLeft = remember { Animatable(0f) }
    val animatedTop = remember { Animatable(0f) }
    val animatedRight = remember { Animatable(0f) }
    val animatedBottom = remember { Animatable(0f) }
    // A single 0→1→0 progress pulse for the "zoom bigger mid-slide" depth effect —
    // scales the highlight up slightly at the midpoint of the transition, then back
    // down as it settles, rather than a flat linear slide.
    val midPulse = remember { Animatable(0f) }

    LaunchedEffect(targetBounds) {
        val bounds = targetBounds ?: return@LaunchedEffect
        val spec = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
        launch { animatedLeft.animateTo(bounds.left, spec) }
        launch { animatedTop.animateTo(bounds.top, spec) }
        launch { animatedRight.animateTo(bounds.right, spec) }
        launch { animatedBottom.animateTo(bounds.bottom, spec) }
        launch {
            midPulse.snapTo(0f)
            midPulse.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
            midPulse.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
        }
    }

    LiquidGlassBar(
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        modifier = Modifier.wrapContentWidth(),
    ) {
        Box {
            if (animatedRight.value > animatedLeft.value) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = animatedLeft.value
                            translationY = animatedTop.value
                            // Bulge up to ~8% bigger at the midpoint of the slide, back to
                            // 1x once settled — the "zoom" depth cue.
                            val scale = 1f + midPulse.value * 0.08f
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                        .size(
                            width = with(LocalDensity.current) { (animatedRight.value - animatedLeft.value).toDp() },
                            height = with(LocalDensity.current) { (animatedBottom.value - animatedTop.value).toDp() },
                        )
                        .clip(RoundedCornerShape(50))
                        .background(
                            (if (isSystemInDarkTheme()) Color.White else Color.Black)
                                .copy(alpha = if (isSystemInDarkTheme()) 0.18f else 0.08f)
                        ),
                )
            }
            Row {
                TabPillItem(
                    label = "Player",
                    icon = Icons.Filled.GraphicEq,
                    selected = current == AppTab.PLAYER,
                    onClick = { onSelect(AppTab.PLAYER) },
                    onBoundsChanged = { itemBounds[AppTab.PLAYER] = it },
                )
                Spacer(Modifier.width(4.dp))
                TabPillItem(
                    label = "Convert",
                    icon = Icons.Filled.SwapHoriz,
                    selected = current == AppTab.CONVERT,
                    onClick = { onSelect(AppTab.CONVERT) },
                    onBoundsChanged = { itemBounds[AppTab.CONVERT] = it },
                )
            }
        }
    }
}

@Composable
private fun TabPillItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    // No default Material ripple here — the pill's own sliding/scaling highlight
    // (drawn by the parent) already communicates selection, so a ripple on top of
    // it just looks like visual noise. `indication = null` suppresses it while
    // keeping the click handling itself untouched.
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInParent()
                onBoundsChanged(
                    Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                )
            }
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        val isDark = isSystemInDarkTheme()
        val selectedColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
        val unselectedColor = if (isDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) selectedColor else unselectedColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) selectedColor else unselectedColor,
        )
    }
}

private fun appendLog(current: List<String>, line: String): List<String> {
    val next = current + line
    return if (next.size > 200) next.takeLast(200) else next
}

private fun stripExt(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(0, dot) else name
}
