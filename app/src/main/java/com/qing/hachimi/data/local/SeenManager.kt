package com.qing.hachimi.data.local

import android.content.Context
import android.content.SharedPreferences

class SeenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hachimi_seen", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SEEN_CHARTS = "seen_chart_ids"
        private const val KEY_SEEN_ALBUMS = "seen_album_ids"
        private const val KEY_SEEN_PLAYLISTS = "seen_playlist_ids"
        private const val SEPARATOR = ","
    }

    private fun getSet(key: String): Set<Long> {
        val str = prefs.getString(key, "") ?: ""
        if (str.isEmpty()) return emptySet()
        return str.split(SEPARATOR).mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun saveSet(key: String, ids: Set<Long>) {
        prefs.edit().putString(key, ids.joinToString(SEPARATOR)).apply()
    }

    fun markSeenChart(chartId: Long) {
        val seen = getSet(KEY_SEEN_CHARTS).toMutableSet()
        seen.add(chartId)
        saveSet(KEY_SEEN_CHARTS, seen)
    }

    fun markSeenAlbum(albumId: Long) {
        val seen = getSet(KEY_SEEN_ALBUMS).toMutableSet()
        seen.add(albumId)
        saveSet(KEY_SEEN_ALBUMS, seen)
    }

    fun markSeenPlaylist(playlistId: Long) {
        val seen = getSet(KEY_SEEN_PLAYLISTS).toMutableSet()
        seen.add(playlistId)
        saveSet(KEY_SEEN_PLAYLISTS, seen)
    }

    fun markAllSeenCharts(ids: Collection<Long>) {
        val seen = getSet(KEY_SEEN_CHARTS).toMutableSet()
        seen.addAll(ids)
        saveSet(KEY_SEEN_CHARTS, seen)
    }

    fun markAllSeenAlbums(ids: Collection<Long>) {
        val seen = getSet(KEY_SEEN_ALBUMS).toMutableSet()
        seen.addAll(ids)
        saveSet(KEY_SEEN_ALBUMS, seen)
    }

    fun markAllSeenPlaylists(ids: Collection<Long>) {
        val seen = getSet(KEY_SEEN_PLAYLISTS).toMutableSet()
        seen.addAll(ids)
        saveSet(KEY_SEEN_PLAYLISTS, seen)
    }

    fun isChartSeen(chartId: Long): Boolean {
        return chartId in getSet(KEY_SEEN_CHARTS)
    }

    fun isAlbumSeen(albumId: Long): Boolean {
        return albumId in getSet(KEY_SEEN_ALBUMS)
    }

    fun isPlaylistSeen(playlistId: Long): Boolean {
        return playlistId in getSet(KEY_SEEN_PLAYLISTS)
    }

    fun clearSeenCharts() {
        prefs.edit().remove(KEY_SEEN_CHARTS).apply()
    }

    fun clearSeenAlbums() {
        prefs.edit().remove(KEY_SEEN_ALBUMS).apply()
    }

    fun clearSeenPlaylists() {
        prefs.edit().remove(KEY_SEEN_PLAYLISTS).apply()
    }

    fun clearAll() {
        prefs.edit().apply {
            remove(KEY_SEEN_CHARTS)
            remove(KEY_SEEN_ALBUMS)
            remove(KEY_SEEN_PLAYLISTS)
        }.apply()
    }
}
