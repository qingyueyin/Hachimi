package com.qing.hachimi.data.api

import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.ArtistArea
import com.qing.hachimi.data.model.ArtistResult
import com.qing.hachimi.data.model.DiscoveryPage
import com.qing.hachimi.data.model.DiscoveryPlaylist
import com.qing.hachimi.data.model.PodcastChannel
import com.qing.hachimi.data.model.SearchCategory
import com.qing.hachimi.data.model.SearchSuggestion
import com.qing.hachimi.data.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class ChartInfo(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val description: String = "",
    val updateFrequency: String = "",
    val trackCount: Int = 0,
    val playCount: Long = 0,
    val updateTime: Long = 0,
)

data class ChartSongsResult(
    val songs: List<Song>,
    val chartName: String = "",
    val coverUrl: String = "",
    val description: String = "",
    val updateFrequency: String = "",
    val trackCount: Int = 0,
    val playCount: Long = 0,
    val updateTime: Long = 0,
)

class DiscoveryApi(private val api: NeteaseApi) {

    companion object {
        private const val REQUEST_TIMEOUT_MS = 10_000L
    }

    private fun weapi(
        path: String,
        payload: JSONObject,
        cookies: Map<String, String>,
    ): JSONObject {
        val raw = api.weapiPost(path, payload.toString(), cookies, REQUEST_TIMEOUT_MS)
        val response = JSONObject(raw)
        if (response.optLong("code") != 200L) {
            val message = response.optString("message").ifBlank {
                response.optString("msg").ifBlank { "网易云接口返回异常" }
            }
            throw IOException(message)
        }
        return response
    }

    private fun JSONObject.requireSuccess(operation: String): JSONObject {
        if (optLong("code") == 200L) return this
        val message = optString("message").ifBlank { optString("msg") }.ifBlank { operation }
        throw IOException(message)
    }

    private fun parsePlaylist(item: JSONObject) = DiscoveryPlaylist(
        id = item.optLong("id"),
        name = item.optString("name"),
        coverUrl = item.optString("picUrl").ifBlank { item.optString("coverImgUrl") },
        creator = item.optJSONObject("creator")?.optString("nickname").orEmpty(),
        trackCount = item.optInt("trackCount"),
        playCount = item.optLong("playCount"),
        description = item.optString("copywriter").ifBlank { item.optString("description") },
        tags = item.optJSONArray("tags")?.let { tags ->
            (0 until tags.length()).mapNotNull { index -> tags.optString(index).takeIf(String::isNotBlank) }
        }.orEmpty(),
        createTime = item.optLong("createTime"),
        updateTime = item.optLong("updateTime"),
    )

    private fun parsePodcast(item: JSONObject) = PodcastChannel(
        id = item.optLong("id"),
        name = item.optString("name"),
        coverUrl = item.optString("picUrl").ifBlank { item.optString("intervenePicUrl") },
        host = item.optJSONObject("dj")?.optString("nickname").orEmpty(),
        category = item.optString("category"),
        programCount = item.optInt("programCount"),
        playCount = item.optLong("playCount"),
        description = item.optString("desc").ifBlank { item.optString("copywriter") },
        createTime = item.optLong("createTime"),
        updateTime = item.optLong("lastProgramCreateTime").takeIf { it > 0 } ?: item.optLong("updateTime"),
    )

    private fun parseSong(item: JSONObject, fallbackCover: String = ""): Song {
        val album = item.optJSONObject("al") ?: item.optJSONObject("album")
        val artists = item.optJSONArray("ar") ?: item.optJSONArray("artists")
        val albumName = album?.optString("name").orEmpty().takeUnless { it == "null" }.orEmpty()
        val coverUrl = album?.optString("picUrl").orEmpty().takeUnless { it == "null" }.orEmpty()
        return Song(
            id = item.optLong("id"),
            name = item.optString("name"),
            artists = artists.names(),
            album = albumName,
            coverUrl = coverUrl.ifBlank { fallbackCover },
        )
    }

    private fun JSONArray?.names(): String {
        if (this == null) return ""
        return (0 until length()).joinToString("、") { optJSONObject(it)?.optString("name").orEmpty() }
    }

    private fun JSONArray?.strings(): String {
        if (this == null) return ""
        return (0 until length()).mapNotNull { index ->
            optString(index).takeIf { it.isNotBlank() }
        }.joinToString("、")
    }

