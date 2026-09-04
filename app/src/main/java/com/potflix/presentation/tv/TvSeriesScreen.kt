package com.potflix.presentation.tv

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
import com.potflix.presentation.common.shimmerEffect
import com.potflix.presentation.navigation.Screen
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvSeriesScreen(
    navController: NavController,
    viewModel: TvSeriesViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val trendingSeries by viewModel.trendingTv.collectAsState()
    val categorySeries by viewModel.categoryTv.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isHeroInWatchlist by viewModel.isHeroInWatchlist.collectAsState()

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        if (isLoading && categories.isEmpty()) {
            Box(modifier = Modifier.padding(top = 90.dp)) {
                ScreenSkeleton()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 90.dp, bottom = 100.dp)
            ) {
                // Hero Banner
                if (trendingSeries.isNotEmpty()) {
                    item {
                        val heroMovie = trendingSeries.first()
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

                // Top 10 Series Row
                if (trendingSeries.size > 1) {
                    item {
                        Top10Row(
                            title = "Trending Now",
                            movies = trendingSeries.drop(1),
                            onMovieClick = { movie ->
                                navController.navigate(Screen.Detail.createRoute(movie))
                            }
                        )
                    }
                }

                // Category Rows
                items(
                    items = categories,
                    key = { it.id },
                    contentType = { "categoryRow" }
                ) { category ->
                    val seriesList = categorySeries[category.id] ?: emptyList()
                    if (seriesList.isNotEmpty()) {
                        CategoryRow(
                            category = category,
                            movies = seriesList,
                            onMovieClick = { movie ->
                                navController.navigate(Screen.Detail.createRoute(movie))
                            },
                            onSeeAllClick = {
                                navController.navigate(Screen.CategoryDetail.createRoute(category.id, category.name, "category_tv"))
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
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
                        painter = painterResource(id = R.drawable.ic_potflix_logo_vector),
                        contentDescription = "PotFlix",
                        modifier = Modifier.height(40.dp)
                    )
                    Text(
                        text = "TV Series",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
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
                            modifier = Modifier.size(32.dp)
                        )
                    }
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
}

@Composable
fun ScreenSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        // Hero skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .shimmerEffect()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Rows
        repeat(3) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .width(120.dp)
                    .height(24.dp)
                    .shimmerEffect()
            )
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(5) {
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(160.dp)
                            .shimmerEffect()
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
