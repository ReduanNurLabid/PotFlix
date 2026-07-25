package com.potflix.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Movies : Screen("movies")
    object TvSeries : Screen("tv_series")
    object Search : Screen("search")
    object Settings : Screen("settings")
    object Downloads : Screen("downloads")
    object Watchlist : Screen("watchlist")
    object CategoryDetail : Screen("category/{categoryId}/{categoryName}") {
        fun createRoute(categoryId: String, categoryName: String): String {
            val encodedId = android.net.Uri.encode(categoryId)
            val encodedName = android.net.Uri.encode(categoryName)
            return "category/$encodedId/$encodedName"
        }
    }
    object Detail : Screen("detail/{movieJson}") {
        fun createRoute(movie: com.potflix.domain.model.Movie): String {
            val json = com.google.gson.Gson().toJson(movie)
            val encoded = android.util.Base64.encodeToString(json.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            return "detail/$encoded"
        }
    }
    object Player : Screen("player/{streamUrl}/{title}") {
        fun createRoute(streamUrl: String, title: String): String {
            val encodedUrl = android.net.Uri.encode(streamUrl)
            val encodedTitle = android.net.Uri.encode(title)
            return "player/$encodedUrl/$encodedTitle"
        }
    }
}
