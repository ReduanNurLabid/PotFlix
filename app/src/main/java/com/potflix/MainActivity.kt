package com.potflix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.potflix.presentation.navigation.BottomNavBar
import com.potflix.presentation.navigation.PotFlixNavGraph
import com.potflix.presentation.theme.PotFlixTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PotFlixTheme {
                val navController = rememberNavController()
                
                Scaffold(
                    bottomBar = { BottomNavBar(navController = navController) }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PotFlixNavGraph(navController = navController)
                    }
                }
            }
        }
    }
}
