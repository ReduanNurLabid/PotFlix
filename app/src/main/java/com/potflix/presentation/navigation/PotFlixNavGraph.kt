package com.potflix.presentation.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.potflix.presentation.detail.DetailScreen
import com.potflix.presentation.home.HomeScreen
import com.potflix.presentation.player.PlayerActivity
import com.potflix.presentation.movies.MoviesScreen
import com.potflix.presentation.tv.TvSeriesScreen
import com.potflix.presentation.search.SearchScreen
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color

@Composable
fun PotFlixNavGraph(
    navController: NavHostController
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(route = Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        
        composable(route = Screen.Movies.route) {
            MoviesScreen(navController = navController)
        }
        
        composable(route = Screen.TvSeries.route) {
            TvSeriesScreen(navController = navController)
        }
        
        composable(route = Screen.Settings.route) {
            com.potflix.presentation.settings.SettingsScreen(navController = navController)
        }
        
        composable(route = Screen.Search.route) {
            SearchScreen(navController = navController)
        }
        
        composable(route = Screen.Downloads.route) {
            com.potflix.presentation.downloads.DownloadsScreen(navController = navController)
        }
        
        composable(route = Screen.Watchlist.route) {
            com.potflix.presentation.watchlist.WatchlistScreen(navController = navController)
        }
        
        composable(
            route = Screen.CategoryDetail.route,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.StringType },
                navArgument("categoryName") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType; defaultValue = "category" }
            )
        ) {
            com.potflix.presentation.category.CategoryDetailScreen(navController = navController)
        }
        
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("movieJson") { type = NavType.StringType })
        ) {
            DetailScreen(navController = navController)
        }
        
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("streamUrl") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val streamUrl = backStackEntry.arguments?.getString("streamUrl") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: ""
            
            // Launch PlayerActivity instead of a Composable if we want landscape/PiP easily
            LaunchedEffect(Unit) {
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    putExtra("streamUrl", streamUrl)
                    putExtra("title", title)
                }
                context.startActivity(intent)
                navController.popBackStack() // Go back after launching activity
            }
        }
    }
}
