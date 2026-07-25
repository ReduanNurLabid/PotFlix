package com.potflix.presentation.watchlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.potflix.presentation.common.MovieCard
import com.potflix.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    navController: NavController,
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val watchlist by viewModel.watchlist.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Watchlist", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (watchlist.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Your watchlist is empty.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(watchlist) { movie ->
                    MovieCard(
                        movie = movie,
                        onClick = { navController.navigate(Screen.Detail.createRoute(movie)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
