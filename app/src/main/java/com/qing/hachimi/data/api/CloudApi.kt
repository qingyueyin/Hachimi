package com.qing.hachimi.data.api

import com.qing.hachimi.util.AppLogger
import org.json.JSONObject

class CloudApi(private val api: NeteaseApi) {

    data class CloudSong(
        val id: Long,
        val name: String,
        val artists: String,
        val album: String,
        val coverUrl: String,
        val duration: Long = 0,
        val fileSize: Long = 0,
        val addTime: Long = 0
    )

    data class CloudDownloadInfo(
        val id: Long,
        val url: String?,
        val size: Long = 0,
        val type: String? = null
    )

    fun getCloudSongs(
        limit: Int = 30,
        offset: Int = 0,
        cookies: Map<String, String>
    ): Result<List<CloudSong>> {
        return try {
            val payload = JSONObject()
                .put("limit", limit)
                .put("offset", offset)

            val raw = api.weapiPost("v1/cloud/get", payload.toString(), cookies)
            if (raw.isBlank()) {
                AppLogger.warn("[CloudApi] getCloudSongs: empty response body")
                return Result.failure(Exception("服务器返回空响应，可能未登录或无云盘权限"))
            }
            val obj = JSONObject(raw)

            val code = obj.optLong("code")
            if (code != 200L) {
                val msg = obj.optString("message").ifEmpty { obj.optString("msg", "云盘获取失败 (code: $code)") }
                AppLogger.warn("[CloudApi] getCloudSongs: code=$code, msg=$msg, raw=${raw.take(300)}")
                return Result.failure(Exception(msg))
            }

            val dataArr = obj.optJSONArray("data") ?: run {
                AppLogger.warn("[CloudApi] getCloudSongs: no 'data' array, keys=${obj.keys().asSequence().toList()}")
                return Result.success(emptyList())
            }

            val result = mutableListOf<CloudSong>()
            for (i in 0 until dataArr.length()) {
                val item = dataArr.getJSONObject(i)
                result.add(toCloudSong(item))
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCloudDownloadUrl(id: Long, cookies: Map<String, String>): CloudDownloadInfo? {
        return try {
            val payload = JSONObject().put("songId", id)

            val raw = api.eapiPost("cloud/dowonload", payload.toString(), cookies)
            val obj = JSONObject(raw)

            if (obj.optLong("code") != 200L) {
                AppLogger.warn("[CloudApi] getCloudDownloadUrl: code=${obj.optLong("code")}, msg=${obj.optString("message")}, raw=${raw.take(300)}")
                return null
            }

            // Response has url/size at top level (NOT inside a "data" wrapper)
            val url = obj.optString("url").ifEmpty { null }
            AppLogger.info("[CloudApi] getCloudDownloadUrl: id=$id, url=${url?.take(80) ?: "(empty)"}, size=${obj.optLong("size")}")
            CloudDownloadInfo(
                id = id,
                url = url,
                size = obj.optLong("size"),
                type = null
            )
        } catch (e: Exception) {
            AppLogger.error("[CloudApi] getCloudDownloadUrl exception", e)
            null
        }
    }

    private fun toCloudSong(s: JSONObject): CloudSong {
        val simple = s.optJSONObject("simpleSong")
        val src = simple ?: s
        val al = src.optJSONObject("album") ?: src.optJSONObject("al")
        val ar = src.optJSONArray("artists") ?: src.optJSONArray("ar")
        return CloudSong(
            id = src.optLong("id", s.optLong("songId", 0)),
            name = src.optString("name").ifEmpty { s.optString("songName").ifEmpty { s.optString("name", "") } },
            artists = if (ar != null) {
                (0 until ar.length()).joinToString("/") { ar.getJSONObject(it).optString("name") }
            } else src.optString("artist").ifEmpty { s.optString("artist").ifEmpty { "未知艺术家" } },
            album = al?.optString("name") ?: src.optString("album").ifEmpty { s.optString("album").ifEmpty { "未知专辑" } },
            coverUrl = src.optString("picUrl").ifEmpty { al?.optString("picUrl") ?: s.optString("cover").ifEmpty { "" } },
            duration = src.optLong("duration", src.optLong("dt", 0)),
            fileSize = s.optLong("size", 0),
            addTime = s.optLong("addTime", 0)
        )
    }
}
