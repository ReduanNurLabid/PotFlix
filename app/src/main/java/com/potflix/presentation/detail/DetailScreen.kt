package com.potflix.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
    
    var showEditTmdbDialog by remember { mutableStateOf(false) }
    
    val playVideo = { streamUrl: String, title: String ->
        val pos = movie?.playbackPosition ?: 0L
        val movieUrl = movie?.url ?: streamUrl
        navController.navigate(Screen.Player.createRoute(movieUrl, streamUrl, title, pos))
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
                Box(modifier = Modifier.height(300.dp)) {
                    AsyncImage(
                        model = movie?.backdrop ?: movie?.poster ?: "https://via.placeholder.com/1280x720",
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background,
                                        Color.Black.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.background
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY
                                )
                            )
                    )
                }
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
                        val playBtnText = if (isResume) "Resume Movie" else "Play Movie"

                        Button(
                            onClick = {
                                movie?.let {
                                    viewModel.onPlayStarted()
                                    playVideo(it.url, it.title)
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

                    // ─── SECONDARY ACTIONS ROW (Download, My List, Fix TMDB) ───
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Download Button (for Movies)
                        if (isMovieVideoUrl && !isTvSeries) {
                            val download = downloads.find { it.streamUrl == movie?.url }
                            var downloadBtnFocused by remember { mutableStateOf(false) }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = if (downloadBtnFocused) 0.3f else 0.15f))
                                    .onFocusChanged { downloadBtnFocused = it.isFocused }
                                    .then(if (downloadBtnFocused) Modifier.border(2.dp, Color.White, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) else Modifier)
                                    .clickable {
                                        if (download == null || download.status == com.potflix.service.DownloadService.STATUS_FAILED) {
                                            movie?.let {
                                                viewModel.startDownload(it.title, it.url, it.poster)
                                            }
                                        } else if (download.status == com.potflix.service.DownloadService.STATUS_SUCCESSFUL) {
                                            val streamUrl = download.localUri
                                            if (streamUrl != null) {
                                                val movieUrl = movie?.url ?: streamUrl
                                                navController.navigate(Screen.Player.createRoute(movieUrl, streamUrl, download.title))
                                            }
                                        } else {
                                            navController.navigate(Screen.Watchlist.route)
                                        }
                                    }
                                    .padding(horizontal = 8.dp)
                            ) {
                                if (download != null && download.status == com.potflix.service.DownloadService.STATUS_SUCCESSFUL) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Offline", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                } else if (download != null && download.status != com.potflix.service.DownloadService.STATUS_FAILED) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${download.progress}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                } else {
                                    Icon(painter = androidx.compose.ui.res.painterResource(id = com.potflix.R.drawable.ic_nav_downloads), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        // 2. My List Button
                        var myListFocused by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(if (inWatchlist) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = if (myListFocused) 0.3f else 0.15f))
                                .onFocusChanged { myListFocused = it.isFocused }
                                .then(if (myListFocused) Modifier.border(2.dp, Color.White, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { viewModel.toggleWatchlist() }
                                .padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (inWatchlist) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                tint = if (inWatchlist) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "My List", 
                                color = if (inWatchlist) MaterialTheme.colorScheme.primary else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // 3. Fix TMDB Button
                        var fixTmdbFocused by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = if (fixTmdbFocused) 0.3f else 0.15f))
                                .onFocusChanged { fixTmdbFocused = it.isFocused }
                                .then(if (fixTmdbFocused) Modifier.border(2.dp, Color.White, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { showEditTmdbDialog = true }
                                .padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Fix TMDB",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Fix TMDB",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
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
                                        .background(Color(0xFF222222), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
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
                        Spacer(modifier = Modifier.height(16.dp))
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
                                        onClick = { showEditTmdbDialog = true },
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
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fix TMDB Metadata", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == "tv",
                        onClick = { selectedType = "tv" },
                        label = { Text("TV Series") },
                        leadingIcon = if (selectedType == "tv") {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = selectedType == "movie",
                        onClick = { selectedType = "movie" },
                        label = { Text("Movie") },
                        leadingIcon = if (selectedType == "movie") {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search query input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Title or Search Query") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { performSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
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

                // Direct TMDB ID input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = directTmdbId,
                        onValueChange = { if (it.all { ch -> ch.isDigit() }) directTmdbId = it },
                        label = { Text("Or TMDB ID") },
                        placeholder = { Text("e.g. 63351") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
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
                        enabled = directTmdbId.isNotBlank()
                    ) {
                        Text("Apply")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Results list or searching indicator
                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                    }
                } else if (results.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No results found. Try editing the search query above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
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
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val posterUrl = item.poster_path?.let { "https://image.tmdb.org/t/p/w200$it" }
                                    AsyncImage(
                                        model = posterUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(width = 45.dp, height = 65.dp)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                            .background(Color.DarkGray),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = itemTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (itemYear != null) {
                                                Text(itemYear, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            }
                                            Text("•", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            Text("ID: ${item.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        if (!item.overview.isNullOrBlank()) {
                                            Text(
                                                text = item.overview,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.7f),
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    FilledTonalButton(
                                        onClick = { onSelectTmdb(item.id.toLong(), selectedType) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Select", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
