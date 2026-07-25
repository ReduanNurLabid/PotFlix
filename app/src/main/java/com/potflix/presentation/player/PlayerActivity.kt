package com.potflix.presentation.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.potflix.presentation.theme.PotFlixTheme
import dagger.hilt.android.AndroidEntryPoint

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
                VideoPlayer(streamUrl = streamUrl, title = title, isPipMode = _isInPipMode.value)
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

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(streamUrl: String, title: String, isPipMode: Boolean = false) {
    val context = LocalContext.current
    var subtitleUrl by remember { mutableStateOf<String?>(null) }
    var resizeMode by remember { mutableStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    
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
    
    val trackSelector = remember { androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context) }
    
    val exoPlayer = remember {
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            
        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context, extractorsFactory)
                    .setDataSourceFactory(
                        DefaultHttpDataSource.Factory().setUserAgent("PotFlix-Android/1.0")
                    )
            )
            .build()
    }

    LaunchedEffect(streamUrl, subtitleUrl) {
        val mediaItemBuilder = MediaItem.Builder().setUri(streamUrl)
        
        if (subtitleUrl != null) {
            val subtitle = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUrl))
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("en")
                .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitle))
        }
        
        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentTime by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)
        
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Battery Manager (Low Power Mode)
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    val batteryPct = level * 100 / scale.toFloat()
                    if (batteryPct <= 20f) {
                        // Low power mode: Cap at 1080p and lower bitrate
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .setMaxVideoSize(1920, 1080)
                                .setMaxVideoBitrate(5000000)
                                .build()
                        )
                    } else {
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .clearVideoSizeConstraints()
                                .build()
                        )
                    }
                }
            }
        }
        context.registerReceiver(receiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Auto-hide controls
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying && !isPipMode) {
            kotlinx.coroutines.delay(4000)
            isControlsVisible = false
        }
    }

    // PiP force hide
    LaunchedEffect(isPipMode) {
        if (isPipMode) {
            isControlsVisible = false
        }
    }

    // Progress tracker
    LaunchedEffect(isPlaying, isControlsVisible) {
        while (true) {
            currentTime = exoPlayer.currentPosition.coerceAtLeast(0L)
            kotlinx.coroutines.delay(1000)
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Hide default controls completely
                    keepScreenOn = true
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
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

        // Auto-hide indicators
        LaunchedEffect(showVolumeIndicator) {
            if (showVolumeIndicator) {
                kotlinx.coroutines.delay(2000)
                showVolumeIndicator = false
            }
        }
        LaunchedEffect(showBrightnessIndicator) {
            if (showBrightnessIndicator) {
                kotlinx.coroutines.delay(2000)
                showBrightnessIndicator = false
            }
        }

        // Gesture Areas (Seek, Brightness, Volume)
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
            // Left Half: Seek Back & Brightness
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                exoPlayer.seekBack()
                                isControlsVisible = true
                            },
                            onTap = { isControlsVisible = !isControlsVisible }
                        )
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val delta = -(dragAmount / 500f) // Sensitivity
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
                androidx.compose.animation.AnimatedVisibility(
                    visible = showBrightnessIndicator,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = "☀️ ${(currentBrightness * 100).toInt()}%",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            // Right Half: Seek Forward & Volume
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                exoPlayer.seekForward()
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
                androidx.compose.animation.AnimatedVisibility(
                    visible = showVolumeIndicator,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = "🔊 ${(currentVolume * 100 / maxVolume)}%",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // Custom Modern Controls Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = isControlsVisible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
            ) {
                // Top Bar
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(androidx.compose.ui.Alignment.TopStart),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.IconButton(
                        onClick = { (context as? ComponentActivity)?.finish() }
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    androidx.compose.material3.Text(
                        text = title,
                        color = androidx.compose.ui.graphics.Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    androidx.compose.material3.TextButton(onClick = {
                        try {
                            androidx.media3.ui.TrackSelectionDialogBuilder(
                                context,
                                "Audio Tracks",
                                exoPlayer,
                                androidx.media3.common.C.TRACK_TYPE_AUDIO
                            ).build().show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }) {
                        androidx.compose.material3.Text(
                            text = "AUDIO",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        try {
                            androidx.media3.ui.TrackSelectionDialogBuilder(
                                context,
                                "Subtitles",
                                exoPlayer,
                                androidx.media3.common.C.TRACK_TYPE_TEXT
                            ).build().show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }) {
                        androidx.compose.material3.Text(
                            text = "SUBS",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        resizeMode = when (resizeMode) {
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }) {
                        androidx.compose.material3.Text(
                            text = when (resizeMode) {
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> "FIT"
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> "STRETCH"
                                else -> "ZOOM"
                            },
                            color = androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Center Controls
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(48.dp)
                ) {
                    // Rewind
                    androidx.compose.material3.IconButton(
                        onClick = { exoPlayer.seekBack() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_rew),
                            contentDescription = "Rewind",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    // Play/Pause
                    androidx.compose.material3.IconButton(
                        onClick = { 
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play() 
                        },
                        modifier = Modifier.size(80.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(70.dp)
                        )
                    }
                    // Fast Forward
                    androidx.compose.material3.IconButton(
                        onClick = { exoPlayer.seekForward() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_ff),
                            contentDescription = "Fast Forward",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Bottom Bar (Progress)
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .align(androidx.compose.ui.Alignment.BottomCenter),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Text(
                        text = formatTime(currentTime),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                    )
                    androidx.compose.material3.Slider(
                        value = if (totalDuration > 0) (currentTime.toFloat() / totalDuration.toFloat()) else 0f,
                        onValueChange = { value ->
                            val newPosition = (value * totalDuration).toLong()
                            exoPlayer.seekTo(newPosition)
                            currentTime = newPosition
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            activeTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)
                        )
                    )
                    androidx.compose.material3.Text(
                        text = formatTime(totalDuration),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
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
