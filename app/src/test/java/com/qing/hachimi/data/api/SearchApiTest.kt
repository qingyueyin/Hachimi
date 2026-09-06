package com.qing.hachimi.data.api

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class SearchApiTest {

    @Test
    fun `album search preserves server paging data`() {
        val api = searchApi {
            """
            {
              "code": 200,
              "result": {
                "albumCount": 458,
                "albums": [{
                  "id": 390031118,
                  "name": "petal",
                  "artist": {"name": "Ariana Grande"},
                  "picUrl": "cover",
                  "publishTime": 1785427201000
                }]
              }
            }
            """.trimIndent()
        }

        val result = api.searchAlbums("Ariana Grande", emptyMap(), limit = 20, offset = 20)

        assertEquals(390031118L, result.albums.single().id)
        assertEquals(458, result.total)
        assertEquals(21, result.nextOffset)
    }

    @Test(expected = IOException::class)
    fun `search service errors are not reported as empty results`() {
        val api = searchApi { """{"code":500,"message":"服务暂不可用"}""" }

        api.searchAlbums("Ariana Grande", emptyMap())
    }

    @Test
    fun `playlist search maps collection metadata and paging`() {
        val api = searchApi {
            """
            {
              "code": 200,
              "result": {
                "playlistCount": 25,
                "playlists": [{
                  "id": 13563122536,
                  "name": "Ariana Grande 巡演歌单",
                  "coverImgUrl": "playlist-cover",
                  "creator": {"nickname": "创建者"},
                  "trackCount": 26,
                  "playCount": 26853,
                  "description": "歌单简介"
                }]
              }
            }
            """.trimIndent()
        }

        val result = api.searchPlaylists("Ariana Grande", emptyMap(), limit = 20, offset = 20)

        assertEquals(13563122536L, result.playlists.single().id)
        assertEquals("创建者", result.playlists.single().creator)
        assertEquals(26, result.playlists.single().trackCount)
        assertEquals(21, result.nextOffset)
        assertEquals(true, result.hasMore)
    }

    @Test
    fun `podcast search maps radio metadata and paging`() {
        val api = searchApi {
            """
            {
              "code": 200,
              "result": {
                "djRadiosCount": 10,
                "djRadios": [{
                  "id": 526811594,
                  "name": "Ariana Grande混音电台",
                  "picUrl": "podcast-cover",
                  "dj": {"nickname": "主播"},
                  "category": "电音",
                  "programCount": 241,
                  "playCount": 18113662,
                  "desc": "播客简介"
                }]
              }
            }
            """.trimIndent()
        }

        val result = api.searchPodcasts("Ariana Grande", emptyMap(), limit = 20, offset = 0)

        assertEquals(526811594L, result.podcasts.single().id)
        assertEquals("主播", result.podcasts.single().host)
        assertEquals("电音", result.podcasts.single().category)
        assertEquals(241, result.podcasts.single().programCount)
        assertEquals(1, result.nextOffset)
        assertEquals(true, result.hasMore)
    }

    private fun searchApi(body: () -> String): SearchApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body().toResponseBody())
                    .build()
            }
            .build()
        return SearchApi(NeteaseApi(client))
    }
}
