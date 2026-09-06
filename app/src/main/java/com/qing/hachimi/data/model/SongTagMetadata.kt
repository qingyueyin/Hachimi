package com.qing.hachimi.data.model

/** Standard audio metadata that is not needed by list UIs but should survive a download. */
data class SongTagMetadata(
    val albumArtist: String = "",
    val aliases: List<String> = emptyList(),
    val translatedTitles: List<String> = emptyList(),
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val discNumber: Int? = null,
    val discTotal: Int? = null,
    val releaseYear: Int? = null,
    val lyricists: List<String> = emptyList(),
    val composers: List<String> = emptyList(),
    val arrangers: List<String> = emptyList(),
    val producers: List<String> = emptyList(),
    val mixers: List<String> = emptyList(),
    val engineers: List<String> = emptyList(),
    val remixers: List<String> = emptyList(),
    val comment: String? = null,
)
