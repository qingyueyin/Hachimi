package com.qing.hachimi.data.api

import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.ArtistResult
import com.qing.hachimi.data.model.DiscoveryPlaylist
import com.qing.hachimi.data.model.PodcastChannel
import com.qing.hachimi.data.model.Song
import org.json.JSONObject
import java.io.IOException

class SearchApi(private val api: NeteaseApi) {

    private fun post(keywords: String, type: Int, limit: Int, offset: Int, cookies: Map<String, String>): JSONObject {
        val raw = api.post(
            "https://music.163.com/api/cloudsearch/pc",
            api.formBody(
                "s", keywords,
                "type", type.toString(),
                "limit", limit.toString(),
                "offset", offset.toString(),
                "total", "true",
            ),
            cookies,
            callTimeoutMs = 10_000L,
        )
        val response = JSONObject(raw)
        if (response.optLong("code") != 200L) {
            val message = response.optString("message").ifBlank { response.optString("msg") }
                .ifBlank { "服务暂不可用" }
            throw IOException("搜索服务错误: $message")
        }
        return response.optJSONObject("result") ?: JSONObject()
    }

    fun searchSongs(keywords: String, cookies: Map<String, String>, limit: Int = 30, offset: Int = 0): SearchSongsResult {
        val result = post(keywords, 1, limit, offset, cookies)
        val songs = result.optJSONArray("songs") ?: return SearchSongsResult(nextOffset = offset)
        val list = (0 until songs.length()).map { i ->
            val s = songs.getJSONObject(i)
            val al = s.optJSONObject("al")
            val ar = s.optJSONArray("ar")
            Song(
                id = s.optLong("id"),
                name = s.optString("name"),
                artists = if (ar != null) {
                    (0 until ar.length()).joinToString("、") { ar.getJSONObject(it).optString("name") }
                } else "",
                album = al?.optString("name") ?: "",
                coverUrl = al?.optString("picUrl") ?: ""
            )
        }
        val total = result.optInt("songCount", list.size)
        val nextOffset = offset + list.size
        return SearchSongsResult(
            songs = list,
            total = total,
            nextOffset = nextOffset,
            hasMore = nextOffset < total,
        )
    }

    fun searchAlbums(keywords: String, cookies: Map<String, String>, limit: Int = 20, offset: Int = 0): SearchAlbumsResult {
        val result = post(keywords, 10, limit, offset, cookies)
        val albums = result.optJSONArray("albums") ?: return SearchAlbumsResult(nextOffset = offset)
        val list = (0 until albums.length()).map { i ->
            val a = albums.getJSONObject(i)
            val artist = a.optJSONObject("artist")
            AlbumResult(
                id = a.optLong("id"),
                name = a.optString("name"),
                artist = artist?.optString("name") ?: "",
                coverUrl = a.optString("picUrl") ?: "",
                publishTime = a.optLong("publishTime")
            )
        }
        val total = result.optInt("albumCount", list.size)
        val nextOffset = offset + list.size
        return SearchAlbumsResult(
            albums = list,
            total = total,
            nextOffset = nextOffset,
            hasMore = nextOffset < total,
        )
    }

    fun searchArtists(keywords: String, cookies: Map<String, String>, limit: Int = 20, offset: Int = 0): SearchArtistsResult {
        val result = post(keywords, 100, limit, offset, cookies)
        val artists = result.optJSONArray("artists") ?: return SearchArtistsResult(nextOffset = offset)
        val list = (0 until artists.length()).map { i ->
            val a = artists.getJSONObject(i)
            val aliasArr = a.optJSONArray("alias")
            ArtistResult(
                id = a.optLong("id"),
                name = a.optString("name"),
                avatarUrl = a.optString("img1v1Url") ?: "",
                alias = if (aliasArr != null && aliasArr.length() > 0) {
                    (0 until aliasArr.length()).joinToString(", ") { aliasArr.optString(it) }
                } else "",
                albumCount = a.optInt("albumSize", 0)
            )
        }
        val total = result.optInt("artistCount", list.size)
        val nextOffset = offset + list.size
        return SearchArtistsResult(
            artists = list,
            total = total,
            nextOffset = nextOffset,
            hasMore = nextOffset < total,
        )
    }

    fun searchPlaylists(
        keywords: String,
        cookies: Map<String, String>,
        limit: Int = 20,
        offset: Int = 0,
    ): SearchPlaylistsResult {
        val result = post(keywords, 1000, limit, offset, cookies)
        val playlists = result.optJSONArray("playlists")
            ?: return SearchPlaylistsResult(nextOffset = offset)
        val list = (0 until playlists.length()).map { index ->
            val playlist = playlists.getJSONObject(index)
            DiscoveryPlaylist(
                id = playlist.optLong("id"),
                name = playlist.optString("name"),
                coverUrl = playlist.optString("coverImgUrl"),
                creator = playlist.optJSONObject("creator")?.optString("nickname").orEmpty(),
                trackCount = playlist.optInt("trackCount"),
                playCount = playlist.optLong("playCount"),
                description = playlist.optString("description"),
                tags = playlist.optJSONArray("tags")?.let { tags ->
                    (0 until tags.length()).mapNotNull { index ->
                        tags.optString(index).takeIf(String::isNotBlank)
                    }
                }.orEmpty(),
                createTime = playlist.optLong("createTime"),
                updateTime = playlist.optLong("updateTime"),
            )
        }
        val total = result.optInt("playlistCount", list.size)
        val nextOffset = offset + list.size
        return SearchPlaylistsResult(
            playlists = list,
            total = total,
            nextOffset = nextOffset,
            hasMore = nextOffset < total,
        )
    }

    fun searchPodcasts(
        keywords: String,
        cookies: Map<String, String>,
        limit: Int = 20,
        offset: Int = 0,
    ): SearchPodcastsResult {
        val result = post(keywords, 1009, limit, offset, cookies)
        val podcasts = result.optJSONArray("djRadios")
            ?: return SearchPodcastsResult(nextOffset = offset)
        val list = (0 until podcasts.length()).map { index ->
            val podcast = podcasts.getJSONObject(index)
            PodcastChannel(
                id = podcast.optLong("id"),
                name = podcast.optString("name"),
                coverUrl = podcast.optString("picUrl"),
                host = podcast.optJSONObject("dj")?.optString("nickname").orEmpty(),
                category = podcast.optString("category"),
                programCount = podcast.optInt("programCount"),
                playCount = podcast.optLong("playCount"),
                description = podcast.optString("desc"),
                createTime = podcast.optLong("createTime"),
                updateTime = podcast.optLong("lastProgramCreateTime").takeIf { it > 0 }
                    ?: podcast.optLong("updateTime"),
            )
        }
        val total = result.optInt("djRadiosCount", list.size)
        val nextOffset = offset + list.size
        return SearchPodcastsResult(
            podcasts = list,
            total = total,
            nextOffset = nextOffset,
            hasMore = nextOffset < total,
        )
    }
}

data class SearchSongsResult(
    val songs: List<Song> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int = 0,
    val hasMore: Boolean = false
)

data class SearchAlbumsResult(
    val albums: List<AlbumResult> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int = 0,
    val hasMore: Boolean = false
)

data class SearchArtistsResult(
    val artists: List<ArtistResult> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int = 0,
    val hasMore: Boolean = false
)

data class SearchPlaylistsResult(
    val playlists: List<DiscoveryPlaylist> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int = 0,
    val hasMore: Boolean = false,
)

data class SearchPodcastsResult(
    val podcasts: List<PodcastChannel> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int = 0,
    val hasMore: Boolean = false,
)
