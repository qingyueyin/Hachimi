package com.qing.hachimi.ui.navigation

// ============================================================================
// Discover Tab Navigation Stack
// ============================================================================

sealed class DiscoverScreen {
    data object Home : DiscoverScreen()
    data object Charts : DiscoverScreen()
    data class ChartDetail(val chartId: Long, val chartName: String) : DiscoverScreen()
    data object Recommend : DiscoverScreen()
    data object NewAlbums : DiscoverScreen()
    data class AlbumDetail(val albumId: Long, val albumName: String) : DiscoverScreen()
    data object MyPlaylists : DiscoverScreen()
    data class PlaylistDetail(val playlistId: Long, val playlistName: String) : DiscoverScreen()
    data object HotSearch : DiscoverScreen()
    data class ArtistDetail(val artistId: Long, val artistName: String) : DiscoverScreen()
}

// ============================================================================
// Search Tab Navigation Stack
// ============================================================================

sealed class SearchScreen {
    data object Home : SearchScreen()
    data class Results(val searchQuery: String) : SearchScreen()
    data class AlbumDetail(val albumId: Long, val albumName: String) : SearchScreen()
    data class ArtistDetail(val artistId: Long, val artistName: String) : SearchScreen()
}

// ============================================================================
// Navigation Stack Helper
// ============================================================================

class NavigationStack<T : Any>(private val defaultScreen: T) {

    private val backStack = mutableListOf<T>()

    val currentScreen: T
        get() = backStack.lastOrNull() ?: defaultScreen

    val canGoBack: Boolean
        get() = backStack.size > 1

    fun push(screen: T) {
        backStack.add(screen)
    }

    fun pop(): T? {
        if (backStack.size <= 1) return null
        backStack.removeAt(backStack.lastIndex)
        return backStack.lastOrNull()
    }

    fun clear() {
        backStack.clear()
        backStack.add(defaultScreen)
    }

    override fun toString(): String {
        return "NavigationStack(size=${backStack.size}, current=$currentScreen)"
    }
}
