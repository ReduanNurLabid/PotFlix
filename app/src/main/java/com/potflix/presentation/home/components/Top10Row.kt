package com.potflix.presentation.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.potflix.domain.model.Movie

@Composable
fun Top10Row(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) return

    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp
                ),
                color = Color.White
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = movies.take(10),
                key = { _, movie -> movie.url },
                contentType = { _, _ -> "top10Item" }
            ) { index, movie ->
                Top10Item(
                    rank = index + 1,
                    movie = movie,
                    onClick = { onMovieClick(movie) }
                )
            }
        }
    }
}

@Composable
private fun Top10Item(
    rank: Int,
    movie: Movie,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(165.dp)
            .height(200.dp)
            .clickable { onClick() }
    ) {
        // Large Rank Number overlay on bottom left
        Text(
            text = rank.toString(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-6).dp, y = 14.dp),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 110.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFE50914)
            )
        )

        // Movie Poster shifted right
        Card(
            modifier = Modifier
                .width(120.dp)
                .height(180.dp)
                .align(Alignment.CenterEnd),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            AsyncImage(
                model = movie.poster ?: "https://via.placeholder.com/342x513?text=${movie.title}",
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
