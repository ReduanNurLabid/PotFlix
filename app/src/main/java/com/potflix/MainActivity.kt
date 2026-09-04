package com.potflix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.potflix.presentation.navigation.BottomNavBar
import com.potflix.presentation.navigation.PotFlixNavGraph
import com.potflix.presentation.theme.PotFlixTheme
import dagger.hilt.android.AndroidEntryPoint

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.potflix.worker.DailyNotificationScheduler

import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var serverPreferences: com.potflix.data.local.preferences.ServerPreferences
    
    @Inject
    lateinit var firebaseSyncManager: com.potflix.data.remote.FirebaseSyncManager

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && serverPreferences.isDailyNotificationEnabled()) {
            DailyNotificationScheduler.scheduleNextDailyNotification(this)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase Anonymous Auth
        lifecycleScope.launch {
            firebaseSyncManager.initAuth()
        }

        // Request POST_NOTIFICATIONS on Android 13+ and ensure daily recommendations are scheduled
        checkNotificationPermissionAndSchedule()
        
        setContent {
            PotFlixTheme {
                val navController = rememberNavController()
                val isTv = remember { com.potflix.util.TvUtils.isTelevision(this@MainActivity) }
                val startDest = if (serverPreferences.isOnboardingCompleted()) com.potflix.presentation.navigation.Screen.Home.route else com.potflix.presentation.navigation.Screen.Onboarding.route
                
                if (isTv) {
                    // TV Layout: Side Rail + Content
                    Row(modifier = Modifier.fillMaxSize()) {
                        com.potflix.presentation.navigation.TvSideNavRail(navController = navController)
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            PotFlixNavGraph(
                                navController = navController,
                                startDestination = startDest
                            )
                        }
                    }
                } else {
                    // Mobile Layout: Bottom Nav Bar
                    Scaffold(
                        bottomBar = { BottomNavBar(navController = navController) }
                    ) { innerPadding ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = innerPadding.calculateBottomPadding()),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            PotFlixNavGraph(
                                navController = navController,
                                startDestination = startDest
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkNotificationPermissionAndSchedule() {
        if (!serverPreferences.isDailyNotificationEnabled()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                DailyNotificationScheduler.scheduleNextDailyNotification(this)
            }
        } else {
            DailyNotificationScheduler.scheduleNextDailyNotification(this)
        }
    }
}
