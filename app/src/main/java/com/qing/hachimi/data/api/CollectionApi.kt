package com.qing.hachimi.data.api

import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.ArtistResult
import com.qing.hachimi.util.AppLogger
import org.json.JSONObject

/**
 * 收藏中心 API —— 已收藏专辑 / 关注的歌手
 * 参考 api-enhanced: album_sublist.js, artist_sublist.js（均为 WEAPI）
 */
class CollectionApi(private val api: NeteaseApi) {

    companion object {
        private const val REQUEST_TIMEOUT_MS = 12_000L
        private const val DEFAULT_PAGE_SIZE = 25
    }

    data class CollectionPage<T>(
        val items: List<T>,
        val total: Int = 0,
        val hasMore: Boolean = false,
    )

    /** 已收藏专辑（WEAPI album/sublist） */
    fun getSubscribedAlbums(
        cookies: Map<String, String>,
        limit: Int = DEFAULT_PAGE_SIZE,
        offset: Int = 0,
    ): CollectionPage<AlbumResult> {
        return try {
            val payload = JSONObject()
                .put("limit", limit)
                .put("offset", offset)
                .put("total", true)
            val raw = api.weapiPost("album/sublist", payload.toString(), cookies, REQUEST_TIMEOUT_MS)
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) {
                AppLogger.warn("[CollectionApi] getSubscribedAlbums code=${obj.optLong("code")} msg=${obj.optString("message")} raw=${raw.take(200)}")
                return CollectionPage(emptyList())
            }
            val arr = obj.optJSONArray("data") ?: return CollectionPage(emptyList())
            val items = (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i)
                val album = item?.optJSONObject("album") ?: item ?: return@mapNotNull null
                AlbumResult(
                    id = album.optLong("id"),
                    name = album.optString("name"),
                    artist = album.optJSONArray("artists")?.let { ar ->
                        (0 until ar.length()).joinToString(" / ") { ar.getJSONObject(it).optString("name") }
                    } ?: album.optString("artist").ifBlank { album.optJSONObject("artist")?.optString("name").orEmpty() },
                    coverUrl = album.optString("picUrl"),
                    publishTime = album.optLong("publishTime"),
                )
            }
            CollectionPage(
                items = items,
                total = obj.optInt("count", obj.optInt("total", items.size)),
                hasMore = items.size >= limit,
            )
        } catch (e: Exception) {
            AppLogger.error("[CollectionApi] getSubscribedAlbums failed", e)
            CollectionPage(emptyList())
        }
    }

    /** 关注的歌手（WEAPI artist/sublist） */
    fun getSubscribedArtists(
        cookies: Map<String, String>,
        limit: Int = DEFAULT_PAGE_SIZE,
        offset: Int = 0,
    ): CollectionPage<ArtistResult> {
        return try {
            val payload = JSONObject()
                .put("limit", limit)
                .put("offset", offset)
                .put("total", true)
            val raw = api.weapiPost("artist/sublist", payload.toString(), cookies, REQUEST_TIMEOUT_MS)
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) {
                AppLogger.warn("[CollectionApi] getSubscribedArtists code=${obj.optLong("code")} msg=${obj.optString("message")} raw=${raw.take(200)}")
                return CollectionPage(emptyList())
            }
            val arr = obj.optJSONArray("data") ?: return CollectionPage(emptyList())
            val items = (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i)
                val artist = item?.optJSONObject("artist") ?: item ?: return@mapNotNull null
                ArtistResult(
                    id = artist.optLong("id"),
                    name = artist.optString("name"),
                    avatarUrl = artist.optString("picUrl").ifBlank { artist.optString("img1v1Url") },
                    alias = artist.optString("alias").ifBlank { artist.optString("trans") },
                )
            }
            CollectionPage(
                items = items,
                total = obj.optInt("count", obj.optInt("total", items.size)),
                hasMore = items.size >= limit,
            )
        } catch (e: Exception) {
            AppLogger.error("[CollectionApi] getSubscribedArtists failed", e)
            CollectionPage(emptyList())
        }
    }
}
