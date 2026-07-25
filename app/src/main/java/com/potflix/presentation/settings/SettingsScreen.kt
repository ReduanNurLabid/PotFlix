package com.potflix.presentation.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Done
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

    var versionClickCount by remember { mutableIntStateOf(0) }
    var isDeveloperMode by remember { mutableStateOf(false) }
    
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
            
            if (isDeveloperMode) {
                item {
                    SettingsClickableItem(
                        icon = androidx.compose.material.icons.Icons.Default.Done,
                        title = "Export Pre-Packaged Database",
                        subtitle = "Saves potflix_db to your phone's Downloads folder",
                        onClick = { viewModel.exportDatabase() }
                    )
                }
            }
            
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
                    onClick = {
                        if (!isDeveloperMode) {
                            versionClickCount++
                            if (versionClickCount >= 3) {
                                isDeveloperMode = true
                                Toast.makeText(context, "Developer mode enabled!", Toast.LENGTH_SHORT).show()
                            } else {
                                val remaining = 3 - versionClickCount
                                Toast.makeText(context, "Tap $remaining more times to unlock Developer options", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "You are already a developer", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    val updateAvailable by viewModel.updateAvailable.collectAsState()
    
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
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
    
    updateAvailable?.let { update ->
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
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
                        uriHandler.openUri(update.apkUrl)
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
            containerColor = MaterialTheme.colorScheme.surface
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
            .background(Color.White.copy(alpha = 0.05f), shape = MaterialTheme.shapes.medium)
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
            .background(Color.White.copy(alpha = 0.05f), shape = MaterialTheme.shapes.medium)
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
