package com.potflix.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.potflix.presentation.common.MovieCard
import com.potflix.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val recentSearches by viewModel.recentSearches.collectAsState()
    var active by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.onQueryChange(it) },
                onSearch = { 
                    viewModel.saveRecentSearch(it)
                    active = false
                },
                active = active,
                onActiveChange = { active = it },
                placeholder = { Text("Search movies, TV series...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                colors = SearchBarDefaults.colors(
                    containerColor = Color(0xFF1E1E1E),
                    dividerColor = Color(0xFF2E2E2E)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (active) 0.dp else 16.dp, vertical = if (active) 0.dp else 8.dp)
            ) {
                // Dropdown suggestions when typing
                LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
                    items(searchResults) { movie ->
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = Color(0xFF0A0A0A),
                                headlineColor = Color.White,
                                leadingIconColor = Color.Gray
                            ),
                            headlineContent = { Text(movie.title) },
                            leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.saveRecentSearch(movie.title)
                                active = false
                                navController.navigate(Screen.Detail.createRoute(movie))
                            }
                        )
                        HorizontalDivider(color = Color(0xFF1E1E1E))
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            val suggestedMovies by viewModel.suggestedMovies.collectAsState()
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Recent Searches UI
                if (recentSearches.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Recent Searches",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            recentSearches.forEach { recent ->
                                Surface(
                                    onClick = { viewModel.onQueryChange(recent) },
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(recent, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }

                // Suggested Movies UI
                if (suggestedMovies.isNotEmpty()) {
                    item {
                        Text(
                            text = "Suggested for You",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
                        )
                    }
                    
                    // Display as a Grid using items chunked for LazyColumn, or just a LazyRow. 
                    // Let's use a standard grid layout simulation using chunked
                    val chunkedSuggestions = suggestedMovies.chunked(3)
                    items(chunkedSuggestions.size) { index ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunkedSuggestions[index].forEach { movie ->
                                MovieCard(
                                    movie = movie,
                                    onClick = { navController.navigate(Screen.Detail.createRoute(movie)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Fill empty spaces to maintain alignment if less than 3
                            val emptySpaces = 3 - chunkedSuggestions[index].size
                            repeat(emptySpaces) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else if (recentSearches.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Search for your favorite movies and shows", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults) { movie ->
                    MovieCard(
                        movie = movie,
                        onClick = {
                            navController.navigate(Screen.Detail.createRoute(movie))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
