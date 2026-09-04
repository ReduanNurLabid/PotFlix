package com.potflix.presentation.downloads

import android.app.DownloadManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.potflix.data.local.entity.LocalDownloadEntity
import com.potflix.presentation.navigation.Screen
import com.potflix.service.DownloadService
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    navController: NavController,
    viewModel: DownloadsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No downloads yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(downloads) { download ->
                    DownloadItemCard(download, navController, viewModel)
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(download: LocalDownloadEntity, navController: NavController, viewModel: DownloadsViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            if (download.status == DownloadService.STATUS_SUCCESSFUL && download.localUri != null) {
                navController.navigate(Screen.Player.createRoute(download.localUri, download.localUri, download.title))
            }
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            AsyncImage(
                model = download.poster ?: "https://via.placeholder.com/150",
                contentDescription = null,
                modifier = Modifier.width(70.dp).fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            
            Column(
                modifier = Modifier.weight(1f).padding(12.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                when (download.status) {
                    DownloadService.STATUS_SUCCESSFUL -> {
                        Text("Completed", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    DownloadService.STATUS_FAILED -> {
                        Text("Failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    DownloadService.STATUS_PAUSED -> {
                        LinearProgressIndicator(
                            progress = { download.progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Paused - ${download.progress}%", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    else -> {
                        LinearProgressIndicator(
                            progress = { download.progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val speedMb = download.speedBytesPerSecond / (1024f * 1024f)
                        val speedText = String.format("%.1f MB/s", speedMb)
                        val etaText = if (download.etaSeconds > 60) "${download.etaSeconds / 60}m ${download.etaSeconds % 60}s" else "${download.etaSeconds}s"
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${download.progress}%", style = MaterialTheme.typography.bodySmall)
                            Text("$speedText • ETA: $etaText", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }
            
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (download.status == DownloadService.STATUS_SUCCESSFUL) {
                    IconButton(onClick = { 
                        if (download.localUri != null) {
                            navController.navigate(Screen.Player.createRoute(download.localUri, download.localUri, download.title))
                        }
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                    }
                } else if (download.status == DownloadService.STATUS_RUNNING) {
                    IconButton(onClick = { viewModel.pauseDownload(download.downloadId) }) {
                        Icon(painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_media_pause), contentDescription = "Pause")
                    }
                } else if (download.status == DownloadService.STATUS_PAUSED || download.status == DownloadService.STATUS_FAILED) {
                    IconButton(onClick = { viewModel.resumeDownload(download) }) {
                        Icon(painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_media_play), contentDescription = "Resume")
                    }
                }
                
                IconButton(onClick = { viewModel.deleteDownload(download.downloadId) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
