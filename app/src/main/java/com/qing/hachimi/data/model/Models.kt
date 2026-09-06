package com.qing.hachimi.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistResponse(
    val code: Long = -1,
    val playlist: PlaylistDetail? = null
)

@Serializable
data class PlaylistDetail(
    val id: Long = 0,
    val name: String = "",
    val trackIds: List<TrackId>? = null,
    val tracks: List<SongDto>? = null
)

@Serializable
data class TrackId(val id: Long = 0)

@Serializable
data class SongDto(
    val id: Long = 0,
    val name: String = "",
    val ar: List<ArtistDto>? = null,
    val al: AlbumDto? = null
)

@Serializable
data class ArtistDto(
    val id: Long = 0,
    val name: String = ""
)

@Serializable
data class AlbumDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("picUrl")
    val picUrl: String = ""
)

@Serializable
data class SongDetailResponse(
    val code: Long = -1,
    val songs: List<SongDto>? = null
)

@Serializable
data class SongUrlResponse(
    val code: Long = -1,
    val data: List<SongUrlData>? = null
)

@Serializable
data class SongUrlData(
    val id: Long = 0,
    val url: String? = null,
    val level: String? = null,
    val type: String? = null,
    val encodeType: String? = null
)

@Serializable
data class LyricResponse(
    val code: Long = -1,
    val lrc: LyricData? = null,
    val tlyric: LyricData? = null
)

@Serializable
data class LyricData(
    val lyric: String = "",
    val version: Int = 0
)

data class Song(
    val id: Long,
    val name: String,
    val artists: String,
    val album: String,
    val coverUrl: String
)

data class ArtistResult(
    val id: Long,
    val name: String,
    val avatarUrl: String = "",
    val alias: String = "",
    val albumCount: Int = 0
)

data class AlbumResult(
    val id: Long,
    val name: String,
    val artist: String = "",
    val coverUrl: String = "",
    val publishTime: Long = 0
)

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val artists: List<ArtistResult> = emptyList(),
    val albums: List<AlbumResult> = emptyList(),
    val playlists: List<DiscoveryPlaylist> = emptyList(),
    val podcasts: List<PodcastChannel> = emptyList(),
    val songOffset: Int = 0,
    val artistOffset: Int = 0,
    val albumOffset: Int = 0,
    val playlistOffset: Int = 0,
    val podcastOffset: Int = 0,
    val hasMoreSongs: Boolean = false,
    val hasMoreArtists: Boolean = false,
    val hasMoreAlbums: Boolean = false,
    val hasMorePlaylists: Boolean = false,
    val hasMorePodcasts: Boolean = false,
    val albumArtistId: Long? = null,
)

enum class SearchCategory(val label: String, val apiType: Int) {
    SONGS("歌曲", 1),
    ARTISTS("歌手", 100),
    ALBUMS("专辑", 10),
    PLAYLISTS("歌单", 1000),
    PODCASTS("播客", 1009);

    companion object {
        fun fromApiType(type: Int): SearchCategory = entries.firstOrNull { it.apiType == type } ?: SONGS
    }
}

data class SearchSuggestion(
    val keyword: String,
    val category: SearchCategory,
)

enum class SourceMode {
    SEARCH,
    RECOMMEND,
    CHARTS,
    PLAYLISTS,
    PODCASTS,
    ARTISTS,
    NEW_ALBUMS,
    MY_PLAYLISTS,
    ARTIST_DETAIL,
    PERSONAL_FM,
    NEW_SONGS,
    STYLE_PLAYLISTS,
}

enum class SongSortOption(val label: String) {
    DEFAULT("默认排序"),
    NAME("歌曲名"),
    ARTIST("歌手"),
    ALBUM("专辑")
}

fun List<Song>.sortedByOption(option: SongSortOption, ascending: Boolean): List<Song> {
    val sorted = when (option) {
        SongSortOption.DEFAULT -> this
        SongSortOption.NAME -> sortedBy { it.name }
        SongSortOption.ARTIST -> sortedBy { it.artists }
        SongSortOption.ALBUM -> sortedBy { it.album }
    }
    return if (ascending || option == SongSortOption.DEFAULT) sorted else sorted.reversed()
}
