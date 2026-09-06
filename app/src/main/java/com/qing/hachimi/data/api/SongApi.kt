package com.qing.hachimi.data.api

import com.qing.hachimi.util.AppLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

class SongApi(private val api: NeteaseApi) {

    data class SongDetail(
        val id: Long,
        val name: String,
        val artists: String,
        val album: String,
        val coverUrl: String,
        val duration: Long = 0,
        val albumArtist: String = "",
        val aliases: List<String> = emptyList(),
        val translatedTitles: List<String> = emptyList(),
        val trackNumber: Int? = null,
        val trackTotal: Int? = null,
        val discNumber: Int? = null,
        val discTotal: Int? = null,
        val releaseYear: Int? = null,
    )

    data class SongCreators(
        val lyricists: List<String> = emptyList(),
        val composers: List<String> = emptyList(),
        val arrangers: List<String> = emptyList(),
        val producers: List<String> = emptyList(),
        val mixers: List<String> = emptyList(),
        val engineers: List<String> = emptyList(),
        val remixers: List<String> = emptyList(),
    )

    data class UrlInfo(
        val url: String?,
        val level: String?,
        val size: Long = 0,
        val type: String? = null,
        val encodeType: String? = null
    )

    data class SongUrlResult(
        val id: Long,
        val url: String?,
        val level: String?,
        val size: Long = 0,
        val type: String?
    )

