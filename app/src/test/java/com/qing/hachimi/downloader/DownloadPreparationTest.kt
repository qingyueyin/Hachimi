package com.qing.hachimi.downloader

import com.qing.hachimi.data.api.SongApi
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class DownloadPreparationTest {

    @Test
    fun `builds embedded and sidecar lyrics from one lyric response`() {
        val song = Song(7, "Petal", "Ariana Grande", "petal", "cover")
        val lyrics = SongApi.LyricResult(
            lrc = "[00:01.00]base",
            tlyric = "[00:01.00]translation",
            romalrc = "[00:01.00]roman",
            yrc = "[123]word-by-word",
            ytlrc = "[123]translated-word-by-word",
            yromalrc = "[123]roman-word-by-word",
        )

        val result = DownloadPreparation.buildLyrics(
            song = song,
            lyrics = lyrics,
            options = DownloadPreparation.Options(
                quality = "exhigh",
                qualityLabel = "Hi-Res",
                downloadLyrics = true,
                embedLyrics = true,
                saveTlLrc = true,
                saveRomaLrc = true,
                saveYrc = true,
                artistDelimiter = "、",
            ),
        )

        assertTrue(result.embedded.orEmpty().contains("word-by-word"))
        assertTrue(result.embedded.orEmpty().contains("translated-word-by-word"))
        assertTrue(result.sidecar.orEmpty().contains("word-by-word"))
        assertTrue(result.sidecar.orEmpty().contains("roman-word-by-word"))
        assertEquals("Ariana Grande", result.formattedArtists)
    }

    @Test
    fun `embedded lyrics follow the single audio tag switch`() {
        val settings = mock(SettingsManager::class.java).apply {
            `when`(quality).thenReturn("exhigh")
            `when`(downloadLyrics).thenReturn(true)
            `when`(enableWriteTags).thenReturn(false)
            `when`(embedLyrics).thenReturn(true)
            `when`(artistDelimiter).thenReturn("、")
        }

        val options = DownloadPreparation.optionsFrom(settings)

        assertFalse(options.embedLyrics)
    }
}
