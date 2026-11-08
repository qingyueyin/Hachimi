package com.qing.hachimi.data.api

import com.qing.hachimi.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

class PlaylistApi(private val api: NeteaseApi) {

    data class PlaylistInfo(
        val id: Long,
        val name: String,
        val coverUrl: String,
        val creator: String,
        val trackCount: Int,
        val songs: List<SongInfo>,
        val description: String = "",
        val playCount: Long = 0,
        val tags: List<String> = emptyList(),
        val createTime: Long = 0,
        val updateTime: Long = 0,
    )

    data class SongInfo(
        val id: Long,
        val name: String,
        val artists: String,
        val album: String,
        val coverUrl: String,
        val duration: Long = 0
    )

    suspend fun getDetail(id: String, cookies: Map<String, String>): PlaylistInfo? {
        return try {
            val raw = api.eapiPost(
                "v6/playlist/detail",
                "{\"id\":$id,\"n\":100000,\"s\":8}",
                cookies,
                callTimeoutMs = 15_000L,
            )
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) return null

            val pl = obj.optJSONObject("playlist") ?: return null
            val trackIds = pl.optJSONArray("trackIds") ?: JSONArray()
            val allIds = (0 until trackIds.length()).mapNotNull { index ->
                trackIds.optJSONObject(index)?.optLong("id")?.takeIf { it > 0L }
            }

            // v6/playlist/detail 的 tracks 最多只返回前 1000 首，后面的要按 trackIds 补
            val tracks = if (pl.has("tracks")) pl.optJSONArray("tracks") else null
            val parsed = if (tracks != null) parseTracks(tracks, cookies) else emptyList()
            val byId = parsed.associateBy { it.id }
            val missingIds = allIds.filterNot { byId.containsKey(it) }
            val fetched = if (missingIds.isEmpty()) emptyList() else fetchSongsByIds(missingIds, cookies)
            val allById = byId + fetched.associateBy { it.id }
            val songs = if (allIds.isEmpty()) {
                (parsed + fetched).distinctBy { it.id }
            } else {
                allIds.mapNotNull { allById[it] }
            }

            PlaylistInfo(
                id = pl.optLong("id"),
                name = pl.optString("name"),
                coverUrl = pl.optString("coverImgUrl"),
                creator = pl.optJSONObject("creator")?.optString("nickname") ?: "",
                trackCount = pl.optInt("trackCount"),
                songs = songs,
                description = pl.optString("description"),
                playCount = pl.optLong("playCount"),
                tags = pl.optJSONArray("tags")?.let { tags ->
                    (0 until tags.length()).mapNotNull { index ->
                        tags.optString(index).takeIf(String::isNotBlank)
                    }
                }.orEmpty(),
                createTime = pl.optLong("createTime"),
                updateTime = pl.optLong("updateTime"),
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun parseTracks(arr: JSONArray, cookies: Map<String, String>): List<SongInfo> {
        return try {
            val tracks = (0 until arr.length()).map { arr.getJSONObject(it) }
            val fetched = tracks.any { it.has("al") }
            if (fetched) {
                tracks.map { toSongInfo(it) }
            } else {
                val ids = tracks.map { it.optLong("id") }
                fetchSongsByIds(ids, cookies)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchSongsByIds(ids: List<Long>, cookies: Map<String, String>): List<SongInfo> {
        if (ids.isEmpty()) return emptyList()
        val fetched = coroutineScope {
            ids.chunked(300)
                .map { batch -> async(Dispatchers.IO) { fetchSongsBatch(batch, cookies) } }
                .awaitAll()
                .flatten()
        }
        val byId = fetched.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    private fun fetchSongsBatch(batch: List<Long>, cookies: Map<String, String>): List<SongInfo> {
        return try {
            val cArr = JSONArray(batch.map { JSONObject(mapOf("id" to it, "v" to 0)) })
            val raw = api.eapiPost(
                "v3/song/detail",
                "{\"c\":${cArr.toString()}}",
                cookies,
                callTimeoutMs = 15_000L,
            )
            val obj = JSONObject(raw)
            val songs = obj.optJSONArray("songs") ?: return emptyList()
            (0 until songs.length()).map { toSongInfo(songs.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    data class UserPlaylist(
        val id: Long,
        val name: String,
        val coverUrl: String,
        val trackCount: Int,
        val creator: String,
        val subscribed: Boolean = false
    )

    fun getUserPlaylists(userId: Long, cookies: Map<String, String>, limit: Int = 50, offset: Int = 0): List<UserPlaylist> {
        return try {
            val payload = JSONObject()
                .put("uid", userId)
                .put("limit", limit)
                .put("offset", offset)
                .put("includeVideo", true)
            val raw = api.weapiPost("user/playlist", payload.toString(), cookies)
            val obj = JSONObject(raw)
            val code = obj.optLong("code")
            if (code != 200L) {
                AppLogger.warn("[PlaylistApi] getUserPlaylists: code=$code, msg=${obj.optString("message").ifEmpty { obj.optString("msg") }}, raw=${raw.take(300)}")
                return emptyList()
            }
            val arr = obj.optJSONArray("playlist") ?: run {
                AppLogger.warn("[PlaylistApi] getUserPlaylists: no 'playlist' array in response, keys=${obj.keys().asSequence().toList()}")
                return emptyList()
            }
            (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                UserPlaylist(
                    id = p.optLong("id"),
                    name = p.optString("name"),
                    coverUrl = p.optString("coverImgUrl") ?: "",
                    trackCount = p.optInt("trackCount"),
                    creator = p.optJSONObject("creator")?.optString("nickname") ?: "",
                    subscribed = p.optBoolean("subscribed")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 拉全用户歌单（创建 + 收藏），避免只取到前 50 个 */
    fun getAllUserPlaylists(userId: Long, cookies: Map<String, String>): List<UserPlaylist> {
        val all = LinkedHashMap<Long, UserPlaylist>()
        var offset = 0
        repeat(50) {
            val page = getUserPlaylists(userId, cookies, limit = 50, offset = offset)
            if (page.isEmpty()) return all.values.toList()
            page.forEach { all[it.id] = it }
            if (page.size < 50) return all.values.toList()
            offset += page.size
        }
        return all.values.toList()
    }

    /**
     * 红心歌曲列表（likelist，无序）。
     * 通过 WEAPI song/like/get 获取全部红心歌曲 id，再批量拉取详情。
     */
    suspend fun getLikedSongs(uid: Long, cookies: Map<String, String>): List<SongInfo> {
        return try {
            // 不传 limit，服务端才会返回全部红心歌曲 id
            val payload = JSONObject().put("uid", uid)
            val raw = api.weapiPost("song/like/get", payload.toString(), cookies)
            val obj = JSONObject(raw)
            val code = obj.optLong("code")
            if (code != 200L) {
                AppLogger.warn("[PlaylistApi] getLikedSongs: code=$code, msg=${obj.optString("message").ifEmpty { obj.optString("msg") }}")
                return emptyList()
            }
            val ids = obj.optJSONArray("ids") ?: return emptyList()
            val idList = (0 until ids.length()).mapNotNull { ids.optLong(it).takeIf { it > 0L } }
            // 部分服务端仍会把 likelist 截到 1000，红心歌单（id = 用户 id）的 trackIds 是完整的
            val allIds = if (idList.size >= 1000) {
                val fullIds = getPlaylistTrackIds(uid.toString(), cookies)
                if (fullIds.size > idList.size) fullIds else idList
            } else {
                idList
            }
            fetchSongsByIds(allIds, cookies)
        } catch (e: Exception) {
            AppLogger.error("[PlaylistApi] getLikedSongs failed", e)
            emptyList()
        }
    }

    private fun getPlaylistTrackIds(playlistId: String, cookies: Map<String, String>): List<Long> {
        return try {
            val raw = api.eapiPost(
                "v6/playlist/detail",
                "{\"id\":$playlistId,\"n\":1,\"s\":8}",
                cookies,
                callTimeoutMs = 15_000L,
            )
            val pl = JSONObject(raw).optJSONObject("playlist") ?: return emptyList()
            val arr = pl.optJSONArray("trackIds") ?: return emptyList()
            (0 until arr.length()).mapNotNull { index ->
                arr.optJSONObject(index)?.optLong("id")?.takeIf { it > 0L }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun toSongInfo(s: JSONObject): SongInfo {
        val al = s.optJSONObject("al") ?: s.optJSONObject("album")
        val ar = s.optJSONArray("ar") ?: s.optJSONArray("artists")
        return SongInfo(
            id = s.optLong("id"),
            name = s.optString("name"),
            artists = if (ar != null) {
                (0 until ar.length()).joinToString("、") { ar.getJSONObject(it).optString("name") }
            } else "",
            album = al?.optString("name") ?: "",
            coverUrl = al?.optString("picUrl") ?: "",
            duration = s.optLong("dt")
        )
    }
}