    fun getRecommendedPlaylists(
        cookies: Map<String, String>,
        limit: Int = 12,
    ): List<DiscoveryPlaylist> {
        val response = weapi(
            path = "personalized/playlist",
            payload = JSONObject()
                .put("limit", limit)
                .put("total", true)
                .put("n", 1000),
            cookies = cookies,
        )
        val items = response.optJSONArray("result") ?: return emptyList()
        return (0 until items.length())
            .map { parsePlaylist(items.getJSONObject(it)) }
            .filter { it.id > 0 && it.name.isNotBlank() }
            .distinctBy { it.id }
    }

    fun getPlaylists(
        cookies: Map<String, String>,
        category: String,
        limit: Int = 30,
        offset: Int = 0,
    ): DiscoveryPage<DiscoveryPlaylist> {
        val response = weapi(
            path = "playlist/list",
            payload = JSONObject()
                .put("cat", category)
                .put("order", "hot")
                .put("limit", limit)
                .put("offset", offset)
                .put("total", true),
            cookies = cookies,
        )
        val array = response.optJSONArray("playlists") ?: JSONArray()
        val items = (0 until array.length())
            .map { parsePlaylist(array.getJSONObject(it)) }
            .filter { it.id > 0 && it.name.isNotBlank() }
            .distinctBy { it.id }
        val nextOffset = offset + array.length()
        val total = response.optInt("total", nextOffset)
        val hasMore = response.optBoolean("more", nextOffset < total)
        return DiscoveryPage(items, nextOffset, hasMore)
    }

    fun getArtists(
        cookies: Map<String, String>,
        area: ArtistArea,
        limit: Int = 30,
        offset: Int = 0,
    ): DiscoveryPage<ArtistResult> {
        val isHot = area.apiValue == null
        val payload = JSONObject()
            .put("limit", limit)
            .put("offset", offset)
            .put("total", true)
        if (!isHot) {
            payload.put("type", -1)
            payload.put("area", area.apiValue)
        }
        val response = weapi(
            path = if (isHot) "artist/top" else "v1/artist/list",
            payload = payload,
            cookies = cookies,
        )
        val array = response.optJSONArray("artists") ?: JSONArray()
        val items = (0 until array.length()).map { index ->
            val artist = array.getJSONObject(index)
            ArtistResult(
                id = artist.optLong("id"),
                name = artist.optString("name"),
                avatarUrl = artist.optString("picUrl").ifBlank { artist.optString("img1v1Url") },
                alias = artist.optJSONArray("alias").strings(),
                albumCount = artist.optInt("albumSize"),
            )
        }.filter { it.id > 0 && it.name.isNotBlank() }.distinctBy { it.id }
        val nextOffset = offset + array.length()
        val total = response.optInt("artistCount", nextOffset)
        val hasMore = response.optBoolean("more", nextOffset < total || array.length() >= limit)
        return DiscoveryPage(items, nextOffset, hasMore)
    }

    fun getPodcasts(
        cookies: Map<String, String>,
        limit: Int = 30,
        offset: Int = 0,
    ): DiscoveryPage<PodcastChannel> {
        val response = weapi(
            path = "djradio/hot/v1",
            payload = JSONObject().put("limit", limit).put("offset", offset),
            cookies = cookies,
        )
        val array = response.optJSONArray("djRadios") ?: JSONArray()
        val items = (0 until array.length())
            .map { parsePodcast(array.getJSONObject(it)) }
            .filter { it.id > 0 && it.name.isNotBlank() }
            .distinctBy { it.id }
        val nextOffset = offset + array.length()
        val total = response.optInt("count", nextOffset)
        val hasMore = response.optBoolean(
            "hasMore",
            response.optBoolean("more", nextOffset < total || array.length() >= limit),
        )
        return DiscoveryPage(items, nextOffset, hasMore)
    }

