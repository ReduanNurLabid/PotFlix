package com.potflix.presentation.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.potflix.presentation.common.MovieCard
import com.potflix.presentation.common.SearchGridSkeleton
import com.potflix.presentation.navigation.Screen
import com.potflix.presentation.theme.PotFlixRed

@Composable
fun SearchScreen(
    navController: NavController,
    initialQuery: String? = null,
    viewModel: SearchViewModel = hiltViewModel()
) {
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.onQueryChange(initialQuery)
            viewModel.saveRecentSearch(initialQuery)
        }
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val trendingSearches by viewModel.trendingSearches.collectAsState()
    val suggestedMovies by viewModel.suggestedMovies.collectAsState()
    val selectedTypeFilter by viewModel.selectedTypeFilter.collectAsState()
    val selectedSortFilter by viewModel.selectedSortFilter.collectAsState()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()

    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ─── TOP SEARCH BAR ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onQueryChange(it) },
                    placeholder = {
                        Text(
                            text = "Search movies, TV shows, actors...",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (searchQuery.isNotEmpty()) PotFlixRed else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    viewModel.onQueryChange("")
                                    focusManager.clearFocus()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            viewModel.saveRecentSearch(searchQuery)
                            focusManager.clearFocus()
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF181818),
                        unfocusedContainerColor = Color(0xFF141414),
                        focusedBorderColor = PotFlixRed,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        cursorColor = PotFlixRed
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ─── FILTER CHIPS ROW ───
            val typeFilters = listOf("All", "Movies", "TV Shows", "Animation")
            val sortFilters = listOf("Relevance", "Latest", "Top Rated")

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type filters
                items(typeFilters) { type ->
                    val isSelected = selectedTypeFilter == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setTypeFilter(type) },
                        label = { Text(type, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PotFlixRed,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.07f),
                            labelColor = Color.White.copy(alpha = 0.75f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color.White.copy(alpha = 0.15f),
                            selectedBorderColor = PotFlixRed,
                            borderWidth = 1.dp
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                }

                item {
                    Spacer(
                        modifier = Modifier
                            .height(20.dp)
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                }

                // Sort filters
                items(sortFilters) { sort ->
                    val isSelected = selectedSortFilter == sort
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSortFilter(sort) },
                        label = { Text(sort, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF333333),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.05f),
                            labelColor = Color.White.copy(alpha = 0.65f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color.White.copy(alpha = 0.15f),
                            selectedBorderColor = Color.White.copy(alpha = 0.4f),
                            borderWidth = 1.dp
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                }

                // Dynamic Categories if available
                if (availableCategories.isNotEmpty()) {
                    item {
                        Spacer(
                            modifier = Modifier
                                .height(20.dp)
                                .width(1.dp)
                                .background(Color.White.copy(alpha = 0.2f))
                        )
                    }

                    items(availableCategories.take(8)) { cat ->
                        val isSelected = selectedCategoryFilter == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.setCategoryFilter(if (isSelected) "All" else cat)
                            },
                            label = { Text(cat, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PotFlixRed.copy(alpha = 0.7f),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.05f),
                                labelColor = Color.White.copy(alpha = 0.65f)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color.White.copy(alpha = 0.15f),
                                selectedBorderColor = PotFlixRed,
                                borderWidth = 1.dp
                            ),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ─── MAIN CONTENT AREA ───
            if (searchQuery.isBlank()) {
                // ─── EMPTY STATE: RECENT SEARCHES, TRENDING & EXPLORE GRID ───
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Recent Searches
                    if (recentSearches.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recent Searches",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Clear All",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { viewModel.clearAllRecentSearches() }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(recentSearches) { recent ->
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color(0xFF1E1E1E),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                            onClick = {
                                                viewModel.onQueryChange(recent)
                                                viewModel.saveRecentSearch(recent)
                                            }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = null,
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = recent,
                                                    color = Color.White,
                                                    fontSize = 12.sp
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = { viewModel.removeRecentSearch(recent) },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Trending Movies & Series
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = PotFlixRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Trending Movies & Series",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(trendingSearches) { title ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.White.copy(alpha = 0.07f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                        onClick = {
                                            viewModel.saveRecentSearch(title)
                                            viewModel.onQueryChange(title)
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = title,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Explore Suggestions Header
                    if (suggestedMovies.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            Text(
                                text = "Explore Popular Titles",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }

                        items(suggestedMovies) { movie ->
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
            } else {
                // ─── ACTIVE SEARCH RESULTS ───
                if (isLoading) {
                    SearchGridSkeleton()
                } else if (searchResults.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No results found for \"$searchQuery\"",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try checking for typos or searching by another category",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.resetFilters()
                                viewModel.onQueryChange("")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PotFlixRed),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Clear Filters & Search", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            Text(
                                text = "Found ${searchResults.size} title${if (searchResults.size > 1) "s" else ""}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        items(searchResults) { movie ->
                            MovieCard(
                                movie = movie,
                                onClick = {
                                    viewModel.saveRecentSearch(searchQuery)
                                    navController.navigate(Screen.Detail.createRoute(movie))
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
