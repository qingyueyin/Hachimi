package com.qing.hachimi.data.api

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistApiTest {

    @Test
    fun `playlist detail keeps creator description play count and tags`() {
        val api = PlaylistApi(neteaseApi(PLAYLIST_RESPONSE))

        val detail = requireNotNull(api.getDetail("123", emptyMap()))

        assertEquals("创建者", detail.creator)
        assertEquals("歌单简介", detail.description)
        assertEquals(12345678L, detail.playCount)
        assertEquals(listOf("流行", "欧美"), detail.tags)
        assertEquals(1700000000000L, detail.createTime)
        assertEquals(1700100000000L, detail.updateTime)
        assertEquals(listOf(1L), detail.songs.map { it.id })
    }

    private fun neteaseApi(body: String): NeteaseApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()
        return NeteaseApi(client)
    }

    companion object {
        private val PLAYLIST_RESPONSE = """
            {
              "code": 200,
              "playlist": {
                "id": 123,
                "name": "歌单",
                "coverImgUrl": "cover",
                "creator": {"nickname": "创建者"},
                "trackCount": 1,
                "description": "歌单简介",
                "playCount": 12345678,
                "tags": ["流行", "欧美"],
                "createTime": 1700000000000,
                "updateTime": 1700100000000,
                "trackIds": [{"id": 1}],
                "tracks": [{
                  "id": 1,
                  "name": "歌曲",
                  "ar": [{"name": "歌手"}],
                  "al": {"name": "专辑", "picUrl": "cover"},
                  "dt": 180000
                }]
              }
            }
        """.trimIndent()
    }
}
