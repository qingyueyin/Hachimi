package com.qing.hachimi.util

import com.qing.hachimi.data.model.SongTagMetadata
import java.io.File
import java.nio.file.Files
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioTaggerTest {

    @Test
    fun `writes standard metadata fields into an mp3 tag`() {
        val file = Files.createTempFile("hachimi-tags", ".mp3").toFile()
        file.writeBytes(minimalMp3())
        try {
            val result = AudioTagger.writeTags(
                filePath = file.absolutePath,
                title = "Petal",
                artist = "Ariana Grande",
                album = "petal",
                coverData = ByteArray(0),
                metadata = SongTagMetadata(
                    albumArtist = "Ariana Grande",
                    aliases = listOf("Track alias"),
                    translatedTitles = listOf("Translated title"),
                    trackNumber = 7,
                    trackTotal = 12,
                    discNumber = 2,
                    discTotal = 3,
                    releaseYear = 2026,
                    lyricists = listOf("Lyricist"),
                    composers = listOf("Composer"),
                    arrangers = listOf("Arranger"),
                    producers = listOf("Producer"),
                    mixers = listOf("Mixer"),
                ),
            )

            assertTrue(result.isSuccess)
            val tag = AudioFileIO.read(file).tag
            assertEquals("Ariana Grande", tag.getFirst(FieldKey.ALBUM_ARTIST))
            val subtitle = tag.getFirst(FieldKey.SUBTITLE)
            assertTrue(subtitle.contains("Track alias"))
            assertTrue(subtitle.contains("Translated title"))
            assertEquals("7", tag.getFirst(FieldKey.TRACK))
            assertEquals("12", tag.getFirst(FieldKey.TRACK_TOTAL))
            assertEquals("2", tag.getFirst(FieldKey.DISC_NO))
            assertEquals("3", tag.getFirst(FieldKey.DISC_TOTAL))
            assertEquals("2026", tag.getFirst(FieldKey.YEAR))
            assertEquals("Lyricist", tag.getFirst(FieldKey.LYRICIST))
            assertEquals("Composer", tag.getFirst(FieldKey.COMPOSER))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `artwork mime type follows the downloaded image bytes`() {
        val detector = AudioTagger::class.java.declaredMethods
            .firstOrNull { it.name == "detectArtworkMimeType" }
            ?.apply { isAccessible = true }

        assertEquals(
            "image/png",
            detector?.invoke(
                AudioTagger,
                byteArrayOf(
                    0x89.toByte(), 0x50, 0x4e, 0x47,
                    0x0d, 0x0a, 0x1a, 0x0a,
                ),
                null,
            ),
        )
        assertEquals(
            "image/webp",
            detector?.invoke(AudioTagger, "RIFF0000WEBP".toByteArray(), "application/octet-stream"),
        )
    }

    @Test
    fun `missing audio file reports tag failure`() {
        val result = AudioTagger.writeTags(
            filePath = File("build", "missing-audio-file.flac").absolutePath,
            title = "Title",
            artist = "Artist",
            album = "Album",
            coverData = ByteArray(0),
        )

        assertTrue(result.isFailure)
    }

    private fun minimalMp3(): ByteArray {
        val frameSize = 417
        return ByteArray(frameSize * 12).also { bytes ->
            for (offset in bytes.indices step frameSize) {
                bytes[offset] = 0xff.toByte()
                bytes[offset + 1] = 0xfb.toByte()
                bytes[offset + 2] = 0x90.toByte()
                bytes[offset + 3] = 0x64.toByte()
            }
        }
    }
}
