package com.qing.hachimi.data.api

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.json.JSONObject

class SongApiTest {

    @Test
    fun `creator roles preserve standard audio credits`() {
        val response = JSONObject(
            """
            {
              "data": {
                "songCreatorsRoleVos": [
                  {"roleName":"作词","creatorMetaVOS":[{"artistName":"Lyricist"}]},
                  {"roleName":"作曲","creatorMetaVOS":[{"artistName":"Composer"}]},
                  {"roleName":"编曲","creatorMetaVOS":[{"artistName":"Arranger"}]},
                  {"roleName":"制作人","creatorMetaVOS":[{"artistName":"Producer"}]},
                  {"roleName":"混音","creatorMetaVOS":[{"artistName":"Mixer"}]}
                ]
              },
              "code": 200
            }
            """.trimIndent(),
        )
        val api = SongApi(neteaseApi(response.toString()))
        val parser = SongApi::class.java.declaredMethods
            .firstOrNull { it.name == "toSongCreators" }
            ?.apply { isAccessible = true }
        val credits = parser?.invoke(api, response)

        assertNotNull(credits)
        assertEquals(listOf("Lyricist"), reflected(requireNotNull(credits), "getLyricists"))
        assertEquals(listOf("Composer"), reflected(credits, "getComposers"))
        assertEquals(listOf("Arranger"), reflected(credits, "getArrangers"))
        assertEquals(listOf("Producer"), reflected(credits, "getProducers"))
        assertEquals(listOf("Mixer"), reflected(credits, "getMixers"))
    }

    @Test
    fun `song detail preserves standard tag metadata`() {
        val response = """
            {
              "code": 200,
              "songs": [{
                "id": 42,
                "name": "Petal",
                "ar": [{"name": "Ariana Grande"}],
                "alia": ["Track alias"],
                "tns": ["Translated title"],
                "al": {
                  "name": "petal",
                  "picUrl": "cover",
                  "artist": {"name": "Ariana Grande"},
                  "size": 12
                },
                "no": 7,
                "cd": "2",
                "publishTime": 1785427200000,
                "dt": 180000
              }]
            }
        """.trimIndent()
        val song = JSONObject(response).getJSONArray("songs").getJSONObject(0)
        val api = SongApi(neteaseApi(response))
        val parser = SongApi::class.java.getDeclaredMethod("toSongDetail", JSONObject::class.java).apply {
            isAccessible = true
        }
        val detail = requireNotNull(parser.invoke(api, song))

        assertEquals(7, reflected(detail, "getTrackNumber"))
        assertEquals(12, reflected(detail, "getTrackTotal"))
        assertEquals(2, reflected(detail, "getDiscNumber"))
        assertEquals("Ariana Grande", reflected(detail, "getAlbumArtist"))
        assertEquals(listOf("Track alias"), reflected(detail, "getAliases"))
        assertEquals(listOf("Translated title"), reflected(detail, "getTranslatedTitles"))
        assertEquals(2026, reflected(detail, "getReleaseYear"))
    }

    @Test
    fun `quality detail uses music detail endpoint and returns best conventional quality`() {
        val response = """
            {
              "code": 200,
              "data": {
                "songId": 42,
                "h": {"br": 320000, "size": 8000000, "sr": 44100},
                "sq": {"br": 1058000, "size": 25000000, "sr": 48000},
                "hr": null,
                "jm": null,
                "sk": {"br": 811759, "size": 27000000, "sr": 44100}
              }
            }
        """.trimIndent()
        var capturedRequest: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(response.toResponseBody())
                    .build()
            }
            .build()
        val neteaseApi = NeteaseApi(client)

        val quality = SongApi(neteaseApi).getQualityInfo("42", emptyMap())

        assertNotNull(quality)
        assertEquals("lossless", quality?.get("bestLevel"))
        assertEquals("flac", quality?.get("type"))
        assertEquals(1_058, quality?.get("bitrate"))
        assertEquals(48_000, quality?.get("sampleRate"))
        assertEquals("${NeteaseApi.BASE_URL}/api/song/music/detail/get", capturedRequest?.url.toString())
        assertEquals("songId=42", capturedRequest?.body?.let { body ->
            okio.Buffer().use { buffer ->
                body.writeTo(buffer)
                buffer.readUtf8()
            }
        })
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

    private fun reflected(instance: Any, getter: String): Any? = runCatching {
        instance.javaClass.getMethod(getter).invoke(instance)
    }.getOrNull()
}
