package com.potflix.presentation.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

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
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSectionTitle("Account")
            }
            
            item {
                val email by viewModel.currentUserEmail.collectAsState(initial = null)
                
                if (email != null) {
                    SettingsClickableItem(
                        icon = androidx.compose.material.icons.Icons.Default.Info,
                        title = "Logged in as",
                        subtitle = email!!,
                        onClick = {}
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsClickableItem(
                        icon = androidx.compose.material.icons.Icons.Default.Delete,
                        title = "Log Out",
                        subtitle = "Sign out of your account",
                        onClick = { viewModel.logout() }
                    )
                } else {
                    SettingsClickableItem(
                        icon = androidx.compose.material.icons.Icons.Default.Info,
                        title = "Login / Create Account",
                        subtitle = "Sync your watchlist across devices",
                        onClick = { navController.navigate(com.potflix.presentation.navigation.Screen.Login.route) }
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionTitle("Server Configuration")
            }
            
            item {
                SettingsClickableItem(
                    icon = Icons.Default.Settings,
                    title = "Content Server",
                    subtitle = activeServer.name,
                    onClick = { showServerDialog = true }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionTitle("Storage")
            }
            
            item {
                SettingsClickableItem(
                    icon = Icons.Default.Delete,
                    title = "Clear App Cache",
                    subtitle = "Free up space by clearing temporary files ($cacheSize)",
                    onClick = { showClearCacheDialog = true },
                    isLoading = isClearingCache
                )
            }
            
            item {
                val isSyncing by viewModel.isSyncing.collectAsState()
                val syncProgress by viewModel.syncProgress.collectAsState()
                val lastSyncTime by viewModel.lastSyncTime.collectAsState()
                
                SettingsClickableItem(
                    icon = androidx.compose.material.icons.Icons.Default.Done,
                    title = if (isSyncing) "Syncing Database..." else "Sync Database",
                    subtitle = if (isSyncing) syncProgress else "Last synced: $lastSyncTime",
                    onClick = { if (!isSyncing) viewModel.syncDatabase() },
                    isLoading = isSyncing
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionTitle("Notifications")
            }

            item {
                val dailyNotifEnabled by viewModel.dailyNotificationEnabled.collectAsState()
                SettingsSwitchItem(
                    icon = Icons.Default.Notifications,
                    title = "Daily Recommendations",
                    subtitle = "Get daily movie & TV show recommendations",
                    checked = dailyNotifEnabled,
                    onCheckedChange = { viewModel.setDailyNotificationEnabled(it) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionTitle("Playback & Language")
            }

            item {
                val currentAudioLang by viewModel.preferredAudioLanguage.collectAsState()
                SettingsClickableItem(
                    icon = Icons.Default.Settings,
                    title = "Preferred Audio Language",
                    subtitle = com.potflix.util.LanguageUtils.getAudioLanguageDisplayName(currentAudioLang),
                    onClick = { showAudioLangDialog = true }
                )
            }

            item {
                val currentSubtitleLang by viewModel.preferredSubtitleLanguage.collectAsState()
                SettingsClickableItem(
                    icon = Icons.Default.Info,
                    title = "Preferred Subtitle Language",
                    subtitle = com.potflix.util.LanguageUtils.getSubtitleLanguageDisplayName(currentSubtitleLang),
                    onClick = { showSubtitleLangDialog = true }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionTitle("Metadata & TMDB")
            }

            item {
                val customTmdbKey by viewModel.customTmdbApiKey.collectAsState()
                val subtitle = if (customTmdbKey.isNotBlank()) {
                    val preview = if (customTmdbKey.length > 8) "${customTmdbKey.take(4)}...${customTmdbKey.takeLast(4)}" else "***"
                    "Custom Key Active ($preview)"
                } else {
                    "Using General Default Key"
                }
                SettingsClickableItem(
                    icon = Icons.Default.Settings,
                    title = "TMDB API Key",
                    subtitle = subtitle,
                    onClick = { showTmdbDialog = true }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionTitle("Updates")
            }
            
            item {
                SettingsClickableItem(
                    icon = Icons.Default.Info,
                    title = "Check for Updates",
                    subtitle = "Make sure you have the latest version",
                    onClick = { viewModel.checkForUpdates() }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionTitle("About")
            }
            
            // Removed Export Pre-Packaged Database section
            
            item {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                SettingsClickableItem(
                    icon = Icons.Default.Info,
                    title = "Developer Info",
                    subtitle = "reduan.vercel.app",
                    onClick = {
                        uriHandler.openUri("https://reduan.vercel.app")
                    }
                )
            }
            
            item {
                SettingsClickableItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "PotFlix v${com.potflix.BuildConfig.VERSION_NAME}",
                    onClick = {}
                )
            }
        }
    }

    val updateAvailable by viewModel.updateAvailable.collectAsState()
    
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Select Content Server") },
            text = {
                Column {
                    com.potflix.data.local.preferences.ServerConfig.BUILT_IN_SERVERS.forEach { server ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setActiveServer(server)
                                    showServerDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = activeServer.id == server.id,
                                onClick = {
                                    viewModel.setActiveServer(server)
                                    showServerDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(server.name, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(server.baseUrl, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E28)
        )
    }

    if (showAudioLangDialog) {
        val currentAudio by viewModel.preferredAudioLanguage.collectAsState()
        AlertDialog(
            onDismissRequest = { showAudioLangDialog = false },
            title = { Text("Preferred Audio Language") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    com.potflix.util.LanguageUtils.AUDIO_LANGUAGES.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPreferredAudioLanguage(lang.code)
                                    showAudioLangDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentAudio == lang.code,
                                onClick = {
                                    viewModel.setPreferredAudioLanguage(lang.code)
                                    showAudioLangDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(lang.displayName, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioLangDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E28)
        )
    }

    if (showSubtitleLangDialog) {
        val currentSub by viewModel.preferredSubtitleLanguage.collectAsState()
        AlertDialog(
            onDismissRequest = { showSubtitleLangDialog = false },
            title = { Text("Preferred Subtitle Language") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    com.potflix.util.LanguageUtils.SUBTITLE_LANGUAGES.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPreferredSubtitleLanguage(lang.code)
                                    showSubtitleLangDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentSub == lang.code,
                                onClick = {
                                    viewModel.setPreferredSubtitleLanguage(lang.code)
                                    showSubtitleLangDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(lang.displayName, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleLangDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E28)
        )
    }

    val showRestartDialog by viewModel.showRestartDialog.collectAsState()
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestartDialog() },
            title = { Text("Restart Required") },
            text = { Text("You have switched the content server to ${activeServer.name}. Please restart the app for the changes to take full effect and avoid any database conflicts.") },
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Restart Now", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRestartDialog() }) {
                    Text("Later", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E28)
        )
    }
    
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache") },
            text = { Text("Are you sure you want to clear the app cache? This will free up space but images may take slightly longer to load the next time you browse.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearCache()
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E28)
        )
    }
    
    updateAvailable?.let { update ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = { Text("Update Available: v${update.versionName}") },
            text = { 
                Column {
                    Text("A new version of PotFlix is available!")
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
                            android.widget.Toast.makeText(context, "Could not open browser to download update", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        viewModel.dismissUpdateDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Download Update", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text("Later", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E28)
        )
    }

    if (showTmdbDialog) {
        val currentCustomKey by viewModel.customTmdbApiKey.collectAsState()
        var inputKey by remember(currentCustomKey) { mutableStateOf(currentCustomKey) }
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

        AlertDialog(
            onDismissRequest = { showTmdbDialog = false },
            title = { Text("TMDB API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "PotFlix uses The Movie Database (TMDB) for posters, summaries, ratings, and cast info. A shared default key is included, but you can enter your personal TMDB API key to avoid rate limits.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    
                    Text(
                        text = "Get a free API key at themoviedb.org ↗",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        trailingIcon = {
                            if (inputKey.isNotEmpty()) {
                                IconButton(onClick = { inputKey = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTmdbDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E28)
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
    )
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161622), shape = MaterialTheme.shapes.medium)
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161622), shape = MaterialTheme.shapes.medium)
            .clickable(onClick = onClick, enabled = !isLoading)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        if (isLoading) {
            Spacer(modifier = Modifier.width(16.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }
}
