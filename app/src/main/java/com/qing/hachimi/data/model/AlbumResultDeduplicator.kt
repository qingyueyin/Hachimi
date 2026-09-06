package com.qing.hachimi.data.model

import java.text.Normalizer
import java.util.Locale

object AlbumResultDeduplicator {
    fun canonicalize(albums: List<AlbumResult>): List<AlbumResult> = albums
        .groupBy(::semanticKey)
        .values
        .mapNotNull { candidates ->
            candidates
                .sortedWith(
                    compareByDescending<AlbumResult> { it.publishTime }
                        .thenBy { it.id },
                )
                .firstOrNull()
        }

    /** Same-day copies with the same title and artist represent one release. */
    private fun semanticKey(album: AlbumResult): String {
        val releaseDay = if (album.publishTime > 0L) album.publishTime / MILLIS_PER_DAY else 0L
        return "${normalize(album.name)}\u0000${normalize(album.artist)}\u0000$releaseDay"
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(PUNCTUATION, " ")
        .trim()
        .replace(WHITESPACE, " ")

    private const val MILLIS_PER_DAY = 86_400_000L
    private val PUNCTUATION = Regex("[\\p{P}]+")
    private val WHITESPACE = Regex("\\s+")
}
