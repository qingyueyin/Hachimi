package com.qing.hachimi.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class NeteaseLinkParserTest {

    @Test
    fun `parses hash album links from music 163`() {
        assertEquals(
            NeteaseLinkParser.Link(NeteaseLinkParser.Type.ALBUM, "390031118"),
            NeteaseLinkParser.parse("https://music.163.com/#/album?id=390031118"),
        )
    }

    @Test
    fun `parses path and query forms`() {
        assertEquals(
            NeteaseLinkParser.Link(NeteaseLinkParser.Type.SONG, "123"),
            NeteaseLinkParser.parse("https://music.163.com/song?id=123"),
        )
        assertEquals(
            NeteaseLinkParser.Link(NeteaseLinkParser.Type.PLAYLIST, "456"),
            NeteaseLinkParser.parse("https://music.163.com/playlist/456"),
        )
    }
}
