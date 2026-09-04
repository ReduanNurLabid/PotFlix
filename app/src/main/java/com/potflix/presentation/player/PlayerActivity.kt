package com.potflix.presentation.player

import android.content.res.Configuration
import android.os.Bundle
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.potflix.presentation.theme.PotFlixTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val _isInPipMode = mutableStateOf(false)
    
    @javax.inject.Inject
    lateinit var movieRepository: com.potflix.domain.repository.MovieRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
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
                    movieRepository = movieRepository
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
    movieRepository: com.potflix.domain.repository.MovieRepository? = null
) {
    val context = LocalContext.current
    var subtitleUrl by remember { mutableStateOf<String?>(null) }
    
    var seasons by remember { mutableStateOf<List<com.potflix.domain.model.Season>>(emptyList()) }
    var currentStreamUrl by remember { mutableStateOf(streamUrl) }
    var currentTitle by remember { mutableStateOf(title) }
    var showEpisodesPanel by remember { mutableStateOf(false) }

    LaunchedEffect(movieUrl) {
        if (movieUrl.isNotEmpty() && movieRepository != null) {
            val result = movieRepository.getSeriesEpisodes(movieUrl)
            result.getOrNull()?.let {
                seasons = it
            }
        }
    }
    
    val currentStreamUrlState by rememberUpdatedState(currentStreamUrl)

    val libVLC = remember { LibVLC(context, ArrayList<String>().apply { add("--drop-late-frames") }) }
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
            mediaPlayer.addSlave(org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle, uri, true)
        }
    }

    LaunchedEffect(currentStreamUrl, subtitleUrl) {
        val media = if (currentStreamUrl.startsWith("/")) {
            Media(libVLC, currentStreamUrl)
        } else {
            Media(libVLC, Uri.parse(currentStreamUrl))
        }
        media.setHWDecoderEnabled(true, false)
        
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
    
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    
    var resizeMode by remember { mutableStateOf("FIT") }

    var audioTracks by remember { mutableStateOf<List<MediaPlayer.TrackDescription>>(emptyList()) }
    var spuTracks by remember { mutableStateOf<List<MediaPlayer.TrackDescription>>(emptyList()) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSpuMenu by remember { mutableStateOf(false) }
    var initializedTracks by remember { mutableStateOf(false) }

    DisposableEffect(mediaPlayer) {
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    isBuffering = false
                    if (!initializedTracks) {
                        val aTracks = mediaPlayer.audioTracks?.toList() ?: emptyList()
                        audioTracks = aTracks
                        spuTracks = mediaPlayer.spuTracks?.toList() ?: emptyList()
                        
                        // Default to English audio if available
                        val engAudio = aTracks.find { it.name.contains("English", ignoreCase = true) || it.name.contains("eng", ignoreCase = true) }
                        if (engAudio != null && mediaPlayer.audioTrack != engAudio.id) {
                            mediaPlayer.audioTrack = engAudio.id
                        }
                        
                        initializedTracks = true
                    }
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

    // Auto-hide controls
    LaunchedEffect(isControlsVisible, isPlaying, isBuffering) {
        if (isControlsVisible && isPlaying && !isBuffering && !isPipMode) {
            delay(4000)
            isControlsVisible = false
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
                    mediaPlayer.attachViews(this, null, false, false)
                    addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: android.view.View) {
                            try {
                                mediaPlayer.detachViews()
                                mediaPlayer.attachViews(this@apply, null, false, false)
                            } catch (e: Exception) {
                                e.printStackTrace()
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
                        isControlsVisible = !isControlsVisible
                        if (showEpisodesPanel) showEpisodesPanel = false
                    }
                }
        )
        
        // Buffering / Loading overlay — always visible when buffering
        if (isBuffering && !isPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 120.dp) // shift up to not overlap with main controls
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading...", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        
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
                                mediaPlayer.time = (mediaPlayer.time - 10000).coerceAtLeast(0)
                                isControlsVisible = true
                            },
                            onTap = {
                                if (showEpisodesPanel) {
                                    showEpisodesPanel = false
                                } else {
                                    isControlsVisible = !isControlsVisible
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
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
            ) {
                if (showBrightnessIndicator) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "☀️ ${(currentBrightness * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
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
                                mediaPlayer.time = (mediaPlayer.time + 10000).coerceAtMost(mediaPlayer.length)
                                isControlsVisible = true
                            },
                            onTap = {
                                if (showEpisodesPanel) {
                                    showEpisodesPanel = false
                                } else {
                                    isControlsVisible = !isControlsVisible
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
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
            ) {
                if (showVolumeIndicator) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "🔊 ${(currentVolume * 100 / maxVolume)}%",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
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
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                val sign = if (seekDelta >= 0) "+" else "-"
                val absSec = kotlin.math.abs(seekDelta) / 1000
                Text(
                    text = "$sign${absSec}s",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        // Custom Modern Controls Overlay
        AnimatedVisibility(
            visible = isControlsVisible,
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
                        .padding(16.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { (context as? ComponentActivity)?.finish() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = currentTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    
                    // Episodes Button
                    if (seasons.isNotEmpty()) {
                        TextButton(onClick = { 
                            showEpisodesPanel = !showEpisodesPanel
                            isControlsVisible = false 
                        }) {
                            Text("Episodes", color = Color.White)
                        }
                    }
                    
                    // Subtitle Button
                    Box {
                        TextButton(onClick = { showSpuMenu = true }) {
                            Text("Subtitles", color = Color.White)
                        }
                        DropdownMenu(
                            expanded = showSpuMenu,
                            onDismissRequest = { showSpuMenu = false },
                            modifier = Modifier.background(Color.DarkGray)
                        ) {
                            if (spuTracks.isNotEmpty()) {
                                spuTracks.forEach { track ->
                                    DropdownMenuItem(
                                        text = { Text(track.name, color = if (mediaPlayer.spuTrack == track.id) MaterialTheme.colorScheme.primary else Color.White) },
                                        onClick = { 
                                            mediaPlayer.spuTrack = track.id
                                            showSpuMenu = false
                                        }
                                    )
                                }
                            }
                            DropdownMenuItem(
                                text = { Text("Load Local File...", color = Color.White) },
                                onClick = { 
                                    showSpuMenu = false
                                    subtitleLauncher.launch("*/*")
                                }
                            )
                        }
                    }

                    // Audio Button
                    if (audioTracks.isNotEmpty()) {
                        Box {
                            TextButton(onClick = { showAudioMenu = true }) {
                                Text("Audio", color = Color.White)
                            }
                            DropdownMenu(
                                expanded = showAudioMenu,
                                onDismissRequest = { showAudioMenu = false },
                                modifier = Modifier.background(Color.DarkGray)
                            ) {
                                audioTracks.forEach { track ->
                                    DropdownMenuItem(
                                        text = { Text(track.name, color = if (mediaPlayer.audioTrack == track.id) MaterialTheme.colorScheme.primary else Color.White) },
                                        onClick = { 
                                            mediaPlayer.audioTrack = track.id
                                            showAudioMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    TextButton(onClick = {
                        resizeMode = when (resizeMode) {
                            "FIT" -> "FILL"
                            "FILL" -> "ZOOM"
                            else -> "FIT"
                        }
                    }) {
                        Text(
                            text = resizeMode,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Center Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    // Rewind
                    IconButton(
                        onClick = { mediaPlayer.time = (mediaPlayer.time - 10000).coerceAtLeast(0) },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_rew),
                            contentDescription = "Rewind",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    // Play/Pause
                    IconButton(
                        onClick = { 
                            if (isPlaying) mediaPlayer.pause() else mediaPlayer.play() 
                        },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(70.dp)
                        )
                    }
                    // Fast Forward
                    IconButton(
                        onClick = { mediaPlayer.time = (mediaPlayer.time + 10000).coerceAtMost(mediaPlayer.length) },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_ff),
                            contentDescription = "Fast Forward",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Bottom Bar (Progress)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .align(Alignment.BottomCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentTime),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = if (totalDuration > 0) (currentTime.toFloat() / totalDuration.toFloat()) else 0f,
                        onValueChange = { value ->
                            val newPosition = (value * totalDuration).toLong()
                            mediaPlayer.time = newPosition
                            currentTime = newPosition
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Text(
                        text = formatTime(totalDuration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
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
                .padding(bottom = 120.dp, end = 32.dp)
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
                        IconButton(onClick = { showEpisodesPanel = false }) {
                            Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
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
