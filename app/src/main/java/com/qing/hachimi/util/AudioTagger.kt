package com.qing.hachimi.util

import com.qing.hachimi.data.model.SongTagMetadata
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

object AudioTagger {

    fun writeTags(
        filePath: String,
        title: String,
        artist: String,
        album: String,
        coverData: ByteArray,
        coverMimeType: String? = null,
        lyrics: String? = null,
        metadata: SongTagMetadata = SongTagMetadata(),
    ): Result<Unit> {
        val file = File(filePath)
        if (!file.exists()) {
            AppLogger.warn("[AudioTagger] 文件不存在: $filePath")
            return Result.failure(IllegalStateException("音频文件不存在"))
        }
        return try {
            val audioFile = AudioFileIO.read(file)
            var tag: Tag? = audioFile.tag
            if (tag == null) {
                tag = audioFile.createDefaultTag()
                audioFile.tag = tag
            }

            tag.setField(tag.createField(FieldKey.TITLE, title))
            tag.setField(tag.createField(FieldKey.ARTIST, artist))
            tag.setField(tag.createField(FieldKey.ALBUM, album))

            setOptionalField(tag, FieldKey.ALBUM_ARTIST, listOf(metadata.albumArtist), file)
            setOptionalField(
                tag,
                FieldKey.SUBTITLE,
                metadata.aliases + metadata.translatedTitles,
                file,
            )
            setOptionalField(tag, FieldKey.TRACK, listOfNotNull(metadata.trackNumber?.toString()), file)
            setOptionalField(tag, FieldKey.TRACK_TOTAL, listOfNotNull(metadata.trackTotal?.toString()), file)
            setOptionalField(tag, FieldKey.DISC_NO, listOfNotNull(metadata.discNumber?.toString()), file)
            setOptionalField(tag, FieldKey.DISC_TOTAL, listOfNotNull(metadata.discTotal?.toString()), file)
            setOptionalField(tag, FieldKey.YEAR, listOfNotNull(metadata.releaseYear?.toString()), file)
            setOptionalField(tag, FieldKey.LYRICIST, metadata.lyricists, file)
            setOptionalField(tag, FieldKey.COMPOSER, metadata.composers, file)
            setOptionalField(tag, FieldKey.ARRANGER, metadata.arrangers, file)
            setOptionalField(tag, FieldKey.PRODUCER, metadata.producers, file)
            setOptionalField(tag, FieldKey.MIXER, metadata.mixers, file)
            setOptionalField(tag, FieldKey.ENGINEER, metadata.engineers, file)
            setOptionalField(tag, FieldKey.REMIXER, metadata.remixers, file)
            setOptionalField(tag, FieldKey.COMMENT, listOfNotNull(metadata.comment), file)

            if (!lyrics.isNullOrBlank()) {
                try {
                    tag.setField(tag.createField(FieldKey.LYRICS, lyrics))
                } catch (e: Throwable) {
                    AppLogger.warn("[AudioTagger] 歌词写入跳过: ${file.name} - ${e.message}")
                }
            }

            if (coverData.isNotEmpty()) {
                try {
                    val artworkMimeType = detectArtworkMimeType(coverData, coverMimeType)
                    when (tag) {
                        is FlacTag -> {
                            val pic = tag.createArtworkField(
                                coverData, 3, artworkMimeType, "", 0, 0, 0, 0
                            )
                            tag.setField(pic)
                        }
                        else -> {
                            val artwork = ArtworkFactory.getNew()
                            artwork.binaryData = coverData
                            artwork.mimeType = artworkMimeType
                            artwork.pictureType = 3
                            tag.setField(artwork)
                        }
                    }
                } catch (e: Throwable) {
                    AppLogger.warn("[AudioTagger] 封面写入跳过: ${file.name} - ${e.message}")
                }
            }

            audioFile.commit()
            AppLogger.debug("[AudioTagger] 标签写入完成: ${file.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.error("[AudioTagger] 写入失败: ${file.name}", e)
            Result.failure(e)
        }
    }

    private fun setOptionalField(
        tag: Tag,
        key: FieldKey,
        rawValues: List<String>,
        file: File,
    ) {
        val values = rawValues.map(String::trim).filter(String::isNotEmpty).distinct()
        if (values.isEmpty()) return
        try {
            tag.setField(key, values.joinToString(" / "))
        } catch (e: Throwable) {
            AppLogger.warn("[AudioTagger] ${key.name} 写入跳过: ${file.name} - ${e.message}")
        }
    }

    private fun detectArtworkMimeType(data: ByteArray, reportedContentType: String?): String {
        return when {
            data.hasPrefix(0xff, 0xd8, 0xff) -> "image/jpeg"
            data.hasPrefix(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "image/png"
            data.hasPrefix(0x47, 0x49, 0x46, 0x38) -> "image/gif"
            data.size >= 12 &&
                data.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                data.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> "image/webp"
            else -> reportedContentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.startsWith("image/") }
                ?: "image/jpeg"
        }
    }

    private fun ByteArray.hasPrefix(vararg bytes: Int): Boolean =
        size >= bytes.size && bytes.indices.all { this[it].toInt() and 0xff == bytes[it] }
}
