package com.qing.hachimi.data.api

import com.qing.hachimi.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject

/**
 * 听歌数据 API —— 听歌排行 / 最近播放 / 听歌足迹
 * 参考 api-enhanced: user_record.js, recent_listen_list.js, listen_data_*.js, summary_annual.js
 */
class ListenDataApi(private val api: NeteaseApi) {

    companion object {
        private const val REQUEST_TIMEOUT_MS = 12_000L
    }

    data class RecordEntry(
        val song: PlaylistApi.SongInfo,
        val playCount: Long = 0,
        val playTime: Long = 0,
    )

    /** 年度听歌足迹/报告摘要（宽松解析，字段缺失时为默认值） */
    data class YearReport(
        val year: Int = 0,
        val songCount: Long = 0,
        val listenMinutes: Long = 0,
        val artistCount: Long = 0,
        val albumCount: Long = 0,
        val topSongs: List<RecordEntry> = emptyList(),
    )

    /** 听歌排行：type=0 所有时间，type=1 最近一周（WEAPI v1/play/record） */
    fun getUserRecord(uid: Long, type: Int, cookies: Map<String, String>): List<RecordEntry> {
        return try {
            val payload = JSONObject().put("uid", uid).put("type", type)
            val raw = api.weapiPost("v1/play/record", payload.toString(), cookies, REQUEST_TIMEOUT_MS)
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) {
                AppLogger.warn("[ListenDataApi] getUserRecord code=${obj.optLong("code")} msg=${obj.optString("message")} raw=${raw.take(200)}")
                return emptyList()
            }
            val arr = obj.optJSONArray("weekData") ?: obj.optJSONArray("allData") ?: return emptyList()
            parseEntries(arr)
        } catch (e: Exception) {
            AppLogger.error("[ListenDataApi] getUserRecord failed", e)
            emptyList()
        }
    }

    /** 最近播放（EAPI pc/recent/listen/list，需要登录） */
    fun getRecentListen(cookies: Map<String, String>): List<RecordEntry> {
        return try {
            val raw = api.eapiPost("pc/recent/listen/list", "{}", cookies, REQUEST_TIMEOUT_MS)
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) {
                AppLogger.warn("[ListenDataApi] getRecentListen code=${obj.optLong("code")} msg=${obj.optString("message")} raw=${raw.take(200)}")
                return emptyList()
            }
            val arr = findSongArray(obj) ?: return emptyList()
            parseEntries(arr)
        } catch (e: Exception) {
            AppLogger.error("[ListenDataApi] getRecentListen failed", e)
            emptyList()
        }
    }

    /** 听歌足迹 - 今日收听（EAPI content/activity/listen/data/today/song/play/rank） */
    fun getTodaySongRank(cookies: Map<String, String>): List<RecordEntry> {
        return try {
            val raw = api.eapiPost(
                "content/activity/listen/data/today/song/play/rank",
                "{}",
                cookies,
                REQUEST_TIMEOUT_MS,
            )
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) {
                AppLogger.warn("[ListenDataApi] getTodaySongRank code=${obj.optLong("code")} msg=${obj.optString("message")} raw=${raw.take(200)}")
                return emptyList()
            }
            val arr = findSongArray(obj) ?: return emptyList()
            parseEntries(arr)
        } catch (e: Exception) {
            AppLogger.error("[ListenDataApi] getTodaySongRank failed", e)
            emptyList()
        }
    }

    /** 听歌足迹 - 歌曲播放排行 Top20（EAPI content/activity/listen/data/song/play/rank，type=week|month） */
    fun getSongPlayRank(type: String, cookies: Map<String, String>): List<RecordEntry> {
        return try {
            val payload = JSONObject().put("type", type)
            val raw = api.eapiPost(
                "content/activity/listen/data/song/play/rank",
                payload.toString(),
                cookies,
                REQUEST_TIMEOUT_MS,
            )
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) {
                AppLogger.warn("[ListenDataApi] getSongPlayRank code=${obj.optLong("code")} msg=${obj.optString("message")} raw=${raw.take(200)}")
                return emptyList()
            }
            val arr = findSongArray(obj) ?: return emptyList()
            parseEntries(arr)
        } catch (e: Exception) {
            AppLogger.error("[ListenDataApi] getSongPlayRank failed", e)
            emptyList()
        }
    }

    /** 听歌足迹 - 年度听歌足迹（EAPI content/activity/listen/data/year/report） */
    fun getYearReport(cookies: Map<String, String>): YearReport? {
        return try {
            val raw = api.eapiPost(
                "content/activity/listen/data/year/report",
                "{}",
                cookies,
                REQUEST_TIMEOUT_MS,
            )
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) {
                AppLogger.warn("[ListenDataApi] getYearReport code=${obj.optLong("code")} msg=${obj.optString("message")} raw=${raw.take(200)}")
                return null
            }
            parseYearReport(obj.optJSONObject("data") ?: obj)
        } catch (e: Exception) {
            AppLogger.error("[ListenDataApi] getYearReport failed", e)
            null
        }
    }

    /** 年度听歌报告摘要（EAPI activity/summary/annual/{year}/{userdata|data}，2017-2023） */
    fun getAnnualSummary(year: String, cookies: Map<String, String>): YearReport? {
        return try {
            val key = if (year in setOf("2017", "2018", "2019")) "userdata" else "data"
            val raw = api.eapiPost(
                "activity/summary/annual/$year/$key",
                "{}",
                cookies,
                REQUEST_TIMEOUT_MS,
            )
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) {
                AppLogger.warn("[ListenDataApi] getAnnualSummary code=${obj.optLong("code")} msg=${obj.optString("message")} raw=${raw.take(200)}")
                return null
            }
            parseYearReport(obj.optJSONObject("data") ?: obj).copy(year = year.toIntOrNull() ?: 0)
        } catch (e: Exception) {
            AppLogger.error("[ListenDataApi] getAnnualSummary failed", e)
            null
        }
    }

    private fun parseYearReport(data: JSONObject): YearReport {
        val topArr = findSongArray(data)
        return YearReport(
            year = data.optInt("year", 0),
            songCount = data.optLong("songCount", data.optLong("playCount", 0)),
            listenMinutes = data.optLong("listenMinutes", data.optLong("listenTime", data.optLong("timeCount", 0))),
            artistCount = data.optLong("artistCount", 0),
            albumCount = data.optLong("albumCount", 0),
            topSongs = topArr?.let { parseEntries(it) }.orEmpty(),
        )
    }

    private fun findSongArray(obj: JSONObject): JSONArray? {
        val candidates = buildList {
            obj.optJSONObject("data")?.let { data ->
                add(data.optJSONArray("list"))
                add(data.optJSONArray("songs"))
                add(data.optJSONArray("songPlayRank"))
                add(data.optJSONArray("songRank"))
                add(data.optJSONArray("playRank"))
                add(data.optJSONArray("playSongs"))
                add(data.optJSONArray("todayRank"))
                add(data.optJSONArray("rank"))
                add(data.optJSONArray("items"))
            }
            add(obj.optJSONArray("list"))
            add(obj.optJSONArray("songs"))
            add(obj.optJSONArray("songPlayRank"))
            add(obj.optJSONArray("rank"))
            add(obj.optJSONArray("items"))
            add(obj.optJSONArray("data"))
        }
        return candidates.firstOrNull { it != null && it.length() > 0 }
    }

    private fun parseEntries(arr: JSONArray): List<RecordEntry> {
        val result = mutableListOf<RecordEntry>()
        for (i in 0 until arr.length()) {
            try {
                val item = arr.get(i)
                if (item !is JSONObject) continue
                val songObj = item.optJSONObject("song") ?: item
                val song = toSongInfo(songObj)
                if (song.id <= 0) continue
                result.add(
                    RecordEntry(
                        song = song,
                        playCount = item.optLong("playCount"),
                        playTime = item.optLong("playTime").takeIf { it > 0 }
                            ?: item.optLong("playTimes"),
                    )
                )
            } catch (_: Exception) {
                // Skip malformed entry
            }
        }
        return result
    }

    private fun toSongInfo(s: JSONObject): PlaylistApi.SongInfo {
        val al = s.optJSONObject("al") ?: s.optJSONObject("album")
        val ar = s.optJSONArray("ar") ?: s.optJSONArray("artists")
        return PlaylistApi.SongInfo(
            id = s.optLong("id"),
            name = s.optString("name"),
            artists = if (ar != null) {
                (0 until ar.length()).joinToString(" / ") { ar.getJSONObject(it).optString("name") }
            } else "",
            album = al?.optString("name") ?: "",
            coverUrl = al?.optString("picUrl") ?: "",
            duration = s.optLong("dt"),
        )
    }
}
