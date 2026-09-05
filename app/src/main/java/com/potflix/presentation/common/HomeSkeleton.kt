package com.potflix.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HeroBannerSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(520.dp)
            .background(Color(0xFF101010))
    ) {
        // Base shimmer across the entire banner backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shimmerEffect()
        )

        // Top gradient for status bar and header overlay contrast
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom gradient fading into pure background color (#0A0A0A)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xFF0A0A0A).copy(alpha = 0.6f),
                            Color(0xFF0A0A0A).copy(alpha = 0.95f),
                            Color(0xFF0A0A0A)
                        ),
                        startY = 150f
                    )
                )
        )

        // Bottom content placeholders matching HeroBanner layout exactly
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // "TRENDING #1" Badge placeholder
            Box(
                modifier = Modifier
                    .width(95.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Title placeholder
            Box(
                modifier = Modifier
                    .width(230.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Genre chips placeholder
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata row (Rating • Year • Quality) placeholder
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: My List | Play | Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // My List placeholder
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmerEffect()
                )

                // Play Button placeholder
                Box(
                    modifier = Modifier
                        .width(115.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .shimmerEffect()
                )

                // Info Button placeholder
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmerEffect()
                )
            }
        }
    }
}

@Composable
fun Top10RowSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        // Section Title Placeholder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(135.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }

        // Top 10 items row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            items(4) {
                Box(
                    modifier = Modifier
                        .width(165.dp)
                        .height(200.dp)
                ) {
                    // Rank number cutout placeholder on left
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(60.dp)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(6.dp))
                            .shimmerEffect()
                    )

                    // Poster card shifted to right
                    Card(
                        modifier = Modifier
                            .width(120.dp)
                            .height(180.dp)
                            .align(Alignment.CenterEnd),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .shimmerEffect()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryRowSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 10.dp)) {
        // Category Header (Title + See All)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )

            Box(
                modifier = Modifier
                    .width(55.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }

        // Horizontal Row of MovieCard Skeletons
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            userScrollEnabled = false
        ) {
            items(4) {
                Card(
                    modifier = Modifier
                        .width(135.dp)
                        .height(200.dp)
                        .padding(4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreenSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 90.dp),
        userScrollEnabled = false
    ) {
        // Hero Banner Skeleton
        item {
            HeroBannerSkeleton()
        }

        // Top 10 Ranked Row Skeleton
        item {
            Top10RowSkeleton()
        }

        // Category Rows Skeletons
        items(3) {
            CategoryRowSkeleton()
        }

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

@Composable
fun CategoryDetailGridSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 12
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        items(itemCount) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Poster Shimmer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmerEffect()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Title Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .shimmerEffect()
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Year Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .shimmerEffect()
                )
            }
        }
    }
}

@Composable
fun SearchGridSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 9
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        // Result count header placeholder
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(14.dp)
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .shimmerEffect()
            )
        }

        items(itemCount) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .padding(2.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shimmerEffect()
                )
            }
        }
    }
}
