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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
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
    
    if (currentRoute == Screen.Onboarding.route || currentRoute == Screen.Player.route || currentRoute == Screen.Detail.route) {
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val isTv = remember { com.potflix.util.TvUtils.isTelevision(context) }
    
    if (isTv) {
        // TV: Don't render anything here — the side rail is rendered from MainActivity
        return
    }

    // Mobile: Standard Bottom Navigation Bar
    NavigationBar(
        modifier = Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        containerColor = Color(0xFF0A0A0A),
        contentColor = PotFlixRed,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route

            NavigationBarItem(
                alwaysShowLabel = true,
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
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

/**
 * Netflix-style side navigation rail for Android TV.
 * Collapsed by default showing only icons; expands on focus to reveal labels.
 */
@Composable
fun TvSideNavRail(navController: NavController) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Movies,
        BottomNavItem.TvSeries,
        BottomNavItem.Watchlist,
        BottomNavItem.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    if (currentRoute == Screen.Onboarding.route || currentRoute == Screen.Player.route || currentRoute == Screen.Detail.route) {
        return
    }
    
    var isRailExpanded by remember { mutableStateOf(false) }

    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (isRailExpanded) 160.dp else 64.dp)
            .background(Color(0xFF0A0A0A))
            .onFocusChanged { focusState ->
                isRailExpanded = focusState.hasFocus
            },
        containerColor = Color(0xFF0A0A0A),
        contentColor = PotFlixRed
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // App logo/title at top
        Text(
            text = if (isRailExpanded) "PotFlix" else "P",
            color = PotFlixRed,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        items.forEach { item ->
            val selected = currentRoute == item.route
            var itemFocused by remember { mutableStateOf(false) }
            
            NavigationRailItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            navController.graph.startDestinationRoute?.let { route ->
                                popUpTo(route) { inclusive = false }
                            }
                            launchSingleTop = true
                        }
                    }
                },
                icon = {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = if (selected) item.selectedIcon else item.unselectedIcon),
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = if (isRailExpanded) {
                    { Text(item.label, style = MaterialTheme.typography.labelMedium) }
                } else null,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .onFocusChanged { itemFocused = it.isFocused }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                            if (!selected) {
                                navController.navigate(item.route) {
                                    navController.graph.startDestinationRoute?.let { route ->
                                        popUpTo(route) { inclusive = false }
                                    }
                                    launchSingleTop = true
                                }
                            }
                            true
                        } else false
                    },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = PotFlixRed,
                    selectedTextColor = PotFlixRed,
                    unselectedIconColor = PotFlixTextMuted,
                    unselectedTextColor = PotFlixTextMuted,
                    indicatorColor = if (itemFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent
                )
            )
        }
    }
}
