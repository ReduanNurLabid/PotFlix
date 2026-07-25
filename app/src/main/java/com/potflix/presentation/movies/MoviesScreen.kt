package com.potflix.presentation.movies

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.potflix.R
import com.potflix.presentation.home.components.CategoryRow
import com.potflix.presentation.home.components.HeroBanner
import com.potflix.presentation.home.components.Top10Row
import com.potflix.presentation.navigation.Screen

@Composable
fun MoviesScreen(
    navController: NavController,
    viewModel: MoviesViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val trendingMovies by viewModel.trendingMovies.collectAsState()
    val categoryMovies by viewModel.categoryMovies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isHeroInWatchlist by viewModel.isHeroInWatchlist.collectAsState()

    var selectedGenre by remember { mutableStateOf("All") }
    val genreChips = listOf("All", "English", "1080p", "Hindi", "South Indian", "Animation", "IMDb Top 250", "3D")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        if (isLoading && categories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
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

                // Top 10 Movies Row
                if (trendingMovies.size > 1) {
                    item {
                        Top10Row(
                            title = "In Cinemas",
                            movies = trendingMovies.drop(1),
                            onMovieClick = { movie ->
                                navController.navigate(Screen.Detail.createRoute(movie))
                            }
                        )
                    }
                }

                // Movie Category Rows
                items(categories) { category ->
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

        // Translucent Header Bar (Overlay on top)
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
            // Logo + Title + Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_potflix_logo),
                        contentDescription = "PotFlix",
                        modifier = Modifier.height(32.dp)
                    )
                    Text(
                        text = "Movies",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        ),
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }


        }
    }
}
