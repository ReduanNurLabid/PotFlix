package com.potflix.presentation.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.potflix.presentation.theme.PotFlixTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val _isInPipMode = mutableStateOf(false)
    
    @javax.inject.Inject
    lateinit var movieRepository: com.potflix.domain.repository.MovieRepository

    @javax.inject.Inject
    lateinit var serverPreferences: com.potflix.data.local.preferences.ServerPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Auto-Orientation: Force landscape mode upon opening, regardless of system orientation lock
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        // Immersive Mode
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val movieUrl = intent.getStringExtra("movieUrl") ?: ""
        val streamUrl = intent.getStringExtra("streamUrl") ?: return
        val title = intent.getStringExtra("title") ?: "Video"
        val playbackPosition = intent.getLongExtra("playbackPosition", 0L)

        setContent {
            PotFlixTheme {
                VlcVideoPlayer(
                    movieUrl = movieUrl,
                    streamUrl = streamUrl, 
                    title = title, 
                    playbackPosition = playbackPosition,
                    isPipMode = _isInPipMode.value,
                    movieRepository = movieRepository,
                    serverPreferences = serverPreferences
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
    }
}

@Composable
fun VlcVideoPlayer(
    movieUrl: String = "",
    streamUrl: String, 
    title: String, 
    playbackPosition: Long = 0L,
    isPipMode: Boolean = false,
    movieRepository: com.potflix.domain.repository.MovieRepository? = null,
    serverPreferences: com.potflix.data.local.preferences.ServerPreferences? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var subtitleUrl by remember { mutableStateOf<String?>(null) }
    var selectedSpuTrack by remember { mutableStateOf(-1) }
    var selectedAudioTrack by remember { mutableStateOf(-1) }
    var audioTracks by remember { mutableStateOf<List<MediaPlayer.TrackDescription>>(emptyList()) }
    var spuTracks by remember { mutableStateOf<List<MediaPlayer.TrackDescription>>(emptyList()) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSpuMenu by remember { mutableStateOf(false) }
    
    var seasons by remember { mutableStateOf<List<com.potflix.domain.model.Season>>(emptyList()) }
    var currentStreamUrl by remember { mutableStateOf(streamUrl) }
    var currentTitle by remember { mutableStateOf(title) }
    var showEpisodesPanel by remember { mutableStateOf(false) }
    var initializedTracks by remember { mutableStateOf(false) }

    LaunchedEffect(movieUrl) {
        if (movieUrl.isNotEmpty() && movieRepository != null) {
            val result = movieRepository.getSeriesEpisodes(movieUrl)
            result.getOrNull()?.let {
                seasons = it
            }
        }
    }
    
    val currentStreamUrlState by rememberUpdatedState(currentStreamUrl)

    val libVLC = remember { 
        LibVLC(context, ArrayList<String>().apply { 
            // Hardware acceleration & GPU decoding
            add("--avcodec-hw=any")
            add("--codec=mediacodec_ndk,mediacodec_jni,all")
            add("--drop-late-frames")
            add("--no-skip-frames")
            add("--network-caching=3000")
            add("--file-caching=3000")
            add("--live-caching=3000")
        }) 
    }
    val mediaPlayer = remember { MediaPlayer(libVLC) }

    val playEpisode: (String, String) -> Unit = { newStreamUrl, newTitle ->
        // Save current progress before switching
        movieRepository?.let { repo ->
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                repo.updateWatchProgress(movieUrl, currentStreamUrlState, mediaPlayer.time, mediaPlayer.length)
            }
        }
        currentStreamUrl = newStreamUrl
        currentTitle = newTitle
    }
    
    // Fetch Subtitles
    LaunchedEffect(currentStreamUrl) {
        withContext(Dispatchers.IO) {
            try {
                if (!currentStreamUrl.contains("nagordola.com.bd")) {
                    val folderUrl = currentStreamUrl.substringBeforeLast("/") + "/"
                    val doc = org.jsoup.Jsoup.connect(folderUrl).get()
                    val links = doc.select("a[href\$=.srt]")
                    if (links.isNotEmpty()) {
                        val srtHref = links.first()?.attr("href")
                        if (!srtHref.isNullOrEmpty()) {
                            subtitleUrl = if (srtHref.startsWith("http")) srtHref else "$folderUrl$srtHref"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val subtitleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val cacheDir = java.io.File(context.cacheDir, "subtitles").apply { mkdirs() }
                    val originalName = getFileNameFromUri(context, uri) ?: "loaded_subtitle.srt"
                    val sanitizedName = originalName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    val localSubFile = java.io.File(cacheDir, "${System.currentTimeMillis()}_$sanitizedName")
                    
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        localSubFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    val existingTrackIds = withContext(Dispatchers.Main) {
                        mediaPlayer.spuTracks?.map { it.id }?.toSet() ?: emptySet()
                    }
                    val fileUri = Uri.fromFile(localSubFile)
                    withContext(Dispatchers.Main) {
                        val added = mediaPlayer.addSlave(
                            org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle,
                            fileUri,
                            true
                        )
                        android.util.Log.d("PlayerActivity", "Added subtitle slave $fileUri: $added")
                        
                        var updatedTracks = mediaPlayer.spuTracks?.toList() ?: emptyList()
                        var attempts = 0
                        while (attempts < 6) {
                            kotlinx.coroutines.delay(200)
                            updatedTracks = mediaPlayer.spuTracks?.toList() ?: emptyList()
                            val newlyAdded = updatedTracks.find { it.id !in existingTrackIds && it.id != -1 }
                            if (newlyAdded != null) break
                            attempts++
                        }
                        spuTracks = updatedTracks
                        val targetTrack = updatedTracks.find { it.id !in existingTrackIds && it.id != -1 }
                            ?: updatedTracks.lastOrNull { it.id != -1 }
                        if (targetTrack != null) {
                            mediaPlayer.spuTrack = targetTrack.id
                            selectedSpuTrack = targetTrack.id
                            android.widget.Toast.makeText(context, "Subtitle active: ${targetTrack.name}", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Loaded: $originalName", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerActivity", "Failed to load subtitle file", e)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Failed to load subtitle: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(currentStreamUrl, subtitleUrl) {
        initializedTracks = false
        val media = if (currentStreamUrl.startsWith("/")) {
            Media(libVLC, currentStreamUrl)
        } else {
            Media(libVLC, Uri.parse(currentStreamUrl))
        }
        // Force GPU MediaCodec hardware decoding for smooth 4K playback
        media.setHWDecoderEnabled(true, true)
        media.addOption(":codec=mediacodec_ndk,mediacodec_jni,all")
        media.addOption(":avcodec-hw=any")
        
        if (playbackPosition > 0L && currentStreamUrl == streamUrl) {
            media.addOption(":start-time=${playbackPosition / 1000}")
        }
        
        if (subtitleUrl != null) {
            media.addSlave(org.videolan.libvlc.interfaces.IMedia.Slave(
                org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle, 
                4, 
                subtitleUrl
            )) // 4 = SLAVE_PRIORITY_USER
        }
        
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentTime by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    
    val activity = context as? ComponentActivity
    var isRotationLocked by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isUnlockPromptVisible by remember { mutableStateOf(false) }

    var isDraggingTimeline by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableStateOf(0L) }
    var dragFraction by remember { mutableStateOf(0f) }
    
    var resizeMode by remember { mutableStateOf("FIT") }

    DisposableEffect(mediaPlayer) {
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    isBuffering = false
                    selectedAudioTrack = mediaPlayer.audioTrack
                    selectedSpuTrack = mediaPlayer.spuTrack
                    if (!initializedTracks) {
                        val aTracks = mediaPlayer.audioTracks?.toList() ?: emptyList()
                        audioTracks = aTracks
                        val sTracks = mediaPlayer.spuTracks?.toList() ?: emptyList()
                        spuTracks = sTracks
                        
                        // Apply Preferred Audio Language
                        val prefAudio = serverPreferences?.getPreferredAudioLanguage() ?: "en"
                        if (prefAudio != "auto") {
                            val matchedAudio = com.potflix.util.LanguageUtils.findMatchingTrack(aTracks, prefAudio, isSubtitle = false)
                            if (matchedAudio != null && mediaPlayer.audioTrack != matchedAudio.id) {
                                mediaPlayer.audioTrack = matchedAudio.id
                                selectedAudioTrack = matchedAudio.id
                            }
                        }

                        // Apply Preferred Subtitle Language
                        val prefSub = serverPreferences?.getPreferredSubtitleLanguage() ?: "off"
                        if (prefSub == "off") {
                            val disableTrack = sTracks.find { it.id == -1 || it.name.contains("disable", ignoreCase = true) }
                            if (disableTrack != null && mediaPlayer.spuTrack != disableTrack.id) {
                                mediaPlayer.spuTrack = disableTrack.id
                                selectedSpuTrack = disableTrack.id
                            } else if (mediaPlayer.spuTrack != -1) {
                                mediaPlayer.spuTrack = -1
                                selectedSpuTrack = -1
                            }
                        } else {
                            val matchedSub = com.potflix.util.LanguageUtils.findMatchingTrack(sTracks, prefSub, isSubtitle = true)
                            if (matchedSub != null && mediaPlayer.spuTrack != matchedSub.id) {
                                mediaPlayer.spuTrack = matchedSub.id
                                selectedSpuTrack = matchedSub.id
                            }
                        }
                        
                        initializedTracks = true
                    }
                }
                MediaPlayer.Event.ESAdded,
                MediaPlayer.Event.ESDeleted -> {
                    audioTracks = mediaPlayer.audioTracks?.toList() ?: emptyList()
                    spuTracks = mediaPlayer.spuTracks?.toList() ?: emptyList()
                }
                MediaPlayer.Event.ESSelected -> {
                    selectedAudioTrack = mediaPlayer.audioTrack
                    selectedSpuTrack = mediaPlayer.spuTrack
                }
                MediaPlayer.Event.Buffering -> isBuffering = event.buffering < 100f
                MediaPlayer.Event.Paused -> isPlaying = false
                MediaPlayer.Event.TimeChanged -> currentTime = mediaPlayer.time
                MediaPlayer.Event.LengthChanged -> totalDuration = mediaPlayer.length
            }
        }
        mediaPlayer.setEventListener(listener)
        
        onDispose {
            val savedTime = mediaPlayer.time
            val savedLength = mediaPlayer.length
            val savedStreamUrl = currentStreamUrlState
            movieRepository?.let { repo ->
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    repo.updateWatchProgress(movieUrl, savedStreamUrl, savedTime, savedLength)
                }
            }
            mediaPlayer.setEventListener(null)
            mediaPlayer.stop()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    // Auto-hide controls after 2.5 seconds of inactivity
    LaunchedEffect(isControlsVisible, isPlaying, isBuffering, isDraggingTimeline, isScreenLocked) {
        if (isControlsVisible && isPlaying && !isBuffering && !isPipMode && !isDraggingTimeline && !isScreenLocked) {
            delay(2500L)
            isControlsVisible = false
        }
    }

    // Auto-hide unlock prompt when screen is locked after 2.5 seconds
    LaunchedEffect(isUnlockPromptVisible) {
        if (isUnlockPromptVisible) {
            delay(2500L)
            isUnlockPromptVisible = false
        }
    }

    LaunchedEffect(isBuffering) {
        if (isBuffering) {
            isControlsVisible = true
        }
    }

    LaunchedEffect(isPipMode) {
        if (isPipMode) {
            isControlsVisible = false
        }
    }

    val playerFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    var vlcLayoutRef by remember { mutableStateOf<VLCVideoLayout?>(null) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    if (!isPipMode && isPlaying) {
                        mediaPlayer.pause()
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_START,
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    vlcLayoutRef?.let { layout ->
                        layout.post {
                            try {
                                mediaPlayer.detachViews()
                                mediaPlayer.attachViews(layout, null, true, false)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter,
                        Key.Enter -> {
                            if (isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                            true
                        }
                        Key.DirectionLeft -> {
                            mediaPlayer.time = (mediaPlayer.time - 10000).coerceAtLeast(0)
                            isControlsVisible = true
                            true
                        }
                        Key.DirectionRight -> {
                            mediaPlayer.time = (mediaPlayer.time + 10000).coerceAtMost(mediaPlayer.length)
                            isControlsVisible = true
                            true
                        }
                        Key.DirectionUp -> {
                            isControlsVisible = true
                            true
                        }
                        Key.DirectionDown -> {
                            isControlsVisible = false
                            true
                        }
                        Key.MediaPlayPause -> {
                            if (isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                            true
                        }
                        Key.MediaPlay -> {
                            mediaPlayer.play()
                            true
                        }
                        Key.MediaPause -> {
                            mediaPlayer.pause()
                            true
                        }
                        Key.MediaFastForward -> {
                            mediaPlayer.time = (mediaPlayer.time + 30000).coerceAtMost(mediaPlayer.length)
                            true
                        }
                        Key.MediaRewind -> {
                            mediaPlayer.time = (mediaPlayer.time - 30000).coerceAtLeast(0)
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    vlcLayoutRef = this
                    mediaPlayer.attachViews(this, null, true, false)
                    addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: android.view.View) {
                            post {
                                try {
                                    mediaPlayer.detachViews()
                                    mediaPlayer.attachViews(this@apply, null, true, false)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        override fun onViewDetachedFromWindow(v: android.view.View) {
                            try {
                                mediaPlayer.detachViews()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    })

                    // SurfaceView recreation handler for screen lock / unlock
                    post {
                        val surfaceView = (0 until childCount).mapNotNull { getChildAt(it) as? SurfaceView }.firstOrNull()
                        surfaceView?.holder?.addCallback(object : android.view.SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                                post {
                                    try {
                                        mediaPlayer.detachViews()
                                        mediaPlayer.attachViews(this@apply, null, true, false)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}
                            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                                try {
                                    mediaPlayer.detachViews()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        })
                    }
                }
            },
            update = { videoLayout ->
                when (resizeMode) {
                    "FIT" -> mediaPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
                    "FILL" -> {
                        mediaPlayer.aspectRatio = null
                        mediaPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_FILL 
                    }
                    "ZOOM" -> mediaPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!isPipMode) {
                        if (isScreenLocked) {
                            isUnlockPromptVisible = !isUnlockPromptVisible
                        } else {
                            isControlsVisible = !isControlsVisible
                            if (showEpisodesPanel) showEpisodesPanel = false
                        }
                    }
                }
        )
        
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }
        
        val window = (context as? ComponentActivity)?.window
        var currentBrightness by remember { mutableStateOf(window?.attributes?.screenBrightness ?: 0.5f) }
        
        var showVolumeIndicator by remember { mutableStateOf(false) }
        var showBrightnessIndicator by remember { mutableStateOf(false) }
        var volumeDragAccumulator by remember { mutableStateOf(0f) }
        
        // Horizontal seek state
        var seekDragAccumulator by remember { mutableStateOf(0f) }
        var showSeekIndicator by remember { mutableStateOf(false) }
        var seekDelta by remember { mutableStateOf(0L) }

        LaunchedEffect(showVolumeIndicator) {
            if (showVolumeIndicator) {
                delay(2000)
                showVolumeIndicator = false
            }
        }
        LaunchedEffect(showBrightnessIndicator) {
            if (showBrightnessIndicator) {
                delay(2000)
                showBrightnessIndicator = false
            }
        }
        LaunchedEffect(showSeekIndicator) {
            if (showSeekIndicator) {
                delay(1500)
                showSeekIndicator = false
                seekDelta = 0L
            }
        }

        // Gesture Areas
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Half: Brightness (vertical) + Seek (horizontal)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (!isScreenLocked) {
                                    mediaPlayer.time = (mediaPlayer.time - 10000).coerceAtLeast(0)
                                    isControlsVisible = true
                                } else {
                                    isUnlockPromptVisible = true
                                }
                            },
                            onTap = {
                                if (isScreenLocked) {
                                    isUnlockPromptVisible = !isUnlockPromptVisible
                                } else if (showEpisodesPanel) {
                                    showEpisodesPanel = false
                                } else {
                                    isControlsVisible = !isControlsVisible
                                }
                            }
                        )
                    }
                    .pointerInput(isScreenLocked) {
                        if (!isScreenLocked) {
                            var totalDx = 0f
                            var totalDy = 0f
                            var isVertical: Boolean? = null
                            detectDragGestures(
                                onDragStart = {
                                    totalDx = 0f
                                    totalDy = 0f
                                    isVertical = null
                                },
                                onDragEnd = {
                                    if (isVertical == false && seekDelta != 0L) {
                                        // Apply the seek
                                        mediaPlayer.time = (mediaPlayer.time + seekDelta).coerceIn(0, mediaPlayer.length)
                                        seekDragAccumulator = 0f
                                    }
                                    isVertical = null
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDx += dragAmount.x
                                    totalDy += dragAmount.y
                                    if (isVertical == null && (kotlin.math.abs(totalDx) > 30f || kotlin.math.abs(totalDy) > 30f)) {
                                        isVertical = kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx)
                                    }
                                    if (isVertical == true) {
                                        // Brightness
                                        val delta = -(dragAmount.y / 1500f)
                                        currentBrightness = (currentBrightness + delta).coerceIn(0f, 1f)
                                        window?.let { w ->
                                            val attrs = w.attributes
                                            attrs.screenBrightness = currentBrightness
                                            w.attributes = attrs
                                        }
                                        showBrightnessIndicator = true
                                    } else if (isVertical == false) {
                                        // Horizontal seek
                                        seekDragAccumulator += dragAmount.x
                                        val seekMs = (seekDragAccumulator / 3f).toLong() * 100L // ~100ms per 3px
                                        seekDelta = seekMs
                                        showSeekIndicator = true
                                    }
                                }
                            )
                        }
                    }
            ) {
                if (showBrightnessIndicator && !isScreenLocked) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        PlayerLevelIndicator(
                            icon = { BrightnessIcon(modifier = Modifier.size(20.dp), color = Color.White) },
                            percent = (currentBrightness * 100).toInt()
                        )
                    }
                }
            }
            // Right Half: Volume (vertical) + Seek (horizontal)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (!isScreenLocked) {
                                    mediaPlayer.time = (mediaPlayer.time + 10000).coerceAtMost(mediaPlayer.length)
                                    isControlsVisible = true
                                } else {
                                    isUnlockPromptVisible = true
                                }
                            },
                            onTap = {
                                if (isScreenLocked) {
                                    isUnlockPromptVisible = !isUnlockPromptVisible
                                } else if (showEpisodesPanel) {
                                    showEpisodesPanel = false
                                } else {
                                    isControlsVisible = !isControlsVisible
                                }
                            }
                        )
                    }
                    .pointerInput(isScreenLocked) {
                        if (!isScreenLocked) {
                            var totalDx = 0f
                            var totalDy = 0f
                            var isVertical: Boolean? = null
                            detectDragGestures(
                                onDragStart = {
                                    totalDx = 0f
                                    totalDy = 0f
                                    isVertical = null
                                },
                                onDragEnd = {
                                    if (isVertical == false && seekDelta != 0L) {
                                        mediaPlayer.time = (mediaPlayer.time + seekDelta).coerceIn(0, mediaPlayer.length)
                                        seekDragAccumulator = 0f
                                    }
                                    isVertical = null
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDx += dragAmount.x
                                    totalDy += dragAmount.y
                                    if (isVertical == null && (kotlin.math.abs(totalDx) > 30f || kotlin.math.abs(totalDy) > 30f)) {
                                        isVertical = kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx)
                                    }
                                    if (isVertical == true) {
                                        // Volume
                                        volumeDragAccumulator -= dragAmount.y
                                        val threshold = 50f
                                        if (kotlin.math.abs(volumeDragAccumulator) > threshold) {
                                            val volDelta = if (volumeDragAccumulator > 0) 1 else -1
                                            volumeDragAccumulator = 0f
                                            val newVol = (audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) + volDelta).coerceIn(0, maxVolume)
                                            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                                            currentVolume = newVol
                                            showVolumeIndicator = true
                                        }
                                    } else if (isVertical == false) {
                                        // Horizontal seek
                                        seekDragAccumulator += dragAmount.x
                                        val seekMs = (seekDragAccumulator / 3f).toLong() * 100L
                                        seekDelta = seekMs
                                        showSeekIndicator = true
                                    }
                                }
                            )
                        }
                    }
            ) {
                if (showVolumeIndicator && !isScreenLocked) {
                    val volPercent = if (maxVolume > 0) (currentVolume * 100 / maxVolume) else 0
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        PlayerLevelIndicator(
                            icon = { VolumeIcon(level = volPercent / 100f, modifier = Modifier.size(20.dp), color = Color.White) },
                            percent = volPercent
                        )
                    }
                }
            }
        }
        
        // Seek indicator overlay (centered)
        AnimatedVisibility(
            visible = showSeekIndicator && !isPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            PlayerSeekIndicator(deltaMs = seekDelta)
        }

        val currentEpisodeInfo = remember(seasons, currentStreamUrl) {
            if (seasons.isEmpty()) null
            else {
                var matchPair: Pair<com.potflix.domain.model.Season, com.potflix.domain.model.Episode>? = null
                for (season in seasons) {
                    val ep = season.episodes.find { 
                        it.url == currentStreamUrl || 
                        currentStreamUrl.endsWith(it.url.substringAfterLast("/")) ||
                        it.url.substringAfterLast("/") == currentStreamUrl.substringAfterLast("/")
                    }
                    if (ep != null) {
                        matchPair = Pair(season, ep)
                        break
                    }
                }
                matchPair
            }
        }

        // Center spinner when buffering and controls are hidden
        if (isBuffering && !isControlsVisible && !isPipMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(46.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.5.dp
                )
            }
        }

        // Screen Lock Floating Unlock Button
        AnimatedVisibility(
            visible = isScreenLocked && isUnlockPromptVisible && !isPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 36.dp)
        ) {
            Button(
                onClick = {
                    isScreenLocked = false
                    isUnlockPromptVisible = false
                    isControlsVisible = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.85f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LockOpen,
                    contentDescription = "Unlock Screen",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Tap to Unlock",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }

        // Custom Modern Controls Overlay (Disappears after 2-3s inactivity)
        AnimatedVisibility(
            visible = isControlsVisible && !isScreenLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { (context as? ComponentActivity)?.finish() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    val baseSeriesName = remember(title) {
                        title.substringBefore(" • ").substringBefore(" - Season").substringBefore(" - Episode").trim()
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = if (currentEpisodeInfo != null) baseSeriesName else currentTitle,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (currentEpisodeInfo != null) {
                            val (season, episode) = currentEpisodeInfo
                            val sNum = season.number.takeIf { it > 0 } ?: Regex("\\d+").find(season.name)?.value?.toIntOrNull() ?: 1
                            val epNum = episode.number.takeIf { (it ?: 0) > 0 } ?: Regex("E(\\d+)", RegexOption.IGNORE_CASE).find(episode.url)?.groupValues?.get(1)?.toIntOrNull()
                            val epCode = if (epNum != null) "S${sNum.toString().padStart(2, '0')}E${epNum.toString().padStart(2, '0')}" else season.name
                            val epName = if (!episode.title.startsWith("Episode ", ignoreCase = true) && !episode.title.contains(".mkv", ignoreCase = true) && !episode.title.contains(".mp4", ignoreCase = true) && episode.title.isNotBlank()) {
                                ": ${episode.title}"
                            } else if (epNum != null) {
                                " • Episode $epNum"
                            } else ""

                            Text(
                                text = "$epCode$epName",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Aspect Ratio mode indicator in top right
                    PlayerControlChip(
                        text = resizeMode,
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.AspectRatio,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        isActive = false,
                        onClick = {
                            resizeMode = when (resizeMode) {
                                "FIT" -> "FILL"
                                "FILL" -> "ZOOM"
                                else -> "FIT"
                            }
                        }
                    )
                }

                // Lower-Third Thumb-Zone Deck (High-frequency controls within natural thumb reach)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Black.copy(alpha = 0.94f)
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    // Visual Seeking Hovering Preview Card
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val density = LocalDensity.current
                        val cardWidth = 148.dp
                        val containerWidthPx = with(density) { maxWidth.toPx() }
                        val cardWidthPx = with(density) { cardWidth.toPx() }
                        val currentFraction = if (isDraggingTimeline) dragFraction
                            else if (totalDuration > 0) (currentTime.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                            else 0f
                        val offsetDp = with(density) {
                            ((containerWidthPx * currentFraction) - (cardWidthPx / 2f))
                                .coerceIn(0f, (containerWidthPx - cardWidthPx).coerceAtLeast(0f))
                                .toDp()
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isDraggingTimeline,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.padding(start = offsetDp, bottom = 4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xF0181824),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                shadowElevation = 8.dp,
                                modifier = Modifier.width(cardWidth)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Thumbnail preview placeholder badge
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.65f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Movie,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = formatTime(dragPositionMs),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    val diffMs = dragPositionMs - currentTime
                                    val diffSign = if (diffMs >= 0) "+" else "-"
                                    val diffText = "$diffSign${formatTime(kotlin.math.abs(diffMs))}"
                                    Text(
                                        text = diffText,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Timeline Scrubber Row with generous touch target (min 48dp height)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(if (isDraggingTimeline) dragPositionMs else currentTime),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = if (isDraggingTimeline) dragFraction
                                else if (totalDuration > 0) (currentTime.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                                else 0f,
                            onValueChange = { value ->
                                isDraggingTimeline = true
                                dragFraction = value
                                dragPositionMs = (value * totalDuration).toLong().coerceIn(0L, totalDuration)
                            },
                            onValueChangeFinished = {
                                mediaPlayer.time = dragPositionMs
                                currentTime = dragPositionMs
                                isDraggingTimeline = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 12.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Text(
                            text = formatTime(totalDuration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Thumb-Zone Controls Row (Generous >=48x48 dp hitboxes, reachable by thumbs)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Thumb-Zone: Screen Lock & Rotation Lock
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Prominent Screen Lock Button
                            IconButton(
                                onClick = {
                                    isScreenLocked = true
                                    isControlsVisible = false
                                    isUnlockPromptVisible = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Lock Screen",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Explicit Rotation Lock Toggle
                            IconButton(
                                onClick = {
                                    isRotationLocked = !isRotationLocked
                                    activity?.requestedOrientation = if (isRotationLocked) {
                                        ActivityInfo.SCREEN_ORIENTATION_LOCKED
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isRotationLocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        else Color.White.copy(alpha = 0.12f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isRotationLocked) Icons.Filled.ScreenLockRotation else Icons.Filled.ScreenRotation,
                                    contentDescription = if (isRotationLocked) "Rotation Locked" else "Auto-Rotation Enabled",
                                    tint = if (isRotationLocked) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Center Thumb-Zone: Skip -10s, Play/Pause/Buffer, Skip +10s
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Skip 10s Backward
                            IconButton(
                                onClick = { mediaPlayer.time = (mediaPlayer.time - 10000).coerceAtLeast(0) },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Replay10,
                                    contentDescription = "Rewind 10 Seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            // Play / Pause / Buffering Spinner
                            if (isBuffering) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(38.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.5.dp
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        if (isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                                    },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }

                            // Skip 10s Forward
                            IconButton(
                                onClick = { mediaPlayer.time = (mediaPlayer.time + 10000).coerceAtMost(mediaPlayer.length) },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forward10,
                                    contentDescription = "Skip 10 Seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        // Right Thumb-Zone: Subtitles, Audio, Episodes (min 48x48 dp hitboxes)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Subtitles
                            Box {
                                IconButton(
                                    onClick = {
                                        spuTracks = mediaPlayer.spuTracks?.toList() ?: emptyList()
                                        selectedSpuTrack = mediaPlayer.spuTrack
                                        showSpuMenu = true
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (showSpuMenu || selectedSpuTrack != -1) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            else Color.White.copy(alpha = 0.12f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Subtitles,
                                        contentDescription = "Subtitles",
                                        tint = if (selectedSpuTrack != -1) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSpuMenu,
                                    onDismissRequest = { showSpuMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E1E28), RoundedCornerShape(12.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Disable Subtitles",
                                                color = if (selectedSpuTrack == -1) MaterialTheme.colorScheme.primary else Color.White,
                                                fontWeight = if (selectedSpuTrack == -1) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            mediaPlayer.spuTrack = -1
                                            selectedSpuTrack = -1
                                            showSpuMenu = false
                                        }
                                    )
                                    val validTracks = spuTracks.filter { it.id != -1 && !it.name.contains("disable", ignoreCase = true) }
                                    if (validTracks.isNotEmpty()) {
                                        validTracks.forEach { track ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        track.name,
                                                        color = if (selectedSpuTrack == track.id) MaterialTheme.colorScheme.primary else Color.White,
                                                        fontWeight = if (selectedSpuTrack == track.id) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = {
                                                    mediaPlayer.spuTrack = track.id
                                                    selectedSpuTrack = track.id
                                                    showSpuMenu = false
                                                }
                                            )
                                        }
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Load Local Subtitle File...", color = Color.White) },
                                        onClick = {
                                            showSpuMenu = false
                                            subtitleLauncher.launch("*/*")
                                        }
                                    )
                                }
                            }

                            // Audio
                            if (audioTracks.isNotEmpty()) {
                                Box {
                                    IconButton(
                                        onClick = {
                                            audioTracks = mediaPlayer.audioTracks?.toList() ?: emptyList()
                                            selectedAudioTrack = mediaPlayer.audioTrack
                                            showAudioMenu = true
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(
                                                if (showAudioMenu) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                                else Color.White.copy(alpha = 0.12f),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Audiotrack,
                                            contentDescription = "Audio Track",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showAudioMenu,
                                        onDismissRequest = { showAudioMenu = false },
                                        modifier = Modifier.background(Color(0xFF1E1E28), RoundedCornerShape(12.dp))
                                    ) {
                                        audioTracks.forEach { track ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        track.name,
                                                        color = if (selectedAudioTrack == track.id) MaterialTheme.colorScheme.primary else Color.White,
                                                        fontWeight = if (selectedAudioTrack == track.id) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = {
                                                    mediaPlayer.audioTrack = track.id
                                                    selectedAudioTrack = track.id
                                                    showAudioMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Episodes Side Panel Trigger (Series only)
                            if (seasons.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        showEpisodesPanel = !showEpisodesPanel
                                        isControlsVisible = false
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (showEpisodesPanel) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            else Color.White.copy(alpha = 0.12f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VideoLibrary,
                                        contentDescription = "Episodes",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Next Episode Button overlay
        val isNextEpisodeVisible = remember(totalDuration, currentTime, seasons) {
            seasons.isNotEmpty() && totalDuration > 0 && (totalDuration - currentTime <= 120_000L)
        }
        AnimatedVisibility(
            visible = isNextEpisodeVisible && !isPipMode,
            enter = fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { 50 }),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 96.dp, end = 32.dp)
        ) {
            Button(
                onClick = {
                    val allEps = seasons.flatMap { it.episodes }
                    val index = allEps.indexOfFirst { it.url == currentStreamUrl }
                    if (index != -1 && index + 1 < allEps.size) {
                        val nextEp = allEps[index + 1]
                        playEpisode(nextEp.url, nextEp.title)
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Next Episode", color = Color.White)
            }
        }

        // Episodes Side Panel
        AnimatedVisibility(
            visible = showEpisodesPanel && !isPipMode,
            enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }),
            exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.35f) // Take up 35% of the screen width
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Episodes", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        IconButton(
                            onClick = { showEpisodesPanel = false },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        seasons.forEach { season ->
                            item {
                                Text(
                                    text = season.name,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            items(season.episodes.size) { index ->
                                val episode = season.episodes[index]
                                val isSelected = episode.url == currentStreamUrl
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            playEpisode(episode.url, episode.title)
                                            showEpisodesPanel = false
                                        }
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = episode.title,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                        style = MaterialTheme.typography.bodyMedium
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

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun BrightnessIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 4f
        drawCircle(color = color, radius = radius, center = center)
        val rayLength = size.minDimension / 5.5f
        val rayStart = radius + 3f
        val strokeWidth = 2.5f
        for (i in 0 until 8) {
            val angle = i * (Math.PI / 4)
            val startX = (center.x + rayStart * kotlin.math.cos(angle)).toFloat()
            val startY = (center.y + rayStart * kotlin.math.sin(angle)).toFloat()
            val endX = (center.x + (rayStart + rayLength) * kotlin.math.cos(angle)).toFloat()
            val endY = (center.y + (rayStart + rayLength) * kotlin.math.sin(angle)).toFloat()
            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun VolumeIcon(level: Float, modifier: Modifier = Modifier, color: Color = Color.White) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.15f, h * 0.35f)
            lineTo(w * 0.38f, h * 0.35f)
            lineTo(w * 0.65f, h * 0.15f)
            lineTo(w * 0.65f, h * 0.85f)
            lineTo(w * 0.38f, h * 0.65f)
            lineTo(w * 0.15f, h * 0.65f)
            close()
        }
        drawPath(path = path, color = color)

        if (level > 0.05f) {
            drawArc(
                color = color,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.48f, h * 0.25f),
                size = Size(w * 0.38f, h * 0.5f),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )
        }
        if (level > 0.5f) {
            drawArc(
                color = color,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.62f, h * 0.12f),
                size = Size(w * 0.52f, h * 0.76f),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun PlayerLevelIndicator(
    icon: @Composable () -> Unit,
    percent: Int
) {
    Box(
        modifier = Modifier
            .background(
                color = Color(0xDD14141E),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Text(
                text = "$percent%",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun PlayerSeekIndicator(
    deltaMs: Long
) {
    val sign = if (deltaMs >= 0) "+" else "-"
    val absSec = kotlin.math.abs(deltaMs) / 1000
    Box(
        modifier = Modifier
            .background(
                color = Color(0xDD14141E),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (deltaMs >= 0) Icons.Filled.Forward10 else Icons.Filled.Replay10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "$sign${absSec}s",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun PlayerControlChip(
    text: String,
    icon: (@Composable () -> Unit)? = null,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else Color.White.copy(alpha = 0.12f)
            )
            .border(
                width = 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) icon()
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

