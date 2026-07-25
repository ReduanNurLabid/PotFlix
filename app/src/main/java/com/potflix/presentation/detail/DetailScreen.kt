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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.potflix.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    navController: NavController,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val movie by viewModel.movie.collectAsState()
    val seasons by viewModel.seasons.collectAsState()
    val streamError by viewModel.streamError.collectAsState()
    val selectedSeasonIndex by viewModel.selectedSeasonIndex.collectAsState()
    val lastPlayedEpisodeUrl by viewModel.lastPlayedEpisodeUrl.collectAsState()
    val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val playVideoExternally = { url: String, title: String ->
        val videoIntent = android.content.Intent(android.content.Intent.ACTION_VIEW)
        videoIntent.setDataAndType(android.net.Uri.parse(url), "video/*")
        videoIntent.putExtra("title", title)
        try {
            context.startActivity(videoIntent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "No external video player found. Please install VLC or MX Player.", android.widget.Toast.LENGTH_LONG).show()
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
        LazyColumn(
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            movie?.rating?.let { rating ->
                                if (rating > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
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
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                .background(if (inWatchlist) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f))
                                .clickable { viewModel.toggleWatchlist() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (inWatchlist) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                tint = if (inWatchlist) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "My List", 
                                color = if (inWatchlist) MaterialTheme.colorScheme.primary else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    if (!movie?.genres.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = movie!!.genres!!.joinToString(" • "),
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    
                    if (movie?.runtime != null || !movie?.language.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (movie?.runtime != null && movie!!.runtime!! > 0) {
                                Text(
                                    text = "${movie!!.runtime} min",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (movie?.runtime != null && movie!!.runtime!! > 0 && !movie?.language.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("•", color = Color.Gray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (!movie?.language.isNullOrEmpty()) {
                                Text(
                                    text = movie!!.language!!,
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (!movie?.cast.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Cast: ${movie!!.cast!!.joinToString(", ")}",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = movie?.overview ?: "No overview available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 22.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (seasons.isEmpty()) {
                        val isVideoUrl = movie?.url?.let { 
                            it.endsWith(".mkv", true) || 
                            it.endsWith(".mp4", true) || 
                            it.endsWith(".avi", true) || 
                            it.endsWith(".webm", true) 
                        } == true
                        
                        if (streamError != null) {
                            Text(
                                text = "Failed to load stream: $streamError\nURL: ${movie?.url}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        if (isVideoUrl) {
                            Button(
                                onClick = {
                                    movie?.let {
                                        playVideoExternally(it.url, it.title)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val download = viewModel.downloads.value.find { it.streamUrl == movie?.url }
                            
                            Button(
                                onClick = {
                                    if (download == null) {
                                        movie?.let {
                                            viewModel.startDownload(it.title, it.url, it.poster)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                enabled = download == null || download.status == android.app.DownloadManager.STATUS_FAILED,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (download?.status == android.app.DownloadManager.STATUS_SUCCESSFUL) 
                                        Color(0xFF4CAF50) else Color.White.copy(alpha = 0.15f),
                                    contentColor = Color.White,
                                    disabledContainerColor = Color.White.copy(alpha = 0.05f)
                                )
                            ) {
                                if (download != null) {
                                    if (download.status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Downloaded", fontWeight = FontWeight.SemiBold)
                                    } else if (download.status == android.app.DownloadManager.STATUS_FAILED) {
                                        Icon(painter = androidx.compose.ui.res.painterResource(id = com.potflix.R.drawable.ic_nav_downloads), contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Retry Download", fontWeight = FontWeight.SemiBold)
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Downloading... ${download.progress}%", fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    Icon(painter = androidx.compose.ui.res.painterResource(id = com.potflix.R.drawable.ic_nav_downloads), contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else if (streamError == null) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f))
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
                        ListItem(
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
                            modifier = Modifier.clickable {
                                viewModel.saveLastPlayedEpisode(episode.url)
                                playVideoExternally(episode.url, episode.title)
                            }
                        )
                    }
                }
            }
        }
    }
}