    fun getPodcastPrograms(
        channel: PodcastChannel,
        cookies: Map<String, String>,
        limit: Int = 50,
        offset: Int = 0,
    ): DiscoveryPage<Song> {
        val response = weapi(
            path = "dj/program/byradio",
            payload = JSONObject()
                .put("radioId", channel.id)
                .put("limit", limit)
                .put("offset", offset)
                .put("asc", false),
            cookies = cookies,
        )
        val array = response.optJSONArray("programs") ?: JSONArray()
        val items = (0 until array.length()).mapNotNull { index ->
            val program = array.optJSONObject(index) ?: return@mapNotNull null
            val mainSong = program.optJSONObject("mainSong") ?: return@mapNotNull null
            parseSong(
                item = mainSong,
                fallbackCover = program.optString("coverUrl").ifBlank { channel.coverUrl },
            ).takeIf { it.id > 0 && it.name.isNotBlank() }
        }.distinctBy { it.id }
        val nextOffset = offset + array.length()
        val total = response.optInt("count", nextOffset)
        val hasMore = response.optBoolean("more", nextOffset < total)
        return DiscoveryPage(items, nextOffset, hasMore)
    }

    fun getPodcastDetail(id: Long, cookies: Map<String, String>): PodcastChannel {
        val response = weapi(
            path = "djradio/v2/get",
            payload = JSONObject().put("id", id),
            cookies = cookies,
        )
        val item = response.optJSONObject("data")
            ?: response.optJSONObject("djRadio")
            ?: throw IOException("播客详情数据缺失")
        return parsePodcast(item).takeIf { it.id > 0 && it.name.isNotBlank() }
            ?: throw IOException("播客详情数据无效")
    }

    fun getChartList(cookies: Map<String, String>): List<ChartInfo> {
        val raw = api.get("https://music.163.com/api/toplist/detail", cookies, REQUEST_TIMEOUT_MS)
        val obj = JSONObject(raw).requireSuccess("排行榜加载失败")
        val arr = obj.optJSONArray("list") ?: throw IOException("排行榜数据缺失")
        return (0 until arr.length()).map { i ->
            val item = arr.getJSONObject(i)
            ChartInfo(
                id = item.optLong("id"),
                name = item.optString("name"),
                coverUrl = item.optString("coverImgUrl") ?: "",
                description = item.optString("description") ?: "",
                updateFrequency = item.optString("updateFrequency") ?: "",
                trackCount = item.optInt("trackCount"),
                playCount = item.optLong("playCount"),
                updateTime = item.optLong("updateTime"),
            )
        }
    }

    fun getChartSongs(chartId: Long, cookies: Map<String, String>): ChartSongsResult {
        val raw = api.post(
            "https://music.163.com/api/v3/playlist/detail",
            api.formBody("id", chartId.toString(), "n", "100000", "s", "8"),
            cookies,
            callTimeoutMs = REQUEST_TIMEOUT_MS,
        )
        val obj = JSONObject(raw).requireSuccess("榜单歌曲加载失败")
        val playlist = obj.optJSONObject("playlist") ?: throw IOException("榜单数据缺失")
        val tracks = playlist.optJSONArray("tracks") ?: JSONArray()
        val songs = (0 until tracks.length()).map { i ->
            parseSong(tracks.getJSONObject(i))
        }
        return ChartSongsResult(
            songs = songs,
            chartName = playlist.optString("name"),
            coverUrl = playlist.optString("coverImgUrl"),
            description = playlist.optString("description"),
            updateFrequency = playlist.optString("updateFrequency"),
            trackCount = playlist.optInt("trackCount", songs.size),
            playCount = playlist.optLong("playCount"),
            updateTime = playlist.optLong("updateTime"),
        )
    }

    fun getDailyRecommendSongs(cookies: Map<String, String>): List<Song> {
        val raw = api.post(
            "https://music.163.com/api/v3/discovery/recommend/songs",
            api.formBody(),
            cookies,
            callTimeoutMs = REQUEST_TIMEOUT_MS,
        )
        val obj = JSONObject(raw).requireSuccess("每日推荐加载失败")
        val data = obj.optJSONObject("data") ?: throw IOException("每日推荐数据缺失")
        val dailySongs = data.optJSONArray("dailySongs") ?: JSONArray()
        return (0 until dailySongs.length()).map { i -> parseSong(dailySongs.getJSONObject(i)) }
    }

