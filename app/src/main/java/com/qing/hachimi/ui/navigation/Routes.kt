package com.qing.hachimi.ui.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
sealed class Route : NavKey, Parcelable {
    // Main tabs
    data object DiscoverHome : Route()
    data object SearchHome : Route()
    data object MyHome : Route()
    data object SettingsHome : Route()

    // Discover sub-pages
    @Serializable
    data class ChartDetail(val chartId: Long, val chartName: String) : Route()

    @Serializable
    data class AlbumDetail(val albumId: Long, val albumName: String) : Route()

    @Serializable
    data class PlaylistDetail(val playlistId: Long, val playlistName: String) : Route()

    @Serializable
    data class ArtistDetail(val artistId: Long, val artistName: String) : Route()

    // Search sub-pages
    @Serializable
    data class SearchResult(val query: String) : Route()

    // Settings sub-pages
    data object AppearanceSettings : Route()
    data object DownloadSettings : Route()
    data object AccountSettings : Route()
}
