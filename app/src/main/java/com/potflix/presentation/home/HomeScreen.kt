package com.potflix.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.potflix.R
import com.potflix.domain.model.Category
import com.potflix.domain.model.Movie
import com.potflix.presentation.home.components.CategoryRow
import com.potflix.presentation.home.components.HeroBanner
import com.potflix.presentation.home.components.Top10Row
import com.potflix.presentation.navigation.Screen
import com.potflix.presentation.common.shimmerEffect
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val trendingMovies by viewModel.trendingMovies.collectAsState()
    val categoryMovies by viewModel.categoryMovies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val isHeroInWatchlist by viewModel.isHeroInWatchlist.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState(initial = emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current
    var movieToDeleteFromHistory by remember { mutableStateOf<Movie?>(null) }

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
        }
    }
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            pullToRefreshState.endRefresh()
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearToastMessage()
        }
    }

    var selectedFilter by remember { mutableStateOf("All") }
    val filterTabs = listOf("All", "TV Shows", "Movies", "Categories")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        if (isLoading && categories.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 90.dp)
            ) {
                item {
                    // Hero Banner Shimmer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(450.dp)
                            .shimmerEffect()
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    // Top 10 Shimmer
                    Box(modifier = Modifier.padding(16.dp).width(120.dp).height(20.dp).shimmerEffect())
                    LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(5) {
                            Box(modifier = Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                        }
                    }
                }
                items(3) {
                    Box(modifier = Modifier.padding(16.dp).width(150.dp).height(20.dp).shimmerEffect())
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(4) {
                            Box(modifier = Modifier.width(100.dp).height(150.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 90.dp)
            ) {
                // Hero Banner
                if (trendingMovies.isNotEmpty()) {
                    item {
                        val heroMovie = trendingMovies.first()
                        HeroBanner(
                            movie = heroMovie,
                            onPlayClick = {
                                navController.navigate(Screen.Detail.createRoute(heroMovie))
                            },
                            onInfoClick = {
                                navController.navigate(Screen.Detail.createRoute(heroMovie))
                            },
                            onMyListClick = {
                                viewModel.toggleHeroWatchlist()
                            },
                            isMyList = isHeroInWatchlist
                        )
                    }
                }
                
                // Continue Watching Row
                if (watchHistory.isNotEmpty()) {
                    item {
                        CategoryRow(
                            category = Category("history", "Continue Watching", "", "", "🕒"),
                            movies = watchHistory,
                            onMovieClick = { movie ->
                                if (movie.lastPlayedStreamUrl != null && !movie.isWatched) {
                                    navController.navigate(Screen.Player.createRoute(movie.url, movie.lastPlayedStreamUrl, movie.title, movie.playbackPosition ?: 0L))
                                } else {
                                    navController.navigate(Screen.Detail.createRoute(movie))
                                }
                            },
                            onMovieLongClick = { movie ->
                                movieToDeleteFromHistory = movie
                            },
                            onSeeAllClick = { } // Hide or do nothing for history see all for now
                        )
                    }
                }

                // Top 10 Ranked Row
                if (trendingMovies.size > 1) {
                    item {
                        Top10Row(
                            title = "Trending Now",
                            movies = trendingMovies.drop(1),
                            onMovieClick = { movie ->
                                navController.navigate(Screen.Detail.createRoute(movie))
                            }
                        )
                    }
                }

                // Filtered Categories or All Categories
                items(
                    items = categories,
                    key = { it.id },
                    contentType = { "categoryRow" }
                ) { category ->
                    val movies = categoryMovies[category.id] ?: emptyList()
                    if (movies.isNotEmpty()) {
                        CategoryRow(
                            category = category,
                            movies = movies,
                            onMovieClick = { movie ->
                                navController.navigate(Screen.Detail.createRoute(movie))
                            },
                            onSeeAllClick = {
                                navController.navigate(Screen.CategoryDetail.createRoute(category.id, category.name))
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(90.dp)) // Extra padding for bottom bar
                }
            }
        }

        // Floating Glassmorphism Header Bar (Overlay on top)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(bottom = 4.dp)
        ) {
            // Logo + Actions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand Logo + Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_potflix_logo_vector),
                        contentDescription = "PotFlix Logo",
                        modifier = Modifier.height(40.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color.White)) {
                                append("POT")
                            }
                            withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color(0xFFE50914))) {
                                append("FLIX")
                            }
                        },
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Search Shortcut
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }


            
            if (isSyncing) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = syncProgress,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (pullToRefreshState.verticalOffset > 0f || pullToRefreshState.isRefreshing) {
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp),
                containerColor = Color(0xFF1E1E1E),
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (movieToDeleteFromHistory != null) {
        val targetMovie = movieToDeleteFromHistory!!
        AlertDialog(
            onDismissRequest = { movieToDeleteFromHistory = null },
            title = { Text("Remove from Continue Watching?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { 
                Text(
                    "Are you sure you want to remove \"${targetMovie.title}\" from your continue watching list?",
                    color = Color.White.copy(alpha = 0.8f)
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeFromWatchHistory(targetMovie)
                        android.widget.Toast.makeText(context, "Removed from Continue Watching", android.widget.Toast.LENGTH_SHORT).show()
                        movieToDeleteFromHistory = null
                    }
                ) {
                    Text("Remove", color = Color(0xFFE50914), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { movieToDeleteFromHistory = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1E1E24)
        )
    }
}
