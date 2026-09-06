package com.qing.hachimi.data.api

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistApiTest {

    @Test
    fun `profile keeps hero image translations and content counts`() {
        val api = artistApi {
            """
            {
              "code": 200,
              "data": {
                "identify": {"imageDesc": "美国知名女歌手"},
                "artist": {
                  "id": 48161,
                  "name": "Ariana Grande",
                  "cover": "hero",
                  "avatar": "avatar",
                  "transNames": ["爱莉安娜·格兰德"],
                  "briefDesc": "介绍",
                  "albumSize": 151,
                  "musicSize": 1277
                }
              }
            }
            """.trimIndent()
        }

        val profile = api.getProfile(48161, emptyMap())

        assertEquals("hero", profile.coverUrl)
        assertEquals(listOf("爱莉安娜·格兰德"), profile.translatedNames)
        assertEquals("美国知名女歌手", profile.identity)
        assertEquals(151, profile.albumCount)
    }

    @Test
    fun `album page deduplicates ids but advances by raw server count`() {
        val api = artistApi {
            """
            {
              "code": 200,
              "more": true,
              "artist": {"name": "Ariana Grande"},
              "hotAlbums": [
                {"id": 390031118, "name": "petal", "picUrl": "one", "publishTime": 1785427201000},
                {"id": 390031118, "name": "petal", "picUrl": "duplicate", "publishTime": 1785427201000},
                {"id": 2, "name": "Second", "picUrl": "two", "publishTime": 2}
              ]
            }
            """.trimIndent()
        }

        val page = api.getAlbumsPage(48161, emptyMap(), limit = 20, offset = 20)

        assertEquals(listOf(390031118L, 2L), page.items.map { it.id })
        assertEquals("Ariana Grande", page.items.first().artist)
        assertEquals(23, page.nextOffset)
        assertTrue(page.hasMore)
    }

    @Test(expected = ArtistAuthenticationRequiredException::class)
    fun `similar artists exposes login requirement`() {
        val api = artistApi { """{"code":301,"message":"未登录"}""" }

        api.getSimilarArtists(48161, emptyMap())
    }

    private fun artistApi(body: () -> String): ArtistApi {
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
        return ArtistApi(NeteaseApi(client))
    }
}
