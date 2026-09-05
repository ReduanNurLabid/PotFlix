package com.potflix.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    object Watchlist : BottomNavItem(
        Screen.Watchlist.route,
        com.potflix.R.drawable.ic_nav_watchlist_hover,
        com.potflix.R.drawable.ic_nav_watchlist,
        "Watchlist"
    )
    object Settings : BottomNavItem(
        Screen.Settings.route,
        com.potflix.R.drawable.ic_nav_settings_hover,
        com.potflix.R.drawable.ic_nav_settings,
        "Settings"
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

    // Mobile: Elevated Glassmorphic Bottom Navigation Bar
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
                ),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ),
        color = Color(0xFF0F0F13).copy(alpha = 0.98f),
        tonalElevation = 8.dp
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = PotFlixRed,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route

                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.15f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "navIconScale"
                )

                val iconColor by animateColorAsState(
                    targetValue = if (selected) PotFlixRed else PotFlixTextMuted,
                    animationSpec = tween(durationMillis = 200),
                    label = "navIconColor"
                )

                val textColor by animateColorAsState(
                    targetValue = if (selected) PotFlixRed else PotFlixTextMuted,
                    animationSpec = tween(durationMillis = 200),
                    label = "navTextColor"
                )

                NavigationBarItem(
                    alwaysShowLabel = true,
                    icon = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = if (selected) item.selectedIcon else item.unselectedIcon),
                                contentDescription = item.label,
                                tint = iconColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                        }
                    },
                    label = {
                        Text(
                            text = item.label,
                            color = textColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
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
                        indicatorColor = PotFlixRed.copy(alpha = 0.16f)
                    )
                )
            }
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

    val railWidth by animateDpAsState(
        targetValue = if (isRailExpanded) 180.dp else 72.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "railWidth"
    )

    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .width(railWidth)
            .background(Color(0xFF0C0C0F))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.08f))
                ),
                shape = androidx.compose.ui.graphics.RectangleShape
            )
            .onFocusChanged { focusState ->
                isRailExpanded = focusState.hasFocus
            },
        containerColor = Color(0xFF0C0C0F),
        contentColor = PotFlixRed
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // App logo/title at top
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            contentAlignment = if (isRailExpanded) Alignment.CenterStart else Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Branded 'P' emblem
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PotFlixRed, Color(0xFFB00610))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "P",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                }
                
                if (isRailExpanded) {
                    Text(
                        text = "PotFlix",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        items.forEach { item ->
            val selected = currentRoute == item.route
            var itemFocused by remember { mutableStateOf(false) }

            val itemScale by animateFloatAsState(
                targetValue = if (itemFocused) 1.08f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "tvItemScale"
            )

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Left active indicator pill
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(PotFlixRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        
                        Icon(
                            painter = painterResource(id = if (selected) item.selectedIcon else item.unselectedIcon),
                            contentDescription = item.label,
                            tint = if (selected || itemFocused) PotFlixRed else PotFlixTextMuted,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = itemScale
                                    scaleY = itemScale
                                }
                        )
                    }
                },
                label = if (isRailExpanded) {
                    {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected || itemFocused) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected || itemFocused) Color.White else PotFlixTextMuted
                        )
                    }
                } else null,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (itemFocused) {
                            Modifier
                                .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                                .border(1.5.dp, PotFlixRed, RoundedCornerShape(12.dp))
                        } else if (selected) {
                            Modifier.background(PotFlixRed.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        }
                    )
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
                    selectedTextColor = Color.White,
                    unselectedIconColor = PotFlixTextMuted,
                    unselectedTextColor = PotFlixTextMuted,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
