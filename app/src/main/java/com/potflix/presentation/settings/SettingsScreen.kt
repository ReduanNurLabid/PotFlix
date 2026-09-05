package com.potflix.presentation.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.potflix.BuildConfig
import com.potflix.R
import com.potflix.data.local.preferences.ServerConfig
import com.potflix.presentation.navigation.Screen
import com.potflix.presentation.theme.PotFlixRed
import com.potflix.util.LanguageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val cacheSize by viewModel.cacheSize.collectAsState()
    val isClearingCache by viewModel.isClearingCache.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var showClearCacheDialog by remember { mutableStateOf(false) }
    val activeServer by viewModel.activeServer.collectAsState()
    var showServerDialog by remember { mutableStateOf(false) }
    var showAudioLangDialog by remember { mutableStateOf(false) }
    var showSubtitleLangDialog by remember { mutableStateOf(false) }
    var showTmdbDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings", 
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        letterSpacing = (-0.5).sp
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0A0A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0A0A0A)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ─── APP BRANDING HERO BANNER ───
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
                    border = BorderStroke(1.dp, Color(0xFF22222E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFE50914).copy(alpha = 0.12f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_potflix_logo_vector),
                                        contentDescription = "PotFlix Logo",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "PotFlix",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME} • Streaming Engine",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.55f)
                                    )
                                }
                            }

                            // Status Pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ready",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            }

            // ─── ACCOUNT SECTION ───
            item {
                val email by viewModel.currentUserEmail.collectAsState(initial = null)
                
                Column {
                    SettingsSectionHeader("Account & Sync")
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
                        border = BorderStroke(1.dp, Color(0xFF22222E))
                    ) {
                        if (email != null) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(
                                                brush = Brush.linearGradient(
                                                    listOf(PotFlixRed, Color(0xFFB80710))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = email!!.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = email!!,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDone,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Cloud Sync Active",
                                                fontSize = 12.sp,
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = { viewModel.logout() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                                    ) {
                                        Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { navController.navigate(Screen.Login.route) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22222E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Sign in to PotFlix",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Sync watchlist & history across devices",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.55f)
                                    )
                                }
                                Button(
                                    onClick = { navController.navigate(Screen.Login.route) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PotFlixRed),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ─── PLAYBACK & STREAMING GROUP ───
            item {
                val currentAudioLang by viewModel.preferredAudioLanguage.collectAsState()
                val currentSubtitleLang by viewModel.preferredSubtitleLanguage.collectAsState()

                SettingsCardGroup(title = "Streaming & Playback") {
                    ModernSettingsItem(
                        icon = Icons.Default.Dns,
                        iconTint = Color(0xFFA855F7), // Purple
                        title = "Content Server",
                        subtitle = activeServer.name,
                        trailingValue = activeServer.name,
                        onClick = { showServerDialog = true },
                        showDivider = true
                    )

                    ModernSettingsItem(
                        icon = Icons.Default.Audiotrack,
                        iconTint = Color(0xFF3B82F6), // Blue
                        title = "Preferred Audio",
                        subtitle = LanguageUtils.getAudioLanguageDisplayName(currentAudioLang),
                        trailingValue = LanguageUtils.getAudioLanguageDisplayName(currentAudioLang),
                        onClick = { showAudioLangDialog = true },
                        showDivider = true
                    )

                    ModernSettingsItem(
                        icon = Icons.Default.Subtitles,
                        iconTint = Color(0xFF06B6D4), // Cyan
                        title = "Preferred Subtitles",
                        subtitle = LanguageUtils.getSubtitleLanguageDisplayName(currentSubtitleLang),
                        trailingValue = LanguageUtils.getSubtitleLanguageDisplayName(currentSubtitleLang),
                        onClick = { showSubtitleLangDialog = true },
                        showDivider = false
                    )
                }
            }

            // ─── STORAGE & DATABASE GROUP ───
            item {
                val isSyncing by viewModel.isSyncing.collectAsState()
                val syncProgress by viewModel.syncProgress.collectAsState()
                val lastSyncTime by viewModel.lastSyncTime.collectAsState()

                SettingsCardGroup(title = "Storage & Database") {
                    ModernSettingsItem(
                        icon = Icons.Default.CleaningServices,
                        iconTint = Color(0xFFF97316), // Orange
                        title = "Clear Cache",
                        subtitle = "Temporary streaming & poster cache ($cacheSize)",
                        trailingValue = cacheSize,
                        isLoading = isClearingCache,
                        onClick = { showClearCacheDialog = true },
                        showDivider = true
                    )

                    ModernSettingsItem(
                        icon = Icons.Default.Sync,
                        iconTint = Color(0xFF10B981), // Emerald
                        title = if (isSyncing) "Syncing Database..." else "Sync Database",
                        subtitle = if (isSyncing) syncProgress else "Last: $lastSyncTime",
                        isLoading = isSyncing,
                        onClick = { if (!isSyncing) viewModel.syncDatabase() },
                        showDivider = false
                    )
                }
            }

            // ─── PREFERENCES & METADATA GROUP ───
            item {
                val dailyNotifEnabled by viewModel.dailyNotificationEnabled.collectAsState()
                val customTmdbKey by viewModel.customTmdbApiKey.collectAsState()
                val tmdbStatus = if (customTmdbKey.isNotBlank()) "Personal Key" else "Default Key"

                SettingsCardGroup(title = "Preferences & Metadata") {
                    ModernSettingsSwitchItem(
                        icon = Icons.Default.NotificationsActive,
                        iconTint = Color(0xFFEC4899), // Pink
                        title = "Daily Recommendations",
                        subtitle = "Receive daily movie & series suggestions",
                        checked = dailyNotifEnabled,
                        onCheckedChange = { viewModel.setDailyNotificationEnabled(it) },
                        showDivider = true
                    )

                    ModernSettingsItem(
                        icon = Icons.Default.Key,
                        iconTint = Color(0xFFF59E0B), // Amber
                        title = "TMDB API Key",
                        subtitle = "Personal key avoids shared rate limits",
                        trailingValue = tmdbStatus,
                        onClick = { showTmdbDialog = true },
                        showDivider = false
                    )
                }
            }

            // ─── COMMUNITY & SUPPORT GROUP ───
            item {
                SettingsCardGroup(title = "Community & Support") {
                    // Telegram Community Item
                    ModernSettingsItem(
                        icon = Icons.AutoMirrored.Filled.Send,
                        iconTint = Color(0xFF29B6F6), // Telegram Blue
                        title = "Telegram Community",
                        subtitle = "Requests, feedback & announcements • @PotFlixx",
                        trailingBadge = "Join ↗",
                        badgeColor = Color(0xFF29B6F6),
                        onClick = {
                            uriHandler.openUri("https://t.me/PotFlixx")
                        },
                        showDivider = true
                    )

                    // Developer Info
                    ModernSettingsItem(
                        icon = Icons.Default.Code,
                        iconTint = Color(0xFF8B5CF6), // Violet
                        title = "Developer Portfolio",
                        subtitle = "reduan.vercel.app ↗",
                        onClick = {
                            uriHandler.openUri("https://reduan.vercel.app")
                        },
                        showDivider = true
                    )

                    // Check for Updates
                    ModernSettingsItem(
                        icon = Icons.Default.SystemUpdate,
                        iconTint = Color(0xFF14B8A6), // Teal
                        title = "Check for Updates",
                        subtitle = "Make sure you're running the newest build",
                        onClick = { viewModel.checkForUpdates() },
                        showDivider = true
                    )

                    // Version Info
                    ModernSettingsItem(
                        icon = Icons.Default.Info,
                        iconTint = Color.White.copy(alpha = 0.5f),
                        title = "Version",
                        subtitle = "PotFlix build ${BuildConfig.VERSION_NAME}",
                        trailingValue = "v${BuildConfig.VERSION_NAME}",
                        onClick = {},
                        showDivider = false
                    )
                }
            }
        }
    }

    // ─── DIALOGS ───

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF161622),
            title = {
                Text("Select Content Server", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ServerConfig.BUILT_IN_SERVERS.forEach { server ->
                        val isSelected = activeServer.id == server.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) PotFlixRed.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    viewModel.setActiveServer(server)
                                    showServerDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setActiveServer(server)
                                    showServerDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = PotFlixRed,
                                    unselectedColor = Color.White.copy(alpha = 0.4f)
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(server.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                Text(server.baseUrl, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Close", color = Color.White.copy(alpha = 0.8f))
                }
            }
        )
    }

    if (showAudioLangDialog) {
        val currentAudio by viewModel.preferredAudioLanguage.collectAsState()
        AlertDialog(
            onDismissRequest = { showAudioLangDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF161622),
            title = { Text("Preferred Audio Language", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LanguageUtils.AUDIO_LANGUAGES.forEach { lang ->
                        val isSelected = currentAudio == lang.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) PotFlixRed.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    viewModel.setPreferredAudioLanguage(lang.code)
                                    showAudioLangDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setPreferredAudioLanguage(lang.code)
                                    showAudioLangDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = PotFlixRed,
                                    unselectedColor = Color.White.copy(alpha = 0.4f)
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(lang.displayName, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioLangDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.8f))
                }
            }
        )
    }

    if (showSubtitleLangDialog) {
        val currentSub by viewModel.preferredSubtitleLanguage.collectAsState()
        AlertDialog(
            onDismissRequest = { showSubtitleLangDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF161622),
            title = { Text("Preferred Subtitle Language", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LanguageUtils.SUBTITLE_LANGUAGES.forEach { lang ->
                        val isSelected = currentSub == lang.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) PotFlixRed.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    viewModel.setPreferredSubtitleLanguage(lang.code)
                                    showSubtitleLangDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setPreferredSubtitleLanguage(lang.code)
                                    showSubtitleLangDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = PotFlixRed,
                                    unselectedColor = Color.White.copy(alpha = 0.4f)
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(lang.displayName, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleLangDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.8f))
                }
            }
        )
    }

    val showRestartDialog by viewModel.showRestartDialog.collectAsState()
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestartDialog() },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF161622),
            title = { Text("Restart Required", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("You have switched the content server to ${activeServer.name}. Please restart the app for changes to take full effect.", color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                val activity = LocalContext.current as? android.app.Activity
                Button(
                    onClick = {
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        if (intent != null) {
                            context.startActivity(intent)
                        }
                        activity?.finish()
                        Runtime.getRuntime().exit(0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PotFlixRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Restart Now", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRestartDialog() }) {
                    Text("Later", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
    
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF161622),
            title = { Text("Clear App Cache", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Are you sure you want to clear temporary cached images and streams? ($cacheSize will be freed).", color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearCache()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PotFlixRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
    
    val updateAvailable by viewModel.updateAvailable.collectAsState()
    updateAvailable?.let { update ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF161622),
            title = { Text("Update Available: v${update.versionName}", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { 
                Column {
                    Text("A new version of PotFlix is ready for download!", color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(update.releaseNotes, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val downloadUrl = update.getDownloadUrlForDevice()
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser for download", Toast.LENGTH_SHORT).show()
                        }
                        viewModel.dismissUpdateDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PotFlixRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Download Update", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text("Later", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    if (showTmdbDialog) {
        val currentCustomKey by viewModel.customTmdbApiKey.collectAsState()
        var inputKey by remember(currentCustomKey) { mutableStateOf(currentCustomKey) }

        AlertDialog(
            onDismissRequest = { showTmdbDialog = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF161622),
            title = { Text("TMDB API Key", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "PotFlix uses The Movie Database (TMDB) for posters, ratings, and synopsis. Enter your personal TMDB API key to avoid shared rate limits.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    
                    Text(
                        text = "Get a free API key at themoviedb.org ↗",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = PotFlixRed,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier
                            .clickable {
                                uriHandler.openUri("https://www.themoviedb.org/settings/api")
                            }
                            .padding(vertical = 4.dp)
                    )

                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        label = { Text("TMDB API Key") },
                        placeholder = { Text("v3 API key or v4 Bearer Token") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PotFlixRed,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedLabelColor = PotFlixRed,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        trailingIcon = {
                            if (inputKey.isNotEmpty()) {
                                IconButton(onClick = { inputKey = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    )

                    if (currentCustomKey.isNotBlank()) {
                        TextButton(
                            onClick = {
                                viewModel.setCustomTmdbApiKey("")
                                showTmdbDialog = false
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Reset to General Default Key", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setCustomTmdbApiKey(inputKey)
                        showTmdbDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PotFlixRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Key", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTmdbDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

// ─── HELPER COMPOSABLES ───

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = Color.White.copy(alpha = 0.5f),
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCardGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        SettingsSectionHeader(title)
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
            border = BorderStroke(1.dp, Color(0xFF22222E))
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ModernSettingsItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailingValue: String? = null,
    trailingBadge: String? = null,
    badgeColor: Color = PotFlixRed,
    isLoading: Boolean = false,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isLoading, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored Squircle Icon Badge
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title + Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Trailing Accessory
            if (isLoading) {
                CircularProgressIndicator(
                    color = PotFlixRed,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else if (trailingBadge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeColor.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = trailingBadge,
                        color = badgeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (trailingValue != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = trailingValue,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                color = Color(0xFF1E1E28),
                thickness = 0.6.dp,
                modifier = Modifier.padding(start = 68.dp)
            )
        }
    }
}

@Composable
private fun ModernSettingsSwitchItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored Squircle Icon Badge
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title + Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PotFlixRed,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    uncheckedTrackColor = Color(0xFF282834)
                )
            )
        }

        if (showDivider) {
            HorizontalDivider(
                color = Color(0xFF1E1E28),
                thickness = 0.6.dp,
                modifier = Modifier.padding(start = 68.dp)
            )
        }
    }
}
