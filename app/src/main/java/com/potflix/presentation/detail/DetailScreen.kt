package com.potflix.presentation.detail

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import com.potflix.presentation.theme.PotFlixRed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.potflix.presentation.navigation.Screen
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    navController: NavController,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val movie by viewModel.movie.collectAsState()
    val seasons by viewModel.seasons.collectAsState()
    val streamError by viewModel.streamError.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedSeasonIndex by viewModel.selectedSeasonIndex.collectAsState()
    val lastPlayedEpisodeUrl by viewModel.lastPlayedEpisodeUrl.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val isUserLoggedIn = remember {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let { !it.isAnonymous } == true
    }

    var showEditTmdbDialog by remember { mutableStateOf(false) }
    var isPlayingTrailer by remember { mutableStateOf(false) }
    var showGuestDownloadDialog by remember { mutableStateOf(false) }
    var showGuestTmdbDialog by remember { mutableStateOf(false) }

    var showMobileDataPrompt by remember { mutableStateOf(false) }
    var pendingStreamUrl by remember { mutableStateOf<String?>(null) }
    var pendingStreamTitle by remember { mutableStateOf<String?>(null) }

    val actuallyPlayVideo = { streamUrl: String, title: String ->
        isPlayingTrailer = false
        val pos = movie?.playbackPosition ?: 0L
        val movieUrl = movie?.url ?: streamUrl
        navController.navigate(Screen.Player.createRoute(movieUrl, streamUrl, title, pos))
    }

    val playVideo = { streamUrl: String, title: String ->
        val isRemoteStream = streamUrl.startsWith("http://", ignoreCase = true) ||
                             streamUrl.startsWith("https://", ignoreCase = true) ||
                             streamUrl.startsWith("ftp://", ignoreCase = true)

        if (isRemoteStream && com.potflix.util.NetworkUtils.isMobileData(context)) {
            pendingStreamUrl = streamUrl
            pendingStreamTitle = title
            showMobileDataPrompt = true
        } else {
            actuallyPlayVideo(streamUrl, title)
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.reload()
        }
    }
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(movie?.title ?: "Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 80.dp
                )
            ) {
            item {
                val currentContext = androidx.compose.ui.platform.LocalContext.current
                DetailCoverHeader(
                    movie = movie,
                    isPlayingTrailer = isPlayingTrailer,
                    onPlayTrailer = {
                        if (!movie?.trailerKey.isNullOrBlank()) {
                            isPlayingTrailer = true
                        } else {
                            try {
                                val query = android.net.Uri.encode("${movie?.title ?: ""} official trailer")
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://www.youtube.com/results?search_query=$query")
                                )
                                currentContext.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("DetailScreen", "Failed to open YouTube search", e)
                            }
                        }
                    },
                    onCloseTrailer = { isPlayingTrailer = false }
                )
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = movie?.title ?: "Unknown Title",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Netflix-style Metadata Row
                    val inWatchlist by viewModel.isInWatchlist.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        movie?.rating?.let { rating ->
                            if (rating > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format("%.1f", rating),
                                        color = Color(0xFFFFC107),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        movie?.year?.let { year ->
                            if (year > 0) {
                                if ((movie?.rating ?: 0.0) > 0) Text("•", color = Color.Gray)
                                Text(year.toString(), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                            }
                        }
                        movie?.quality?.let { quality ->
                            Text("•", color = Color.Gray)
                            Box(
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(quality.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (seasons.isNotEmpty()) {
                            Text("•", color = Color.Gray)
                            Text(
                                text = "${seasons.size} Season${if (seasons.size > 1) "s" else ""}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ─── PRIMARY PLAY BUTTON (Movies & TV Series) ───
                    var playBtnFocused by remember { mutableStateOf(false) }
                    val playFocusRequester = remember { FocusRequester() }
                    val isTvSeries = movie?.type == "tv" || seasons.isNotEmpty()

                    LaunchedEffect(Unit) {
                        delay(200)
                        try {
                            playFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }

                    val isMovieVideoUrl = movie?.url?.let { 
                        it.endsWith(".mkv", true) || 
                        it.endsWith(".mp4", true) || 
                        it.endsWith(".avi", true) || 
                        it.endsWith(".webm", true) 
                    } == true

                    if (isTvSeries) {
                        val allEpisodesWithSeason = seasons.flatMap { s -> s.episodes.map { ep -> Pair(s, ep) } }
                        val lastPlayedPair = allEpisodesWithSeason.find { it.second.url == lastPlayedEpisodeUrl }
                        val firstPair = allEpisodesWithSeason.firstOrNull()
                        val targetPair = lastPlayedPair ?: firstPair

                        if (targetPair != null) {
                            val (targetSeason, targetEp) = targetPair
                            val isResume = lastPlayedPair != null
                            val epTitle = if (targetEp.title.isNotBlank()) " • ${targetEp.title}" else ""
                            val playBtnText = if (isResume) {
                                "Resume S${targetSeason.number}:E${targetEp.number ?: 1}$epTitle"
                            } else {
                                "Play S${targetSeason.number}:E${targetEp.number ?: 1}$epTitle"
                            }

                            Button(
                                onClick = {
                                    val epDownload = downloads.find { it.streamUrl == targetEp.url }
                                    viewModel.onPlayStarted()
                                    viewModel.saveLastPlayedEpisode(targetEp.url)
                                    val fullTitle = if (movie != null) "${movie?.title} • ${targetEp.title}" else targetEp.title
                                    if (epDownload?.status == com.potflix.service.DownloadService.STATUS_SUCCESSFUL && epDownload.localUri != null) {
                                        playVideo(epDownload.localUri, fullTitle)
                                    } else {
                                        playVideo(targetEp.url, fullTitle)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .focusRequester(playFocusRequester)
                                    .onFocusChanged { playBtnFocused = it.isFocused },
                                border = if (playBtnFocused) androidx.compose.foundation.BorderStroke(3.dp, Color.White) else null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (playBtnFocused) Color.White else MaterialTheme.colorScheme.primary,
                                    contentColor = if (playBtnFocused) Color.Black else Color.White
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(26.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = playBtnText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        } else if (isLoading) {
                            Box(modifier = Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                        }
                    } else if (isMovieVideoUrl) {
                        val isResume = (movie?.playbackPosition ?: 0L) > 60_000L
                        val movieDownloaded = downloads.find {
                            (it.streamUrl == movie?.url || (movie?.title != null && it.title.equals(movie?.title, ignoreCase = true))) &&
                            it.status == com.potflix.service.DownloadService.STATUS_SUCCESSFUL &&
                            it.localUri != null
                        }
                        val playBtnText = when {
                            movieDownloaded != null && isResume -> "Resume Movie (Offline)"
                            movieDownloaded != null -> "Play Movie (Offline)"
                            isResume -> "Resume Movie"
                            else -> "Play Movie"
                        }

                        Button(
                            onClick = {
                                movie?.let {
                                    viewModel.onPlayStarted()
                                    if (movieDownloaded != null && movieDownloaded.localUri != null) {
                                        actuallyPlayVideo(movieDownloaded.localUri, it.title)
                                    } else {
                                        playVideo(it.url, it.title)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .focusRequester(playFocusRequester)
                                .onFocusChanged { playBtnFocused = it.isFocused },
                            border = if (playBtnFocused) androidx.compose.foundation.BorderStroke(3.dp, Color.White) else null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (playBtnFocused) Color.White else MaterialTheme.colorScheme.primary,
                                contentColor = if (playBtnFocused) Color.Black else Color.White
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(playBtnText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ─── SECONDARY ACTIONS ROW (Download, Trailer, My List, Fix TMDB) ───
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isWideScreen = configuration.screenWidthDp >= 600

                    // 1. Download State & Callbacks (for Movies)
                    val movieDownload = if (isMovieVideoUrl && !isTvSeries) {
                        downloads.find { it.streamUrl == movie?.url }
                    } else null

                    val onDownloadClick: () -> Unit = {
                        if (!isUserLoggedIn) {
                            showGuestDownloadDialog = true
                        } else {
                            if (movieDownload == null || movieDownload.status == com.potflix.service.DownloadService.STATUS_FAILED) {
                                movie?.let {
                                    viewModel.startDownload(it.title, it.url, it.poster)
                                }
                            } else if (movieDownload.status == com.potflix.service.DownloadService.STATUS_SUCCESSFUL) {
                                val streamUrl = movieDownload.localUri
                                if (streamUrl != null) {
                                    val movieUrl = movie?.url ?: streamUrl
                                    navController.navigate(Screen.Player.createRoute(movieUrl, streamUrl, movieDownload.title))
                                }
                            } else {
                                navController.navigate(Screen.Watchlist.route)
                            }
                        }
                    }

                    // 2. Trailer Action Callback
                    val detailContext = androidx.compose.ui.platform.LocalContext.current
                    val onTrailerActionClick: () -> Unit = {
                        if (!movie?.trailerKey.isNullOrBlank()) {
                            isPlayingTrailer = true
                        } else {
                            try {
                                val query = android.net.Uri.encode("${movie?.title ?: ""} official trailer")
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://www.youtube.com/results?search_query=$query")
                                ).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                detailContext.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("DetailScreen", "Failed to open YouTube search", e)
                            }
                        }
                    }

                    // Predefined composable button helpers
                    val downloadButton: @Composable (Modifier) -> Unit = { btnModifier ->
                        val isDownloaded = movieDownload != null && movieDownload.status == com.potflix.service.DownloadService.STATUS_SUCCESSFUL
                        val isDownloading = movieDownload != null && movieDownload.status != com.potflix.service.DownloadService.STATUS_FAILED && !isDownloaded

                        val dlIcon: @Composable () -> Unit = {
                            when {
                                isDownloaded -> Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                isDownloading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                else -> Icon(painter = androidx.compose.ui.res.painterResource(id = com.potflix.R.drawable.ic_nav_downloads), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }

                        val dlText = when {
                            isDownloaded -> "Play Offline"
                            isDownloading -> "${movieDownload?.progress ?: 0}%"
                            else -> "Download"
                        }

                        DetailActionButton(
                            icon = dlIcon,
                            label = dlText,
                            onClick = onDownloadClick,
                            isActive = isDownloaded,
                            activeColor = Color(0xFF4CAF50),
                            modifier = btnModifier
                        )
                    }

                    val trailerButton: @Composable (Modifier) -> Unit = { btnModifier ->
                        DetailActionButton(
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Watch Trailer",
                                    tint = PotFlixRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = "Watch Trailer",
                            onClick = onTrailerActionClick,
                            modifier = btnModifier
                        )
                    }

                    val myListButton: @Composable (Modifier) -> Unit = { btnModifier ->
                        DetailActionButton(
                            icon = {
                                Icon(
                                    imageVector = if (inWatchlist) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (inWatchlist) PotFlixRed else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = if (inWatchlist) "In Watchlist" else "My List",
                            onClick = { viewModel.toggleWatchlist() },
                            isActive = inWatchlist,
                            activeColor = PotFlixRed,
                            modifier = btnModifier
                        )
                    }

                    val fixTmdbButton: @Composable (Modifier) -> Unit = { btnModifier ->
                        DetailActionButton(
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Fix TMDB",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            label = "Fix TMDB",
                            onClick = {
                                if (!isUserLoggedIn) {
                                    showGuestTmdbDialog = true
                                } else {
                                    showEditTmdbDialog = true
                                }
                            },
                            modifier = btnModifier
                        )
                    }

                    if (isWideScreen) {
                        // Wide Screen (TV / Tablet): 1 horizontal row with plenty of room
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMovieVideoUrl && !isTvSeries) {
                                downloadButton(Modifier.weight(1f))
                            }
                            trailerButton(Modifier.weight(1f))
                            myListButton(Modifier.weight(1f))
                            fixTmdbButton(Modifier.weight(1f))
                        }
                    } else {
                        // Mobile Portrait: Clean 2-row layout so text is NEVER squished or cropped
                        if (isMovieVideoUrl && !isTvSeries) {
                            // Row 1: Media Playback Actions (Download & Trailer)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                downloadButton(Modifier.weight(1f))
                                trailerButton(Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 2: Library & Metadata Actions (My List & Fix TMDB)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                myListButton(Modifier.weight(1f))
                                fixTmdbButton(Modifier.weight(1f))
                            }
                        } else {
                            // TV Series: Download is in episode list, so Trailer & My List take Row 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                trailerButton(Modifier.weight(1f))
                                myListButton(Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            fixTmdbButton(Modifier.fillMaxWidth())
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Overview
                    Text(
                        text = movie?.overview ?: "No overview available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 22.sp
                    )

                    // Genres
                    if (!movie?.genres.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = movie!!.genres!!.joinToString(" • "),
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    // Cast
                    if (!movie?.cast.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cast",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(movie!!.cast!!) { actor ->
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                        .background(Color(0xFF222222))
                                        .clickable {
                                            navController.navigate(com.potflix.presentation.navigation.Screen.Search.createRoute(actor))
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = actor,
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Unavailable Stream / Episodes Error Card
                    if (!isLoading && !isMovieVideoUrl && seasons.isEmpty()) {
                        val isWifi = com.potflix.util.NetworkUtils.isWifiOrEthernet(context)
                        val isMobile = com.potflix.util.NetworkUtils.isMobileData(context)
                        val downloadedEpisodesForSeries = downloads.filter {
                            (it.title.startsWith(movie?.title ?: "", ignoreCase = true) || it.title.contains(movie?.title ?: "", ignoreCase = true)) &&
                            it.status == com.potflix.service.DownloadService.STATUS_SUCCESSFUL &&
                            it.localUri != null
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isMobile || !isWifi) {
                            // On Mobile Data or disconnected from Wi-Fi
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF261D10)),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB74D),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Wi-Fi Connection Required",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (isMobile) {
                                            "Local ISP content servers are only reachable over broadband Wi-Fi. You are currently connected to Mobile Data."
                                        } else {
                                            "Local ISP content servers are only reachable over broadband Wi-Fi. No Wi-Fi connection detected."
                                        },
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = { com.potflix.util.NetworkUtils.openWifiSettings(context) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open Wi-Fi Settings", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.reload() },
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Retry", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            if (downloadedEpisodesForSeries.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E261E)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Available Offline (${downloadedEpisodesForSeries.size})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        downloadedEpisodesForSeries.forEach { epDl ->
                                            val localPath = epDl.localUri ?: return@forEach
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = epDl.title,
                                                    fontSize = 13.sp,
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Button(
                                                    onClick = { actuallyPlayVideo(localPath, epDl.title) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(16.dp), tint = Color.Black)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Play", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // On Wi-Fi: if no episodes, show error or TMDB correction
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Stream or Episodes Unavailable",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (streamError != null) "Error: $streamError" else "The server did not return playable streams or episodes. You can fix the TMDB metadata to resolve it.",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                if (!isUserLoggedIn) {
                                                    showGuestTmdbDialog = true
                                                } else {
                                                    showEditTmdbDialog = true
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Fix TMDB Info", fontSize = 13.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.reload() },
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Retry", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (seasons.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Episodes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(seasons.size) { index ->
                                val season = seasons[index]
                                val isSelected = index == selectedSeasonIndex
                                var seasonFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f))
                                        .onFocusChanged { seasonFocused = it.isFocused }
                                        .then(if (seasonFocused) Modifier.border(2.dp, Color.White, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) else Modifier)
                                        .clickable { viewModel.setSeasonIndex(index) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = season.name,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (selectedSeason != null) {
                    items(selectedSeason.episodes) { episode ->
                        var episodeFocused by remember { mutableStateOf(false) }
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { episodeFocused = it.isFocused }
                                .then(if (episodeFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(Color.White.copy(alpha=0.05f)) else Modifier)
                                .clickable {
                                    val epDownload = downloads.find { it.streamUrl == episode.url }
                                    viewModel.onPlayStarted()
                                    viewModel.saveLastPlayedEpisode(episode.url)
                                    
                                    val fullTitle = if (movie != null) "${movie?.title} • ${episode.title}" else episode.title
                                    if (epDownload?.status == com.potflix.service.DownloadService.STATUS_SUCCESSFUL && epDownload.localUri != null) {
                                        playVideo(epDownload.localUri, fullTitle)
                                    } else {
                                        playVideo(episode.url, fullTitle)
                                    }
                                },
                            headlineContent = { 
                                val titleText = if ((episode.number ?: 0) > 0) "${episode.number}. ${episode.title}" else episode.title
                                Text(
                                    text = titleText, 
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                ) 
                            },
                            supportingContent = { 
                                Column {
                                    if (!episode.overview.isNullOrEmpty()) {
                                        Text(
                                            text = episode.overview ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f),
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                    if (!episode.quality.isNullOrEmpty()) {
                                        Text(episode.quality ?: "", style = MaterialTheme.typography.labelMedium) 
                                    }
                                    if (episode.url == lastPlayedEpisodeUrl) {
                                        Text(
                                            text = "Last Played",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            },
                            leadingContent = {
                                if (episode.stillPath != null) {
                                    Box(modifier = Modifier.width(120.dp).height(68.dp)) {
                                        AsyncImage(
                                            model = episode.stillPath,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.align(Alignment.Center).size(24.dp).background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
                                        )
                                    }
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(40.dp))
                                }
                            },
                            trailingContent = {
                                val epDownload = downloads.find { it.streamUrl == episode.url }
                                IconButton(
                                    onClick = {
                                        if (!isUserLoggedIn) {
                                            showGuestDownloadDialog = true
                                        } else {
                                            if (epDownload == null || epDownload.status == com.potflix.service.DownloadService.STATUS_FAILED) {
                                                val fullTitle = "${movie?.title ?: "TV Show"} - S${selectedSeason?.number ?: 1}E${episode.number ?: 1} - ${episode.title}"
                                                viewModel.startDownload(
                                                    title = fullTitle,
                                                    streamUrl = episode.url,
                                                    poster = episode.stillPath ?: movie?.poster
                                                )
                                            } else {
                                                navController.navigate(Screen.Watchlist.route)
                                            }
                                        }
                                    }
                                ) {
                                    if (epDownload != null) {
                                        if (epDownload.status == com.potflix.service.DownloadService.STATUS_SUCCESSFUL) {
                                            Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = "Downloaded", tint = Color(0xFF4CAF50))
                                        } else if (epDownload.status == com.potflix.service.DownloadService.STATUS_FAILED) {
                                            Icon(painter = androidx.compose.ui.res.painterResource(id = com.potflix.R.drawable.ic_nav_downloads), contentDescription = "Retry", tint = MaterialTheme.colorScheme.error)
                                        } else {
                                            CircularProgressIndicator(
                                                progress = { epDownload.progress / 100f },
                                                modifier = Modifier.size(24.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    } else {
                                        Icon(painter = androidx.compose.ui.res.painterResource(id = com.potflix.R.drawable.ic_nav_downloads), contentDescription = "Download")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        if (pullToRefreshState.verticalOffset > 0f || pullToRefreshState.isRefreshing) {
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = padding.calculateTopPadding()),
                containerColor = Color(0xFF1E1E1E),
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

    if (showEditTmdbDialog) {
        val currentMovie = movie
        TmdbCorrectionDialog(
            initialTitle = currentMovie?.title ?: "",
            initialType = currentMovie?.type ?: "tv",
            onDismiss = { showEditTmdbDialog = false },
            onSelectTmdb = { tmdbId, type ->
                viewModel.applyTmdbCorrection(tmdbId, type) {
                    showEditTmdbDialog = false
                }
            },
            onSearch = { query, type, callback ->
                viewModel.searchTmdb(query, type, callback)
            }
        )
    }

    if (showGuestDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showGuestDownloadDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Sign In Required", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "Offline downloading is available exclusively for registered accounts. Please sign in or create a free account to download content for offline viewing.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGuestDownloadDialog = false
                        navController.navigate(Screen.Login.route)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text("Sign In / Register")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuestDownloadDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF222222),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        )
    }

    if (showGuestTmdbDialog) {
        AlertDialog(
            onDismissRequest = { showGuestTmdbDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Sign In Required", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "TMDB metadata corrections and community suggestions require an account. Please sign in to contribute.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGuestTmdbDialog = false
                        navController.navigate(Screen.Login.route)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text("Sign In")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuestTmdbDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF222222),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        )
    }

    if (showMobileDataPrompt) {
        AlertDialog(
            onDismissRequest = { showMobileDataPrompt = false },
            icon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFFFF9800)) },
            title = { Text("Switch to Wi-Fi for Streaming", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "PotFlix content servers are hosted on local broadband Wi-Fi (BDIX). Streaming over mobile data may fail to connect or consume cellular data.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMobileDataPrompt = false
                        com.potflix.util.NetworkUtils.openWifiSettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text("Open Wi-Fi Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showMobileDataPrompt = false
                            pendingStreamUrl?.let { url ->
                                actuallyPlayVideo(url, pendingStreamTitle ?: "")
                            }
                        }
                    ) {
                        Text("Stream Anyway", color = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { showMobileDataPrompt = false }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                    }
                }
            },
            containerColor = Color(0xFF242424),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        )
    }
}

fun cleanMediaTitle(rawTitle: String): String {
    return rawTitle
        .replace(Regex("""(?i)\b(s\d{1,2}(-s\d{1,2})?|season[.\s_-]*\d{1,2}|complete|bluray|web-dl|1080p|720p|4k|2160p|x264|x265|hevc|aac|dts)\b.*"""), "")
        .replace(Regex("""[._\-]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

@Composable
fun TmdbCorrectionDialog(
    initialTitle: String,
    initialType: String,
    onDismiss: () -> Unit,
    onSelectTmdb: (tmdbId: Long, type: String) -> Unit,
    onSearch: (query: String, type: String, callback: (List<com.potflix.data.remote.TmdbMovieDto>) -> Unit) -> Unit
) {
    var searchQuery by remember { mutableStateOf(cleanMediaTitle(initialTitle)) }
    var selectedType by remember { mutableStateOf(if (initialType.equals("tv", ignoreCase = true)) "tv" else "movie") }
    var directTmdbId by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<com.potflix.data.remote.TmdbMovieDto>>(emptyList()) }

    val performSearch = {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            onSearch(searchQuery.trim(), selectedType) { found ->
                results = found
                isSearching = false
            }
        }
    }

    LaunchedEffect(selectedType) {
        performSearch()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .heightIn(max = 620.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF14141E),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PotFlixRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = PotFlixRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Fix TMDB Metadata",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "Search or enter direct TMDB ID",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222230))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                // ─── SEGMENTED PILL TYPE SELECTOR ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C28))
                        .padding(4.dp)
                ) {
                    // TV Series Option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selectedType == "tv") PotFlixRed else Color.Transparent)
                            .clickable { selectedType = "tv" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TV Series 📺",
                            fontWeight = if (selectedType == "tv") FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (selectedType == "tv") Color.White else Color.White.copy(alpha = 0.6f)
                        )
                    }

                    // Movie Option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selectedType == "movie") PotFlixRed else Color.Transparent)
                            .clickable { selectedType = "movie" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Movie 🎬",
                            fontWeight = if (selectedType == "movie") FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (selectedType == "movie") Color.White else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ─── SEARCH INPUT ───
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by title...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PotFlixRed,
                        unfocusedBorderColor = Color(0xFF2B2B3C),
                        focusedContainerColor = Color(0xFF1B1B26),
                        unfocusedContainerColor = Color(0xFF1B1B26),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = PotFlixRed,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { performSearch() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Search",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { performSearch() }
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ─── DIRECT TMDB ID INPUT ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = directTmdbId,
                        onValueChange = { if (it.all { ch -> ch.isDigit() }) directTmdbId = it },
                        placeholder = { Text("Or direct ID (e.g. 63351)", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PotFlixRed,
                            unfocusedBorderColor = Color(0xFF2B2B3C),
                            focusedContainerColor = Color(0xFF1B1B26),
                            unfocusedContainerColor = Color(0xFF1B1B26),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                    Button(
                        onClick = {
                            val id = directTmdbId.toLongOrNull()
                            if (id != null && id > 0) {
                                onSelectTmdb(id, selectedType)
                            }
                        },
                        enabled = directTmdbId.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PotFlixRed),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ─── SEARCH RESULTS LIST / EMPTY / LOADING ───
                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = PotFlixRed,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Searching TMDB...",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else if (results.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No results found on TMDB",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try simplifying keywords or input the direct TMDB ID above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { item ->
                            val itemTitle = item.title ?: item.name ?: "Unknown"
                            val itemDate = item.release_date ?: item.first_air_date
                            val itemYear = itemDate?.take(4)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTmdb(item.id.toLong(), selectedType) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26)),
                                border = BorderStroke(1.dp, Color(0xFF282838))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val posterUrl = item.poster_path?.let { "https://image.tmdb.org/t/p/w200$it" }
                                    AsyncImage(
                                        model = posterUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(width = 46.dp, height = 66.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF222230)),
                                        contentScale = ContentScale.Crop
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = itemTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (itemYear != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF282838))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = itemYear,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(PotFlixRed.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "ID: ${item.id}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PotFlixRed
                                                )
                                            }
                                        }

                                        if (!item.overview.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = item.overview,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.55f),
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = { onSelectTmdb(item.id.toLong(), selectedType) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PotFlixRed),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Match", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
fun DetailCoverHeader(
    movie: com.potflix.domain.model.Movie?,
    isPlayingTrailer: Boolean,
    onPlayTrailer: () -> Unit,
    onCloseTrailer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasBackdrop = !movie?.backdrop.isNullOrBlank() && movie?.backdrop?.contains("placeholder", ignoreCase = true) != true
    val hasPoster = !movie?.poster.isNullOrBlank() && movie?.poster?.contains("placeholder", ignoreCase = true) != true
    val trailerKey = movie?.trailerKey

    Crossfade(
        targetState = isPlayingTrailer && !trailerKey.isNullOrBlank(),
        label = "coverTrailerCrossfade",
        modifier = modifier
    ) { playing ->
        if (playing && !trailerKey.isNullOrBlank()) {
            TrailerPlayer(
                trailerKey = trailerKey,
                onClose = onCloseTrailer,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .aspectRatio(16f / 9f)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(Color(0xFF0D0D11)),
                contentAlignment = Alignment.Center
            ) {
                if (hasBackdrop) {
                    // True 16:9 Backdrop with Natural Proportions
                    AsyncImage(
                        model = movie?.backdrop,
                        contentDescription = movie?.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Smooth Cinematic Gradient Fade
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.45f to Color.Transparent,
                                        0.75f to Color.Black.copy(alpha = 0.5f),
                                        1.0f to MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                } else if (hasPoster) {
                    // Ambient Frosted Blurred Background of the Poster
                    AsyncImage(
                        model = movie?.poster,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0.45f }
                            .blur(24.dp),
                        contentScale = ContentScale.Crop
                    )
                    // Darkening overlay over the blur
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Black.copy(alpha = 0.7f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                    // Properly Framed 2:3 Vertical Poster Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 10.dp,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxHeight(0.86f)
                            .aspectRatio(2f / 3f)
                    ) {
                        AsyncImage(
                            model = movie?.poster,
                            contentDescription = movie?.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    // Fallback placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF141419)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                // Floating "Watch Trailer" Pill Badge
                TrailerBadgeButton(
                    onClick = onPlayTrailer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun TrailerPlayer(
    trailerKey: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var hasEmbedError by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val openYouTube = {
        try {
            val appIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("vnd.youtube:$trailerKey")
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appIntent)
        } catch (e: Exception) {
            val webIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.youtube.com/watch?v=$trailerKey")
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    DisposableEffect(trailerKey) {
        onDispose {
            try {
                webViewInstance?.stopLoading()
                webViewInstance?.loadUrl("about:blank")
                webViewInstance?.destroy()
            } catch (e: Exception) {
                android.util.Log.e("TrailerPlayer", "Error destroying WebView", e)
            }
            webViewInstance = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        if (!hasEmbedError) {
            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            allowContentAccess = true
                            allowFileAccess = true
                            // Strip WebView flag '; wv' to prevent YouTube anti-embed blocks
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        }
                        webChromeClient = android.webkit.WebChromeClient()
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onReceivedError(
                                view: android.webkit.WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    hasEmbedError = true
                                }
                            }
                        }

                        // Register JavaScript bridge for YouTube IFrame player error events
                        addJavascriptInterface(
                            object {
                                @android.webkit.JavascriptInterface
                                fun onPlayerError(errorCode: Int) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        hasEmbedError = true
                                    }
                                }
                            },
                            "AndroidBridge"
                        )

                        val embedHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                <meta name="referrer" content="strict-origin-when-cross-origin">
                                <style>
                                    * { margin: 0; padding: 0; box-sizing: border-box; }
                                    html, body { width: 100%; height: 100%; background: #000000; overflow: hidden; }
                                    iframe { width: 100%; height: 100%; border: 0; }
                                </style>
                            </head>
                            <body>
                                <iframe 
                                    id="player"
                                    type="text/html"
                                    width="100%"
                                    height="100%"
                                    src="https://www.youtube-nocookie.com/embed/$trailerKey?autoplay=1&playsinline=1&enablejsapi=1&rel=0&origin=https://www.youtube-nocookie.com" 
                                    frameborder="0"
                                    referrerpolicy="strict-origin-when-cross-origin"
                                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                    allowfullscreen>
                                </iframe>
                                <script>
                                    var tag = document.createElement('script');
                                    tag.src = "https://www.youtube.com/iframe_api";
                                    var firstScriptTag = document.getElementsByTagName('script')[0];
                                    firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                                    function onYouTubeIframeAPIReady() {
                                        new YT.Player('player', {
                                            events: {
                                                'onError': function(event) {
                                                    // 101/150/152 = Embed restricted by studio
                                                    if (window.AndroidBridge) {
                                                        window.AndroidBridge.onPlayerError(event.data);
                                                    }
                                                }
                                            }
                                        });
                                    }
                                </script>
                            </body>
                            </html>
                        """.trimIndent()

                        loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "UTF-8", null)
                        webViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Elegant studio-restriction fallback UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF141414))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = PotFlixRed,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Trailer Playback Restricted",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "The studio does not permit embedded playback in third-party apps.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = openYouTube,
                    colors = ButtonDefaults.buttonColors(containerColor = PotFlixRed),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Watch on YouTube",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Top Controls: "Open in YouTube" button & Close button
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = openYouTube,
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Open in YouTube",
                        tint = PotFlixRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "YouTube",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Close button overlay to exit trailer
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.75f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Trailer",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TrailerBadgeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (isFocused) PotFlixRed else Color.Black.copy(alpha = 0.7f),
        border = BorderStroke(
            1.dp,
            if (isFocused) Color.White else Color.White.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                if (isFocused) {
                    scaleX = 1.08f
                    scaleY = 1.08f
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Watch Trailer",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DetailActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeColor: Color = PotFlixRed,
    contentColor: Color = Color.White
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = when {
            isFocused -> Color.White.copy(alpha = 0.25f)
            isActive -> activeColor.copy(alpha = 0.20f)
            else -> Color.White.copy(alpha = 0.10f)
        },
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = when {
                isFocused -> Color.White
                isActive -> activeColor.copy(alpha = 0.6f)
                else -> Color.White.copy(alpha = 0.18f)
            }
        ),
        modifier = modifier
            .height(44.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                if (isFocused) {
                    scaleX = 1.03f
                    scaleY = 1.03f
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (isActive) activeColor else contentColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
