package com.potflix.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.potflix.presentation.theme.PotFlixRed
import com.potflix.presentation.theme.PotFlixTextMuted

sealed class BottomNavItem(
    val route: String,
    val selectedIcon: Int,
    val unselectedIcon: Int,
    val label: String,
) {
    object Home : BottomNavItem(
        Screen.Home.route,
        com.potflix.R.drawable.ic_nav_home_hover,
        com.potflix.R.drawable.ic_nav_home,
        "Home"
    )
    object Movies : BottomNavItem(
        Screen.Movies.route,
        com.potflix.R.drawable.ic_nav_movies_hover,
        com.potflix.R.drawable.ic_nav_movies,
        "Movies"
    )
    object TvSeries : BottomNavItem(
        Screen.TvSeries.route,
        com.potflix.R.drawable.ic_nav_tvseries_hover,
        com.potflix.R.drawable.ic_nav_tvseries,
        "Series"
    )
    object Settings : BottomNavItem(
        Screen.Settings.route,
        com.potflix.R.drawable.ic_nav_settings_hover,
        com.potflix.R.drawable.ic_nav_settings,
        "Settings"
    )
    object Watchlist : BottomNavItem(
        Screen.Watchlist.route,
        com.potflix.R.drawable.ic_nav_watchlist_hover,
        com.potflix.R.drawable.ic_nav_watchlist,
        "Watchlist"
    )
}

@Composable
fun BottomNavBar(navController: NavController) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Movies,
        BottomNavItem.TvSeries,
        BottomNavItem.Watchlist,
        BottomNavItem.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Minimal, standard Bottom Navigation Bar
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = PotFlixRed,
        tonalElevation = 8.dp,
        windowInsets = WindowInsets.navigationBars
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route

            NavigationBarItem(
                alwaysShowLabel = true, // Set to true to prevent the shifting/scaling animation
                icon = {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = if (selected) item.selectedIcon else item.unselectedIcon),
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            navController.graph.startDestinationRoute?.let { route ->
                                popUpTo(route) { 
                                    inclusive = false
                                }
                            }
                            launchSingleTop = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PotFlixRed,
                    selectedTextColor = PotFlixRed,
                    unselectedIconColor = PotFlixTextMuted,
                    unselectedTextColor = PotFlixTextMuted,
                    indicatorColor = Color.Transparent // Minimal design: no background pill
                )
            )
        }
    }
}

