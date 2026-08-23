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

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val _isInPipMode = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val streamUrl = intent.getStringExtra("streamUrl") ?: return
        val title = intent.getStringExtra("title") ?: "Video"

        setContent {
            PotFlixTheme {
                VlcVideoPlayer(streamUrl = streamUrl, title = title, isPipMode = _isInPipMode.value)
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
fun VlcVideoPlayer(streamUrl: String, title: String, isPipMode: Boolean = false) {
    val context = LocalContext.current
    var subtitleUrl by remember { mutableStateOf<String?>(null) }
    
    // Fetch Subtitles
    LaunchedEffect(streamUrl) {
        try {
            val folderUrl = streamUrl.substringBeforeLast("/") + "/"
            val doc = org.jsoup.Jsoup.connect(folderUrl).get()
            val links = doc.select("a[href\$=.srt]")
            if (links.isNotEmpty()) {
                val srtHref = links.first()?.attr("href")
                if (!srtHref.isNullOrEmpty()) {
                    subtitleUrl = if (srtHref.startsWith("http")) srtHref else "$folderUrl$srtHref"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val libVLC = remember { LibVLC(context, ArrayList<String>().apply { add("--drop-late-frames") }) }
    val mediaPlayer = remember { MediaPlayer(libVLC) }

    LaunchedEffect(streamUrl, subtitleUrl) {
        val media = if (streamUrl.startsWith("/")) {
            Media(libVLC, streamUrl)
        } else {
            Media(libVLC, Uri.parse(streamUrl))
        }
        media.setHWDecoderEnabled(true, false)
        
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
    var currentTime by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    
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
                MediaPlayer.Event.Paused -> isPlaying = false
                MediaPlayer.Event.TimeChanged -> currentTime = mediaPlayer.time
                MediaPlayer.Event.LengthChanged -> totalDuration = mediaPlayer.length
            }
        }
        mediaPlayer.setEventListener(listener)
        
        onDispose {
            mediaPlayer.setEventListener(null)
            mediaPlayer.stop()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    // Auto-hide controls
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying && !isPipMode) {
            delay(4000)
            isControlsVisible = false
        }
    }

    LaunchedEffect(isPipMode) {
        if (isPipMode) {
            isControlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    mediaPlayer.attachViews(this, null, false, false)
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
                    if (!isPipMode) isControlsVisible = !isControlsVisible
                }
        )
        
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }
        
        val window = (context as? ComponentActivity)?.window
        var currentBrightness by remember { mutableStateOf(window?.attributes?.screenBrightness ?: 0.5f) }
        
        var showVolumeIndicator by remember { mutableStateOf(false) }
        var showBrightnessIndicator by remember { mutableStateOf(false) }

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

        // Gesture Areas
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Half: Seek Back & Brightness
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
                            onTap = { isControlsVisible = !isControlsVisible }
                        )
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val delta = -(dragAmount / 500f)
                            currentBrightness = (currentBrightness + delta).coerceIn(0f, 1f)
                            window?.let { w ->
                                val attrs = w.attributes
                                attrs.screenBrightness = currentBrightness
                                w.attributes = attrs
                            }
                            showBrightnessIndicator = true
                        }
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
            // Right Half: Seek Forward & Volume
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
                            onTap = { isControlsVisible = !isControlsVisible }
                        )
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val delta = -(dragAmount / 150f)
                            val volDelta = if (delta > 0) 1 else if (delta < 0) -1 else 0
                            if (volDelta != 0) {
                                val newVol = (audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) + volDelta).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                                currentVolume = newVol
                                showVolumeIndicator = true
                            }
                        }
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
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    // Subtitle Button
                    if (spuTracks.isNotEmpty()) {
                        Box {
                            TextButton(onClick = { showSpuMenu = true }) {
                                Text("Subtitles", color = Color.White)
                            }
                            DropdownMenu(
                                expanded = showSpuMenu,
                                onDismissRequest = { showSpuMenu = false },
                                modifier = Modifier.background(Color.DarkGray)
                            ) {
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
