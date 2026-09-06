package com.qing.hachimi.ui.screens

/** Parses the link variants users can paste from the NetEase web and mobile clients. */
object NeteaseLinkParser {
    enum class Type { SONG, ALBUM, PLAYLIST }

    data class Link(val type: Type, val id: String)

    private val pattern = Regex(
        """(?:^|[/#?&])(song|album|playlist)(?:[/=]|\?id=|&id=)(\d+)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(raw: String): Link? {
        val value = raw.trim()
        if (value.isBlank()) return null
        val match = pattern.find(value) ?: return null
        val type = when (match.groupValues[1].lowercase()) {
            "song" -> Type.SONG
            "album" -> Type.ALBUM
            "playlist" -> Type.PLAYLIST
            else -> return null
        }
        return Link(type, match.groupValues[2])
    }
}
