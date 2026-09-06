package com.qing.hachimi.data.api

import com.qing.hachimi.data.model.ArtistArea
import com.qing.hachimi.data.model.PodcastChannel
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DiscoveryApiTest {

    @Test
    fun `playlist page keeps server offset and removes duplicate ids`() {
        val api = discoveryApi {
            """
            {
              "code": 200,
              "total": 50,
              "more": true,
              "playlists": [
                {"id": 1, "name": "第一张", "coverImgUrl": "http://p1.music.126.net/a", "trackCount": 20},
                {"id": 1, "name": "重复项", "coverImgUrl": "http://p1.music.126.net/b"},
                {"id": 2, "name": "第二张", "coverImgUrl": "http://p1.music.126.net/c"}
              ]
            }
            """.trimIndent()
        }

        val page = api.getPlaylists(emptyMap(), "全部", limit = 20, offset = 20)

        assertEquals(listOf(1L, 2L), page.items.map { it.id })
        assertEquals(23, page.nextOffset)
        assertTrue(page.hasMore)
    }

    @Test
    fun `artist page parses string aliases and terminal page`() {
        val api = discoveryApi {
            """
            {
              "code": 200,
              "more": false,
              "artists": [
                {"id": 42, "name": "歌手", "picUrl": "cover", "alias": ["别名一", "别名二"], "albumSize": 7}
              ]
            }
            """.trimIndent()
        }

        val page = api.getArtists(emptyMap(), ArtistArea.HOT, limit = 30)

        assertEquals("别名一、别名二", page.items.single().alias)
        assertEquals(7, page.items.single().albumCount)
        assertFalse(page.hasMore)
    }

    @Test
    fun `podcast programs become downloadable songs with channel cover fallback`() {
        val api = discoveryApi {
            """
            {
              "code": 200,
              "count": 1,
              "more": false,
              "programs": [{
                "id": 99,
                "name": "一期节目",
                "mainSong": {
                  "id": 9001,
                  "name": "一期节目",
                  "artists": [{"name": "主播"}],
                  "album": {"name": "节目专辑"}
                }
              }]
            }
            """.trimIndent()
        }
        val channel = PodcastChannel(id = 8, name = "播客", coverUrl = "channel-cover")

        val page = api.getPodcastPrograms(channel, emptyMap())

        assertEquals(9001L, page.items.single().id)
        assertEquals("主播", page.items.single().artists)
        assertEquals("channel-cover", page.items.single().coverUrl)
        assertFalse(page.hasMore)
    }

    @Test
    fun `chart list keeps ids above 32 bit range`() {
        val api = discoveryApi {
            """
            {
              "code": 200,
              "list": [{
                "id": 18176153161,
                "name": "Realtime Share Chart",
                "coverImgUrl": "chart-cover"
              }]
            }
            """.trimIndent()
        }

        val chart = api.getChartList(emptyMap()).single()

        assertEquals(18_176_153_161L, chart.id.toLong())
    }

    @Test
    fun `chart songs keep chart description and update metadata`() {
        val api = discoveryApi {
            """
            {
              "code": 200,
              "playlist": {
                "name": "飙升榜",
                "coverImgUrl": "chart-cover",
                "description": "每天更新的热门歌曲",
                "updateFrequency": "每天更新",
                "trackCount": 1,
                "playCount": 80000,
                "updateTime": 1700000000000,
                "tracks": [{
                  "id": 1,
                  "name": "歌曲",
                  "ar": [{"name": "歌手"}],
                  "al": {"name": "专辑", "picUrl": "cover"}
                }]
              }
            }
            """.trimIndent()
        }

        val detail = api.getChartSongs(19723756, emptyMap())

        assertEquals("每天更新的热门歌曲", detail.description)
        assertEquals("每天更新", detail.updateFrequency)
        assertEquals(80000L, detail.playCount)
        assertEquals(1, detail.trackCount)
    }

    @Test
    fun `podcast detail keeps description and latest update time`() {
        val api = discoveryApi {
            """
            {
              "code": 200,
              "data": {
                "id": 8,
                "name": "播客",
                "picUrl": "cover",
                "dj": {"nickname": "主播"},
                "category": "音乐",
                "programCount": 20,
                "playCount": 90000,
                "desc": "播客简介",
                "createTime": 1600000000000,
                "lastProgramCreateTime": 1700000000000
              }
            }
            """.trimIndent()
        }

        val detail = api.getPodcastDetail(8, emptyMap())

        assertEquals("播客简介", detail.description)
        assertEquals("主播", detail.host)
        assertEquals(1700000000000L, detail.updateTime)
    }

    @Test(expected = IOException::class)
    fun `server errors are not converted to empty content`() {
        val api = discoveryApi { "{\"code\":500,\"message\":\"服务暂不可用\"}" }

        api.getRecommendedPlaylists(emptyMap())
    }

    private fun discoveryApi(body: () -> String): DiscoveryApi {
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
        return DiscoveryApi(NeteaseApi(client))
    }
}
