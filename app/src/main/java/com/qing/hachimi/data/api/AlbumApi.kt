package com.qing.hachimi.data.api

import org.json.JSONObject

class AlbumApi(private val api: NeteaseApi) {

    data class AlbumInfo(
        val id: Long,
        val name: String,
        val coverUrl: String,
        val artist: String,
        val songs: List<PlaylistApi.SongInfo>,
        val description: String = "",
        val publishTime: Long = 0,
        val company: String = "",
        val subtype: String = "",
        val size: Int = 0,
    )

    fun getDetail(id: String, cookies: Map<String, String>): AlbumInfo? {
        return try {
            val raw = api.post(
                "https://music.163.com/api/v1/album/$id",
                api.formBody(),
                cookies,
                callTimeoutMs = 10_000L,
            )
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) return null

            val album = obj.optJSONObject("album") ?: return null
            val songs = obj.optJSONArray("songs") ?: return null

            val songList = (0 until songs.length()).map { i ->
                val s = songs.getJSONObject(i)
                PlaylistApi.SongInfo(
                    id = s.optLong("id"),
                    name = s.optString("name"),
                    artists = s.optJSONArray("ar")?.let { ar ->
                        (0 until ar.length()).joinToString("、") { ar.getJSONObject(it).optString("name") }
                    } ?: "",
                    album = s.optJSONObject("al")?.optString("name") ?: "",
                    coverUrl = s.optJSONObject("al")?.optString("picUrl") ?: "",
                    duration = s.optLong("dt")
                )
            }

            val artist = album.optJSONObject("artist")?.optString("name")
                ?: album.optJSONArray("artists")?.optJSONObject(0)?.optString("name")
                ?: ""

            AlbumInfo(
                id = album.optLong("id"),
                name = album.optString("name"),
                coverUrl = album.optString("picUrl"),
                artist = artist,
                songs = songList,
                description = album.optString("description").ifBlank { album.optString("briefDesc") },
                publishTime = album.optLong("publishTime"),
                company = album.optString("company"),
                subtype = album.optString("subType").ifBlank { album.optString("type") },
                size = album.optInt("size", songList.size),
            )
        } catch (e: Exception) {
            null
        }
    }
}
