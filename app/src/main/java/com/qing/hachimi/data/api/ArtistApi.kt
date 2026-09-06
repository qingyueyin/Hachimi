package com.qing.hachimi.data.api

import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.ArtistAbout
import com.qing.hachimi.data.model.ArtistIntroduction
import com.qing.hachimi.data.model.ArtistProfile
import com.qing.hachimi.data.model.ArtistResult
import com.qing.hachimi.data.model.DiscoveryPage
import com.qing.hachimi.data.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ArtistAuthenticationRequiredException : IOException("登录后可查看相似歌手")

class ArtistApi(private val api: NeteaseApi) {

    fun getProfile(artistId: Long, cookies: Map<String, String>): ArtistProfile {
        val raw = api.post(
            "https://music.163.com/api/artist/head/info/get",
            api.formBody("id", artistId.toString()),
            cookies,
            callTimeoutMs = REQUEST_TIMEOUT_MS,
        )
        val response = JSONObject(raw).requireSuccess("歌手资料")
        val data = response.optJSONObject("data") ?: throw IOException("歌手资料为空")
        val artist = data.optJSONObject("artist") ?: throw IOException("歌手资料为空")
        val identity = data.optJSONObject("identify")?.optString("imageDesc").orEmpty()
            .ifBlank { artist.optJSONArray("identifyTag").strings().firstOrNull().orEmpty() }
        return ArtistProfile(
            id = artist.optLong("id", artistId),
            name = artist.optString("name"),
            coverUrl = artist.optString("cover"),
            avatarUrl = artist.optString("avatar"),
            translatedNames = artist.optJSONArray("transNames").strings(),
            aliases = artist.optJSONArray("alias").strings(),
            identity = identity,
            briefDescription = artist.optString("briefDesc"),
            albumCount = artist.optInt("albumSize"),
            songCount = artist.optInt("musicSize"),
        )
    }

    fun getAlbumsPage(
        artistId: Long,
        cookies: Map<String, String>,
        limit: Int = 50,
        offset: Int = 0,
    ): DiscoveryPage<AlbumResult> {
        val raw = api.post(
            "https://music.163.com/api/artist/albums/$artistId",
            api.formBody("limit", limit.toString(), "offset", offset.toString(), "total", "true"),
            cookies,
            callTimeoutMs = REQUEST_TIMEOUT_MS,
        )
        val response = JSONObject(raw).requireSuccess("歌手专辑")
        val array = response.optJSONArray("hotAlbums") ?: response.optJSONArray("albums") ?: JSONArray()
        val fallbackArtist = response.optJSONObject("artist")?.optString("name").orEmpty()
        val albums = (0 until array.length()).map { index ->
            val album = array.getJSONObject(index)
            AlbumResult(
                id = album.optLong("id"),
                name = album.optString("name"),
                artist = album.optJSONObject("artist")?.optString("name").orEmpty().ifBlank { fallbackArtist },
                coverUrl = album.optString("picUrl"),
                publishTime = album.optLong("publishTime"),
            )
        }.distinctBy { it.id }
        val nextOffset = offset + array.length()
        return DiscoveryPage(
            items = albums,
            nextOffset = nextOffset,
            hasMore = response.optBoolean("more", array.length() >= limit),
        )
    }

    fun getAlbums(
        artistId: Long,
        cookies: Map<String, String>,
        limit: Int = 50,
        offset: Int = 0,
    ): List<AlbumResult> = getAlbumsPage(artistId, cookies, limit, offset).items

    fun getTopSongs(artistId: Long, cookies: Map<String, String>): List<Song> {
        val raw = api.post(
            "https://music.163.com/api/artist/top/song",
            api.formBody("id", artistId.toString()),
            cookies,
            callTimeoutMs = REQUEST_TIMEOUT_MS,
        )
        val response = JSONObject(raw).requireSuccess("歌手热门歌曲")
        val array = response.optJSONArray("hotSongs") ?: response.optJSONArray("songs") ?: JSONArray()
        return (0 until array.length()).map { index ->
            val song = array.getJSONObject(index)
            val album = song.optJSONObject("al")
            val artists = song.optJSONArray("ar")
            Song(
                id = song.optLong("id"),
                name = song.optString("name"),
                artists = artists.names(),
                album = album?.optString("name").orEmpty(),
                coverUrl = album?.optString("picUrl").orEmpty(),
            )
        }.distinctBy { it.id }
    }

    fun getIntroduction(artistId: Long, cookies: Map<String, String>): ArtistAbout {
        val payload = JSONObject().put("id", artistId).toString()
        val response = JSONObject(
            api.weapiPost("artist/introduction", payload, cookies, callTimeoutMs = REQUEST_TIMEOUT_MS),
        ).requireSuccess("歌手介绍")
        val introduction = response.optJSONArray("introduction") ?: JSONArray()
        return ArtistAbout(
            briefDescription = response.optString("briefDesc"),
            introductions = (0 until introduction.length()).mapNotNull { index ->
                val item = introduction.optJSONObject(index) ?: return@mapNotNull null
                val text = item.optString("txt").trim()
                if (text.isBlank()) null else ArtistIntroduction(
                    title = item.optString("ti").ifBlank { "更多资料" },
                    text = text,
                )
            },
        )
    }

    fun getSimilarArtists(artistId: Long, cookies: Map<String, String>): List<ArtistResult> {
        val payload = JSONObject().put("artistid", artistId).toString()
        val response = JSONObject(
            api.weapiPost("discovery/simiArtist", payload, cookies, callTimeoutMs = REQUEST_TIMEOUT_MS),
        )
        if (response.optLong("code") == 301L) throw ArtistAuthenticationRequiredException()
        response.requireSuccess("相似歌手")
        val array = response.optJSONArray("artists") ?: JSONArray()
        return (0 until array.length()).map { index ->
            val artist = array.getJSONObject(index)
            ArtistResult(
                id = artist.optLong("id"),
                name = artist.optString("name"),
                avatarUrl = artist.optString("picUrl").ifBlank { artist.optString("img1v1Url") },
                alias = artist.optJSONArray("alias").strings().joinToString("、"),
                albumCount = artist.optInt("albumSize"),
            )
        }.distinctBy { it.id }
    }

    private fun JSONObject.requireSuccess(resource: String): JSONObject {
        if (optLong("code") == 200L) return this
        val message = optString("message").ifBlank { optString("msg") }.ifBlank { "服务暂不可用" }
        throw IOException("$resource: $message")
    }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).takeUnless { it.isBlank() || it == "null" }
        }
    }

    private fun JSONArray?.names(): String {
        if (this == null) return ""
        return (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.optString("name")?.takeUnless { it.isBlank() }
        }.joinToString("、")
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 10_000L
    }
}
