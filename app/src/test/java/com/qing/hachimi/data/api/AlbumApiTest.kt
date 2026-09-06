package com.qing.hachimi.data.api

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumApiTest {

    @Test
    fun `album detail keeps description and release metadata`() {
        val api = AlbumApi(neteaseApi(ALBUM_RESPONSE))

        val detail = requireNotNull(api.getDetail("390031118", emptyMap()))

        assertEquals("petal", detail.name)
        assertEquals("Ariana Grande", detail.artist)
        assertEquals("完整的专辑简介", detail.description)
        assertEquals(1785427200000L, detail.publishTime)
        assertEquals("Republic Records", detail.company)
        assertEquals("录音室版", detail.subtype)
        assertEquals(12, detail.size)
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
        private val ALBUM_RESPONSE = """
            {
              "code": 200,
              "album": {
                "id": 390031118,
                "name": "petal",
                "picUrl": "cover",
                "artist": {"name": "Ariana Grande"},
                "description": "完整的专辑简介",
                "publishTime": 1785427200000,
                "company": "Republic Records",
                "subType": "录音室版",
                "size": 12
              },
              "songs": [{
                "id": 1,
                "name": "Track",
                "ar": [{"name": "Ariana Grande"}],
                "al": {"name": "petal", "picUrl": "cover"},
                "dt": 180000
              }]
            }
        """.trimIndent()
    }
}