    fun getNewAlbums(cookies: Map<String, String>, limit: Int = 20, offset: Int = 0): List<AlbumResult> {
        val raw = api.post(
            "https://music.163.com/api/album/new",
            api.formBody("area", "ALL", "limit", limit.toString(), "offset", offset.toString()),
            cookies,
            callTimeoutMs = REQUEST_TIMEOUT_MS,
        )
        val obj = JSONObject(raw).requireSuccess("新碟加载失败")
        val arr = obj.optJSONArray("albums") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val album = arr.getJSONObject(i)
            val artist = album.optJSONObject("artist")
            AlbumResult(
                id = album.optLong("id"),
                name = album.optString("name"),
                artist = artist?.optString("name") ?: "",
                coverUrl = album.optString("picUrl") ?: "",
                publishTime = album.optLong("publishTime")
            )
        }
    }

    fun getHotSearchKeywords(cookies: Map<String, String>): List<Pair<String, Int>> {
        val detail = try {
            val raw = api.weapiPost("hotsearchlist/get", "{}", cookies, REQUEST_TIMEOUT_MS)
            val obj = JSONObject(raw)
            if (obj.optLong("code") == 200L) {
                val data = obj.optJSONArray("data")
                if (data != null) {
                    (0 until data.length()).mapNotNull { i ->
                        val h = data.optJSONObject(i) ?: return@mapNotNull null
                        val keyword = h.optString("searchWord").ifBlank { h.optString("word") }
                        if (keyword.isBlank()) null else keyword to h.optInt("score", 0)
                    }
                } else emptyList()
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        if (detail.isNotEmpty()) return detail

        return try {
            val raw = api.get("https://music.163.com/api/search/hot", cookies, REQUEST_TIMEOUT_MS)
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) return emptyList()
            val data = obj.optJSONObject("result") ?: return emptyList()
            val hots = data.optJSONArray("hots") ?: return emptyList()
            (0 until hots.length()).map { i ->
                val h = hots.getJSONObject(i)
                h.optString("first") to h.optInt("score", 0)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 搜索联想词（WEAPI search/suggest/web）：解析 allMatch 关键词+类型，fallback 到歌曲名 */
    fun getSearchSuggestions(keywords: String, cookies: Map<String, String>): List<SearchSuggestion> {
        val keyword = keywords.trim()
        if (keyword.isEmpty()) return emptyList()
        return try {
            val response = weapi(
                path = "search/suggest/web",
                payload = JSONObject().put("s", keyword),
                cookies = cookies,
            )
            val result = response.optJSONObject("result") ?: return emptyList()
            val allMatch = result.optJSONArray("allMatch")
            if (allMatch != null && allMatch.length() > 0) {
                return (0 until allMatch.length()).mapNotNull { i ->
                    val item = allMatch.getJSONObject(i)
                    val word = item.optString("keyword").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    if (word == keyword) null else SearchSuggestion(word, SearchCategory.fromApiType(item.optInt("type", 1)))
                }.distinctBy { it.keyword to it.category }.take(8)
            }
            val songs = result.optJSONArray("songs") ?: return emptyList()
            (0 until songs.length()).mapNotNull { i ->
                val name = songs.getJSONObject(i).optString("name").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (name == keyword) null else SearchSuggestion(name, SearchCategory.SONGS)
            }.distinctBy { it.keyword }.take(8)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 新歌速递（WEAPI v1/discovery/new/songs）：areaId 0全部 7华语 96欧美 8日本 16韩国 */
    fun getNewSongs(cookies: Map<String, String>, areaId: Int = 0): List<Song> {
        val response = weapi(
            path = "v1/discovery/new/songs",
            payload = JSONObject().put("areaId", areaId).put("total", true),
            cookies = cookies,
        )
        val array = response.optJSONArray("data") ?: return emptyList()
        return (0 until array.length())
            .map { parseSong(array.getJSONObject(it)) }
            .filter { it.id > 0 && it.name.isNotBlank() }
            .distinctBy { it.id }
    }

    /** 曲风标签列表（WEAPI tag/list/get） */
    fun getStyleTags(cookies: Map<String, String>): List<StyleTag> {
        val response = weapi(
            path = "tag/list/get",
            payload = JSONObject(),
            cookies = cookies,
        )
        val array = response.optJSONArray("data") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val tag = array.optJSONObject(i) ?: return@mapNotNull null
            val name = tag.optString("name").ifBlank { tag.optString("tagName") }
            if (name.isBlank()) null
            else StyleTag(
                id = tag.optLong("id"),
                name = name,
                category = tag.optString("category").ifBlank { tag.optString("typeName") },
            )
        }.filter { it.id > 0 }.distinctBy { it.id }
    }
}

data class StyleTag(
    val id: Long,
    val name: String,
    val category: String = "",
)
