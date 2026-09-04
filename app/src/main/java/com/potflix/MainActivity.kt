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

import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var serverPreferences: com.potflix.data.local.preferences.ServerPreferences
    
    @Inject
    lateinit var firebaseSyncManager: com.potflix.data.remote.FirebaseSyncManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase Anonymous Auth
        lifecycleScope.launch {
            firebaseSyncManager.initAuth()
        }
        
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
}
