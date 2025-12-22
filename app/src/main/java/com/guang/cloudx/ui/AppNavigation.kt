package com.guang.cloudx.ui

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Login : Screen("login")
    object DownloadManager : Screen("download_manager")
    object Settings : Screen("settings")
    object Playlist : Screen("playlist/{type}/{id}") {
        fun createRoute(type: String, id: String) = "playlist/$type/$id"
    }
}