    fun getDetail(id: String, cookies: Map<String, String>): SongDetail? {
        return try {
            val c = JSONObject()
                .put("id", id.toLongOrNull() ?: id)
                .put("v", 0)
            val cArr = org.json.JSONArray().put(c)
            val raw = api.eapiPost(
                "v3/song/detail",
                "{\"c\":${org.json.JSONArray(cArr).toString()}}",
                cookies
            )
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) return null
            val s = obj.optJSONArray("songs")?.optJSONObject(0) ?: return null
            toSongDetail(s)
        } catch (e: Exception) {
            null
        }
    }

    fun getUrl(id: String, level: String, cookies: Map<String, String>): SongUrlResult? {
        return try {
            // Build the full header matching api-enhanced's eapi format
            // The authSecret in the response URL depends on these fields being correct
            val buildver = System.currentTimeMillis().toString().take(10)
            val headerObj = JSONObject().apply {
                put("os", "pc")
                put("osver", cookies["osver"] ?: "Microsoft-Windows-10-Professional-build-19045-64bit")
                put("deviceId", cookies["deviceId"] ?: "pyncm!")
                put("appVer", cookies["appver"] ?: "")
                put("versioncode", cookies["versioncode"] ?: "140")
                put("mobilename", cookies["mobilename"] ?: "")
                put("buildver", cookies["buildver"] ?: buildver)
                put("resolution", cookies["resolution"] ?: "1920x1080")
                put("__csrf", cookies["__csrf"] ?: "")
                put("channel", cookies["channel"] ?: "netease")
                put("requestId", (20000000..30000000).random().toString())
                if ((cookies["MUSIC_U"] ?: "").isNotEmpty()) {
                    put("MUSIC_U", cookies["MUSIC_U"]!!)
                }
                if ((cookies["MUSIC_A"] ?: "").isNotEmpty()) {
                    put("MUSIC_A", cookies["MUSIC_A"]!!)
                }
            }

            val ids = org.json.JSONArray().put(id.toLongOrNull() ?: 0)
            val payload = JSONObject()
                .put("ids", ids)
                .put("level", level)
                .put("encodeType", "flac")
                .put("header", headerObj.toString())
            if (level == "sky") {
                payload.put("immerseType", "c51")
            }

            val raw = api.eapiPost(
                "song/enhance/player/url/v1",
                payload.toString(),
                cookies,
                callTimeoutMs = 15_000L,
            )
            val obj = JSONObject(raw)

            // Check for error code in response
            val code = obj.optLong("code")
            if (code != 200L) {
                AppLogger.warn("getUrl returned code=$code")
                return null
            }

            val d = obj.optJSONArray("data")?.optJSONObject(0) ?: return null
            val url = d.optString("url").ifEmpty { null }
            val errorMsg = d.optString("message").ifEmpty { d.optString("msg") }

            if (url == null) {
                AppLogger.warn("URL is null for song $id (level=$level, code=$code, msg=$errorMsg)")
            }

            SongUrlResult(
                id = d.optLong("id"),
                url = url,
                level = d.optString("level"),
                size = d.optLong("size"),
                type = d.optString("type")
            )
        } catch (e: Exception) {
            AppLogger.error("getUrl exception", e)
            null
        }
    }

    data class LyricResult(
        val lrc: String = "",
        val tlyric: String = "",
        val romalrc: String = "",
        val yrc: String = "",
        val ytlrc: String = "",
        val yromalrc: String = ""
    )

    fun getLyric(id: String, cookies: Map<String, String>): String? {
        return getLyricFull(id, cookies)?.lrc?.takeIf { it.isNotBlank() }
    }

    fun getLyricFull(id: String, cookies: Map<String, String>): LyricResult? {
        return try {
            val raw = api.eapiPost(
                "song/lyric",
                "{\"id\":$id,\"tv\":-1,\"lv\":-1,\"rv\":-1,\"kv\":-1,\"_nmclfl\":1}",
                cookies
            )
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) return null
            LyricResult(
                lrc = obj.optJSONObject("lrc")?.optString("lyric") ?: "",
                tlyric = obj.optJSONObject("tlyric")?.optString("lyric") ?: "",
                romalrc = obj.optJSONObject("romalrc")?.optString("lyric") ?: "",
                yrc = obj.optJSONObject("yrc")?.optString("lyric") ?: "",
                ytlrc = obj.optJSONObject("ytlrc")?.optString("lyric") ?: "",
                yromalrc = obj.optJSONObject("yromalrc")?.optString("lyric") ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }
    fun getQualityInfo(id: String, cookies: Map<String, String>): Map<String, Any>? {
        return try {
            val raw = api.post(
                "${NeteaseApi.BASE_URL}/api/song/music/detail/get",
                api.formBody("songId", id),
                cookies,
                callTimeoutMs = 15_000L,
            )
            val obj = Json.parseToJsonElement(raw).jsonObject
            if (obj["code"]?.jsonPrimitive?.longOrNull != 200L) return null
            val data = obj["data"]?.jsonObject ?: return null

            // Spatial formats are intentionally excluded: they are alternate mixes, not
            // straightforward fidelity upgrades for a local stereo file.
            val levels = listOf(
                "jm" to "jymaster",
                "hr" to "hires",
                "sq" to "lossless",
                "h" to "exhigh",
                "m" to "higher",
                "l" to "standard",
            )
            val available = levels.mapNotNull { (key, level) ->
                (data[key] as? JsonObject)
                    ?.takeIf { it["br"]?.jsonPrimitive?.longOrNull?.let { bitrate -> bitrate > 0L } == true }
                    ?.let { level }
            }
            val bestLevel = available.firstOrNull() ?: return null
            val bestKey = levels.first { it.second == bestLevel }.first
            val best = data.getValue(bestKey).jsonObject
            val isLossless = bestLevel in setOf("lossless", "hires", "jymaster")

            mapOf(
                "id" to (data["songId"]?.jsonPrimitive?.longOrNull ?: 0L),
                "availableLevels" to available,
                "bestLevel" to bestLevel,
                "type" to if (isLossless) "flac" else "mp3",
                "bitrate" to ((best["br"]?.jsonPrimitive?.longOrNull ?: 0L) / 1_000L).toInt(),
                "sampleRate" to (best["sr"]?.jsonPrimitive?.longOrNull ?: 0L).toInt(),
                "bitDepth" to if (bestLevel in setOf("hires", "jymaster")) 24 else 16,
                "encoder" to if (isLossless) "FLAC" else "MP3",
                "size" to (best["size"]?.jsonPrimitive?.longOrNull ?: 0L),
                "duration" to 0L,
            )
        } catch (e: Exception) {
            AppLogger.error("获取歌曲音质详情失败: $id", e)
            null
        }
    }

    fun getCreators(id: String, cookies: Map<String, String>): SongCreators? {
        return try {
            val raw = api.post(
                "${NeteaseApi.BASE_URL}/api/song/creators",
                api.formBody("songId", id),
                cookies,
                callTimeoutMs = 10_000L,
            )
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) return null
            toSongCreators(obj)
        } catch (e: Exception) {
            AppLogger.warn("getCreators failed for song $id: ${e.message}")
            null
        }
    }

    private fun toSongDetail(s: JSONObject): SongDetail {
        val al = s.optJSONObject("al") ?: s.optJSONObject("album")
        val ar = s.optJSONArray("ar") ?: s.optJSONArray("artists")
        val discNumbers = NUMBER.findAll(s.optString("cd")).mapNotNull {
            it.value.toIntOrNull()
        }.toList()
        val publishTime = s.optLong("publishTime")
        return SongDetail(
            id = s.optLong("id"),
            name = s.optString("name"),
            artists = if (ar != null) {
                (0 until ar.length()).joinToString("、") { ar.getJSONObject(it).optString("name") }
            } else "",
            album = al?.optString("name") ?: "",
            coverUrl = al?.optString("picUrl") ?: "",
            duration = s.optLong("dt"),
            albumArtist = al?.optJSONObject("artist")?.optString("name").orEmpty(),
            aliases = s.stringList("alia"),
            translatedTitles = s.stringList("tns"),
            trackNumber = s.optInt("no").takeIf { it > 0 },
            trackTotal = al?.optInt("size")?.takeIf { it > 0 },
            discNumber = discNumbers.getOrNull(0)?.takeIf { it > 0 },
            discTotal = discNumbers.getOrNull(1)?.takeIf { it > 0 },
            releaseYear = publishTime.takeIf { it > 0 }?.let(::yearFromEpochMillis),
        )
    }

    private fun toSongCreators(obj: JSONObject): SongCreators {
        val lyricists = mutableListOf<String>()
        val composers = mutableListOf<String>()
        val arrangers = mutableListOf<String>()
        val producers = mutableListOf<String>()
        val mixers = mutableListOf<String>()
        val engineers = mutableListOf<String>()
        val remixers = mutableListOf<String>()
        val roles = obj.optJSONObject("data")?.optJSONArray("songCreatorsRoleVos")
        if (roles != null) {
            for (index in 0 until roles.length()) {
                val role = roles.optJSONObject(index) ?: continue
                val roleName = role.optString("roleName")
                val names = role.optJSONArray("creatorMetaVOS")?.let { creators ->
                    buildList {
                        for (creatorIndex in 0 until creators.length()) {
                            creators.optJSONObject(creatorIndex)
                                ?.optString("artistName")
                                ?.trim()
                                ?.takeIf(String::isNotEmpty)
                                ?.let(::add)
                        }
                    }
                }.orEmpty()
                when {
                    roleName.contains("作词") || roleName.contains("lyric", ignoreCase = true) -> lyricists += names
                    roleName.contains("作曲") || roleName.contains("compos", ignoreCase = true) -> composers += names
                    roleName.contains("编曲") || roleName.contains("arrang", ignoreCase = true) -> arrangers += names
                    roleName.contains("制作") || roleName.contains("produc", ignoreCase = true) -> producers += names
                    roleName.contains("混音") || roleName.contains("mix", ignoreCase = true) -> mixers += names
                    roleName.contains("录音") || roleName.contains("工程") || roleName.contains("engineer", ignoreCase = true) -> engineers += names
                    roleName.contains("重混") || roleName.contains("remix", ignoreCase = true) -> remixers += names
                }
            }
        }
        return SongCreators(
            lyricists = lyricists.distinct(),
            composers = composers.distinct(),
            arrangers = arrangers.distinct(),
            producers = producers.distinct(),
            mixers = mixers.distinct(),
            engineers = engineers.distinct(),
            remixers = remixers.distinct(),
        )
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val values = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until values.length()) {
                values.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }.distinct()
    }

    private fun yearFromEpochMillis(timestamp: Long): Int = Calendar
        .getInstance(TimeZone.getTimeZone("UTC"))
        .apply { timeInMillis = timestamp }
        .get(Calendar.YEAR)

    private companion object {
        val NUMBER = Regex("\\d+")
    }
}
