package com.potflix.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.scale
import com.potflix.data.local.preferences.ServerConfig
import com.potflix.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val serverStatuses by viewModel.serverStatuses.collectAsState()
    val isChecking by viewModel.isChecking.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val syncPercentage by viewModel.syncPercentage.collectAsState()
    val onboardingComplete by viewModel.onboardingComplete.collectAsState()

    var selectedServer by remember { mutableStateOf<ServerConfig?>(null) }
    var isServerSelectedForSync by remember { mutableStateOf(false) }

    LaunchedEffect(onboardingComplete) {
        if (onboardingComplete) {
            val isUserLoggedIn = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let { !it.isAnonymous } == true
            if (isUserLoggedIn) {
                navController.navigate(Screen.Home.route) {
                    popUpTo("onboarding") { inclusive = true }
                }
            } else {
                navController.navigate(Screen.Login.route) {
                    popUpTo("onboarding") { inclusive = true }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome to PotFlix", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isSyncing || onboardingComplete) {
                Spacer(modifier = Modifier.weight(1f))
                
                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.1f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                
                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = syncPercentage,
                    animationSpec = androidx.compose.animation.core.tween(500),
                    label = "progress"
                )

                Box(
                    modifier = Modifier.size(100.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), shape = androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.potflix.R.drawable.ic_nav_home),
                        contentDescription = "Syncing",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp).scale(scale)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "Setting up your library...",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(0.8f).height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.1f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = syncProgress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Text(
                    text = "Select Content Server",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "We are checking which servers are available on your network. Select a reachable server to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ServerConfig.BUILT_IN_SERVERS) { server ->
                        val status = serverStatuses[server.id]
                        ServerSelectionItem(
                            server = server,
                            status = status,
                            isSelected = selectedServer?.id == server.id,
                            onClick = { 
                                if (status == true) {
                                    selectedServer = server 
                                }
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { viewModel.checkServers() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Re-check Servers", color = Color.White)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { 
                        selectedServer?.let { 
                            viewModel.selectServer(it) 
                            isServerSelectedForSync = true
                        }
                    },
                    enabled = selectedServer != null && !isChecking,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ServerSelectionItem(
    server: ServerConfig,
    status: Boolean?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color(0xFF161622)
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = status == true, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = server.baseUrl,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            when (status) {
                null -> {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                true -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Reachable",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
                false -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Unreachable",
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
@Composable
fun FolderSelectionItem(
    folder: OnboardingViewModel.DiscoveredFolder,
    isFetching: Boolean,
    canFetch: Boolean,
    onFetch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = folder.parentType,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            if (isFetching) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fetching...", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                Button(
                    onClick = onFetch,
                    enabled = canFetch,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Fetch", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
