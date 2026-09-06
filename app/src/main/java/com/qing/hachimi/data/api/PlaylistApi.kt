package com.qing.hachimi.data.api

import com.qing.hachimi.util.AppLogger
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

    fun getDetail(id: String, cookies: Map<String, String>): PlaylistInfo? {
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
            val allIds = (0 until trackIds.length()).map { trackIds.getJSONObject(it).getLong("id") }

            val songs = if (pl.has("tracks")) {
                parseTracks(pl.getJSONArray("tracks"), allIds, cookies)
            } else {
                fetchSongsByIds(allIds, cookies)
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

    private fun parseTracks(arr: JSONArray, allIds: List<Long>, cookies: Map<String, String>): List<SongInfo> {
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

    private fun fetchSongsByIds(ids: List<Long>, cookies: Map<String, String>): List<SongInfo> {
        val result = mutableListOf<SongInfo>()
        ids.chunked(100).forEach { batch ->
            try {
                val cArr = JSONArray(batch.map { JSONObject(mapOf("id" to it, "v" to 0)) })
                val raw = api.eapiPost(
                    "v3/song/detail",
                    "{\"c\":${cArr.toString()}}",
                    cookies,
                    callTimeoutMs = 15_000L,
                )
                val obj = JSONObject(raw)
                val songs = obj.optJSONArray("songs") ?: return@forEach
                for (i in 0 until songs.length()) {
                    result.add(toSongInfo(songs.getJSONObject(i)))
                }
            } catch (e: Exception) {
                // Skip this batch
            }
        }
        return result
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

    /**
     * 红心歌曲列表（likelist，无序）。
     * 通过 WEAPI song/like/get 获取全部红心歌曲 id，再批量拉取详情。
     */
    fun getLikedSongs(uid: Long, cookies: Map<String, String>, limit: Int = 1000): List<SongInfo> {
        return try {
            val payload = JSONObject().put("uid", uid).put("limit", limit).put("offset", 0)
            val raw = api.weapiPost("song/like/get", payload.toString(), cookies)
            val obj = JSONObject(raw)
            val code = obj.optLong("code")
            if (code != 200L) {
                AppLogger.warn("[PlaylistApi] getLikedSongs: code=$code, msg=${obj.optString("message").ifEmpty { obj.optString("msg") }}")
                return emptyList()
            }
            val ids = obj.optJSONArray("ids") ?: return emptyList()
            val idList = (0 until ids.length()).mapNotNull { ids.optLong(it).takeIf { it > 0L } }
            fetchSongsByIds(idList, cookies)
        } catch (e: Exception) {
            AppLogger.error("[PlaylistApi] getLikedSongs failed", e)
            emptyList()
        }
    }

    private fun toSongInfo(s: JSONObject): SongInfo {        val al = s.optJSONObject("al") ?: s.optJSONObject("album")
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
